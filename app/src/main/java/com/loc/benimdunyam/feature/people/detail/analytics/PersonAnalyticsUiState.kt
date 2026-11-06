package com.loc.benimdunyam.feature.people.detail.analytics

import com.loc.benimdunyam.feature.people.detail.analytics.model.AnalyticsSeasonOption
import com.loc.benimdunyam.feature.people.detail.analytics.model.PersonAnalyticsFilters
import com.loc.benimdunyam.feature.people.detail.analytics.model.PersonAnalyticsReport

data class PersonAnalyticsUiState(
    val personName: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filters: PersonAnalyticsFilters = PersonAnalyticsFilters(),
    val availableYears: List<Int> = emptyList(),
    val availableSeasons: List<AnalyticsSeasonOption> = emptyList(),
    val report: PersonAnalyticsReport = PersonAnalyticsReport(),
    /** Indicates whether the person has any linked journal before filtering. */
    val hasAnyMemories: Boolean = false
)
