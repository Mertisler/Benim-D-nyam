package com.loc.benimdunyam.feature.seasons.episodes

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Galeriden seçilen fotoğrafı uygulamanın özel depolama alanına kopyalar.
 * Dönen mutlak dosya yolu LifeSeason.coverImagePath alanında saklanabilir.
 */
internal fun copySeasonCoverToInternalStorage(
    context: Context,
    sourceUri: Uri
): String {

    val seasonDirectory = File(context.filesDir, "seasons")

    if (!seasonDirectory.exists() && !seasonDirectory.mkdirs()) {
        throw IOException("Fotoğraf klasörü oluşturulamadı.")
    }

    val mimeType = context.contentResolver.getType(sourceUri)
    val extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType)
        ?: "img"

    val destinationFile = File(
        seasonDirectory,
        "season_${UUID.randomUUID()}.$extension"
    )

    try {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Seçilen fotoğraf açılamadı.")

        inputStream.use { input ->
            destinationFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }

        if (!destinationFile.exists() || destinationFile.length() == 0L) {
            throw IOException("Fotoğraf dosyası boş oluşturuldu.")
        }

        return destinationFile.absolutePath
    } catch (exception: Exception) {
        destinationFile.delete()
        throw exception
    }
}

/**
 * Yalnızca uygulamanın files/seasons klasöründeki geçici fotoğrafı siler.
 * content:// veya uygulama klasörü dışındaki yolları silmez.
 */
internal fun deleteSeasonCoverFromInternalStorage(
    context: Context,
    path: String?
) {
    if (path.isNullOrBlank()) return

    try {
        val seasonDirectory = File(context.filesDir, "seasons").canonicalFile
        val imageFile = File(path).canonicalFile

        if (imageFile.parentFile == seasonDirectory) {
            imageFile.delete()
        }
    } catch (_: Exception) {
        // Temizlik başarısız olsa da ekranın çalışması engellenmez.
    }
}

/**
 * Yeni mutlak dosya yollarını ve eski content:// URI kayıtlarını destekler.
 */
fun seasonCoverImageModel(path: String?): Any? {
    if (path.isNullOrBlank()) return null

    val value = path.trim()

    return when {
        value.startsWith("content://", ignoreCase = true) ||
                value.startsWith("file://", ignoreCase = true) -> Uri.parse(value)

        else -> File(value)
    }
}
