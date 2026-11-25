package com.loc.benimdunyam.feature.mystory

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Hikâyem" sayfasının arşiv geneli hesapları. */
class MyStoryCalculatorTest {

    private val tz: TimeZone = TimeZone.getDefault()
    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month - 1, day, 12, 0, 0) }.timeInMillis

    private val now = at(2026, 11, 23)
    private val ayse = Person("p1", "Ayşe", null, 1, 1)
    private val mehmet = Person("p2", "Mehmet", null, 1, 1)
    private val zeynep = Person("p3", "Zeynep", null, 1, 1)
    private val can = Person("p4", "Can", null, 1, 1)
    private val season = LifeSeason("s1", "Üniversite", "", 1, null, "#fff", null, 1)

    private fun journal(
        id: String, date: Long, people: List<Person> = emptyList(),
        content: String = "bir iki", title: String = id, seasonId: String? = null
    ) = JournalEntry(id, title, content, date, date, date, people, seasonId)

    private fun calc(
        journals: List<JournalEntry>,
        period: LocalAnalyticsPeriod = LocalAnalyticsPeriod.LAST_12_MONTHS,
        moods: Map<String, String> = emptyMap(),
        photos: Map<String, Int> = emptyMap()
    ) = MyStoryCalculator.calculate(journals, listOf(season), moods, photos, period, now, tz)

    @Test
    fun bosArsiv() {
        assertTrue(calc(emptyList()).isEmpty)
    }

    @Test
    fun donemeGoreKelimeVeFotografSayilari() {
        val journals = listOf(
            journal("eski", at(2020, 1, 1), content = "çok eski bir anı burada"),
            journal("yeni", at(2026, 6, 1), content = "yeni anı")
        )
        val photos = mapOf("eski" to 5, "yeni" to 2)
        val year = calc(journals, photos = photos)
        assertEquals(2, year.totalWords)
        assertEquals(2, year.photoCount)
        val all = calc(journals, LocalAnalyticsPeriod.ALL, photos = photos)
        assertEquals(2 + 5, all.totalWords)
        assertEquals(7, all.photoCount)
    }

    @Test
    fun budonemdeHikayeyeKatilanlarYalnizcaIlkKezGorunenler() {
        val journals = listOf(
            journal("a", at(2021, 3, 1), listOf(ayse)),
            journal("b", at(2026, 5, 1), listOf(ayse, mehmet)),
            journal("c", at(2026, 9, 1), listOf(zeynep))
        )
        val report = calc(journals)
        // Ayşe 2021'den beri var; yeni değil.
        assertEquals(listOf("p3", "p2"), report.newPeople.map { it.id })
        assertTrue(calc(journals, LocalAnalyticsPeriod.ALL).newPeople.isEmpty())
    }

    @Test
    fun uzunZamandirAnilardaOlmayanlar() {
        val journals = listOf(
            // Mehmet: 3 anı, en son 2025 Mart → 6 aydan eski
            journal("m1", at(2024, 1, 1), listOf(mehmet)),
            journal("m2", at(2024, 6, 1), listOf(mehmet)),
            journal("m3", at(2025, 3, 1), listOf(mehmet)),
            // Zeynep: 2 anı, en son 2024 → 6 aydan eski
            journal("z1", at(2023, 1, 1), listOf(zeynep)),
            journal("z2", at(2024, 2, 1), listOf(zeynep)),
            // Can: tek anı, eski → listeye girmez
            journal("c1", at(2020, 1, 1), listOf(can)),
            // Ayşe: yakın zamanda → listeye girmez
            journal("a1", at(2024, 1, 1), listOf(ayse)),
            journal("a2", at(2026, 10, 1), listOf(ayse))
        )
        val quiet = calc(journals).quietPeople
        assertEquals(listOf("p2", "p3"), quiet.map { it.id })
        assertEquals(3, quiet.first().memoryCount)
        assertEquals(at(2025, 3, 1), quiet.first().lastMemoryAt)
        assertTrue(quiet.first().monthsAgo >= 20)
        // Dönem seçiminden bağımsızdır.
        assertEquals(quiet, calc(journals, LocalAnalyticsPeriod.LAST_30_DAYS).quietPeople)
    }

    @Test
    fun kilometreTaslari() {
        val journals = (1..120).map { i ->
            journal("j$i", at(2020, 1, 1) + i * 86_400_000L,
                people = if (i == 50) listOf(ayse, mehmet, zeynep) else emptyList(),
                content = if (i == 7) "çok uzun bir anı metni burada yazıyor" else "kısa")
        }
        val milestones = calc(journals).milestones
        assertEquals(listOf("İlk anın", "100. anın", "En uzun anın", "En kalabalık anın"), milestones.map { it.title })
        assertEquals("j1", milestones[0].journalId)
        assertEquals("j100", milestones[1].journalId)
        assertEquals("j7", milestones[2].journalId)
        assertEquals("j50", milestones[3].journalId)
    }

    @Test
    fun yillarVeTamamlanmamisArsiv() {
        val journals = listOf(
            journal("a", at(2024, 2, 1), seasonId = "s1"),
            journal("b", at(2026, 2, 1), seasonId = "silinmis-donem"),
            journal("c", at(2025, 2, 1))
        )
        val report = calc(journals, moods = mapOf("a" to "HAPPY", "b" to "ESKI_BILINMEYEN"))
        assertEquals(listOf(2026, 2025, 2024), report.years)
        // Bilinmeyen ruh hali adı "seçilmemiş" sayılır.
        assertEquals(2, report.withoutMood)
        // Var olmayan döneme bağlı anı da dönemsiz sayılır.
        assertEquals(2, report.withoutSeason)
    }

    @Test
    fun gelecekTarihliAniSayilmaz() {
        val report = calc(listOf(journal("a", at(2026, 1, 1)), journal("gelecek", at(2027, 1, 1))))
        assertEquals(1, report.allTimeJournalCount)
    }
}
