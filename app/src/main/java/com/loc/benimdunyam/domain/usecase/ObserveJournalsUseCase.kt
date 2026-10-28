package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveJournalsUseCase @Inject constructor(
    private val journalRepository: JournalRepository
) {
    operator fun invoke(): Flow<List<JournalEntry>> {
        return journalRepository.observeAllJournals()
    }
}