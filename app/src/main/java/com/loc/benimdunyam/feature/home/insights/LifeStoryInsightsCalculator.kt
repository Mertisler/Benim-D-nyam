package com.loc.benimdunyam.feature.home.insights

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeSeason
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/** Analizler yalnızca kayıtlı günlüklerin somut verilerini kullanır; duygu/ilişki tahmini yapmaz. */
data class LifeStoryInsight(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val journalId: String? = null
)

data class LifeStoryInsightsReport(
    val totalJournals: Int = 0,
    val recentJournals: Int = 0,
    val insights: List<LifeStoryInsight> = emptyList()
)

object LifeStoryInsightsCalculator {

    private val tr = Locale.forLanguageTag("tr-TR")
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun calculate(
        journals: List<JournalEntry>,
        seasons: List<LifeSeason>,
        now: Long = System.currentTimeMillis()
    ): LifeStoryInsightsReport {
        val valid = journals.filter { it.entryDate > 0L }
        val recentCount = valid.count { it.entryDate in (now - 30L * DAY_MILLIS)..now }
        if (valid.isEmpty()) {
            return LifeStoryInsightsReport()
        }

        val insights = mutableListOf<LifeStoryInsight>()
        val currentDate = dateParts(now)

        // Önceki yılların aynı ay ve günündeki kayıtlardan en sonuncusu.
        val onThisDay = valid.asSequence()
            .filter { entry ->
                val d = dateParts(entry.entryDate)
                d.year < currentDate.year &&
                        d.month == currentDate.month &&
                        d.day == currentDate.day
            }
            .sortedWith(compareByDescending<JournalEntry> { it.entryDate }.thenBy { it.id })
            .firstOrNull()

        if (onThisDay != null) {
            val yearsAgo = currentDate.year - dateParts(onThisDay.entryDate).year
            insights += LifeStoryInsight(
                id = "on_this_day",
                emoji = "🕰️",
                title = "Bugün, $yearsAgo yıl önce",
                description = "“${onThisDay.title}” anını yazmıştın. Yeniden okumak ister misin?",
                journalId = onThisDay.id
            )
        }

        // Aynı kişi bir anıda birden fazla etiketlenmiş olsa bile bir kez sayılır.
        val peopleCounts = mutableMapOf<String, Pair<String, Int>>()
        valid.forEach { journal ->
            journal.mentionedPeople.distinctBy { it.id }.forEach { person ->
                val previous = peopleCounts[person.id]
                peopleCounts[person.id] = person.name to ((previous?.second ?: 0) + 1)
            }
        }
        val topPerson = peopleCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Pair<String, Int>>> { it.value.second }
                .thenBy { it.key })
            .firstOrNull()

        if (topPerson != null) {
            val (name, count) = topPerson.value
            insights += LifeStoryInsight(
                id = "top_person",
                emoji = "👥",
                title = "Anılarında sık karşılaştığın biri",
                description = "$name, $count farklı anında anılmış."
            )
        }

        val seasonTitles = seasons.associate { it.id to it.title }
        val seasonCounts = valid.mapNotNull { entry ->
            entry.seasonId?.takeIf { it in seasonTitles }
        }.groupingBy { it }.eachCount()
        val topSeason = seasonCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()

        if (topSeason != null) {
            insights += LifeStoryInsight(
                id = "top_season",
                emoji = "🎬",
                title = "En çok anı biriktirdiğin sezon",
                description = "${seasonTitles.getValue(topSeason.key)} döneminde ${topSeason.value} anın bulunuyor."
            )
        }

        // Aylar yıl ile birlikte gruplandırılır: ör. Mayıs 2025 ve Mayıs 2026 ayrıdır.
        val monthCounts = valid.groupingBy {
            val d = dateParts(it.entryDate)
            d.year * 100 + d.month
        }.eachCount()
        val topMonth = monthCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
            .firstOrNull()
        if (topMonth != null && topMonth.value >= 2) {
            val monthEntry = valid.first { entry ->
                val d = dateParts(entry.entryDate)
                d.year * 100 + d.month == topMonth.key
            }
            val monthName = SimpleDateFormat("MMMM yyyy", tr).format(Date(monthEntry.entryDate))
            insights += LifeStoryInsight(
                id = "top_month",
                emoji = "📅",
                title = "En hareketli arşiv ayın",
                description = "$monthName ayında ${topMonth.value} anı kaydetmişsin."
            )
        }

        val weekdayCounts = valid.groupingBy { dateParts(it.entryDate).dayOfWeek }.eachCount()
        val topWeekday = weekdayCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
        if (topWeekday != null && valid.size >= 3) {
            val dayNames = arrayOf("", "Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")
            insights += LifeStoryInsight(
                id = "top_weekday",
                emoji = "✍️",
                title = "En çok hangi gün yazmışsın?",
                description = "Anılarının ${topWeekday.value} tanesinin tarihi ${dayNames[topWeekday.key]} gününe denk geliyor."
            )
        }

        val longestStreak = longestConsecutiveDays(valid.map { dateParts(it.entryDate) })
        if (longestStreak >= 2) {
            insights += LifeStoryInsight(
                id = "writing_streak",
                emoji = "🌿",
                title = "Arka arkaya yazdığın günler",
                description = "Arşivinde en uzun kesintisiz günlük yazma serin $longestStreak gün."
            )
        }

        // Bugünün anısı yoksa, arşivden eski bir kaydı keşfetme fırsatı.
        if (onThisDay == null) {
            val ninetyDaysAgo = now - 90L * DAY_MILLIS
            val resurfaced = valid.asSequence()
                .filter { it.entryDate <= ninetyDaysAgo }
                .sortedWith(compareByDescending<JournalEntry> { it.entryDate }.thenBy { it.id })
                .firstOrNull()
            if (resurfaced != null) {
                insights.add(0, LifeStoryInsight(
                    id = "rediscover",
                    emoji = "📖",
                    title = "Geçmişten bir sayfa",
                    description = "“${resurfaced.title}” anına yeniden göz atabilirsin.",
                    journalId = resurfaced.id
                ))
            }
        }

        return LifeStoryInsightsReport(
            totalJournals = valid.size,
            recentJournals = recentCount,
            insights = insights
        )
    }

    private data class DayParts(
        val year: Int,
        val month: Int,
        val day: Int,
        val dayOfWeek: Int
    )

    private fun dateParts(timestamp: Long): DayParts {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return DayParts(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        )
    }

    private fun longestConsecutiveDays(allDays: List<DayParts>): Int {
        val days = allDays.distinctBy { Triple(it.year, it.month, it.day) }
            .sortedWith(compareBy<DayParts> { it.year }.thenBy { it.month }.thenBy { it.day })
        if (days.isEmpty()) return 0

        var longest = 1
        var current = 1
        for (i in 1 until days.size) {
            val previous = days[i - 1]
            val calendar = GregorianCalendar(previous.year, previous.month - 1, previous.day)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val nextIsConsecutive = calendar.get(Calendar.YEAR) == days[i].year &&
                    calendar.get(Calendar.MONTH) + 1 == days[i].month &&
                    calendar.get(Calendar.DAY_OF_MONTH) == days[i].day
            current = if (nextIsConsecutive) current + 1 else 1
            if (current > longest) longest = current
        }
        return longest
    }
}
