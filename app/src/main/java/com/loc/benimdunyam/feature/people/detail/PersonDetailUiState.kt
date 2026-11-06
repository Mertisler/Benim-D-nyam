package com.loc.benimdunyam.feature.people.detail

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.feature.people.detail.model.PersonConnectionItem
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import com.loc.benimdunyam.feature.people.detail.model.PersonProfileStats
import com.loc.benimdunyam.feature.people.detail.model.PersonSpecialMemoryItem

/** Complete presentation state for the person profile screen. */
data class PersonDetailUiState(
    val person: Person? = null,
    val associatedJournals: List<JournalEntry> = emptyList(),
    /** Journal records enriched with their matching season and episode. */
    val appearances: List<PersonAppearance> = emptyList(),
    val profileStats: PersonProfileStats = PersonProfileStats(),
    val specialMemories: List<PersonSpecialMemoryItem> = emptyList(),
    val connections: List<PersonConnectionItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSavingImage: Boolean = false,
    val isSavingSpecialMemory: Boolean = false,
    val specialMemoryErrorMessage: String? = null,
    val errorMessage: String? = null
)
