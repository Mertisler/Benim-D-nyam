
package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.repository.JournalRepository
import com.loc.benimdunyam.data.ai.insight.MemoryAnalysisScheduler
import javax.inject.Inject

class SaveJournalUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val writeCoordinator: BackupWriteCoordinator,
    // Yapay zekâ izni açıksa kaydedilen anı arka planda analiz edilir; kapalıysa işçi hemen biter.
    private val analysisScheduler: MemoryAnalysisScheduler
) {

    suspend operator fun invoke(
        journal: JournalEntry,
        personIds: List<String>
    ) {
        writeCoordinator.withRegularWrite {

            val existing =
                repository.getJournalById(journal.id)

            if (existing == null) {
                repository.insertJournal(
                    journal,
                    personIds
                )
            } else {
                repository.updateJournal(
                    journal,
                    personIds
                )
            }
        }
        runCatching { analysisScheduler.requestSoon() }
    }
}
