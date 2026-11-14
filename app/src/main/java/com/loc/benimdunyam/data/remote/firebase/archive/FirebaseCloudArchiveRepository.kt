package com.loc.benimdunyam.data.remote.firebase.archive

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.backup.BackupValidator
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.model.CloudArchiveSummary
import com.loc.benimdunyam.domain.model.CloudRestorePreview
import com.loc.benimdunyam.domain.model.CloudLocalCoverage
import com.loc.benimdunyam.domain.model.CloudSyncAuditReport
import com.loc.benimdunyam.domain.model.CloudRestoreResult
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.repository.BackupRepository
import com.loc.benimdunyam.domain.repository.CloudArchiveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Bilerek salt KOPYA oluşturur: Room'a asla yazmaz, hiçbir uzak kaydı silmez,
 * otomatik senkronizasyon yapmaz. İlk yükleme seçilmiş hesabın UID'sine bağlanır.
 */
class FirebaseCloudArchiveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val backupRepository: BackupRepository,
    private val moodRepository: JournalMoodRepository,
    private val validator: BackupValidator,
    private val importer: CloudArchiveLocalImporter,
    private val photoStore: JournalPhotoStore
) : CloudArchiveRepository {

    private val gson = Gson()
    private val preferences by lazy {
        context.getSharedPreferences("benimdunyam_cloud_archive_owner_v1", Context.MODE_PRIVATE)
    }

    companion object {
        private const val CHUNK_CHARS = 100_000
        private const val MAX_ENCODED_CHARS = 12_000_000
        private const val OWNER_KEY = "archive_owner_uid"
        private const val FORMAT_VERSION = 1L
    }

    /**
     * Bulut kotası veya kullanıcı hesabı gerektirmeyen yerel kapsam denetimi.
     * Özellikle henüz buluta yedeklenmeyen anı fotoğraflarını görünür kılar.
     */
    override suspend fun inspectLocalCoverage(): CloudLocalCoverage {
        val snapshot = backupRepository.createBackupData()
        val journalIds = snapshot.journals.map { it.id }.toSet()
        val moods = moodRepository.observeAll().first().filter { it.journalId in journalIds }
        return buildLocalCoverage(snapshot, moods)
    }

    /**
     * Kaynak yalnızca Firestore SERVER'dır. Bulut arşivi önceden kullanılan
     * şifreyle açılır, Room ile karşılaştırılır; hiçbir kayıt yazılmaz veya silinmez.
     */
    override suspend fun compareLatestWithLocal(passphrase: CharArray): CloudSyncAuditReport {
        val uid = requireUid()
        ensureAccountCanUpload(uid) // Farklı hesaba bağlı yerel veri karıştırılamaz.
        require(passphrase.size in 12..128) { "Arşiv şifresi 12–128 karakter olmalı." }
        val remote = readAndValidate(uid, latestArchiveDocument(uid), passphrase)
        val localRaw = backupRepository.createBackupData()
        val journalIds = localRaw.journals.map { it.id }.toSet()
        val localMoods = moodRepository.observeAll().first().filter { it.journalId in journalIds }
        val localCoverage = buildLocalCoverage(localRaw, localMoods)
        val local = localRaw.removeDeviceOnlyImagePaths()
        val cloud = remote.archive.removeDeviceOnlyImagePaths()

        // Fotoğraf yolları cihazdan cihaza geçerli değildir; fark analizine katılmaz.
        // Ruh halinde yalnızca seçilen duygu karşılaştırılır; tarih damgası dikkate alınmaz.
        val result = withContext(Dispatchers.Default) {
            CloudSyncAuditReport(
                archive = remote.summary,
                local = localCoverage,
                journals = CloudRecordDiffCalculator.calculate(local.journals, cloud.journals, { it.id }),
                people = CloudRecordDiffCalculator.calculate(local.people, cloud.people, { it.id }),
                seasons = CloudRecordDiffCalculator.calculate(local.seasons, cloud.seasons, { it.id }),
                episodes = CloudRecordDiffCalculator.calculate(local.episodes, cloud.episodes, { it.id }),
                mentions = CloudRecordDiffCalculator.calculate(
                    local.mentions, cloud.mentions, { it.journalId to it.personId }
                ),
                specialMemories = CloudRecordDiffCalculator.calculate(
                    local.specialMemories, cloud.specialMemories, { it.journalId to it.personId }
                ),
                moods = CloudRecordDiffCalculator.calculate(
                    localMoods, remote.moods, { it.journalId }, { it.moodName }
                ),
                comparedAtMillis = System.currentTimeMillis()
            )
        }
        checkUid(uid)
        return result
    }

    /**
     * Fotoğraf dosyalarını kopyalamadan boyutlarını hesaplar. Draft fotoğraflar
     * henüz kalıcı anıya bağlı olmadığı için rapora dahil edilmez.
     */
    private suspend fun buildLocalCoverage(
        snapshot: BackupData,
        moods: List<JournalMoodRecord>
    ): CloudLocalCoverage {
        var photoCount = 0
        var photoBytes = 0L
        for (journal in snapshot.journals) {
            for (photo in photoStore.list(journal.id)) {
                val length = withContext(Dispatchers.IO) {
                    File(photo.absolutePath).takeIf { it.isFile }?.length() ?: 0L
                }
                photoCount++
                photoBytes += length
            }
        }
        return CloudLocalCoverage(
            journalCount = snapshot.journals.size,
            peopleCount = snapshot.people.size,
            seasonCount = snapshot.seasons.size,
            episodeCount = snapshot.episodes.size,
            mentionCount = snapshot.mentions.size,
            specialMemoryCount = snapshot.specialMemories.size,
            moodCount = moods.size,
            journalPhotoCount = photoCount,
            journalPhotoBytes = photoBytes,
            personImageReferenceCount = snapshot.people.sumOf {
                listOf(it.profileImagePath, it.coverImagePath).count { path -> !path.isNullOrBlank() }
            },
            seasonImageReferenceCount = snapshot.seasons.count { !it.coverImagePath.isNullOrBlank() },
            inspectedAtMillis = System.currentTimeMillis()
        )
    }

    override suspend fun uploadEncrypted(passphrase: CharArray): CloudArchiveSummary {
        val uid = requireUid()
        require(passphrase.size in 12..128) { "Arşiv şifresi 12–128 karakter arasında olmalı." }
        ensureAccountCanUpload(uid)

        // Var olan .bdbackup API'sinden yalnızca veriyi OKUR; .bdbackup biçimi değişmez.
        val archive = backupRepository.createBackupData().removeDeviceOnlyImagePaths()
        val journalIds = archive.journals.map { it.id }.toSet()
        val moods = moodRepository.observeAll().first().filter { it.journalId in journalIds }
        val payload = CloudArchivePayload(archive = archive, moods = moods)
        val json = gson.toJson(payload)
        validator.parseAndValidate(gson.toJson(archive))

        val encrypted = withContext(Dispatchers.Default) {
            CloudArchiveCrypto.encrypt(json, passphrase)
        }
        val encoded = withContext(Dispatchers.Default) {
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }
        require(encoded.length <= MAX_ENCODED_CHARS) {
            "Arşiv bu ilk sürümün boyut sınırını aşıyor. Veri kaybını önlemek için işlem iptal edildi."
        }
        val checksum = sha256(encrypted)
        val chunks = encoded.chunked(CHUNK_CHARS)
        require(chunks.isNotEmpty()) { "Oluşturulan arşiv boş." }

        checkUid(uid)
        // İlk bilinçli aktarımda cihazın bulut kopyasını tek UID'ye bağlarız.
        // Bu yalnızca istemci tarafında ek bir korumadır; asıl izolasyonu Firestore Rules sağlar.
        withContext(Dispatchers.IO) {
            if (preferences.getString(OWNER_KEY, null) == null) {
                check(preferences.edit().putString(OWNER_KEY, uid).commit()) {
                    "Hesap bağlantısı yerel olarak kaydedilemedi. Aktarım durduruldu."
                }
            }
        }
        val ref = archiveCollection(uid).document(UUID.randomUUID().toString())

        // Parçalar önce yazılır. Manifest en son yazıldığından yarım aktarım görünmez.
        chunks.withIndex().chunked(10).forEach { group ->
            checkUid(uid)
            val batch = firestore.batch()
            group.forEach { (index, value) ->
                batch.set(ref.collection("parts").document(partId(index)), mapOf(
                    "formatVersion" to FORMAT_VERSION,
                    "index" to index.toLong(),
                    "data" to value
                ))
            }
            batch.commit().await()
        }
        checkUid(uid)
        ref.set(mapOf(
            "formatVersion" to FORMAT_VERSION,
            "createdAt" to FieldValue.serverTimestamp(),
            "partCount" to chunks.size.toLong(),
            "encryptedBytes" to encrypted.size.toLong(),
            "sha256" to checksum,
            "journalCount" to archive.journals.size.toLong(),
            "peopleCount" to archive.people.size.toLong(),
            "seasonCount" to archive.seasons.size.toLong(),
            "episodeCount" to archive.episodes.size.toLong(),
            "mentionCount" to archive.mentions.size.toLong(),
            "specialMemoryCount" to archive.specialMemories.size.toLong(),
            "moodCount" to moods.size.toLong()
        )).await()
        // Sunucudaki belgeyi tekrar okuyarak gerçekten tamamlandığını doğrula.
        return toSummary(ref.get(Source.SERVER).await())
    }

    override suspend fun latestArchive(): CloudArchiveSummary? {
        val uid = requireUid()
        val documents = archiveCollection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get(Source.SERVER).await().documents
        checkUid(uid)
        return documents.firstOrNull()?.let(::toSummary)
    }

    override suspend fun verifyLatestArchive(passphrase: CharArray): CloudArchiveSummary {
        val uid = requireUid()
        val doc = latestArchiveDocument(uid)
        val verified = readAndValidate(uid, doc, passphrase)
        return verified.summary
    }

    override suspend fun previewLatestRestore(passphrase: CharArray): CloudRestorePreview {
        val uid = requireUid()
        val doc = latestArchiveDocument(uid)
        val verified = readAndValidate(uid, doc, passphrase)
        val localCount = importer.localRecordCount()
        val owner = preferences.getString(OWNER_KEY, null)
        val blockedReason = when {
            owner != null && owner != uid ->
                "Bu cihaz daha önce farklı bir bulut hesabına bağlanmış. Hesaplar birleştirilmez."
            localCount > 0 ->
                "Cihazda $localCount kayıt var. Bu sürüm yalnızca boş cihaza güvenli içe aktarmayı destekliyor."
            else -> null
        }
        checkUid(uid)
        return CloudRestorePreview(
            archive = verified.summary,
            encryptedSha256 = verified.sha256,
            localRecordCount = localCount,
            canImport = blockedReason == null,
            blockedReason = blockedReason
        )
    }

    override suspend fun importPreviewedArchive(
        preview: CloudRestorePreview,
        passphrase: CharArray
    ): CloudRestoreResult {
        val uid = requireUid()
        require(preview.canImport) { "İçe aktarma önizlemesi güvenli değil." }
        ensureAccountCanUpload(uid) // Prevent cross-account local data mixing.
        val doc = archiveCollection(uid).document(preview.archive.id).get(Source.SERVER).await()
        val verified = readAndValidate(uid, doc, passphrase)
        require(verified.summary == preview.archive && verified.sha256 == preview.encryptedSha256) {
            "Bulut arşivi önizlemeden sonra değişmiş. Yeniden önizleme yap."
        }
        require(importer.localRecordCount() == 0) {
            "Cihazda kayıtlar oluşmuş. İçe aktarmadan önce verileri koru."
        }
        checkUid(uid)
        // Bind the local archive to one Firebase user BEFORE staging import rows.
        // If a restore fails, conservatively keep the binding instead of mixing accounts.
        withContext(Dispatchers.IO) {
            if (preferences.getString(OWNER_KEY, null) == null) {
                check(preferences.edit().putString(OWNER_KEY, uid).commit()) {
                    "Bulut hesap bağlantısı kaydedilemedi. İçe aktarma durduruldu."
                }
            }
        }
        return importer.importIntoEmptyDatabase(
            archive = verified.archive.removeDeviceOnlyImagePaths(),
            moods = verified.moods,
            checkOwner = { checkUid(uid) }
        )
    }

    private data class VerifiedCloudArchive(
        val summary: CloudArchiveSummary,
        val sha256: String,
        val archive: BackupData,
        val moods: List<JournalMoodRecord>
    )

    private suspend fun latestArchiveDocument(uid: String): DocumentSnapshot =
        archiveCollection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get(Source.SERVER).await().documents.firstOrNull()
            ?: error("Bulutta tamamlanmış bir arşiv bulunamadı.")

    /** Her önizleme ve içe aktarma, parçaları önbellekten değil sunucudan alır. */
    private suspend fun readAndValidate(
        uid: String,
        doc: DocumentSnapshot,
        passphrase: CharArray
    ): VerifiedCloudArchive {
        require(passphrase.size in 12..128) { "Arşiv şifresi 12–128 karakter olmalı." }
        checkUid(uid)
        val summary = toSummary(doc)
        val count = (doc.getLong("partCount") ?: error("Parça sayısı eksik.")).toInt()
        require(count in 1..120) { "Geçersiz arşiv parça sayısı." }
        val builder = StringBuilder(count * CHUNK_CHARS)
        repeat(count) { index ->
            checkUid(uid)
            val part = doc.reference.collection("parts").document(partId(index))
                .get(Source.SERVER).await()
            require(part.exists() && part.getLong("index") == index.toLong()) {
                "Bulut arşivinde eksik veya yanlış bir bölüm var."
            }
            builder.append(part.getString("data") ?: error("Arşiv bölümü okunamadı."))
            require(builder.length <= MAX_ENCODED_CHARS) { "Arşiv boyut sınırı aşıldı." }
        }
        val encrypted = Base64.decode(builder.toString(), Base64.NO_WRAP)
        require(encrypted.size.toLong() == doc.getLong("encryptedBytes")) {
            "Bulut arşivinin uzunluğu uyuşmuyor."
        }
        val checksum = sha256(encrypted)
        require(checksum == doc.getString("sha256")) {
            "Bulut arşivinin bütünlük kontrolü başarısız."
        }
        val json = withContext(Dispatchers.Default) {
            CloudArchiveCrypto.decrypt(encrypted, passphrase)
        }
        val verified = withContext(Dispatchers.Default) {
            val payload = gson.fromJson(json, CloudArchivePayload::class.java)
                ?: error("Bulut arşivi çözümlenemedi.")
            require(payload.formatVersion == FORMAT_VERSION.toInt()) {
                "Desteklenmeyen bulut arşiv sürümü."
            }
            val archive = validator.parseAndValidate(gson.toJson(payload.archive))
            val moods = requireNotNull(payload.moods) { "Ruh hali listesi eksik." }
            val journalIds = archive.journals.map { it.id }.toSet()
            val validMoodNames = JournalMood.values().map { it.name }.toSet()
            require(moods.map { it.journalId }.distinct().size == moods.size &&
                    moods.all { it.journalId in journalIds && it.moodName in validMoodNames && it.updatedAt > 0L }
            ) { "Ruh hali kayıtlarında geçersiz ya da tekrarlanan bilgi var." }
            require(archive.journals.size == summary.journalCount &&
                    archive.people.size == summary.peopleCount &&
                    archive.seasons.size == summary.seasonCount &&
                    archive.episodes.size == summary.episodeCount &&
                    archive.mentions.size == summary.mentionCount &&
                    archive.specialMemories.size == summary.specialMemoryCount &&
                    moods.size == summary.moodCount
            ) { "Bulut arşivindeki kayıt sayıları uyuşmuyor." }
            VerifiedCloudArchive(summary, checksum, archive, moods)
        }
        checkUid(uid)
        return verified
    }

    private fun requireUid(): String = auth.currentUser?.uid
        ?: error("Önce bulut hesabına giriş yapmalısın.")

    private fun checkUid(uid: String) {
        check(auth.currentUser?.uid == uid) {
            "Bulut hesabı işlem sırasında değişti. İşlem güvenlik için durduruldu."
        }
    }

    private fun ensureAccountCanUpload(uid: String) {
        val owner = preferences.getString(OWNER_KEY, null)
        check(owner == null || owner == uid) {
            "Bu cihazdaki arşiv başka bir bulut hesabına bağlanmış. " +
                    "Hesapların verilerini karıştırmamak için yükleme engellendi."
        }
    }

    private fun archiveCollection(uid: String) = firestore.collection("users")
        .document(uid).collection("cloud_archives")

    private fun partId(index: Int) = "part_${index.toString().padStart(4, '0')}"

    private fun toSummary(doc: DocumentSnapshot): CloudArchiveSummary {
        require(doc.exists() && doc.getLong("formatVersion") == FORMAT_VERSION) {
            "Desteklenmeyen veya eksik bulut arşiv kaydı."
        }
        fun count(name: String) = (doc.getLong(name) ?: error("Eksik alan: $name")).toInt()
        return CloudArchiveSummary(
            id = doc.id,
            createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time
                ?: error("Bulut arşivi tarihi eksik."),
            journalCount = count("journalCount"),
            peopleCount = count("peopleCount"),
            seasonCount = count("seasonCount"),
            episodeCount = count("episodeCount"),
            mentionCount = count("mentionCount"),
            specialMemoryCount = count("specialMemoryCount"),
            moodCount = count("moodCount")
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun BackupData.removeDeviceOnlyImagePaths(): BackupData = copy(
        seasons = seasons.map { it.copy(coverImagePath = null) },
        people = people.map { it.copy(profileImagePath = null, coverImagePath = null) }
    )
}

/** Bulut biçimi .bdbackup'tan bilinçli olarak ayrı tutulur. */
private data class CloudArchivePayload(
    val formatVersion: Int = 1,
    val archive: BackupData,
    val moods: List<JournalMoodRecord>
)
