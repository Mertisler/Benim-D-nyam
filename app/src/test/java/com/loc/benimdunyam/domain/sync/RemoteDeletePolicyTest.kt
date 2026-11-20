package com.loc.benimdunyam.domain.sync

import com.loc.benimdunyam.domain.sync.RemoteDeleteDecision.ALREADY_GONE
import com.loc.benimdunyam.domain.sync.RemoteDeleteDecision.CONFLICT
import com.loc.benimdunyam.domain.sync.RemoteDeleteDecision.MOVE_TO_TRASH
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Faz B: uzaktan silme yalnızca bu cihazda değişiklik yoksa uygulanır ve o
 * zaman bile anı kalıcı silinmez, 30 gün "Son silinenler"de tutulur.
 */
class RemoteDeletePolicyTest {

    private fun classify(
        exists: Boolean = true,
        state: String? = "SYNCED",
        type: String? = "UPSERT",
        local: Long? = 3,
        remote: Long
    ) = RemoteDeletePolicy.classify(exists, state, type, local, remote)

    @Test
    fun yalnizcaDigerCihazSildiyseCopKutusunaTasinir() {
        assertEquals(MOVE_TO_TRASH, classify(local = 3, remote = 4))
    }

    @Test
    fun buCihazdaGonderilmemisDuzenlemeVarsaSilinmez() {
        assertEquals(CONFLICT, classify(state = "PENDING", local = 4, remote = 9))
        assertEquals(CONFLICT, classify(state = "CONFLICT", local = 4, remote = 9))
    }

    @Test
    fun ayniVeyaEskiSurumdekiSilmeUygulanmaz() {
        // Diğer cihaz bu cihazın en son sürümünü görmeden silmiş: kullanıcıya bırak.
        assertEquals(CONFLICT, classify(local = 4, remote = 4))
        assertEquals(CONFLICT, classify(local = 5, remote = 4))
    }

    @Test
    fun izlenmeyenAniSilinmez() {
        assertEquals(CONFLICT, classify(state = null, type = null, local = null, remote = 4))
    }

    @Test
    fun cihazdaOlmayanAniIcinIslemYok() {
        assertEquals(ALREADY_GONE, classify(exists = false, remote = 4))
    }

    @Test
    fun gecersizSurumReddedilir() {
        assertFailsWith<IllegalArgumentException> { classify(remote = 0) }
    }

    @Test
    fun baskaCihazdaGeriGetirilenAniYeniGibiAlinir() {
        assertTrue(RemoteDeletePolicy.isRestoredElsewhere("SYNCED", "DELETE", 5, 6))
        // Silme henüz buluta gitmediyse veya bulut daha eski ise alınmaz.
        assertFalse(RemoteDeletePolicy.isRestoredElsewhere("PENDING", "DELETE", 5, 6))
        assertFalse(RemoteDeletePolicy.isRestoredElsewhere("SYNCED", "DELETE", 6, 6))
        assertFalse(RemoteDeletePolicy.isRestoredElsewhere("SYNCED", "UPSERT", 5, 6))
    }

    @Test
    fun otuzGunSonraSuresiDolar() {
        val trashedAt = 1_000_000L
        val expires = RemoteDeletePolicy.expiresAt(trashedAt)
        assertEquals(trashedAt + TimeUnit.DAYS.toMillis(30), expires)
        assertFalse(RemoteDeletePolicy.isExpired(expires, expires - 1))
        assertTrue(RemoteDeletePolicy.isExpired(expires, expires))
    }

    @Test
    fun kalanGunYukariYuvarlanir() {
        val day = TimeUnit.DAYS.toMillis(1)
        assertEquals(30, RemoteDeletePolicy.daysLeft(30 * day, 0))
        assertEquals(1, RemoteDeletePolicy.daysLeft(day, day - 1))
        assertEquals(0, RemoteDeletePolicy.daysLeft(day, day))
    }

    @Test
    fun tasmaGuvenlidir() {
        assertEquals(Long.MAX_VALUE, RemoteDeletePolicy.expiresAt(Long.MAX_VALUE - 1))
    }
}
