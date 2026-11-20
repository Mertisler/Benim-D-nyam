package com.loc.benimdunyam.domain.sync

import com.loc.benimdunyam.domain.sync.RemoteEditDecision.ALREADY_CURRENT
import com.loc.benimdunyam.domain.sync.RemoteEditDecision.APPLY
import com.loc.benimdunyam.domain.sync.RemoteEditDecision.CONFLICT
import com.loc.benimdunyam.domain.sync.RemoteEditDecision.LOCAL_AHEAD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Faz C: "bu cihazda gönderilmemiş değişiklik yoksa ve bulut daha yeniyse uygula;
 * aksi halde kullanıcıya bırak" kuralı. Bu kurallardaki bir hata, kullanıcının
 * yazdığı metnin diğer cihazın metniyle sessizce ezilmesi demektir.
 */
class RemoteEditPolicyTest {

    private fun classify(
        exists: Boolean = true,
        state: String? = "SYNCED",
        type: String? = "UPSERT",
        local: Long? = 3,
        remote: Long
    ) = RemoteEditPolicy.classify(exists, state, type, local, remote)

    // ---- classify ----

    @Test
    fun yalnizcaDigerCihazDuzenlediyseUygulanir() {
        assertEquals(APPLY, classify(local = 3, remote = 4))
        assertEquals(APPLY, classify(local = 3, remote = 10))
    }

    @Test
    fun ayniSurumdeyseDokunulmaz() {
        assertEquals(ALREADY_CURRENT, classify(local = 4, remote = 4))
    }

    @Test
    fun cihazDahaYeniyseDokunulmaz() {
        assertEquals(LOCAL_AHEAD, classify(local = 5, remote = 4))
    }

    @Test
    fun gonderilmemisYerelDegisiklikAsla_ezilmez() {
        // Bulut ne kadar yeni olursa olsun PENDING kayıt kullanıcı kararına kalır.
        assertEquals(CONFLICT, classify(state = "PENDING", local = 4, remote = 4))
        assertEquals(CONFLICT, classify(state = "PENDING", local = 4, remote = 99))
    }

    @Test
    fun cakismaOlarakIsaretlenmisKayitOtomatikCozulmez() {
        assertEquals(CONFLICT, classify(state = "CONFLICT", local = 4, remote = 5))
    }

    @Test
    fun yereldeSilinmisVeyaIzlenmeyenAniDuzenlemeSayilmaz() {
        assertEquals(CONFLICT, classify(exists = false, remote = 5))
        assertEquals(CONFLICT, classify(type = "DELETE", remote = 5))
        assertEquals(CONFLICT, classify(state = null, local = null, remote = 5))
    }

    @Test
    fun gecersizBulutSurumuReddedilir() {
        assertFailsWith<IllegalArgumentException> { classify(remote = 0) }
    }

    // ---- uploadWouldConflict ----

    @Test
    fun bulutta_esitVeyaYuksekSurumVarsaGonderilmez() {
        // İki cihaz da sürüm 3'ten düzenledi: ikisi de 4 göndermek ister.
        assertTrue(RemoteEditPolicy.uploadWouldConflict(4, listOf(1, 2, 3, 4)))
        assertTrue(RemoteEditPolicy.uploadWouldConflict(4, listOf(5)))
    }

    @Test
    fun bulutDahaEskiyseGonderilir() {
        assertFalse(RemoteEditPolicy.uploadWouldConflict(4, listOf(1, 2, 3)))
        assertFalse(RemoteEditPolicy.uploadWouldConflict(1, emptyList()))
    }

    // ---- versionToKeepLocal ----

    @Test
    fun benimkiniKoruTumBulutSurumlerindenYuksekOlur() {
        assertEquals(8, RemoteEditPolicy.versionToKeepLocal(highestRemoteRevision = 7, localVersion = 4))
        assertEquals(10, RemoteEditPolicy.versionToKeepLocal(highestRemoteRevision = 7, localVersion = 9))
        // Yeni sürüm yükleme öncesi kontrolden geçmeli.
        val version = RemoteEditPolicy.versionToKeepLocal(7, 7)
        assertFalse(RemoteEditPolicy.uploadWouldConflict(version, listOf(5, 6, 7, 7)))
    }

    @Test
    fun surumTasmasiReddedilir() {
        assertFailsWith<IllegalArgumentException> {
            RemoteEditPolicy.versionToKeepLocal(Long.MAX_VALUE, 1)
        }
    }
}
