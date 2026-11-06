package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.repository.PersonSpecialMemoryRepository
import javax.inject.Inject

class ObservePersonSpecialMemoriesUseCase @Inject constructor(
    private val repository: PersonSpecialMemoryRepository
) {
    operator fun invoke(personId: String) = repository.observeForPerson(personId)
}
