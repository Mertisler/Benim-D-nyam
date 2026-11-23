
package com.loc.benimdunyam.data.backup

/**
 * ZIP arşivinin kimliğini ve içeriğini tanımlar.
 *
 * archiveVersion: ZIP paket yapısının sürümü.
 *   1: data/backup.json + sezon/kişi görselleri.
 *   2: 1'e ek olarak data/moods.json (ruh halleri). Eski uygulamalar
 *      sürüm 2'yi "desteklenmeyen" diye reddeder; ruh halleri sessizce
 *      kaybolmaz.
 *   3: 2'ye ek olarak media/journal-media.zip (anı fotoğrafları, sıralama ve
 *      fotoğraf silme işaretleri). Tek dosya yedek: ayrı .bdmedia gerekmez.
 * dataVersion: BackupData modelinin sürümü.
 * moodsSizeBytes / moodsSha256: yalnızca sürüm 2'de; Gson null alanları yazmaz.
 */
data class BackupArchiveManifest(
    val format: String = "benim-dunyam",
    val archiveVersion: Int = CURRENT_ARCHIVE_VERSION,
    val dataVersion: Int = 2,
    val createdAt: Long,
    val dataSizeBytes: Long,
    val dataSha256: String,
    val mediaFiles: List<BackupMediaFile>,
    val moodsSizeBytes: Long? = null,
    val moodsSha256: String? = null,
    val journalMediaSizeBytes: Long? = null,
    val journalMediaSha256: String? = null
) {
    companion object {
        const val LEGACY_ARCHIVE_VERSION = 1
        /** Ruh halleri (data/moods.json) bu sürümden itibaren vardır. */
        const val MOODS_ARCHIVE_VERSION = 2
        /** Anı fotoğrafları (media/journal-media.zip) bu sürümden itibaren vardır. */
        const val JOURNAL_MEDIA_ARCHIVE_VERSION = 3
        const val CURRENT_ARCHIVE_VERSION = 3
        const val JOURNAL_MEDIA_ENTRY = "media/journal-media.zip"
        const val MAX_JOURNAL_MEDIA_BYTES = 300L * 1024L * 1024L
    }
}

/**
 * ZIP içerisindeki tek bir görselin
 * dosya adı, boyutu ve SHA-256 özeti.
 */
data class BackupMediaFile(
    val archivePath: String,
    val sizeBytes: Long,
    val sha256: String
)
