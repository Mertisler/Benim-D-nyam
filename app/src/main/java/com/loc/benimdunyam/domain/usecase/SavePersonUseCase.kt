package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.PersonRepository
import javax.inject.Inject

class SavePersonUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    suspend operator fun invoke(person: Person, isUpdate: Boolean = false) {
        if (isUpdate) {
            personRepository.updatePerson(person)
        } else {
            personRepository.insertPerson(person)
        }
    }
}