package com.loc.benimdunyam.feature.mystory

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import com.loc.benimdunyam.feature.home.analytics.LocalStoryAnalyticsCalculator
import com.loc.benimdunyam.feature.home.analytics.LocalStoryAnalyticsReport
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class MyStoryPerson(val id: String, val name: String, val memoryCount: Int, val atMillis: Long)

/** Uzun süredir hiçbir anıda etiketlenmemiş, ama hikâyede yeri olan kişi. */
data class QuietPerson(
    val id: String,
    val name: String,
    val memoryCount: Int,
    val lastMemoryAt: Long,
    val monthsAgo: Int
)

data class MyStoryMilestone(
    val emoji: String,
    val title: String,
    val detail: String,
    val journalId: String
)

data class MyStoryReport(
    /** Dönem seçimine göre mevcut özet: aylar, ruh hali, kişiler, dönemler. */
    val base: LocalStoryAnalyticsReport = LocalStoryAnalyticsReport(),
    val totalWords: Int = 0,
    val photoCount: Int = 0,
    /** Bu dönemde ilk kez bir anıda etiketlenen kişiler (Tümü seçiliyken boş). */
    val newPeople: List<MyStoryPerson> = emptyList(),
    /** Dönemden bağımsız: uzun zamandır anılarında olmayanlar. */
    val quietPeople: List<QuietPerson> = emptyList(),
    /** Dönemden bağımsız kilometre taşları. */
    val milestones: List<MyStoryMilestone> = emptyList(),
    /** Yıl Özeti'nin açılabileceği yıllar (yeniden eskiye). */
    val years: List<Int> = emptyList(),
    /** Tüm arşivde: ruh hali seçilmemiş anı sayısı. */
    val withoutMood: Int = 0,
    /** Tüm arşivde: hiçbir döneme bağlı olmayan anı sayısı. */
    val withoutSeason: Int = 0,
    val allTimeJournalCount: Int = 0,
    /** Dönemden bağımsız: son 26 haftanın yazma takvimi. */
    val writingCalendar: WritingCalendar = WritingCalendar(),
    /** Dönemden bağımsız: art arda yazılan günler. */
    val streak: WritingStreak = WritingStreak(),
    /** Seçilen dönemde hangi gün ve saatlerde yazıldığı. */
    val habits: WritingHabits = WritingHabits(),
    /** Son 12 ayın baskın ruh halleri (30 günlük dönemde boş). */
    val moodTrend: List<MoodMonth> = emptyList()
) {
    val isEmpty: Boolean get() = allTimeJournalCount == 0
}

/**
 * "Hikâyem" sayfasının hesaplayıcısı. Mevcut LocalStoryAnalyticsCalculator'ı
 * aynen kullanır ve üzerine arşiv geneli bilgiler ekler.
 *
 * Saf Kotlin; zaman ve saat dilimi parametredir. Cihazda hesaplanır, hiçbir
 * yere gönderilmez. Kişiler hakkında puanlama veya yargı içermez.
 */
object MyStoryCalculator {

    private val DAY = TimeUnit.DAYS.toMillis(1)
    /** Bu kadar süredir anılmayan kişi "uzun zamandır" sayılır. */
    val QUIET_AFTER_MILLIS: Long = TimeUnit.DAYS.toMillis(180)
    /** Tek bir anıda geçmiş kişiler bu listeye alınmaz; hikâyede yeri olanlar gösterilir. */
    private const val QUIET_MIN_MEMORIES = 2
    private const val QUIET_LIMIT = 6
    private const val NEW_PEOPLE_LIMIT = 8

    fun calculate(
        journals: List<JournalEntry>,
        seasons: List<LifeSeason>,
        moodNamesByJournalId: Map<String, String>,
        photoCountByJournalId: Map<String, Int>,
        period: LocalAnalyticsPeriod,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): MyStoryReport {
        val all = journals.distinctBy { it.id }.filter { it.entryDate in 1..now }
        if (all.isEmpty()) return MyStoryReport(base = LocalStoryAnalyticsReport(period = period))

        val base = LocalStoryAnalyticsCalculator.calculate(
            journals = all, seasons = seasons, moodNamesByJournalId = moodNamesByJournalId,
            period = period, now = now
        )
        val start = periodStart(period, now, timeZone)
        val inPeriod = all.filter { it.entryDate >= start }

        return MyStoryReport(
            base = base,
            totalWords = inPeriod.sumOf { wordCount(it.content) },
            photoCount = inPeriod.sumOf { photoCountByJournalId[it.id] ?: 0 },
            newPeople = if (period == LocalAnalyticsPeriod.ALL) emptyList() else newPeople(all, start),
            quietPeople = quietPeople(all, now),
            milestones = milestones(all),
            years = all.map { calendar(it.entryDate, timeZone).get(Calendar.YEAR) }
                .distinct().sortedDescending(),
            withoutMood = all.count { journal ->
                moodNamesByJournalId[journal.id]?.let { name ->
                    com.loc.benimdunyam.domain.model.JournalMood.entries.any { it.name == name }
                } != true
            },
            withoutSeason = all.count { journal -> seasons.none { it.id == journal.seasonId } },
            allTimeJournalCount = all.size,
            writingCalendar = MyStoryHabitsCalculator.calendar(all, now, timeZone),
            streak = MyStoryHabitsCalculator.streak(all, now, timeZone),
            habits = MyStoryHabitsCalculator.habits(all, start, now, timeZone),
            moodTrend = MyStoryHabitsCalculator.moodTrend(all, moodNamesByJournalId, period, now, timeZone)
        )
    }

