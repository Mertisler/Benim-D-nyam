package com.loc.benimdunyam.data.sync.remote

import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.dao.PersonSpecialMemoryDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy

/**
 * Faz C — buluttaki bir düzenlemeyi cihazdaki MEVCUT anıya yazar.
 *
 * Bu sınıf bir Room transaction'ının İÇİNDEN çağrılmalıdır; kendi transaction'ını
 * açmaz. Kişi / dönem / bölüm bağımlılıklarının yerelde bulunduğunu çağıran
 * doğrular. Bilinçli olarak:
 *  - Anı satırı @Update ile güncellenir (REPLACE kullanılmaz; REPLACE satırı
 *    silip yeniden eklediği için bağlı kayıtları etkileyebilir).
 *  - Yalnızca uzakta KALDIRILAN kişi etiketleri silinir. Hâlâ duran etiketler
 *    güncellenir; böylece onlara bağlı "unutulmaz anı" kayıtları CASCADE ile
 *    kaybolmaz. Kaldırılan etiketin unutulmaz anısı ise tutarlılık için gider.
 *  - Yerelde eklenmiş unutulmaz anılar silinmez; katalogdaki eksik olanlar eklenir.
 *  - Kuyruk kaydı SYNCED ve uzak sürümle yazılır: bu değişiklik tekrar buluta
 *    PENDING olarak gönderilmez.
 *  - Ruh hali ayrı Room'dadır ve bu adımda DEĞİŞTİRİLMEZ.
 */
internal object RemoteJournalEditWriter {

    suspend fun apply(
        journalDao: JournalDao,
        mentionDao: JournalPersonMentionDao,
        specialDao: PersonSpecialMemoryDao,
        eventDao: JournalSyncEventDao,
        uid: String,
        journal: JournalEntryEntity,
        mentions: List<JournalPersonMentionEntity>,
        specialMemories: List<PersonSpecialMemoryEntity>,
        remoteRevision: Long,
        remoteChangedAt: Long
    ) {
        require(mentions.all { it.journalId == journal.id }) { "Etiket başka anıya ait." }
        checkNotNull(journalDao.getJournalById(journal.id)) { "Güncellenecek anı bulunamadı." }
        journalDao.updateJournal(journal)

        val local = mentionDao.getMentionsForJournal(journal.id).associateBy { it.personId }
        val remote = mentions.associateBy { it.personId }
        for (personId in local.keys - remote.keys) {
            mentionDao.deleteMention(journal.id, personId)
        }
        for ((personId, mention) in remote) {
            val existing = local[personId]
            when {
                existing == null -> mentionDao.insertMention(mention)
                existing != mention -> mentionDao.updateMention(mention)
            }
        }

        for (memory in specialMemories) {
            if (memory.journalId == journal.id && memory.personId in remote.keys &&
                specialDao.get(memory.personId, memory.journalId) == null) {
                specialDao.insert(memory)
            }
        }

        eventDao.save(
            JournalSyncEventEntity(
                journalId = journal.id,
                ownerUid = uid,
                changeType = JournalSyncChangePolicy.UPSERT,
                state = JournalSyncChangePolicy.SYNCED,
                changeVersion = remoteRevision,
                updatedAtMillis = remoteChangedAt
            )
        )
    }
}
