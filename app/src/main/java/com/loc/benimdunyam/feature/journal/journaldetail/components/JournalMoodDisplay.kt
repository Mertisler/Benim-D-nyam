package com.loc.benimdunyam.feature.journal.journaldetail.components

import java.util.Locale

data class JournalMoodDisplay(val emoji: String, val label: String)

/** Önceki editör sürümlerindeki Türkçe/İngilizce enum isimlerini de destekler. */
fun journalMoodDisplay(moodName: String): JournalMoodDisplay {
    val key = moodName.trim().uppercase(Locale.ROOT)
        .replace('Ç', 'C').replace('Ğ', 'G').replace('İ', 'I')
        .replace('Ö', 'O').replace('Ş', 'S').replace('Ü', 'U')
        .replace(Regex("[^A-Z0-9]"), "")

    return when (key) {
        "VERYGOOD", "COKIYI" -> JournalMoodDisplay("🥰", "Çok İyi")
        "HAPPY", "MUTLU" -> JournalMoodDisplay("😊", "Mutlu")
        "PEACEFUL", "CALM", "HUZURLU" -> JournalMoodDisplay("😌", "Huzurlu")
        "ENERGETIC", "ENERGIZED", "ENERJIK" -> JournalMoodDisplay("⚡", "Enerjik")
        "TIRED", "YORGUN" -> JournalMoodDisplay("😴", "Yorgun")
        "TENSE", "STRESSED", "NERVOUS", "GERGIN" -> JournalMoodDisplay("😣", "Gergin")
        "SAD", "UZGUN" -> JournalMoodDisplay("😢", "Üzgün")
        "HURT", "BROKEN", "OFFENDED", "KIRGIN" -> JournalMoodDisplay("💔", "Kırgın")
        else -> JournalMoodDisplay(
            emoji = "🎭",
            label = moodName.replace('_', ' ')
                .lowercase(Locale.forLanguageTag("tr-TR"))
                .replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) }
        )
    }
}