    // -----------------------------------------------------------------------

    internal fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    private fun calendar(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }

    /** LocalStoryAnalyticsCalculator ile aynı dönem sınırları. */
    internal fun periodStart(period: LocalAnalyticsPeriod, now: Long, timeZone: TimeZone): Long {
        if (period == LocalAnalyticsPeriod.ALL) return Long.MIN_VALUE
        val c = calendar(now, timeZone).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            LocalAnalyticsPeriod.LAST_30_DAYS -> c.add(Calendar.DAY_OF_YEAR, -29)
            LocalAnalyticsPeriod.LAST_12_MONTHS -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, -11) }
            LocalAnalyticsPeriod.ALL -> Unit
        }
        return c.timeInMillis
    }

    private data class PersonSpan(val name: String, val count: Int, val first: Long, val last: Long)

    private fun personSpans(all: List<JournalEntry>): Map<String, PersonSpan> {
        val spans = mutableMapOf<String, PersonSpan>()
        for (journal in all) {
            for (person in journal.mentionedPeople.distinctBy { it.id }) {
                val old = spans[person.id]
                spans[person.id] = if (old == null) PersonSpan(person.name, 1, journal.entryDate, journal.entryDate)
                else old.copy(
                    name = person.name,
                    count = old.count + 1,
                    first = minOf(old.first, journal.entryDate),
                    last = maxOf(old.last, journal.entryDate)
                )
            }
        }
        return spans
    }

    private fun newPeople(all: List<JournalEntry>, start: Long): List<MyStoryPerson> =
        personSpans(all).filterValues { it.first >= start }
            .map { (id, span) -> MyStoryPerson(id, span.name, span.count, span.first) }
            .sortedWith(compareByDescending<MyStoryPerson> { it.atMillis }.thenBy { it.name })
            .take(NEW_PEOPLE_LIMIT)

    private fun quietPeople(all: List<JournalEntry>, now: Long): List<QuietPerson> =
        personSpans(all).filterValues { it.count >= QUIET_MIN_MEMORIES && now - it.last >= QUIET_AFTER_MILLIS }
            .map { (id, span) ->
                QuietPerson(id, span.name, span.count, span.last, ((now - span.last) / DAY / 30).toInt())
            }
            // Hikâyede en çok yeri olanlar önce; eşitlikte en uzun süredir anılmayan.
            .sortedWith(compareByDescending<QuietPerson> { it.memoryCount }.thenBy { it.lastMemoryAt }.thenBy { it.name })
            .take(QUIET_LIMIT)

    private fun milestones(all: List<JournalEntry>): List<MyStoryMilestone> {
        val sorted = all.sortedWith(compareBy<JournalEntry> { it.entryDate }.thenBy { it.createdAt }.thenBy { it.id })
        val result = mutableListOf<MyStoryMilestone>()
        val first = sorted.first()
        result += MyStoryMilestone("🌱", "İlk anın", first.title.ifBlank { "Başlıksız anı" }, first.id)
        listOf(1000, 500, 100).firstOrNull { sorted.size >= it }?.let { n ->
            val item = sorted[n - 1]
            result += MyStoryMilestone("🏅", "$n. anın", item.title.ifBlank { "Başlıksız anı" }, item.id)
        }
        sorted.maxWithOrNull(compareBy<JournalEntry> { wordCount(it.content) }.thenBy { it.entryDate })
            ?.takeIf { wordCount(it.content) > 0 && sorted.size > 1 }
            ?.let { result += MyStoryMilestone("✍️", "En uzun anın", "${it.title.ifBlank { "Başlıksız anı" }} · ${wordCount(it.content)} kelime", it.id) }
        sorted.maxWithOrNull(compareBy<JournalEntry> { it.mentionedPeople.distinctBy { p -> p.id }.size }.thenBy { it.entryDate })
            ?.takeIf { it.mentionedPeople.distinctBy { p -> p.id }.size >= 3 }
            ?.let { result += MyStoryMilestone("👥", "En kalabalık anın", "${it.title.ifBlank { "Başlıksız anı" }} · ${it.mentionedPeople.distinctBy { p -> p.id }.size} kişi", it.id) }
        return result
    }
}
