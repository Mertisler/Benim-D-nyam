package com.loc.benimdunyam.data.sync.remote

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.loc.benimdunyam.data.backup.BackupValidator
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.repository.BackupRepository
import com.loc.benimdunyam.domain.sync.FullSyncCoveragePolicy
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.RemoteJournalEventPolicy
import com.loc.benimdunyam.domain.sync.SyncRemoteFact
import com.loc.benimdunyam.domain.sync.SyncRelationIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Her iki işlem de SALT OKUNUR veya yalnızca yeni şifreli katalog OLUŞTURUR. */
data class FullSyncCoverageReport(
    val totalEvents: Int,
    val uniqueJournals: Int,
    val catalogJournals: Int,
    val catalogPeople: Int,
    val catalogSeasons: Int,
    val catalogEpisodes: Int,
    val catalogMoods: Int,
    val missingRelationEvents: Int,
    val staleCatalogEvents: Int,
    val localPendingEvents: Int,
    val localConflictEvents: Int,
    val remoteDeleteLocalExists: Int,
    val remotelyOnlyEvents: Int
)

/**
 * Faz 2F.5: Tam olay taraması + şifreli ilişki kataloğu + güvenli kapsam raporu.
 *
 * YAPMADIKLARI:
 * - Anıları veya ilişkileri Room'a yazmaz.
 * - Otomatik birleştirme, silme veya senkronizasyon başlatmaz.
 * - Şifreyi hiçbir depoya/Worker'a kaydetmez.
 *
 * Sınırlar:
 * - Sunucudan en fazla MAX_EVENTS olay taranır; fazlası varsa KISMİ başarı dönmez.
 * - Katalog şifreli parçalara ayrılır, manifest EN SON oluşturulur.
 * - Fotoğraflar bu kataloğa dahil edilmez.
 */
