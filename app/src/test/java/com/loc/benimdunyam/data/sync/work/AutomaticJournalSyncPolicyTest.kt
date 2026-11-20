package com.loc.benimdunyam.data.sync.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz A: otomatik içe alma kararı olay bazında verilir. Önceden buluttaki tek bir
 * silme ya da katalogdan yeni tek bir olay, tüm otomatik içe almayı kalıcı olarak
 * durduruyordu. Yeni kurallarda bu sayılar karara GİRMEZ; yalnızca güvenli
 * ("eligible") olay sayısı ve yarım kalan ruh hali aktarımı belirleyicidir.
 */
class AutomaticJournalSyncPolicyTest {

    @Test
    fun guvenliYeniAniVarsaIceAlinir() {
        assertTrue(AutomaticJournalSyncPolicy.canImport(eligible = 1, pendingMoodFinishes = 0))
        assertTrue(AutomaticJournalSyncPolicy.canImport(eligible = 12, pendingMoodFinishes = 0))
    }

    @Test
    fun guvenliAniYoksaIceAlmaYapilmaz() {
        assertFalse(AutomaticJournalSyncPolicy.canImport(eligible = 0, pendingMoodFinishes = 0))
    }

    @Test
    fun yarimKalanRuhHaliVarkenYeniIceAlmaYapilmaz() {
        assertFalse(AutomaticJournalSyncPolicy.canImport(eligible = 3, pendingMoodFinishes = 1))
    }

    @Test
    fun yarimKalanRuhHalleriOnceTamamlanir() {
        assertTrue(AutomaticJournalSyncPolicy.shouldResumePendingMoods(2))
        assertFalse(AutomaticJournalSyncPolicy.shouldResumePendingMoods(0))
    }

    @Test
    fun hepsiAlinincaTaramaTamamlanmisSayilir() {
        assertTrue(AutomaticJournalSyncPolicy.scanCompleted(eligibleBefore = 3, inserted = 3, pendingMoodFinishesAfter = 0))
        assertTrue(AutomaticJournalSyncPolicy.scanCompleted(eligibleBefore = 0, inserted = 0, pendingMoodFinishesAfter = 0))
    }

    @Test
    fun besSinirinaTakilanAnilarIcinTaramaErtelenmez() {
        // 8 uygun anı var, tek çalışmada 5 alındı: kalanlar için kota beklenmemeli.
        assertFalse(AutomaticJournalSyncPolicy.scanCompleted(eligibleBefore = 8, inserted = 5, pendingMoodFinishesAfter = 0))
    }

    @Test
    fun ruhHaliYarimKaldiysaTaramaErtelenmez() {
        assertFalse(AutomaticJournalSyncPolicy.scanCompleted(eligibleBefore = 2, inserted = 2, pendingMoodFinishesAfter = 1))
    }
}
