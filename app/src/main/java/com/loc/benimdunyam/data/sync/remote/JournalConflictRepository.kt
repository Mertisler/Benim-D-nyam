package com.loc.benimdunyam.data.sync.remote

import android.util.Base64
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.RemoteJournalEventPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Faz 2F.8: Anının hangi nedenle manuel inceleme gerektirdiği. */
enum class JournalConflictType {
    DIFFERENT_TEXT_OR_METADATA,
    REMOTE_DELETE_LOCAL_PRESENT,
    REMOTE_UPSERT_LOCAL_DELETED,
    INCONSISTENT_REMOTE_HEADS
}

/**
 * Ekrana yalnızca kısa metin örnekleri gider. Tam bulut metni StateFlow'a aktarılmaz.
 * confirmationKey, kullanıcı onayının hangi yerel/uzak sürüme ait olduğunu bağlar.
 */
data class JournalConflictCard(
    val remoteDocumentId: String,
    val journalId: String,
    val type: JournalConflictType,
    val remoteRevision: Long,
    val localRevision: Long?,
    val remoteTitle: String?,
    val localTitle: String?,
    val remoteExcerpt: String?,
    val localExcerpt: String?,
    val localHasPendingChange: Boolean,
    val canPreserveCopy: Boolean,
    val savedAsSeparateCopy: Boolean,
    val confirmationKey: String,
    val simultaneousRemoteHeads: Int
)

data class JournalConflictReport(
    val scannedEvents: Int,
    val totalConflicts: Int,
    val cards: List<JournalConflictCard>,
    val hiddenConflictCount: Int
)

data class PreservedJournalConflict(val copyJournalId: String, val alreadyPresent: Boolean)

private data class ConflictRemoteEvent(
    val documentId: String,
    val journalId: String,
    val revision: Long,
    val operation: String,
    val encryptedSha256: String,
    val plainLength: Int,
    val journal: JournalEntryEntity?,
    val mentions: List<JournalPersonMentionEntity>
)

private data class LocalConflictState(
    val journal: JournalEntryEntity?,
    val syncEvent: JournalSyncEventEntity?,
    val mentions: List<JournalPersonMentionEntity>
)

/**
 * Faz 2F.8: Çakışmaları tespit et ve iki metni KAYBETMEDEN koru.
 *
 * Bu sınıf:
 * - Firebase'den yalnızca kullanıcıya ait AES-GCM olaylarını SUNUCUDAN okur.
 * - Aynı anıdaki en yüksek sürüme ait TÜM olay başlarını inceler; eşit sürümde
 *   DELETE ve UPSERT varsa bunlardan birini sessizce seçmez.
 * - Mevcut anıyı ve bekleyen DELETE/UPSERT kuyruğunu ASLA değiştirmez.
 * - Kullanıcı ikinci kez şifre girip onaylarsa uyuşmayan metni YENİ kimlikle
 *   yalnızca metin kopyası olarak Room'a ekler. Kopya için yeni bir PENDING
 *   işlem oluşturur; buluta otomatik gönderim YAPMAZ.
 * - Kimlik, onay parmak izi ve yerel kayıtlar yazma öncesinde yeniden kontrol edilir.
 *
 * ÖNEMLİ: Bu bilinçli olarak 'otomatik kazanan seçimi' değildir. Çift yönlü
 * sürüm uzlaştırma ve çakışmanın bulut tarafında kapanması sonraki fazdadır.
 */
