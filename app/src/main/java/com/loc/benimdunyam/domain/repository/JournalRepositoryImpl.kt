package com.loc.benimdunyam.data.repository

import androidx.room.withTransaction
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.sync.local.JournalSyncChangeRecorder
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class JournalRepositoryImpl @Inject constructor(
    private val journalDao: JournalDao,
    private val mentionDao: JournalPersonMentionDao,
    private val database: AppDatabase,
    private val changeRecorder: JournalSyncChangeRecorder
) : JournalRepository {

    override fun observeAllJournals(): Flow<List<JournalEntry>> {
        return journalDao.observeAllJournals().map { entities ->
            entities.map { entity ->
                val peopleEntities = mentionDao.getPeopleForJournal(entity.id)
                JournalEntry(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    entryDate = entity.entryDate,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    seasonId = entity.seasonId,
                    episodeId = entity.episodeId, // <-- EKLENDİ (DÜZELTME)
                    mentionedPeople = peopleEntities.map { p ->
                        Person(
                            id = p.id,
                            name = p.name,
                            note = p.note,
                            createdAt = p.createdAt,
                            updatedAt = p.updatedAt
                        )
                    }
                )
            }
        }
    }

    override suspend fun getJournalById(id: String): JournalEntry? {
        val entity = journalDao.getJournalById(id) ?: return null
        val peopleEntities = mentionDao.getPeopleForJournal(id)
        return JournalEntry(
            id = entity.id,
            title = entity.title,
            content = entity.content,
            entryDate = entity.entryDate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            seasonId = entity.seasonId,
            episodeId = entity.episodeId,
            mentionedPeople = peopleEntities.map { p ->
                Person(
                    id = p.id,
                    name = p.name,
                    note = p.note,
                    createdAt = p.createdAt,
                    updatedAt = p.updatedAt
                )
            }
        )
    }

    /**
     * Anıyı, kişi etiketlerini ve yerel senkronizasyon olayını TEK transaction
     * içinde kaydeder. Bir adım başarısız olursa hiçbiri yarım kalmaz.
     */
    override suspend fun insertJournal(journal: JournalEntry, personIds: List<String>) {
        val actor = changeRecorder.requireWritableActor()
        database.withTransaction {
            changeRecorder.requireSameActor(actor)
            val entity = journal.toRoomEntity()
            journalDao.insertJournal(entity)
            replaceMentions(journal.id, personIds)
            changeRecorder.record(journal.id, actor, JournalSyncChangePolicy.UPSERT)
            changeRecorder.requireSameActor(actor)
        }
    }

    /** Kaydedilen anının son sürümü için UPSERT olayı güncellenir. */
    override suspend fun updateJournal(journal: JournalEntry, personIds: List<String>) {
        val actor = changeRecorder.requireWritableActor()
        database.withTransaction {
            changeRecorder.requireSameActor(actor)
            check(journalDao.getJournalById(journal.id) != null) {
                "Güncellenecek anı bulunamadı."
            }
            journalDao.updateJournal(journal.toRoomEntity())
            replaceMentions(journal.id, personIds)
            changeRecorder.record(journal.id, actor, JournalSyncChangePolicy.UPSERT)
            changeRecorder.requireSameActor(actor)
        }
    }

    /**
     * Anı silinince DELETE kaydını aynı transaction'da saklıyoruz.
     * Günlük satırı silinse de gelecekteki bulut eşitlemesi için kimliği kalır.
     * Böylece silinen anının uzak cihazdan yeniden indirilmesini önleyebiliriz.
     */
    override suspend fun deleteJournal(id: String) {
        val actor = changeRecorder.requireWritableActor()
        database.withTransaction {
            changeRecorder.requireSameActor(actor)
            mentionDao.deleteAllMentionsForJournal(id)
            journalDao.deleteJournalById(id)
            changeRecorder.record(id, actor, JournalSyncChangePolicy.DELETE)
            changeRecorder.requireSameActor(actor)
        }
    }

    /** Bütün CRUD işlemlerinde aynı Room alanlarının kullanılması sağlanır. */
    private fun JournalEntry.toRoomEntity() = JournalEntryEntity(
        id = id,
        title = title,
        content = content,
        entryDate = entryDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        seasonId = seasonId,
        episodeId = episodeId
    )

    /** Kişi etiketleri de günlükle aynı transaction'dadır. */
    private suspend fun replaceMentions(journalId: String, personIds: List<String>) {
        mentionDao.deleteAllMentionsForJournal(journalId)
        personIds.forEachIndexed { index, personId ->
            mentionDao.insertMention(
                JournalPersonMentionEntity(
                    journalId = journalId,
                    personId = personId,
                    mentionIndex = index,
                    startOffset = null,
                    endOffset = null
                )
            )
        }
    }

    override fun searchJournals(query: String): Flow<List<JournalEntry>> {
        return journalDao.searchJournals(query).map { entities ->
            entities.map { entity ->
                val peopleEntities = mentionDao.getPeopleForJournal(entity.id)
                JournalEntry(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    entryDate = entity.entryDate,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    seasonId = entity.seasonId,
                    episodeId = entity.episodeId,
                    mentionedPeople = peopleEntities.map { p ->
                        Person(
                            id = p.id,
                            name = p.name,
                            note = p.note,
                            createdAt = p.createdAt,
                            updatedAt = p.updatedAt
                        )
                    }
                )
            }
        }
    }
}