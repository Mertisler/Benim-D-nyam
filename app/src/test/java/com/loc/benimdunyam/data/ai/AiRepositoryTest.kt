package com.loc.benimdunyam.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yapay zekâ katmanı: izin, önbellek, sürüm ayıklama ve sunucu yanıtı okuma. */
class AiRepositoryTest {

    private class FakeConsent(enabled: Boolean) : AiConsentSource {
        private val state = MutableStateFlow(if (enabled) AiConsent(true, AiConsentStore.CONSENT_VERSION, 1) else AiConsent())
        override val consent: StateFlow<AiConsent> = state
        override fun accept(now: Long) { state.value = AiConsent(true, AiConsentStore.CONSENT_VERSION, now) }
        override fun revoke() { state.value = state.value.copy(enabled = false) }
    }

    private class FakeStore : AiResultStore {
        val rows = mutableMapOf<String, AiResponse>()
        override suspend fun get(task: AiTask, inputHash: String) = rows["${task.wireName}|$inputHash"]?.copy(fromCache = true)
        override suspend fun put(inputHash: String, response: AiResponse, now: Long) {
            rows.entries.removeIf { it.value.task == response.task && it.value.version != response.version }
            rows["${response.task.wireName}|$inputHash"] = response
        }
        override suspend fun clear() = rows.clear()
        override suspend fun count() = rows.size
    }

    private class FakeGateway(var version: String = "claude-haiku-5-5:1") : AiGateway {
        val calls = mutableListOf<Pair<AiTask, Map<String, Any?>>>()
        override suspend fun run(task: AiTask, input: Map<String, Any?>): AiResponse {
            calls += task to input
            return AiResponse(task, AiResultKind.TEXT, text = "cevap ${calls.size} [a1]",
                sources = mapOf("a1" to "j-1"), version = version, model = version.substringBefore(':'))
        }
    }

    private val input = mapOf("soru" to "Ne?", "bugun" to "2026-11-26", "anilar" to listOf(mapOf("id" to "j-1")))