@Singleton
class SyncDependencyCoverageRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val workspace: ActiveWorkspaceSession,
    private val backupRepository: BackupRepository,
    private val backupValidator: BackupValidator,
    private val moodRepository: JournalMoodRepository,
    private val journalDao: JournalDao,
    private val eventDao: JournalSyncEventDao
) {
    private val mutex = Mutex()
    private val gson = Gson()

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                workspace.isReadyFor(uid) && !workspace.needsRestart()) {
            "Bulut hesabı ile etkin yerel arşiv eşleşmiyor."
        }
    }

    private fun catalogCollection(uid: String) = firestore.collection("users")
        .document(uid).collection("sync_dependency_snapshots")

    /**
     * Kullanıcının açık onayıyla yeni bir şifreli ilişki kopyası oluşturur.
     * Mevcut Firestore belgeleri veya Room kayıtları DEĞİŞTİRİLMEZ.
     */
    suspend fun uploadCatalog(uid: String, password: CharArray): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(password.size in 12..128) { "Aktarım şifresi en az 12 karakter olmalı." }
            requireAccount(uid)
            // BackupRepository sadece veriyi okur. restore/replace fonksiyonları ASLA çağrılmaz.
            val backup = backupRepository.createBackupData()
            // Ana Room ilişkilerinde hata varsa eksik veriyle katalog üretmeyiz.
            backupValidator.parseAndValidate(gson.toJson(backup))
            // Günlüğü silinmiş (yetim) ruh hali kayıtları katalogda anlamsızdır ve
            // fromLocal'ın ilişki kontrolünü kalıcı olarak başarısız yapardı.
            // Var olan bir anının ruh hali ise asla atlanmaz.
            val journalIds = backup.journals.map { it.id }.toSet()
            val moods = moodRepository.observeAll().first().filter { it.journalId in journalIds }
            val catalog = SyncDependencyCatalog.fromLocal(uid, backup, moods)
            catalog.validatedIndex(uid)
            val raw = gson.toJson(catalog)
            val encrypted = CloudArchiveCrypto.encrypt(raw, password)
            val checksum = sha256(encrypted)
            val encoded = try { Base64.encodeToString(encrypted, Base64.NO_WRAP) }
            finally { encrypted.fill(0) }
            require(encoded.length in 1..MAX_ENCODED_CHARS) {
                "İlişki kataloğu büyük. Bu sürümde yükleme iptal edildi."
            }
            val parts = encoded.chunked(PART_CHARS)
            require(parts.size in 1..MAX_PARTS)
            requireAccount(uid)
            val ref = catalogCollection(uid).document(UUID.randomUUID().toString())
            // Eksik aktarım görünmesin: önce parçalar, sonra tamamlandı manifesti.
            parts.withIndex().chunked(10).forEach { group ->
                requireAccount(uid)
                val batch = firestore.batch()
                group.forEach { (index, value) ->
                    batch.set(ref.collection("parts").document(partId(index)), mapOf(
                        "formatVersion" to 1,
                        "index" to index,
                        "data" to value
                    ))
                }
                batch.commit().await()
            }
            requireAccount(uid)
            ref.set(mapOf(
                "formatVersion" to 1,
                "ownerUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "capturedAtMillis" to catalog.capturedAtMillis,
                "partCount" to parts.size,
                "encodedLength" to encoded.length,
                "sha256" to checksum
            )).await()
            val verified = ref.get(Source.SERVER).await()
            require(verified.exists() && verified.getString("sha256") == checksum) {
                "Yüklenen katalog manifesti doğrulanamadı."
            }
            parts.size
        }
    }

    /**
     * Kataloğu ve olayların TAMAMINI sunucudan okur. Sınır aşılırsa başarılı sayılmaz.
     * Sonuç yalnızca kimlik/ilişki durumlarıdır, anı metinleri dönmez.
     */
    suspend fun audit(uid: String, password: CharArray): FullSyncCoverageReport = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(password.size in 12..128) { "Aktarım şifresi en az 12 karakter olmalı." }
            requireAccount(uid)
            val catalog = downloadCatalog(uid, password)
            val index = catalog.validatedIndex(uid)
            val allEvents = readAllEvents(uid, password)
            val latest = FullSyncCoveragePolicy.selectLatest(allEvents)
            var missing = 0
            var stale = 0
            var pending = 0
            var conflicts = 0
            var deleteConflicts = 0
            var onlyRemote = 0

            for (event in latest) {
                requireAccount(uid)
                val coverage = FullSyncCoveragePolicy.inspect(event, index, catalog.capturedAtMillis)
                if (coverage.missingRelations > 0) missing++
                if (coverage.oldCatalog) stale++
                val local = journalDao.getJournalById(event.journalId)
                val localEvent = eventDao.findByJournalId(event.journalId)
                if (localEvent?.ownerUid == uid && localEvent.state == JournalSyncChangePolicy.PENDING) {
                    pending++
                } else if (event.operation == JournalSyncChangePolicy.DELETE && local != null) {
                    deleteConflicts++
                } else if (local != null && event.operation == JournalSyncChangePolicy.UPSERT) {
                    // Aynı sürüm kaydı bile içerik eşitliği anlamına gelmez.
                    conflicts++
                } else if (event.operation == JournalSyncChangePolicy.UPSERT) {
                    onlyRemote++
                }
            }
            requireAccount(uid)
            FullSyncCoverageReport(
                totalEvents = allEvents.size,
                uniqueJournals = latest.size,
                catalogJournals = catalog.journalIds.size,
                catalogPeople = catalog.people.size,
                catalogSeasons = catalog.seasons.size,
                catalogEpisodes = catalog.episodes.size,
                catalogMoods = catalog.moods.size,
                missingRelationEvents = missing,
                staleCatalogEvents = stale,
                localPendingEvents = pending,
                localConflictEvents = conflicts,
                remoteDeleteLocalExists = deleteConflicts,
                remotelyOnlyEvents = onlyRemote
            )
        }
    }

    /**
     * Sade kurulum: en son şifreli katalog bu şifreyle açılabiliyor mu?
     * Katalog yoksa false döner; varsa ama şifre yanlışsa hata fırlatır.
     * Hiçbir belge veya Room kaydı değişmez.
     */
    suspend fun canOpenLatestCatalog(uid: String, password: CharArray): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            requireAccount(uid)
            val exists = !catalogCollection(uid).limit(1).get(Source.SERVER).await().isEmpty
            if (!exists) return@withContext false
            downloadCatalog(uid, password)
            true
        }
    }

    private suspend fun downloadCatalog(uid: String, password: CharArray): SyncDependencyCatalog {
        val doc = catalogCollection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1).get(Source.SERVER).await().documents.firstOrNull()
            ?: error("Bu hesap için şifreli ilişki kataloğu bulunamadı. Önce katalog oluştur.")
        requireAccount(uid)
        check(doc.getLong("formatVersion") == 1L && doc.getString("ownerUid") == uid)
        val count = doc.getLong("partCount")?.toInt() ?: error("Katalog parça sayısı eksik.")
        val expectedLength = doc.getLong("encodedLength")?.toInt() ?: error("Katalog uzunluğu eksik.")
        require(count in 1..MAX_PARTS && expectedLength in 1..MAX_ENCODED_CHARS)
        val encoded = StringBuilder(expectedLength)
        repeat(count) { index ->
            requireAccount(uid)
            val part = doc.reference.collection("parts").document(partId(index))
                .get(Source.SERVER).await()
            require(part.exists() && part.getLong("index") == index.toLong()) {
                "Şifreli katalog parçası eksik."
            }
            encoded.append(part.getString("data") ?: error("Boş katalog parçası."))
            require(encoded.length <= MAX_ENCODED_CHARS) { "Katalog boyutu aşıldı." }
        }
        require(encoded.length == expectedLength) { "Katalog uzunluğu uyuşmuyor." }
        val encrypted = Base64.decode(encoded.toString(), Base64.NO_WRAP)
        val raw = try {
            require(sha256(encrypted) == doc.getString("sha256")) { "Katalog bütünlüğü bozuk." }
            CloudArchiveCrypto.decrypt(encrypted, password)
        } finally { encrypted.fill(0) }
        val catalog = gson.fromJson(raw, SyncDependencyCatalog::class.java)
            ?: error("Katalog biçimi okunamadı.")
        catalog.validatedIndex(uid)
        check(catalog.capturedAtMillis == doc.getLong("capturedAtMillis")) {
            "Katalog üstbilgisi ve şifreli içeriği uyuşmuyor."
        }
        return catalog
    }

    private suspend fun readAllEvents(uid: String, password: CharArray): List<SyncRemoteFact> {
        val facts = mutableListOf<SyncRemoteFact>()
        var last: DocumentSnapshot? = null
        while (true) {
            requireAccount(uid)
            val query = firestore.collection("users").document(uid)
                .collection("journal_change_events")
                .orderBy("uploadedAt", Query.Direction.ASCENDING)
                .limit(PAGE_SIZE.toLong())
            val page = (if (last == null) query else query.startAfter(last))
                .get(Source.SERVER).await()
            if (page.documents.isEmpty()) break
            for (doc in page.documents) {
                require(facts.size < MAX_EVENTS) {
                    "Bulutta $MAX_EVENTS'den fazla olay var. Eksik listeyi tam sonuç olarak göstermiyoruz."
                }
                facts += decodeEvent(uid, doc, password)
            }
            last = page.documents.last()
            if (page.documents.size < PAGE_SIZE) break
        }
        requireAccount(uid)
        return facts
    }

    private fun decodeEvent(uid: String, doc: DocumentSnapshot, password: CharArray): SyncRemoteFact {
        check(doc.getLong("formatVersion") == 1L) { "Olay biçimi desteklenmiyor." }
        val encoded = doc.getString("encryptedPayload") ?: error("Şifreli olay eksik.")
        require(encoded.length in 51..600_000) { "Olay boyutu geçersiz." }
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val raw = try { CloudArchiveCrypto.decrypt(bytes, password) }
        finally { bytes.fill(0) }
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1) { "Olay şeması desteklenmiyor." }
        val journalId = json.getString("journalId")
        val operation = json.getString("operation")
        val revision = json.getLong("revision")
        RemoteJournalEventPolicy.verify(
            expectedUid = uid,
            documentId = doc.id,
            headerUid = doc.getString("ownerUid"),
            headerHash = doc.getString("journalHash"),
            headerOperation = doc.getString("operation"),
            headerRevision = doc.getLong("revision"),
            payloadUid = json.getString("ownerUid"),
            payloadJournalId = journalId,
            payloadOperation = operation,
            payloadRevision = revision
        )
        var season: String? = null
        var episode: String? = null
        val personIds = linkedSetOf<String>()
        if (operation == JournalSyncChangePolicy.UPSERT) {
            val journal = json.getJSONObject("journal")
            require(journal.getString("id") == journalId) { "Anı kimliği uyuşmuyor." }
            journal.getString("title")
            journal.getString("content")
            journal.getLong("entryDate")
            journal.getLong("createdAt")
            journal.getLong("updatedAt")
            season = journal.optString("seasonId").takeUnless { it.isBlank() || it == "null" }
            episode = journal.optString("episodeId").takeUnless { it.isBlank() || it == "null" }
            val mentions = json.getJSONArray("mentions")
            repeat(mentions.length()) { index ->
                val mention = mentions.getJSONObject(index)
                val personId = mention.getString("personId")
                require(personId.isNotBlank() && personIds.add(personId)) {
                    "Tekrarlı veya eksik kişi bağlantısı."
                }
                mention.getInt("mentionIndex")
            }
        } else {
            check(!json.has("journal")) { "Silme olayında anı içeriği bulunamaz." }
        }
        val recordedAt = json.getLong("changeRecordedAt")
        require(recordedAt > 0) { "Olay değişiklik tarihi geçersiz." }
        return SyncRemoteFact(journalId, revision, operation, season, episode,
            personIds, recordedAt)
    }

    private fun partId(index: Int): String = "part_${index.toString().padStart(4, '0')}"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_EVENTS = 2_000
        const val PART_CHARS = 100_000
        const val MAX_PARTS = 80
        const val MAX_ENCODED_CHARS = 8_000_000
    }
}
