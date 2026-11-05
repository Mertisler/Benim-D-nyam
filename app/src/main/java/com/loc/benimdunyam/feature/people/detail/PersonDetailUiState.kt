package com.loc.benimdunyam.feature.people.detail

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.feature.people.detail.model.PersonProfileStats

data class PersonDetailUiState(
    val person: Person? = null,
    val associatedJournals: List<JournalEntry> = emptyList(),
    val profileStats: PersonProfileStats = PersonProfileStats(),
    val isLoading: Boolean = true,
    val isSavingImage: Boolean = false,
    val errorMessage: String? = null
)
