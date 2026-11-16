package com.loc.benimdunyam.data.local.isolation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.loc.benimdunyam.core.auth.LocalArchiveAccessStore
import com.loc.benimdunyam.data.backup.BackupValidator
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.draft.JournalDraftStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.model.AccountMigrationRules
import com.loc.benimdunyam.domain.model.AccountShadowStorageRules
import com.loc.benimdunyam.domain.model.AccountMigrationSnapshotInfo
import com.loc.benimdunyam.domain.repository.BackupRepository
import com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Doğrulanmış şifreli kopyadan geçici olarak açılan veri bölümleri.
 *
 * Medya dosyaları yalnızca `useVerifiedPayload` çağrısı süresince kullanılabilir.
 * Bu veri yapısı canlı Room veritabanlarını değiştirmez.
 */
internal data class AccountShadowInput(
    val info: AccountMigrationSnapshotInfo,
    val jsonSections: Map<String, ByteArray>,
    val temporaryMediaRoot: File,
    val mediaPaths: Set<String>
)

/**
 * Mevcut verileri silmeden, kullanıcı hesabına özel ŞİFRELİ hazırlık kopyası oluşturur.
 *
 * Güvenli yaklaşım:
 * 1) Açık SQLite dosyalarını doğrudan kopyalamak YASAK. Ana Room'un transaction
 *    içindeki mantıksal görüntüsü ve diğer repository akışları okunur.
 * 2) Dört veritabanının kayıtları + uygulama-içi görseller ZIP'e aktarılır.
 * 3) ZIP akışı disk üzerine yazılmadan önce Android Keystore AES-GCM ile şifrelenir.
 * 4) Kopya yeniden çözülür; her kayıt SHA-256, sayılar ve JSON yapısı kontrol edilir.
 * 5) Yalnızca doğrulanan geçici dosyanın adı `verified` olarak değiştirilir.
 *
 * Bu hazırlık kopyası CANLI hesap veritabanı değildir. Fotoğraf yolu eşleme,
 * tam tutarlı çoklu-DB dondurma, yeni hesaba yazma ve geri dönüş testi tamamlanmadan
 * otomatik aktivasyon yapılmaz. `.bdbackup` biçimi kesinlikle değişmez.
 */
