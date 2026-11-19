package com.loc.benimdunyam.data.sync.media

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.media.PublishedPhotoDeletion
import com.loc.benimdunyam.data.local.media.PublishedPhotoDeletionLedger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Bulut dosyaları silinmez: şifreli silme işaretleri oluşturulur. */
data class MediaDeletionPublishResult(val published: Int, val alreadyPublished: Int, val inspected: Int)

data class MediaDeletionPreview(
    val remoteMarkers: Int,
    val stillOnThisDevice: Int,
    val pendingLocalMarkers: Int,
    val examples: List<String>
)

/**
 * Faz 2G.3: Silme olayı ayrı ve DEĞİŞTİRİLEMEZ Firestore belgesidir.
 * Kimlik fotoğraf adı + anı kimliğinden üretilir; dosya adları ve içerik
 * Firestore'da açık metin değildir. Metadata hash'leri kimliği kanıtlamaz;
 * bu nedenle AES-GCM çözülen içerik de ayrıca doğrulanır.
 *
 * Firebase Storage'dan fiziksel dosya silme bu sınıfta YOKTUR.
 */
@Singleton
class EncryptedMediaDeletionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val ledger: PublishedPhotoDeletionLedger,
    private val photos: JournalPhotoStore,
    private val sha: EncryptedMediaCodec,
    private val crypto: EncryptedPhotoLayoutCodec
) {
    private val prefs = context.getSharedPreferences("bd_media_deletion_upload_v1", Context.MODE_PRIVATE)
    private val shaPattern = Regex("^[0-9a-f]{64}$")

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Medya silme kontrolünde Firebase hesabı veya yerel çalışma alanı değişti."
        }
    }

    private fun collection(uid: String) = firestore.collection("users")
        .document(uid).collection("encrypted_media_deletions")

    private fun markerId(uid: String, journalId: String, photoId: String): String =
        sha.sha256("$uid|MEDIA_DELETE|JOURNAL_PHOTO|$journalId|$photoId")

    private fun ownerHash(uid: String, journalId: String) =
        sha.sha256("$uid|JOURNAL_PHOTO|$journalId")

    private fun fileHash(uid: String, journalId: String, photoId: String) =
        sha.sha256("$uid|$journalId|$photoId")

    /** Sunucu belgesini önce şifre çözüp sonra tam olarak doğrular. */
    private fun verifyDocument(
        uid: String,
        id: String,
        version: Long?,
        owner: String?,
        ownerHashValue: String?,
        filenameHashValue: String?,
        ciphertext: String?,
        password: CharArray
    ): PublishedPhotoDeletion {
        check(id.matches(shaPattern) && version == 3L && owner == uid &&
                ownerHashValue?.matches(shaPattern) == true &&
                filenameHashValue?.matches(shaPattern) == true) {
            "Şifreli silme işareti metadata kontrolünden geçmedi."
        }
        val text = crypto.decrypt(ciphertext ?: error("Silme olayı şifresi eksik."), password)
        val json = JSONObject(text)
        check(json.getInt("formatVersion") == 3 && json.getString("ownerUid") == uid &&
                json.getString("kind") == "JOURNAL_PHOTO") {
            "Şifreli silme olayı kimliği geçersiz."
        }
        val journalId = json.getString("journalId")
        val photoId = json.getString("photoId")
        val deletedAtMillis = json.getLong("deletedAtMillis")
        check(deletedAtMillis > 0 &&
                markerId(uid, journalId, photoId) == id &&
                ownerHash(uid, journalId) == ownerHashValue &&
                fileHash(uid, journalId, photoId) == filenameHashValue) {
            "Silme işareti içerik/kimlik eşleşmesi başarısız."
        }
        return PublishedPhotoDeletion(journalId, photoId, deletedAtMillis)
    }

    private fun encryptedJson(uid: String, item: PublishedPhotoDeletion): String =
        JSONObject().apply {
            put("formatVersion", 3)
            put("ownerUid", uid)
            put("kind", "JOURNAL_PHOTO")
            put("journalId", item.journalId)
            put("photoId", item.photoId)
            put("deletedAtMillis", item.deletedAtMillis)
        }.toString()

    /**
     * Yalnızca kullanıcının bu cihazda açıkça sildiği fotoğrafları işaretler.
     * Aynı işaretin iki kez gönderilmesi yeni belge oluşturmaz.
     * Bir çalışmada en fazla üç işaret gönderilir; tarama 24 kayıtla sınırlıdır.
     */
    suspend fun uploadPending(uid: String, password: CharArray): MediaDeletionPublishResult =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            requireAccount(uid)
            require(password.size in 12..128)
            val source = ledger.list().sortedWith(compareBy({ it.deletedAtMillis }, { it.journalId }, { it.photoId }))
            if (source.isEmpty()) return@withContext MediaDeletionPublishResult(0, 0, 0)
            val key = "position_${sha.sha256(uid).take(24)}"
            var position = prefs.getInt(key, 0).coerceIn(0, source.size)
            var published = 0
            var existed = 0
            var inspected = 0
            while (position < source.size && inspected < 24 && published < 3) {
                requireAccount(uid)
                val item = source[position]
                val id = markerId(uid, item.journalId, item.photoId)
                val ref = collection(uid).document(id)
                val found = ref.get(Source.SERVER).await()
                requireAccount(uid)
                if (found.exists()) {
                    val existing = verifyDocument(uid, found.id, found.getLong("formatVersion"),
                        found.getString("ownerUid"), found.getString("ownerHash"),
                        found.getString("fileNameHash"), found.getString("encryptedPayload"), password)
                    check(existing.journalId == item.journalId && existing.photoId == item.photoId)
                    existed++
                } else {
                    val payload = crypto.encrypt(encryptedJson(uid, item), password)
                    val fields = mapOf(
                        "formatVersion" to 3,
                        "ownerUid" to uid,
                        "ownerHash" to ownerHash(uid, item.journalId),
                        "fileNameHash" to fileHash(uid, item.journalId, item.photoId),
                        "encryptedPayload" to payload,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    firestore.runTransaction { tx ->
                        val current = tx.get(ref)
                        if (!current.exists()) tx.set(ref, fields)
                        else check(current.getString("ownerUid") == uid &&
                                current.getString("ownerHash") == ownerHash(uid, item.journalId) &&
                                current.getString("fileNameHash") == fileHash(uid, item.journalId, item.photoId)) {
                            "Eşzamanlı silme kaydı farklı içerikle oluşturuldu."
                        }
                        null
                    }.await()
                    published++
                }
                position++
                inspected++
                check(prefs.edit().putInt(key, position).commit()) {
                    "Medya silme işareti aktarım ilerlemesi saklanamadı."
                }
            }
            if (position >= source.size) check(prefs.edit().putInt(key, 0).commit())
            MediaDeletionPublishResult(published, existed, inspected)
        }

    /**
     * Her geri yükleme/yükleme adayının fotoğraf ismi bilindiğinde çalışır.
     * Yerel silme işareti varsa bulut sorgusuna bile ihtiyaç duyulmaz.
     * Sunucudan okuma zorunludur: offline önbellek "silinmedi" kanıtı olamaz.
     */
    suspend fun isDeleted(uid: String, password: CharArray, journalId: String, photoId: String): Boolean =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            requireAccount(uid)
            if (ledger.contains(journalId, photoId)) return@withContext true
            val doc = collection(uid).document(markerId(uid, journalId, photoId))
                .get(Source.SERVER).await()
            requireAccount(uid)
            if (!doc.exists()) return@withContext false
            val opened = verifyDocument(uid, doc.id, doc.getLong("formatVersion"),
                doc.getString("ownerUid"), doc.getString("ownerHash"),
                doc.getString("fileNameHash"), doc.getString("encryptedPayload"), password)
            check(opened.journalId == journalId && opened.photoId == photoId)
            true
        }

    /**
     * Faz 2G.4C: Temizleme adaylarını bulmadan ÖNCE silme işaretleri sunucudan okunur,
     * AES-GCM ile açılır ve orijinal fotoğraf/anı kimliği doğrulanır.
     * Şifre, sonuç nesnelerine veya kalıcı depoya yazılmaz. Eksik tarama hata verir.
     */
    suspend fun listVerifiedRemote(uid: String, password: CharArray): List<PublishedPhotoDeletion> =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            requireAccount(uid)
            require(password.size in 12..128) { "Medya aktarım şifresi 12–128 karakter olmalı." }
            var query: Query = collection(uid)
                .orderBy("createdAt", Query.Direction.ASCENDING).limit(50)
            val result = mutableListOf<PublishedPhotoDeletion>()
            while (true) {
                val page = query.get(Source.SERVER).await()
                requireAccount(uid)
                if (page.isEmpty) break
                check(result.size + page.size() <= 500) {
                    "500 silme işareti sınırı aşıldı; eksik tarama ile temizleme yapılamaz."
                }
                page.documents.forEach { doc ->
                    result += verifyDocument(
                        uid, doc.id, doc.getLong("formatVersion"), doc.getString("ownerUid"),
                        doc.getString("ownerHash"), doc.getString("fileNameHash"),
                        doc.getString("encryptedPayload"), password
                    )
                }
                if (page.size() < 50) break
                query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING)
                    .startAfter(page.documents.last()).limit(50)
            }
            check(result.map { it.journalId to it.photoId }.toSet().size == result.size) {
                "Bir fotoğraf için birden çok geçersiz silme kaydı var."
            }
            result.toList()
        }

    /**
     * Bulutta silinmiş görünen ama bu cihazda hâlâ bulunan fotoğraflar ÇAKIŞMADIR.
     * Bu işlem hiçbir yerel dosyayı silmez, hiçbir Storage nesnesine dokunmaz.
     */
    suspend fun previewRemote(uid: String, password: CharArray): MediaDeletionPreview =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            requireAccount(uid)
            require(password.size in 12..128)
            var query: Query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING).limit(50)
            val remote = mutableListOf<PublishedPhotoDeletion>()
            while (true) {
                val page = query.get(Source.SERVER).await()
                requireAccount(uid)
                if (page.isEmpty) break
                check(remote.size + page.size() <= 500) {
                    "500 silme işareti tarama sınırı aşıldı; eksik sonuç gösterilmedi."
                }
                page.documents.forEach { doc ->
                    remote += verifyDocument(uid, doc.id, doc.getLong("formatVersion"),
                        doc.getString("ownerUid"), doc.getString("ownerHash"),
                        doc.getString("fileNameHash"), doc.getString("encryptedPayload"), password)
                }
                if (page.size() < 50) break
                query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING)
                    .startAfter(page.documents.last()).limit(50)
            }
            val local = ledger.list()
            val remoteKeys = remote.map { it.journalId to it.photoId }.toSet()
            val pending = local.count { (it.journalId to it.photoId) !in remoteKeys }
            val byJournal = remote.groupBy { it.journalId }
            var stillLocal = 0
            val examples = mutableListOf<String>()
            for ((journalId, deleted) in byJournal) {
                val existing = photos.list(journalId).map { it.id }.toSet()
                deleted.forEach { item ->
                    if (item.photoId in existing) {
                        stillLocal++
                        if (examples.size < 12) examples += "Anı ${journalId.take(8)}… / fotoğraf ${item.photoId.take(13)}…"
                    }
                }
            }
            MediaDeletionPreview(remote.size, stillLocal, pending, examples)
        }
}
