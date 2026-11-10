package com.loc.benimdunyam.feature.yearreview

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodReport
import java.util.Calendar

/**
 * Yıl özeti için sinematik sahne planı.
 * Şartlı sahneler yalnızca gerçekten mevcut verilerden oluşur.
 * Hesaplama saf Kotlin'dir; Room ve Compose bağımlılığı yoktur.
 */
enum class YearMovieChapter {
    OPENING, MONTHS, MONTH_STORY, MOOD, CAST, SEASONS, MEMORIES, FEATURED, PHOTOS, FINALE
}

data class YearMovieFrame(
    val chapter: YearMovieChapter,
    val title: String,
    val durationMillis: Int,
    val journalId: String? = null,
    val monthIndex: Int? = null
)

object YearMovieStoryboard {
    fun build(
        report: YearReviewReport,
        coverPaths: Map<String, String>,
        featuredMemories: List<JournalEntry> = emptyList(),
        moodReport: YearMoodReport? = null
    ): List<YearMovieFrame> {
        if (report.journalCount == 0) return emptyList()

        val availableCoverIds = report.coverCandidates.map { it.id }.toSet()
        val hasPhotos = coverPaths.keys.any { it in availableCoverIds }

        return buildList {
            add(YearMovieFrame(YearMovieChapter.OPENING, "Açılış", 7_500))
            add(YearMovieFrame(YearMovieChapter.MONTHS, "On iki ay", 8_500))
            val monthNames = listOf(
                "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
            )
            // Her ay ayrı kare. Boş aylar kısa tutulur; yıllık hikâyede atlanmaz.
            report.monthlyStories.asSequence()
                .filter { it.monthIndex in 0..11 }
                .distinctBy { it.monthIndex }
                .sortedBy { it.monthIndex }
                .forEach { month ->
                    add(YearMovieFrame(
                        chapter = YearMovieChapter.MONTH_STORY,
                        title = monthNames[month.monthIndex],
                        durationMillis = if (month.journalCount == 0) 4_500 else 8_800,
                        monthIndex = month.monthIndex
                    ))
                }
            // Kullanıcı tarafından gerçekten işaretlenen ruh halleri varsa filmde göster.
            // Varsayılan null => MP4 dışa aktarım, duygu bilgilerini otomatik almaz.
            if (moodReport?.year == report.year &&
                moodReport.taggedJournalCount > 0 && moodReport.moods.isNotEmpty()
            ) {
                add(YearMovieFrame(YearMovieChapter.MOOD, "Duygu Yolculuğum", 10_500))
            }
            if (report.topPeople.isNotEmpty()) {
                add(YearMovieFrame(YearMovieChapter.CAST, "Oyuncu kadrosu", 9_000))
            }
            if (report.topSeasons.any { it.id != null }) {
                add(YearMovieFrame(YearMovieChapter.SEASONS, "Yılın perdeleri", 8_000))
            }
            if (report.selectedMemories.isNotEmpty()) {
                add(YearMovieFrame(YearMovieChapter.MEMORIES, "Dönüm sayfaları", 10_000))
            }
            // Her özel anı bağımsız kare: aynı chapter'daki sahneler de animasyonla değişir.
            featuredMemories.asSequence()
                .filter { journal ->
                    Calendar.getInstance().apply { timeInMillis = journal.entryDate }
                        .get(Calendar.YEAR) == report.year
                }
                .distinctBy { it.id }
                .take(5)
                .forEachIndexed { index, journal ->
                    add(YearMovieFrame(
                        YearMovieChapter.FEATURED,
                        "Unutulmaz sahne ${index + 1}",
                        11_000,
                        journalId = journal.id
                    ))
                }
            if (hasPhotos) {
                add(YearMovieFrame(YearMovieChapter.PHOTOS, "Hatıra kareleri", 10_000))
            }
            add(YearMovieFrame(YearMovieChapter.FINALE, "Final", 9_000))
        }
    }
}
