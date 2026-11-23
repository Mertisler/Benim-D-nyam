package com.loc.benimdunyam.domain.sync

import com.loc.benimdunyam.domain.sync.ConflictChoice.KEEP_BOTH
import com.loc.benimdunyam.domain.sync.ConflictChoice.KEEP_THIS_DEVICE
import com.loc.benimdunyam.domain.sync.ConflictChoice.MOVE_TO_TRASH
import com.loc.benimdunyam.domain.sync.ConflictChoice.TAKE_OTHER_DEVICE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aşama 2: sade çakışma ekranında her durumda yalnızca anlamlı ve güvenli
 * seçenekler gösterilir. Hiçbir durumda kullanıcı "seçeneksiz" ama çözülebilir
 * bir kartla karşılaşmaz; belirsiz durumda yerel sürümü korumak her zaman mümkündür.
 */
class ConflictChoicePolicyTest {

    private fun choices(local: Boolean, localDeleted: Boolean = false, remoteDelete: Boolean = false, single: Boolean = true) =
        ConflictChoicePolicy.choices(local, localDeleted, remoteDelete, single)

    @Test
    fun ikiCihazdaFarkliDuzenlemeUcSecenekSunar() {
        assertEquals(listOf(KEEP_THIS_DEVICE, TAKE_OTHER_DEVICE, KEEP_BOTH), choices(local = true))
    }

    @Test
    fun digerCihazdaSilinenAniIcinTutVeyaCopeTasi() {
        val result = choices(local = true, remoteDelete = true)
        assertEquals(listOf(KEEP_THIS_DEVICE, MOVE_TO_TRASH), result)
        // Kalıcı silme seçeneği yoktur; çöp kutusu 30 gün geri alınabilir.
        assertFalse(TAKE_OTHER_DEVICE in result)
    }

    @Test
    fun buTelefondaSilinenAmaDigerdeDuzenlenenGeriGetirilebilir() {
        assertEquals(listOf(TAKE_OTHER_DEVICE, KEEP_THIS_DEVICE), choices(local = false, localDeleted = true))
    }

    @Test
    fun belirsizBulutKaydindaYalnizcaYerelSurumKorunur() {
        assertEquals(listOf(KEEP_THIS_DEVICE), choices(local = true, single = false))
        assertEquals(listOf(KEEP_THIS_DEVICE), choices(local = false, localDeleted = true, single = false))
    }

    @Test
    fun ikiTarafindaSildigiDurumdaUzakSurumAlinmaz() {
        val result = choices(local = false, localDeleted = true, remoteDelete = true)
        assertFalse(TAKE_OTHER_DEVICE in result)
    }

    @Test
    fun cihazdaHicKaydiOlmayanAniIcinSecenekYok() {
        assertTrue(choices(local = false).isEmpty())
    }
}
