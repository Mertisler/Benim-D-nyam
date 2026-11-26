package com.loc.benimdunyam.feature.ask

import com.loc.benimdunyam.data.ai.AiConsent
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiConsentStore
import com.loc.benimdunyam.data.ai.AiGateway
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiResponse
import com.loc.benimdunyam.data.ai.AiResultKind
import com.loc.benimdunyam.data.ai.AiResultStore
import com.loc.benimdunyam.data.ai.AiTask
import com.loc.benimdunyam.data.ai.history.AskHistoryStore
import com.loc.benimdunyam.data.ai.history.AskedQuestion
import com.loc.benimdunyam.data.ai.insight.MemoryInsight
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Anılarına sor": cihazda anı bulma, kaynak numaraları ve iki adımlı akış. */
class AskMemoriesTest {
    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis

    private val ayse = Person("p_ayse", "Ayşe", null, 1, 1)
    private val elif = Person("p_elif", "Elif", null, 1, 1)
    private fun j(id: String, date: Long, title: String, content: String, vararg people: Person) =
        JournalEntry(id, title, content, date, date, date, people.toList())

    private val archive = listOf(
        j("bodrum", at(2025, 7, 28), "Korkuyu yendiğim sabah", "Ayşe elimi tuttu, kıyıdan uzaklaştık, dalgaların arasına açıldım.", ayse),
        j("kas", at(2026, 7, 11), "Kaş", "Tekneyle Kekova'ya gittik, suya atladım.", ayse, elif),
        j("kitap", at(2026, 2, 21), "Kitap kulübü", "Elif'le kitabın sonu hakkında tartıştık, sonra kafeye gittik.", elif),
        j("karar", at(2025, 10, 25), "Babamın sözü", "Babamı aradım. Kararımı o an verdim: öğreneceğim yere gideceğim."),
        j("is", at(2025, 9, 15), "Pazartesi", "Alarm çaldığında içimden ağlamak geldi, işte yorgunum.")
    )

    // ------------------------------------------------------------------ bulma

    @Test
    fun kisiVeTarihFiltresiUygulanir() {
        val f = QuestionFilter(personIds = listOf("p_ayse"), start = "2026-06-01", end = "2026-08-31", keywords = listOf("tatil", "gezi"))
        // "tatil" metinde yok; kişi+tarih daralttığı için yine de o dönemin anısı gelir.
        assertEquals(listOf("kas"), MemoryRetriever.retrieve(archive, f, timeZone = tz).map { it.id })
    }

    @Test
    fun ekliKelimelerVeYapayZekaEtiketleriylBulunur() {
        val f = QuestionFilter(keywords = listOf("deniz", "yüzmek", "dalga", "korku"))
        assertEquals("bodrum", MemoryRetriever.retrieve(archive, f, timeZone = tz).first().id)

        // Metinde "deniz" geçmeyen anı, analizdeki konu etiketiyle de bulunur.
        val insight = mapOf("kas" to MemoryInsight("kas", "fp", topics = listOf("deniz", "seyahat")))
        val onlyTag = MemoryRetriever.retrieve(archive, QuestionFilter(keywords = listOf("deniz")), insight, timeZone = tz)
        assertEquals(listOf("kas"), onlyTag.map { it.id })
    }

    @Test
    fun kararGibiEkliKelimeKokuyleEslesir() {
        val f = QuestionFilter(keywords = listOf("karar", "istifa", "ayrılmak"))
        assertTrue(MemoryRetriever.retrieve(archive, f, timeZone = tz).any { it.id == "karar" })
    }

    @Test
    fun hicbirSeyUymuyorsaBosDoner() {
        assertTrue(MemoryRetriever.retrieve(archive, QuestionFilter(keywords = listOf("japonya", "tokyo")), timeZone = tz).isEmpty())
        // Kelime yok, filtre de yok: bütün arşivi göndermeyiz.
        assertTrue(MemoryRetriever.retrieve(archive, QuestionFilter(), timeZone = tz).isEmpty())
    }

    @Test
    fun yalnizcaKisiSorusundaEskidenYeniyeSiralanir() {
        val r = MemoryRetriever.retrieve(archive, QuestionFilter(personIds = listOf("p_elif")), timeZone = tz)
        assertEquals(listOf("kitap", "kas"), r.map { it.id })
    }

    @Test
    fun filtreJsonunuOkur() {
        val f = QuestionFilter.from(mapOf(
            "kisiIdleri" to listOf("p_ayse", "p_ayse"), "baslangic" to "2026-06-01", "bitis" to "geçen yaz",
            "anahtarKelimeler" to listOf(" tatil ", ""), "konular" to null
        ))
        assertEquals(listOf("p_ayse"), f.personIds)
        assertEquals("2026-06-01", f.start)
        assertEquals(null, f.end)
        assertEquals(listOf("tatil"), f.keywords)
    }

