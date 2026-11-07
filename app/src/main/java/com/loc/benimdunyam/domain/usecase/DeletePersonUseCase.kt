package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.repository.PersonRepository
import javax.inject.Inject

class DeletePersonUseCase @Inject constructor(
    private val repository: PersonRepository,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend operator fun invoke(personId: String) {
        writeCoordinator.withRegularWrite {
            repository.deletePerson(personId)
        }
    }
}
