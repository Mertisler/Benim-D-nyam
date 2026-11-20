package com.loc.benimdunyam.data.trash

import com.google.gson.Gson
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.dao.LifeEpisodeDao
import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.dao.PersonSpecialMemoryDao
import com.loc.benimdunyam.data.local.dao.TrashedJournalDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.entity.TrashReason
import com.loc.benimdunyam.data.local.entity.TrashedJournalEntity
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.RemoteDeletePolicy
import com.loc.benimdunyam.domain.sync.RemoteEditPolicy

/** "Son silinenler"de saklanan anlık görüntü. Alan adları R8'de korunur. */
data class TrashedJournalSnapshot(
    val formatVersion: Int = 1,
    val journal: JournalEntryEntity,
    val mentions: List<JournalPersonMentionEntity>,
    val specialMemories: List<PersonSpecialMemoryEntity>,
    val mood: JournalMoodRecord?
)

/** Geri getirmede, cihazda artık bulunmayan bağlar nedeniyle atlananlar. */
data class TrashRestoreOutcome(
    val snapshot: TrashedJournalSnapshot,
    val skippedMentions: Int,
    val detachedSeason: Boolean
)

/**
 * Faz B — çöp kutusuna taşıma ve geri getirmenin veritabanı adımları.
 *
 * Bu nesnedeki fonksiyonlar bir ana Room transaction'ının İÇİNDEN çağrılır;
 * ağ, dosya veya ayrı ruh hali veritabanına dokunmaz. Böylece anının silinmesi
 * ile anlık görüntünün kaydedilmesi tek atomik işlemdir: biri olmadan diğeri olmaz.
 */
internal object JournalTrashWriter {

    private val gson = Gson()

    fun encode(snapshot: TrashedJournalSnapshot): String = gson.toJson(snapshot)

    fun decode(json: String): TrashedJournalSnapshot {
        val snapshot = gson.fromJson(json, TrashedJournalSnapshot::class.java)
            ?: error("Silinen anının kaydı okunamadı.")
        check(snapshot.formatVersion == 1) { "Silinen anı kaydının biçimi desteklenmiyor." }
        check(snapshot.mentions.all { it.journalId == snapshot.journal.id } &&
                snapshot.specialMemories.all { it.journalId == snapshot.journal.id }) {
            "Silinen anı kaydı tutarsız."
        }
        return snapshot
    }

    /**
     * Anıyı ana tablodan kaldırır ve tam görüntüsünü çöp kutusuna yazar.
     * Kuyruğa SYNCED DELETE yazılır: bu silme buluta tekrar gönderilmez.
     *
     * @param mood ayrı veritabanından ÖNCEDEN okunmuş ruh hali (varsa)
     */
    suspend fun moveToTrash(
        journalDao: JournalDao,
        mentionDao: JournalPersonMentionDao,
        specialDao: PersonSpecialMemoryDao,
        eventDao: JournalSyncEventDao,
        trashDao: TrashedJournalDao,
        uid: String,
        journalId: String,
        reason: TrashReason,
        remoteRevision: Long,
        mood: JournalMoodRecord?,
        now: Long
    ): TrashedJournalSnapshot {
        val journal = checkNotNull(journalDao.getJournalById(journalId)) { "Silinecek anı bulunamadı." }
        check(trashDao.find(journalId) == null) { "Bu anı zaten Son silinenler'de." }
        val mentions = mentionDao.getMentionsForJournal(journalId)
        val special = mentions.mapNotNull { specialDao.get(it.personId, journalId) }
        val snapshot = TrashedJournalSnapshot(
            journal = journal,
            mentions = mentions,
            specialMemories = special,
            mood = mood?.takeIf { it.journalId == journalId }
        )
        trashDao.insert(
            TrashedJournalEntity(
                journalId = journalId,
                ownerUid = uid,
                reason = reason.name,
                trashedAt = now,
                expiresAt = RemoteDeletePolicy.expiresAt(now),
                remoteRevision = remoteRevision,
                title = journal.title.take(300),
                snapshotJson = encode(snapshot)
            )
        )
        // Etiketler silinince unutulmaz anılar yabancı anahtarla (CASCADE) gider;
        // görüntüleri yukarıda alındı.
        mentionDao.deleteAllMentionsForJournal(journalId)
        journalDao.deleteJournalById(journalId)
        eventDao.save(
            JournalSyncEventEntity(
                journalId = journalId,
                ownerUid = uid,
                changeType = JournalSyncChangePolicy.DELETE,
                state = JournalSyncChangePolicy.SYNCED,
                changeVersion = remoteRevision,
                updatedAtMillis = now
            )
        )
        return snapshot
    }

