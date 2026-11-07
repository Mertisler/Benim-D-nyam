
package com.loc.benimdunyam.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class StagedEncryptedBackup(
    val file: File,
    val sha256: String
)

@Singleton
class BackupDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        const val MAX_BYTES = 650L * 1024L * 1024L
    }

    /**
     * Kullanıcının seçtiği belgeyi özel geçici
     * depolama alanına kopyalar.
     */
    suspend fun stageEncryptedBackup(
        uri: Uri
    ): StagedEncryptedBackup = withContext(Dispatchers.IO) {

        val destination = createTemporaryFile()

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L

            val input = context.contentResolver
                .openInputStream(uri)
                ?: throw IOException("Yedek dosyası açılamadı.")

            input.use { source ->
                destination.outputStream().buffered().use { output ->

                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val count = source.read(buffer)
                        if (count == -1) break
                        if (count == 0) continue

                        if (count.toLong() > MAX_BYTES - total) {
                            throw IOException(
                                "Yedek dosyası boyut sınırını aşıyor."
                            )
                        }

                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        total += count
                    }
                }
            }

            if (total == 0L) {
                throw IOException("Seçilen yedek dosyası boş.")
            }

            StagedEncryptedBackup(
                file = destination,
                sha256 = digest.digest().toHexString()
            )

        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    /**
     * Şifreli dosyayı SAF üzerinden seçilen
     * konuma kaydeder ve tekrar okuyarak doğrular.
     */
    suspend fun saveEncryptedBackup(
        sourceFile: File,
        destinationUri: Uri
    ) = withContext(Dispatchers.IO) {

        require(sourceFile.isFile && sourceFile.length() > 0L) {
            "Kaydedilecek şifreli yedek bulunamadı."
        }

        require(sourceFile.length() <= MAX_BYTES) {
            "Şifreli yedek çok büyük."
        }

        val expectedDigest =
            MessageDigest.getInstance("SHA-256")

        val output = context.contentResolver
            .openOutputStream(destinationUri, "w")
            ?: throw IOException(
                "Seçilen konumda yedek dosyası oluşturulamadı."
            )

        var writtenBytes = 0L

        output.use { destination ->
            sourceFile.inputStream().buffered().use { source ->

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    currentCoroutineContext().ensureActive()

                    val count = source.read(buffer)
                    if (count == -1) break
                    if (count == 0) continue

                    destination.write(buffer, 0, count)
                    expectedDigest.update(buffer, 0, count)
                    writtenBytes += count
                }

                destination.flush()
            }
        }

        if (writtenBytes != sourceFile.length()) {
            throw IOException(
                "Yedek dosyası eksik kaydedildi."
            )
        }

        // Kaydetme işleminden sonra dosyayı
        // yeniden açarak içerik bütünlüğünü kontrol et.
        val actualDigest =
            MessageDigest.getInstance("SHA-256")

        val verifyInput = context.contentResolver
            .openInputStream(destinationUri)
            ?: throw IOException(
                "Kaydedilen yedek tekrar açılamadı."
            )

        var verifiedBytes = 0L

        verifyInput.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                currentCoroutineContext().ensureActive()

                val count = input.read(buffer)
                if (count == -1) break
                if (count == 0) continue

                if (count.toLong() > writtenBytes - verifiedBytes) {
                    throw IOException(
                        "Kaydedilen dosyanın boyutu uyuşmuyor."
                    )
                }

                actualDigest.update(buffer, 0, count)
                verifiedBytes += count
            }
        }

        if (
            verifiedBytes != writtenBytes ||
            !MessageDigest.isEqual(
                expectedDigest.digest(),
                actualDigest.digest()
            )
        ) {
            throw IOException(
                "Kaydedilen yedek doğrulanamadı."
            )
        }
    }

    private fun createTemporaryFile(): File {
        val directory = File(
            context.noBackupFilesDir,
            "backup_staging"
        )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Geçici yedek klasörü oluşturulamadı."
            )
        }

        return File.createTempFile(
            "bd_import_",
            ".bdbackup",
            directory
        )
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
}
