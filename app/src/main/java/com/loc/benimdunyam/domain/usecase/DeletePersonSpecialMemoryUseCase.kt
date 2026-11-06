package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.repository.PersonSpecialMemoryRepository
import javax.inject.Inject

class DeletePersonSpecialMemoryUseCase @Inject constructor(
    private val repository: PersonSpecialMemoryRepository
) {
    suspend operator fun invoke(personId: String, journalId: String) {
        repository.remove(personId, journalId)
    }
}
