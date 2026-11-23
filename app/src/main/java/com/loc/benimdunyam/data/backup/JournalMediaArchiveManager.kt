package com.loc.benimdunyam.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.media.PublishedPhotoDeletion
import com.loc.benimdunyam.data.local.media.PublishedPhotoDeletionLedger
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Anı fotoğrafları için ayrı, şifreli .bdmedia yedeğinin okunur özeti. */
data class JournalMediaArchivePreview(
    val journalCount: Int,
    val archivePhotoCount: Int,
    val restorePhotoCount: Int,
    val alreadyPresentCount: Int,
    val blockedByDeletionCount: Int,
    val mediaBytes: Long,
    val createdAtMillis: Long,
    val encryptedSha256: String,
    val localStateSha256: String
)

data class JournalMediaArchiveRestoreResult(
    val restoredCount: Int,
    val alreadyPresentCount: Int,
    val deletedPhotoBlockedCount: Int,
    val layoutRestoredCount: Int
)

private data class MediaPhotoRecord(
    val journalId: String,
    val photoId: String,
    val orderIndex: Int,
    val isCover: Boolean,
    val size: Long,
    val sha256: String
) {
    val entryPath: String
        get() = "media/journals/${digestOf(journalId.toByteArray(Charsets.UTF_8))}/$photoId"
}

private data class MediaArchiveContent(
    val createdAtMillis: Long,
    val owner: String,
    val photos: List<MediaPhotoRecord>,
    val deletions: List<PublishedPhotoDeletion>
)

private data class MediaRestorePlan(
    val toRestore: List<MediaPhotoRecord>,
    val alreadyPresent: Int,
    val blocked: Int,
    val stateHash: String,
    val previouslyEmptyJournals: Set<String>
)

