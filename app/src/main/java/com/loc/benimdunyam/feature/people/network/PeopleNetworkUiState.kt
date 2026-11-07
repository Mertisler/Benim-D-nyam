package com.loc.benimdunyam.feature.people.network

import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph

data class PeopleNetworkUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val graph: PeopleNetworkGraph = PeopleNetworkGraph(),
    val availableSeasons: List<LifeSeason> = emptyList(),
    val selectedSeasonId: String? = null,
    val onlyWithMemories: Boolean = true,
    val selectedPersonId: String? = null
)
