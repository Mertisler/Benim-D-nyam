package com.loc.benimdunyam.feature.yearreview.poster

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.YearReviewReport

/** Kullanıcı paylaşmayı açıkça seçmedikçe özel yazılar, kişi adları ve fotoğraflar görünmez. */
enum class YearPosterTheme { CINEMA, PAPER }

data class YearPosterOptions(
    val theme: YearPosterTheme = YearPosterTheme.CINEMA,
    val includePhotos: Boolean = false,
    val includePersonNames: Boolean = false,
    val includeMemoryText: Boolean = false,
    val includeStats: Boolean = true,
    val preferredPhotoJournalId: String? = null,
    val preferredMemoryJournalId: String? = null
)

data class YearPosterContent(
    val year: Int,
    val journalCount: Int,
    val uniquePeopleCount: Int,
    val seasonCount: Int,
    val activeMonthCount: Int,
    val busiestMonthIndex: Int?,
    val photoPath: String?,
    val personNames: List<String>,
    val memoryTitle: String?,
    val memoryExcerpt: String?,
    val seasonTitle: String? = null
)

object YearPosterModel {
    fun create(
        report: YearReviewReport,
        coverPaths: Map<String, String>,
        featuredMemories: List<JournalEntry>,
        options: YearPosterOptions
    ): YearPosterContent {
        // Önce kullanıcı tarafından seçilen anılar, sonra yılın diğer fotoğrafları.
        val photoCandidates = (
                featuredMemories + report.coverCandidates +
                        report.monthlyStories.flatMap { it.coverCandidates } + report.selectedMemories
                ).distinctBy { it.id }
        val photoPath = if (options.includePhotos) {
            val chosen = photoCandidates.firstOrNull { it.id == options.preferredPhotoJournalId }
                ?.let { coverPaths[it.id] }
            chosen?.takeIf { it.isNotBlank() }
                ?: photoCandidates.asSequence().mapNotNull { coverPaths[it.id] }
                    .firstOrNull { it.isNotBlank() }
        } else null

        val memoryCandidates = (featuredMemories + report.selectedMemories).distinctBy { it.id }
        val memory = if (options.includeMemoryText) {
            memoryCandidates.firstOrNull { it.id == options.preferredMemoryJournalId }
                ?: memoryCandidates.firstOrNull()
        } else null
        return YearPosterContent(
            year = report.year,
            // Özel sezon adı yalnızca kullanıcı metin paylaşımını açarsa dışa çıkar.
            seasonTitle = report.seasonTitle?.let { title ->
                if (options.includeMemoryText) title else "Bir Dönem"
            },
            journalCount = report.journalCount,
            uniquePeopleCount = report.uniquePeopleCount,
            seasonCount = report.seasonCount,
            activeMonthCount = report.activeMonthCount,
            busiestMonthIndex = report.busiestMonthIndex,
            photoPath = photoPath,
            personNames = if (options.includePersonNames) {
                report.topPeople.take(3).map { it.name.trim() }.filter { it.isNotEmpty() }
            } else emptyList(),
            memoryTitle = memory?.title?.trim()?.takeIf { it.isNotEmpty() },
            memoryExcerpt = memory?.content
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(150)
                ?.takeIf { it.isNotEmpty() }
        )
    }
}
