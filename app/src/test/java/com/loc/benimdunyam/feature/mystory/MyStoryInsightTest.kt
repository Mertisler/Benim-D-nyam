package com.loc.benimdunyam.feature.mystory

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hikâyem: yapay zekâ konuları ve ruh hali önerileri. */
class MyStoryInsightTest {
    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis
    private val now = at(2026, 11, 26)
    private fun j(id: String, date: Long) = JournalEntry(id, "Anı $id", "metin", date, date, date)

    private fun calc(
        journals: List<JournalEntry>,
        topics: Map<String, List<String>> = emptyMap(),
        moods: Map<String, String> = emptyMap(),
        suggestions: Map<String, JournalMood> = emptyMap(),
        period: LocalAnalyticsPeriod = LocalAnalyticsPeriod.LAST_12_MONTHS
    ) = MyStoryCalculator.calculate(journals, emptyList(), moods, emptyMap(), period, now, tz, topics, suggestions)

    @Test
    fun konularDonemdekiAnalizlerdenSayilir() {
        val journals = listOf(j("a", at(2026, 5, 1)), j("b", at(2026, 6, 1)), j("c", at(2026, 7, 1)), j("eski", at(2020, 1, 1)))
        val topics = mapOf(
            "a" to listOf("aile", "iş"), "b" to listOf("Aile", "aile"), "c" to listOf("seyahat"), "eski" to listOf("okul")
        )
        val report = calc(journals, topics)
        assertEquals(listOf(MyStoryTopic("aile", 2), MyStoryTopic("iş", 1), MyStoryTopic("seyahat", 1)), report.topics)
        assertEquals(3, report.analyzedInPeriod)
        // Dönem dışındaki "okul" sayılmaz; tüm zamanlarda sayılır.
        assertTrue(calc(journals, topics, period = LocalAnalyticsPeriod.ALL).topics.any { it.name == "okul" })
    }

    @Test
    fun azAnalizliDonemdeKonuGosterilmez() {
        val journals = listOf(j("a", at(2026, 5, 1)), j("b", at(2026, 6, 1)))
        assertTrue(calc(journals, mapOf("a" to listOf("aile"), "b" to listOf("aile"))).topics.isEmpty())
    }

    @Test
    fun ruhHaliOnerisiYalnizcaSecilmemisAnilarIcin() {
        val journals = listOf(j("secili", at(2026, 5, 1)), j("bos", at(2026, 6, 1)), j("eski", at(2019, 1, 1)))
        val report = calc(
            journals,
            moods = mapOf("secili" to "SAD"),
            suggestions = mapOf("secili" to JournalMood.HAPPY, "bos" to JournalMood.EXCITED, "eski" to JournalMood.PEACEFUL)
        )
        // Dönemden bağımsız; yeniden eskiye.
        assertEquals(listOf("bos", "eski"), report.moodSuggestions.map { it.journalId })
        assertEquals(JournalMood.EXCITED, report.moodSuggestions.first().mood)
    }
}
