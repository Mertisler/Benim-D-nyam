package com.loc.benimdunyam.feature.journal.filter

import com.loc.benimdunyam.domain.model.JournalEntry

/**
 * The same filters are AND-ed together. Search tokens can match across fields.
 * Dates are absolute milliseconds; endDate must be the end of the chosen local day.
 */
data class JournalArchiveFilters(
    val searchQuery: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val selectedPersonId: String? = null,
    val selectedSeasonId: String? = null,
    val selectedEpisodeId: String? = null
) {
    val hasAny: Boolean
        get() = searchQuery.isNotBlank() || startDate != null || endDate != null ||
                selectedPersonId != null || selectedSeasonId != null || selectedEpisodeId != null
}

object JournalArchiveFilterEngine {

    fun filter(
        journals: List<JournalEntry>,
        filters: JournalArchiveFilters
    ): List<JournalEntry> = search(journals, filters).journals

    /**
     * Tarih, kişi, dönem ve bölüm filtreleri kesindir; metin araması [JournalTextSearch] ile
     * Türkçe eklere ve harf farklarına duyarlı yapılır ve sonuçlar alakaya göre sıralanır.
     * Arama kutusu boşsa anıların gelen sırası korunur.
     */
    fun search(
        journals: List<JournalEntry>,
        filters: JournalArchiveFilters
    ): JournalSearchResult {
        val faceted = journals.filter match@{ journal ->
            if (filters.startDate != null && journal.entryDate < filters.startDate) return@match false
            if (filters.endDate != null && journal.entryDate > filters.endDate) return@match false
            if (filters.selectedPersonId != null && journal.mentionedPeople.none { it.id == filters.selectedPersonId }) return@match false
            if (filters.selectedSeasonId != null && journal.seasonId != filters.selectedSeasonId) return@match false
            if (filters.selectedEpisodeId != null && journal.episodeId != filters.selectedEpisodeId) return@match false
            true
        }
        return JournalTextSearch.search(faceted, filters.searchQuery)
    }
}
