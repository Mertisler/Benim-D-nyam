package com.loc.benimdunyam.domain.model

/**
 * A co-appearance in the user's journal archive, not a statement about
 * how close the two people are in real life.
 */
data class PersonConnection(
    val personId: String,
    val name: String,
    val profileImagePath: String?,
    val sharedJournalIds: List<String>
) {
    val sharedMemoryCount: Int get() = sharedJournalIds.size
}
