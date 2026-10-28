package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.repository.JournalRepository
import javax.inject.Inject

class SaveJournalUseCase @Inject constructor(
    private val journalRepository: JournalRepository
) {
    suspend operator fun invoke(journal: JournalEntry, personIds: List<String>) {
        // Yeni kayıt veya güncelleme işlemi repository içinde REPLACE stratejisiyle yürütülür
        journalRepository.insertJournal(journal, personIds)
    }
}