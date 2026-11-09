package com.loc.benimdunyam.feature.yearreview

/**
 * Yıl özeti için sinematik sahne planı.
 * Şartlı sahneler yalnızca gerçekten mevcut verilerden oluşur.
 * Hesaplama saf Kotlin'dir; Room ve Compose bağımlılığı yoktur.
 */
enum class YearMovieChapter {
    OPENING, MONTHS, CAST, SEASONS, MEMORIES, PHOTOS, FINALE
}

data class YearMovieFrame(
    val chapter: YearMovieChapter,
    val title: String,
    val durationMillis: Int
)

object YearMovieStoryboard {
    fun build(
        report: YearReviewReport,
        coverPaths: Map<String, String>
    ): List<YearMovieFrame> {
        if (report.journalCount == 0) return emptyList()

        val availableCoverIds = report.coverCandidates.map { it.id }.toSet()
        val hasPhotos = coverPaths.keys.any { it in availableCoverIds }

        return buildList {
            add(YearMovieFrame(YearMovieChapter.OPENING, "Açılış", 7_500))
            add(YearMovieFrame(YearMovieChapter.MONTHS, "On iki ay", 8_500))
            if (report.topPeople.isNotEmpty()) {
                add(YearMovieFrame(YearMovieChapter.CAST, "Oyuncu kadrosu", 9_000))
            }
            if (report.topSeasons.any { it.id != null }) {
                add(YearMovieFrame(YearMovieChapter.SEASONS, "Yılın perdeleri", 8_000))
            }
            if (report.selectedMemories.isNotEmpty()) {
                add(YearMovieFrame(YearMovieChapter.MEMORIES, "Dönüm sayfaları", 10_000))
            }
            if (hasPhotos) {
                add(YearMovieFrame(YearMovieChapter.PHOTOS, "Hatıra kareleri", 10_000))
            }
            add(YearMovieFrame(YearMovieChapter.FINALE, "Final", 9_000))
        }
    }
}
