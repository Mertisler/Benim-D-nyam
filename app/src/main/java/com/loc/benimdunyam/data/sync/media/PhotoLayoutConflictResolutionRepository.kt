package com.loc.benimdunyam.data.sync.media

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Önizlemede kullanıcının gördüğü yerel sıralama/kapak bilgisi korunur.
 * Şifre ve çözülmüş fotoğraf metadatası kalıcı diske veya SavedState'e yazılmaz.
 */
data class CloudPhotoLayoutChoice(
    val journalId: String,
    val journalTitle: String,
    val documentId: String,
    val photoCount: Int,
    val coverPosition: Int?,
    val canApply: Boolean,
    val note: String,
    val expectedLocalOrder: List<String>,
    val expectedLocalCover: String?
)

/**
 * Faz 2G.3: Kullanıcı bulutta birden fazla fotoğraf düzeni arasından seçim yapar.
 * Seçimin uygulanması ancak yerel fotoğraf kimlikleri birebir aynı kaldıysa mümkündür.
 * Her seçimden önce sunucudaki ŞİFRELİ belge tekrar doğrulanır; mevcut anı metni
 * veya fotoğraf dosyası değişmez. Yalnızca kapak ve sıralama metadata'sı yazılır.
 */
@Singleton
class PhotoLayoutConflictResolutionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val journals: JournalDao,
    private val photos: JournalPhotoStore,
    private val hash: EncryptedMediaCodec,
    private val crypto: EncryptedPhotoLayoutCodec,
    private val deleted: EncryptedMediaDeletionRepository
) {
    private val shaPattern = Regex("^[0-9a-f]{64}$")
    private val photoPattern = Regex("^[0-9]{13}_[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Kapak çakışması sırasında hesap veya çalışma alanı değişmiş."
        }
    }

    private fun collection(uid: String) = firestore.collection("users")
        .document(uid).collection("encrypted_photo_layouts")

    private data class VerifiedLayout(
        val journalId: String,
        val order: List<String>,
        val cover: String?
    )

    private fun decode(
        uid: String, docId: String, storedUid: String?, version: Long?,
        storedHash: String?, encrypted: String?, password: CharArray
    ): VerifiedLayout {
        check(docId.matches(shaPattern) && storedUid == uid && version == 2L) {
            "Bulut fotoğraf düzeni metadata doğrulaması başarısız."
        }
        val clear = crypto.decrypt(encrypted ?: error("Fotoğraf düzeni şifreli içeriği eksik."), password)
        val json = JSONObject(clear)
        val journalId = json.getString("journalId")
        val array = json.getJSONArray("order")
        val ids = (0 until array.length()).map { array.getString(it) }
        val cover = if (json.isNull("coverId")) null else json.getString("coverId")
        check(json.getInt("formatVersion") == 2 && json.getString("ownerUid") == uid &&
                ids.size in 1..JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL &&
                ids.size == ids.distinct().size && ids.all(photoPattern::matches) &&
                (cover == null || cover in ids) &&
                storedHash == hash.sha256("$uid|PHOTO_LAYOUT|$journalId") &&
                docId == hash.sha256("$uid|PHOTO_LAYOUT|$journalId|$clear")) {
            "Şifreli fotoğraf düzeninin içeriği ve kimliği eşleşmedi."
        }
        return VerifiedLayout(journalId, ids, cover)
    }

    /**
     * En fazla 300 kayıt okunur. Sınır aşılırsa eksik listeyi tam gibi göstermeyiz.
     * Yerel fotoğraf listesi eksikse kullanıcı uzaktaki kapak seçimini uygulayamaz.
     */
    suspend fun preview(uid: String, password: CharArray): List<CloudPhotoLayoutChoice> =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            requireAccount(uid)
            require(password.size in 12..128)
            val localJournals = journals.getAllJournalEntities().associateBy { it.id }
            val matches = mutableListOf<Pair<String, VerifiedLayout>>()
            var query: Query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING).limit(50)
            while (true) {
                val page = query.get(Source.SERVER).await()
                requireAccount(uid)
                if (page.isEmpty) break
                check(matches.size + page.size() <= 300) {
                    "300 düzen kaydı sınırı aşıldı; eksik listeyle seçim yapılamaz."
                }
                page.documents.forEach { doc ->
                    val layout = decode(uid, doc.id, doc.getString("ownerUid"),
                        doc.getLong("formatVersion"), doc.getString("ownerHash"),
                        doc.getString("encryptedPayload"), password)
                    if (layout.journalId in localJournals) matches += doc.id to layout
                }
                if (page.size() < 50) break
                query = collection(uid).orderBy("createdAt", Query.Direction.ASCENDING)
                    .startAfter(page.documents.last()).limit(50)
            }
            val result = mutableListOf<CloudPhotoLayoutChoice>()
            for ((docId, remote) in matches) {
                val local = photos.list(remote.journalId)
                val ids = local.map { it.id }
                val localCover = local.firstOrNull { it.isCover }?.id
                val complete = ids.size == remote.order.size && ids.toSet() == remote.order.toSet()
                val same = complete && ids == remote.order && localCover == remote.cover
                val note = when {
                    !complete -> "Eksik veya farklı fotoğraflar: önce medya aktarımını tamamla."
                    same -> "Bu cihazdaki kapak ve sıra ile aynı."
                    else -> "Farklı kapak/sıra; yalnızca açık onayla uygulanabilir."
                }
                result += CloudPhotoLayoutChoice(
                    journalId = remote.journalId,
                    journalTitle = localJournals[remote.journalId]?.title.orEmpty().take(60),
                    documentId = docId,
                    photoCount = remote.order.size,
                    coverPosition = remote.cover?.let { remote.order.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) },
                    canApply = complete && !same,
                    note = note,
                    expectedLocalOrder = ids,
                    expectedLocalCover = localCover
                )
            }
            result
        }

    /**
     * Kullanıcı seçtiği bulut belgesini onayladığında; belgeyi tekrar indirip
     * gerçek fotoğraf listesini, mevcut kapağı ve silme kayıtlarını karşılaştırırız.
     */
    suspend fun applyApproved(uid: String, password: CharArray, selected: CloudPhotoLayoutChoice): Boolean =
        withContext(Dispatchers.IO) {
            SparkMediaPolicy.requireRemoteMedia()
            requireAccount(uid)
            require(password.size in 12..128)
            require(selected.canApply && selected.documentId.matches(shaPattern)) {
                "Bu fotoğraf düzeni uygulanabilir değil."
            }
            check(journals.getJournalById(selected.journalId) != null) {
                "Anı artık cihazda bulunmuyor."
            }
            val doc = collection(uid).document(selected.documentId).get(Source.SERVER).await()
            requireAccount(uid)
            check(doc.exists()) { "Seçilen bulut düzeni kaldırılmış." }
            val data = decode(uid, doc.id, doc.getString("ownerUid"),
                doc.getLong("formatVersion"), doc.getString("ownerHash"),
                doc.getString("encryptedPayload"), password)
            check(data.journalId == selected.journalId) { "Seçilen anı değişmiş." }
            for (id in data.order) {
                check(!deleted.isDeleted(uid, password, data.journalId, id)) {
                    "Bu düzende silindi olarak işaretlenen fotoğraf bulunuyor; seçim uygulanmadı."
                }
            }
            requireAccount(uid)
            photos.applyApprovedCloudLayoutIfUnmodified(
                journalId = data.journalId,
                expectedLocalOrder = selected.expectedLocalOrder,
                expectedLocalCover = selected.expectedLocalCover,
                requestedOrder = data.order,
                requestedCover = data.cover
            )
        }
}
