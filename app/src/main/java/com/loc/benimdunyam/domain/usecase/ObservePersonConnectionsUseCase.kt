package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.PersonConnection
import com.loc.benimdunyam.domain.repository.PersonConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePersonConnectionsUseCase @Inject constructor(
    private val repository: PersonConnectionRepository
) {
    operator fun invoke(personId: String): Flow<List<PersonConnection>> =
        repository.observeConnections(personId)
}
