package com.loc.benimdunyam.domain.model

/**
 * A dry run for one immutable, encrypted Firestore archive.
 * No local records are changed during a preview.
 */
data class CloudRestorePreview(
    val archive: CloudArchiveSummary,
    val encryptedSha256: String,
    val localRecordCount: Int,
    val canImport: Boolean,
    val blockedReason: String? = null
)

data class CloudRestoreResult(
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val moodCount: Int
)
