
package com.loc.benimdunyam.data.backup

/**
 * ZIP arşivinin kimliğini ve içeriğini tanımlar.
 *
 * archiveVersion: ZIP paket yapısının sürümü.
 * dataVersion: BackupData modelinin sürümü.
 */
data class BackupArchiveManifest(
    val format: String = "benim-dunyam",
    val archiveVersion: Int = 1,
    val dataVersion: Int = 2,
    val createdAt: Long,
    val dataSizeBytes: Long,
    val dataSha256: String,
    val mediaFiles: List<BackupMediaFile>
)

/**
 * ZIP içerisindeki tek bir görselin
 * dosya adı, boyutu ve SHA-256 özeti.
 */
data class BackupMediaFile(
    val archivePath: String,
    val sizeBytes: Long,
    val sha256: String
)
