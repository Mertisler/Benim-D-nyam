package com.loc.benimdunyam.feature.people.detail.model

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.PersonConnection

/** One season where a pair of characters co-appeared in the journal. */
data class PersonConnectionSeason(
    val seasonId: String?,
    val title: String,
    val memoryCount: Int
) {
    val key: String get() = seasonId ?: "__unassigned__"
}

/** Presentation-only data. Never persisted in a separate Room table. */
data class PersonConnectionItem(
    val personId: String,
    val name: String,
    val profileImagePath: String?,
    val sharedJournals: List<JournalEntry>,
    val seasonByJournalId: Map<String, String?>,
    val seasonBreakdown: List<PersonConnectionSeason>
) {
    val sharedMemoryCount: Int get() = sharedJournals.size
    val firstSharedDate: Long? get() = sharedJournals.minOfOrNull { it.entryDate }
    val lastSharedDate: Long? get() = sharedJournals.maxOfOrNull { it.entryDate }
    val topSeason: PersonConnectionSeason?
        get() = seasonBreakdown.firstOrNull { it.seasonId != null }
}

fun PersonConnection.asUiItem(
    journalsById: Map<String, JournalEntry>,
    appearancesByJournalId: Map<String, PersonAppearance>
): PersonConnectionItem {
    val sharedJournals = sharedJournalIds.mapNotNull(journalsById::get)
        .sortedWith(
            compareByDescending<JournalEntry> { it.entryDate }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
    val seasonByJournalId = sharedJournals.associate { journal ->
        journal.id to appearancesByJournalId[journal.id]?.seasonId
    }
    val seasons = sharedJournals.groupBy { journal -> seasonByJournalId[journal.id] }
        .map { (seasonId, journals) ->
            val title = journals.firstNotNullOfOrNull { journal ->
                appearancesByJournalId[journal.id]?.seasonTitle
            } ?: "Dönem belirtilmemiş"
            PersonConnectionSeason(seasonId, title, journals.size)
        }
        .sortedWith(
            compareByDescending<PersonConnectionSeason> { it.memoryCount }
                .thenBy { it.title }
        )
    return PersonConnectionItem(
        personId = personId,
        name = name,
        profileImagePath = profileImagePath,
        sharedJournals = sharedJournals,
        seasonByJournalId = seasonByJournalId,
        seasonBreakdown = seasons
    )
}
