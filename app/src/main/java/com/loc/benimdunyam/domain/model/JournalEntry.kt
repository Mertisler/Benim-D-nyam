package com.loc.benimdunyam.domain.model

data class JournalEntry(
    val id: String,
    val title: String,
    val content: String,
    val entryDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val mentionedPeople: List<Person> = emptyList(),
    val seasonId: String? = null,
    val episodeId: String? = null

)