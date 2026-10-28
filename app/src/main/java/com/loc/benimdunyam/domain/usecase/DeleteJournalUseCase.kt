package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.repository.JournalRepository
import javax.inject.Inject

class DeleteJournalUseCase @Inject constructor(
    private val journalRepository: JournalRepository
) {
    suspend operator fun invoke(id: String) {
        journalRepository.deleteJournal(id)
    }
}