private fun digestOf(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

/**
 * Faz 2I.3: .bdbackup yapısını değiştirmeden günlük fotoğraflarını ayrı korur.
 *
 * Kullanılan şifreleme, mevcut BackupCryptoManager'ın AES-GCM/KDF biçimidir;
 * .bdmedia sadece DOSYA ADI uzantısıdır. Şifresiz ZIP hiçbir zaman SAF'a verilmez.
 * Geri yükleme mevcut fotoğrafları silmez, değiştirmez ve Room tablolarına dokunmaz.
 */
@Singleton
class JournalMediaArchiveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ActiveWorkspaceSession,
    private val backupRepository: BackupRepository,
    private val journalPhotoStore: JournalPhotoStore,
    private val ledger: PublishedPhotoDeletionLedger,
    private val crypto: BackupCryptoManager,
    private val writeCoordinator: BackupWriteCoordinator
) {
    private val operationMutex = Mutex()

    companion object {
        private const val FORMAT = "benim-dunyam-journal-media"
        private const val VERSION = 1
        private const val MANIFEST = "manifest.json"
        private const val MAX_PHOTOS = 500
        private const val MAX_DELETIONS = 5000
        private const val MAX_TOTAL_BYTES = 250L * 1024 * 1024
        private const val MAX_ENCRYPTED_BYTES = 300L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 1024 * 1024
        private const val MAX_PIXELS = 60_000_000L
        private val JOURNAL_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
        private val PHOTO_ID = Regex("^[0-9]{13}_[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
        private val HASH = Regex("^[a-f0-9]{64}$")
    }

    private fun requireWorkspace() {
        check(session.mode != WorkspaceMode.LOCKED && !session.needsRestart()) {
            "Yerel hesap alanı kilitli veya değişmiş. Uygulamayı yeniden aç."
        }
    }

    /** Başka hesaba ait medya ZIP'inin yanlışlıkla bu çalışma alanına alınmasını engeller. */
    private fun ownerTag(): String = when (session.mode) {
        WorkspaceMode.ACCOUNT -> "ACCOUNT:${session.accountUid ?: error("Hesap yok") }"
        WorkspaceMode.LEGACY -> "LEGACY"
        WorkspaceMode.LOCKED -> error("Kilitli çalışma alanı")
    }

    private fun temporary(suffix: String): File {
        val root = File(context.noBackupFilesDir, "bdmedia_temp")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Geçici klasör oluşturulamadı.")
        return File.createTempFile("bdmedia_", suffix, root)
    }

    /** Kullanıcıya özel SAF dosyası oluşturarak içeriği güvenli biçimde yazar ve geri okur. */
    suspend fun export(destination: Uri, passphrase: CharArray): Int = operationMutex.withLock {
        var plainZip: File? = null
        var encrypted: File? = null
        var checkZip: File? = null
        try {
            requireWorkspace()
            plainZip = writeCoordinator.withRegularWrite {
                withContext(Dispatchers.IO) { buildPlainArchive() }
            }
            encrypted = crypto.encryptArchive(plainZip, passphrase.copyOf())
            // Üretilen arşiv, kullanıcıya verilmeden önce tekrar açılarak doğrulanır.
            checkZip = crypto.decryptArchive(encrypted, passphrase.copyOf())
            val content = withContext(Dispatchers.IO) { inspectPlainArchive(requireNotNull(checkZip)) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    requireNotNull(encrypted).inputStream().buffered().use { input -> input.copyTo(output, 64 * 1024) }
                } ?: throw IOException("Seçilen hedef belgeye yazılamadı.")
                val expected = sha256OfFile(requireNotNull(encrypted))
                val observed = context.contentResolver.openInputStream(destination)?.use {
                    sha256OfStream(it, MAX_ENCRYPTED_BYTES).first
                } ?: throw IOException("Kaydedilen yedek geri okunamadı.")
                check(expected == observed) { "Kaydedilen belge doğrulanamadı; bu dosyayı yedek sayma." }
            }
            content.photos.size
        } finally {
            passphrase.fill('\u0000')
            checkZip?.delete()
            plainZip?.delete()
            encrypted?.delete()
        }
    }

    /** Seçilen SAF belgesi tamamen doğrulanır; hiçbir günlük veya fotoğraf yazılmaz. */
    suspend fun preview(source: Uri, passphrase: CharArray): JournalMediaArchivePreview =
        operationMutex.withLock {
            var encrypted: File? = null
            var plain: File? = null
            try {
                requireWorkspace()
                encrypted = withContext(Dispatchers.IO) { copyEncryptedSource(source) }
                val encryptedHash = withContext(Dispatchers.IO) { sha256OfFile(requireNotNull(encrypted)) }
                plain = crypto.decryptArchive(requireNotNull(encrypted), passphrase.copyOf())
                val archive = withContext(Dispatchers.IO) { inspectPlainArchive(requireNotNull(plain)) }
                val plan = writeCoordinator.withRegularWrite { buildRestorePlan(archive) }
                JournalMediaArchivePreview(
                    journalCount = archive.photos.map { it.journalId }.distinct().size,
                    archivePhotoCount = archive.photos.size,
                    restorePhotoCount = plan.toRestore.size,
                    alreadyPresentCount = plan.alreadyPresent,
                    blockedByDeletionCount = plan.blocked,
                    mediaBytes = archive.photos.sumOf { it.size },
                    createdAtMillis = archive.createdAtMillis,
                    encryptedSha256 = encryptedHash,
                    localStateSha256 = plan.stateHash
                )
            } finally {
                passphrase.fill('\u0000')
                plain?.delete()
                encrypted?.delete()
            }
        }

    /**
     * Ayrı onaydan sonra dosya + o anki yerel durum tekrar kontrol edilir.
     * Önce HER yeni fotoğraf özel geçici klasöre çıkarılıp doğrulanır;
     * daha sonra mevcut fotoğrafların üzerine yazmadan eksikler eklenir.
     */
    suspend fun restore(
        source: Uri,
        passphrase: CharArray,
        expected: JournalMediaArchivePreview
    ): JournalMediaArchiveRestoreResult = operationMutex.withLock {
        var encrypted: File? = null
        var plain: File? = null
        val staged = mutableListOf<File>()
        try {
            requireWorkspace()
            encrypted = withContext(Dispatchers.IO) { copyEncryptedSource(source) }
            check(withContext(Dispatchers.IO) { sha256OfFile(requireNotNull(encrypted)) } == expected.encryptedSha256) {
                "Yedek dosyası önizlemeden sonra değişmiş. Yeniden incele."
            }
            plain = crypto.decryptArchive(requireNotNull(encrypted), passphrase.copyOf())
            val archive = withContext(Dispatchers.IO) { inspectPlainArchive(requireNotNull(plain)) }
            writeCoordinator.withRegularWrite {
                val plan = buildRestorePlan(archive)
                check(plan.stateHash == expected.localStateSha256 &&
                        plan.toRestore.size == expected.restorePhotoCount) {
                    "Yerel anılar önizlemeden sonra değişmiş. Yeniden incele."
                }
                applyPlan(requireNotNull(plain), archive, plan, staged)
            }
        } finally {
            passphrase.fill('\u0000')
            withContext(Dispatchers.IO) { staged.forEach { it.delete() }; plain?.delete(); encrypted?.delete() }
        }
    }


    /**
     * Doğrulanmış plana göre fotoğrafları önce geçici klasörde hazırlar, sonra
     * mevcutların üzerine yazmadan ekler. Çağıran yazma kilidini tutmalıdır.
     */
    private suspend fun applyPlan(
        plain: File,
        archive: MediaArchiveContent,
        plan: MediaRestorePlan,
        staged: MutableList<File>
    ): JournalMediaArchiveRestoreResult {
        // İlk aşama: tamamını hazırla. Bozuk resim veya ZIP, hiçbir fotoğraf yazılmadan fark edilir.
        val prepared = withContext(Dispatchers.IO) {
            ZipFile(plain).use { zip ->
                plan.toRestore.associateWith { photo ->
                    val temp = temporary(".${photo.photoId.substringAfterLast('.')}")
                    staged += temp
                    val entry = zip.getEntry(photo.entryPath) ?: error("Medya eksik")
                    zip.getInputStream(entry).use { input ->
                        temp.outputStream().buffered().use { output ->
                            copyLimited(input, output, photo.size)
                        }
                    }
                    check(temp.length() == photo.size && sha256OfFile(temp) == photo.sha256) {
                        "Fotoğraf doğrulaması başarısız."
                    }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, options)
                    val pixels = options.outWidth.toLong() * options.outHeight.toLong()
                    check(pixels in 1..MAX_PIXELS) { "Geçersiz fotoğraf çözünürlüğü." }
                    temp
                }
            }
        }
        // Arşivdeki silme işaretlerini yalnızca birleştir; mevcut kayıtları azaltma.
        ledger.mergeVerifiedArchive(archive.deletions)
        var restored = 0
        for ((photo, temp) in prepared) {
            currentCoroutineContext().ensureActive()
            if (journalPhotoStore.restoreMissingFromCloud(photo.journalId, photo.photoId, temp)) {
                restored++
            } else {
                // Kullanıcı silme işaretini güncellediyse fotoğrafı GERİ GETİRME.
                throw IOException("Fotoğraf eşitleme sırasında durum değişti. Kısmi içe alma olabilir; yeniden incele.")
            }
        }
        var layoutCount = 0
        archive.photos.groupBy { it.journalId }.forEach { (journalId, photos) ->
            if (journalId in plan.previouslyEmptyJournals && photos.all { row ->
                    plan.toRestore.any { it.journalId == journalId && it.photoId == row.photoId }
                }) {
                val local = journalPhotoStore.list(journalId)
                val desiredOrder = photos.sortedBy { it.orderIndex }.map { it.photoId }
                val cover = photos.firstOrNull { it.isCover }?.photoId
                if (local.map { it.id }.toSet() == desiredOrder.toSet() &&
                    journalPhotoStore.applyApprovedCloudLayoutIfUnmodified(
                        journalId = journalId,
                        expectedLocalOrder = local.map { it.id },
                        expectedLocalCover = local.firstOrNull { it.isCover }?.id,
                        requestedOrder = desiredOrder,
                        requestedCover = cover
                    )) layoutCount++
            }
        }
        return JournalMediaArchiveRestoreResult(
            restoredCount = restored,
            alreadyPresentCount = plan.alreadyPresent,
            deletedPhotoBlockedCount = plan.blocked,
            layoutRestoredCount = layoutCount
        )
    }

    /**
     * Aşama 3 — tek yedek dosyası (.bdbackup arşiv sürüm 3) için şifresiz fotoğraf
     * ZIP'i üretir. Çağıran (CreateBackupUseCase) ortak yazma kilidini tutar.
     * Dönen geçici dosyanın silinmesi çağırana aittir.
     */
    internal suspend fun buildEmbeddedArchive(): File = withContext(Dispatchers.IO) {
        requireWorkspace()
        buildPlainArchive()
    }

    /** Gömülü fotoğraf arşivini doğrular ve fotoğraf sayısını döndürür. Hiçbir şey yazmaz. */
    internal suspend fun inspectEmbedded(plainZip: File): Int = withContext(Dispatchers.IO) {
        inspectPlainArchive(plainZip, requireSameOwner = false).photos.size
    }

    /**
     * Tek yedek dosyasının geri yüklenmesinde, anılar Room'a yazıldıktan SONRA
     * çağrılır. Hesap etiketi kontrol edilmez: kullanıcı bu dosyayı bilerek
     * (ör. yeni telefona) geri yüklemektedir. Diğer tüm kontroller aynıdır:
     * SHA-256, boyut, silme işaretleri, mevcut fotoğrafın üzerine yazmama.
     */
    internal suspend fun restoreEmbedded(plainZip: File): JournalMediaArchiveRestoreResult =
        operationMutex.withLock {
            val staged = mutableListOf<File>()
            try {
                requireWorkspace()
                val archive = withContext(Dispatchers.IO) {
                    inspectPlainArchive(plainZip, requireSameOwner = false)
                }
                writeCoordinator.withRegularWrite {
                    val plan = buildRestorePlan(archive)
                    applyPlan(plainZip, archive, plan, staged)
                }
            } finally {
                withContext(Dispatchers.IO) { staged.forEach { it.delete() } }
            }
        }

    private suspend fun buildPlainArchive(): File {
        val journals = backupRepository.createBackupData().journals
        val photos = mutableListOf<MediaPhotoRecord>()
        val sources = linkedMapOf<String, File>()
        var totalSize = 0L
        for (journal in journals) {
            require(JOURNAL_ID.matches(journal.id)) { "Geçersiz günlük kimliği." }
            val list = journalPhotoStore.list(journal.id)
            require(list.size <= JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL)
            val allowed = File(session.mediaRoot(),
                "benim_dunyam_journal_photos/${digestOf(journal.id.toByteArray(Charsets.UTF_8))}").canonicalFile
            for ((index, photo) in list.withIndex()) {
                check(PHOTO_ID.matches(photo.id)) { "Geçersiz fotoğraf kimliği." }
                val file = File(photo.absolutePath).canonicalFile
                check(file.isFile && file.parentFile == allowed &&
                        file.length() in 1..JournalPhotoStore.MAX_FILE_SIZE_BYTES) {
                    "Günlük fotoğrafı bulunamadı veya güvenli klasörün dışında."
                }
                totalSize += file.length()
                check(totalSize <= MAX_TOTAL_BYTES && photos.size < MAX_PHOTOS) {
                    "Medya yedeği 500 fotoğraf / 250 MB sınırını aşıyor."
                }
                val item = MediaPhotoRecord(journal.id, photo.id, index, photo.isCover,
                    file.length(), sha256OfFile(file))
                check(sources.putIfAbsent(item.entryPath, file) == null) { "Tekrarlanan fotoğraf kimliği." }
                photos += item
            }
        }
        val deletions = ledger.list()
        check(deletions.size <= MAX_DELETIONS)
        // Boş medya arşivi de silme defterini taşımak için kullanılabilir.
        val archive = MediaArchiveContent(System.currentTimeMillis(), ownerTag(), photos, deletions)
        val manifestBytes = buildManifest(archive).toString().toByteArray(Charsets.UTF_8)
        check(manifestBytes.size <= MAX_MANIFEST_BYTES) { "Medya manifesti çok büyük." }
        val zipFile = temporary(".zip")
        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                sources.forEach { (entryName, source) ->
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry(entryName))
                    source.inputStream().buffered().use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifestBytes)
                zip.closeEntry()
            }
            check(zipFile.length() in 1..MAX_ENCRYPTED_BYTES) { "Medya ZIP boyutu geçersiz." }
            return zipFile
        } catch (error: Exception) {
            zipFile.delete()
            throw error
        }
    }

    private fun buildManifest(data: MediaArchiveContent): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", data.createdAtMillis)
        put("owner", data.owner)
        put("photos", JSONArray().apply { data.photos.forEach { photo ->
            put(JSONObject().apply {
                put("journalId", photo.journalId); put("photoId", photo.photoId)
                put("orderIndex", photo.orderIndex); put("isCover", photo.isCover)
                put("size", photo.size); put("sha256", photo.sha256)
            })
        } })
        put("deletions", JSONArray().apply { data.deletions.forEach { item ->
            put(JSONObject().apply {
                put("journalId", item.journalId); put("photoId", item.photoId)
                put("deletedAtMillis", item.deletedAtMillis)
            })
        } })
    }

    private suspend fun inspectPlainArchive(
        file: File,
        requireSameOwner: Boolean = true
    ): MediaArchiveContent {
        check(file.isFile && file.length() in 1..MAX_ENCRYPTED_BYTES) { "ZIP boyutu geçersiz." }
        return ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            check(entries.size in 1..(MAX_PHOTOS + 1) && entries.none { it.isDirectory }) {
                "ZIP dosya sayısı güvenli sınırı aştı."
            }
            val names = entries.map { it.name }
            check(names.size == names.distinct().size && MANIFEST in names) { "ZIP girişleri geçersiz." }
            val manifestEntry = zip.getEntry(MANIFEST) ?: error("Manifest eksik")
            check(manifestEntry.size in 1..MAX_MANIFEST_BYTES.toLong()) { "Manifest boyutu geçersiz." }
            val bytes = zip.getInputStream(manifestEntry).use { input ->
                val memory = java.io.ByteArrayOutputStream()
                copyLimited(input, memory, (MAX_MANIFEST_BYTES + 1).toLong())
                check(memory.size() <= MAX_MANIFEST_BYTES) { "Manifest sınırı aşıldı." }
                memory.toByteArray()
            }
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            check(root.getString("format") == FORMAT && root.getInt("version") == VERSION) {
                "Bu dosya bir BD medya yedeği değil."
            }
            val owner = root.getString("owner")
            check(!requireSameOwner || owner == ownerTag()) {
                "Yedek başka hesap/çalışma alanına ait; aktarma durduruldu."
            }
            val createdAt = root.getLong("createdAt")
            check(createdAt > 0 && createdAt <= System.currentTimeMillis() + 86_400_000L) {
                "Yedek tarihi geçersiz."
            }
            val photoArray = root.getJSONArray("photos")
            check(photoArray.length() <= MAX_PHOTOS)
            val photos = (0 until photoArray.length()).map { i ->
                val row = photoArray.getJSONObject(i)
                MediaPhotoRecord(
                    journalId = row.getString("journalId"),
                    photoId = row.getString("photoId"),
                    orderIndex = row.getInt("orderIndex"),
                    isCover = row.getBoolean("isCover"),
                    size = row.getLong("size"),
                    sha256 = row.getString("sha256")
                ).also { p ->
                    check(JOURNAL_ID.matches(p.journalId) && PHOTO_ID.matches(p.photoId) &&
                            HASH.matches(p.sha256) && p.size in 1..JournalPhotoStore.MAX_FILE_SIZE_BYTES) {
                        "Medya kaydı geçersiz."
                    }
                }
            }
            val deletionsArray = root.getJSONArray("deletions")
            check(deletionsArray.length() <= MAX_DELETIONS) { "Silme defteri çok büyük." }
            val deletions = (0 until deletionsArray.length()).map { i ->
                val row = deletionsArray.getJSONObject(i)
                PublishedPhotoDeletion(row.getString("journalId"), row.getString("photoId"),
                    row.getLong("deletedAtMillis")).also { d ->
                    check(JOURNAL_ID.matches(d.journalId) && PHOTO_ID.matches(d.photoId) &&
                            d.deletedAtMillis > 0) { "Silme kaydı geçersiz." }
                }
            }
            check(deletions.distinctBy { it.journalId to it.photoId }.size == deletions.size) {
                "Aynı silme işareti tekrarlanıyor."
            }
            val grouped = photos.groupBy { it.journalId }
            grouped.values.forEach { group ->
                check(group.size <= JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL &&
                        group.count { it.isCover } <= 1 &&
                        group.map { it.orderIndex }.sorted() == (0 until group.size).toList() &&
                        group.map { it.photoId }.distinct().size == group.size) {
                    "Günlük fotoğrafları / sıralama verisi geçersiz."
                }
            }
            check(photos.sumOf { it.size } <= MAX_TOTAL_BYTES) { "Toplam arşiv boyutu aşılmış." }
            val known = photos.map { it.entryPath }.toSet()
            check(known.size == photos.size && names.toSet() == known + MANIFEST) {
                "ZIP içerisindeki fotoğraflar manifest ile eşleşmiyor."
            }
            // Bütün dosyaların boyut ve SHA-256'larını okunarak doğrula; ZIP bombası durdurulur.
            for (photo in photos) {
                currentCoroutineContext().ensureActive()
                val entry = zip.getEntry(photo.entryPath) ?: error("ZIP fotoğrafı eksik")
                check(entry.size == photo.size) { "Fotoğraf boyutu geçersiz." }
                val actual = zip.getInputStream(entry).use { input -> sha256OfStream(input, photo.size).also {
                    check(it.second == photo.size) { "Fotoğraf boyutu uyuşmuyor." }
                }.first }
                check(actual == photo.sha256) { "Fotoğraf bütünlüğü bozulmuş." }
            }
            MediaArchiveContent(createdAt, owner, photos, deletions)
        }
    }

    /** Geri yükleme planı yalnızca şu an mevcut olan Room anıları ile eşleşirse hazırlanır. */
    private suspend fun buildRestorePlan(archive: MediaArchiveContent): MediaRestorePlan {
        val journals = backupRepository.createBackupData().journals.map { it.id }.toSet()
        check(archive.photos.all { it.journalId in journals }) {
            "Yedekte olup bu cihazda olmayan anılar var. Önce .bdbackup içe aktar."
        }
        val deleted = (ledger.list() + archive.deletions)
            .map { it.journalId to it.photoId }.toSet()
        val toRestore = mutableListOf<MediaPhotoRecord>()
        val snapshot = mutableListOf<String>()
        val empty = mutableSetOf<String>()
        var already = 0
        var blocked = 0
        for ((journalId, items) in archive.photos.groupBy { it.journalId }.toSortedMap()) {
            val current = journalPhotoStore.list(journalId)
            val existing = current.associateBy { it.id }
            if (current.isEmpty()) empty += journalId
            snapshot += "$journalId:current=${current.joinToString(",") { it.id + ":" + it.isCover }}"
            var additions = 0
            for (item in items.sortedBy { it.photoId }) {
                val key = journalId to item.photoId
                val local = existing[item.photoId]
                if (key in deleted) {
                    // Silme işareti üstündür; eşleşen dosya mevcutsa bile ASLA üzerine yazma.
                    blocked++
                    snapshot += "${item.entryPath}:deleted"
                } else if (local != null) {
                    val file = File(local.absolutePath)
                    check(file.isFile && sha256OfFile(file) == item.sha256) {
                        "Aynı kimlikli farklı fotoğraf bulundu. Çakışma elle çözülmeli."
                    }
                    already++
                    snapshot += "${item.entryPath}:existing:${item.sha256}"
                } else {
                    additions++
                    toRestore += item
                    snapshot += "${item.entryPath}:missing"
                }
            }
            check(current.size + additions <= JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL) {
                "Günlükte fotoğraf sınırı aşılacak; içe alma durduruldu."
            }
        }
        // Kullanıcının mevcut silme defteri ve yedekteki silmeler onay sonrası birleştirilecek.
        val delFingerprint = deleted.sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("|") { "${it.first}:${it.second}" }
        snapshot += "deletions:$delFingerprint"
        return MediaRestorePlan(toRestore, already, blocked,
            digestOf(snapshot.joinToString("\n").toByteArray(Charsets.UTF_8)), empty)
    }

    private fun copyEncryptedSource(uri: Uri): File {
        val temp = temporary(".bdmedia")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Medya arşivi okunamadı.")
            input.use { source -> temp.outputStream().buffered().use { output ->
                copyLimited(source, output, MAX_ENCRYPTED_BYTES)
            } }
            check(temp.length() in 1..MAX_ENCRYPTED_BYTES) { "Şifreli medya arşivi boyutu geçersiz." }
            return temp
        } catch (e: Exception) { temp.delete(); throw e }
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, max: Long) {
        var count = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            if (n == 0) continue
            count += n
            check(count <= max) { "Dosya izin verilen boyutu aştı." }
            output.write(buffer, 0, n)
        }
    }

    private fun sha256OfFile(file: File): String = file.inputStream().buffered().use {
        sha256OfStream(it, MAX_ENCRYPTED_BYTES).first
    }

    private fun sha256OfStream(input: java.io.InputStream, max: Long): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            total += n
            check(total <= max) { "SHA doğrulama boyut sınırı aşıldı." }
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } to total
    }
}
