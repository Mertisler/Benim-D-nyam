package com.loc.benimdunyam.data.sync.media

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Her çalışmada en fazla iki yeni dizilim gönderilir; eski dizilimler silinmez. */
data class EncryptedPhotoLayoutResult(
    val uploaded: Int = 0,
    val applied: Int = 0,
    val skipped: Int = 0,
    val conflicts: Int = 0
)

/**
 * Faz 2G.2: Şifreli fotoğraf sırası ve kapak bilgisi.
 * Her farklı düzen Firestore'da yeni, DEĞİŞTİRİLEMEZ belge olur.
 * Bir anının bulutta birden çok farklı düzeni varsa sessiz seçim YAPILMAZ.
 * Yerelde önceden bulunan düzenler ASLA ezilmez.
 */
@Singleton
class EncryptedPhotoLayoutSyncRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val journals: JournalDao,
    private val photos: JournalPhotoStore,
    private val mediaCodec: EncryptedMediaCodec,
    private val layoutCodec: EncryptedPhotoLayoutCodec,
    private val deletions: EncryptedMediaDeletionRepository
) {
    private val prefs = context.getSharedPreferences("bd_photo_layout_cursor_v2", Context.MODE_PRIVATE)
    private val imageId = Regex("^[0-9]{13}_[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
    private val shaId = Regex("^[0-9a-f]{64}$")

    private fun account(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Fotoğraf düzeni için hesap veya çalışma alanı uyuşmuyor."
        }
    }

    private fun collection(uid: String) = firestore.collection("users")
        .document(uid).collection("encrypted_photo_layouts")

    private fun mediaCollection(uid: String) = firestore.collection("users")
        .document(uid).collection("encrypted_media_entries")

    private fun ownerHash(uid: String, journalId: String) =
        mediaCodec.sha256("$uid|PHOTO_LAYOUT|$journalId")

    /**
     * Kullanıcı yeni otomatik medya iznini açarken bulutta varsa BİR şifreli
     * düzen örneği de doğrulanır. Yanlış cihaz parolasıyla düzen üretilmez.
     */
    suspend fun verifyRemoteSample(uid: String, passphrase: CharArray): Boolean =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            account(uid)
            val first = collection(uid).limit(1).get(Source.SERVER).await()
                .documents.firstOrNull() ?: return@withContext false
            check(first.id.matches(shaId) && first.getString("ownerUid") == uid &&
                    first.getLong("formatVersion") == 2L) {
                "Bulut fotoğraf düzeni manifesti geçersiz."
            }
            val clear = layoutCodec.decrypt(
                first.getString("encryptedPayload") ?: error("Şifreli fotoğraf düzeni yok."),
                passphrase
            )
            val parsed = JSONObject(clear)
            check(parsed.getInt("formatVersion") == 2 &&
                    parsed.getString("ownerUid") == uid &&
                    first.getString("ownerHash") == ownerHash(uid, parsed.getString("journalId")) &&
                    first.id == mediaCodec.sha256(
                "$uid|PHOTO_LAYOUT|${parsed.getString("journalId")}|$clear"
            )) { "Bulut fotoğraf düzeni şifre veya kimlik doğrulaması başarısız." }
            true
        }

    /** Aynı fotoğraflar sunucuda yoksa sıra/kapak manifesti gönderilmez. */
    private suspend fun allPhotosUploaded(uid: String, journalId: String, password: CharArray): Boolean {
        val items = photos.list(journalId)
        if (items.isEmpty() || items.size > JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL) return false
        val root = File(session.mediaRoot(), "benim_dunyam_journal_photos").canonicalFile
        for (photo in items) {
            // Silme işareti bulunan eski bir fotoğrafı içeren düzen gönderilmez.
            if (deletions.isDeleted(uid, password, journalId, photo.id)) return false
            val file = File(photo.absolutePath).canonicalFile
            if (!imageId.matches(photo.id) || file.name != photo.id ||
                !file.path.startsWith(root.path + File.separator) ||
                !file.isFile || file.length() !in 1..EncryptedMediaCodec.MAX_PLAIN_BYTES) return false
            val fingerprint = mediaCodec.sha256(file)
            val mediaId = mediaCodec.sha256("$uid|JOURNAL_PHOTO|$journalId|${photo.id}|$fingerprint")
            val doc = mediaCollection(uid).document(mediaId).get(Source.SERVER).await()
            account(uid)
            if (!doc.exists() || doc.getString("ownerUid") != uid ||
                doc.getString("storagePath") != "users/$uid/encrypted_media_v1/$mediaId.bin") return false
        }
        return true
    }

    suspend fun uploadNext(uid: String, passphrase: CharArray): EncryptedPhotoLayoutResult =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            account(uid)
            require(passphrase.size in 12..128)
            var uploaded = 0
            var skipped = 0
            // Bütün anıları taramak ücretsiz yerel okumadır; bulut denetimi sadece dolu galerilerde.
            val all = journals.getAllJournalEntities().sortedBy { it.id }
            if (all.isEmpty()) return@withContext EncryptedPhotoLayoutResult()
            val cursor = "cursor_${mediaCodec.sha256(uid).take(24)}"
            var index = prefs.getInt(cursor, 0).coerceIn(0, all.size)
            var checked = 0
            while (index < all.size && checked < 4 && uploaded < 2) {
                val journal = all[index]
                // Ağ hatasında imleci ilerletmeyiz; yeniden deneme aynı anıyı inceler.
                account(uid)
                val items = photos.list(journal.id)
                if (items.isEmpty()) {
                    index++; checked++
                    check(prefs.edit().putInt(cursor, index).commit())
                    continue
                }
                if (!allPhotosUploaded(uid, journal.id, passphrase)) {
                    skipped++; index++; checked++
                    check(prefs.edit().putInt(cursor, index).commit())
                    continue
                }
                val ids = items.map { it.id }
                val cover = items.firstOrNull { it.isCover }?.id
                val json = JSONObject().apply {
                    put("formatVersion", 2)
                    put("ownerUid", uid)
                    put("journalId", journal.id)
                    put("order", JSONArray().apply { ids.forEach { put(it) } })
                    put("coverId", cover ?: JSONObject.NULL)
                }.toString()
                // Kimlik içerikten türetilir, aynı düzen ikinci kez eklenmez.
                val id = mediaCodec.sha256("$uid|PHOTO_LAYOUT|${journal.id}|$json")
                val doc = collection(uid).document(id)
                val existing = doc.get(Source.SERVER).await()
                account(uid)
                if (existing.exists()) {
                    check(existing.getString("ownerUid") == uid &&
                            existing.getString("ownerHash") == ownerHash(uid, journal.id)) {
                        "Bulut düzen kimliği uyuşmuyor."
                    }
                    skipped++; index++; checked++
                    check(prefs.edit().putInt(cursor, index).commit())
                    continue
                }
                val encrypted = layoutCodec.encrypt(json, passphrase)
                account(uid)
                val recorded = mapOf(
                    "formatVersion" to 2,
                    "ownerUid" to uid,
                    "ownerHash" to ownerHash(uid, journal.id),
                    "encryptedPayload" to encrypted,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.runTransaction { tx ->
                    val old = tx.get(doc)
                    if (!old.exists()) tx.set(doc, recorded)
                    else check(old.getString("ownerUid") == uid &&
                            old.getString("ownerHash") == ownerHash(uid, journal.id)) {
                        "Eşzamanlı düzen yükleme çakışması."
                    }
                    null
                }.await()
                uploaded++
                index++; checked++
                check(prefs.edit().putInt(cursor, index).commit())
            }
            if (index >= all.size) prefs.edit().putInt(cursor, 0).commit()
            EncryptedPhotoLayoutResult(uploaded = uploaded, skipped = skipped)
        }

    /**
     * Sunucuda birden fazla düzen varsa kullanıcı seçimi olmadan hiçbirini uygulamayız.
     * Tarama 2.000 belge sınırına ulaşırsa kısmi sonuca güvenmeden hata verir.
     */
    suspend fun restoreSafe(
        uid: String, passphrase: CharArray, maxDocuments: Int = 2000
    ): EncryptedPhotoLayoutResult =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            account(uid)
            require(passphrase.size in 12..128)
            val journalIds = journals.getAllJournalEntities().map { it.id }
            val hashToId = journalIds.associateBy { ownerHash(uid, it) }
            val matches = mutableMapOf<String, MutableList<Pair<String, String>>>()
            var scanned = 0
            var query: Query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING).limit(50)
            while (true) {
                val page = query.get(Source.SERVER).await()
                account(uid)
                if (page.isEmpty) break
                scanned += page.size()
                check(scanned <= maxDocuments.coerceIn(1, 2000)) {
                    "Düzen kaydı tarama sınırı aşıldı; kısmi eşitleme yapılmadı."
                }
                page.documents.forEach { doc ->
                    val owner = hashToId[doc.getString("ownerHash")]
                    if (owner != null) {
                        check(doc.id.matches(shaId) && doc.getString("ownerUid") == uid &&
                                doc.getLong("formatVersion") == 2L) { "Şifreli düzen manifesti geçersiz." }
                        matches.getOrPut(owner) { mutableListOf() }
                            .add(doc.id to (doc.getString("encryptedPayload")
                                ?: error("Şifreli düzen alanı eksik.")))
                    }
                }
                if (page.size() < 50) break
                query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING)
                    .startAfter(page.documents.last()).limit(50)
            }
            // Önce TÜM adayları doğrula; yanlış şifre varsa hiçbir yazma yapılmaz.
            data class Resolved(val journalId: String, val ids: List<String>, val cover: String?)
            val resolved = mutableListOf<Resolved>()
            var conflicts = 0
            for ((journalId, records) in matches) {
                account(uid)
                if (records.size != 1) { conflicts++; continue }
                val (documentId, ciphertext) = records.single()
                val clearJson = layoutCodec.decrypt(ciphertext, passphrase)
                val plain = JSONObject(clearJson)
                val array = plain.getJSONArray("order")
                val ids = (0 until array.length()).map { array.getString(it) }
                val cover = if (plain.isNull("coverId")) null else plain.getString("coverId")
                check(plain.getInt("formatVersion") == 2 &&
                        plain.getString("ownerUid") == uid &&
                        plain.getString("journalId") == journalId &&
                        ids.isNotEmpty() && ids.size <= JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL &&
                        ids.distinct().size == ids.size && ids.all(imageId::matches) &&
                        (cover == null || cover in ids) &&
                        documentId == mediaCodec.sha256("$uid|PHOTO_LAYOUT|$journalId|$clearJson")) {
                    "Bulut fotoğraf sırası ya da kapak kimliği uyuşmuyor."
                }
                resolved += Resolved(journalId, ids, cover)
            }
            var applied = 0
            var skipped = 0
            for (candidate in resolved) {
                account(uid)
                // Yalnızca geçerli, silinmemiş fotoğrafların kapak ve sırası uygulanabilir.
                if (candidate.ids.any { deletions.isDeleted(uid, passphrase, candidate.journalId, it) }) {
                    skipped++
                    continue
                }
                if (photos.applyCloudLayoutIfUnmodified(
                        candidate.journalId, candidate.ids, candidate.ids, candidate.cover
                    )) applied++ else skipped++
            }
            EncryptedPhotoLayoutResult(applied = applied, skipped = skipped, conflicts = conflicts)
        }
}
