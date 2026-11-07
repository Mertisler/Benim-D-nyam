
package com.loc.benimdunyam.data.backup

import android.content.Context
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupHealthReport(
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val invalidRelations: Int,
    val missingImages: Int,
    val legacyImageReferences: Int,
    val unsafeImagePaths: Int,
    val orphanRestoreImages: Int,
    val staleTemporaryEntries: Int
) {
    val hasDataProblems: Boolean
        get() = invalidRelations > 0 ||
                missingImages > 0 ||
                legacyImageReferences > 0 ||
                unsafeImagePaths > 0

    val isHealthy: Boolean
        get() = !hasDataProblems
}

data class BackupCleanupReport(
    val removedTemporaryEntries: Int,
    val removedOrphanImages: Int
)

@Singleton
class BackupMaintenanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository
) {

    private companion object {
        const val STALE_TEMP_AGE =
            48L * 60 * 60 * 1000

        const val ORPHAN_IMAGE_AGE =
            7L * 24 * 60 * 60 * 1000
    }

    /**
     * Verileri silmeden arşiv sağlığını denetler.
     */
    suspend fun inspect(): BackupHealthReport =
        withContext(Dispatchers.IO) {

            val data = backupRepository.createBackupData()

            val journalIds =
                data.journals.map { it.id }.toSet()

            val personIds =
                data.people.map { it.id }.toSet()

            val seasonIds =
                data.seasons.map { it.id }.toSet()

            val episodes =
                data.episodes.associateBy { it.id }

            val mentionPairs = data.mentions.map {
                it.journalId to it.personId
            }.toSet()

            var invalidRelations = 0
            var missingImages = 0
            var legacyImages = 0
            var unsafePaths = 0

            // 1. Bölüm - sezon ilişkileri
            data.episodes.forEach { episode ->
                if (episode.seasonId !in seasonIds) {
                    invalidRelations++
                }
            }

            // 2. Günlük - sezon ve bölüm ilişkileri
            data.journals.forEach { journal ->

                if (
                    journal.seasonId != null &&
                    journal.seasonId !in seasonIds
                ) {
                    invalidRelations++
                }

                journal.episodeId?.let { episodeId ->

                    val episode = episodes[episodeId]

                    if (
                        episode == null ||
                        episode.seasonId != journal.seasonId
                    ) {
                        invalidRelations++
                    }
                }
            }

            // 3. Kişi - günlük bağlantıları
            data.mentions.forEach { mention ->

                if (
                    mention.journalId !in journalIds ||
                    mention.personId !in personIds
                ) {
                    invalidRelations++
                }
            }

            // 4. Unutulmaz anı bağlantıları
            data.specialMemories.forEach { memory ->

                if (
                    (memory.journalId to memory.personId)
                    !in mentionPairs
                ) {
                    invalidRelations++
                }
            }

            // 5. Fotoğraf dosyaları
            fun inspectImage(
                path: String?,
                expectedDirectory: File
            ) {
                if (path == null) return

                if (path.isBlank()) {
                    unsafePaths++
                    return
                }

                if (
                    path.startsWith("content://", true) ||
                    path.startsWith("file://", true)
                ) {
                    legacyImages++
                    return
                }

                try {
                    val file = File(path).canonicalFile
                    val allowed = expectedDirectory.canonicalFile

                    if (file.parentFile != allowed) {
                        unsafePaths++
                    } else if (
                        !file.isFile ||
                        file.length() == 0L
                    ) {
                        missingImages++
                    }

                } catch (_: Exception) {
                    missingImages++
                }
            }

            data.seasons.forEach { season ->
                inspectImage(
                    path = season.coverImagePath,
                    expectedDirectory = File(
                        context.filesDir,
                        "seasons"
                    )
                )
            }

            data.people.forEach { person ->

                val directory = File(
                    context.filesDir,
                    "people/${person.id}"
                )

                inspectImage(
                    person.profileImagePath,
                    directory
                )

                inspectImage(
                    person.coverImagePath,
                    directory
                )
            }

            val now = System.currentTimeMillis()

            val staleEntries =
                findOldTemporaryEntries(now)

            val referencedPaths =
                collectReferencedImages(data)

            val orphanImages = findOrphanRestoreImages(
                referencedPaths,
                now
            )

            BackupHealthReport(
                journalCount = data.journals.size,
                personCount = data.people.size,
                seasonCount = data.seasons.size,
                episodeCount = data.episodes.size,
                invalidRelations = invalidRelations,
                missingImages = missingImages,
                legacyImageReferences = legacyImages,
                unsafeImagePaths = unsafePaths,
                orphanRestoreImages = orphanImages.size,
                staleTemporaryEntries = staleEntries.size
            )
        }

    /**
     * Sadece çok eski geçici çıktıları ve
     * Room'da kullanılmayan bd_restore_ görsellerini
     * temizler.
     */
    suspend fun cleanupOldFiles(): BackupCleanupReport =
        withContext(Dispatchers.IO) {

            // Veritabanı okunamazsa silme yapılmaz.
            val data = backupRepository.createBackupData()

            val now = System.currentTimeMillis()

            val referencedPaths =
                collectReferencedImages(data)

            val temporaryEntries =
                findOldTemporaryEntries(now)

            val orphanImages =
                findOrphanRestoreImages(
                    referencedPaths,
                    now
                )

            var removedTemporary = 0
            var removedImages = 0

            temporaryEntries.forEach { entry ->
                val deleted = if (entry.isDirectory) {
                    entry.deleteRecursively()
                } else {
                    entry.delete()
                }

                if (deleted) {
                    removedTemporary++
                }
            }

            orphanImages.forEach { file ->
                if (file.delete()) {
                    removedImages++
                }
            }

            BackupCleanupReport(
                removedTemporaryEntries = removedTemporary,
                removedOrphanImages = removedImages
            )
        }

    /**
     * Room'un hâlen referans verdiği fotoğraflar.
     */
    private fun collectReferencedImages(
        data: BackupData
    ): Set<String> {

        val result = mutableSetOf<String>()

        fun addPath(path: String?) {
            if (
                path.isNullOrBlank() ||
                path.startsWith("content://", true)
            ) return

            runCatching {
                result.add(File(path).canonicalPath)
            }
        }

        data.seasons.forEach {
            addPath(it.coverImagePath)
        }

        data.people.forEach {
            addPath(it.profileImagePath)
            addPath(it.coverImagePath)
        }

        return result
    }

    /**
     * Sadece backup_staging klasöründeki
     * bilinen geçici dosyaları bulur.
     */
    private fun findOldTemporaryEntries(
        now: Long
    ): List<File> {

        val root = File(
            context.noBackupFilesDir,
            "backup_staging"
        ).canonicalFile

        if (!root.isDirectory) return emptyList()

        return root.listFiles().orEmpty()
            .filter { entry ->

                val name = entry.name

                val knownFile = entry.isFile && (
                        (
                                name.startsWith("benim_dunyam_") &&
                                        name.endsWith(".zip")
                                ) ||
                                (
                                        name.startsWith("bd_") &&
                                                (
                                                        name.endsWith(".zip") ||
                                                                name.endsWith(".bdbackup")
                                                        )
                                        )
                        )

                val knownDirectory =
                    entry.isDirectory &&
                            name.startsWith("restore_") &&
                            name.endsWith(".tmp")

                (knownFile || knownDirectory) &&
                        isOldAndContained(
                            entry,
                            root,
                            now,
                            STALE_TEMP_AGE
                        )
            }
    }

    /**
     * Alt dosyalar arasında yakın zamanda
     * değiştirilmiş olan varsa klasör silinmez.
     */
    private fun isOldAndContained(
        entry: File,
        root: File,
        now: Long,
        requiredAge: Long
    ): Boolean {

        val rootPath = root.canonicalPath +
                File.separator

        var visited = 0

        for (file in entry.walkTopDown()) {

            visited++

            if (visited > 5000) return false

            val canonical = runCatching {
                file.canonicalPath
            }.getOrNull() ?: return false

            if (!canonical.startsWith(rootPath)) {
                return false
            }

            val modified = file.lastModified()

            if (
                modified <= 0L ||
                modified > now ||
                now - modified < requiredAge
            ) {
                return false
            }
        }

        return true
    }

    /**
     * Önceki başarısız geri yüklemelerden kalan,
     * Room tarafından kullanılmayan eski fotoğrafları bulur.
     *
     * Normal sezon ve kişi fotoğraflarını tarayıp
     * silinecekler listesine eklemez.
     */
    private fun findOrphanRestoreImages(
        referencedPaths: Set<String>,
        now: Long
    ): List<File> {

        val result = mutableListOf<File>()

        val seasonRoot = File(
            context.filesDir,
            "seasons"
        ).canonicalFile

        val peopleRoot = File(
            context.filesDir,
            "people"
        ).canonicalFile

        fun scan(directory: File) {

            if (!directory.isDirectory) return

            val safeDirectory = directory.canonicalFile

            directory.listFiles().orEmpty()
                .forEach { candidate ->

                    if (!candidate.isFile) return@forEach

                    if (
                        !candidate.name.startsWith(
                            "bd_restore_"
                        )
                    ) return@forEach

                    val safeFile = runCatching {
                        candidate.canonicalFile
                    }.getOrNull() ?: return@forEach

                    if (
                        safeFile.parentFile != safeDirectory
                    ) return@forEach

                    val modified = candidate.lastModified()

                    if (
                        modified <= 0L ||
                        modified > now ||
                        now - modified < ORPHAN_IMAGE_AGE
                    ) return@forEach

                    if (
                        safeFile.path !in referencedPaths
                    ) {
                        result.add(candidate)
                    }
                }
        }

        if (seasonRoot.isDirectory) {
            scan(seasonRoot)
        }

        if (peopleRoot.isDirectory) {

            peopleRoot.listFiles().orEmpty()
                .forEach { personDirectory ->

                    if (
                        personDirectory.isDirectory &&
                        personDirectory.canonicalFile.parentFile
                        == peopleRoot
                    ) {
                        scan(personDirectory)
                    }
                }
        }

        return result
    }
}
