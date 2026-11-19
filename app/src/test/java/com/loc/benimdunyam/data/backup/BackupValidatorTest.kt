package com.loc.benimdunyam.data.backup

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Geri yüklemeden önce yedek JSON'unun yapısını ve kayıtlar arası
 * ilişkileri doğrulayan katmanı sınar. Burada kaçan bir tutarsızlık,
 * geri yükleme sırasında yarım/bozuk bir arşive yol açabilir.
 */
class BackupValidatorTest {

    private val validator = BackupValidator()
    private val gson = Gson()

    private fun validData(): BackupData {
        val season = LifeSeasonEntity(
            id = "s1", title = "Üniversite", description = "Ankara yılları",
            startDate = 1_000L, endDate = null, colorHex = "#FFAA00",
            coverImagePath = null, createdAt = 1_000L
        )
        val episode = LifeEpisodeEntity(
            id = "e1", seasonId = "s1", title = "1. sınıf", description = "",
            orderIndex = 0, startDate = 1_000L, endDate = null, createdAt = 1_000L
        )
        val journal = JournalEntryEntity(
            id = "j1", title = "İlk gün", content = "@Ayşe ile tanıştım",
            entryDate = 2_000L, createdAt = 2_000L, updatedAt = 2_000L,
            seasonId = "s1", episodeId = "e1"
        )
        val journalWithoutSeason = JournalEntryEntity(
            id = "j2", title = "Serbest", content = "Bugün yağmur vardı",
            entryDate = 3_000L, createdAt = 3_000L, updatedAt = 3_000L
        )
        val person = PersonEntity(
            id = "p1", name = "Ayşe", note = null,
            createdAt = 1_500L, updatedAt = 1_500L
        )
        val mention = JournalPersonMentionEntity(
            journalId = "j1", personId = "p1", mentionIndex = 0,
            startOffset = 0, endOffset = 5
        )
        val special = PersonSpecialMemoryEntity(
            personId = "p1", journalId = "j1", category = "ilk_tanisma",
            note = "Unutulmaz", createdAt = 2_100L, updatedAt = 2_100L
        )
        return BackupData(
            version = 2,
            exportTimestamp = 10_000L,
            journals = listOf(journal, journalWithoutSeason),
            people = listOf(person),
            mentions = listOf(mention),
            seasons = listOf(season),
            episodes = listOf(episode),
            specialMemories = listOf(special)
        )
    }

    private fun json(data: BackupData): String = gson.toJson(data)

    private fun assertRejected(json: String, messagePart: String? = null) {
        try {
            validator.parseAndValidate(json)
            fail("Geçersiz yedek kabul edilmemeli")
        } catch (e: IllegalArgumentException) {
            if (messagePart != null) {
                assertTrue(
                    "Beklenen mesaj '$messagePart', gelen '${e.message}'",
                    e.message.orEmpty().contains(messagePart)
                )
            }
        }
    }

    // ---- Geçerli yedek ----

    @Test
    fun gecerliYedekBirebirOkunur() {
        val original = validData()
        val parsed = validator.parseAndValidate(json(original))
        assertEquals(original, parsed)
    }

    @Test
    fun bosArsivGecerlidir() {
        val empty = BackupData(
            exportTimestamp = 1L, journals = emptyList(), people = emptyList(),
            mentions = emptyList(), seasons = emptyList(), episodes = emptyList(),
            specialMemories = emptyList()
        )
        assertEquals(empty, validator.parseAndValidate(json(empty)))
    }

    @Test
    fun turkceVeOzelKarakterlerKorunur() {
        val data = validData().let {
            it.copy(journals = it.journals.map { j ->
                j.copy(content = "Çığ, şüphe, ÖĞÜ 😊 \"tırnak\" \n yeni satır")
            })
        }
        assertEquals(data, validator.parseAndValidate(json(data)))
    }

    // ---- Yapısal hatalar ----

    @Test
    fun bosVeyaBozukJsonReddedilir() {
        assertRejected("   ", "boş")
        assertRejected("{bozuk", "JSON")
        assertRejected("[]", "kök")
    }