@Singleton
class JournalConflictRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val workspace: ActiveWorkspaceSession,
    private val database: AppDatabase,
    private val journalDao: JournalDao,
    private val syncDao: JournalSyncEventDao,
    private val mentionDao: JournalPersonMentionDao,
    private val writeCoordinator: BackupWriteCoordinator
) {
    private val mutex = Mutex()

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                workspace.isReadyFor(uid) && !workspace.needsRestart()) {
            "Aktif Firebase hesabı ile yerel arşivin sahibi uyuşmuyor."
        }
    }

    /** Önizleme SALT OKUNUR; hiçbir veritabanı veya bulut belgesi değişmez. */
    suspend fun inspect(uid: String, password: CharArray): JournalConflictReport = mutex.withLock {
        withContext(Dispatchers.IO) {
            requirePassword(password)
            requireAccount(uid)
            val events = readAllEvents(uid, password)
            val conflicts = mutableListOf<JournalConflictCard>()
            // Yalnızca her anı için en yeni sürümün BÜTÜN başlarını değerlendiririz.
            // Eski geçmiş olayları yeni sürüm yerine geçiremez.
            events.groupBy { it.journalId }.toSortedMap().forEach { (journalId, versions) ->
                requireAccount(uid)
                val topVersion = versions.maxOf { it.revision }
                val heads = versions.filter { it.revision == topVersion }
                val local = readLocal(journalId)
                heads.forEach headLoop@ { remote ->
                    val type = conflictType(remote, local, heads.size) ?: return@headLoop
                    val copyId = safeCopyId(uid, remote, local)
                    conflicts += JournalConflictCard(
                        remoteDocumentId = remote.documentId,
                        journalId = journalId,
                        type = type,
                        remoteRevision = remote.revision,
                        localRevision = local.syncEvent?.changeVersion,
                        remoteTitle = remote.journal?.title,
                        localTitle = local.journal?.title,
                        remoteExcerpt = remote.journal?.content?.take(EXCERPT_SIZE),
                        localExcerpt = local.journal?.content?.take(EXCERPT_SIZE),
                        localHasPendingChange = local.syncEvent?.state == JournalSyncChangePolicy.PENDING,
                        canPreserveCopy = remote.operation != JournalSyncChangePolicy.DELETE || local.journal != null,
                        savedAsSeparateCopy = journalDao.getJournalById(copyId) != null,
                        confirmationKey = confirmationKey(uid, remote, local, heads.size),
                        simultaneousRemoteHeads = heads.size
                    )
                }
            }
            requireAccount(uid)
            JournalConflictReport(
                scannedEvents = events.size,
                totalConflicts = conflicts.size,
                cards = conflicts.take(MAX_CARDS),
                hiddenConflictCount = (conflicts.size - MAX_CARDS).coerceAtLeast(0)
            )
        }
    }

    /**
     * Ayrı metin kopyası. Uzak UPSERT için bulut sürümünü, uzak DELETE için
     * henüz cihazda duran YEREL sürümü kopyalar. Orijinal anı ASLA silinmez.
     * Önizleme onayı, belge ve yerel sürüm yeniden doğrulanmadıkça geçerli değildir.
     */
    suspend fun preserveSeparateCopy(
        uid: String,
        password: CharArray,
        documentId: String,
        expectedConfirmationKey: String,
        remoteHeadCount: Int,
        confirmed: Boolean
    ): PreservedJournalConflict = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(confirmed) { "Ayrı kopya oluşturmak için açık onay gerekiyor." }
            requirePassword(password)
            require(documentId.startsWith("e_") && documentId.length == 66) {
                "Bulut belgesi kimliği geçersiz."
            }
            require(expectedConfirmationKey.matches(Regex("[a-f0-9]{64}"))) {
                "Önizleme doğrulama anahtarı geçersiz."
            }
            require(remoteHeadCount in 1..2) { "Uzaktaki olay başı sayısı geçersiz." }
            requireAccount(uid)
            // Ağ isteği açıkken Room transaction veya yazma kilidi TUTULMAZ.
            val doc = firestore.collection("users").document(uid)
                .collection("journal_change_events").document(documentId)
                .get(Source.SERVER).await()
            check(doc.exists()) { "Bulut olayı değişti veya kaldırıldı. Yeniden incele." }
            val remote = decodeEvent(uid, doc, password)
            requireAccount(uid)
            // Uzak baş sayısı değiştiyse bile kopyalama orijinali bozmaz. Ancak
            // gösterilen önizleme artık eski olabileceğinden önce TAM tarama isteriz.
            val currentHeads = readAllEvents(uid, password)
                .filter { it.journalId == remote.journalId }
                .let { all -> all.filter { it.revision == all.maxOf { e -> e.revision } } }
            check(currentHeads.size == remoteHeadCount &&
                    currentHeads.any { it.documentId == documentId }) {
                "Bulut sürümleri değişmiş. Yeniden önizleme yap."
            }

            writeCoordinator.withExclusiveRestore {
                requireAccount(uid)
                database.withTransaction {
                    requireAccount(uid)
                    val local = readLocalInTransaction(remote.journalId)
                    check(confirmationKey(uid, remote, local, remoteHeadCount) == expectedConfirmationKey &&
                            conflictType(remote, local, remoteHeadCount) != null) {
                        "Anı veya işlem kuyruğu değişti. Yeni önizleme gerekiyor."
                    }
                    val copyId = safeCopyId(uid, remote, local)
                    if (journalDao.getJournalById(copyId) != null) {
                        return@withTransaction PreservedJournalConflict(copyId, alreadyPresent = true)
                    }
                    val source = if (remote.operation == JournalSyncChangePolicy.DELETE)
                        local.journal ?: error("Silme çakışmasında koruyacak yerel anı bulunamadı.")
                    else remote.journal ?: error("Uzak metin bulunamadı.")
                    val now = System.currentTimeMillis()
                    // Aynı kimliği/ilişkileri başka cihaza zorla bağlamayız.
                    // İçerik BİREBİR saklanır; kişi/ruh hali bağları henüz aktarılmaz.
                    val titleSuffix = if (remote.operation == JournalSyncChangePolicy.DELETE)
                        " · Silme çakışması kopyası" else " · Bulut sürümü kopyası"
                    val newTitle = source.title.take(180) + titleSuffix
                    journalDao.insertJournal(source.copy(
                        id = copyId,
                        title = newTitle,
                        seasonId = null,
                        episodeId = null,
                        createdAt = now,
                        updatedAt = now
                    ))
                    // Kopya yeni bir yerel anıdır; bulut şifresi/parola/medya kaydedilmez.
                    syncDao.save(JournalSyncEventEntity(
                        journalId = copyId,
                        ownerUid = uid,
                        changeType = JournalSyncChangePolicy.UPSERT,
                        state = JournalSyncChangePolicy.PENDING,
                        changeVersion = 1L,
                        updatedAtMillis = now
                    ))
                    requireAccount(uid)
                    PreservedJournalConflict(copyId, alreadyPresent = false)
                }
            }
        }
    }

    private suspend fun readLocal(journalId: String): LocalConflictState = database.withTransaction {
        readLocalInTransaction(journalId)
    }

    private suspend fun readLocalInTransaction(journalId: String) = LocalConflictState(
        journal = journalDao.getJournalById(journalId),
        syncEvent = syncDao.findByJournalId(journalId),
        mentions = mentionDao.getMentionsForJournal(journalId)
    )

    /** Yalnızca uyuşmayan veya çelişen son olaylar kart olur. */
    private fun conflictType(
        remote: ConflictRemoteEvent,
        local: LocalConflictState,
        headCount: Int
    ): JournalConflictType? {
        if (headCount > 1) return JournalConflictType.INCONSISTENT_REMOTE_HEADS
        if (remote.operation == JournalSyncChangePolicy.DELETE) {
            return if (local.journal != null || local.syncEvent?.state == JournalSyncChangePolicy.PENDING)
                JournalConflictType.REMOTE_DELETE_LOCAL_PRESENT else null
        }
        if (local.journal == null) {
            return if (local.syncEvent?.changeType == JournalSyncChangePolicy.DELETE)
                JournalConflictType.REMOTE_UPSERT_LOCAL_DELETED else null
        }
        return if (local.journal != remote.journal ||
            local.mentions.toSet() != remote.mentions.toSet())
            JournalConflictType.DIFFERENT_TEXT_OR_METADATA else null
    }

    /** Görülen uzak olay ve yerel anının DEĞİŞMEDİĞİNİ garantileyen kontrol özeti. */
    private fun confirmationKey(
        uid: String, remote: ConflictRemoteEvent, local: LocalConflictState, heads: Int
    ): String = sha256Hex(
        listOf(uid, remote.documentId, remote.encryptedSha256, heads.toString(),
            local.journal.toString(), local.syncEvent.toString(),
            local.mentions.sortedWith(compareBy({ it.personId }, { it.mentionIndex })).toString()
        ).joinToString("\u0000").toByteArray(Charsets.UTF_8)
    )

    /**
     * Bulut UPSERT'inde belgeye, uzaktan DELETE geldiğinde ise mevcut yerel
     * metne bağlanır: yerel metin daha sonra değişirse yeni koruma kopyası mümkündür.
     */
    private fun safeCopyId(
        uid: String, remote: ConflictRemoteEvent, local: LocalConflictState
    ): String {
        val sourceDigest = if (remote.operation == JournalSyncChangePolicy.DELETE)
            sha256Hex(local.journal.toString().toByteArray(Charsets.UTF_8))
        else remote.encryptedSha256
        return UUID.nameUUIDFromBytes(
            "BD-CONFLICT-COPY-V1|$uid|${remote.documentId}|$sourceDigest"
                .toByteArray(Charsets.UTF_8)
        ).toString()
    }

    /** Faz 2F.3 ile aynı AES-GCM ve belge doğrulama sözleşmesi. */
    private fun decodeEvent(
        uid: String, doc: DocumentSnapshot, password: CharArray
    ): ConflictRemoteEvent {
        check(doc.getLong("formatVersion") == 1L) { "Desteklenmeyen olay biçimi." }
        val encoded = doc.getString("encryptedPayload") ?: error("Şifreli olay içeriği eksik.")
        require(encoded.length in 51..MAX_ENCODED_CHARS) { "Olay boyutu geçersiz." }
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val plain = try {
            CloudArchiveCrypto.decrypt(bytes, password)
        } finally { bytes.fill(0) }
        require(plain.length <= 600_000) { "Olayın çözülmüş boyutu sınırı aşıyor." }
        val json = JSONObject(plain)
        check(json.getInt("schema") == 1) { "Bulut olay şeması uyuşmuyor." }
        val journalId = json.getString("journalId")
        val operation = json.getString("operation")
        val revision = json.getLong("revision")
        RemoteJournalEventPolicy.verify(
            expectedUid = uid, documentId = doc.id,
            headerUid = doc.getString("ownerUid"), headerHash = doc.getString("journalHash"),
            headerOperation = doc.getString("operation"), headerRevision = doc.getLong("revision"),
            payloadUid = json.getString("ownerUid"), payloadJournalId = journalId,
            payloadOperation = operation, payloadRevision = revision
        )
        check(json.getLong("changeRecordedAt") > 0) { "Olay tarihi geçersiz." }
        if (operation == JournalSyncChangePolicy.DELETE) {
            check(!json.has("journal")) { "Silme olayı anı içeriği taşıyamaz." }
            return ConflictRemoteEvent(doc.id, journalId, revision, operation,
                sha256Hex(encoded.toByteArray(Charsets.UTF_8)), plain.length, null, emptyList())
        }
        val value = json.getJSONObject("journal")
        check(value.getString("id") == journalId) { "Anı kimliği uyuşmuyor." }
        val journal = JournalEntryEntity(
            id = journalId,
            title = value.getString("title"),
            content = value.getString("content"),
            entryDate = value.getLong("entryDate"),
            createdAt = value.getLong("createdAt"),
            updatedAt = value.getLong("updatedAt"),
            seasonId = value.optString("seasonId").takeUnless { it.isBlank() || it == "null" },
            episodeId = value.optString("episodeId").takeUnless { it.isBlank() || it == "null" }
        )
        require(journal.id.length in 1..128 && journal.title.length <= 1000 &&
                journal.content.length <= 300_000 && journal.createdAt > 0 && journal.updatedAt > 0) {
            "Buluttaki anı alanları geçersiz."
        }
        val array = json.getJSONArray("mentions")
        require(array.length() <= 500) { "Etiket sayısı güvenlik sınırını aşıyor." }
        val mentions = buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                fun nullable(key: String): Int? = if (!item.has(key) || item.isNull(key))
                    null else item.getInt(key)
                add(JournalPersonMentionEntity(
                    journalId = journalId,
                    personId = item.getString("personId"),
                    mentionIndex = item.getInt("mentionIndex"),
                    startOffset = nullable("startOffset"),
                    endOffset = nullable("endOffset")
                ))
            }
        }
        check(mentions.distinctBy { it.personId }.size == mentions.size && mentions.all {
            it.personId.isNotBlank() && it.mentionIndex >= 0 &&
                    ((it.startOffset == null && it.endOffset == null) ||
                            (it.startOffset != null && it.endOffset != null &&
                                    it.startOffset >= 0 && it.endOffset >= it.startOffset &&
                                    it.endOffset <= journal.content.length))
        }) { "Buluttaki kişi etiketleri geçersiz." }
        return ConflictRemoteEvent(doc.id, journalId, revision, operation,
            sha256Hex(encoded.toByteArray(Charsets.UTF_8)), plain.length, journal, mentions)
    }

    /** Eksiksiz 50'lik sayfalama; limit aşılırsa kısmi başarı raporlamayız. */
    private suspend fun readAllEvents(uid: String, password: CharArray): List<ConflictRemoteEvent> {
        val result = ArrayList<ConflictRemoteEvent>()
        var cursor: DocumentSnapshot? = null
        var plainChars = 0
        while (true) {
            requireAccount(uid)
            val base = firestore.collection("users").document(uid)
                .collection("journal_change_events")
                .orderBy("uploadedAt", Query.Direction.ASCENDING)
                .limit(50)
            val page = (if (cursor == null) base else base.startAfter(cursor))
                .get(Source.SERVER).await()
            if (page.documents.isEmpty()) break
            for (doc in page.documents) {
                check(result.size < MAX_EVENTS) { "2.000 olay sınırı aşıldı. Kısmi inceleme yapılmaz." }
                val event = decodeEvent(uid, doc, password)
                plainChars += event.plainLength
                check(plainChars <= MAX_TOTAL_TEXT) { "Toplam anı boyutu sınırı aşıldı." }
                result += event
            }
            cursor = page.documents.last()
            if (page.size() < 50) break
        }
        requireAccount(uid)
        return result
    }

    private fun requirePassword(password: CharArray) {
        require(password.size in 12..128) { "Aktarım şifresi 12–128 karakter olmalı." }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val MAX_EVENTS = 2_000
        const val MAX_CARDS = 40
        const val MAX_TOTAL_TEXT = 12_000_000
        const val MAX_ENCODED_CHARS = 600_000
        const val EXCERPT_SIZE = 160
    }
}
