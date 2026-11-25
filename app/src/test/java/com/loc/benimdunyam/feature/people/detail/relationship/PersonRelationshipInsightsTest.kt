package com.loc.benimdunyam.feature.people.detail.relationship

import com.loc.benimdunyam.domain.model.JournalMood
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kişi detayı içgörüleri: az veriyle genelleme yapmaz, tarihleri doğru hesaplar. */
class PersonRelationshipInsightsTest {

    private val tz = TimeZone.getTimeZone("Europe/Istanbul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply {
            clear(); set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun memory(id: String, millis: Long, content: String = "metin", title: String = id) =
        RelationshipMemory(id, title, content, millis)

    private fun calc(
        memories: List<RelationshipMemory>,
        moods: Map<String, String> = emptyMap(),
        now: Long = at(2026, 11, 23),
        topSeason: String? = null
    ) = PersonRelationshipInsightsCalculator.calculate("Ayşe", memories, moods, topSeason, now, tz)

    @Test
    fun aniYoksaBosVeNazikBirMesaj() {
        val result = calc(emptyList())
        assertTrue(result.isEmpty)
        assertTrue(result.summary.contains("henüz"))
    }

    @Test
    fun tekAnidaGenellemeYapilmaz() {
        val result = calc(listOf(memory("j1", at(2026, 5, 1))))
        assertTrue(result.summary.contains("yeni başlıyor"))
        assertNull(result.moodComparison)
        assertNull(result.weekendPercent)
        assertNull(result.dominantSeasonOfYear)
        assertNull(result.longestQuietPeriod)
    }

    @Test
    fun ilkVeSonAniVeBirlikteGecenYillar() {
        val result = calc(listOf(
            memory("j2", at(2024, 3, 10)),
            memory("j1", at(2019, 12, 1)),
            memory("j3", at(2026, 11, 2))
        ))
        assertEquals(at(2019, 12, 1), result.firstMemoryAt)
        assertEquals(at(2026, 11, 2), result.lastMemoryAt)
        // 1 Aralık 2019 → 23 Kasım 2026: henüz 7. yıl dolmadı.
        assertEquals(6, result.yearsTogether)
        assertEquals(21L, result.daysSinceLastMemory)
        assertTrue(result.summary.startsWith("Ayşe ile 2019 yılından beri 3 anın var."))
    }

    @Test
    fun yilDonumuYalnizcaAyniGunde() {
        val memories = listOf(memory("j1", at(2021, 11, 23, hour = 9)), memory("j2", at(2025, 1, 1)))
        assertEquals(5, calc(memories, now = at(2026, 11, 23, hour = 20)).anniversaryYearToday)
        assertNull(calc(memories, now = at(2026, 11, 24)).anniversaryYearToday)
        // İlk yıl dolmadan yıl dönümü yok.
        assertNull(calc(listOf(memory("j1", at(2026, 11, 23, hour = 8))), now = at(2026, 11, 23, hour = 20))
            .anniversaryYearToday)
    }

    @Test
    fun enUzunSessizDonemBulunurKisaAralarSayilmaz() {
        val result = calc(listOf(
            memory("j1", at(2022, 1, 1)),
            memory("j2", at(2022, 2, 1)),
            memory("j3", at(2023, 1, 1)),   // 11 aylık ara
            memory("j4", at(2023, 2, 1))
        ))
        val quiet = requireNotNull(result.longestQuietPeriod)
        assertEquals(at(2022, 2, 1), quiet.fromMillis)
        assertEquals(at(2023, 1, 1), quiet.toMillis)
        assertEquals(11, quiet.months)

        val dense = calc(listOf(memory("a", at(2022, 1, 1)), memory("b", at(2022, 3, 1))))
        assertNull(dense.longestQuietPeriod)
    }

    @Test
    fun ruhHaliGenelOrtalamaylaKarsilastirilir() {
        val memories = (1..4).map { memory("p$it", at(2025, it, 5)) }
        val moods = mapOf(
            "p1" to "HAPPY", "p2" to "HAPPY", "p3" to "HAPPY", "p4" to "SAD",
            // Kişinin olmadığı anılar (genel ortalama)
            "o1" to "SAD", "o2" to "SAD", "o3" to "SAD", "o4" to "NEUTRAL"
        )
        val comparison = requireNotNull(calc(memories, moods).moodComparison)
        assertEquals(JournalMood.HAPPY, comparison.mood)
        assertEquals(75, comparison.personPercent)
        assertEquals(37, comparison.overallPercent) // 3 / 8
    }

    @Test
    fun azRuhHaliVerisindeKarsilastirmaYok() {
        val memories = (1..4).map { memory("p$it", at(2025, it, 5)) }
        assertNull(calc(memories, mapOf("p1" to "HAPPY", "p2" to "HAPPY")).moodComparison)
    }

    @Test
    fun haftaSonuVeMevsimAliskanligi() {
        // 2025: 5 Temmuz Cumartesi, 6 Temmuz Pazar, 12 Temmuz Cumartesi, 14 Ocak Salı
        val result = calc(listOf(
            memory("a", at(2025, 7, 5)), memory("b", at(2025, 7, 6)),
            memory("c", at(2025, 7, 12)), memory("d", at(2025, 1, 14))
        ))
        assertEquals(75, result.weekendPercent)
        assertEquals("yaz", result.dominantSeasonOfYear)
        assertTrue(result.summary.contains("yaz aylarında"))
    }

    @Test
    fun esitDagilimdaMevsimSoylenmez() {
        val result = calc(listOf(
            memory("a", at(2025, 1, 5)), memory("b", at(2025, 4, 6)),
            memory("c", at(2025, 7, 12)), memory("d", at(2025, 10, 14))
        ))
        assertNull(result.dominantSeasonOfYear)
    }

    @Test
    fun kelimeSayisiVeEnUzunAni() {
        val result = calc(listOf(
            memory("a", at(2025, 1, 1), content = "Bugün   deniz kenarındaydık.", title = "Deniz"),
            memory("b", at(2025, 2, 1), content = "Kısa — ama 😊 güzel", title = "Kısa"),
            memory("c", at(2025, 3, 1), content = "Uzun bir gün: sabah kahve, öğlen yürüyüş, akşam film.", title = "Uzun")
        ))
        // "—" ve "😊" kelime sayılmaz.
        assertEquals(3 + 3 + 9, result.totalWords)
        assertEquals("c", result.longestMemory?.journalId)
        assertEquals(9, result.longestMemory?.words)
    }

    @Test
    fun ozetteKisiAdinaEkGetirilmez() {
        // Ünlü uyumu hatası olmasın: ad hep "X ile" kalıbıyla kullanılır.
        val summary = calc(listOf(memory("a", at(2020, 1, 1)), memory("b", at(2021, 1, 1))),
            topSeason = "Üniversite").summary
        assertTrue(summary.contains("Ayşe ile"))
        assertTrue(summary.contains("En yoğun dönemin: Üniversite."))
        assertFalse(summary.contains("Ayşe'"))
    }
}
