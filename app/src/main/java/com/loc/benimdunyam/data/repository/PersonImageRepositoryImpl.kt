package com.loc.benimdunyam.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.loc.benimdunyam.domain.model.PersonImageType
import com.loc.benimdunyam.domain.repository.PersonImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PersonImageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PersonImageRepository {

    override suspend fun saveImage(
        personId: String,
        sourceUri: String,
        imageType: PersonImageType
    ): String = withContext(Dispatchers.IO) {

        val uri = Uri.parse(sourceUri)
        val mimeType = context.contentResolver.getType(uri)

        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw IllegalArgumentException("Seçilen dosya bir görsel değil.")
        }

        val safePersonId = personId.filter {
            it.isLetterOrDigit() || it == '-' || it == '_'
        }
        if (safePersonId.isBlank()) {
            throw IllegalArgumentException("Geçersiz kişi kimliği.")
        }

        val peopleRoot = File(context.filesDir, "people")
        val personDirectory = File(peopleRoot, safePersonId)

        if (!personDirectory.exists() && !personDirectory.mkdirs()) {
            throw IOException("Kişi görsel klasörü oluşturulamadı.")
        }

        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "img"

        val destination = File(
            personDirectory,
            "${imageType.filePrefix}_${UUID.randomUUID()}.$extension"
        )

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Seçilen görsel açılamadı.")

            input.use { inputStream ->
                destination.outputStream().buffered().use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L

                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read < 0) break

                        totalBytes += read
                        if (totalBytes > MAX_IMAGE_SIZE_BYTES) {
                            throw IOException("Görsel boyutu 25 MB sınırını aşıyor.")
                        }

                        outputStream.write(buffer, 0, read)
                    }
                }
            }

            if (!destination.exists() || destination.length() == 0L) {
                throw IOException("Görsel dosyası boş veya kaydedilemedi.")
            }

            destination.absolutePath
        } catch (exception: Exception) {
            destination.delete()
            throw exception
        }
    }

    override suspend fun deleteImage(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext

        runCatching {
            val peopleRoot = File(context.filesDir, "people").canonicalFile
            val imageFile = File(path).canonicalFile
            val allowedPrefix = peopleRoot.path + File.separator

            if (imageFile.path.startsWith(allowedPrefix)) {
                imageFile.delete()
            }
        }
        Unit
    }

    private companion object {
        const val MAX_IMAGE_SIZE_BYTES = 25L * 1024L * 1024L
    }
}
