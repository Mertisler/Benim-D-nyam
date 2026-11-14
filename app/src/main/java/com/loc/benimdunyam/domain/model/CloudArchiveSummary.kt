package com.loc.benimdunyam.domain.model

/** Firestore'da başarıyla tamamlanmış, salt okunur bulut arşivi özeti. */
data class CloudArchiveSummary(
    val id: String,
    val createdAtMillis: Long,
    val journalCount: Int,
    val peopleCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val moodCount: Int
)
