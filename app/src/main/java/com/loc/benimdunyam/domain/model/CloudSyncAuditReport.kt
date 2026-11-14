package com.loc.benimdunyam.domain.model

/**
 * İki arşiv arasındaki kayıt farkları. Yalnızca sayılar tutulur; günlük metinleri
 * ve kişi isimleri UI durumuna taşınmaz.
 */
data class CloudRecordDifference(
    val onlyOnDevice: Int,
    val onlyInCloud: Int,
    val different: Int,
    val matching: Int
) {
    val hasDifference: Boolean
        get() = onlyOnDevice > 0 || onlyInCloud > 0 || different > 0
}

/**
 * Cihazdaki kayıtların ve fotoğrafların salt okunur dökümü.
 * Fotoğraflar ile kişi/dönem görselleri henüz Firestore arşivinde bulunmuyor.
 */
data class CloudLocalCoverage(
    val journalCount: Int,
    val peopleCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val moodCount: Int,
    val journalPhotoCount: Int,
    val journalPhotoBytes: Long,
    val personImageReferenceCount: Int,
    val seasonImageReferenceCount: Int,
    val inspectedAtMillis: Long
)

/**
 * Şifresi çözülüp doğrulanmış SON bulut arşivi ile anlık Room kayıtları karşılaştırılır.
 * Bu sonuç bir geri yükleme veya otomatik senkronizasyon işlemi başlatmaz.
 */
data class CloudSyncAuditReport(
    val archive: CloudArchiveSummary,
    val local: CloudLocalCoverage,
    val journals: CloudRecordDifference,
    val people: CloudRecordDifference,
    val seasons: CloudRecordDifference,
    val episodes: CloudRecordDifference,
    val mentions: CloudRecordDifference,
    val specialMemories: CloudRecordDifference,
    val moods: CloudRecordDifference,
    val comparedAtMillis: Long
) {
    val textRecordsMatch: Boolean
        get() = listOf(journals, people, seasons, episodes, mentions, specialMemories, moods)
            .none { it.hasDifference }
}
