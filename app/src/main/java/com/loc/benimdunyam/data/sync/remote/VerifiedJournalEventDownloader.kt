package com.loc.benimdunyam.data.sync.remote

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.RemoteJournalEventPolicy
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Şifresi doğrulanan bir olayın yalnızca ekranda gösterilecek kısa özeti. */
data class RemoteJournalPreviewItem(
    val label: String,
    val operation: String,
    val revision: Long,
    val localStatus: RemoteJournalLocalStatus
)

/**
 * İncelenen olay sayısı, koleksiyonun tamamı anlamına gelmeyebilir.
 * hasMore=true olduğunda listede daha fazla olay bulunduğu kesinleşir.
 */
data class RemoteJournalDownloadPreview(
    val items: List<RemoteJournalPreviewItem>,
    val hasMore: Boolean
)

/**
 * Faz 2F.4: Şifreli anı olaylarını yalnızca SUNUCUDAN okuyup doğrular.
 *
 * Güvenlik sınırları:
 * - Kullanıcı oturumu ile uygulamanın aktif Room hesabı eşleşmek zorundadır.
 * - Şifre saklanmaz; içeriği çözülmüş günlük metni ekrana veya Logcat'e yazılmaz.
 * - Belge kimliği, UID, şifreli JSON ve sürüm birbirini doğrulamalıdır.
 * - Herhangi bir belge hatalıysa kısmi sonuç "başarılı" sayılmaz.
 * - Hiçbir Room INSERT / UPDATE / DELETE işlemi YAPILMAZ.
 *
 * İlk sürümde en yeni 20 olay gösterilir; daha fazlası varsa kullanıcıya
 * eksik kapsam uyarısı verilir. Bu, bütün arşivin eşit olduğu anlamına gelmez.
 */
@Singleton
class VerifiedJournalEventDownloader @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val journalDao: JournalDao,
    private val eventDao: JournalSyncEventDao
) {
    private val mutex = Mutex()

    private fun requireCorrectAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Aktif yerel arşiv ile Firebase hesabı uyuşmuyor; işlem durduruldu."
        }
    }

    suspend fun preview(uid: String, password: CharArray): RemoteJournalDownloadPreview =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                require(password.size in 12..128) { "Aktarım şifresi 12–128 karakter olmalı." }
                requireCorrectAccount(uid)

                // Özellikle Source.SERVER kullanılır: Firestore önbelleği başarı sayılmaz.
                val result = firestore.collection("users").document(uid)
                    .collection("journal_change_events")
                    .orderBy("uploadedAt", Query.Direction.DESCENDING)
                    .limit((MAX_EVENTS + 1).toLong())
                    .get(Source.SERVER)
                    .await()
                requireCorrectAccount(uid)

                val hasMore = result.documents.size > MAX_EVENTS
                val items = ArrayList<RemoteJournalPreviewItem>()
                for (document in result.documents.take(MAX_EVENTS)) {
                    requireCorrectAccount(uid)
                    check(document.getLong("formatVersion") == 1L) {
                        "Desteklenmeyen şifreli olay biçimi."
                    }
                    val ciphertextBase64 = document.getString("encryptedPayload")
                        ?: error("Şifreli olay içeriği eksik.")
                    require(ciphertextBase64.length in 51..600_000) {
                        "Şifreli olay boyutu sınırların dışında."
                    }
                    val payload = try {
                        Base64.decode(ciphertextBase64, Base64.NO_WRAP)
                    } catch (invalid: IllegalArgumentException) {
                        throw IllegalStateException("Bulut olayı Base64 biçiminde değil.", invalid)
                    }
                    val plainJson = try {
                        // Yanlış şifrede veya değiştirilmiş şifreli veride AES-GCM reddeder.
                        CloudArchiveCrypto.decrypt(payload, password)
                    } catch (failure: Exception) {
                        throw IllegalStateException(
                            "Şifre yanlış, bulut olayı bozuk veya farklı aktarım şifresi kullanılmış.",
                            failure
                        )
                    } finally {
                        payload.fill(0)
                    }

                    // Çözülen JSON yalnızca bu işlem boyunca kullanılır.
                    val json = JSONObject(plainJson)
                    check(json.getInt("schema") == 1) { "Desteklenmeyen olay JSON şeması." }
                    val operation = json.getString("operation")
                    val journalId = json.getString("journalId")
                    val revision = json.getLong("revision")
                    val verified = RemoteJournalEventPolicy.verify(
                        expectedUid = uid,
                        documentId = document.id,
                        headerUid = document.getString("ownerUid"),
                        headerHash = document.getString("journalHash"),
                        headerOperation = document.getString("operation"),
                        headerRevision = document.getLong("revision"),
                        payloadUid = json.getString("ownerUid"),
                        payloadJournalId = journalId,
                        payloadOperation = operation,
                        payloadRevision = revision
                    )
                    val title = validatePayloadAndReadTitle(json, verified.operation, verified.journalId)
                    requireCorrectAccount(uid)

                    // Yerel kaydı sadece OKUYORUZ; bulutun üzerine yazmasına izin yok.
                    val local = journalDao.getJournalById(journalId)
                    val localEvent = eventDao.findByJournalId(journalId)
                    val localPending = localEvent?.ownerUid == uid &&
                            localEvent.state == JournalSyncChangePolicy.PENDING
                    val sameSyncedEvent = localEvent?.ownerUid == uid &&
                            localEvent.state == "SYNCED" &&
                            localEvent.changeVersion == revision &&
                            localEvent.changeType == operation

                    items += RemoteJournalPreviewItem(
                        label = title.take(64),
                        operation = operation,
                        revision = revision,
                        localStatus = RemoteJournalEventPolicy.classify(
                            operation, local != null, localPending, sameSyncedEvent
                        )
                    )
                }
                requireCorrectAccount(uid)
                RemoteJournalDownloadPreview(items, hasMore)
            }
        }

    /**
     * Yükleyicinin v1 JSON sözleşmesini denetler. Eksik kişi/dönem kayıtlarını
     * otomatik içe aktarmıyoruz; burada yalnızca olay şemasının sağlamlığı ölçülür.
     */
    private fun validatePayloadAndReadTitle(
        json: JSONObject,
        operation: String,
        journalId: String
    ): String {
        return when (operation) {
            JournalSyncChangePolicy.UPSERT -> {
                val journal = json.getJSONObject("journal")
                require(journal.getString("id") == journalId) {
                    "Şifreli olayın anı kimliği uyuşmuyor."
                }
                val title = journal.getString("title")
                journal.getString("content")
                journal.getLong("entryDate")
                journal.getLong("createdAt")
                journal.getLong("updatedAt")
                val mentions: JSONArray = json.getJSONArray("mentions")
                for (i in 0 until mentions.length()) {
                    val mention = mentions.getJSONObject(i)
                    require(mention.getString("personId").isNotBlank()) { "Kişi bağlantısı geçersiz." }
                    mention.getInt("mentionIndex")
                }
                title.ifBlank { "Başlıksız anı" }
            }
            JournalSyncChangePolicy.DELETE -> {
                require(!json.has("journal")) { "Silme olayı anı içeriği taşıyamaz." }
                "Silme olayı · ${journalId.takeLast(6)}"
            }
            else -> error("Desteklenmeyen bulut olayı.")
        }
    }

    private companion object {
        const val MAX_EVENTS = 20
    }
}
