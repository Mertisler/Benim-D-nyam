package com.loc.benimdunyam.feature.people.detail.model

import com.loc.benimdunyam.domain.model.JournalEntry

/** A journal in which the selected person appears, enriched with season/episode data. */
data class PersonAppearance(
    val journal: JournalEntry,
    val seasonId: String?,
    val seasonTitle: String?,
    val episodeId: String?,
    val episodeTitle: String?,
    val episodeOrderIndex: Int?
)

data class PersonEpisodeStat(
    val episodeId: String?,
    val episodeTitle: String,
    val memoryCount: Int,
    val orderIndex: Int?,
    val seasonTitle: String?
)

data class PersonSeasonStat(
    val seasonId: String?,
    val seasonTitle: String,
    val memoryCount: Int,
    val episodeStats: List<PersonEpisodeStat>
)

data class PersonProfileBadge(
    val title: String,
    val description: String,
    val emoji: String
)

data class PersonProfileStats(
    val totalMemoryCount: Int = 0,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val activeYearCount: Int = 0,
    val seasonBreakdown: List<PersonSeasonStat> = emptyList(),
    val topSeason: PersonSeasonStat? = null,
    val topEpisode: PersonEpisodeStat? = null,
    val firstAppearance: PersonAppearance? = null,
    val lastAppearance: PersonAppearance? = null,
    val badges: List<PersonProfileBadge> = emptyList()
)
