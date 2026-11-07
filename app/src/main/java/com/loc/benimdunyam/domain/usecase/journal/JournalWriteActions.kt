package com.loc.benimdunyam.domain.usecase.journal

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.repository.JournalRepository
import javax.inject.Inject

/**
 * Günlük ekleme/güncelleme/silme için tam bir kayıt kapısı.
 *
 * Bu sınıf mevcut JournalRepositoryImpl'i değiştirmez.
 * Ekranlar HALEN doğrudan eski SaveJournalUseCase'ini çağırıyorsa,
 * o çağrılar otomatik olarak kilitlenmiş SAYILMAZ.
 *
 * JournalRepository'nin önceki sürümdeki metot adlarını temel alır.
 */
class JournalWriteActions @Inject constructor(
    private val journalRepository: JournalRepository,
    private val coordinator: BackupWriteCoordinator
) {
    suspend fun create(journal: JournalEntry, personIds: List<String>) {
        coordinator.withRegularWrite {
            journalRepository.insertJournal(journal, personIds)
        }
    }

    suspend fun update(journal: JournalEntry, personIds: List<String>) {
        coordinator.withRegularWrite {
            journalRepository.updateJournal(journal, personIds)
        }
    }

    suspend fun delete(journalId: String) {
        coordinator.withRegularWrite {
            journalRepository.deleteJournal(journalId)
        }
    }
}
