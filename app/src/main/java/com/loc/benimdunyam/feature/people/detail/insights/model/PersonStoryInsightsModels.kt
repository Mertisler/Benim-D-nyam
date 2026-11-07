package com.loc.benimdunyam.feature.people.detail.insights.model

/** All numbers describe JOURNALS saved in the app, not real-life relationship quality. */
data class StoryInsightTheme(
    val id: String,
    val title: String,
    val emoji: String,
    val journalCount: Int,
    /** Unique journal IDs providing verifiable evidence for this theme. */
    val journalIds: List<String>
)

data class StoryInsightPeakYear(
    val year: Int,
    val journalCount: Int
)

data class StoryInsightPeakMonth(
    val year: Int,
    /** One-based month: 1..12. */
    val month: Int,
    val journalCount: Int
)

data class StoryInsightPeakSeason(
    val seasonId: String,
    val seasonTitle: String,
    val journalCount: Int
)

data class StoryInsightRevisitMemory(
    val journalId: String,
    val title: String,
    val entryDate: Long,
    val seasonTitle: String?
)

data class PersonStoryInsightsReport(
    val totalJournalCount: Int = 0,
    val specialJournalCount: Int = 0,
    val activeYearCount: Int = 0,
    val themes: List<StoryInsightTheme> = emptyList(),
    val peakYear: StoryInsightPeakYear? = null,
    val peakMonth: StoryInsightPeakMonth? = null,
    val peakSeason: StoryInsightPeakSeason? = null,
    val revisitMemories: List<StoryInsightRevisitMemory> = emptyList()
) {
    val isEmpty: Boolean get() = totalJournalCount == 0
}
