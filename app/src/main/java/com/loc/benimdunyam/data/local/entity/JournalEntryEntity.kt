package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey
    val id: String, // UUID
    val title: String,
    val content: String,
    val entryDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val seasonId: String? = null,
    val episodeId: String? = null
)