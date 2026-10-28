package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "journal_person_mentions",
    primaryKeys = ["journalId", "personId"]
)
data class JournalPersonMentionEntity(
    val journalId: String,
    val personId: String,
    val mentionIndex: Int,
    val startOffset: Int?,
    val endOffset: Int?
)