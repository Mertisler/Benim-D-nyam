package com.loc.benimdunyam.feature.people.detail.mood

import com.loc.benimdunyam.feature.journal.journaldetail.components.journalMoodDisplay

data class PersonMoodCount(
    val label: String,
    val emoji: String,
    val count: Int
)

data class PersonMoodInsights(
    val totalMemories: Int,
    val taggedMemories: Int,
    val untaggedMemories: Int,
    val moods: List<PersonMoodCount>
)

/**
 * Yalnızca bu kişinin bulunduğu tekil anılar hesaba katılır.
 * Bu kayıtlar kişinin duygularını değil, anı yazarının seçtiği ruh halini yansıtır.
 */
object PersonMoodInsightsCalculator {
    fun calculate(
        journalIds: Collection<String>,
        moodByJournalId: Map<String, String>
    ): PersonMoodInsights {
        val uniqueIds = journalIds.filter { it.isNotBlank() }.distinct()
        val tagged = uniqueIds.mapNotNull { id ->
            moodByJournalId[id]?.takeIf { it.isNotBlank() }?.let(::journalMoodDisplay)
        }
        val counts = tagged.groupingBy { it }.eachCount()
            .map { (display, count) ->
                PersonMoodCount(
                    label = display.label,
                    emoji = display.emoji,
                    count = count
                )
            }
            .sortedWith(compareByDescending<PersonMoodCount> { it.count }.thenBy { it.label })

        return PersonMoodInsights(
            totalMemories = uniqueIds.size,
            taggedMemories = tagged.size,
            untaggedMemories = uniqueIds.size - tagged.size,
            moods = counts
        )
    }
}
