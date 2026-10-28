package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.PersonRepository
import javax.inject.Inject

class GetPersonByIdUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(id: String): Person? {
        return personRepository.getPersonById(id)
    }
}