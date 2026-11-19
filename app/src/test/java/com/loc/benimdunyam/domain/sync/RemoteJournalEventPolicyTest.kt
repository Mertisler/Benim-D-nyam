package com.loc.benimdunyam.domain.sync

import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus.LOCAL_PENDING
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus.LOCAL_PRESENT_NEEDS_REVIEW
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus.ONLY_REMOTE
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus.REMOTE_DELETE_ALREADY_ABSENT
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus.REMOTE_DELETE_LOCAL_EXISTS
import com.loc.benimdunyam.domain.sync.RemoteJournalLocalStatus.SAME_SYNCED_EVENT
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Buluttan indirilen bir olayın gerçekten bu hesaba ve bu anıya ait olduğunu
 * doğrulayan kapı. Burada kaçan bir eşleşmeme, başka bir hesabın verisinin
 * ya da kurcalanmış bir olayın yerel arşive girmesi demektir.
 */
class RemoteJournalEventPolicyTest {

    private val uid = "uid-sahip"
    private val journalId = "j1"
    private val revision = 4L
    private val op = "UPSERT"

    /** Gerçek bir yüklemenin üreteceği tutarlı başlık + içerik. */
    private fun verify(
        expectedUid: String = uid,
        documentId: String = SyncEventIdentity.eventId(uid, journalId, revision, op),
        headerUid: String? = uid,
        headerHash: String? = SyncEventIdentity.journalHash(uid, journalId),
        headerOperation: String? = op,
        headerRevision: Long? = revision,
        payloadUid: String = uid,
        payloadJournalId: String = journalId,
        payloadOperation: String = op,
        payloadRevision: Long = revision
    ) = RemoteJournalEventPolicy.verify(
        expectedUid, documentId, headerUid, headerHash, headerOperation, headerRevision,
        payloadUid, payloadJournalId, payloadOperation, payloadRevision
    )

    private fun assertRejected(block: () -> Unit) {
        assertFailsWith<IllegalArgumentException> { block() }
    }

    // ---- verify ----

    @Test
    fun tutarliOlayKabulEdilir() {
        val result = verify()
        assertEquals(uid, result.ownerUid)
        assertEquals(journalId, result.journalId)
        assertEquals(op, result.operation)
        assertEquals(revision, result.revision)
    }

    @Test
    fun silmeOlayiDaDogrulanabilir() {
        val result = verify(
            documentId = SyncEventIdentity.eventId(uid, journalId, revision, "DELETE"),
            headerOperation = "DELETE",
            payloadOperation = "DELETE"
        )
        assertEquals("DELETE", result.operation)
    }

    @Test
    fun baskaHesabinBelgesiReddedilir() {
        assertRejected { verify(headerUid = "uid-baska") }
        assertRejected { verify(payloadUid = "uid-baska") }
        assertRejected { verify(headerUid = null) }
    }

    @Test
    fun baskaHesabinTutarliOlayiBileReddedilir() {
        // Saldırgan kendi hesabında geçerli bir olay üretip bu kullanıcıya sunarsa.
        val other = "uid-baska"
        assertRejected {
            verify(
                documentId = SyncEventIdentity.eventId(other, journalId, revision, op),
                headerUid = other,
                headerHash = SyncEventIdentity.journalHash(other, journalId),
                payloadUid = other
            )
        }
    }

    @Test
    fun baslikVeIcerikUyusmazsaReddedilir() {
        // Şifreli içerik DELETE derken başlık UPSERT diyorsa.
        assertRejected { verify(headerOperation = "DELETE") }
        assertRejected { verify(headerRevision = revision + 1) }
        assertRejected { verify(headerRevision = null) }
    }

    @Test
    fun bilinmeyenIslemReddedilir() {
        assertRejected { verify(headerOperation = "MERGE", payloadOperation = "MERGE") }
    }

    @Test
    fun sifirVeyaNegatifSurumReddedilir() {
        assertRejected { verify(headerRevision = 0, payloadRevision = 0) }
        assertRejected { verify(headerRevision = -1, payloadRevision = -1) }
    }

    @Test
    fun yanlisAniOzetiReddedilir() {
        assertRejected { verify(headerHash = SyncEventIdentity.journalHash(uid, "j2")) }
        assertRejected { verify(headerHash = null) }
    }

    @Test
    fun belgeKimligiIcerikleUyusmazsaReddedilir() {
        // Eski bir sürümün içeriği yeni sürümün belgesine kopyalanmışsa.
        assertRejected {
            verify(documentId = SyncEventIdentity.eventId(uid, journalId, revision - 1, op))
        }
        assertRejected { verify(documentId = "e_rastgele") }
    }

    @Test
    fun icerikteBaskaAniyaYonlendirmeReddedilir() {
        // Başlık j1 için, içerik j2 diyorsa: özet ve belge kimliği tutmamalı.
        assertRejected { verify(payloadJournalId = "j2") }
    }

    @Test
    fun bosKimliklerReddedilir() {
        assertRejected { verify(expectedUid = "", headerUid = "", payloadUid = "") }
        assertRejected { verify(payloadJournalId = "") }
    }

    // ---- classify ----

    @Test
    fun yereldeBekleyenYazmaHerZamanOnceliklidir() {
        // Uzaktaki silme bile, kullanıcının henüz yüklenmemiş düzenlemesini ezmemeli.
        for (operation in listOf("UPSERT", "DELETE")) {
            for (exists in listOf(true, false)) {
                for (same in listOf(true, false)) {
                    assertEquals(
                        LOCAL_PENDING,
                        RemoteJournalEventPolicy.classify(operation, exists, localPending = true, sameSyncedEvent = same)
                    )
                }
            }
        }
    }

    @Test
    fun ayniOlayZatenUygulanmissaTekrarIslenmez() {
        assertEquals(SAME_SYNCED_EVENT, RemoteJournalEventPolicy.classify("UPSERT", true, false, true))
        assertEquals(SAME_SYNCED_EVENT, RemoteJournalEventPolicy.classify("DELETE", false, false, true))
    }

    @Test
    fun guncellemeOlaylariSiniflandirilir() {
        assertEquals(ONLY_REMOTE, RemoteJournalEventPolicy.classify("UPSERT", false, false, false))
        assertEquals(LOCAL_PRESENT_NEEDS_REVIEW, RemoteJournalEventPolicy.classify("UPSERT", true, false, false))
    }

    @Test
    fun silmeOlaylariSiniflandirilir() {
        assertEquals(REMOTE_DELETE_LOCAL_EXISTS, RemoteJournalEventPolicy.classify("DELETE", true, false, false))
        assertEquals(REMOTE_DELETE_ALREADY_ABSENT, RemoteJournalEventPolicy.classify("DELETE", false, false, false))
    }

    @Test
    fun bilinmeyenIslemSiniflandirilmaz() {
        assertRejected { RemoteJournalEventPolicy.classify("MERGE", true, false, false) }
    }
}
