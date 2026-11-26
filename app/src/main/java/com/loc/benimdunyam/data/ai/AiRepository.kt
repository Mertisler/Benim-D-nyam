package com.loc.benimdunyam.data.ai

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Yapay zekâ isteklerinin tek giriş noktası.
 *
 * - İzin yoksa hiçbir şey gönderilmez.
 * - Aynı görev ve aynı girdi için önbellekteki sonuç döner; para ve zaman harcanmaz.
 * - Sunucuda model ya da talimat değişirse yeni sonuç gelince o görevin eski sonuçları silinir.
 */
@Singleton
class AiRepository @Inject constructor(
    private val gateway: AiGateway,
    private val store: AiResultStore,
    private val consentSource: AiConsentSource
) {
    val consent: StateFlow<AiConsent> get() = consentSource.consent

    suspend fun run(
        task: AiTask,
        input: Map<String, Any?>,
        useCache: Boolean = true,
        /** false: sonuç önbelleğe yazılmaz (ör. bağlantı denemesi). */
        storeResult: Boolean = true,
        now: Long = System.currentTimeMillis()
    ): AiResponse {
        if (!consentSource.consent.value.isValid) throw AiException.NotEnabled()
        val hash = AiInputHasher.hash(task, input)
        if (useCache) store.get(task, hash)?.let { return it }
        val response = gateway.run(task, input)
        // Önbellek yazılamasa da kullanıcı cevabı alır.
        if (storeResult) runCatching { store.put(hash, response, now) }
        return response
    }

    /** İzni geri alır ve cihazdaki tüm yapay zekâ sonuçlarını siler. */
    suspend fun disableAndClear() {
        consentSource.revoke()
        store.clear()
    }

    fun enable(now: Long = System.currentTimeMillis()) = consentSource.accept(now)

    suspend fun cachedCount(): Int = runCatching { store.count() }.getOrDefault(0)
}

/** Girdinin anahtar sırasından bağımsız, kararlı özeti (SHA-256). */
object AiInputHasher {

    fun hash(task: AiTask, input: Map<String, Any?>): String {
        val canonical = task.wireName + "\n" + canonical(input)
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long, is Short, is Byte -> value.toString()
        is Number -> {
            val d = value.toDouble()
            if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()
        }
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString() }
            .joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + canonical(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        is Array<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        else -> quote(value.toString())
    }

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
}
