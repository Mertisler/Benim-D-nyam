package com.loc.benimdunyam.data.trash

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.entity.TrashReason
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.domain.sync.RemoteDeletePolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Faz B: çöpe taşıma ve geri getirmenin gerçek Room şemasında (yabancı
 * anahtarlar açık) tek transaction içinde doğru çalıştığını sınar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class JournalTrashWriterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    private val journal = JournalEntryEntity("j1", "Deniz kenarı", "Ayşe ve Mehmet ile 😊", 10, 10, 20, "s1", "e1")
    private val mood = JournalMoodRecord("j1", "HAPPY", 25)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.backupRestoreDao()
        dao.insertSeasons(listOf(LifeSeasonEntity("s1", "Yaz", "", 1, null, "#fff", null, 1)))
        dao.insertEpisodes(listOf(LifeEpisodeEntity("e1", "s1", "Tatil", "", 0, 1, null, 1)))
        dao.insertPeople(listOf(
            PersonEntity("p1", "Ayşe", null, 1, 1),
            PersonEntity("p2", "Mehmet", null, 1, 1)
        ))
        dao.insertJournals(listOf(journal))
        dao.insertMentions(listOf(
            JournalPersonMentionEntity("j1", "p1", 0, 0, 4),
            JournalPersonMentionEntity("j1", "p2", 1, 9, 15)
        ))
        dao.insertSpecialMemories(listOf(PersonSpecialMemoryEntity("p2", "j1", "dostluk", "Not", 30, 30)))
        db.journalSyncEventDao().save(JournalSyncEventEntity("j1", "uid", "UPSERT", "SYNCED", 3, 20))
    }

    @After
    fun tearDown() = db.close()

    private fun moveToTrash(now: Long = 1_000) = runBlocking {
        db.withTransaction {
            JournalTrashWriter.moveToTrash(
                journalDao = db.journalDao(),
                mentionDao = db.journalPersonMentionDao(),
                specialDao = db.personSpecialMemoryDao(),
                eventDao = db.journalSyncEventDao(),
                trashDao = db.trashedJournalDao(),
                uid = "uid",
                journalId = "j1",
                reason = TrashReason.REMOTE_DELETE,
                remoteRevision = 4,
                mood = mood,
                now = now
            )
        }
    }

    private fun restore(uid: String? = "uid") = runBlocking {
        db.withTransaction {
            JournalTrashWriter.restore(
                journalDao = db.journalDao(),
                mentionDao = db.journalPersonMentionDao(),
                specialDao = db.personSpecialMemoryDao(),
                eventDao = db.journalSyncEventDao(),
                trashDao = db.trashedJournalDao(),
                personDao = db.personDao(),
                seasonDao = db.lifeSeasonDao(),
                episodeDao = db.lifeEpisodeDao(),
                journalId = "j1",
                uid = uid,
                now = 2_000
            )
        }
    }

    private fun mentions() = runBlocking { db.journalPersonMentionDao().getMentionsForJournal("j1") }
    private fun event() = runBlocking { db.journalSyncEventDao().findByJournalId("j1") }
    private fun trashed() = runBlocking { db.trashedJournalDao().find("j1") }

    @Test
    fun copeTasimaAniyiKaldirirVeTamGoruntuyuSaklar() {
        val snapshot = moveToTrash(now = 1_000)

        assertNull(runBlocking { db.journalDao().getJournalById("j1") })
        assertTrue(mentions().isEmpty())
        assertNull(runBlocking { db.personSpecialMemoryDao().get("p2", "j1") })

        val row = assertNotNull(trashed())
        assertEquals("Deniz kenarı", row.title)
        assertEquals(RemoteDeletePolicy.expiresAt(1_000), row.expiresAt)
        assertEquals(4L, row.remoteRevision)
        assertEquals("REMOTE_DELETE", row.reason)

        val decoded = JournalTrashWriter.decode(row.snapshotJson)
        assertEquals(snapshot, decoded)
        assertEquals(journal, decoded.journal)
        assertEquals(2, decoded.mentions.size)
        assertEquals("Not", decoded.specialMemories.single().note)
        assertEquals(mood, decoded.mood)

        // Bu silme buluta tekrar gönderilmez.
        assertEquals(JournalSyncEventEntity("j1", "uid", "DELETE", "SYNCED", 4, 1_000), event())
    }

    @Test
    fun ayniAniIkiKezCopeAtilamaz() {
        moveToTrash()
        assertFailsWith<IllegalStateException> { moveToTrash() }
    }

    @Test
    fun geriGetirmeHerSeyiEskiHalineKoyar() {
        moveToTrash()
        val outcome = restore()

        assertEquals(journal, runBlocking { db.journalDao().getJournalById("j1") })
        assertEquals(setOf("p1", "p2"), mentions().map { it.personId }.toSet())
        assertEquals("Not", runBlocking { db.personSpecialMemoryDao().get("p2", "j1") }?.note)
        assertEquals(mood, outcome.snapshot.mood)
        assertEquals(0, outcome.skippedMentions)
        assertFalse(outcome.detachedSeason)
        assertNull(trashed())
    }

    @Test
    fun geriGetirilenAniBulutSilmesindenYuksekSurumleGonderilir() {
        moveToTrash()
        restore()

        val e = assertNotNull(event())
        assertEquals("UPSERT", e.changeType)
        assertEquals("PENDING", e.state)
        // Buluttaki silme 4. sürüm: geri getirme onu aşmalı.
        assertTrue(e.changeVersion > 4)
    }

    @Test
    fun hesapYoksaGeriGetirilirAmaKuyrugaAlinmaz() {
        moveToTrash()
        restore(uid = null)
        assertNotNull(runBlocking { db.journalDao().getJournalById("j1") })
        assertEquals("DELETE", event()?.changeType)
    }

    @Test
    fun silinmisKisiVeDonemAtlanirAmaMetinKorunur() {
        moveToTrash()
        runBlocking {
            db.personDao().deletePersonById("p2")
            db.backupRestoreDao().clearEpisodes()
        }
        val outcome = restore()

        val restored = assertNotNull(runBlocking { db.journalDao().getJournalById("j1") })
        assertEquals(journal.content, restored.content)
        assertNull(restored.episodeId)
        assertEquals("s1", restored.seasonId)
        assertEquals(listOf("p1"), mentions().map { it.personId })
        assertEquals(1, outcome.skippedMentions)
        assertTrue(outcome.detachedSeason)
    }

    @Test
    fun ayniKimlikteAniVarsaGeriGetirmeYapilmaz() {
        moveToTrash()
        runBlocking { db.journalDao().insertJournal(journal.copy(title = "Başka cihazdan geldi")) }
        assertFailsWith<IllegalStateException> { restore() }
        assertNotNull(trashed())
    }
}
