package com.loc.benimdunyam.data.sync.media

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Faz 2G.4C — Android istemcisi GCS delete() çağırmaz.
 * Önce cihazda AES-GCM kayıtları çözülür. Sunucu yalnızca 2G.3 marker metadata'sını,
 * 2G.1 manifest tekilliğini ve GCS sürümünü denetler; parola ASLA gönderilmez.
 * Sunucunun AES-GCM içeriğini çözemediği ve fiziksel silmenin varsayılan
 * olarak kapalı olduğu unutulmamalıdır.
 */
class FirebaseCallableMediaCleanupGateway(
    // Bu bölgeyi functions/.env içindeki BD_CLEANUP_REGION ile aynı tut.
    private val region: String = "europe-west1"
) : VerifiedMediaCleanupGateway {
    private val functions by lazy { FirebaseFunctions.getInstance(region) }
    private val auth get() = FirebaseAuth.getInstance()

    private fun requireUid(uid: String) {
        SparkMediaPolicy.requireRemoteMedia()
        check(uid.isNotBlank() && auth.currentUser?.uid == uid) {
            "Aktif Firebase hesabı değişti; temizleme durduruldu."
        }
    }

    override suspend fun isReady(uid: String): Boolean {
        requireUid(uid)
        // Sunucuya erişim yoksa önceki 2G.4 raporlaması korunmalı; silme KİLİTLİ kalmalı.
        return try {
            val result = functions.getHttpsCallable("bdMediaCleanupStatusV2")
                .call(emptyMap<String, Any>()).awaitTask().data as? Map<*, *>
            requireUid(uid)
            result?.get("ready") == true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun prepare(uid: String, item: CloudMediaObject, journalId: String, photoId: String): VerifiedMediaCleanupPermit? {
        requireUid(uid)
        val generation = item.generation ?: return null
        val result = try {
            // Şifre cihazdan çıkmaz; kimlikler yalnızca bağlı şifreli silme belgesinden gelir.
            val request = item.asRequest().toMutableMap()
            request["journalId"] = journalId
            request["photoId"] = photoId
            functions.getHttpsCallable("bdMediaCleanupPrepareV2")
                .call(request).awaitTask().data as? Map<*, *>
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            // Kanıtı doğrulanmamış nesneler aday listesine katılmaz.
            // Diğer hatalar (özellikle bağlantı, yetki, kod) GİZLENMEZ.
            if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND) return null
            throw e
        }
        requireUid(uid)
        val permit = requireNotNull(result) { "Beklenmeyen temizleme izni yanıtı." }
        fun required(key: String) = permit[key] as? String
            ?: error("Sunucu temizleme izni eksik: $key")
        val expires = (permit["expiresAtMs"] as? Number)?.toLong()
            ?: error("Sunucunun izin süresi okunamadı.")
        return VerifiedMediaCleanupPermit(
            uid = required("uid"), bucket = required("bucket"),
            fullPath = required("fullPath"), generation = required("generation"),
            immutableDeletionRecordId = required("immutableDeletionRecordId"),
            journalId = required("journalId"), photoId = required("photoId"),
            expiresAtMs = expires, opaqueProof = required("opaqueProof")
        ).also { verified ->
            // Sunucunun döndürdüğü kapsam/nesne değiştirilemez.
            check(verified.uid == uid && verified.bucket == item.bucket &&
                    verified.fullPath == item.fullPath && verified.generation == generation &&
                    verified.journalId == journalId && verified.photoId == photoId) {
                "Sunucu izin kapsamı nesneyle eşleşmedi."
            }
        }
    }

    override suspend fun deleteAtomically(
        uid: String,
        item: CloudMediaObject,
        permit: VerifiedMediaCleanupPermit
    ) {
        requireUid(uid)
        require(item.generation != null && permit.generation == item.generation)
        val params = item.asRequest().toMutableMap()
        params["opaqueProof"] = permit.opaqueProof
        params["immutableDeletionRecordId"] = permit.immutableDeletionRecordId
        params["journalId"] = permit.journalId
        params["photoId"] = permit.photoId
        params["confirmation"] = "TEMİZLE"
        params["confirmed"] = true

        val response = functions.getHttpsCallable("bdMediaCleanupExecuteV2")
            .call(params).awaitTask().data as? Map<*, *>
        requireUid(uid)
        check(response?.get("deleted") == true && response["generation"] == item.generation) {
            "Sunucu fiziksel silmeyi doğrulayamadı; yeniden tarama gerekir."
        }
    }

    private fun CloudMediaObject.asRequest(): Map<String, Any> = buildMap {
        put("bucket", bucket)
        put("fullPath", fullPath)
        put("generation", requireNotNull(generation))
        put("bytes", bytes)
        // Metadata içermeyen nesnelerde null ile gönderilir, sunucu null olarak yorumlar.
        md5Hash?.let { put("md5Hash", it) }
    }
}

/** Firebase Task sonucunu coroutine uyumlu biçimde bekler. */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(
            task.exception ?: IllegalStateException("Firebase Functions yanıtı alınamadı.")
        )
    }
}
