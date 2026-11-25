package com.loc.benimdunyam.feature.mystory

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hikâyem 2. aşama: yazma takvimi, seri, alışkanlıklar ve ruh hali eğilimi. */
class MyStoryHabitsCalculatorTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month - 1, day, hour, 0, 0) }.timeInMillis

    /** 23 Kasım 2026 bir pazartesidir. */
    private val now = at(2026, 11, 23, 20)

    private var seq = 0
    private fun written(createdAt: Long, entryDate: Long = createdAt) =
        JournalEntry("j${seq++}", "başlık", "içerik", entryDate, createdAt, createdAt)

    @Test
    fun devamEdenVeEnUzunSeri() {
        val journals = (1..6).map { written(at(2025, 3, it)) } +   // 6 günlük eski seri
            (20..23).map { written(at(2026, 11, it, 9)) } +          // bugün dahil 4 gün
            written(at(2026, 11, 23, 18))                            // aynı güne ikinci anı
        val streak = MyStoryHabitsCalculator.streak(journals, now, tz)
        assertEquals(4, streak.current)
        assertEquals(6, streak.longest)
        assertEquals(at(2025, 3, 6, 0), streak.longestEndMillis)
        assertTrue(streak.writtenToday)
    }

    @Test
    fun dunYazildiysaSeriSurerDahaEskiyseBiter() {
        val untilYesterday = MyStoryHabitsCalculator.streak(listOf(written(at(2026, 11, 21)), written(at(2026, 11, 22))), now, tz)
        assertEquals(2, untilYesterday.current)
        assertFalse(untilYesterday.writtenToday)

        val broken = MyStoryHabitsCalculator.streak(listOf(written(at(2026, 11, 19)), written(at(2026, 11, 20))), now, tz)
        assertEquals(0, broken.current)
        assertEquals(2, broken.longest)
    }

    @Test
    fun seriAniTarihineDegilYazildigiGuneBakar() {
        // Geçmişe ait bir anı bugün yazıldı.
        val streak = MyStoryHabitsCalculator.streak(listOf(written(createdAt = now, entryDate = at(2010, 5, 5))), now, tz)
        assertEquals(1, streak.current)
        // createdAt boşsa anı tarihi kullanılır.
        val legacy = MyStoryHabitsCalculator.streak(listOf(written(createdAt = 0, entryDate = at(2026, 11, 23))), now, tz)
        assertEquals(1, legacy.current)
    }

    @Test
    fun yazmaTakvimi26HaftaPazartesidenBaslar() {
        val journals = listOf(written(at(2026, 11, 23, 8)), written(at(2026, 11, 23, 19)), written(at(2026, 11, 2)))
        val calendar = MyStoryHabitsCalculator.calendar(journals, now, tz)
        assertEquals(26, calendar.weeks.size)
        assertTrue(calendar.weeks.all { it.size == 7 })
        val first = Calendar.getInstance(tz).apply { timeInMillis = calendar.weeks.first().first().dayStartMillis }
        assertEquals(Calendar.MONDAY, first.get(Calendar.DAY_OF_WEEK))
        // Bugün pazartesi: son haftanın ilk günü bugün, kalan altı gün henüz gelmedi.
        val lastWeek = calendar.weeks.last()
        assertEquals(at(2026, 11, 23, 0), lastWeek.first().dayStartMillis)
        assertEquals(2, lastWeek.first().count)
        assertTrue(lastWeek.drop(1).all { it.count == -1 })
        assertEquals(2, calendar.activeDays)
        assertEquals(2, calendar.maxCount)
    }

    @Test
    fun azAnidaAliskanlikGosterilmez() {
        val habits = MyStoryHabitsCalculator.habits((1..4).map { written(at(2026, 11, it)) }, Long.MIN_VALUE, now, tz)
        assertFalse(habits.hasEnoughData)
    }

    @Test
    fun enCokYazilanGunVeSaat() {
        val journals = listOf(
            written(at(2026, 11, 2, 21)),  // pazartesi akşam
            written(at(2026, 11, 9, 20)),  // pazartesi akşam
            written(at(2026, 11, 16, 19)), // pazartesi akşam
            written(at(2026, 11, 4, 8)),   // çarşamba sabah
            written(at(2026, 11, 7, 23))   // cumartesi gece
        )
        val habits = MyStoryHabitsCalculator.habits(journals, Long.MIN_VALUE, now, tz)
        assertTrue(habits.hasEnoughData)
        assertEquals(listOf(3, 0, 1, 0, 0, 1, 0), habits.weekdayCounts)
        assertEquals(0, habits.topWeekday)
        assertEquals(DayPart.EVENING, habits.topDayPart)
        assertEquals(1, habits.dayPartCounts[DayPart.NIGHT])
    }

    @Test
    fun esitlikteOneCikanGunYok() {
        val journals = listOf(
            written(at(2026, 11, 2, 9)), written(at(2026, 11, 9, 21)),   // pazartesi ×2
            written(at(2026, 11, 3, 9)), written(at(2026, 11, 10, 21)),  // salı ×2
            written(at(2026, 11, 4, 14))
        )
        val habits = MyStoryHabitsCalculator.habits(journals, Long.MIN_VALUE, now, tz)
        assertNull(habits.topWeekday)
        assertNull(habits.topDayPart)
    }

    @Test
    fun aliskanliklarDonemeGoreSuzulur() {
        val old = (1..5).map { written(at(2024, 1, it)) }
        val start = MyStoryCalculator.periodStart(LocalAnalyticsPeriod.LAST_30_DAYS, now, tz)
        assertFalse(MyStoryHabitsCalculator.habits(old, start, now, tz).hasEnoughData)
        assertTrue(MyStoryHabitsCalculator.habits(old, Long.MIN_VALUE, now, tz).hasEnoughData)
    }

    @Test
    fun ruhHaliEgilimi() {
        val a = written(at(2026, 10, 3)); val b = written(at(2026, 10, 9)); val c = written(at(2026, 10, 20))
        val d = written(at(2026, 11, 1)); val e = written(at(2026, 11, 2))
        val moods = mapOf(
            a.id to JournalMood.HAPPY.name, b.id to JournalMood.HAPPY.name, c.id to JournalMood.SAD.name,
            d.id to JournalMood.SAD.name, e.id to JournalMood.EXCITED.name
        )
        val journals = listOf(a, b, c, d, e)
        val trend = MyStoryHabitsCalculator.moodTrend(journals, moods, LocalAnalyticsPeriod.LAST_12_MONTHS, now, tz)
        assertEquals(12, trend.size)
        assertEquals(JournalMood.HAPPY, trend[10].dominant)   // Ekim
        assertEquals(3, trend[10].taggedCount)
        assertNull(trend[11].dominant)                        // Kasım: eşitlik
        assertEquals(2, trend[11].taggedCount)
        assertEquals(0, trend[0].taggedCount)

        assertTrue(MyStoryHabitsCalculator.moodTrend(journals, moods, LocalAnalyticsPeriod.LAST_30_DAYS, now, tz).isEmpty())
        // Tek ayda ruh hali varsa eğilim gösterilmez.
        assertTrue(MyStoryHabitsCalculator.moodTrend(listOf(a, b), moods, LocalAnalyticsPeriod.ALL, now, tz).isEmpty())
    }

    @Test
    fun raporaBaglanir() {
        val journals = (19..23).map { written(at(2026, 11, it, 9)) }
        val report = MyStoryCalculator.calculate(journals, emptyList(), emptyMap(), emptyMap(),
            LocalAnalyticsPeriod.LAST_30_DAYS, now, tz)
        assertEquals(5, report.streak.current)
        assertEquals(5, report.writingCalendar.activeDays)
        assertEquals(DayPart.MORNING, report.habits.topDayPart)
        assertTrue(report.moodTrend.isEmpty())
    }
}
