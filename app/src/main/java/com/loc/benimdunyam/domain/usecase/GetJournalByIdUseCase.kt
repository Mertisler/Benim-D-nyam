package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.repository.JournalRepository
import javax.inject.Inject

class GetJournalByIdUseCase @Inject constructor(
    private val journalRepository: JournalRepository
) {
    suspend operator fun invoke(id: String): JournalEntry? {
        return journalRepository.getJournalById(id)
    }
}