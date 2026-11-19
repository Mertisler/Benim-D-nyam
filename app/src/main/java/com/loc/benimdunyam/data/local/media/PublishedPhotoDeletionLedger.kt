package com.loc.benimdunyam.data.local.media

import android.util.AtomicFile
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Kullanıcının cihazında açıkça sildiği yayınlanmış anı fotoğrafının kimlik kaydı. */
data class PublishedPhotoDeletion(
    val journalId: String,
    val photoId: String,
    val deletedAtMillis: Long
)

/**
 * Faz 2G.3: Fotoğraf silinmeden ÖNCE kalıcı silme işareti kaydedilir.
 * Bu defter yayınlanmış fotoğraf klasöründen AYRI tutulur; böylece anının
 * bütün fotoğrafları silinse bile işaret kaybolmaz. Hesap çalışma alanı
 * değiştiğinde başka bir çalışma alanının defteri okunmaz.
 *
 * Not: Fotoğraf dosyası silinemese dahi işaret korunur. Bu durum veri kaybı
 * değil, yanlışlıkla yeniden buluta gönderimi engelleyen güvenli bir duruştur.
 */
@Singleton
class PublishedPhotoDeletionLedger @Inject constructor(
    private val session: ActiveWorkspaceSession
) {
    private val lock = Mutex()
    private val photoIdPattern = Regex("^[0-9]{13}_[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
    private val journalIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

    private fun ledgerFile(): File = File(session.mediaRoot(), "bd_published_photo_deletions_v1.json")

    private fun requireSafe(journalId: String, photoId: String) {
        require(journalIdPattern.matches(journalId) && photoIdPattern.matches(photoId)) {
            "Anı veya fotoğraf kimliği güvenli biçimde tanınamadı."
        }
    }

    private fun read(): List<PublishedPhotoDeletion> {
        val file = ledgerFile()
        // Android AtomicFile, yarım kalan yazma sonrası .bak kopyasını
        // kurtarabilir. Bu yüzden yalnızca ana dosyanın yokluğuna bakmayız.
        val backup = File(file.absolutePath + ".bak")
        if (!file.isFile && !backup.isFile) return emptyList()
        require(file.length() <= 2_000_000 && backup.length() <= 2_000_000) {
            "Fotoğraf silme defteri beklenenden büyük."
        }
        // AtomicFile.openRead(), yarım kalan yazma sonrası eski sağlam
        // kopyayı kurtarabilir; doğrudan File.readText kullanılmaz.
        val text = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Silme defteri biçimi desteklenmiyor." }
        val array = root.getJSONArray("deletions")
        require(array.length() <= 5_000) { "Silme işareti güvenlik sınırı aşıldı." }
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val item = PublishedPhotoDeletion(
                journalId = row.getString("journalId"),
                photoId = row.getString("photoId"),
                deletedAtMillis = row.getLong("deletedAtMillis")
            )
            requireSafe(item.journalId, item.photoId)
            item
        }
    }

    private fun write(entries: List<PublishedPhotoDeletion>) {
        require(entries.size <= 5_000) { "Silme defteri sınırına ulaşıldı; fotoğraf silinmedi." }
        val file = ledgerFile()
        if (!file.parentFile.isDirectory && !file.parentFile.mkdirs()) {
            throw IOException("Silme defteri klasörü oluşturulamadı.")
        }
        val document = JSONObject().apply {
            put("version", 1)
            put("deletions", JSONArray().apply {
                entries.forEach { item ->
                    put(JSONObject().apply {
                        put("journalId", item.journalId)
                        put("photoId", item.photoId)
                        put("deletedAtMillis", item.deletedAtMillis)
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(document)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let(atomic::failWrite)
            throw error
        }
    }

    suspend fun record(journalId: String, photoId: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            requireSafe(journalId, photoId)
            val old = read()
            if (old.none { it.journalId == journalId && it.photoId == photoId }) {
                write(old + PublishedPhotoDeletion(journalId, photoId, System.currentTimeMillis()))
            }
        }
    }

    suspend fun contains(journalId: String, photoId: String): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            requireSafe(journalId, photoId)
            read().any { it.journalId == journalId && it.photoId == photoId }
        }
    }

    suspend fun list(): List<PublishedPhotoDeletion> = lock.withLock {
        withContext(Dispatchers.IO) { read().toList() }
    }

    /**
     * Faz 2I.3: Kimliği ve bütünlüğü doğrulanmış medya arşivinden gelen
     * silme işaretlerini EKSİLTMEDEN birleştirir. Hiçbir fotoğraf silmez.
     * Kullanıcı yanlışlıkla eski bir fotoğrafı yeniden canlandırmasın.
     */
    suspend fun mergeVerifiedArchive(items: List<PublishedPhotoDeletion>) = lock.withLock {
        withContext(Dispatchers.IO) {
            require(items.size <= 5_000) { "Silme işareti sınırı aşıldı." }
            items.forEach { item ->
                requireSafe(item.journalId, item.photoId)
                require(item.deletedAtMillis > 0) { "Silme zamanı geçersiz." }
            }
            val current = read()
            val keys = current.map { it.journalId to it.photoId }.toMutableSet()
            val merged = current.toMutableList()
            items.forEach { item ->
                if (keys.add(item.journalId to item.photoId)) merged += item
            }
            require(merged.size <= 5_000) { "Silme defteri kapasitesi aşıldı." }
            if (merged.size != current.size) write(merged)
        }
    }
}
