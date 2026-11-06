package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A special memory must refer to an actual person-journal link.
 * Removing that link (or deleting its person) cascades to this row.
 */
@Entity(
    tableName = "person_special_memories",
    primaryKeys = ["personId", "journalId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = JournalPersonMentionEntity::class,
            parentColumns = ["journalId", "personId"],
            childColumns = ["journalId", "personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["journalId", "personId"])]
)
data class PersonSpecialMemoryEntity(
    val personId: String,
    val journalId: String,
    val category: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
)
