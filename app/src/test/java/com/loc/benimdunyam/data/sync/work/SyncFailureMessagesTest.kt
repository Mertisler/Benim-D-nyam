package com.loc.benimdunyam.data.sync.work

import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.feature.settings.cloudaccount.relativeTime
import java.io.IOException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sade bulut ekranı teknik hata metinlerini göstermez; kullanıcıya ne olduğunu
 * ve ne yapması gerektiğini anlatan kısa Türkçe açıklamalar gösterir.
 */
class SyncFailureMessagesTest {

    private fun firestore(code: FirebaseFirestoreException.Code) =
        FirebaseFirestoreException("teknik", code)

    @Test
    fun agHatasiYenidenDeneneceginiSoyler() {
        val text = SyncFailureMessages.describe(firestore(FirebaseFirestoreException.Code.UNAVAILABLE))
        assertTrue(text.contains("İnternet"))
    }

    @Test
    fun yetkiHatasiAnlasilirdir() {
        val text = SyncFailureMessages.describe(firestore(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertTrue(text.contains("reddetti"))
    }

    @Test
    fun icIceSarilmisFirestoreHatasiDaTaninir() {
        val wrapped = IllegalStateException("dış", firestore(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
        assertTrue(SyncFailureMessages.describe(wrapped).contains("İnternet"))
    }

    @Test
    fun yanlisSifreSifreyiIsaretEder() {
        val text = SyncFailureMessages.describe(IllegalStateException("x", AEADBadTagException("tag")))
        assertTrue(text.contains("şifre"))
    }

    @Test
    fun olaySiniriAyriAnlatilir() {
        val text = SyncFailureMessages.describe(IllegalStateException("2.000 olay sınırı aşıldı; kısmi aktarım yapılmaz."))
        assertTrue(text.contains("çok fazla kayıt"))
    }

    @Test
    fun bilinmeyenHataGuvenceVerir() {
        val text = SyncFailureMessages.describe(IOException("disk"))
        assertTrue(text.contains("cihazında güvende"))
        // Teknik ayrıntı kullanıcıya sızmaz.
        assertFalse(text.contains("disk"))
    }

    @Test
    fun goreceliZamanMetinleri() {
        val now = 10_000_000_000L
        assertEquals("az önce", relativeTime(now - 30_000, now))
        assertEquals("12 dk önce", relativeTime(now - 12 * 60_000, now))
        assertEquals("3 saat önce", relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2 gün önce", relativeTime(now - 2 * 86_400_000L, now))
        // Saat ileri alınmışsa negatif süre gösterilmez.
        assertEquals("az önce", relativeTime(now + 60_000, now))
    }
}
