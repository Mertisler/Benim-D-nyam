package com.loc.benimdunyam.data.sync.remote

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

/**
 * Faz C: uzak düzenlemenin gerçek Room şemasına (yabancı anahtarlar açık)
 * yazılması. Özellikle "unutulmaz anı" kayıtlarının etiket güncellemesi
 * sırasında CASCADE ile kaybolmadığını doğrular.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RemoteJournalEditWriterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    private val original = JournalEntryEntity("j1", "Eski başlık", "Ayşe ve Mehmet ile", 1, 1, 1)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.backupRestoreDao()
        dao.insertPeople(listOf(
            PersonEntity("p1", "Ayşe", null, 1, 1),
            PersonEntity("p2", "Mehmet", null, 1, 1),
            PersonEntity("p3", "Zeynep", null, 1, 1)
        ))
        dao.insertJournals(listOf(original))
        dao.insertMentions(listOf(
            JournalPersonMentionEntity("j1", "p1", 0, 0, 4),
            JournalPersonMentionEntity("j1", "p2", 1, 9, 15)
        ))
        dao.insertSpecialMemories(listOf(
            PersonSpecialMemoryEntity("p1", "j1", "ilk_tanisma", "Ayşe notu", 2, 2),
            PersonSpecialMemoryEntity("p2", "j1", "dostluk", "Mehmet notu", 2, 2)
        ))
        db.journalSyncEventDao().save(JournalSyncEventEntity("j1", "uid", "UPSERT", "SYNCED", 3, 1))
    }

    @After
    fun tearDown() = db.close()

    private fun apply(
        journal: JournalEntryEntity,
        mentions: List<JournalPersonMentionEntity>,
        special: List<PersonSpecialMemoryEntity> = emptyList()
    ) = runBlocking {
        db.withTransaction {
            RemoteJournalEditWriter.apply(
                journalDao = db.journalDao(),
                mentionDao = db.journalPersonMentionDao(),
                specialDao = db.personSpecialMemoryDao(),
                eventDao = db.journalSyncEventDao(),
                uid = "uid",
                journal = journal,
                mentions = mentions,
                specialMemories = special,
                remoteRevision = 4,
                remoteChangedAt = 500
            )
        }
    }

    private fun mentions() = runBlocking { db.journalPersonMentionDao().getMentionsForJournal("j1") }
    private fun special(person: String) = runBlocking { db.personSpecialMemoryDao().get(person, "j1") }

    @Test
    fun metinGuncellenirVeKuyrukSyncedOlur() {
        val edited = original.copy(title = "Yeni başlık", content = "Ayşe ve Mehmet ile, sonra deniz", updatedAt = 9)
        apply(edited, mentions())

        assertEquals(edited, runBlocking { db.journalDao().getJournalById("j1") })
        val event = runBlocking { db.journalSyncEventDao().findByJournalId("j1") }
        // Uzak değişiklik buluta tekrar PENDING gönderilmez.
        assertEquals(JournalSyncEventEntity("j1", "uid", "UPSERT", "SYNCED", 4, 500), event)
    }

    @Test
    fun kalanEtiketinUnutulmazAnisiKaybolmaz() {
        // Mehmet'in etiket konumu değişti (metin düzenlendi). REPLACE kullanılsaydı
        // etiket silinip eklenir ve Mehmet'in unutulmaz anısı CASCADE ile giderdi.
        val edited = original.copy(content = "Bugün Ayşe ve sonra Mehmet ile")
        apply(edited, listOf(
            JournalPersonMentionEntity("j1", "p1", 0, 6, 10),
            JournalPersonMentionEntity("j1", "p2", 1, 20, 26)
        ))

        assertEquals(6, mentions().single { it.personId == "p1" }.startOffset)
        assertNotNull(special("p1"))
        assertNotNull(special("p2"))
        assertEquals("Mehmet notu", special("p2")?.note)
    }

    @Test
    fun kaldirilanEtiketVeOnaBagliUnutulmazAniSilinir() {
        apply(original.copy(content = "Sadece Ayşe ile"), listOf(JournalPersonMentionEntity("j1", "p1", 0, 7, 11)))

        assertEquals(listOf("p1"), mentions().map { it.personId })
        assertNotNull(special("p1"))
        assertNull(special("p2"))
    }

    @Test
    fun yeniEtiketVeKatalogdakiUnutulmazAniEklenir() {
        apply(
            original.copy(content = "Ayşe, Mehmet ve Zeynep ile"),
            mentions() + JournalPersonMentionEntity("j1", "p3", 2, 16, 22),
            special = listOf(
                PersonSpecialMemoryEntity("p3", "j1", "yeni", "Zeynep notu", 3, 3),
                // Yerelde zaten olan unutulmaz anı katalogdakiyle EZİLMEZ.
                PersonSpecialMemoryEntity("p1", "j1", "baska", "Katalog notu", 3, 3)
            )
        )

        assertEquals(setOf("p1", "p2", "p3"), mentions().map { it.personId }.toSet())
        assertEquals("Zeynep notu", special("p3")?.note)
        assertEquals("Ayşe notu", special("p1")?.note)
    }

    @Test
    fun olmayanAniGuncellenemez() {
        assertFailsWith<IllegalStateException> {
            apply(original.copy(id = "yok"), emptyList())
        }
    }
}
