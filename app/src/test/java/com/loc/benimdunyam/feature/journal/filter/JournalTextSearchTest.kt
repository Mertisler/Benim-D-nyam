package com.loc.benimdunyam.feature.journal.filter

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arşiv araması: Türkçe ekler, Türkçe karakter farkı, sıralama ve vurgular. */
class JournalTextSearchTest {

    private val ayse = Person("p1", "Ayşe", null, 1, 1)
    private val elif = Person("p2", "Elif", null, 1, 1)

    private fun j(id: String, title: String, content: String, date: Long = 1_000L, people: List<Person> = emptyList()) =
        JournalEntry(id, title, content, date, date, date, people)

    private fun ids(query: String, vararg journals: JournalEntry) =
        JournalTextSearch.search(journals.toList(), query).journals.map { it.id }

    @Test
    fun normallestirmeUzunluguKorurVeTurkceHarfleriSadelestirir() {
        val text = "AYŞE'YLE İstanbul'da IŞIK gördük"
        val normalized = JournalTextSearch.normalize(text)
        assertEquals(text.length, normalized.length)
        assertEquals("ayse'yle istanbul'da isik gorduk", normalized)
    }

    @Test
    fun kokBulma() {
        assertEquals("deniz", JournalTextSearch.stem("denizde"))
        assertEquals("ameliyat", JournalTextSearch.stem("ameliyati"))
        assertEquals("tartis", JournalTextSearch.stem("tartistik"))
        assertEquals("sahiplen", JournalTextSearch.stem("sahiplendik"))
        assertEquals("kedi", JournalTextSearch.stem("kedimizi"))
        assertEquals("anne", JournalTextSearch.stem("annem"))
        // Kök en az dört harf kalmalı.
        assertEquals("kedi", JournalTextSearch.stem("kedi"))
        assertEquals("ev", JournalTextSearch.stem("ev"))
    }

    @Test
    fun ekliVeAksansizYazimlarBulunur() {
        val a = j("a", "Sahil", "Ayşe'yle denize girdik, yalnızlığımı unuttum.")
        val b = j("b", "İş", "Toplantı uzun sürdü.")
        assertEquals(listOf("a"), ids("ayse", a, b))
        assertEquals(listOf("a"), ids("Ayşe'yle", a, b))
        assertEquals(listOf("a"), ids("denizde", a, b))
        assertEquals(listOf("a"), ids("yalniz", a, b))
        assertEquals(listOf("b"), ids("TOPLANTI", a, b))
    }

    @Test
    fun kelimeOrtasindaEslesmeYapilmaz() {
        // "niz" deniz kelimesinin ortasında geçiyor; kelime başı olmadığı için eşleşmez.
        assertTrue(ids("niz", j("a", "", "Deniz çok güzeldi.")).isEmpty())
    }

    @Test
    fun birdenFazlaKelimeOnceHepsiniArar() {
        val a = j("a", "Pamuk", "Kedimizi barınaktan aldık.")
        val b = j("b", "Barınak", "Barınakta gönüllü oldum.")
        val all = JournalTextSearch.search(listOf(a, b), "kedi barınak")
        assertEquals(listOf("a"), all.journals.map { it.id })
        assertFalse(all.isPartialMatch)
    }

    @Test
    fun hepsiniIcerenYoksaBazilariniIcerenlerIsaretlenir() {
        val a = j("a", "Pamuk", "Kedimizi barınaktan aldık.")
        val partial = JournalTextSearch.search(listOf(a), "kedi japonya")
        assertEquals(listOf("a"), partial.journals.map { it.id })
        assertTrue(partial.isPartialMatch)

        val none = JournalTextSearch.search(listOf(a), "japonya")
        assertTrue(none.journals.isEmpty())
        assertFalse(none.isPartialMatch)
    }

    @Test
    fun baglaclarYokSayilir() {
        val a = j("a", "", "Kedi ile köpek oynadı.")
        val result = JournalTextSearch.search(listOf(a), "kedi ve köpek")
        assertEquals(listOf("a"), result.journals.map { it.id })
        assertFalse(result.isPartialMatch)
        assertEquals(listOf("kedi", "kopek"), JournalTextSearch.parseQuery("kedi ve köpek").map { it.stem })
    }

    @Test
    fun baslikEslesmesiOnceEsitlikteYeniAniOnce() {
        val icerikte = j("icerik", "Bir gün", "Bugün denize girdik.", date = 3_000)
        val baslikta = j("baslik", "Deniz", "Güzel bir gündü.", date = 1_000)
        val eskiIcerikte = j("eski", "Başka gün", "Yine denize girdik.", date = 2_000)
        assertEquals(listOf("baslik", "icerik", "eski"), ids("deniz", icerikte, baslikta, eskiIcerikte))
    }

    @Test
    fun kisiAdiylaBulunurVeIsaretlenir() {
        val a = j("a", "Kitap kulübü", "Toplantıdan sonra kafeye gittik.", people = listOf(elif))
        val result = JournalTextSearch.search(listOf(a), "elif")
        assertEquals(listOf("a"), result.journals.map { it.id })
        assertEquals(listOf("Elif"), result.highlights.getValue("a").matchedPeople)
    }

    @Test
    fun vurguBaslikVeAlintidaDogruYeriGosterir() {
        val content = "Uzun bir giriş cümlesi. ".repeat(12) + "Sonunda Ayşe'yle denize girdik ve çok güldük."
        val a = j("a", "Denizde bir gün", content, people = listOf(ayse))
        val h = JournalTextSearch.search(listOf(a), "deniz").highlights.getValue("a")

        assertEquals(listOf(0..6), h.titleRanges)
        assertEquals("Denizde", "Denizde bir gün".substring(h.titleRanges[0]))

        assertNotNull(h.snippet)
        val snippet = h.snippet!!
        assertTrue(snippet.startsWith("…"))
        assertEquals(1, h.snippetRanges.size)
        assertEquals("denize", snippet.substring(h.snippetRanges[0]))
    }

    @Test
    fun bosSorgudaSiraKorunur() {
        val list = listOf(j("b", "B", "x", 1), j("a", "A", "y", 2))
        assertEquals(listOf("b", "a"), JournalTextSearch.search(list, "   ").journals.map { it.id })
    }

    @Test
    fun digerFiltrelerAramadanOnceUygulanir() {
        val a = j("a", "Deniz", "x", date = 1_000, people = listOf(ayse))
        val b = j("b", "Deniz", "y", date = 5_000)
        val result = JournalArchiveFilterEngine.search(
            listOf(a, b),
            JournalArchiveFilters(searchQuery = "deniz", selectedPersonId = "p1")
        )
        assertEquals(listOf("a"), result.journals.map { it.id })
    }
}
