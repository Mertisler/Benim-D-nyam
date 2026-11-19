package com.loc.benimdunyam.data.sync.remote

import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.data.remote.firebase.archive.CloudRecordDiffCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Buluta şifreli giden ilişki kataloğu: hangi alanları dışarıda bıraktığı ve
 * ikinci cihazda kullanılmadan önce tutarlılığının nasıl doğrulandığı.
 * Ayrıca cihaz/bulut karşılaştırma raporunun sayımları.
 */
class SyncDependencyCatalogTest {

    private val uid = "uid-1"

    private fun archive() = BackupData(
        exportTimestamp = 1L,
        journals = listOf(
            JournalEntryEntity("j1", "Başlık", "Metin", 1, 1, 1, "s1", "e1"),
            JournalEntryEntity("j2", "Başlık 2", "Metin 2", 2, 2, 2)
        ),
        people = listOf(
            PersonEntity("p1", "Ayşe", null, 1, 1, "/data/user/0/app/files/p1.jpg", "/data/cover.jpg"),
            PersonEntity("p2", "Mehmet", "not", 1, 1)
        ),
        mentions = listOf(JournalPersonMentionEntity("j1", "p1", 0, 0, 4)),
        seasons = listOf(LifeSeasonEntity("s1", "Okul", "", 1, null, "#fff", "/data/season.jpg", 1)),
        episodes = listOf(LifeEpisodeEntity("e1", "s1", "Bölüm", "", 0, 1, null, 1)),
        specialMemories = listOf(PersonSpecialMemoryEntity("p1", "j1", "ilk", null, 1, 1))
    )

    private val moods = listOf(JournalMoodRecord("j1", "HAPPY", 5))

    private fun catalog() = SyncDependencyCatalog.fromLocal(uid, archive(), moods)

    // ---- fromLocal ----

    @Test
    fun cihazaOzelFotografYollariBulutaGitmez() {
        val c = catalog()
        assertTrue(c.people.all { it.profileImagePath == null && it.coverImagePath == null })
        assertNull(c.seasons.single().coverImagePath)
        // Diğer alanlar korunur.
        assertEquals("Ayşe", c.people.first().name)
        assertEquals("Okul", c.seasons.single().title)
    }

    @Test
    fun katalogGunlukMetniniTasimaz() {
        // Katalog günlük metni için alan içermez; yalnızca kimlikler.
        assertEquals(listOf("j1", "j2"), catalog().journalIds)
    }

    @Test
    fun anisiOlmayanRuhHaliYedeklemeyiDurdurur() {
        assertFailsWith<IllegalArgumentException> {
            SyncDependencyCatalog.fromLocal(uid, archive(), moods + JournalMoodRecord("yok", "SAD", 1))
        }
    }

    // ---- validatedIndex ----

    @Test
    fun gecerliKatalogDizinUretir() {
        val index = catalog().validatedIndex(uid)
        assertEquals(setOf("j1", "j2"), index.journalIds)
        assertEquals(setOf("p1", "p2"), index.personIds)
        assertEquals(mapOf("e1" to "s1"), index.episodesToSeason)
        assertEquals(setOf("j1"), index.moodJournalIds)
    }

    @Test
    fun baskaHesabinKataloguKullanilmaz() {
        assertFailsWith<IllegalArgumentException> { catalog().validatedIndex("uid-2") }
    }

    @Test
    fun bilinmeyenSurumVeyaTarihReddedilir() {
        assertFailsWith<IllegalArgumentException> { catalog().copy(formatVersion = 2).validatedIndex(uid) }
        assertFailsWith<IllegalArgumentException> { catalog().copy(capturedAtMillis = 0).validatedIndex(uid) }
    }

    @Test
    fun tekrarlananKimlikReddedilir() {
        val c = catalog()
        assertFailsWith<IllegalArgumentException> { c.copy(journalIds = c.journalIds + "j1").validatedIndex(uid) }
        assertFailsWith<IllegalArgumentException> { c.copy(people = c.people + c.people.first()).validatedIndex(uid) }
        assertFailsWith<IllegalArgumentException> { c.copy(moods = c.moods + c.moods.first()).validatedIndex(uid) }
    }

    @Test
    fun kopukIliskilerReddedilir() {
        val c = catalog()
        assertFailsWith<IllegalArgumentException>("dönemsiz bölüm") {
            c.copy(episodes = c.episodes.map { it.copy(seasonId = "yok") }).validatedIndex(uid)
        }
        assertFailsWith<IllegalArgumentException>("anısız ruh hali") {
            c.copy(moods = listOf(JournalMoodRecord("yok", "SAD", 1))).validatedIndex(uid)
        }
        assertFailsWith<IllegalArgumentException>("kişisiz bahsetme") {
            c.copy(mentions = c.mentions + JournalPersonMentionEntity("j2", "yok", 0, null, null)).validatedIndex(uid)
        }
        assertFailsWith<IllegalArgumentException>("tekrarlı bahsetme") {
            c.copy(mentions = c.mentions + c.mentions.first().copy(mentionIndex = 1)).validatedIndex(uid)
        }
        assertFailsWith<IllegalArgumentException>("bahsetmesiz özel anı") {
            c.copy(specialMemories = c.specialMemories + PersonSpecialMemoryEntity("p2", "j2", "x", null, 1, 1))
                .validatedIndex(uid)
        }
    }

    // ---- CloudRecordDiffCalculator ----

    private data class Rec(val id: String, val value: String, val localOnlyPath: String? = null)

    @Test
    fun farkRaporuDogruSayar() {
        val device = listOf(Rec("a", "1"), Rec("b", "2"), Rec("c", "3"))
        val cloud = listOf(Rec("b", "2"), Rec("c", "DEGISTI"), Rec("d", "4"))

        val diff = CloudRecordDiffCalculator.calculate(device, cloud, key = { it.id })

        assertEquals(1, diff.onlyOnDevice)   // a
        assertEquals(1, diff.onlyInCloud)    // d
        assertEquals(1, diff.different)      // c
        assertEquals(1, diff.matching)       // b
    }

    @Test
    fun karsilastirmaAlaniCihazaOzelAlanlariGozArdiEdebilir() {
        val device = listOf(Rec("a", "1", "/cihaz/foto.jpg"))
        val cloud = listOf(Rec("a", "1", null))
        val diff = CloudRecordDiffCalculator.calculate(
            device, cloud, key = { it.id }, comparable = { it.copy(localOnlyPath = null) }
        )
        assertEquals(1, diff.matching)
        assertEquals(0, diff.different)
    }

    @Test
    fun tekrarlananKimlikliKarsilastirmaReddedilir() {
        assertFailsWith<IllegalArgumentException> {
            CloudRecordDiffCalculator.calculate(listOf(Rec("a", "1"), Rec("a", "2")), emptyList(), { it.id })
        }
    }

    @Test
    fun bosListelerFarksizdir() {
        val diff = CloudRecordDiffCalculator.calculate(emptyList<Rec>(), emptyList(), { it.id })
        assertEquals(0, diff.onlyOnDevice + diff.onlyInCloud + diff.different + diff.matching)
    }
}
