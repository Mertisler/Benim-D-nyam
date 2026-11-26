package com.loc.benimdunyam.data.ai.insight

import com.loc.benimdunyam.data.ai.AiConsent
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiConsentStore
import com.loc.benimdunyam.data.ai.AiException
import com.loc.benimdunyam.data.ai.AiGateway
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiResponse
import com.loc.benimdunyam.data.ai.AiResultKind
import com.loc.benimdunyam.data.ai.AiResultStore
import com.loc.benimdunyam.data.ai.AiTask
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anı analizi: hangi anıların, ne zaman, kaç kez analiz edildiği. */
class MemoryAnalysisProcessorTest {

    private val consentAt = 10_000L

    private class Consent(enabled: Boolean, at: Long) : AiConsentSource {
        private val s = MutableStateFlow(if (enabled) AiConsent(true, AiConsentStore.CONSENT_VERSION, at) else AiConsent())
        override val consent: StateFlow<AiConsent> = s
        override fun accept(now: Long) { s.value = AiConsent(true, AiConsentStore.CONSENT_VERSION, now) }
        override fun revoke() { s.value = s.value.copy(enabled = false) }
    }

    private class NoCache : AiResultStore {
        override suspend fun get(task: AiTask, inputHash: String): AiResponse? = null
        override suspend fun put(inputHash: String, response: AiResponse, now: Long) = Unit
        override suspend fun clear() = Unit
        override suspend fun count() = 0
    }

    private class Gateway : AiGateway {
        val analyzed = mutableListOf<String>()
        var failWith: AiException? = null
        override suspend fun run(task: AiTask, input: Map<String, Any?>): AiResponse {
            failWith?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            val ani = input["ani"] as Map<String, Any?>
            analyzed += ani["id"] as String
            return AiResponse(task, AiResultKind.JSON, json = mapOf(
                "konular" to listOf("Aile", "aile", " iş "), "yerler" to listOf("İzmir"), "olaylar" to emptyList<String>(),
                "ruhHaliOnerisi" to "HAPPY", "ozet" to "Kısa özet."
            ), version = "claude-haiku-5-5:1", model = "claude-haiku-5-5")
        }
    }

    private class Store : MemoryInsightStore {
        val rows = mutableMapOf<String, MemoryInsight>()
        override fun observeAll(): Flow<List<MemoryInsight>> = MutableStateFlow(rows.values.toList())
        override suspend fun getAll() = rows.values.toList()
        override suspend fun get(journalId: String) = rows[journalId]
        override suspend fun upsert(insight: MemoryInsight) { rows[insight.journalId] = insight }
        override suspend fun delete(journalIds: List<String>) { journalIds.forEach(rows::remove) }
    }

    private class Sources(var list: List<JournalEntry>, var archive: Boolean = false) : MemoryAnalysisSources {
        override suspend fun journals() = list
        override suspend fun seasonTitles() = mapOf("s1" to "Üniversite")
        override fun archiveRequested() = archive
    }

    private fun j(id: String, written: Long, content: String = "metin $id") =
        JournalEntry(id, "Başlık $id", content, 1_700_000_000_000L, written, written, listOf(Person("p1", "Ayşe", null, 1, 1)), "s1")

    private fun setup(journals: List<JournalEntry>, enabled: Boolean = true): Quad {
        val gateway = Gateway()
        val store = Store()
        val sources = Sources(journals)
        val consent = Consent(enabled, consentAt)
        val processor = MemoryAnalysisProcessor(sources, store, AiRepository(gateway, NoCache(), consent), consent)
        return Quad(processor, gateway, store, sources)
    }

    private data class Quad(val p: MemoryAnalysisProcessor, val g: Gateway, val s: Store, val src: Sources)

    @Test
    fun izinYoksaHicbirSeyYapilmaz() = runBlocking {
        val (p, g) = setup(listOf(j("a", 20_000)), enabled = false)
        assertEquals(AnalysisStop.NOT_ALLOWED, p.processBatch().stop)
        assertTrue(g.analyzed.isEmpty())
    }

