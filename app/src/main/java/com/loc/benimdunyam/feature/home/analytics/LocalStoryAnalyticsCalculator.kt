package com.loc.benimdunyam.feature.home.analytics

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.LifeSeason
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Faz 2I.2: Tüm hesaplamalar mevcut, cihazda kayıtlı anılardan yapılır.
 * Firebase/AI API çağrısı, yeni Room tablosu, ağ izni veya kullanıcıya ilişkin psikolojik çıkarım YOKTUR.
 */
enum class LocalAnalyticsPeriod(val label: String) {
    LAST_30_DAYS("30 gün"),
    LAST_12_MONTHS("12 ay"),
    ALL("Tümü")
}

data class LocalAnalyticsBar(val label: String, val count: Int)
data class LocalAnalyticsMood(val mood: JournalMood, val count: Int)
data class LocalAnalyticsPerson(val id: String, val name: String, val count: Int)
data class LocalAnalyticsSeason(val id: String, val title: String, val count: Int)

data class LocalStoryAnalyticsReport(
    val period: LocalAnalyticsPeriod = LocalAnalyticsPeriod.LAST_12_MONTHS,
    val journalCount: Int = 0,
    val distinctWritingDays: Int = 0,
    val moodTaggedCount: Int = 0,
    val distinctPeopleCount: Int = 0,
    val withoutSeasonCount: Int = 0,
    val monthBars: List<LocalAnalyticsBar> = emptyList(),
    val moods: List<LocalAnalyticsMood> = emptyList(),
    val people: List<LocalAnalyticsPerson> = emptyList(),
    val seasons: List<LocalAnalyticsSeason> = emptyList()
)

object LocalStoryAnalyticsCalculator {
    private val turkish = Locale.forLanguageTag("tr-TR")

    fun calculate(
        journals: List<JournalEntry>,
        seasons: List<LifeSeason>,
        moodNamesByJournalId: Map<String, String>,
        period: LocalAnalyticsPeriod = LocalAnalyticsPeriod.LAST_12_MONTHS,
        now: Long = System.currentTimeMillis()
    ): LocalStoryAnalyticsReport {
        val start = rangeStart(period, now)
        // Anı tarihi gelecekteyse bu dönem için yazılmış bir anı sayılmaz.
        // Aynı kimlik bir nedenle iki kez dönerse istatistikleri şişirmiyoruz.
        val entries = journals.asSequence()
            .distinctBy { it.id }
            .filter { it.entryDate > 0L && it.entryDate <= now && it.entryDate >= start }
            .toList()

        val moodCounts = mutableMapOf<JournalMood, Int>()
        val personCounts = mutableMapOf<String, Pair<String, Int>>()
        val seasonCounts = mutableMapOf<String, Int>()
        val seasonTitles = seasons.associate { it.id to it.title }
        var withoutSeasonCount = 0
        var taggedCount = 0
        val days = HashSet<String>()
        val monthCounts = mutableMapOf<String, Int>()

        entries.forEach { journal ->
            val calendar = Calendar.getInstance().apply { timeInMillis = journal.entryDate }
            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            days += dayKey
            val monthKey = monthKey(calendar)
            monthCounts[monthKey] = (monthCounts[monthKey] ?: 0) + 1

            // Ruh hali isimleri JournalMoodRepository'deki ayrı Room tablosundan gelir.
            // Tanınmayan/eski mood etiketlerini rastgele başka bir sınıfa çevirmiyoruz.
            val mood = JournalMood.entries.firstOrNull { it.name == moodNamesByJournalId[journal.id] }
            if (mood != null) {
                taggedCount++
                moodCounts[mood] = (moodCounts[mood] ?: 0) + 1
            }

            // Tek anıda aynı kişi birden fazla anılmışsa yalnızca bir anı sayılır.
            journal.mentionedPeople.distinctBy { it.id }.forEach { person ->
                val old = personCounts[person.id]
                personCounts[person.id] = person.name to ((old?.second ?: 0) + 1)
            }

            // Sezon aidiyeti tahmin edilmez; yalnızca geçerli açık sezon seçimi kullanılır.
            val seasonId = journal.seasonId?.takeIf { it in seasonTitles }
            if (seasonId == null) withoutSeasonCount++
            else seasonCounts[seasonId] = (seasonCounts[seasonId] ?: 0) + 1
        }

        return LocalStoryAnalyticsReport(
            period = period,
            journalCount = entries.size,
            distinctWritingDays = days.size,
            moodTaggedCount = taggedCount,
            distinctPeopleCount = personCounts.size,
            withoutSeasonCount = withoutSeasonCount,
            monthBars = monthsToShow(period, now).map { month ->
                LocalAnalyticsBar(month.second, monthCounts[month.first] ?: 0)
            },
            moods = moodCounts.entries.sortedWith(
                compareByDescending<Map.Entry<JournalMood, Int>> { it.value }.thenBy { it.key.ordinal }
            ).map { LocalAnalyticsMood(it.key, it.value) },
            people = personCounts.entries.sortedWith(
                compareByDescending<Map.Entry<String, Pair<String, Int>>> { it.value.second }
                    .thenBy { it.value.first }.thenBy { it.key }
            ).map { LocalAnalyticsPerson(it.key, it.value.first, it.value.second) },
            seasons = seasonCounts.entries.sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { seasonTitles[it.key].orEmpty() }.thenBy { it.key }
            ).map { LocalAnalyticsSeason(it.key, seasonTitles[it.key].orEmpty(), it.value) }
        )
    }

    /** Yerel takvim günleri kullanılır; yaz/kış saati geçişlerinde 24 saatlik sabit bölme yapılmaz. */
    private fun rangeStart(period: LocalAnalyticsPeriod, now: Long): Long {
        if (period == LocalAnalyticsPeriod.ALL) return Long.MIN_VALUE
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            LocalAnalyticsPeriod.LAST_30_DAYS -> c.add(Calendar.DAY_OF_YEAR, -29)
            LocalAnalyticsPeriod.LAST_12_MONTHS -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                c.add(Calendar.MONTH, -11)
            }
            LocalAnalyticsPeriod.ALL -> Unit
        }
        return c.timeInMillis
    }

    private fun monthKey(c: Calendar): String =
        "%04d-%02d".format(Locale.ROOT, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)

    private fun monthsToShow(period: LocalAnalyticsPeriod, now: Long): List<Pair<String, String>> {
        val cursor = Calendar.getInstance().apply {
            timeInMillis = rangeStart(period.takeUnless { it == LocalAnalyticsPeriod.ALL }
                ?: LocalAnalyticsPeriod.LAST_12_MONTHS, now)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = Calendar.getInstance().apply { timeInMillis = now }
        val output = mutableListOf<Pair<String, String>>()
        val format = SimpleDateFormat("MMM yy", turkish)
        // Tüm zamanlar görünümünde toplam değerler tüm arşivden; grafik son 12 ayı gösterir.
        while (monthKey(cursor) <= monthKey(end) && output.size < 12) {
            output += monthKey(cursor) to format.format(Date(cursor.timeInMillis))
            cursor.add(Calendar.MONTH, 1)
        }
        return output
    }
}
