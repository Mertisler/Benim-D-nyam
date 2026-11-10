package com.loc.benimdunyam.feature.yearreview.video

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.YearMovieChapter
import com.loc.benimdunyam.feature.yearreview.YearMovieFrame
import com.loc.benimdunyam.feature.yearreview.YearMovieStoryboard
import com.loc.benimdunyam.feature.yearreview.YearReviewReport

/** Gizlilik tercihi her yeni dışa aktarmada varsayılan olarak KAPALI. */
data class YearMovieVideoOptions(
    val includePhotos: Boolean = false,
    val includePersonNames: Boolean = false,
    val includeMemoryText: Boolean = false
)

data class YearMovieVideoClip(
    val frame: YearMovieFrame,
    val firstFrame: Int,
    val frameCount: Int
)

data class YearMovieVideoPlan(
    val clips: List<YearMovieVideoClip>,
    val totalFrames: Int,
    val fps: Int = FPS
) {
    val durationSeconds: Int get() = (totalFrames + fps - 1) / fps
    companion object { const val FPS = 24; const val WIDTH = 720; const val HEIGHT = 1280 }
}

/** Var olan film sahnelerini daha kısa, paylaşılabilir bir dikey filme dönüştürür. */
object YearMovieVideoPlanner {
    fun create(
        report: YearReviewReport,
        coverPaths: Map<String, String>,
        featured: List<JournalEntry>,
        options: YearMovieVideoOptions
    ): YearMovieVideoPlan {
        val allowedCovers = if (options.includePhotos) coverPaths else emptyMap()
        // Duygu raporu geçirilmez: kullanıcının ruh hali verileri MP4'e kendiliğinden girmez.
        val storyboard = YearMovieStoryboard.build(report, allowedCovers, featured, moodReport = null)
        var offset = 0
        val clips = storyboard.map { frame ->
            val millis = when (frame.chapter) {
                YearMovieChapter.OPENING -> 3_200
                YearMovieChapter.MONTHS -> 2_800
                YearMovieChapter.MONTH_STORY -> {
                    val count = report.monthlyStories.firstOrNull { it.monthIndex == frame.monthIndex }?.journalCount ?: 0
                    if (count == 0) 1_200 else 2_600
                }
                YearMovieChapter.MOOD -> 0 // Bilerek MP4 planına alınmıyor.
                YearMovieChapter.CAST -> 2_800
                YearMovieChapter.SEASONS -> 2_800
                YearMovieChapter.MEMORIES -> 3_200
                YearMovieChapter.FEATURED -> 3_500
                YearMovieChapter.PHOTOS -> 3_200
                YearMovieChapter.FINALE -> 3_600
            }
            val count = ((millis * YearMovieVideoPlan.FPS) + 999) / 1000
            YearMovieVideoClip(frame, offset, count).also { offset += count }
        }
        return YearMovieVideoPlan(clips, offset)
    }
}
