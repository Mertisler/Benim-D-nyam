package com.loc.benimdunyam.data.ai

/**
 * Sunucudaki yapay zekâ görevleri (functions/ai/gorevler.js ile aynı adlar).
 * Uygulama yalnızca görevi ve girdiyi bilir; model seçimi sunucudadır.
 */
enum class AiTask(val wireName: String) {
    MEMORY_ANALYSIS("ani_analizi"),
    QUESTION_FILTER("soru_filtresi"),
    QUESTION_ANSWER("soru_cevabi"),
    PERIOD_SUMMARY("donem_ozeti"),
    PERSON_STORY("kisi_hikayesi")
}

/** Sunucuya gönderilen anı. [date] yyyy-MM-dd; [moodName] JournalMood adı ya da null. */
data class AiMemoryInput(
    val id: String,
    val date: String,
    val title: String,
    val content: String,
    val people: List<String> = emptyList(),
    val seasonTitle: String? = null,
    val moodName: String? = null
) {
    fun toWire(): Map<String, Any?> = buildMap {
        put("id", id)
        put("tarih", date)
        put("baslik", title)
        put("metin", content)
        put("kisiler", people)
        seasonTitle?.let { put("donem", it) }
        moodName?.let { put("ruhHali", it) }
    }
}

enum class AiResultKind { TEXT, JSON }

data class AiResponse(
    val task: AiTask,
    val kind: AiResultKind,
    /** [AiResultKind.TEXT] için cevap; [a3] gibi kaynak işaretleri içerebilir. */
    val text: String? = null,
    /** [AiResultKind.JSON] için yapılandırılmış sonuç. */
    val json: Map<String, Any?>? = null,
    /** Kaynak işareti → anı kimliği (ör. "a3" → "journal-uuid"). */
    val sources: Map<String, String> = emptyMap(),
    /** model:talimatSürümü — değişince önbellekteki eski sonuçlar ayıklanır. */
    val version: String,
    val model: String,
    val truncated: Boolean = false,
    val fromCache: Boolean = false
)

/** Sunucunun kullanım sayacı. Tutarlar mikro-dolar (1.000.000 = 1 USD). */
data class AiUsageCounter(val requests: Int = 0, val microUsd: Long = 0)

data class AiUsage(
    val day: String,
    val dailyLimit: Int,
    val today: AiUsageCounter,
    val month: AiUsageCounter,
    /** Görev adı (sunucudaki) → bu ayki sayaç */
    val monthByTask: Map<String, AiUsageCounter> = emptyMap()
) {
    val remainingToday: Int get() = (dailyLimit - today.requests).coerceAtLeast(0)
}

/** Kullanıcıya gösterilebilir, Türkçe hata. */
sealed class AiException(message: String) : Exception(message) {
    class NotEnabled : AiException("Yapay zekâ özellikleri kapalı. Ayarlar'dan açabilirsin.")
    class NoAccount : AiException("Yapay zekâ için bulut hesabınla giriş yapmalısın.")
    class Offline : AiException("İnternet bağlantısı yok. Bağlantı gelince tekrar dene.")
    class LimitReached(message: String) : AiException(message)
    class Busy(message: String) : AiException(message)
    class Rejected(message: String) : AiException(message)
    class Invalid(message: String) : AiException(message)
    class Unknown(message: String = "Yapay zekâ isteği tamamlanamadı.") : AiException(message)
}
