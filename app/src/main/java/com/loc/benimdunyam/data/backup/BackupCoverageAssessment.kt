package com.loc.benimdunyam.data.backup

import com.loc.benimdunyam.domain.usecase.backup.BackupPreview
import kotlin.math.abs

/** İki ayrı şifreli arşivin sağlık durumunu değerlendiren salt okunur karar modeli. */
enum class BackupCoverageLevel { NOT_CHECKED, REVIEW, FILES_VERIFIED }

data class BackupCoverageAssessment(
    val level: BackupCoverageLevel,
    val notes: List<String>
)

object BackupCoverageAssessor {
    private const val DAY = 24 * 60 * 60 * 1000L

    /**
     * Her iki arşivin ayrı ayrı doğrulanması, birlikte geri yükleme garantisi değildir.
     * Kimlikleri eşleştirme ve yeni cihazda geri yükleme ayrıca test edilmelidir.
     */
    fun assess(
        local: LocalBackupCoverage?,
        normal: BackupPreview?,
        media: JournalMediaArchivePreview?,
        now: Long
    ): BackupCoverageAssessment {
        val notes = mutableListOf<String>()
        if (local == null) notes += "Cihaz kapsam taraması henüz yapılmadı."
        if (normal == null) notes += ".bdbackup dosyası henüz seçilip şifresiyle doğrulanmadı."
        if (media == null) notes += ".bdmedia dosyası henüz seçilip şifresiyle doğrulanmadı."
        if (local != null && local.missingPhotoFiles > 0)
            notes += "${local.missingPhotoFiles} kayıtlı fotoğraf dosyası eksik veya güvenli dosya alanı dışında."
        if (local != null && normal != null) {
            if (local.journalCount != normal.journalCount ||
                local.personCount != normal.personCount ||
                local.seasonCount != normal.seasonCount ||
                local.episodeCount != normal.episodeCount ||
                local.mentionCount != normal.mentionCount ||
                local.specialMemoryCount != normal.specialMemoryCount) {
                notes += "Güncel Room kayıtlarının sayıları .bdbackup ile aynı değil; yeni yedek almayı değerlendir."
            }
        }
        if (local != null && media != null) {
            if (local.publishedPhotoCount != media.archivePhotoCount)
                notes += "Cihazdaki yayınlanmış fotoğraf sayısı .bdmedia ile farklı."
            if (local.newestDeletionAtMillis != null && local.newestDeletionAtMillis > media.createdAtMillis)
                notes += "Bazı silme işaretleri medya yedeğinden daha yeni. Eski yedek silinen anıları yeniden getirebilir."
            if (media.restorePhotoCount > 0)
                notes += "Medya yedeğinde cihazda bulunmayan ${media.restorePhotoCount} fotoğraf var. Bu ekran geri yüklemez."
            if (media.blockedByDeletionCount > 0)
                notes += "${media.blockedByDeletionCount} fotoğraf silme işareti nedeniyle geri yüklemeye kapalı."
        }
        if (normal != null && media != null) {
            if (abs(normal.createdAt - media.createdAtMillis) > DAY)
                notes += "İki yedek farklı günlerde oluşturulmuş olabilir; aynı anlık veri durumunu temsil etmeyebilir."
        }
        if (normal != null && now - normal.createdAt > 7 * DAY)
            notes += ".bdbackup bir haftadan eski; yeni anılar kapsanmayabilir."
        if (media != null && now - media.createdAtMillis > 7 * DAY)
            notes += ".bdmedia bir haftadan eski; yeni fotoğraflar kapsanmayabilir."

        val both = normal != null && media != null && local != null
        return BackupCoverageAssessment(
            level = when {
                !both -> BackupCoverageLevel.NOT_CHECKED
                notes.isNotEmpty() -> BackupCoverageLevel.REVIEW
                else -> BackupCoverageLevel.FILES_VERIFIED
            },
            notes = notes
        )
    }
}
