package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.PersonConnectionDao
import com.loc.benimdunyam.domain.model.PersonConnection
import com.loc.benimdunyam.domain.repository.PersonConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonConnectionRepositoryImpl @Inject constructor(
    private val dao: PersonConnectionDao
) : PersonConnectionRepository {

    override fun observeConnections(personId: String): Flow<List<PersonConnection>> {
        return dao.observeConnectionRows(personId).map { rows ->
            rows.groupBy { it.relatedPersonId }
                .mapNotNull { (id, relatedRows) ->
                    val first = relatedRows.firstOrNull() ?: return@mapNotNull null
                    PersonConnection(
                        personId = id,
                        name = first.relatedPersonName,
                        profileImagePath = first.relatedProfileImagePath,
                        sharedJournalIds = relatedRows.map { it.sharedJournalId }.distinct()
                    )
                }
                .sortedWith(
                    compareByDescending<PersonConnection> { it.sharedMemoryCount }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        .thenBy { it.personId }
                )
        }
    }
}
