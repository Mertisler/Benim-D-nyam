package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row per (other person, shared journal). */
data class PersonConnectionRow(
    val relatedPersonId: String,
    val relatedPersonName: String,
    val relatedProfileImagePath: String?,
    val sharedJournalId: String
)

@Dao
interface PersonConnectionDao {
    /**
     * One reactive, batched self-join; no per-person DAO calls.
     * A mention's PK (journalId, personId) makes each pair unique.
     * Joining people also refreshes the avatar/name if the person is edited.
     */
    @Query(
        """
        SELECT related.personId AS relatedPersonId,
               p.name AS relatedPersonName,
               p.profileImagePath AS relatedProfileImagePath,
               related.journalId AS sharedJournalId
        FROM journal_person_mentions AS base
        INNER JOIN journal_person_mentions AS related
            ON base.journalId = related.journalId
            AND base.personId != related.personId
        INNER JOIN people AS p ON p.id = related.personId
        WHERE base.personId = :personId
        ORDER BY p.name COLLATE NOCASE ASC, related.journalId ASC
        """
    )
    fun observeConnectionRows(personId: String): Flow<List<PersonConnectionRow>>
}