    @Test
    fun izinYoksaSunucuyaHicbirSeyGitmez() = runBlocking {
        val gateway = FakeGateway()
        val repo = AiRepository(gateway, FakeStore(), FakeConsent(enabled = false))
        val error = runCatching { repo.run(AiTask.QUESTION_ANSWER, input) }.exceptionOrNull()
        assertTrue(error is AiException.NotEnabled)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun eskiSurumIzniGecersizdir() {
        assertFalse(AiConsent(enabled = true, acceptedVersion = 0).isValid)
        assertTrue(AiConsent(enabled = true, acceptedVersion = AiConsentStore.CONSENT_VERSION).isValid)
    }

    @Test
    fun ayniGirdiIkinciKezOnbellektenGelir() = runBlocking {
        val gateway = FakeGateway()
        val repo = AiRepository(gateway, FakeStore(), FakeConsent(enabled = true))
        val first = repo.run(AiTask.QUESTION_ANSWER, input)
        val second = repo.run(AiTask.QUESTION_ANSWER, input)
        assertEquals(1, gateway.calls.size)
        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(first.text, second.text)

        // Zorla yenileme sunucuya gider.
        repo.run(AiTask.QUESTION_ANSWER, input, useCache = false)
        assertEquals(2, gateway.calls.size)
    }

    @Test
    fun kaydetmeKapaliysaOnbellegeYazilmaz() = runBlocking {
        val store = FakeStore()
        val repo = AiRepository(FakeGateway(), store, FakeConsent(enabled = true))
        repo.run(AiTask.PERIOD_SUMMARY, input, useCache = false, storeResult = false)
        assertEquals(0, store.count())
    }

    @Test
    fun modelDegisinceEskiSonuclarAyiklanir() = runBlocking {
        val gateway = FakeGateway()
        val store = FakeStore()
        val repo = AiRepository(gateway, store, FakeConsent(enabled = true))
        repo.run(AiTask.QUESTION_ANSWER, input)
        repo.run(AiTask.QUESTION_ANSWER, input + ("soru" to "Başka?"))
        repo.run(AiTask.PERIOD_SUMMARY, input)
        assertEquals(3, store.count())

        gateway.version = "claude-opus-5-5:1"
        repo.run(AiTask.QUESTION_ANSWER, input + ("soru" to "Üçüncü?"))
        // Aynı görevin eski iki sonucu silindi; diğer görevinki kaldı.
        assertEquals(2, store.count())
    }

    @Test
    fun kapatinceIzinGiderVeSonuclarSilinir() = runBlocking {
        val store = FakeStore()
        val repo = AiRepository(FakeGateway(), store, FakeConsent(enabled = true))
        repo.run(AiTask.QUESTION_ANSWER, input)
        repo.disableAndClear()
        assertFalse(repo.consent.value.isValid)
        assertEquals(0, store.count())
    }

    @Test
    fun ozetAnahtarSirasindanBagimsizVeIcerigeDuyarli() {
        val a = mapOf("b" to 1, "a" to listOf("x", mapOf("z" to true, "y" to null)))
        val b = linkedMapOf("a" to listOf("x", linkedMapOf("y" to null, "z" to true)), "b" to 1L)
        assertEquals(AiInputHasher.hash(AiTask.QUESTION_ANSWER, a), AiInputHasher.hash(AiTask.QUESTION_ANSWER, b))
        assertNotEquals(AiInputHasher.hash(AiTask.QUESTION_ANSWER, a), AiInputHasher.hash(AiTask.PERIOD_SUMMARY, a))
        assertNotEquals(AiInputHasher.hash(AiTask.QUESTION_ANSWER, a),
            AiInputHasher.hash(AiTask.QUESTION_ANSWER, a + ("b" to 2)))
        assertEquals("{\"k\":\"satır\\n\\\"tırnak\\\"\"}", AiInputHasher.canonical(mapOf("k" to "satır\n\"tırnak\"")))
    }

    @Test
    fun sunucuYanitiOkunur() {
        val text = FirebaseAiGateway.parse(AiTask.QUESTION_ANSWER, mapOf(
            "tur" to "metin", "sonuc" to "Kaş'a gitmişsin [a2].", "kaynaklar" to mapOf("a2" to "j-2"),
            "surum" to "claude-haiku-5-5:1", "model" to "claude-haiku-5-5", "kesildi" to false
        ))
        assertEquals(AiResultKind.TEXT, text.kind)
        assertEquals("Kaş'a gitmişsin [a2].", text.text)
        assertEquals(mapOf("a2" to "j-2"), text.sources)

        val json = FirebaseAiGateway.parse(AiTask.MEMORY_ANALYSIS, mapOf(
            "tur" to "json", "sonuc" to mapOf("konular" to listOf("iş"), "ruhHaliOnerisi" to "HAPPY"),
            "kaynaklar" to emptyMap<String, String>(), "surum" to "claude-haiku-5-5:1", "model" to "claude-haiku-5-5"
        ))
        assertEquals(listOf("iş"), json.json?.get("konular"))
        assertEquals(null, json.text)

        val empty = runCatching {
            FirebaseAiGateway.parse(AiTask.QUESTION_ANSWER, mapOf("tur" to "metin", "sonuc" to "  "))
        }.exceptionOrNull()
        assertTrue(empty is AiException.Unknown)
    }

    @Test
    fun aniGirdisiSunucununBekledigiAlanAdlariyla() {
        val wire = AiMemoryInput("j-1", "2025-07-28", "Koy", "Metin", listOf("Ayşe"), "Üniversite", "HAPPY").toWire()
        assertEquals(setOf("id", "tarih", "baslik", "metin", "kisiler", "donem", "ruhHali"), wire.keys)
        assertFalse(AiMemoryInput("j-1", "2025-07-28", "Koy", "Metin").toWire().containsKey("donem"))
    }

    @Test
    fun kullanimYanitiOkunur() {
        val u = FirebaseAiGateway.parseUsage(mapOf(
            "gun" to "2026-11-26", "gunlukIstekLimiti" to 300,
            "bugun" to mapOf("istek" to 12, "mikroUsd" to 8000),
            "ay" to mapOf("istek" to 40, "mikroUsd" to 32000L,
                "gorevler" to mapOf("ani_analizi" to mapOf("istek" to 30, "mikroUsd" to 9000)))
        ))
        assertEquals(300, u.dailyLimit)
        assertEquals(AiUsageCounter(12, 8000), u.today)
        assertEquals(AiUsageCounter(40, 32000), u.month)
        assertEquals(AiUsageCounter(30, 9000), u.monthByTask["ani_analizi"])
        assertEquals(288, u.remainingToday)
        // Eksik alanlar sıfır sayılır.
        assertEquals(AiUsageCounter(), FirebaseAiGateway.parseUsage(emptyMap<String, Any>()).today)
    }
}
