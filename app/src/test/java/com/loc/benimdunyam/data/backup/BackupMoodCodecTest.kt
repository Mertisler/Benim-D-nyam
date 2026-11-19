package com.loc.benimdunyam.data.backup

import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

/** `data/moods.json` biçiminin seçim, kodlama ve katı doğrulama kuralları. */
class BackupMoodCodecTest {

    private val journals = setOf("j1", "j2", "j3")

    private fun decode(json: String) =
        BackupMoodCodec.decodeAndValidate(json.toByteArray(Charsets.UTF_8), journals)

    @Test
    fun kodlananVeriAynenGeriOkunur() {
        val records = listOf(
            JournalMoodRecord("j1", "HAPPY", 10),
            JournalMoodRecord("j2", "ANXIOUS", 20)
        )
        assertEquals(records, BackupMoodCodec.decodeAndValidate(BackupMoodCodec.encode(records), journals))
    }

    @Test
    fun yedegeYetimVeBilinmeyenKayitlarAlinmazSiraKararlidir() {
        val selected = BackupMoodCodec.selectForBackup(
            listOf(
                JournalMoodRecord("j3", "SAD", 3),
                JournalMoodRecord("silinmis", "HAPPY", 1),
                JournalMoodRecord("j1", "ESKI_SURUM_MOOD", 2),
                JournalMoodRecord("j2", "EXCITED", 4)
            ),
            journals
        )
        assertEquals(listOf("j2", "j3"), selected.map { it.journalId })
    }

    @Test
    fun bosListeGecerlidir() {
        assertEquals(emptyList<JournalMoodRecord>(), decode("""{"version":1,"moods":[]}"""))
    }

    @Test
    fun yapisalHatalarReddedilir() {
        assertFailsWith<IOException> { decode("bozuk") }
        assertFailsWith<IOException> { decode("[]") }
        assertFailsWith<IOException> { decode("""{"version":2,"moods":[]}""") }
        assertFailsWith<IOException> { decode("""{"version":1}""") }
        assertFailsWith<IOException> { decode("""{"version":1,"moods":[1]}""") }
        assertFailsWith<IOException> { decode("""{"version":1,"moods":[{"journalId":"j1","moodName":"HAPPY"}]}""") }
        assertFailsWith<IOException> { decode("""{"version":1,"moods":[{"journalId":"j1","moodName":"HAPPY","updatedAt":"x"}]}""") }
    }

    @Test
    fun icerikHatalariReddedilir() {
        // Yedekte olmayan günlük
        assertFailsWith<IOException> { decode("""{"version":1,"moods":[{"journalId":"yok","moodName":"HAPPY","updatedAt":1}]}""") }
        // Bilinmeyen ruh hali
        assertFailsWith<IOException> { decode("""{"version":1,"moods":[{"journalId":"j1","moodName":"happy","updatedAt":1}]}""") }
        // Geçersiz tarih
        assertFailsWith<IOException> { decode("""{"version":1,"moods":[{"journalId":"j1","moodName":"HAPPY","updatedAt":0}]}""") }
        // Aynı anıya iki ruh hali
        assertFailsWith<IOException> {
            decode("""{"version":1,"moods":[{"journalId":"j1","moodName":"HAPPY","updatedAt":1},{"journalId":"j1","moodName":"SAD","updatedAt":2}]}""")
        }
    }
}
