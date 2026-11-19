package com.loc.benimdunyam.data.sync.remote

import android.util.Base64
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.SyncEventIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Kullanıcı onaylı bir gönderimin sonucu. */
data class ManualJournalUploadResult(
    val confirmed: Int,
    val changedDuringUpload: Int,
    val pending: Int
)

/** Bir bölüm gönderilmiş olsa bile hata nedenini saklar. */
class PartialJournalUploadException(
    val confirmed: Int,
    cause: Throwable
) : Exception("$confirmed olay doğrulandı; aktarım kesildi: ${cause.message ?: "Bilinmeyen hata"}", cause)

/**
 * Faz 2F.3: Anı değişikliklerini Firestore'a yalnızca KULLANICI ONAYIYLA yollar.
 *
 * - Şifreyi veya anı metnini Room kuyruğuna, DataStore'a, WorkManager'a yazmaz.
 * - Her olay belge olarak EKLENİR; mevcut belge üzerine yazılmaz.
 * - Aynı olay tekrar gönderilirse sunucudaki şifreli içerik çözümlenip karşılaştırılır.
 * - DELETE olayı sunucudaki veriyi silmez, sadece silme niyetini saklar.
 * - Eski sürüm gönderilirken anı yeniden değişirse yeni sürüm PENDING kalır.
 * - Bu sınıf buluttan veri İNDİRİP Room'a UYGULAMAZ.
 */
