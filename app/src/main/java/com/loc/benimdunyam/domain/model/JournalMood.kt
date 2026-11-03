package com.loc.benimdunyam.domain.model

enum class JournalMood(
    val key: String,
    val emoji: String,
    val label: String
) {
    HAPPY("happy", "😄", "Mutlu"),
    PEACEFUL("peaceful", "🥰", "Aşk Dolu"),
    EXCITED("excited", "🤩", "Heyecanlı"),
    EMOTIONAL("emotional", "🥹", "Duygusal"),

    NEUTRAL("neutral", "😐", "Sıradan"),
    SAD("sad", "😔", "Üzgün"),
    ANXIOUS("anxious", "😰", "Endişeli"),
    ANGRY("angry", "😡", "Öfkeli");

    companion object {
        fun fromKey(key: String?): JournalMood? {
            return entries.firstOrNull { it.key == key }
        }
    }
}