    /**
     * Anıyı çöp kutusundan geri getirir.
     *
     * Cihazda artık bulunmayan kişilerin etiketleri ve dönem/bölüm bağı atlanır
     * (anı metni hiçbir koşulda kaybolmaz). Hesap bağlıysa anı, buluttaki
     * silmeden daha yüksek bir sürümle gönderilmek üzere kuyruğa alınır; diğer
     * cihazlar da onu geri alır.
     */
    suspend fun restore(
        journalDao: JournalDao,
        mentionDao: JournalPersonMentionDao,
        specialDao: PersonSpecialMemoryDao,
        eventDao: JournalSyncEventDao,
        trashDao: TrashedJournalDao,
        personDao: PersonDao,
        seasonDao: LifeSeasonDao,
        episodeDao: LifeEpisodeDao,
        journalId: String,
        uid: String?,
        now: Long
    ): TrashRestoreOutcome {
        val trashed = checkNotNull(trashDao.find(journalId)) { "Silinen anı bulunamadı." }
        check(journalDao.getJournalById(journalId) == null) {
            "Bu kimlikte bir anı zaten var. Geri getirme yapılmadı."
        }
        check(trashed.ownerUid == null || uid == null || trashed.ownerUid == uid) {
            "Bu silinen anı başka bir hesaba ait."
        }
        val snapshot = decode(trashed.snapshotJson)

        var journal = snapshot.journal
        var detachedSeason = false
        val seasonOk = journal.seasonId == null || seasonDao.getSeasonById(journal.seasonId!!) != null
        val episodeOk = journal.episodeId == null ||
                episodeDao.getEpisodeById(journal.episodeId!!)?.seasonId == journal.seasonId
        if (!seasonOk) {
            journal = journal.copy(seasonId = null, episodeId = null); detachedSeason = true
        } else if (!episodeOk) {
            journal = journal.copy(episodeId = null); detachedSeason = true
        }
        journalDao.insertJournal(journal)

        val restoredMentions = snapshot.mentions.filter { personDao.getPersonById(it.personId) != null }
        restoredMentions.forEach { mentionDao.insertMention(it) }
        val restoredPeople = restoredMentions.map { it.personId }.toSet()
        snapshot.specialMemories
            .filter { it.personId in restoredPeople }
            .forEach { specialDao.insert(it) }

        if (uid != null) {
            val previous = eventDao.findByJournalId(journalId)
            val localVersion = previous?.changeVersion ?: 1L
            val highestKnown = maxOf(trashed.remoteRevision ?: 1L, localVersion)
            eventDao.save(
                JournalSyncEventEntity(
                    journalId = journalId,
                    ownerUid = uid,
                    changeType = JournalSyncChangePolicy.UPSERT,
                    state = JournalSyncChangePolicy.PENDING,
                    changeVersion = RemoteEditPolicy.versionToKeepLocal(highestKnown, localVersion),
                    updatedAtMillis = now
                )
            )
        }
        trashDao.delete(journalId)
        return TrashRestoreOutcome(
            snapshot = snapshot.copy(journal = journal),
            skippedMentions = snapshot.mentions.size - restoredMentions.size,
            detachedSeason = detachedSeason
        )
    }
}
