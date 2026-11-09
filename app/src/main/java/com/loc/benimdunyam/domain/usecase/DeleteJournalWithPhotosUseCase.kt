package com.loc.benimdunyam.domain.usecase

import android.util.Log
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.draft.JournalDraftStore
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Use in EVERY journal deletion entry point (archive swipe, detail, etc).
 * Existing Room deletion MUST succeed before deleting any photo files.
 * Cleanup failures are reported separately; they do not falsely mark Room deletion as failed.
 */
data class JournalDeleteWithPhotosResult(val cleanupWarning: String? = null)

class DeleteJournalWithPhotosUseCase @Inject constructor(
    private val deleteJournalUseCase: DeleteJournalUseCase,
    private val photoStore: JournalPhotoStore,
    private val draftStore: JournalDraftStore,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend operator fun invoke(journalId: String): JournalDeleteWithPhotosResult =
        writeCoordinator.withRegularWrite {
            require(journalId.isNotBlank()) { "Günlük kimliği boş." }

            // One coordinator lease covers Room deletion AND photo cleanup:
            // a restore cannot interleave between these operations.
            deleteJournalUseCase(journalId)

            val problems = mutableListOf<String>()
            suspend fun tryCleanup(label: String, task: suspend () -> Unit) {
                try {
                    task()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("JournalDeleteWithPhotos", "$label temizlenemedi: $journalId", error)
                    problems += label
                }
            }

            tryCleanup("Anı fotoğrafları") { photoStore.deleteAllForJournal(journalId) }
            val draftKey = JournalDraftStore.keyFor(journalId)
            tryCleanup("Düzenleme fotoğrafları") { photoStore.discardDraftPhotos(draftKey) }
            tryCleanup("Düzenleme taslağı") { draftStore.delete(draftKey) }

            JournalDeleteWithPhotosResult(
                cleanupWarning = if (problems.isEmpty()) null else
                    "Anı silindi ancak ${problems.joinToString(", ")} temizlenemedi. " +
                            "Uygulama verilerini silme; Logcat'teki hatayı kontrol et."
            )
        }
}
