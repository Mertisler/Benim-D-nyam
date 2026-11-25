package com.loc.benimdunyam.feature.mystory

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Isı haritasının bir günü. count = -1 → henüz gelmemiş gün. */
data class WritingDay(val dayStartMillis: Long, val count: Int)

data class WritingCalendar(
    /** Pazartesi → Pazar sıralı haftalar; eskiden yeniye. */
    val weeks: List<List<WritingDay>> = emptyList(),
    val activeDays: Int = 0,
    val maxCount: Int = 0
)

data class WritingStreak(
    /** Bugün ya da dün yazılmışsa devam eden seri; yoksa 0. */
    val current: Int = 0,
    val longest: Int = 0,
    val longestEndMillis: Long? = null,
    val writtenToday: Boolean = false
)

enum class DayPart(val emoji: String, val label: String) {
    MORNING("🌅", "Sabah"),
    AFTERNOON("☀️", "Öğleden sonra"),
    EVENING("🌆", "Akşam"),
    NIGHT("🌙", "Gece")
}

data class WritingHabits(
    /** Pazartesi → Pazar. */
    val weekdayCounts: List<Int> = List(7) { 0 },
    val dayPartCounts: Map<DayPart, Int> = emptyMap(),
    /** 0 = Pazartesi. Tek başına öne çıkan bir gün yoksa null. */
    val topWeekday: Int? = null,
    val topDayPart: DayPart? = null,
    val sampleSize: Int = 0
) {
    val hasEnoughData: Boolean get() = sampleSize >= MyStoryHabitsCalculator.HABITS_MIN_SAMPLES
}

data class MoodMonth(val label: String, val dominant: JournalMood?, val taggedCount: Int)

/**
 * Yazma alışkanlıkları ve ruh hali eğilimi.
 *
 * Yazma alışkanlıkları (takvim, seri, gün ve saat) anının yazıldığı zamana
 * (createdAt) bakar; ruh hali eğilimi anının tarihine (entryDate).
 */
internal object MyStoryHabitsCalculator {

    const val CALENDAR_WEEKS = 26
    const val HABITS_MIN_SAMPLES = 5
    private val TR = Locale.forLanguageTag("tr-TR")
    val WEEKDAY_LABELS = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    /** Eski kayıtlarda createdAt boş olabilir; o zaman anı tarihi kullanılır. */
    fun writtenAt(journal: JournalEntry): Long =
        if (journal.createdAt > 0) journal.createdAt else journal.entryDate

    private fun dayStart(millis: Long, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun addDays(day: Long, amount: Int, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply { timeInMillis = day; add(Calendar.DAY_OF_YEAR, amount) }.timeInMillis

    /** Pazartesi = 0 … Pazar = 6. */
    private fun mondayIndex(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun countsByDay(journals: List<JournalEntry>, now: Long, tz: TimeZone): Map<Long, Int> =
        journals.map { writtenAt(it) }.filter { it in 1..now }
            .groupingBy { dayStart(it, tz) }.eachCount()

    fun calendar(journals: List<JournalEntry>, now: Long, tz: TimeZone): WritingCalendar {
        val counts = countsByDay(journals, now, tz)
        val today = dayStart(now, tz)
        val cursor = Calendar.getInstance(tz).apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -mondayIndex(this))
            add(Calendar.DAY_OF_YEAR, -7 * (CALENDAR_WEEKS - 1))
        }
        val weeks = (0 until CALENDAR_WEEKS).map {
            (0 until 7).map {
                val start = cursor.timeInMillis
                cursor.add(Calendar.DAY_OF_YEAR, 1)
                WritingDay(start, if (start > today) -1 else counts[start] ?: 0)
            }
        }
        val active = weeks.flatten().filter { it.count > 0 }
        return WritingCalendar(weeks, active.size, active.maxOfOrNull { it.count } ?: 0)
    }

    fun streak(journals: List<JournalEntry>, now: Long, tz: TimeZone): WritingStreak {
        val days = countsByDay(journals, now, tz).keys.sorted()
        if (days.isEmpty()) return WritingStreak()
        var longest = 1
        var longestEnd = days.first()
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] == addDays(days[i - 1], 1, tz)) run + 1 else 1
            if (run >= longest) { longest = run; longestEnd = days[i] }
        }
        val today = dayStart(now, tz)
        val last = days.last()
        val current = if (last == today || last == addDays(today, -1, tz)) run else 0
        return WritingStreak(current, longest, longestEnd, writtenToday = last == today)
    }

    fun habits(journals: List<JournalEntry>, start: Long, now: Long, tz: TimeZone): WritingHabits {
        val times = journals.map { writtenAt(it) }.filter { it in 1..now && it >= start }
        if (times.size < HABITS_MIN_SAMPLES) return WritingHabits(sampleSize = times.size)
        val weekdays = IntArray(7)
        val parts = IntArray(DayPart.entries.size)
        for (t in times) {
            val c = Calendar.getInstance(tz).apply { timeInMillis = t }
            weekdays[mondayIndex(c)]++
            val part = when (c.get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> DayPart.MORNING
                in 12..16 -> DayPart.AFTERNOON
                in 17..21 -> DayPart.EVENING
                else -> DayPart.NIGHT
            }
            parts[part.ordinal]++
        }
        return WritingHabits(
            weekdayCounts = weekdays.toList(),
            dayPartCounts = DayPart.entries.associateWith { parts[it.ordinal] },
            topWeekday = strictTop(weekdays.toList()),
            topDayPart = strictTop(parts.toList())?.let { DayPart.entries[it] },
            sampleSize = times.size
        )
    }

    /** Tek başına en yüksek değerin indeksi; eşitlikte null (yanlış izlenim vermemek için). */
    private fun strictTop(values: List<Int>): Int? {
        val max = values.maxOrNull() ?: return null
        if (max == 0 || values.count { it == max } > 1) return null
        return values.indexOf(max)
    }

    /**
     * Son 12 ayın her biri için baskın ruh hali (anı tarihine göre).
     * 30 günlük dönemde ay ayrımı anlamsız olduğundan boş döner.
     * En az iki ayda ruh hali yoksa da boş döner.
     */
    fun moodTrend(
        journals: List<JournalEntry>,
        moodNamesByJournalId: Map<String, String>,
        period: LocalAnalyticsPeriod,
        now: Long,
        tz: TimeZone
    ): List<MoodMonth> {
        if (period == LocalAnalyticsPeriod.LAST_30_DAYS) return emptyList()
        val cursor = Calendar.getInstance(tz).apply {
            timeInMillis = dayStart(now, tz); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -11)
        }
        val format = SimpleDateFormat("MMM", TR).apply { timeZone = tz }
        val result = (0 until 12).map {
            val from = cursor.timeInMillis
            val label = format.format(Date(from))
            cursor.add(Calendar.MONTH, 1)
            val to = cursor.timeInMillis
            val moods = journals.filter { it.entryDate in from until to && it.entryDate <= now }
                .mapNotNull { j -> JournalMood.entries.firstOrNull { it.name == moodNamesByJournalId[j.id] } }
            val counts = moods.groupingBy { it }.eachCount()
            val max = counts.values.maxOrNull()
            val dominant = max?.let { m -> counts.filterValues { it == m }.keys.singleOrNull() }
            MoodMonth(label, dominant, moods.size)
        }
        return if (result.count { it.taggedCount > 0 } >= 2) result else emptyList()
    }
}
