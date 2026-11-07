package com.loc.benimdunyam.data.backup

import android.content.Context
import com.google.gson.Gson
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupArchiveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val backupValidator: BackupValidator
) {

    private val gson = Gson()

    companion object {
        private const val MAX_JSON_SIZE = 32L * 1024 * 1024
        private const val MAX_IMAGE_SIZE = 100L * 1024 * 1024
        private const val MAX_TOTAL_MEDIA_SIZE = 500L * 1024 * 1024
        private const val MAX_MEDIA_COUNT = 2000
    }

    /**
     * Geçici, henüz ŞİFRELENMEMİŞ ZIP oluşturur.
     *
     * Dönen dosya kullanıcıya doğrudan verilmemelidir.
     * Şifreleme tamamlandığında çağıran taraf
     * bu geçici dosyayı mutlaka silmelidir.
     */
    suspend fun createTemporaryArchive(): File =
        withContext(Dispatchers.IO) {

            // 1. Room verilerinin tutarlı görüntüsünü al.
            val originalData =
                backupRepository.createBackupData()

            require(originalData.version == 2) {
                "Desteklenmeyen yedek veri sürümü."
            }

            // ZIP yolu -> cihazdaki gerçek görsel dosyası
            val images = linkedMapOf<String, File>()

            // 2. Mutlak görsel yollarını taşınabilir
            // ZIP içi göreli yollara dönüştür.
            val portableSeasons =
                originalData.seasons.map { season ->

                    requireSafeId(season.id)

                    val portablePath = registerImage(
                        rawPath = season.coverImagePath,
                        localFolder = "seasons",
                        archiveBase = "media/seasons/${season.id}/cover",
                        images = images
                    )

                    season.copy(coverImagePath = portablePath)
                }

            val portablePeople =
                originalData.people.map { person ->

                    requireSafeId(person.id)

                    val profilePath = registerImage(
                        rawPath = person.profileImagePath,
                        localFolder = "people/${person.id}",
                        archiveBase = "media/people/${person.id}/profile",
                        images = images
                    )

                    val coverPath = registerImage(
                        rawPath = person.coverImagePath,
                        localFolder = "people/${person.id}",
                        archiveBase = "media/people/${person.id}/cover",
                        images = images
                    )

                    person.copy(
                        profileImagePath = profilePath,
                        coverImagePath = coverPath
                    )
                }

            val portableData = originalData.copy(
                seasons = portableSeasons,
                people = portablePeople
            )

            // 3. JSON oluştur ve mevcut doğrulayıcıdan geçir.
            val json = gson.toJson(portableData)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)

            require(jsonBytes.size.toLong() <= MAX_JSON_SIZE) {
                "Yedek verileri boyut sınırını aşıyor."
            }

            backupValidator.parseAndValidate(json)

            require(images.size <= MAX_MEDIA_COUNT) {
                "Yedeklenecek görsel sayısı sınırı aşıyor."
            }

            // 4. Uygulamanın özel geçici alanını hazırla.
            val temporaryDirectory = File(
                context.noBackupFilesDir,
                "backup_staging"
            )

            if (!temporaryDirectory.exists() &&
                !temporaryDirectory.mkdirs()
            ) {
                throw IOException(
                    "Geçici yedek klasörü oluşturulamadı."
                )
            }

            val archiveFile = File.createTempFile(
                "benim_dunyam_",
                ".zip",
                temporaryDirectory
            )

            try {
                val mediaManifest =
                    mutableListOf<BackupMediaFile>()

                var totalMediaSize = 0L

                // 5. ZIP dosyasını oluştur.
                ZipOutputStream(
                    BufferedOutputStream(
                        archiveFile.outputStream()
                    )
                ).use { zip ->

                    // Room verileri
                    writeBytes(
                        zip = zip,
                        entryName = "data/backup.json",
                        bytes = jsonBytes
                    )

                    // Sezon ve kişi fotoğrafları
                    for ((archivePath, sourceFile) in images) {

                        val imageInfo = writeImage(
                            zip = zip,
                            archivePath = archivePath,
                            sourceFile = sourceFile
                        )

                        totalMediaSize += imageInfo.sizeBytes

                        require(
                            totalMediaSize <= MAX_TOTAL_MEDIA_SIZE
                        ) {
                            "Toplam görsel boyutu sınırı aşıyor."
                        }

                        mediaManifest.add(imageInfo)
                    }

                    // 6. Arşiv manifest dosyası
                    val manifest = BackupArchiveManifest(
                        createdAt = portableData.exportTimestamp,
                        dataSizeBytes = jsonBytes.size.toLong(),
                        dataSha256 = sha256(jsonBytes),
                        mediaFiles = mediaManifest
                    )

                    writeBytes(
                        zip = zip,
                        entryName = "manifest.json",
                        bytes = gson.toJson(manifest)
                            .toByteArray(Charsets.UTF_8)
                    )
                }

                if (archiveFile.length() == 0L) {
                    throw IOException(
                        "ZIP dosyası boş oluşturuldu."
                    )
                }

                // Şifreleme katmanının kullanacağı dosya.
                archiveFile

            } catch (exception: Exception) {
                archiveFile.delete()
                throw exception
            }
        }

    /**
     * Fotoğrafı yalnızca beklenen uygulama içi
     * klasörde bulunuyorsa arşive kaydeder.
     */
    private fun registerImage(
        rawPath: String?,
        localFolder: String,
        archiveBase: String,
        images: MutableMap<String, File>
    ): String? {

        if (rawPath == null) return null

        require(rawPath.isNotBlank()) {
            "Boş bir fotoğraf yolu bulundu."
        }

        require(!rawPath.startsWith("content://", true)) {
            "Eski galeri URI'si bulundu. " +
                    "Fotoğraf önce uygulama içine taşınmalı."
        }

        val allowedDirectory = File(
            context.filesDir,
            localFolder
        ).canonicalFile

        val sourceFile = File(rawPath).canonicalFile

        require(
            sourceFile.isFile &&
                    sourceFile.parentFile == allowedDirectory
        ) {
            "Fotoğraf uygulamanın beklenen " +
                    "klasöründe bulunamadı: $localFolder"
        }

        require(
            sourceFile.length() in 1..MAX_IMAGE_SIZE
        ) {
            "Fotoğraf boş veya boyutu çok büyük."
        }

        val extension = sourceFile.extension
            .lowercase(Locale.ROOT)
            .takeIf {
                it.matches(Regex("[a-z0-9]{1,8}"))
            } ?: "img"

        val archivePath = "$archiveBase.$extension"

        require(!images.containsKey(archivePath)) {
            "ZIP içerisinde tekrarlanan görsel yolu var."
        }

        images[archivePath] = sourceFile

        return archivePath
    }

    /**
     * ZIP dosyasına JSON gibi küçük veri ekler.
     */
    private fun writeBytes(
        zip: ZipOutputStream,
        entryName: String,
        bytes: ByteArray
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    /**
     * Fotoğrafı belleğe tamamen yüklemeden
     * parça parça ZIP dosyasına aktarır.
     *
     * Aynı sırada SHA-256 hesaplar.
     */
    private fun writeImage(
        zip: ZipOutputStream,
        archivePath: String,
        sourceFile: File
    ): BackupMediaFile {

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        val originalSize = sourceFile.length()

        zip.putNextEntry(ZipEntry(archivePath))

        sourceFile.inputStream().buffered().use { input ->

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {

                val count = input.read(buffer)

                if (count == -1) break
                if (count == 0) continue

                totalBytes += count

                if (totalBytes > MAX_IMAGE_SIZE) {
                    throw IOException(
                        "Fotoğraf boyutu sınırı aşıldı."
                    )
                }

                digest.update(buffer, 0, count)
                zip.write(buffer, 0, count)
            }
        }

        zip.closeEntry()

        if (totalBytes == 0L || totalBytes != originalSize) {
            throw IOException(
                "Fotoğraf yedeklenirken değişti veya boş kaldı."
            )
        }

        return BackupMediaFile(
            archivePath = archivePath,
            sizeBytes = totalBytes,
            sha256 = toHex(digest.digest())
        )
    }

    private fun requireSafeId(id: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
            "Güvensiz veya geçersiz kayıt kimliği bulundu."
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return toHex(
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
        )
    }

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}
