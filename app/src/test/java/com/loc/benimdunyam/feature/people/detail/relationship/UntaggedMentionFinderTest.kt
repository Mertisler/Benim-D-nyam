package com.loc.benimdunyam.feature.people.detail.relationship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Türkçe eklere ve büyük/küçük harf kurallarına duyarlı ad eşleştirmesi. */
class UntaggedMentionFinderTest {

    private fun journal(id: String, content: String, tagged: Set<String> = emptySet(), date: Long = 1, title: String = id) =
        UntaggedMentionFinder.Candidate(id, title, content, date, tagged)

    private fun find(name: String, vararg journals: UntaggedMentionFinder.Candidate) =
        UntaggedMentionFinder.find("p1", name, journals.toList()).map { it.journalId }

    @Test
    fun yalinAdVeKesmeIsaretliEklerEslesir() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            find("Ayşe",
                journal("a", "Bugün Ayşe geldi.", date = 4),
                journal("b", "Ayşe'yle kahve içtik.", date = 3),
                journal("c", "Ayşe’nin doğum günü", date = 2),   // tipografik kesme
                journal("d", "Hediyeyi AYŞE'YE verdim", date = 1))
        )
    }

    @Test
    fun adinParcasiOlanBaskaKelimelerEslesmez() {
        assertTrue(find("Ayşe", journal("a", "Ayşegül ile görüştük. Mehmetçik."), journal("b", "maAyşe")).isEmpty())
        assertTrue(find("Ali", journal("a", "Alişan ve Kalinka"), journal("b", "Halil")).isEmpty())
    }

    @Test
    fun turkceBuyukKucukHarfKurallari() {
        // I/ı ve İ/i Türkçe kurallarla eşleşir.
        assertEquals(listOf("a"), find("Işıl", journal("a", "ışıl'la yürüdük")))
        assertEquals(listOf("a"), find("İrem", journal("a", "irem'in evinde")))
        // "Irmak" ile "irmak" Türkçe'de farklı harflerle başlar; eşleşmemeli.
        assertTrue(find("Irmak", journal("a", "irmak kenarında")).isEmpty())
    }

    @Test
    fun zatenEtiketliAnilarOnerilmez() {
        assertTrue(find("Ayşe", journal("a", "Ayşe'yle", tagged = setOf("p1"))).isEmpty())
        assertEquals(listOf("a"), find("Ayşe", journal("a", "Ayşe'yle", tagged = setOf("p2"))))
    }

    @Test
    fun baslikDaTaranirVeAlintiUretilir() {
        val result = UntaggedMentionFinder.find("p1", "Mehmet",
            listOf(journal("a", "Uzun bir gündü ve akşam Mehmet'le uzun uzun konuştuk, gece geç saate kadar.", title = "Sohbet")))
        assertEquals(1, result.size)
        assertTrue(result.single().excerpt.contains("Mehmet'le"))
        assertEquals(listOf("a"), find("Mehmet", journal("a", "metin", title = "Mehmet ile tatil")))
    }

    @Test
    fun cokKisaAdlarIcinOneriYapilmaz() {
        assertTrue(find("Al", journal("a", "Al bunu")).isEmpty())
        assertTrue(find("  ", journal("a", "metin")).isEmpty())
    }

    @Test
    fun ozelKarakterliAdGuvenliEslesir() {
        assertEquals(listOf("a"), find("Dr. Can", journal("a", "Dr. Can'a gittim")))
        assertTrue(find("Dr. Can", journal("a", "DrX Can")).isEmpty())
    }

    @Test
    fun enYeniAnilarOnceVeSinirli() {
        val many = (1..30).map { journal("j$it", "Ayşe", date = it.toLong()) }.toTypedArray()
        val result = UntaggedMentionFinder.find("p1", "Ayşe", many.toList(), limit = 5)
        assertEquals(listOf("j30", "j29", "j28", "j27", "j26"), result.map { it.journalId })
    }
}
