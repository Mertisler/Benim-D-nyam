package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.repository.PersonRepository
import javax.inject.Inject

class DeletePersonUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(id: String) {
        personRepository.deletePerson(id)
    }
}