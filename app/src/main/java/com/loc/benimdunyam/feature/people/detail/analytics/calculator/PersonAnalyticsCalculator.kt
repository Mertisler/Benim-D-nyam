package com.loc.benimdunyam.feature.people.detail.analytics.calculator

import com.loc.benimdunyam.domain.model.PersonConnection
import com.loc.benimdunyam.domain.model.PersonSpecialMemory
import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory
import com.loc.benimdunyam.feature.people.detail.analytics.model.*
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import java.util.Calendar

/**
 * Pure, deterministic calculation over already loaded data.
 * No Android Context, Room, ViewModel, or mutable UI state is used here.
 * A recorded journal is always counted once, regardless of mention count.
 */
object PersonAnalyticsCalculator {

    fun availableYears(appearances: List<PersonAppearance>): List<Int> = appearances
        .distinctBy { it.journal.id }
        .map { year(it.journal.entryDate) }
        .distinct()
        .sortedDescending()

    fun availableSeasons(appearances: List<PersonAppearance>): List<AnalyticsSeasonOption> {
        return appearances
            .distinctBy { it.journal.id }
            .groupBy { it.seasonId ?: ANALYTICS_UNASSIGNED_SEASON_ID }
            .map { (id, rows) ->
                AnalyticsSeasonOption(
                    id = id,
                    title = rows.firstNotNullOfOrNull { it.seasonTitle }
                        ?: "Dönem belirtilmemiş"
                )
            }
            .sortedWith(compareBy<AnalyticsSeasonOption> { it.id == ANALYTICS_UNASSIGNED_SEASON_ID }
                .thenBy { it.title })
    }

    fun calculate(
        appearances: List<PersonAppearance>,
        specialMemories: List<PersonSpecialMemory>,
        connections: List<PersonConnection>,
        filters: PersonAnalyticsFilters = PersonAnalyticsFilters()
    ): PersonAnalyticsReport {
        val start = filters.startDateMillis?.let(::startOfDay)
        val endExclusive = filters.endDateMillis?.let(::startOfNextDay)

        val filtered = appearances
            .distinctBy { it.journal.id }
            .filter { row ->
                val millis = row.journal.entryDate
                (filters.year == null || year(millis) == filters.year) &&
                        (filters.seasonId == null ||
                                (row.seasonId ?: ANALYTICS_UNASSIGNED_SEASON_ID) == filters.seasonId) &&
                        (start == null || millis >= start) &&
                        (endExclusive == null || millis < endExclusive)
            }
        if (filtered.isEmpty()) return PersonAnalyticsReport()

        val journalIds = filtered.mapTo(hashSetOf()) { it.journal.id }

        val yearly = filtered.groupingBy { year(it.journal.entryDate) }.eachCount()
            .toSortedMap().map { (yr, count) -> YearlyMemoryStat(yr, count) }

        // Across all selected years, month counts are aggregated by calendar month.
        val monthlyCounts = filtered.groupingBy { month(it.journal.entryDate) }.eachCount()
        val monthly = (1..12).map { m -> MonthlyMemoryStat(m, monthlyCounts[m] ?: 0) }

        val seasonCounts = filtered.groupBy { it.seasonId }
            .map { (seasonId, rows) ->
                SeasonMemoryStat(
                    seasonId = seasonId,
                    title = rows.firstNotNullOfOrNull { it.seasonTitle }
                        ?: "Dönem belirtilmemiş",
                    count = rows.size
                )
            }
            .sortedWith(compareByDescending<SeasonMemoryStat> { it.count }.thenBy { it.title })

        val episodes = filtered.filter { it.episodeId != null }
            .groupBy { it.episodeId!! }
            .map { (id, rows) ->
                EpisodeMemoryStat(
                    episodeId = id,
                    title = rows.firstNotNullOfOrNull { it.episodeTitle } ?: "Bölüm",
                    count = rows.size
                )
            }
            .sortedWith(compareByDescending<EpisodeMemoryStat> { it.count }.thenBy { it.title })

        val validHighlights = specialMemories
            .filter { it.journalId in journalIds }
            .distinctBy { it.journalId }
        val categoryCounts = validHighlights.groupingBy { it.category }.eachCount()
        val categories = PersonSpecialMemoryCategory.values()
            .filter { (categoryCounts[it] ?: 0) > 0 }
            .map { category ->
                val (title, emoji) = categoryDisplay(category)
                SpecialCategoryStat(title, emoji, categoryCounts[category] ?: 0)
            }

        val shared = connections.mapNotNull { connection ->
            val commonCount = connection.sharedJournalIds.distinct().count { it in journalIds }
            if (commonCount == 0) null
            else SharedCharacterStat(connection.personId, connection.name, commonCount)
        }.sortedWith(compareByDescending<SharedCharacterStat> { it.count }
            .thenBy { it.name.lowercase() }.thenBy { it.id })

        val yearsCount = yearly.size
        return PersonAnalyticsReport(
            totalMemories = filtered.size,
            activeYearCount = yearsCount,
            seasonCount = filtered.mapNotNull { it.seasonId }.distinct().size,
            episodeCount = filtered.mapNotNull { it.episodeId }.distinct().size,
            unforgettableCount = validHighlights.size,
            sharedCharacterCount = shared.size,
            firstMemoryDate = filtered.minOf { it.journal.entryDate },
            lastMemoryDate = filtered.maxOf { it.journal.entryDate },
            yearly = yearly,
            monthly = monthly,
            seasons = seasonCounts,
            topEpisodes = episodes.take(5),
            specialCategories = categories,
            sharedCharacters = shared.take(5)
        )
    }

    fun year(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.get(Calendar.YEAR)

    fun month(millis: Long): Int = Calendar.getInstance().apply {
        timeInMillis = millis
    }.get(Calendar.MONTH) + 1

    /** Ensures date picker selections compare calendar days rather than time-of-day. */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfNextDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    private fun categoryDisplay(category: PersonSpecialMemoryCategory): Pair<String, String> = when (category) {
        PersonSpecialMemoryCategory.UNFORGETTABLE -> "Unutulmaz" to "⭐"
        PersonSpecialMemoryCategory.HAPPY_MOMENT -> "Mutlu an" to "💛"
        PersonSpecialMemoryCategory.TURNING_POINT -> "Dönüm noktası" to "🌱"
        PersonSpecialMemoryCategory.SPECIAL_DAY -> "Özel gün" to "🎉"
        PersonSpecialMemoryCategory.LIFE_LESSON -> "Hayat dersi" to "📖"
    }
}