    @Test
    fun izindenSonrakilerKendiliginden_eskilerYalnizcaArsivIstenirse() = runBlocking {
        val (p, g, _, src) = setup(listOf(j("eski", 5_000), j("yeni", 20_000)))
        val progress = p.progress()
        assertEquals(1, progress.pendingAutomatic)
        assertEquals(1, progress.pendingArchive)

        val r = p.processBatch()
        assertEquals(listOf("yeni"), g.analyzed)
        assertEquals(AnalysisBatchResult(1, 0, AnalysisStop.NONE), r)

        src.archive = true
        p.processBatch()
        assertEquals(listOf("yeni", "eski"), g.analyzed)
        assertEquals(2, p.progress().analyzed)
    }

    @Test
    fun degismeyenAniTekrarAnalizEdilmez_degisenEdilir() = runBlocking {
        val (p, g, _, src) = setup(listOf(j("a", 20_000)))
        p.processBatch()
        p.processBatch()
        assertEquals(1, g.analyzed.size)

        src.list = listOf(j("a", 30_000, content = "değişti"))
        p.processBatch()
        assertEquals(2, g.analyzed.size)
    }

    @Test
    fun kullanicininSildigiNotlarAniDegisenekadarUretilmez() = runBlocking {
        val (p, g, s, src) = setup(listOf(j("a", 20_000)))
        p.processBatch()
        s.rows["a"] = s.rows.getValue("a").copy(hidden = true)
        p.processBatch()
        assertEquals(1, g.analyzed.size)

        src.list = listOf(j("a", 30_000, content = "yeni metin"))
        p.processBatch()
        assertEquals(2, g.analyzed.size)
        assertTrue(s.rows.getValue("a").isVisible)
    }

    @Test
    fun gecersizIstekTekrarDenenmez_yogunlukSonraDenenir() = runBlocking {
        val (p, g, s) = setup(listOf(j("a", 20_000), j("b", 21_000)))
        g.failWith = AiException.Busy("yoğun")
        val busy = p.processBatch()
        assertEquals(AnalysisStop.RETRY_LATER, busy.stop)
        assertEquals(2, busy.remaining)
        assertTrue(s.rows.isEmpty())

        g.failWith = AiException.Invalid("geçersiz")
        val invalid = p.processBatch()
        assertEquals(AnalysisBatchResult(2, 0, AnalysisStop.NONE), invalid)
        assertTrue(s.rows.values.all { it.failed })
        g.failWith = null
        p.processBatch()
        assertTrue(g.analyzed.isEmpty())
    }

    @Test
    fun silinenAnininAnaliziTemizlenir() = runBlocking {
        val (p, _, s, src) = setup(listOf(j("a", 20_000), j("b", 21_000)))
        p.processBatch()
        src.list = listOf(j("a", 20_000))
        p.processBatch()
        assertEquals(setOf("a"), s.rows.keys)
    }

    @Test
    fun partiSiniriVeKalanSayisi() = runBlocking {
        val (p, g) = setup((1..5).map { j("j$it", 20_000L + it) })
        val r = p.processBatch(limit = 2)
        assertEquals(AnalysisBatchResult(2, 3, AnalysisStop.NONE), r)
        // En yeni önce.
        assertEquals(listOf("j5", "j4"), g.analyzed)
    }

    @Test
    fun sonucTemizlenir() {
        val i = MemoryAnalysisProcessor.parse("a", "fp", mapOf(
            "konular" to listOf("Aile", "aile", " İş ", "", 5), "yerler" to listOf("İzmir"),
            "ruhHaliOnerisi" to "BILINMEYEN", "ozet" to "  Özet  "
        ), "v", 1L)
        assertEquals(listOf("aile", "iş"), i.topics)
        assertEquals(listOf("İzmir"), i.places)
        assertEquals(null, i.moodSuggestion)
        assertEquals("Özet", i.summary)
        assertEquals(JournalMood.HAPPY, MemoryAnalysisProcessor.parse("a", "fp", mapOf("ruhHaliOnerisi" to "HAPPY"), "v", 1L).moodSuggestion)
    }
}
