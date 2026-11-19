package com.loc.benimdunyam.data.sync.media

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** İndirme ve yükleme sonucunda yalnızca SAYILAR saklanır; fotoğraf/şifre UI state'e girmez. */
data class EncryptedMediaResult(val completed: Int, val skipped: Int, val inspected: Int)
data class EncryptedMediaLocalCount(val photos: Int, val seasonCovers: Int, val peopleImages: Int)

/** Bu fazda taşınabilen kalıcı medyalar; taslak fotoğrafları aktarılmaz. */
private data class MediaCandidate(
    val kind: String,
    val ownerId: String,
    val name: String,
    val path: String
)

/**
 * Faz 2G.1: Firestore'da sadece işaretçi, Firebase Storage'da şifreli dosya.
 * Kullanıcı onaylamadan hiçbir yükleme/indirme yapılmaz; yerel dosyalar asla ezilmez.
 * İşlem başına 3 medya sınırı ile eski cihazlarda RAM ve veri kullanımını sınırlıyoruz.
 */
@Singleton
class EncryptedMediaSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val journals: JournalDao,
    private val seasons: LifeSeasonDao,
    private val people: PersonDao,
    private val photoStore: JournalPhotoStore,
    private val codec: EncryptedMediaCodec,
    private val coordinator: BackupWriteCoordinator,
    private val deletions: EncryptedMediaDeletionRepository
) {
    private val mutex = Mutex()
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val prefs = context.getSharedPreferences("bd_encrypted_media_cursor_v1", Context.MODE_PRIVATE)
    private val allowedPhotoId = Regex("^[0-9]{13}_[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
    private val validImageName = Regex("^[A-Za-z0-9._-]{1,160}$")
    private val supportedKinds = setOf("JOURNAL_PHOTO", "SEASON_COVER", "PERSON_PROFILE", "PERSON_COVER")

    private fun checkAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Firebase hesabı veya aktif yerel arşiv uyuşmuyor."
        }
    }

    private fun mediaCollection(uid: String) = firestore.collection("users")
        .document(uid).collection("encrypted_media_entries")

    private fun mediaPath(uid: String, id: String) = "users/$uid/encrypted_media_v1/$id.bin"

    private fun cursorKey(uid: String) = "position_${codec.sha256(uid).take(24)}"

    /** Yalnızca mevcut ve kalıcı kayıtların medya dosyalarını aday yapar. */
    private suspend fun scanLocal(): List<MediaCandidate> {
        val result = mutableListOf<MediaCandidate>()
        journals.getAllJournalEntities().forEach { journal ->
            photoStore.list(journal.id).forEach { photo ->
                result += MediaCandidate("JOURNAL_PHOTO", journal.id, photo.id, photo.absolutePath)
            }
        }
        seasons.getAllSeasonEntities().forEach { season ->
            season.coverImagePath?.takeIf { it.isNotBlank() }?.let { path ->
                result += MediaCandidate("SEASON_COVER", season.id, File(path).name, path)
            }
        }
        people.getAllPersonEntities().forEach { person ->
            person.profileImagePath?.takeIf { it.isNotBlank() }?.let { path ->
                result += MediaCandidate("PERSON_PROFILE", person.id, File(path).name, path)
            }
            person.coverImagePath?.takeIf { it.isNotBlank() }?.let { path ->
                result += MediaCandidate("PERSON_COVER", person.id, File(path).name, path)
            }
        }
        return result.sortedWith(compareBy({ it.kind }, { it.ownerId }, { it.name }))
    }

    suspend fun localCount(uid: String): EncryptedMediaLocalCount = withContext(Dispatchers.IO) {
        checkAccount(uid)
        val sources = scanLocal()
        EncryptedMediaLocalCount(
            photos = sources.count { it.kind == "JOURNAL_PHOTO" },
            seasonCovers = sources.count { it.kind == "SEASON_COVER" },
            peopleImages = sources.count { it.kind.startsWith("PERSON_") }
        )
    }

    /** Eski content:// ve çalışma alanı dışı dosyaları güvenle atlarız. */
    private fun verifiedLocalFile(candidate: MediaCandidate): File? {
        if (candidate.name.isBlank() || !validImageName.matches(candidate.name)) return null
        if (candidate.path.startsWith("content://") || candidate.path.startsWith("file://")) return null
        val root = session.mediaRoot().canonicalFile
        val scope = when (candidate.kind) {
            "JOURNAL_PHOTO" -> File(root, "benim_dunyam_journal_photos")
            "SEASON_COVER" -> File(root, "seasons")
            "PERSON_PROFILE", "PERSON_COVER" -> File(root, "people")
            else -> return null
        }.canonicalFile
        val file = File(candidate.path).canonicalFile
        if (!file.path.startsWith(scope.path + File.separator)) return null
        if (candidate.kind == "JOURNAL_PHOTO" && !allowedPhotoId.matches(candidate.name)) return null
        if (!file.isFile || file.length() !in 1..EncryptedMediaCodec.MAX_PLAIN_BYTES) return null
        return file
    }

    /** Uzantıya değil gerçek imzaya bakarız; yalnızca Android'de çözümlenebilir resimler gönderilir. */
    private fun detectMime(file: File): String? {
        val bytes = ByteArray(12)
        val read = file.inputStream().use { it.read(bytes) }
        if (read < 12) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                    bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
            else -> null
        }
    }

    /**
     * Faz 2G.2: Otomatik eşitleme iznini açmadan önce bulutta VARSA bir
     * gerçek medya nesnesinin AES-GCM etiketi ve kimliğini kontrol ederiz.
     * Örnek yoksa false döner; "doğrulandı" iddiası üretilmez.
     */
    suspend fun verifyCloudSample(uid: String, password: CharArray): Boolean =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            checkAccount(uid)
            require(password.size in 12..128)
            val first = mediaCollection(uid).limit(1).get(Source.SERVER).await()
                .documents.firstOrNull() ?: return@withContext false
            val path = mediaPath(uid, first.id)
            check(first.id.matches(Regex("[0-9a-f]{64}")) &&
                    first.getString("ownerUid") == uid &&
                    first.getLong("formatVersion") == 1L &&
                    first.getString("storagePath") == path) {
                "Bulut medya manifesti hesap doğrulamasından geçmedi."
            }
            val temporary = File.createTempFile("bd_media_sample_", ".part", context.cacheDir)
            try {
                val ref = storage.reference.child(path)
                val meta = ref.metadata.await()
                require(meta.sizeBytes in 49..EncryptedMediaCodec.MAX_ENCRYPTED_BYTES)
                ref.getFile(temporary).await()
                checkAccount(uid)
                val opened = codec.decrypt(temporary, password, uid)
                try {
                    val info = opened.info
                    check(codec.sha256("$uid|${info.kind}|${info.ownerId}|${info.fileName}|${info.plainSha256}") == first.id &&
                            codec.sha256("$uid|${info.kind}|${info.ownerId}") == first.getString("ownerHash") &&
                            codec.sha256("$uid|${info.ownerId}|${info.fileName}") == first.getString("fileNameHash")) {
                        "Bulut medya örneğinin kimliği doğrulanamadı."
                    }
                } finally { opened.file.delete() }
            } finally { temporary.delete() }
            true
        }

    /** Kapsam doğrulamasının dışında tek bir dosyayı bile Firebase'e göndermiyoruz. */
    suspend fun uploadNext(uid: String, password: CharArray, maxInspected: Int = Int.MAX_VALUE): EncryptedMediaResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            check(password.size in 12..128)
            checkAccount(uid)
            val candidates = scanLocal()
            if (candidates.isEmpty()) return@withContext EncryptedMediaResult(0, 0, 0)
            var completed = 0
            var skipped = 0
            var inspected = 0
            var index = prefs.getInt(cursorKey(uid), 0).coerceIn(0, candidates.size)
            while (index < candidates.size && completed < 3 && inspected < maxInspected.coerceAtLeast(1)) {
                checkAccount(uid)
                val item = candidates[index]
                // Ağ hatası durumunda imleç ilerlemez. Tekrar denemede aynı kimlik doğrulanır.
                val file = verifiedLocalFile(item)
                val mime = file?.let(::detectMime)
                // Faz 2G.3: Diğer cihazda silinen bir fotoğrafın eski kopyası
                // bu telefondan yeniden buluta gönderilmesin.
                val deletionMarked = item.kind == "JOURNAL_PHOTO" &&
                        deletions.isDeleted(uid, password, item.ownerId, item.name)
                if (file == null || mime == null || deletionMarked) {
                    skipped++
                } else {
                    val fingerprint = codec.sha256(file)
                    val id = codec.sha256("$uid|${item.kind}|${item.ownerId}|${item.name}|$fingerprint")
                    val objectPath = mediaPath(uid, id)
                    val doc = mediaCollection(uid).document(id)
                    val existing = doc.get(Source.SERVER).await()
                    checkAccount(uid)
                    if (existing.exists()) {
                        check(existing.getString("ownerUid") == uid &&
                                existing.getString("storagePath") == objectPath &&
                                existing.getLong("formatVersion") == 1L) {
                            "Bulutta aynı medya kimliği için farklı belge bulundu."
                        }
                        skipped++
                    } else {
                        val meta = EncryptedMediaCodec.MediaInfo(
                            ownerUid = uid,
                            kind = item.kind,
                            ownerId = item.ownerId,
                            fileName = item.name,
                            mimeType = mime,
                            plainSize = file.length(),
                            plainSha256 = fingerprint
                        )
                        val encrypted = codec.encrypt(file, meta, password)
                        try {
                            // Kaynak şifreleme esnasında değiştiyse sunucuya HİÇBİR ŞEY gönderme.
                            check(codec.sha256(file) == fingerprint) {
                                "Fotoğraf şifrelenirken değişti; yükleme iptal edildi."
                            }
                            val ref = storage.reference.child(objectPath)
                            val exists = try {
                                ref.metadata.await()
                                true
                            } catch (error: StorageException) {
                                if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) false
                                else throw error
                            }
                            checkAccount(uid)
                            if (!exists) {
                                ref.putFile(Uri.fromFile(encrypted), StorageMetadata.Builder()
                                    .setContentType("application/octet-stream").build()).await()
                            } else {
                                // Önceki yarım denemeden nesne kaldıysa parolayı ve
                                // kimliği doğrulamadan eksik Firestore manifesti tamamlamayız.
                                val previous = File.createTempFile("bd_retry_cipher_", ".part", context.cacheDir)
                                try {
                                    ref.getFile(previous).await()
                                    val opened = codec.decrypt(previous, password, uid)
                                    try {
                                        check(opened.info == meta) {
                                            "Önceki bulut görseli farklı; üzerine yazılmadı."
                                        }
                                    } finally { opened.file.delete() }
                                } finally { previous.delete() }
                            }
                            // Dosya değişmişse yeni kimliğe ait içerik doğrulanamaz; manifest oluşturmayız.
                            check(codec.sha256(file) == fingerprint) {
                                "Fotoğraf gönderilirken değişti; kayıt tamamlanmadı."
                            }
                            checkAccount(uid)
                            val recorded = mapOf(
                                "formatVersion" to 1,
                                "ownerUid" to uid,
                                "storagePath" to objectPath,
                                "kind" to item.kind,
                                "ownerHash" to codec.sha256("$uid|${item.kind}|${item.ownerId}"),
                                "fileNameHash" to codec.sha256("$uid|${item.ownerId}|${item.name}"),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            firestore.runTransaction { tx ->
                                val snapshot = tx.get(doc)
                                if (!snapshot.exists()) tx.set(doc, recorded)
                                else check(snapshot.getString("storagePath") == objectPath &&
                                        snapshot.getString("ownerUid") == uid) {
                                    "Yükleme sırasında belge kimliği değişti."
                                }
                                null
                            }.await()
                            completed++
                        } finally {
                            encrypted.delete()
                        }
                    }
                }
                index++
                inspected++
                check(prefs.edit().putInt(cursorKey(uid), index).commit()) {
                    "Yükleme ilerlemesi saklanamadı."
                }
            }
            if (index >= candidates.size) {
                // Yeniden başlatılan tarama yeni fotoğrafları da görür; sunucudakiler atlanır.
                prefs.edit().putInt(cursorKey(uid), 0).commit()
            }
            EncryptedMediaResult(completed, skipped, inspected)
        }
    }

    /** Buluttaki kaydın yerel karşılığı varsa ve HENÜZ görseli yoksa kabul edilir. */
    suspend fun restoreNext(uid: String, password: CharArray): EncryptedMediaResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            require(password.size in 12..128)
            checkAccount(uid)
            val journalIds = journals.getAllJournalEntities().map { it.id }.toSet()
            val seasonRows = seasons.getAllSeasonEntities().associateBy { it.id }
            val peopleRows = people.getAllPersonEntities().associateBy { it.id }
            val owners = mutableMapOf<Pair<String, String>, String>()
            journalIds.forEach { owners["JOURNAL_PHOTO" to codec.sha256("$uid|JOURNAL_PHOTO|$it")] = it }
            seasonRows.keys.forEach { owners["SEASON_COVER" to codec.sha256("$uid|SEASON_COVER|$it")] = it }
            peopleRows.keys.forEach { id ->
                owners["PERSON_PROFILE" to codec.sha256("$uid|PERSON_PROFILE|$id")] = id
                owners["PERSON_COVER" to codec.sha256("$uid|PERSON_COVER|$id")] = id
            }
            var query: Query = mediaCollection(uid).orderBy("createdAt", Query.Direction.ASCENDING).limit(50)
            var seen = 0
            var completed = 0
            var skipped = 0
            while (completed < 3) {
                checkAccount(uid)
                val page = query.get(Source.SERVER).await()
                if (page.isEmpty) break
                for (doc in page.documents) {
                    checkAccount(uid)
                    seen++
                    require(seen <= 2000) { "2.000 medya sınırı aşıldı; toplu içe aktarma durduruldu." }
                    val kind = doc.getString("kind").orEmpty()
                    val ownerHash = doc.getString("ownerHash").orEmpty()
                    val owner = owners[kind to ownerHash]
                    val path = mediaPath(uid, doc.id)
                    if (owner == null || kind !in supportedKinds) {
                        skipped++
                        continue
                    }
                    check(doc.getLong("formatVersion") == 1L &&
                            doc.getString("ownerUid") == uid &&
                            doc.getString("storagePath") == path &&
                            doc.id.matches(Regex("[0-9a-f]{64}"))) {
                        "Bulut medya üstbilgisi güvenlik kontrolünden geçmedi."
                    }
                    // Mevcut kişi/dönem görselleri asla değiştirilmeyecek.
                    val hasCover = when (kind) {
                        "SEASON_COVER" -> !seasonRows[owner]?.coverImagePath.isNullOrBlank()
                        "PERSON_PROFILE" -> !peopleRows[owner]?.profileImagePath.isNullOrBlank()
                        "PERSON_COVER" -> !peopleRows[owner]?.coverImagePath.isNullOrBlank()
                        else -> false
                    }
                    if (hasCover) {
                        skipped++
                        continue
                    }
                    // Gerekli olabilecek eski anı fotoğraflarını taramadan indirebiliriz.
                    if (kind == "JOURNAL_PHOTO") {
                        val names = photoStore.list(owner).map { it.id }
                        if (names.any { codec.sha256("$uid|$owner|$it") == doc.getString("fileNameHash") }) {
                            skipped++
                            continue
                        }
                    }
                    val staged = File.createTempFile("bd_cloud_cipher_", ".part", context.cacheDir)
                    try {
                        val storageRef = storage.reference.child(path)
                        val properties = storageRef.metadata.await()
                        require(properties.sizeBytes in 1..EncryptedMediaCodec.MAX_ENCRYPTED_BYTES) {
                            "Bulut görselinin dosya boyutu sınırı aşıldı."
                        }
                        storageRef.getFile(staged).await()
                        checkAccount(uid)
                        val decoded = codec.decrypt(staged, password, uid)
                        try {
                            val info = decoded.info
                            check(info.kind == kind && info.ownerId == owner &&
                                    codec.sha256("$uid|${info.kind}|${info.ownerId}|${info.fileName}|${info.plainSha256}") == doc.id &&
                                    codec.sha256("$uid|$owner|${info.fileName}") == doc.getString("fileNameHash")) {
                                "Bulut belgesi ile şifreli fotoğraf kimliği eşleşmiyor."
                            }
                            // Faz 2G.3: Kullanıcının sildiği fotoğrafı yeniden
                            // oluşturmayız. Silme işareti şifreli ve sunucudan doğrulanır.
                            val wasDeleted = info.kind == "JOURNAL_PHOTO" &&
                                    deletions.isDeleted(uid, password, owner, info.fileName)
                            if (wasDeleted) skipped++
                            else {
                                val written = safelyRestore(info, decoded.file, owner)
                                if (written) completed++ else skipped++
                            }
                        } finally {
                            decoded.file.delete()
                        }
                    } finally {
                        staged.delete()
                    }
                    if (completed >= 3) break
                }
                if (page.size() < 50) break
                query = mediaCollection(uid).orderBy("createdAt", Query.Direction.ASCENDING)
                    .startAfter(page.documents.last()).limit(50)
            }
            EncryptedMediaResult(completed, skipped, seen)
        }
    }

    private suspend fun safelyRestore(
        info: EncryptedMediaCodec.MediaInfo, source: File, ownerId: String
    ): Boolean = coordinator.withRegularWrite {
        val extension = when (info.mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("Desteklenmeyen fotoğraf türü.")
        }
        when (info.kind) {
            "JOURNAL_PHOTO" -> {
                if (journals.getJournalById(ownerId) == null) false
                else if (!allowedPhotoId.matches(info.fileName) ||
                    !info.fileName.endsWith(".$extension", ignoreCase = true)) false
                else photoStore.restoreMissingFromCloud(ownerId, info.fileName, source)
            }
            "SEASON_COVER" -> {
                val row = seasons.getSeasonById(ownerId)
                if (row == null || !row.coverImagePath.isNullOrBlank()) false
                else {
                    val dest = safeNewCoverFile("seasons", ownerId, extension, source)
                    try {
                        if (seasons.setCoverImageIfEmpty(ownerId, dest.absolutePath) == 1) true
                        else { dest.delete(); false }
                    } catch (error: Exception) { dest.delete(); throw error }
                }
            }
            "PERSON_PROFILE", "PERSON_COVER" -> {
                val row = people.getPersonById(ownerId)
                val existing = if (info.kind == "PERSON_PROFILE") row?.profileImagePath else row?.coverImagePath
                if (row == null || !existing.isNullOrBlank()) false
                else {
                    val dest = safeNewCoverFile("people", ownerId, extension, source)
                    try {
                        val modified = if (info.kind == "PERSON_PROFILE")
                            people.setProfileImageIfEmpty(ownerId, dest.absolutePath)
                        else people.setCoverImageIfEmpty(ownerId, dest.absolutePath)
                        if (modified == 1) true else { dest.delete(); false }
                    } catch (error: Exception) { dest.delete(); throw error }
                }
            }
            else -> false
        }
    }

    /** Yeni dosya benzersiz ad alır; var olan kapak veya kişi görseli üzerine yazılmaz. */
    private fun safeNewCoverFile(kind: String, ownerId: String, ext: String, source: File): File {
        val root = session.mediaRoot()
        val dir = if (kind == "seasons") File(root, "seasons")
        else File(File(root, "people"), ownerId)
        require(ownerId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
            "Güvenli olmayan kişi/dönem kimliği."
        }
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Medya dizini oluşturulamadı.")
        val dest = File(dir, "cloud_${UUID.randomUUID()}.$ext")
        val partial = File(dir, dest.name + ".part")
        try {
            source.inputStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output, 32 * 1024) }
            }
            if (!partial.renameTo(dest)) throw IOException("Görsel kalıcı klasöre taşınamadı.")
            return dest
        } finally { partial.delete() }
    }
}
