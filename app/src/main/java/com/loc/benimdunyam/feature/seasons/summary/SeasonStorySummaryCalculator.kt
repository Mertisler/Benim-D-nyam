package com.loc.benimdunyam.feature.seasons.summary

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.Person
import java.util.Locale

/**
 * Pure data calculations; no database writes, network calls or AI inference.
 * A mention means a person is tagged in a distinct journal (not a word count).
 */
data class StoryPersonCount(
    val personId: String,
    val name: String,
    val journalCount: Int
)

data class StoryEpisodeSummary(
    val episode: LifeEpisode,
    val memoryCount: Int,
    val peopleCount: Int,
    val topPerson: StoryPersonCount?,
    val firstMemory: JournalEntry?,
    val lastMemory: JournalEntry?
)

data class SeasonStoryReport(
    val seasonId: String,
    val memoryCount: Int,
    val uniquePeopleCount: Int,
    val filledEpisodeCount: Int,
    val firstMemory: JournalEntry?,
    val lastMemory: JournalEntry?,
    val topPeople: List<StoryPersonCount>,
    val timeline: List<JournalEntry>,
    val episodeReports: List<StoryEpisodeSummary>,
    val recentMemories: List<JournalEntry>
)

object SeasonStorySummaryCalculator {
    fun calculate(
        seasonId: String,
        episodes: List<LifeEpisode>,
        journals: List<JournalEntry>
    ): SeasonStoryReport {
        // Both date and ID tie breakers keep results stable across recompositions.
        val seasonMemories = journals.asSequence()
            .filter { it.seasonId == seasonId }
            .distinctBy { it.id }
            .sortedWith(compareBy<JournalEntry> { it.entryDate }.thenBy { it.id })
            .toList()
        val episodeReports = episodes.asSequence()
            .filter { it.seasonId == seasonId }
            .distinctBy { it.id }
            .sortedWith(compareBy<LifeEpisode> { it.orderIndex }.thenBy { it.id })
            .map { episode ->
                val matched = seasonMemories.filter { it.episodeId == episode.id }
                val people = countPeople(matched)
                StoryEpisodeSummary(
                    episode = episode,
                    memoryCount = matched.size,
                    peopleCount = people.size,
                    topPerson = people.firstOrNull(),
                    firstMemory = matched.firstOrNull(),
                    lastMemory = matched.lastOrNull()
                )
            }.toList()
        val people = countPeople(seasonMemories)
        val checkpoints = when (seasonMemories.size) {
            0 -> emptyList()
            1 -> seasonMemories
            2 -> seasonMemories
            else -> listOf(
                seasonMemories.first(),
                seasonMemories[seasonMemories.lastIndex / 2],
                seasonMemories.last()
            ).distinctBy { it.id }
        }
        return SeasonStoryReport(
            seasonId = seasonId,
            memoryCount = seasonMemories.size,
            uniquePeopleCount = people.size,
            filledEpisodeCount = episodeReports.count { it.memoryCount > 0 },
            firstMemory = seasonMemories.firstOrNull(),
            lastMemory = seasonMemories.lastOrNull(),
            topPeople = people.take(3),
            timeline = checkpoints,
            episodeReports = episodeReports,
            recentMemories = seasonMemories.asReversed().take(36)
        )
    }

    private fun countPeople(journals: List<JournalEntry>): List<StoryPersonCount> {
        // Map by ID rather than display name (two people may have the same name).
        val counts = linkedMapOf<String, Pair<Person, Int>>()
        for (journal in journals) {
            for (person in journal.mentionedPeople.distinctBy { it.id }) {
                val previous = counts[person.id]?.second ?: 0
                counts[person.id] = person to (previous + 1)
            }
        }
        return counts.values.map { (person, count) ->
            StoryPersonCount(person.id, person.name, count)
        }.sortedWith(compareByDescending<StoryPersonCount> { it.journalCount }
            .thenBy { it.name.lowercase(Locale.forLanguageTag("tr-TR")) }.thenBy { it.personId })
    }
}
