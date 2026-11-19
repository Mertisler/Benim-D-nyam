package com.loc.benimdunyam.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Buluttan gelen olaylardan her anının en güncel sürümünü seçme ve
 * yerel ilişki kataloğuyla (kişi, dönem, bölüm) kapsam kontrolü.
 */
class FullSyncCoveragePolicyTest {

    private fun fact(
        journalId: String = "j1",
        revision: Long = 1,
        operation: String = "UPSERT",
        seasonId: String? = null,
        episodeId: String? = null,
        personIds: Set<String> = emptySet(),
        recordedAt: Long = 1_000
    ) = SyncRemoteFact(journalId, revision, operation, seasonId, episodeId, personIds, recordedAt)

    private val index = SyncRelationIndex(
        journalIds = setOf("j1", "j2"),
        seasonIds = setOf("s1", "s2"),
        episodesToSeason = mapOf("e1" to "s1", "e2" to "s2"),
        personIds = setOf("p1", "p2"),
        moodJournalIds = setOf("j1")
    )

    // ---- selectLatest ----

    @Test
    fun herAniIcinEnYuksekSurumSecilir() {
        val result = FullSyncCoveragePolicy.selectLatest(
            listOf(
                fact("j1", 1), fact("j1", 3), fact("j1", 2),
                fact("j2", 5, "DELETE"), fact("j2", 4)
            )
        )
        assertEquals(2, result.size)
        assertEquals(3L, result.single { it.journalId == "j1" }.revision)
        assertEquals("DELETE", result.single { it.journalId == "j2" }.operation)
    }

    @Test
    fun siraFarkiSonucuDegistirmez() {
        val facts = listOf(fact("j1", 1), fact("j1", 7), fact("j1", 3))
        val a = FullSyncCoveragePolicy.selectLatest(facts).single()
        val b = FullSyncCoveragePolicy.selectLatest(facts.reversed()).single()
        assertEquals(a, b)
        assertEquals(7L, a.revision)
    }

    @Test
    fun ayniOlayinTekrariSorunOlmaz() {
        val result = FullSyncCoveragePolicy.selectLatest(listOf(fact("j1", 2), fact("j1", 2)))
        assertEquals(1, result.size)
    }

    @Test
    fun ayniSurumdeCakisanOlaylarOtomatikSecilmez() {
        // Aynı sürüm hem UPSERT hem DELETE ise hangisinin doğru olduğu bilinemez.
        assertFailsWith<IllegalStateException> {
            FullSyncCoveragePolicy.selectLatest(listOf(fact("j1", 2), fact("j1", 2, "DELETE")))
        }
        assertFailsWith<IllegalStateException> {
            FullSyncCoveragePolicy.selectLatest(
                listOf(fact("j1", 2, personIds = setOf("p1")), fact("j1", 2, personIds = setOf("p2")))
            )
        }
    }

    @Test
    fun gecersizOlaylarReddedilir() {
        assertFailsWith<IllegalArgumentException> { FullSyncCoveragePolicy.selectLatest(listOf(fact(journalId = ""))) }
        assertFailsWith<IllegalArgumentException> { FullSyncCoveragePolicy.selectLatest(listOf(fact(revision = 0))) }
        assertFailsWith<IllegalArgumentException> { FullSyncCoveragePolicy.selectLatest(listOf(fact(operation = "PATCH"))) }
    }

    @Test
    fun bosListeBosDoner() {
        assertTrue(FullSyncCoveragePolicy.selectLatest(emptyList()).isEmpty())
    }

    // ---- inspect ----

    @Test
    fun tumIliskileriKatalogdaOlanOlayEksiksizdir() {
        val r = FullSyncCoveragePolicy.inspect(
            fact("j1", seasonId = "s1", episodeId = "e1", personIds = setOf("p1", "p2")),
            index, catalogTimeMillis = 2_000
        )
        assertEquals(0, r.missingRelations)
        assertFalse(r.oldCatalog)
    }

    @Test
    fun eksikKisilerTekTekSayilir() {
        val r = FullSyncCoveragePolicy.inspect(
            fact("j1", personIds = setOf("p1", "yok1", "yok2")), index, 2_000
        )
        assertEquals(2, r.missingRelations)
    }

    @Test
    fun katalogdaOlmayanAniVeDonemSayilir() {
        val r = FullSyncCoveragePolicy.inspect(fact("yok", seasonId = "yokSezon"), index, 2_000)
        assertEquals(2, r.missingRelations)
    }

    @Test
    fun bolumBaskaDonemeAitseEksikSayilir() {
        // e2, s2'ye ait; olay ise s1 diyor.
        val r = FullSyncCoveragePolicy.inspect(fact("j1", seasonId = "s1", episodeId = "e2"), index, 2_000)
        assertEquals(1, r.missingRelations)
    }

    @Test
    fun donemsizBolumEksikSayilir() {
        val r = FullSyncCoveragePolicy.inspect(fact("j1", episodeId = "e1"), index, 2_000)
        assertEquals(1, r.missingRelations)
    }

    @Test
    fun katalogdanSonraKaydedilenOlayEskiKatalogOlarakIsaretlenir() {
        val r = FullSyncCoveragePolicy.inspect(fact("j1", recordedAt = 3_000), index, 2_000)
        assertTrue(r.oldCatalog)
    }

    @Test
    fun silmeOlayiIliskiKontroluGerektirmez() {
        val r = FullSyncCoveragePolicy.inspect(
            fact("katalogdaYok", operation = "DELETE", seasonId = "yok", personIds = setOf("yok")),
            index, 2_000
        )
        assertEquals(0, r.missingRelations)
        assertFalse(r.oldCatalog)
    }

    @Test
    fun otomatikBirlestirmeVarsayilanOlarakKapali() {
        val r = FullSyncCoveragePolicy.inspect(fact("j1"), index, 2_000)
        assertFalse(r.canBeAutoMerged)
    }

    // ---- uniqueIds ----

    @Test
    fun benzersizKimliklerKabulEdilir() {
        assertEquals(setOf("a", "b"), FullSyncCoveragePolicy.uniqueIds(listOf("a", "b")) { it })
    }

    @Test
    fun bosVeTekrarlananKimliklerReddedilir() {
        assertFailsWith<IllegalArgumentException> { FullSyncCoveragePolicy.uniqueIds(listOf("a", " ")) { it } }
        assertFailsWith<IllegalArgumentException> { FullSyncCoveragePolicy.uniqueIds(listOf("a", "a")) { it } }
    }
}
