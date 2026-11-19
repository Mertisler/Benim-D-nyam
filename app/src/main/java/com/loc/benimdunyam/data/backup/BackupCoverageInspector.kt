package com.loc.benimdunyam.data.backup

import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.media.PublishedPhotoDeletionLedger
import com.loc.benimdunyam.domain.repository.BackupRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Salt okunur cihaz fotoğrafları ve Room arşivi kapsam özeti. */
data class LocalBackupCoverage(
    val checkedAtMillis: Long,
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val publishedPhotoCount: Int,
    val publishedPhotoBytes: Long,
    val missingPhotoFiles: Int,
    val deletionMarkerCount: Int,
    val newestDeletionAtMillis: Long?
)

/**
 * Faz 2I.4: Yedeklerde ne olması gerektiğini mevcut çalışma alanından sayar.
 * Kesinlikle veritabanına yazmaz, silme işaretlerini değiştirmez, ağ kullanmaz.
 */
@Singleton
class BackupCoverageInspector @Inject constructor(
    private val workspace: ActiveWorkspaceSession,
    private val backupRepository: BackupRepository,
    private val journalPhotoStore: JournalPhotoStore,
    private val deletionLedger: PublishedPhotoDeletionLedger,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend fun scanLocal(): LocalBackupCoverage = writeCoordinator.withRegularWrite {
        withContext(Dispatchers.IO) {
            check(workspace.mode != WorkspaceMode.LOCKED && !workspace.needsRestart()) {
                "Yerel arşiv kilitli. Önce doğru hesapla giriş yap."
            }
            val data = backupRepository.createBackupData()
            val mediaRoot = File(workspace.mediaRoot(), "benim_dunyam_journal_photos").canonicalFile
            val deletionMarkers = deletionLedger.list()
            var count = 0
            var bytes = 0L
            var missing = 0
            for (journal in data.journals) {
                currentCoroutineContext().ensureActive()
                for (photo in journalPhotoStore.list(journal.id)) {
                    count++
                    // Yalnızca uygulama özel klasöründeki dosyaları kontrol et.
                    // Dosyalar başka yola işaret ediyorsa okunmaz, eksik olarak raporlanır.
                    val file = File(photo.absolutePath).canonicalFile
                    val withinMediaRoot = file.path.startsWith(mediaRoot.path + File.separator)
                    if (!withinMediaRoot || !file.isFile || file.length() !in 1..JournalPhotoStore.MAX_FILE_SIZE_BYTES) {
                        missing++
                    } else {
                        check(bytes <= Long.MAX_VALUE - file.length()) { "Medya boyut toplamı geçersiz." }
                        bytes += file.length()
                    }
                }
            }
            LocalBackupCoverage(
                checkedAtMillis = System.currentTimeMillis(),
                journalCount = data.journals.size,
                personCount = data.people.size,
                seasonCount = data.seasons.size,
                episodeCount = data.episodes.size,
                mentionCount = data.mentions.size,
                specialMemoryCount = data.specialMemories.size,
                publishedPhotoCount = count,
                publishedPhotoBytes = bytes,
                missingPhotoFiles = missing,
                deletionMarkerCount = deletionMarkers.size,
                newestDeletionAtMillis = deletionMarkers.maxOfOrNull { it.deletedAtMillis }
            )
        }
    }
}
