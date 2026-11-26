package com.loc.benimdunyam.data.ai

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Yapay zekâ sunucusu. Testlerde sahte uygulamayla değiştirilir. */
interface AiGateway {
    suspend fun run(task: AiTask, input: Map<String, Any?>): AiResponse

    /** Bugün ve bu ayın kullanım sayacı (Claude çağrılmaz, ücret çıkmaz). */
    suspend fun usage(): AiUsage = throw AiException.Unknown("Kullanım bilgisi alınamadı.")
}

/**
 * Cloud Functions `bdAiGatewayV1` istemcisi. Firebase Auth girişi ve App Check jetonu
 * Firebase SDK tarafından otomatik eklenir. API anahtarı uygulamada yoktur.
 */
class FirebaseAiGateway(
    private val region: String = "europe-west1"
) : AiGateway {
    private val functions by lazy { FirebaseFunctions.getInstance(region) }

    override suspend fun run(task: AiTask, input: Map<String, Any?>): AiResponse {
        if (FirebaseAuth.getInstance().currentUser == null) throw AiException.NoAccount()
        val data = try {
            functions.getHttpsCallable(FUNCTION_NAME)
                .apply { setTimeout(120, TimeUnit.SECONDS) }
                .call(mapOf("izin" to true, "gorev" to task.wireName, "girdi" to input))
                .await()
                .getData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: FirebaseFunctionsException) {
            throw mapError(e)
        } catch (e: java.io.IOException) {
            throw AiException.Offline()
        }
        return parse(task, data as? Map<*, *> ?: throw AiException.Unknown("Yapay zekâ yanıtı okunamadı."))
    }

    override suspend fun usage(): AiUsage {
        if (FirebaseAuth.getInstance().currentUser == null) throw AiException.NoAccount()
        val data = try {
            functions.getHttpsCallable(FUNCTION_NAME)
                .apply { setTimeout(30, TimeUnit.SECONDS) }
                .call(mapOf("gorev" to "kullanim"))
                .await()
                .getData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: FirebaseFunctionsException) {
            throw mapError(e)
        } catch (e: java.io.IOException) {
            throw AiException.Offline()
        }
        return parseUsage(data as? Map<*, *> ?: throw AiException.Unknown("Kullanım bilgisi okunamadı."))
    }

    companion object {
        const val FUNCTION_NAME = "bdAiGatewayV1"

        private fun counter(value: Any?): AiUsageCounter {
            val m = value as? Map<*, *> ?: return AiUsageCounter()
            return AiUsageCounter(
                requests = (m["istek"] as? Number)?.toInt() ?: 0,
                microUsd = (m["mikroUsd"] as? Number)?.toLong() ?: 0L
            )
        }

        /** Sunucudaki "kullanim" yanıtını okur. */
        fun parseUsage(data: Map<*, *>): AiUsage {
            val month = data["ay"] as? Map<*, *>
            return AiUsage(
                day = data["gun"] as? String ?: "",
                dailyLimit = (data["gunlukIstekLimiti"] as? Number)?.toInt() ?: 0,
                today = counter(data["bugun"]),
                month = counter(month),
                monthByTask = (month?.get("gorevler") as? Map<*, *>).orEmpty()
                    .mapNotNull { (k, v) -> (k as? String)?.let { it to counter(v) } }.toMap()
            )
        }

        fun mapError(e: FirebaseFunctionsException): AiException {
            val message = e.message?.takeIf { it.isNotBlank() }
            return when (e.code) {
                Code.UNAUTHENTICATED -> AiException.NoAccount()
                Code.RESOURCE_EXHAUSTED -> AiException.LimitReached(message ?: "Bugünkü kullanım sınırına ulaşıldı.")
                Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED ->
                    if (e.cause is java.io.IOException) AiException.Offline()
                    else AiException.Busy(message ?: "Yapay zekâ servisi şu anda yoğun.")
                Code.FAILED_PRECONDITION -> AiException.Rejected(message ?: "Bu istek yanıtlanamadı.")
                Code.INVALID_ARGUMENT -> AiException.Invalid(message ?: "İstek geçersiz.")
                Code.NOT_FOUND -> AiException.Unknown("Yapay zekâ servisi henüz kurulmamış.")
                else -> AiException.Unknown()
            }
        }

        /** Sunucu yanıtını ([functions/ai/gateway.js]) okur. */
        fun parse(task: AiTask, data: Map<*, *>): AiResponse {
            val kind = if (data["tur"] == "json") AiResultKind.JSON else AiResultKind.TEXT
            val result = data["sonuc"]
            @Suppress("UNCHECKED_CAST")
            val json = (result as? Map<*, *>)?.mapKeys { it.key.toString() } as Map<String, Any?>?
            val sources = (data["kaynaklar"] as? Map<*, *>).orEmpty()
                .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap()
            return AiResponse(
                task = task,
                kind = kind,
                text = (result as? String)?.takeIf { kind == AiResultKind.TEXT },
                json = json.takeIf { kind == AiResultKind.JSON },
                sources = sources,
                version = data["surum"] as? String ?: "",
                model = data["model"] as? String ?: "",
                truncated = data["kesildi"] == true
            ).also {
                if (kind == AiResultKind.TEXT && it.text.isNullOrBlank()) throw AiException.Unknown("Yapay zekâ boş yanıt döndü.")
                if (kind == AiResultKind.JSON && it.json == null) throw AiException.Unknown("Yapay zekâ yanıtı okunamadı.")
            }
        }
    }
}
