package com.loc.benimdunyam.data.backup

import android.content.Context
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class RestoreRecoveryReport(
    val removedPartialFiles: Int,
    val scannedFiles: Int
)

/**
 * Ani kapanmadan kalan eski bd_restore_*.part dosyalarını temizler.
 * Tamamlanmış fotoğrafları, Room kayıtlarını ve normal resimleri silmez.
 */
@Singleton
class RestoreRecoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val coordinator: BackupWriteCoordinator
) {
    private companion object {
        const val MIN_AGE_MS = 48L * 60 * 60 * 1000
        const val MAX_SCANNED = 10_000
    }

    private val validPersonId = Regex("[A-Za-z0-9_-]{1,128}")
    private val partialPattern = Regex("bd_restore_[A-Za-z0-9_-]+\\.part")

    suspend fun cleanupAbandonedParts(): RestoreRecoveryReport =
        coordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                // DB okunamazsa güvenli biçimde dur: hiçbir şey silme.
                val current = backupRepository.createBackupData()
                val referenced = buildSet {
                    fun register(path: String?) {
                        if (path.isNullOrBlank() || path.startsWith("content://")) return
                        runCatching { File(path).canonicalPath }.getOrNull()?.let { add(it) }
                    }
                    current.seasons.forEach { register(it.coverImagePath) }
                    current.people.forEach {
                        register(it.profileImagePath)
                        register(it.coverImagePath)
                    }
                }

                val root = context.filesDir.canonicalFile
                val targets = mutableListOf<File>()
                safeDirectory(root, "seasons")?.let(targets::add)
                safeDirectory(root, "people")?.let { people ->
                    people.listFiles().orEmpty().forEach { personFolder ->
                        if (validPersonId.matches(personFolder.name)) {
                            safeDirectory(people, personFolder.name)?.let(targets::add)
                        }
                    }
                }

                val now = System.currentTimeMillis()
                var scanned = 0
                var removed = 0

                outer@ for (folder in targets) {
                    for (candidate in folder.listFiles().orEmpty()) {
                        currentCoroutineContext().ensureActive()
                        if (scanned >= MAX_SCANNED) break@outer
                        scanned++
                        if (!candidate.isFile || !partialPattern.matches(candidate.name)) continue

                        val canonical = runCatching { candidate.canonicalFile }.getOrNull()
                            ?: continue
                        if (canonical.parentFile != folder) continue
                        // Sembolik bağlantı kullanarak klasör dışına yönlendirmeyi reddet.
                        if (canonical.path != candidate.absolutePath) continue
                        if (canonical.path in referenced) continue

                        val changed = candidate.lastModified()
                        if (changed <= 0L || changed > now || now - changed < MIN_AGE_MS) {
                            continue
                        }
                        if (candidate.delete()) removed++
                    }
                }
                RestoreRecoveryReport(
                    removedPartialFiles = removed,
                    scannedFiles = scanned
                )
            }
        }

    private fun safeDirectory(parent: File, child: String): File? {
        val original = File(parent, child)
        if (!original.isDirectory) return null
        val base = parent.canonicalFile
        val candidate = runCatching { original.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.parentFile == base && it.name == child }
    }
}
