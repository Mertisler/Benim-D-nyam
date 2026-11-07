package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.repository.JournalRepository
import javax.inject.Inject

class DeleteJournalUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend operator fun invoke(journalId: String) {
        writeCoordinator.withRegularWrite {
            repository.deleteJournal(journalId)
        }
    }
}
