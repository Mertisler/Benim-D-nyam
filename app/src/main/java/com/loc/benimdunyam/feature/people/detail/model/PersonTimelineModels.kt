package com.loc.benimdunyam.feature.people.detail.model

/**
 * One chronological group in the selected person's story.
 * Entries without a resolvable season are grouped under a dedicated key.
 */
data class PersonTimelineSeasonGroup(
    val key: String,
    val seasonId: String?,
    val seasonTitle: String,
    val appearances: List<PersonAppearance>
) {
    val memoryCount: Int
        get() = appearances.size
}