    @Test
    fun desteklenmeyenSurumReddedilir() {
        assertRejected(json(validData().copy(version = 1)), "sürüm")
        assertRejected(json(validData().copy(version = 3)), "sürüm")
    }

    @Test
    fun eksikTabloReddedilir() {
        val obj = JsonParser.parseString(json(validData())).asJsonObject
        obj.remove("episodes")
        assertRejected(obj.toString(), "episodes")
    }

    @Test
    fun zorunluAlaniEksikKayitReddedilir() {
        val obj = JsonParser.parseString(json(validData())).asJsonObject
        (obj.getAsJsonArray("journals")[0] as JsonObject).remove("content")
        assertRejected(obj.toString(), "content")
    }

    // ---- Kimlik (ID) hataları ----

    @Test
    fun tekrarlananIdReddedilir() {
        val data = validData().let { it.copy(people = it.people + it.people.first()) }
        assertRejected(json(data), "tekrarlanan ID")
    }

    @Test
    fun bosIdReddedilir() {
        val data = validData().let {
            it.copy(people = it.people + PersonEntity("", "İsimsiz", null, 1L, 1L))
        }
        assertRejected(json(data), "boş ID")
    }

    // ---- İlişki hataları ----

    @Test
    fun sezonuOlmayanBolumReddedilir() {
        val data = validData().let {
            it.copy(episodes = it.episodes.map { e -> e.copy(seasonId = "yok") })
        }
        assertRejected(json(data), "sezon bulunamadı")
    }

    @Test
    fun olmayanSezonaBagliGunlukReddedilir() {
        val data = validData().let {
            it.copy(journals = it.journals.map { j ->
                if (j.id == "j2") j.copy(seasonId = "yok") else j
            })
        }
        assertRejected(json(data), "sezon bulunamadı")
    }

    @Test
    fun bolumuBaskaSezondanOlanGunlukReddedilir() {
        val base = validData()
        val otherSeason = base.seasons.first().copy(id = "s2")
        val data = base.copy(
            seasons = base.seasons + otherSeason,
            journals = base.journals.map { j ->
                if (j.id == "j1") j.copy(seasonId = "s2") else j
            }
        )
        assertRejected(json(data), "uyuşmuyor")
    }

    @Test
    fun olmayanGunlugeBahsetmeReddedilir() {
        val data = validData().let {
            it.copy(mentions = it.mentions + it.mentions.first().copy(journalId = "yok"))
        }
        assertRejected(json(data), "günlüğü bulunamadı")
    }

    @Test
    fun olmayanKisiyeBahsetmeReddedilir() {
        val data = validData().let {
            it.copy(mentions = it.mentions + it.mentions.first().copy(personId = "yok"))
        }
        assertRejected(json(data), "kişisi bulunamadı")
    }

    @Test
    fun tekrarlananBahsetmeReddedilir() {
        val data = validData().let {
            it.copy(mentions = it.mentions + it.mentions.first().copy(mentionIndex = 1))
        }
        assertRejected(json(data), "Tekrarlanan kişi-anı")
    }

    @Test
    fun bahsetmesiOlmayanOzelAniReddedilir() {
        // Kişi ve günlük var ama aralarında bahsetme bağlantısı yok.
        val data = validData().let {
            it.copy(specialMemories = it.specialMemories.map { m -> m.copy(journalId = "j2") })
        }
        assertRejected(json(data), "geçersiz bir")
    }

    @Test
    fun kategorisiBosOzelAniReddedilir() {
        val data = validData().let {
            it.copy(specialMemories = it.specialMemories.map { m -> m.copy(category = " ") })
        }
        assertRejected(json(data), "kategorisi boş")
    }

    @Test
    fun tekrarlananOzelAniReddedilir() {
        val data = validData().let {
            it.copy(specialMemories = it.specialMemories + it.specialMemories.first())
        }
        assertRejected(json(data), "Tekrarlanan unutulmaz")
    }
}
