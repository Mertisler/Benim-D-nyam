package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveJournalsForPersonUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    operator fun invoke(personId: String): Flow<List<JournalEntry>> {
        return personRepository.observeJournalsForPerson(personId)
    }
}