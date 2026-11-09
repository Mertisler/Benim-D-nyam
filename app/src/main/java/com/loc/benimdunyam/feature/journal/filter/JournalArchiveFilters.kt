package com.loc.benimdunyam.feature.journal.filter

import com.loc.benimdunyam.domain.model.JournalEntry
import java.util.Locale

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
    private val turkishLocale = Locale.forLanguageTag("tr-TR")
    private val spaces = Regex("\\s+")

    fun filter(
        journals: List<JournalEntry>,
        filters: JournalArchiveFilters
    ): List<JournalEntry> {
        val tokens = filters.searchQuery.trim().lowercase(turkishLocale)
            .split(spaces).filter { it.isNotEmpty() }

        return journals.filter match@{ journal ->
            if (filters.startDate != null && journal.entryDate < filters.startDate) return@match false
            if (filters.endDate != null && journal.entryDate > filters.endDate) return@match false
            if (filters.selectedPersonId != null && journal.mentionedPeople.none { it.id == filters.selectedPersonId }) return@match false
            if (filters.selectedSeasonId != null && journal.seasonId != filters.selectedSeasonId) return@match false
            if (filters.selectedEpisodeId != null && journal.episodeId != filters.selectedEpisodeId) return@match false

            if (tokens.isEmpty()) return@match true

            val searchableText = buildString {
                append(journal.title)
                append(' ')
                append(journal.content)
                journal.mentionedPeople.forEach { person ->
                    append(' ')
                    append(person.name)
                }
            }.lowercase(turkishLocale)

            tokens.all { token -> searchableText.contains(token) }
        }
    }
}
