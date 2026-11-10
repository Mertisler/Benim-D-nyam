package com.loc.benimdunyam.feature.yearreview.video

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Kaydetme ve paylaşma yalnızca kullanıcının butona basmasıyla gerçekleşir. */
object YearMovieVideoExporter {
    fun saveToGallery(context: Context, mp4: File, year: Int): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Android 9 ve öncesinde sistem dosya seçicisini kullanın."
        }
        validate(mp4)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName(year))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BenimDunyam")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galeri kaydı açılamadı.")
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> mp4.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Galeriye MP4 yazılamadı.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) throw IOException("Galeri kaydı tamamlanamadı.")
            return uri
        } catch (e: Exception) {
            resolver.delete(uri,null,null)
            throw e
        }
    }

    fun saveToDocument(context: Context, mp4: File, uri: Uri) {
        validate(mp4)
        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            mp4.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("Seçilen dosyaya yazılamadı.")
    }

    /** Poster özelliğinin FileProvider'ını tekrar kullanır. Aynı authority ile ikinci provider eklenmez. */
    fun createShareIntent(context: Context, mp4: File, year: Int): Intent {
        validate(mp4)
        val dir = File(context.cacheDir,"year_movies_share")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Video paylaşım klasörü oluşturulamadı.")
        dir.listFiles()?.filter {
            it.isFile && System.currentTimeMillis() - it.lastModified() > 24*60*60*1000L
        }?.forEach { it.delete() }
        val target = File(dir,fileName(year))
        mp4.copyTo(target,overwrite = false)
        val uri = FileProvider.getUriForFile(
            context,"${context.packageName}.yearposter.fileprovider",target
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM,uri)
            clipData = ClipData.newUri(context.contentResolver,"Yılımın Filmi",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun fileName(year: Int): String = "BenimDunyam_YiliminFilmi_${year}_${System.currentTimeMillis()}.mp4"

    private fun validate(file: File) {
        if (!file.isFile || file.length() < 1024) throw IOException("Video dosyası eksik veya boş.")
    }
}
