package com.loc.benimdunyam.data.local.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fotoğraflar Room satırları yerine uygulama içi dosyalarda saklanır.
 * Kaydedilmiş anıların fotoğrafları: benim_dunyam_journal_photos/
 * Henüz kaydedilmemiş editör fotoğrafları: benim_dunyam_journal_photo_drafts/
 *
 * ÖNEMLİ: Mevcut .bdbackup formatı bu iki klasörü henüz yedeklemiyor.
 */
data class JournalPhoto(
    val id: String,
    val absolutePath: String,
    val addedAt: Long
)

@Singleton
class JournalPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writeCoordinator: BackupWriteCoordinator
) {
    companion object {
        const val MAX_PHOTOS_PER_JOURNAL = 12
        const val MAX_FILE_SIZE_BYTES = 12L * 1024 * 1024
        private const val MAX_PIXEL_COUNT = 60_000_000L
        private val VALID_FILE = Regex("^([0-9]{13})_[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
    }

    private val publishedRoot: File
        get() = File(context.filesDir, "benim_dunyam_journal_photos")

    private val draftRoot: File
        get() = File(context.filesDir, "benim_dunyam_journal_photo_drafts")

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun publishedDir(journalId: String): File {
        require(journalId.isNotBlank()) { "Günlük kimliği boş olamaz." }
        return File(publishedRoot, hash(journalId))
    }

    private fun draftDir(draftKey: String): File {
        require(draftKey.isNotBlank()) { "Taslak anahtarı boş olamaz." }
        return File(draftRoot, hash(draftKey))
    }

    private fun validFiles(dir: File): List<File> = dir.listFiles().orEmpty()
        .filter { it.isFile && VALID_FILE.matches(it.name) }
        .sortedByDescending { it.name }

    private fun toPhoto(file: File) = JournalPhoto(
        id = file.name,
        absolutePath = file.absolutePath,
        addedAt = file.name.take(13).toLongOrNull() ?: file.lastModified()
    )

    suspend fun list(journalId: String): List<JournalPhoto> = withContext(Dispatchers.IO) {
        validFiles(publishedDir(journalId)).map(::toPhoto)
    }

    suspend fun listDraft(draftKey: String): List<JournalPhoto> = withContext(Dispatchers.IO) {
        validFiles(draftDir(draftKey)).map(::toPhoto)
    }

    /** Eski detay galerisi API'si; geriye uyumludur. */
    suspend fun importPhoto(journalId: String, source: Uri): JournalPhoto =
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = publishedDir(journalId)
                require(validFiles(dir).size < MAX_PHOTOS_PER_JOURNAL) {
                    "Bir anıya en fazla $MAX_PHOTOS_PER_JOURNAL fotoğraf eklenebilir."
                }
                copyAndValidatePhoto(source, dir)
            }
        }

    /**
     * Editörde seçilen fotoğraf anı henüz kaydedilmemiş olsa bile hemen diske yazılır.
     * Edit modunda mevcut fotoğraf sayısı da 12 fotoğraf sınırına dahildir.
     */
    suspend fun importDraftPhoto(
        draftKey: String,
        source: Uri,
        editingJournalId: String?
    ): JournalPhoto = writeCoordinator.withRegularWrite {
        withContext(Dispatchers.IO) {
            val savedCount = editingJournalId?.let { validFiles(publishedDir(it)).size } ?: 0
            val stagedCount = validFiles(draftDir(draftKey)).size
            require(savedCount + stagedCount < MAX_PHOTOS_PER_JOURNAL) {
                "Bir anıya en fazla $MAX_PHOTOS_PER_JOURNAL fotoğraf eklenebilir."
            }
            copyAndValidatePhoto(source, draftDir(draftKey))
        }
    }

    private fun copyAndValidatePhoto(source: Uri, dir: File): JournalPhoto {
        val mime = context.contentResolver.getType(source)?.lowercase()
        val extension = when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException(
                "Şimdilik yalnızca JPEG, PNG ve WebP fotoğraflar destekleniyor."
            )
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Fotoğraf klasörü oluşturulamadı.")
        }
        val name = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
        val destination = File(dir, name)
        val temp = File(dir, "$name.part")
        try {
            val sourceStream = context.contentResolver.openInputStream(source)
                ?: throw IOException("Fotoğraf açılamadı.")
            sourceStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        require(total <= MAX_FILE_SIZE_BYTES) {
                            "Fotoğraf en fazla 12 MB olabilir."
                        }
                        output.write(buffer, 0, read)
                    }
                    require(total > 0) { "Fotoğraf dosyası boş." }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && pixels in 1..MAX_PIXEL_COUNT) {
                "Fotoğraf geçersiz veya çözünürlüğü çok yüksek."
            }
            if (!temp.renameTo(destination)) {
                throw IOException("Fotoğraf kalıcı klasöre taşınamadı.")
            }
            return toPhoto(destination)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    suspend fun delete(journalId: String, photoId: String) {
        require(VALID_FILE.matches(photoId)) { "Geçersiz fotoğraf kimliği." }
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val target = File(publishedDir(journalId), photoId)
                if (target.exists() && !target.delete()) throw IOException("Fotoğraf silinemedi.")
            }
        }
    }

    suspend fun deleteDraftPhoto(draftKey: String, photoId: String) {
        require(VALID_FILE.matches(photoId)) { "Geçersiz fotoğraf kimliği." }
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val target = File(draftDir(draftKey), photoId)
                if (target.exists() && !target.delete()) throw IOException("Taslak fotoğrafı silinemedi.")
            }
        }
    }

    /**
     * Yeni anının kimliğini diske yazar. Kaydetme yarıda kesilirse aynı ID ile
     * tekrar denemeye izin verir; mükerrer JournalEntry oluşma riskini azaltır.
     */
    suspend fun getOrCreatePublicationId(draftKey: String): String =
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = draftDir(draftKey)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("Taslak klasörü açılamadı.")
                val marker = File(dir, ".publication_id")
                if (marker.isFile) {
                    val existing = marker.readText().trim()
                    require(runCatching { UUID.fromString(existing) }.isSuccess) {
                        "Taslağın kayıt kimliği geçersiz. Veri güvenliği için işlem durduruldu."
                    }
                    existing
                } else {
                    val fresh = UUID.randomUUID().toString()
                    val tmp = File(dir, ".publication_id.part")
                    try {
                        tmp.writeText(fresh)
                        if (!tmp.renameTo(marker)) throw IOException("Taslak kimliği kaydedilemedi.")
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                    fresh
                }
            }
        }

    /**
     * Günlük Room'a BAŞARIYLA kaydedildikten sonra çağrılır.
     * Aynı filesDir üzerinde rename kullanılır. Kısmi başarısızlıkta kalan
     * fotoğraflar taslakta kalır; tekrar kaydet ile işlem sürdürülebilir.
     */
    suspend fun publishDraftPhotos(draftKey: String, journalId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val sourceDir = draftDir(draftKey)
                val sources = validFiles(sourceDir)
                if (sources.isEmpty()) return@withContext
                val targetDir = publishedDir(journalId)
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    throw IOException("Anı fotoğraf klasörü oluşturulamadı.")
                }
                require(validFiles(targetDir).size + sources.size <= MAX_PHOTOS_PER_JOURNAL) {
                    "Fotoğraf sayısı 12 sınırını aşıyor."
                }
                for (photo in sources) {
                    val target = File(targetDir, photo.name)
                    if (target.exists()) {
                        throw IOException("Aynı isimli fotoğraf zaten var; üzerine yazılmadı.")
                    }
                    if (!photo.renameTo(target)) {
                        throw IOException("Fotoğraf anıya taşınamadı: ${photo.name}")
                    }
                }
            }
        }
    }

    /** Yalnızca taslak silindiğinde veya yayınlama tamamen bitince çağrılır. */
    suspend fun discardDraftPhotos(draftKey: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = draftDir(draftKey)
                if (dir.exists() && !dir.deleteRecursively()) {
                    throw IOException("Taslak fotoğrafları silinemedi.")
                }
            }
        }
    }

    suspend fun deleteAllForJournal(journalId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = publishedDir(journalId)
                if (dir.exists() && !dir.deleteRecursively()) {
                    throw IOException("Günlük fotoğrafları temizlenemedi.")
                }
            }
        }
    }

    /** Kullanıcı 'Tüm verileri sıfırla' dediğinde kullanılır. */
    suspend fun deleteAll() {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                for (dir in listOf(publishedRoot, draftRoot)) {
                    if (dir.exists() && !dir.deleteRecursively()) {
                        throw IOException("Günlük fotoğrafları temizlenemedi.")
                    }
                }
            }
        }
    }
}
