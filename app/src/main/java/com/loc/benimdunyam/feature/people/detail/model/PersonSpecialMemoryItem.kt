package com.loc.benimdunyam.feature.people.detail.model

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.PersonSpecialMemory

/** View-specific model; the persisted relation does not duplicate the journal text. */
data class PersonSpecialMemoryItem(
    val journal: JournalEntry,
    val memory: PersonSpecialMemory
)
