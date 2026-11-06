package com.loc.benimdunyam.feature.people.detail.analytics.model

/** The unique ID below represents journals without an assigned season. */
const val ANALYTICS_UNASSIGNED_SEASON_ID = "__analytics_unassigned_season__"

data class PersonAnalyticsFilters(
    val year: Int? = null,
    val seasonId: String? = null,
    /** Local calendar day at 00:00; inclusive. */
    val startDateMillis: Long? = null,
    /** Local calendar day at 00:00; inclusive. */
    val endDateMillis: Long? = null
) {
    val isActive: Boolean get() = year != null || seasonId != null ||
            startDateMillis != null || endDateMillis != null
}

data class AnalyticsSeasonOption(val id: String, val title: String)
data class YearlyMemoryStat(val year: Int, val count: Int)
data class MonthlyMemoryStat(val month: Int, val count: Int)
data class SeasonMemoryStat(val seasonId: String?, val title: String, val count: Int)
data class EpisodeMemoryStat(val episodeId: String, val title: String, val count: Int)
data class SpecialCategoryStat(val title: String, val emoji: String, val count: Int)
data class SharedCharacterStat(val id: String, val name: String, val count: Int)

data class PersonAnalyticsReport(
    val totalMemories: Int = 0,
    val activeYearCount: Int = 0,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val unforgettableCount: Int = 0,
    val sharedCharacterCount: Int = 0,
    val firstMemoryDate: Long? = null,
    val lastMemoryDate: Long? = null,
    val yearly: List<YearlyMemoryStat> = emptyList(),
    val monthly: List<MonthlyMemoryStat> = emptyList(),
    val seasons: List<SeasonMemoryStat> = emptyList(),
    val topEpisodes: List<EpisodeMemoryStat> = emptyList(),
    val specialCategories: List<SpecialCategoryStat> = emptyList(),
    val sharedCharacters: List<SharedCharacterStat> = emptyList()
)