@Singleton
class AccountMigrationSnapshotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val layout: AccountStorageLayout,
    private val accessStore: LocalArchiveAccessStore,
    private val firebaseAuth: FirebaseAuth,
    private val backupRepository: BackupRepository,
    private val backupValidator: BackupValidator,
    private val moods: JournalMoodRepository,
    private val drafts: JournalDraftStore,
    private val featured: YearFeaturedMemoryRepository,
    private val coordinator: BackupWriteCoordinator
) {
    private val gson = Gson()
    private val mutex = Mutex()

    private data class EntryDigest(val path: String, val bytes: Long, val sha256: String)
    private data class DataCounts(
        val journals: Int, val moods: Int, val drafts: Int, val featured: Int,
        val media: Int
    )

    companion object {
        private const val KEY_ALIAS = "benimdunyam_local_isolation_aes_v1"
        private const val MAC_ALIAS = "benimdunyam_local_isolation_hmac_v1"
        private const val MAC_SIZE = 32
        private val MAGIC = "BDSTG1".toByteArray(Charsets.US_ASCII)
        private const val MAX_JSON_BYTES = 16 * 1024 * 1024
        private const val MAX_MEDIA_FILE_BYTES = 20L * 1024 * 1024
        private const val MAX_MEDIA_TOTAL_BYTES = 300L * 1024 * 1024
        private const val MAX_MEDIA_FILES = 3_000
        private const val MAX_MANIFEST_BYTES = 1024 * 1024
    }

    /**
     * Hesaba ait mevcut hazırlık kopyasını dosyadan yeniden okuyup doğrular.
     * `true` varsayımıyla yalnızca dosyanın varlığına güvenmeyiz.
     */
    suspend fun inspect(): AccountMigrationSnapshotInfo? = withContext(Dispatchers.IO) {
        val uid = requireOwnerUid()
        val file = layout.verifiedSnapshot(uid)
        if (!file.isFile) return@withContext null
        verifyEncryptedFile(uid, file)
    }

    /**
     * İşlem yalnızca kullanıcı onayından sonra çağrılır. Eski kopya varsa asla
     * üzerine yazılmaz: veri güvenliği için kullanıcıya mevcut kopya gösterilir.
     */
    suspend fun prepareWithConsent(): AccountMigrationSnapshotInfo = mutex.withLock {
        withContext(Dispatchers.IO) {
            val uid = requireOwnerUid()
            val finalFile = layout.verifiedSnapshot(uid)
            if (finalFile.isFile) return@withContext verifyEncryptedFile(uid, finalFile)

            coordinator.withExclusiveRestore {
                requireOwnerUid(uid)
                val root = layout.stagingFolder(uid)
                check(root.isDirectory || root.mkdirs()) { "Hesap alanı oluşturulamadı." }
                val pending = File(root, "local_snapshot_v1.pending")
                // Eski bir başarısız denemeden kalan GEÇİCİ kopya dışında dosya silmeyiz.
                if (pending.exists()) check(pending.delete()) { "Eski geçici dosya temizlenemedi." }
                try {
                    val source = backupRepository.createBackupData()
                    val sourceBytes = gson.toJson(source).toByteArray(Charsets.UTF_8)
                    require(sourceBytes.size <= MAX_JSON_BYTES) { "Günlük arşivi bu sürümün boyut sınırını aşıyor." }
                    backupValidator.parseAndValidate(String(sourceBytes, Charsets.UTF_8))

                    val moodRecords = moods.observeAll().first()
                    val draftRecords = drafts.observeAll().first()
                    val featuredRecords = featured.observeAll().first()
                    val jsonSections = linkedMapOf(
                        "room/main.json" to sourceBytes,
                        "room/moods.json" to gson.toJson(moodRecords).toByteArray(Charsets.UTF_8),
                        "room/drafts.json" to gson.toJson(draftRecords).toByteArray(Charsets.UTF_8),
                        "room/featured.json" to gson.toJson(featuredRecords).toByteArray(Charsets.UTF_8)
                    )
                    check(jsonSections.values.all { it.size <= MAX_JSON_BYTES }) {
                        "Yerel veri bölümlerinden biri çok büyük. İşlem durduruldu."
                    }
                    val media = listPrivateMedia()
                    val totalMediaBytes = media.sumOf { it.second.length() }
                    check(totalMediaBytes <= MAX_MEDIA_TOTAL_BYTES) { "Görseller 300 MB sınırını aşıyor." }
                    check(root.usableSpace > totalMediaBytes + sourceBytes.size + 16L * 1024 * 1024) {
                        "Şifreli hazırlık dosyası için yeterli boş alan yok."
                    }
                    val counts = DataCounts(
                        journals = source.journals.size,
                        moods = moodRecords.size,
                        drafts = draftRecords.size,
                        featured = featuredRecords.size,
                        media = media.size
                    )
                    val preparedAt = System.currentTimeMillis()
                    writeEncryptedFile(uid, pending, jsonSections, media, counts, preparedAt)
                    val verified = verifyEncryptedFile(uid, pending)
                    requireOwnerUid(uid)
                    check(!finalFile.exists()) { "Bu hesap için zaten bir hazırlık kopyası var." }
                    check(pending.renameTo(finalFile)) { "Doğrulanmış kopya son konumuna alınamadı." }
                    verified
                } catch (cancelled: CancellationException) {
                    pending.delete()
                    throw cancelled
                } catch (error: Exception) {
                    pending.delete()
                    throw error
                }
            }
        }
    }

    /**
     * Faz 2E.4: Kullanıcının önceden doğrulanmış şifreli kopyasını bir geçici
     * çalışma alanına açar. Bu yöntem mevcut Room veya fotoğrafları DEĞİŞTİRMEZ.
     *
     * Önce dosyanın HMAC, AES-GCM ve manifest bütünlüğü yeniden doğrulanır.
     * `block` bitince (hata veya iptal dahil) geçici düz metin/görseller silinir.
     * Uygulama işlem sırasında kapanırsa kalan geçici alan sonraki denemede silinir.
     */
    internal suspend fun <T> useVerifiedPayload(
        expectedUid: String,
        block: suspend (AccountShadowInput) -> T
    ): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            requireOwnerUid(expectedUid)
            val source = layout.verifiedSnapshot(expectedUid)
            check(source.isFile) { "Bu hesap için doğrulanmış hazırlık kopyası yok." }
            val info = verifyEncryptedFile(expectedUid, source)
            val scratch = File(layout.stagingFolder(expectedUid), "transient_unpack_v1")
            // Bu klasör yalnızca bu sınıfın geçici çalışma alanıdır.
            if (scratch.exists()) check(scratch.deleteRecursively()) { "Eski geçici alan temizlenemedi." }
            check(scratch.mkdirs()) { "Geçici alan oluşturulamadı." }
            try {
                val sections = linkedMapOf<String, ByteArray>()
                val actual = linkedMapOf<String, EntryDigest>()
                var manifest: JSONObject? = null
                var mediaBytes = 0L
                val mediaPaths = linkedSetOf<String>()
                DataInputStream(LimitedInputStream(FileInputStream(source), source.length() - MAC_SIZE)).use { input ->
                    val magic = ByteArray(MAGIC.size)
                    input.readFully(magic)
                    check(magic.contentEquals(MAGIC)) { "Hazırlık kopyasının başlığı geçersiz." }
                    val iv = ByteArray(12)
                    input.readFully(iv)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                    CipherInputStream(input, cipher).use { decrypted ->
                        ZipInputStream(decrypted).use { zip ->
                            var entries = 0
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                check(!entry.isDirectory) { "Beklenmedik arşiv klasörü." }
                                val path = entry.name
                                val isRoom = path in setOf(
                                    "room/main.json", "room/moods.json", "room/drafts.json", "room/featured.json"
                                )
                                val isMedia = isApprovedMediaEntry(path)
                                check(path == "manifest.json" || isRoom || isMedia) { "Arşivde beklenmeyen dosya." }
                                check(path !in actual && (manifest == null)) { "Tekrarlı arşiv girdisi." }
                                val sha = MessageDigest.getInstance("SHA-256")
                                var size = 0L
                                val outputMemory = if (isRoom || path == "manifest.json") ByteArrayOutputStream() else null
                                val outputFile = if (isMedia) {
                                    val target = File(scratch, path).canonicalFile
                                    check(target.path.startsWith(scratch.canonicalPath + File.separator)) { "Geçersiz medya yolu." }
                                    check(target.parentFile.mkdirs() || target.parentFile.isDirectory) { "Medya klasörü oluşturulamadı." }
                                    target
                                } else null
                                val fileStream = outputFile?.outputStream()
                                try {
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read < 0) break
                                        size += read
                                        check(size <= (if (path == "manifest.json") MAX_MANIFEST_BYTES.toLong()
                                        else if (isRoom) MAX_JSON_BYTES.toLong()
                                        else MAX_MEDIA_FILE_BYTES)) { "Kopyadaki bir dosya çok büyük." }
                                        sha.update(buffer, 0, read)
                                        outputMemory?.write(buffer, 0, read)
                                        fileStream?.write(buffer, 0, read)
                                    }
                                } finally {
                                    fileStream?.close()
                                }
                                if (path == "manifest.json") {
                                    manifest = JSONObject(String(requireNotNull(outputMemory).toByteArray(), Charsets.UTF_8))
                                } else {
                                    actual[path] = EntryDigest(path, size, hex(sha.digest()))
                                    if (isRoom) sections[path] = requireNotNull(outputMemory).toByteArray()
                                    if (isMedia) {
                                        mediaBytes += size
                                        check(mediaBytes <= MAX_MEDIA_TOTAL_BYTES) { "Medya sınırı aşıldı." }
                                        mediaPaths.add(path)
                                    }
                                }
                                zip.closeEntry()
                                entries++
                                check(entries <= MAX_MEDIA_FILES + 5) { "Arşiv dosya sayısı çok yüksek." }
                            }
                            // GCM doğrulamasının tamamlanabilmesi için kalan akış da okunur.
                            val remaining = ByteArray(8192)
                            while (decrypted.read(remaining) != -1) { }
                        }
                    }
                }
                val meta = requireNotNull(manifest) { "Manifest yok." }
                check(meta.getInt("version") == 1 &&
                        meta.getString("accountKey") == AccountMigrationRules.folderKey(expectedUid)) {
                    "Kopya sürümü veya hesap sahibi uyuşmuyor."
                }
                val manifestEntries = meta.getJSONArray("entries")
                val expected = linkedMapOf<String, EntryDigest>()
                for (i in 0 until manifestEntries.length()) {
                    val e = manifestEntries.getJSONObject(i)
                    val name = e.getString("path")
                    check(name !in expected) { "Manifestte yinelenen kayıt." }
                    expected[name] = EntryDigest(name, e.getLong("bytes"), e.getString("sha256"))
                }
                check(expected == actual) { "Geri açılan dosyalar manifestle uyuşmuyor." }
                check(sections.size == 4 && meta.getInt("mediaCount") == mediaPaths.size) {
                    "Arşivden bazı bölümler okunamadı."
                }
                requireOwnerUid(expectedUid)
                block(AccountShadowInput(info, sections, scratch, mediaPaths))
            } finally {
                // Geçici açık veriyi sonraki kullanıcının okuyabileceği şekilde bırakma.
                scratch.deleteRecursively()
            }
        }
    }

    /** Kaynak yolları ortak, birim testli güvenlik politikasından geçirilir. */
    private fun isApprovedMediaEntry(path: String): Boolean =
        AccountShadowStorageRules.safeMediaPath(path)

    private fun requireOwnerUid(expected: String? = null): String {
        val uid = firebaseAuth.currentUser?.uid
        check(AccountMigrationRules.canPrepareSnapshot(accessStore.ownerUidOrNull(), uid)) {
            "Önce yerel arşiv sahibi olan Firebase hesabıyla giriş yapmalısın."
        }
        if (expected != null) check(expected == uid) { "İşlem sırasında Firebase hesabı değişti." }
        return requireNotNull(uid)
    }

    /**
     * Sadece uygulamanın özel dosya alanındaki dört bilinen medya klasörü alınır.
     * İçerik URI'leri ve harici dosya referansları bu kopyaya DAHİL DEĞİLDİR.
     */
    private fun listPrivateMedia(): List<Pair<String, File>> {
        val names = listOf(
            "benim_dunyam_journal_photos",
            "benim_dunyam_journal_photo_drafts",
            "seasons",
            "people"
        )
        val result = mutableListOf<Pair<String, File>>()
        for (name in names) {
            val root = File(context.filesDir, name)
            if (!root.exists()) continue
            check(root.isDirectory) { "Beklenen klasör dosya türünde: $name" }
            val rootCanonical = root.canonicalFile
            val stack = ArrayDeque<Pair<File, Int>>()
            val visited = mutableSetOf<String>()
            stack.add(root to 0)
            while (stack.isNotEmpty()) {
                val (folder, depth) = stack.removeLast()
                check(depth <= 20) { "Medya klasörü çok derin. Güvenli kopyalama durduruldu." }
                // Sembolik bağlantı döngülerini tekrar ziyaret etmeyiz.
                check(visited.add(folder.canonicalPath)) { "Döngüsel medya klasörü tespit edildi." }
                val children = folder.listFiles() ?: error("Fotoğraf klasörü okunamadı: $name")
                for (child in children) {
                    // Canonical path kontrolü sembolik bağlantı ile kök dışına kaçmayı önler.
                    val canonical = child.canonicalFile
                    check(canonical.path.startsWith(rootCanonical.path + File.separator)) {
                        "Uygulama klasörü dışına çıkan medya bağlantısı bulundu."
                    }
                    if (child.isDirectory) stack.add(child to depth + 1)
                    else if (child.isFile) {
                        check(child.length() <= MAX_MEDIA_FILE_BYTES) { "20 MB'tan büyük medya dosyası var." }
                        val relative = canonical.path.removePrefix(rootCanonical.path + File.separator)
                            .replace(File.separatorChar, '/')
                        result.add("media/$name/$relative" to child)
                        check(result.size <= MAX_MEDIA_FILES) { "Görsel sayısı üst sınırı aşıyor." }
                    }
                }
            }
        }
        return result.sortedBy { it.first }
    }

    private fun writeEncryptedFile(
        uid: String,
        destination: File,
        sections: Map<String, ByteArray>,
        media: List<Pair<String, File>>,
        counts: DataCounts,
        timestamp: Long
    ) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        check(iv.size == 12) { "Geçersiz şifreleme IV'si." }
        FileOutputStream(destination).use { fileOut ->
            fileOut.write(MAGIC)
            fileOut.write(iv)
            CipherOutputStream(fileOut, cipher).use { encrypted ->
                ZipOutputStream(encrypted).use { zip ->
                    val entries = mutableListOf<EntryDigest>()
                    for ((path, bytes) in sections) {
                        entries.add(writeZipEntry(zip, path, ByteArrayInputStream(bytes)))
                    }
                    for ((path, file) in media) {
                        FileInputStream(file).use { input ->
                            entries.add(writeZipEntry(zip, path, BufferedInputStream(input)))
                        }
                    }
                    val manifest = JSONObject().apply {
                        put("version", 1)
                        put("accountKey", AccountMigrationRules.folderKey(uid))
                        put("createdAt", timestamp)
                        put("journalCount", counts.journals)
                        put("moodCount", counts.moods)
                        put("draftCount", counts.drafts)
                        put("featuredCount", counts.featured)
                        put("mediaCount", counts.media)
                        put("entries", JSONArray().apply {
                            entries.forEach { item ->
                                put(JSONObject().apply {
                                    put("path", item.path)
                                    put("bytes", item.bytes)
                                    put("sha256", item.sha256)
                                })
                            }
                        })
                    }
                    val bytes = manifest.toString().toByteArray(Charsets.UTF_8)
                    check(bytes.size <= MAX_MANIFEST_BYTES) { "Doğrulama dizini çok büyük." }
                    writeZipEntry(zip, "manifest.json", ByteArrayInputStream(bytes))
                }
            }
        }
        // CipherInputStream bazı sağlayıcılarda GCM hata bilgisini yutabilir.
        // Bu yüzden şifreli dosyaya ikinci, cihaz anahtarına bağlı HMAC ekleriz.
        // Doğrulama yapılmadan ZIP içeriği güvenilir sayılmaz.
        appendAuthenticationTag(uid, destination)
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, input: InputStream): EntryDigest {
        zip.putNextEntry(ZipEntry(path))
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size == -1) break
            zip.write(buffer, 0, size)
            digest.update(buffer, 0, size)
            total += size
        }
        zip.closeEntry()
        return EntryDigest(path, total, hex(digest.digest()))
    }

    private fun verifyEncryptedFile(uid: String, file: File): AccountMigrationSnapshotInfo {
        // Önce HMAC doğrulanır; saldırgan tarafından değiştirilmiş akış
        // hiçbir şekilde şifre çözme ve ZIP aşamasına sokulmaz.
        verifyAuthenticationTag(uid, file)
        val calculated = linkedMapOf<String, EntryDigest>()
        var manifest: JSONObject? = null
        var mainDataBytes: ByteArray? = null
        DataInputStream(LimitedInputStream(FileInputStream(file), file.length() - MAC_SIZE)).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            check(magic.contentEquals(MAGIC)) { "Hesap hazırlık dosyasının biçimi geçersiz." }
            val iv = ByteArray(12)
            input.readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            CipherInputStream(input, cipher).use { decrypted ->
                ZipInputStream(decrypted).use { zip ->
                    var count = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val path = entry.name
                        check(path == "manifest.json" ||
                                path.startsWith("room/") || path.startsWith("media/")) {
                            "Beklenmeyen arşiv dosyası."
                        }
                        check(!path.contains("..") && !path.startsWith("/")) { "Geçersiz dosya yolu." }
                        check(path !in calculated && (manifest == null)) { "Tekrarlanan veya sondan eklenen giriş." }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var sum = 0L
                        val capture = if (path == "manifest.json" || path == "room/main.json") ByteArrayOutputStream() else null
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read == -1) break
                            sum += read
                            check(sum <= (if (path == "manifest.json") MAX_MANIFEST_BYTES.toLong()
                            else if (path.startsWith("room/")) MAX_JSON_BYTES.toLong()
                            else MAX_MEDIA_FILE_BYTES)) { "Arşivdeki bir bölüm çok büyük." }
                            digest.update(buffer, 0, read)
                            capture?.write(buffer, 0, read)
                        }
                        if (path == "manifest.json") {
                            manifest = JSONObject(String(requireNotNull(capture).toByteArray(), Charsets.UTF_8))
                        } else {
                            calculated[path] = EntryDigest(path, sum, hex(digest.digest()))
                            if (path == "room/main.json") mainDataBytes = capture?.toByteArray()
                        }
                        zip.closeEntry()
                        count++
                        check(count <= MAX_MEDIA_FILES + 5) { "Arşiv giriş sayısı sınırı aşıldı." }
                    }
                    // ZIP'in sonundaki merkez dizini ve GCM etiketi de okunmalıdır.
                    // Şifre doğrulaması bitmeden kopya başarılı sayılamaz.
                    val rest = ByteArray(8192)
                    while (decrypted.read(rest) != -1) { /* GCM son etiketi doğrulanır. */ }
                }
            }
        }
        val source = requireNotNull(mainDataBytes) { "Ana Room verisi arşivde yok." }
        val validatedData = backupValidator.parseAndValidate(String(source, Charsets.UTF_8))
        val meta = requireNotNull(manifest) { "Doğrulama manifesti bulunamadı." }
        check(meta.getInt("version") == 1) { "Desteklenmeyen kopya sürümü." }
        check(meta.getString("accountKey") == AccountMigrationRules.folderKey(uid)) { "Kopya farklı hesaba ait." }
        val entriesArray = meta.getJSONArray("entries")
        check(entriesArray.length() == calculated.size) { "Eksik arşiv girişi." }
        val expected = linkedMapOf<String, EntryDigest>()
        for (index in 0 until entriesArray.length()) {
            val item = entriesArray.getJSONObject(index)
            val path = item.getString("path")
            check(path !in expected) { "Manifestte tekrar eden giriş." }
            expected[path] = EntryDigest(path, item.getLong("bytes"), item.getString("sha256"))
        }
        check(expected == calculated) { "Kopya bütünlüğü doğrulanamadı." }
        check(listOf("room/main.json", "room/moods.json", "room/drafts.json", "room/featured.json")
            .all { it in calculated }) { "Yerel veritabanı bölümlerinden biri eksik." }
        check(validatedData.journals.size == meta.getInt("journalCount")) { "Günlük sayısı tutarsız." }
        val mediaCount = calculated.keys.count { it.startsWith("media/") }
        check(mediaCount == meta.getInt("mediaCount")) { "Medya sayısı tutarsız." }
        return AccountMigrationSnapshotInfo(
            preparedAtMillis = meta.getLong("createdAt"),
            totalEntries = calculated.size,
            encryptedBytes = file.length(),
            validated = true,
            journalCount = meta.getInt("journalCount"),
            moodCount = meta.getInt("moodCount"),
            draftCount = meta.getInt("draftCount"),
            featuredCount = meta.getInt("featuredCount"),
            mediaCount = mediaCount
        )
    }

    /**
     * Dosyanın sonundaki 32 bayt HMAC-SHA256 etiketi şifreli dosyanın tamamını
     * ve dosyanın bağlı olduğu hesap anahtarını doğrular.
     */
    private fun appendAuthenticationTag(uid: String, file: File) {
        val mac = newMac(uid)
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n == -1) break
                mac.update(buffer, 0, n)
            }
        }
        FileOutputStream(file, true).use {
            it.write(mac.doFinal())
            it.fd.sync() // Dosya doğrulanmış sayılmadan önce fiziksel yazma tamamlanır.
        }
    }

    private fun verifyAuthenticationTag(uid: String, file: File) {
        check(file.length() > MAGIC.size + 12 + MAC_SIZE) { "Şifreli kopya eksik." }
        val mac = newMac(uid)
        val cipherLength = file.length() - MAC_SIZE
        FileInputStream(file).use { stream ->
            var remaining = cipherLength
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(read > 0) { "Şifreli dosya kesilmiş." }
                mac.update(buffer, 0, read)
                remaining -= read
            }
            val actual = ByteArray(MAC_SIZE)
            var offset = 0
            while (offset < actual.size) {
                val n = stream.read(actual, offset, actual.size - offset)
                check(n > 0) { "Bütünlük etiketi eksik." }
                offset += n
            }
            check(MessageDigest.isEqual(mac.doFinal(), actual)) {
                "Hesap kopyasının bütünlük doğrulaması başarısız."
            }
        }
    }

    private fun newMac(uid: String): Mac = Mac.getInstance("HmacSHA256").apply {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(MAC_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(MAC_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generator.generateKey()
        }
        init(key)
        update(AccountMigrationRules.folderKey(uid).toByteArray(Charsets.UTF_8))
    }

    /** Şifreli dosyanın sonundaki HMAC etiketini CipherInputStream'den gizler. */
    private class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = super.read()
            if (value >= 0) remaining--
            return value
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
