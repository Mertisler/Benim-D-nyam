package com.loc.benimdunyam.data.local.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Photo bytes live in app-private files. Ordering/cover metadata lives beside them. */
data class JournalPhoto(
    val id: String,
    val absolutePath: String,
    val addedAt: Long,
    val isCover: Boolean = false,
    val isDraft: Boolean = false
)

data class JournalEditorPhotoSnapshot(
    val photos: List<JournalPhoto>,
    val savedPhotos: List<JournalPhoto>,
    val draftPhotos: List<JournalPhoto>,
    val coverId: String?
)

private data class PhotoLayout(
    val order: List<String>,
    val coverId: String?
)

/**
 * WARNING: .bdbackup currently does NOT include these journal photo files.
 * Kept independent of Room to avoid schema/migration and backup-format changes.
 */
@Singleton
class JournalPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writeCoordinator: BackupWriteCoordinator
) {
    companion object {
        const val MAX_PHOTOS_PER_JOURNAL = 12
        const val MAX_FILE_SIZE_BYTES = 12L * 1024 * 1024
        private const val MAX_PIXEL_COUNT = 60_000_000L
        private const val LAYOUT_FILE = ".journal_photo_layout.json"
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

    // The old store presented newest-first. Preserve that order when no layout exists.
    private fun validFiles(dir: File): List<File> = dir.listFiles().orEmpty()
        .filter { it.isFile && VALID_FILE.matches(it.name) }
        .sortedByDescending { it.name }

    private fun toPhoto(file: File, isCover: Boolean = false, isDraft: Boolean = false) =
        JournalPhoto(
            id = file.name,
            absolutePath = file.absolutePath,
            addedAt = file.name.take(13).toLongOrNull() ?: file.lastModified(),
            isCover = isCover,
            isDraft = isDraft
        )

    private fun readLayout(dir: File): PhotoLayout? {
        val file = File(dir, LAYOUT_FILE)
        if (!file.isFile) return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        require(json.optInt("version") == 1) { "Fotoğraf sıralama verisi sürümü desteklenmiyor." }
        val array = json.getJSONArray("order")
        val order = (0 until array.length()).map { array.getString(it) }
            .filter { VALID_FILE.matches(it) }.distinct()
        val coverId = json.optString("coverId").takeIf { VALID_FILE.matches(it) }
        return PhotoLayout(order, coverId)
    }

    /** AtomicFile keeps the previous metadata if a write is interrupted. */
    private fun writeLayout(dir: File, layout: PhotoLayout) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Fotoğraf klasörü oluşturulamadı.")
        val json = JSONObject().apply {
            put("version", 1)
            put("order", JSONArray().apply { layout.order.forEach { put(it) } })
            put("coverId", layout.coverId ?: JSONObject.NULL)
        }.toString()
        val atomic = AtomicFile(File(dir, LAYOUT_FILE))
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(json.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let(atomic::failWrite)
            throw error
        }
    }

    private fun orderedFiles(files: List<File>, storedOrder: List<String>): List<File> {
        val index = files.associateBy { it.name }
        val ids = JournalPhotoOrderRules.reconcile(files.map { it.name }, storedOrder)
        return ids.mapNotNull(index::get)
    }

    private fun publishedPhotosInternal(journalId: String): List<JournalPhoto> {
        val dir = publishedDir(journalId)
        val files = validFiles(dir)
        val layout = readLayout(dir)
        val order = orderedFiles(files, layout?.order.orEmpty())
        val coverId = JournalPhotoOrderRules.cover(layout?.coverId, order.map { it.name })
        return order.map { toPhoto(it, isCover = it.name == coverId) }
    }

    private fun editorSnapshotInternal(draftKey: String, editingJournalId: String?): JournalEditorPhotoSnapshot {
        val saved = editingJournalId?.let(::publishedPhotosInternal).orEmpty()
        val staged = validFiles(draftDir(draftKey)).map { toPhoto(it, isDraft = true) }
        val available = saved + staged
        val editLayout = readLayout(draftDir(draftKey))
        val defaultOrder = available.map { it.id }
        val ids = JournalPhotoOrderRules.reconcile(defaultOrder, editLayout?.order ?: defaultOrder)
        val coverId = JournalPhotoOrderRules.cover(
            editLayout?.coverId ?: saved.firstOrNull { it.isCover }?.id,
            ids
        )
        val photos = available.associateBy { it.id }
        val ordered = ids.mapNotNull { id -> photos[id]?.copy(isCover = id == coverId) }
        return JournalEditorPhotoSnapshot(
            photos = ordered,
            savedPhotos = ordered.filterNot { it.isDraft },
            draftPhotos = ordered.filter { it.isDraft },
            coverId = coverId
        )
    }

    suspend fun list(journalId: String): List<JournalPhoto> = withContext(Dispatchers.IO) {
        publishedPhotosInternal(journalId)
    }

    suspend fun coverPhoto(journalId: String): JournalPhoto? =
        list(journalId).firstOrNull { it.isCover }

    suspend fun listDraft(draftKey: String): List<JournalPhoto> = withContext(Dispatchers.IO) {
        validFiles(draftDir(draftKey)).map { toPhoto(it, isDraft = true) }
    }

    suspend fun editorSnapshot(draftKey: String, editingJournalId: String?): JournalEditorPhotoSnapshot =
        withContext(Dispatchers.IO) { editorSnapshotInternal(draftKey, editingJournalId) }

    suspend fun hasDraftPhotoLayout(draftKey: String): Boolean = withContext(Dispatchers.IO) {
        File(draftDir(draftKey), LAYOUT_FILE).isFile
    }

    /** Reordering while editing touches only the draft metadata, never published order. */
    suspend fun moveDraftPhoto(draftKey: String, editingJournalId: String?, photoId: String, delta: Int) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val snapshot = editorSnapshotInternal(draftKey, editingJournalId)
                val ids = snapshot.photos.map { it.id }
                require(photoId in ids) { "Fotoğraf bulunamadı." }
                writeLayout(draftDir(draftKey), PhotoLayout(
                    order = JournalPhotoOrderRules.move(ids, photoId, delta),
                    coverId = snapshot.coverId
                ))
            }
        }
    }

    suspend fun setDraftCover(draftKey: String, editingJournalId: String?, photoId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val snapshot = editorSnapshotInternal(draftKey, editingJournalId)
                val ids = snapshot.photos.map { it.id }
                require(photoId in ids) { "Kapak fotoğrafı bulunamadı." }
                writeLayout(draftDir(draftKey), PhotoLayout(ids, photoId))
            }
        }
    }

    /** Detail gallery changes apply immediately to an already saved journal. */
    suspend fun movePublishedPhoto(journalId: String, photoId: String, delta: Int) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val photos = publishedPhotosInternal(journalId)
                val ids = photos.map { it.id }
                require(photoId in ids) { "Fotoğraf bulunamadı." }
                writeLayout(publishedDir(journalId), PhotoLayout(
                    JournalPhotoOrderRules.move(ids, photoId, delta),
                    photos.firstOrNull { it.isCover }?.id
                ))
            }
        }
    }

    suspend fun setPublishedCover(journalId: String, photoId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val ids = publishedPhotosInternal(journalId).map { it.id }
                require(photoId in ids) { "Kapak fotoğrafı bulunamadı." }
                writeLayout(publishedDir(journalId), PhotoLayout(ids, photoId))
            }
        }
    }

    suspend fun importPhoto(journalId: String, source: Uri): JournalPhoto =
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val before = publishedPhotosInternal(journalId)
                require(before.size < MAX_PHOTOS_PER_JOURNAL) {
                    "Bir anıya en fazla $MAX_PHOTOS_PER_JOURNAL fotoğraf eklenebilir."
                }
                val added = copyAndValidatePhoto(source, publishedDir(journalId))
                try {
                    val order = before.map { it.id } + added.id
                    writeLayout(publishedDir(journalId), PhotoLayout(
                        order = order,
                        coverId = before.firstOrNull { it.isCover }?.id ?: added.id
                    ))
                } catch (error: Exception) {
                    File(added.absolutePath).delete()
                    throw error
                }
                added
            }
        }

    suspend fun importDraftPhoto(draftKey: String, source: Uri, editingJournalId: String?): JournalPhoto =
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val before = editorSnapshotInternal(draftKey, editingJournalId)
                require(before.photos.size < MAX_PHOTOS_PER_JOURNAL) {
                    "Bir anıya en fazla $MAX_PHOTOS_PER_JOURNAL fotoğraf eklenebilir."
                }
                val added = copyAndValidatePhoto(source, draftDir(draftKey))
                try {
                    writeLayout(draftDir(draftKey), PhotoLayout(
                        order = before.photos.map { it.id } + added.id,
                        coverId = before.coverId ?: added.id
                    ))
                } catch (error: Exception) {
                    File(added.absolutePath).delete()
                    throw error
                }
                added.copy(isDraft = true)
            }
        }

    private fun copyAndValidatePhoto(source: Uri, dir: File): JournalPhoto {
        val mime = context.contentResolver.getType(source)?.lowercase()
        val extension = when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("Yalnızca JPEG, PNG ve WebP fotoğraflar destekleniyor.")
        }
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Fotoğraf klasörü oluşturulamadı.")
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
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        require(total <= MAX_FILE_SIZE_BYTES) { "Fotoğraf en fazla 12 MB olabilir." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0L) { "Fotoğraf dosyası boş." }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && pixels in 1..MAX_PIXEL_COUNT) {
                "Fotoğraf geçersiz veya çözünürlüğü çok yüksek."
            }
            if (!temp.renameTo(destination)) throw IOException("Fotoğraf kalıcı klasöre taşınamadı.")
            return toPhoto(destination)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    suspend fun delete(journalId: String, photoId: String) {
        require(VALID_FILE.matches(photoId)) { "Geçersiz fotoğraf kimliği." }
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = publishedDir(journalId)
                val target = File(dir, photoId)
                if (target.exists() && !target.delete()) throw IOException("Fotoğraf silinemedi.")
                val rest = publishedPhotosInternal(journalId)
                if (rest.isNotEmpty()) {
                    writeLayout(dir, PhotoLayout(rest.map { it.id }, rest.firstOrNull { it.isCover }?.id))
                } else {
                    File(dir, LAYOUT_FILE).delete()
                }
            }
        }
    }

    suspend fun deleteDraftPhoto(draftKey: String, photoId: String) {
        require(VALID_FILE.matches(photoId)) { "Geçersiz fotoğraf kimliği." }
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val target = File(draftDir(draftKey), photoId)
                if (target.exists() && !target.delete()) throw IOException("Taslak fotoğrafı silinemedi.")
                // The draft layout is reconciled against remaining files on next read.
            }
        }
    }

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

    suspend fun publishDraftPhotos(draftKey: String, journalId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val sources = validFiles(draftDir(draftKey))
                if (sources.isEmpty()) return@withContext
                val targetDir = publishedDir(journalId)
                if (!targetDir.exists() && !targetDir.mkdirs()) throw IOException("Anı fotoğraf klasörü oluşturulamadı.")
                require(validFiles(targetDir).size + sources.size <= MAX_PHOTOS_PER_JOURNAL) {
                    "Fotoğraf sayısı 12 sınırını aşıyor."
                }
                for (photo in sources) {
                    val target = File(targetDir, photo.name)
                    if (target.exists()) throw IOException("Aynı isimli fotoğraf zaten var; üzerine yazılmadı.")
                    if (!photo.renameTo(target)) throw IOException("Fotoğraf anıya taşınamadı: ${photo.name}")
                }
            }
        }
    }

    /** Call AFTER publishDraftPhotos and BEFORE deleting the draft directory. */
    suspend fun applyDraftLayoutAfterPublish(draftKey: String, journalId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val draftLayout = readLayout(draftDir(draftKey))
                val previouslyPublished = readLayout(publishedDir(journalId))
                val published = publishedPhotosInternal(journalId)
                if (published.isEmpty()) return@withContext
                val available = published.map { it.id }
                val effective = JournalPhotoOrderRules.reconcile(
                    available,
                    draftLayout?.order ?: previouslyPublished?.order.orEmpty()
                )
                val cover = JournalPhotoOrderRules.cover(
                    draftLayout?.coverId ?: previouslyPublished?.coverId,
                    effective
                )
                writeLayout(publishedDir(journalId), PhotoLayout(effective, cover))
            }
        }
    }

    suspend fun discardDraftPhotos(draftKey: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = draftDir(draftKey)
                if (dir.exists() && !dir.deleteRecursively()) throw IOException("Taslak fotoğrafları silinemedi.")
            }
        }
    }

    suspend fun deleteAllForJournal(journalId: String) {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val dir = publishedDir(journalId)
                if (dir.exists() && !dir.deleteRecursively()) throw IOException("Günlük fotoğrafları temizlenemedi.")
            }
        }
    }

    suspend fun deleteAll() {
        writeCoordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                for (dir in listOf(publishedRoot, draftRoot)) {
                    if (dir.exists() && !dir.deleteRecursively()) throw IOException("Günlük fotoğrafları temizlenemedi.")
                }
            }
        }
    }
}