    // ------------------------------------------------------------------ kaynaklar

    @Test
    fun kaynakIsaretleriGorunmeSirasinaGoreNumaralanir() {
        val r = CitationFormatter.format(
            "Kaş'a gittiniz [a3]. Daha önce Bodrum'da [a1][a1] açılmıştın [a3]. Bilinmeyen [a9].",
            mapOf("a1" to "bodrum", "a3" to "kas")
        )
        assertEquals("Kaş'a gittiniz [1]. Daha önce Bodrum'da [2] açılmıştın [1]. Bilinmeyen.", r.text)
        assertEquals(listOf("kas", "bodrum"), r.journalIds)
    }

    // ------------------------------------------------------------------ akış

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

    private class Gateway(val filter: Map<String, Any?>, val answer: String) : AiGateway {
        val calls = mutableListOf<Pair<AiTask, Map<String, Any?>>>()
        override suspend fun run(task: AiTask, input: Map<String, Any?>): AiResponse {
            calls += task to input
            return if (task == AiTask.QUESTION_FILTER) {
                AiResponse(task, AiResultKind.JSON, json = filter, version = "v", model = "m")
            } else {
                @Suppress("UNCHECKED_CAST")
                val ids = (input["anilar"] as List<Map<String, Any?>>).mapIndexed { i, m -> "a${i + 1}" to m["id"] as String }.toMap()
                AiResponse(task, AiResultKind.TEXT, text = answer, sources = ids, version = "v", model = "m")
            }
        }
    }

    private class History : AskHistoryStore {
        val items = mutableListOf<AskedQuestion>()
        override fun observeRecent(limit: Int): Flow<List<AskedQuestion>> = MutableStateFlow(items.toList())
        override suspend fun add(question: AskedQuestion): Long { items += question; return items.size.toLong() }
        override suspend fun delete(id: Long) = Unit
        override suspend fun clear() = items.clear()
    }

    private inner class Sources : AskSources {
        override suspend fun journals() = archive
        override suspend fun moods() = mapOf("kas" to "HAPPY")
        override suspend fun seasonTitles() = emptyMap<String, String>()
        override suspend fun insights() = emptyMap<String, MemoryInsight>()
    }

    @Test
    fun ikiAdimliAkisVeGecmis() = runBlocking {
        val gateway = Gateway(
            filter = mapOf("kisiIdleri" to listOf("p_ayse"), "baslangic" to "2026-06-01", "bitis" to "2026-08-31",
                "anahtarKelimeler" to listOf("tatil"), "konular" to emptyList<String>()),
            answer = "Kaş'a gitmişsin [a1]."
        )
        val history = History()
        val steps = mutableListOf<AskStep>()
        val service = AskMemoriesService(AiRepository(gateway, NoCache(), Consent()), Sources(), history)

        val r = service.ask("  Geçen yaz Ayşe ile nereye gittik?  ", onStep = { steps += it }, now = at(2026, 11, 26), timeZone = tz)

        assertEquals("Kaş'a gitmişsin [1].", r.answer)
        assertEquals(listOf("kas"), r.sourceIds)
        assertFalse(r.notFound)
        assertEquals(listOf(AskStep.SEARCHING, AskStep.WRITING), steps)

        // 1. istek: yalnızca soru, tarih ve kişi adları; anı metni yok.
        val (filterTask, filterInput) = gateway.calls[0]
        assertEquals(AiTask.QUESTION_FILTER, filterTask)
        assertEquals("2026-11-26", filterInput["bugun"])
        assertFalse(filterInput.toString().contains("Kekova"))
        // 2. istek: yalnızca bulunan anı, ruh haliyle.
        @Suppress("UNCHECKED_CAST")
        val sent = gateway.calls[1].second["anilar"] as List<Map<String, Any?>>
        assertEquals(listOf("kas"), sent.map { it["id"] })
        assertEquals("HAPPY", sent[0]["ruhHali"])

        assertEquals("Geçen yaz Ayşe ile nereye gittik?", history.items.single().question)
        assertEquals(listOf("kas"), history.items.single().sources)
    }

    @Test
    fun ilgiliAniYoksaIkinciIstekYapilmaz() = runBlocking {
        val gateway = Gateway(
            filter = mapOf("kisiIdleri" to emptyList<String>(), "baslangic" to null, "bitis" to null,
                "anahtarKelimeler" to listOf("japonya"), "konular" to emptyList<String>()),
            answer = "-"
        )
        val history = History()
        val r = AskMemoriesService(AiRepository(gateway, NoCache(), Consent()), Sources(), history)
            .ask("Japonya'da neler yaptım?", now = at(2026, 11, 26), timeZone = tz)
        assertTrue(r.notFound)
        assertEquals(AskMemoriesService.NOT_FOUND, r.answer)
        assertEquals(1, gateway.calls.size)
        assertTrue(history.items.single().notFound)
    }
}
