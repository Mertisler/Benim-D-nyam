package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.PersonRepository
import javax.inject.Inject

/** Kişi ekleme ve profil düzenleme kayıtlarını geri yükleme kilidine bağlar. */
class SavePersonUseCase @Inject constructor(
    private val repository: PersonRepository,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend operator fun invoke(person: Person, isUpdate: Boolean = false) {
        writeCoordinator.withRegularWrite {
            if (isUpdate) {
                repository.updatePerson(person)
            } else {
                repository.insertPerson(person)
            }
        }
    }
}
