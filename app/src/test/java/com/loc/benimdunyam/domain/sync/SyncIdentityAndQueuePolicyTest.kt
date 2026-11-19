package com.loc.benimdunyam.domain.sync

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Bulut olay kimlikleri, sürüm sayacı ve kuyruk erişim kuralları.
 * Bu kurallardaki bir hata; aynı değişikliğin iki kez yüklenmesine,
 * eski bir sürümün yenisinin üzerine yazmasına veya bir hesabın verisinin
 * diğer hesabın kuyruğuna girmesine yol açar.
 */
class SyncIdentityAndQueuePolicyTest {

    // ---- SyncEventIdentity ----

    @Test
    fun sha256BilinenTestVektoruyleUyusur() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SyncEventIdentity.sha256("abc")
        )
    }

    @Test
    fun ayniGirdiHerZamanAyniOlayKimliginiUretir() {
        // Başarısız isteğin tekrarı aynı belgeye yazmalı (idempotent).
        val a = SyncEventIdentity.eventId("uid-1", "j1", 3, "UPSERT")
        val b = SyncEventIdentity.eventId("uid-1", "j1", 3, "UPSERT")
        assertEquals(a, b)
        assertTrue(a.startsWith("e_"))
        assertEquals(2 + 64, a.length)
    }

    @Test
    fun herAlanOlayKimliginiDegistirir() {
        val base = SyncEventIdentity.eventId("uid-1", "j1", 3, "UPSERT")
        assertNotEquals(base, SyncEventIdentity.eventId("uid-2", "j1", 3, "UPSERT"))
        assertNotEquals(base, SyncEventIdentity.eventId("uid-1", "j2", 3, "UPSERT"))
        assertNotEquals(base, SyncEventIdentity.eventId("uid-1", "j1", 4, "UPSERT"))
        assertNotEquals(base, SyncEventIdentity.eventId("uid-1", "j1", 3, "DELETE"))
    }

    @Test
    fun kimliklerGunlukKimliginiAcikEtmez() {
        val id = SyncEventIdentity.eventId("uid-1", "gizli-gunluk-id", 1, "UPSERT")
        val hash = SyncEventIdentity.journalHash("uid-1", "gizli-gunluk-id")
        assertFalse(id.contains("gizli"))
        assertFalse(hash.contains("gizli"))
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun ayniGunlukFarkliHesaplardaFarkliOzetUretir() {
        assertNotEquals(
            SyncEventIdentity.journalHash("uid-1", "j1"),
            SyncEventIdentity.journalHash("uid-2", "j1")
        )
    }

    @Test
    fun kimliklerCihazDilindenEtkilenmez() {
        // Türkçe telefonda üretilen kimlik, İngilizce telefondakiyle aynı olmalı;
        // aksi halde iki cihaz aynı olayı farklı belgelere yazar.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val us = SyncEventIdentity.eventId("UID-I", "Jİ", 7, "UPSERT")
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val tr = SyncEventIdentity.eventId("UID-I", "Jİ", 7, "UPSERT")
            assertEquals(us, tr)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun gecersizKimlikGirdileriReddedilir() {
        assertFailsWith<IllegalArgumentException> { SyncEventIdentity.journalHash("", "j1") }
        assertFailsWith<IllegalArgumentException> { SyncEventIdentity.journalHash("uid", " ") }
        assertFailsWith<IllegalArgumentException> { SyncEventIdentity.eventId("uid", "j1", 0, "UPSERT") }
        assertFailsWith<IllegalArgumentException> { SyncEventIdentity.eventId("uid", "j1", 1, "upsert") }
    }

    // ---- JournalSyncChangePolicy ----

    @Test
    fun surumBirdenBaslarVeHepIleriGider() {
        assertEquals(1L, JournalSyncChangePolicy.nextVersion(null))
        assertEquals(2L, JournalSyncChangePolicy.nextVersion(1L))
        assertEquals(Long.MAX_VALUE, JournalSyncChangePolicy.nextVersion(Long.MAX_VALUE - 1))
    }

    @Test
    fun gecersizVeyaTasanSurumReddedilir() {
        assertFailsWith<IllegalArgumentException> { JournalSyncChangePolicy.nextVersion(0L) }
        assertFailsWith<IllegalArgumentException> { JournalSyncChangePolicy.nextVersion(-5L) }
        // Taşma: MAX_VALUE + 1 negatife dönmemeli.
        assertFailsWith<IllegalArgumentException> { JournalSyncChangePolicy.nextVersion(Long.MAX_VALUE) }
    }

    @Test
    fun yalnizcaBilinenIslemTurleriKabulEdilir() {
        assertEquals("UPSERT", JournalSyncChangePolicy.requireChangeType("UPSERT"))
        assertEquals("DELETE", JournalSyncChangePolicy.requireChangeType("DELETE"))
        assertFailsWith<IllegalArgumentException> { JournalSyncChangePolicy.requireChangeType("UPDATE") }
        assertFailsWith<IllegalArgumentException> { JournalSyncChangePolicy.requireChangeType("") }
    }

    @Test
    fun hesapYoksaKuyrugaEklenmez() {
        assertFalse(JournalSyncChangePolicy.shouldQueue(null))
        assertFalse(JournalSyncChangePolicy.shouldQueue(""))
        assertFalse(JournalSyncChangePolicy.shouldQueue("   "))
        assertTrue(JournalSyncChangePolicy.shouldQueue("uid-1"))
    }

    // ---- SyncQueuePolicy ----

    @Test
    fun kuyrugaYalnizcaAyniHesapVeHazirAlanErisebilir() {
        assertTrue(SyncQueuePolicy.canAccess("uid-1", "uid-1", workspaceReady = true))
    }

    @Test
    fun farkliHesapOturumuKuyrugaErisemez() {
        assertFalse(SyncQueuePolicy.canAccess("uid-1", "uid-2", workspaceReady = true))
        assertFalse(SyncQueuePolicy.canAccess("uid-1", null, workspaceReady = true))
    }

    @Test
    fun calismaAlaniHazirDegilseVeyaHesapBossaErisilemez() {
        assertFalse(SyncQueuePolicy.canAccess("uid-1", "uid-1", workspaceReady = false))
        assertFalse(SyncQueuePolicy.canAccess(null, null, workspaceReady = true))
        assertFalse(SyncQueuePolicy.canAccess("", "", workspaceReady = true))
    }
}
