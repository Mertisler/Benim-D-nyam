package com.loc.benimdunyam.feature.yearreview.poster

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Yalnızca kullanıcı eylemiyle çağrılır; uygulama hiçbir posteri kendiliğinden paylaşmaz. */
object YearPosterExporter {
    fun saveToGallery(context: Context, bitmap: Bitmap, year: Int): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Android 9 ve öncesinde PNG dosyasını sistem dosya seçicisiyle kaydedin."
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename(year))
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/BenimDunyam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galeri kaydı oluşturulamadı.")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("PNG oluşturulamadı.")
                }
            } ?: throw IOException("Galeri dosyasına yazılamadı.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun saveToDocument(context: Context, bitmap: Bitmap, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IOException("PNG oluşturulamadı.")
            }
        } ?: throw IOException("Seçilen dosyaya yazılamadı.")
    }

    fun createShareIntent(context: Context, bitmap: Bitmap, year: Int): Intent {
        val dir = File(context.cacheDir, "year_posters")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Paylaşım dizini oluşturulamadı.")
        // Paylaşım hedefinin PNG'yi açması için bir günlük tolerans bırakılır.
        dir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
        val file = File(dir, filename(year))
        file.outputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw IOException("Paylaşılacak PNG oluşturulamadı.")
            }
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.yearposter.fileprovider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, "Yılın posteri", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun filename(year: Int): String = "BenimDunyam_${year}_${System.currentTimeMillis()}.png"
}
