package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class NetworkPersonRow(
    val id: String,
    val name: String,
    val profileImagePath: String?
)

data class NetworkMentionRow(
    val journalId: String,
    val personId: String
)

@Dao
interface PeopleNetworkDao {
    // İki toplu gözlem: her kişi için ayrı sorgu çalıştırılmaz.
    @Query("SELECT id, name, profileImagePath FROM people ORDER BY name COLLATE NOCASE, id")
    fun observePeople(): Flow<List<NetworkPersonRow>>

    @Query("SELECT journalId, personId FROM journal_person_mentions ORDER BY journalId, personId")
    fun observeMentions(): Flow<List<NetworkMentionRow>>
}
