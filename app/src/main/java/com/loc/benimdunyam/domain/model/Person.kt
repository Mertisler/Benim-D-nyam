package com.loc.benimdunyam.domain.model

data class Person(
    val id: String,
    val name: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val uniqueJournalCount: Int = 0,
    val totalMentionCount: Int = 0
)