@Singleton
class ManualJournalEventUploader @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: ActiveWorkspaceSession,
    private val database: AppDatabase,
    private val eventDao: JournalSyncEventDao,
    private val journalDao: JournalDao,
    private val mentionDao: JournalPersonMentionDao
) {
    private val mutex = Mutex()

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Firebase hesabı ile aktif yerel arşiv uyuşmuyor. Lütfen doğru hesapla giriş yap."
        }
    }

    suspend fun pending(uid: String): Int = withContext(Dispatchers.IO) {
        requireAccount(uid)
        eventDao.pendingUpserts(uid) + eventDao.pendingDeletes(uid)
    }

    /**
     * Bir dokunuşta en fazla beş kayıt yüklenir. 5'ten fazla varsa kullanıcı
     * yeniden onaylayarak sonraki partiyi gönderir; arka planda gizli yükleme yoktur.
     */
    suspend fun uploadPending(uid: String, password: CharArray): ManualJournalUploadResult =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                require(password.size in 12..128) { "Aktarım şifresi 12–128 karakter olmalı." }
                requireAccount(uid)
                val events = eventDao.pendingForAccount(uid, 5)
                var confirmed = 0
                var stale = 0

                for (event in events) {
                    try {
                        requireAccount(uid)
                        val json = readEventSnapshot(event)
                        if (json == null) {
                            stale++
                            continue
                        }
                        val eventId = SyncEventIdentity.eventId(
                            uid, event.journalId, event.changeVersion, event.changeType
                        )
                        val ciphertext = CloudArchiveCrypto.encrypt(json, password)
                        val encoded = try {
                            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
                        } finally {
                            ciphertext.fill(0)
                        }
                        require(encoded.length <= 600_000) {
                            "Anı çok büyük. Bu sürüm tek olay için 600 KB sınırını aşmamalı."
                        }

                        // Firestore işlemi sunucuya ulaşmadan tamamlanmaz.
                        // Önceden oluşturulmuş aynı olay varsa İÇERİK karşılaştırılır.
                        val doc = firestore.collection("users").document(uid)
                            .collection("journal_change_events").document(eventId)
                        val fields = mapOf(
                            "formatVersion" to 1,
                            "ownerUid" to uid,
                            "journalHash" to SyncEventIdentity.journalHash(uid, event.journalId),
                            "operation" to event.changeType,
                            "revision" to event.changeVersion,
                            "encryptedPayload" to encoded,
                            "uploadedAt" to FieldValue.serverTimestamp()
                        )
                        firestore.runTransaction { tx ->
                            val existing = tx.get(doc)
                            if (!existing.exists()) {
                                tx.set(doc, fields)
                            } else {
                                // Yeniden denemede yeni nonce kullanılabilir. Bu nedenle
                                // şifreli baytları değil, açılmış JSON içeriğini karşılaştırırız.
                                check(existing.getString("ownerUid") == uid &&
                                        existing.getString("journalHash") == fields["journalHash"] &&
                                        existing.getString("operation") == event.changeType &&
                                        existing.getLong("revision") == event.changeVersion) {
                                    "Sunucuda aynı kimlikte farklı bir olay var; veri üzerine yazılmadı."
                                }
                                val remote = existing.getString("encryptedPayload")
                                    ?: error("Sunucudaki şifreli içerik eksik.")
                                val remoteBytes = Base64.decode(remote, Base64.NO_WRAP)
                                val same = try {
                                    CloudArchiveCrypto.decrypt(remoteBytes, password) == json
                                } finally {
                                    remoteBytes.fill(0)
                                }
                                check(same) {
                                    "Olay daha önce farklı içerik veya şifreyle yüklenmiş. Üzerine yazılmadı."
                                }
                            }
                            null
                        }.await()

                        requireAccount(uid)
                        // Şifreli belgenin sunucuya yazılması doğrulandıktan sonra
                        // yalnızca aynı yerel SÜRÜM tamamlanmış sayılır.
                        val updated = eventDao.markSyncedIfUnchanged(
                            event.journalId, uid, event.changeVersion, event.changeType
                        )
                        if (updated == 1) confirmed++ else stale++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        throw PartialJournalUploadException(confirmed, error)
                    }
                }
                ManualJournalUploadResult(
                    confirmed = confirmed,
                    changedDuringUpload = stale,
                    pending = eventDao.pendingUpserts(uid) + eventDao.pendingDeletes(uid)
                )
            }
        }

    /**
     * Anı ile kuyruk olayını TEK Room okuma transaction'ında alırız.
     * Olayın sürümü arada değişmişse artık geçersiz olan veriyi göndermeyiz.
     */
    private suspend fun readEventSnapshot(event: JournalSyncEventEntity): String? =
        database.withTransaction {
            val current = eventDao.findByJournalId(event.journalId)
            if (current != event || current.state != JournalSyncChangePolicy.PENDING) {
                return@withTransaction null
            }
            val journal = journalDao.getJournalById(event.journalId)
            if (event.changeType == JournalSyncChangePolicy.UPSERT) {
                check(journal != null) { "Gönderilecek anı Room'da bulunamadı; işlem durduruldu." }
            } else {
                check(journal == null) {
                    "Silme olayı bekliyor ancak anı hâlâ Room'da; işlem durduruldu."
                }
            }

            val payload = JSONObject()
                .put("schema", 1)
                .put("ownerUid", event.ownerUid)
                .put("journalId", event.journalId)
                .put("revision", event.changeVersion)
                .put("operation", event.changeType)
                .put("changeRecordedAt", event.updatedAtMillis)

            if (journal != null) {
                payload.put("journal", JSONObject()
                    .put("id", journal.id)
                    .put("title", journal.title)
                    .put("content", journal.content)
                    .put("entryDate", journal.entryDate)
                    .put("createdAt", journal.createdAt)
                    .put("updatedAt", journal.updatedAt)
                    .put("seasonId", journal.seasonId)
                    .put("episodeId", journal.episodeId))
                val mentions = JSONArray()
                mentionDao.getMentionsForJournal(journal.id).forEach { mention ->
                    mentions.put(JSONObject()
                        .put("personId", mention.personId)
                        .put("mentionIndex", mention.mentionIndex)
                        .put("startOffset", mention.startOffset)
                        .put("endOffset", mention.endOffset))
                }
                payload.put("mentions", mentions)
            }
            payload.toString()
        }
}
