package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.PersonConnection
import kotlinx.coroutines.flow.Flow

interface PersonConnectionRepository {
    /** Reacts to person changes and journal-person mention changes. */
    fun observeConnections(personId: String): Flow<List<PersonConnection>>
}
