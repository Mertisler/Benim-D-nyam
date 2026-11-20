package com.loc.benimdunyam.data.sync.work

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Katalog yenileme işaretinin yarış durumlarında kaybolmadığını doğrular. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncCatalogFreshnessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store() = SyncCatalogFreshness(context)

    @Test
    fun baslangictaKatalogGunceldir() {
        assertFalse(store().isStale("uid-1"))
    }

    @Test
    fun olayGonderilinceKatalogEskirVeYayinlanincaGuncellenir() {
        val s = store()
        s.markStale("uid-1")
        assertTrue(s.isStale("uid-1"))

        s.markPublished("uid-1", s.changeVersion("uid-1"))
        assertFalse(s.isStale("uid-1"))
    }

    @Test
    fun yenilemeSirasindaGelenYeniOlayKaybolmaz() {
        val s = store()
        s.markStale("uid-1")
        val atStart = s.changeVersion("uid-1")

        // Katalog yüklenirken başka bir olay buluta yazıldı.
        s.markStale("uid-1")
        s.markPublished("uid-1", atStart)

        // Sonraki olay yeni katalogda olmayabilir: yeniden yenilenmeli.
        assertTrue(s.isStale("uid-1"))
    }

    @Test
    fun durumUygulamaYenidenAcilincaKorunur() {
        store().markStale("uid-1")
        assertTrue(store().isStale("uid-1"))
    }

    @Test
    fun hesaplarBirbirindenBagimsizdir() {
        val s = store()
        s.markStale("uid-1")
        assertTrue(s.isStale("uid-1"))
        assertFalse(s.isStale("uid-2"))
    }

    @Test
    fun eskiBirSurumleYayinlamaIsaretiGeriAlmaz() {
        val s = store()
        s.markStale("uid-1")
        s.markStale("uid-1")
        s.markPublished("uid-1", s.changeVersion("uid-1"))
        // Geç biten eski bir yenileme durumu bozmamalı.
        s.markPublished("uid-1", 1)
        assertFalse(s.isStale("uid-1"))
    }
}
