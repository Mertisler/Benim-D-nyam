
package com.loc.benimdunyam.data.backup

import android.content.Context
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Geri yükleme sonucunda aktarılan veri sayıları.
 *
 * UI katmanı bu bilgilerle kullanıcıya
 * başarı mesajı gösterebilir.
 */
data class RestoreSummary(
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val imageCount: Int
)

/**
 * Doğrulanmış bir yedeğin geri yüklenmesini yönetir.
 *
 * 1. Fotoğrafları uygulama depolamasına hazırlar.
 * 2. Yeni fotoğraf yollarını Room verilerine yazar.
 * 3. Veritabanını tek transaction ile değiştirir.
 * 4. Hata halinde kullanılmayan yeni dosyaları temizler.
 *
 * Eski fotoğrafları doğrudan silmez.
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository
) {

    private val restoreMutex = Mutex()

    private val safeIdRegex =
        Regex("[A-Za-z0-9_-]{1,128}")

    private val extensionRegex =
        Regex("[a-z0-9]{1,8}")

    private companion object {
        const val MAX_IMAGE_BYTES =
            100L * 1024L * 1024L
    }

    /**
     * BackupArchiveReader tarafından
     * doğrulanmış yedeği geri yükler.
     *
     * Bu işlem mevcut Room kayıtlarının
     * yerine yedekteki kayıtları koyar.
     */
    suspend fun restore(
        preparedBackup: PreparedBackup
    ): RestoreSummary = restoreMutex.withLock {

        withContext(Dispatchers.IO) {

            val installedFiles = mutableListOf<File>()

            try {
                val originalData = preparedBackup.data

                val mediaIndex = preparedBackup.manifest
                    .mediaFiles
                    .associateBy { it.archivePath }

                // 1. Sezon fotoğraflarını hazırla.
                val restoredSeasons =
                    originalData.seasons.map { season ->

                        requireSafeId(season.id)

                        val coverPath = installImage(
                            preparedBackup = preparedBackup,
                            mediaIndex = mediaIndex,
                            archivePath = season.coverImagePath,
                            expectedBase =
                                "media/seasons/${season.id}/cover",
                            destinationFolder = "seasons",
                            role = "season",
                            installedFiles = installedFiles
                        )

                        season.copy(
                            coverImagePath = coverPath
                        )
                    }

                // 2. Kişi fotoğraflarını hazırla.
                val restoredPeople =
                    originalData.people.map { person ->

                        requireSafeId(person.id)

                        val profilePath = installImage(
                            preparedBackup = preparedBackup,
                            mediaIndex = mediaIndex,
                            archivePath = person.profileImagePath,
                            expectedBase =
                                "media/people/${person.id}/profile",
                            destinationFolder =
                                "people/${person.id}",
                            role = "profile",
                            installedFiles = installedFiles
                        )

                        val coverPath = installImage(
                            preparedBackup = preparedBackup,
                            mediaIndex = mediaIndex,
                            archivePath = person.coverImagePath,
                            expectedBase =
                                "media/people/${person.id}/cover",
                            destinationFolder =
                                "people/${person.id}",
                            role = "cover",
                            installedFiles = installedFiles
                        )

                        person.copy(
                            profileImagePath = profilePath,
                            coverImagePath = coverPath
                        )
                    }

                // 3. Taşınabilir yollar yerine
                // cihazın yeni fotoğraf yollarını koy.
                val restoredData = originalData.copy(
                    seasons = restoredSeasons,
                    people = restoredPeople
                )

                // 4. Room kayıtlarını atomik değiştir.
                backupRepository
                    .replaceDatabaseWithVerifiedBackup(
                        restoredData
                    )

                // 5. Sonuç oluştur.
                RestoreSummary(
                    journalCount = restoredData.journals.size,
                    personCount = restoredData.people.size,
                    seasonCount = restoredData.seasons.size,
                    episodeCount = restoredData.episodes.size,
                    mentionCount = restoredData.mentions.size,
                    specialMemoryCount =
                        restoredData.specialMemories.size,
                    imageCount = installedFiles.size
                )

            } catch (exception: Exception) {

                // İşlem hata verirse yalnızca bu işlemde
                // oluşturulan ve Room tarafından
                // kullanılmayan dosyaları temizle.
                //
                // DB okunamazsa dosyaları koru.
                withContext(
                    NonCancellable + Dispatchers.IO
                ) {
                    runCatching {
                        cleanupUnreferencedFiles(
                            installedFiles
                        )
                    }
                }

                throw exception
            }
        }
    }

    /**
     * Doğrulanmış bir görseli yeni dosyaya kopyalar.
     *
     * Önce .part dosyası hazırlanır.
     * Tamamlanınca kalıcı dosya adına taşınır.
     */
    private suspend fun installImage(
        preparedBackup: PreparedBackup,
        mediaIndex: Map<String, BackupMediaFile>,
        archivePath: String?,
        expectedBase: String,
        destinationFolder: String,
        role: String,
        installedFiles: MutableList<File>
    ): String? {

        if (archivePath == null) return null

        require(archivePath.startsWith("$expectedBase.")) {
            "Görsel referansı beklenen kayıtla uyuşmuyor."
        }

        val extension =
            archivePath.substringAfterLast('.')

        require(extensionRegex.matches(extension)) {
            "Geçersiz fotoğraf uzantısı."
        }

        val media = mediaIndex[archivePath]
            ?: throw IOException(
                "Fotoğraf manifest içerisinde bulunamadı."
            )

        require(
            media.sizeBytes in 1..MAX_IMAGE_BYTES
        ) {
            "Fotoğraf boyutu geçersiz."
        }

        val sourceFile =
            preparedBackup.stagedImageFile(
                archivePath
            )

        val filesRoot = context.filesDir.canonicalFile

        val destinationDirectory = File(
            filesRoot,
            destinationFolder
        ).canonicalFile

        require(
            destinationDirectory.path.startsWith(
                filesRoot.path + File.separator
            )
        ) {
            "Fotoğraf hedef klasörü güvenli değil."
        }

        if (!destinationDirectory.exists() &&
            !destinationDirectory.mkdirs()
        ) {
            throw IOException(
                "Fotoğraf klasörü oluşturulamadı."
            )
        }

        val destinationFile = File(
            destinationDirectory,
            "bd_restore_${UUID.randomUUID()}_$role.$extension"
        ).canonicalFile

        require(
            destinationFile.parentFile == destinationDirectory
        ) {
            "Fotoğraf hedef yolu geçersiz."
        }

        require(!destinationFile.exists()) {
            "Fotoğraf dosya adı çakışması."
        }

        val temporaryFile = File.createTempFile(
            "bd_restore_",
            ".part",
            destinationDirectory
        )

        try {
            val digest =
                MessageDigest.getInstance("SHA-256")

            var copiedBytes = 0L

            sourceFile.inputStream().buffered().use { input ->

                FileOutputStream(temporaryFile).use { fileOutput ->

                    BufferedOutputStream(fileOutput).use { output ->

                        val buffer =
                            ByteArray(DEFAULT_BUFFER_SIZE)

                        while (true) {
                            currentCoroutineContext().ensureActive()

                            val count = input.read(buffer)

                            if (count == -1) break
                            if (count == 0) continue

                            if (
                                count.toLong() >
                                media.sizeBytes - copiedBytes
                            ) {
                                throw IOException(
                                    "Fotoğraf boyutu beklenenden büyük."
                                )
                            }

                            output.write(buffer, 0, count)

                            digest.update(
                                buffer,
                                0,
                                count
                            )

                            copiedBytes += count
                        }

                        output.flush()
                        fileOutput.fd.sync()
                    }
                }
            }

            require(copiedBytes == media.sizeBytes) {
                "Fotoğraf eksik kopyalandı."
            }

            val actualHash =
                digest.digest().joinToString("") {
                    "%02x".format(it.toInt() and 0xff)
                }

            require(actualHash == media.sha256) {
                "Fotoğraf bütünlük kontrolü başarısız."
            }

            if (!temporaryFile.renameTo(destinationFile)) {
                throw IOException(
                    "Fotoğraf kalıcı konuma taşınamadı."
                )
            }

            installedFiles.add(destinationFile)

            return destinationFile.absolutePath

        } finally {
            temporaryFile.delete()
        }
    }

    private fun requireSafeId(id: String) {
        require(safeIdRegex.matches(id)) {
            "Geçersiz kayıt kimliği: $id"
        }
    }

    /**
     * Yalnızca başarısız geri yüklemenin
     * oluşturduğu ve Room'da kullanılmayan
     * görselleri silmeye çalışır.
     */
    private suspend fun cleanupUnreferencedFiles(
        installedFiles: List<File>
    ) {
        if (installedFiles.isEmpty()) return

        val currentData =
            backupRepository.createBackupData()

        val referencedPaths = mutableSetOf<String>()

        currentData.seasons.forEach { season ->
            season.coverImagePath?.let {
                referencedPaths.add(it)
            }
        }

        currentData.people.forEach { person ->

            person.profileImagePath?.let {
                referencedPaths.add(it)
            }

            person.coverImagePath?.let {
                referencedPaths.add(it)
            }
        }

        installedFiles.forEach { file ->
            if (file.absolutePath !in referencedPaths) {
                file.delete()
            }
        }
    }
}
