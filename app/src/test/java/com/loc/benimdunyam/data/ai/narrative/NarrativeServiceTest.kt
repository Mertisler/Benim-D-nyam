package com.loc.benimdunyam.data.ai.narrative

import com.loc.benimdunyam.data.ai.AiConsent
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiConsentStore
import com.loc.benimdunyam.data.ai.AiGateway
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiResponse
import com.loc.benimdunyam.data.ai.AiResultKind
import com.loc.benimdunyam.data.ai.AiResultStore
import com.loc.benimdunyam.data.ai.AiTask
import com.loc.benimdunyam.data.ai.insight.MemoryInsight
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.feature.ask.AskSources
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dönem, yıl ve kişi hikâyesi yazıları. */
class NarrativeServiceTest {
    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis
    private val now = at(2026, 11, 26)
    private val ayse = Person("p1", "Ayşe", null, 1, 1)

    private fun j(id: String, date: Long, content: String = "metin $id", vararg people: Person) =
        JournalEntry(id, "Başlık $id", content, date, date, date, people.toList())

    private class Consent : AiConsentSource {
        override val consent: StateFlow<AiConsent> = MutableStateFlow(AiConsent(true, AiConsentStore.CONSENT_VERSION, 1))
        override fun accept(now: Long) = Unit
        override fun revoke() = Unit
    }

    private class NoCache : AiResultStore {
        override suspend fun get(task: AiTask, inputHash: String): AiResponse? = null
        override suspend fun put(inputHash: String, response: AiResponse, now: Long) = Unit
        override suspend fun clear() = Unit
        override suspend fun count() = 0
    }

    private class Gateway : AiGateway {
        val calls = mutableListOf<Pair<AiTask, Map<String, Any?>>>()
        override suspend fun run(task: AiTask, input: Map<String, Any?>): AiResponse {
            calls += task to input
            @Suppress("UNCHECKED_CAST")
            val ids = (input["anilar"] as List<Map<String, Any?>>).mapIndexed { i, m -> "a${i + 1}" to m["id"] as String }.toMap()
            return AiResponse(task, AiResultKind.TEXT, text = "Güzel bir dönem [a2][a1].", sources = ids, version = "v1", model = "m")
        }
    }

    private class Store : NarrativeStore {
        val rows = mutableMapOf<String, StoredNarrative>()
        override fun observe(scopeKey: String): Flow<StoredNarrative?> = MutableStateFlow(rows[scopeKey])
        override suspend fun save(narrative: StoredNarrative) { rows[narrative.scopeKey] = narrative }
        override suspend fun delete(scopeKey: String) { rows.remove(scopeKey) }
    }

    private inner class Sources(var list: List<JournalEntry>) : AskSources {
        override suspend fun journals() = list
        override suspend fun moods() = emptyMap<String, String>()
        override suspend fun seasonTitles() = emptyMap<String, String>()
        override suspend fun insights() = emptyMap<String, MemoryInsight>()
    }

    private val archive = listOf(
        j("a", at(2025, 3, 1), "Ayşe ile yürüdük", ayse),
        j("b", at(2025, 8, 1), "Deniz", ayse),
        j("c", at(2026, 5, 1)),
        j("d", at(2026, 10, 1), "Ayşe aradı", ayse),
        j("gelecek", at(2027, 1, 1), "henüz yaşanmadı", ayse)
    )

    private fun service(sources: Sources = Sources(archive), gateway: Gateway = Gateway(), store: Store = Store()) =
        NarrativeService(AiRepository(gateway, NoCache(), Consent()), sources, store)

    @Test
    fun kapsamlarDogruAnilariSecer() = runBlocking {
        val s = service()
        assertEquals(2, s.status(NarrativeScope.Year(2025), now, tz).memoryCount)
        assertEquals(2, s.status(NarrativeScope.Period(LocalAnalyticsPeriod.LAST_12_MONTHS), now, tz).memoryCount)
        // Gelecek tarihli anı sayılmaz.
        assertEquals(3, s.status(NarrativeScope.Person("p1", "Ayşe"), now, tz).memoryCount)
        assertFalse(s.status(NarrativeScope.Year(2024), now, tz).enough)
    }

    @Test
    fun yaziUretilirSaklanirVeKaynaklarNumaralanir() = runBlocking {
        val gateway = Gateway()
        val store = Store()
        val s = service(gateway = gateway, store = store)
        val r = s.generate(NarrativeScope.Person("p1", "Ayşe"), now, tz)

        assertEquals("Güzel bir dönem [1][2].", r.text)
        assertEquals(listOf("b", "a"), r.sources)
        assertEquals(r, store.rows["person:p1"])
        val (task, input) = gateway.calls.single()
        assertEquals(AiTask.PERSON_STORY, task)
        assertEquals("Ayşe", input["kisiAdi"])
        assertEquals("2026-11-26", input["bugun"])
        // Kronolojik sıra.
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("a", "b", "d"), (input["anilar"] as List<Map<String, Any?>>).map { it["id"] })
    }

    @Test
    fun guncellikGunDegisinceBozulmazAniDegisinceBozulur() = runBlocking {
        val sources = Sources(archive)
        val s = service(sources = sources)
        val scope = NarrativeScope.Year(2025)
        val saved = s.generate(scope, now, tz)
        assertEquals(saved.inputHash, s.status(scope, now + 86_400_000L * 3, tz).currentHash)

        sources.list = archive.map { if (it.id == "b") it.copy(content = "Deniz ve kum") else it }
        assertNotEquals(saved.inputHash, s.status(scope, now, tz).currentHash)
    }

    @Test
    fun yetersizAnidaUretilmez() = runBlocking {
        val gateway = Gateway()
        val error = runCatching { service(gateway = gateway).generate(NarrativeScope.Year(2024), now, tz) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun cokAniliKapsamdaEnDolu60AniSecilirVeSinirAsilmaz() {
        val many = (1..80).map { i -> j("j$i", at(2025, 1, 1) + i * 86_400_000L, content = "x".repeat(if (i % 2 == 0) 1500 else 50)) }
        val selected = NarrativeInputBuilder.select(many)
        assertEquals(60, selected.size)
        // 40 uzun anının hepsi seçildi.
        assertTrue((2..80 step 2).all { i -> selected.any { it.id == "j$i" } })
        assertEquals(selected.sortedBy { it.entryDate }, selected)
        assertTrue(NarrativeInputBuilder.contentBudget(60) * 60 <= NarrativeInputBuilder.MAX_TOTAL_CHARS)
        assertEquals(6000, NarrativeInputBuilder.contentBudget(3))
    }
}
