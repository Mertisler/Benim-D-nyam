package com.loc.benimdunyam.data.ai.insight

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.loc.benimdunyam.domain.model.JournalMood
import kotlinx.coroutines.flow.Flow

/** Bir anının yapay zekâ analizi (konular, yerler, olaylar, ruh hali önerisi, özet). */
data class MemoryInsight(
    val journalId: String,
    /** Analiz edilen girdinin özeti; anı değişince farklılaşır ve yeniden analiz edilir. */
    val fingerprint: String,
    val topics: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val events: List<String> = emptyList(),
    val moodSuggestion: JournalMood? = null,
    val summary: String? = null,
    val version: String = "",
    val analyzedAt: Long = 0L,
    /** Kullanıcı bir etiketi kaldırdı. */
    val userEdited: Boolean = false,
    /** Kullanıcı bu anının notlarını sildi; anı değişene kadar yeniden üretilmez. */
    val hidden: Boolean = false,
    /** Sunucu bu anıyı işleyemedi; anı değişene kadar tekrar denenmez. */
    val failed: Boolean = false,
    /** Kullanıcı ruh hali önerisini reddetti. */
    val moodDismissed: Boolean = false
) {
    val isVisible: Boolean get() = !hidden && !failed
}

enum class InsightTagKind { TOPIC, PLACE, EVENT }

@Entity(tableName = "memory_insights")
data class MemoryInsightEntity(
    @PrimaryKey val journalId: String,
    val fingerprint: String,
    val topicsJson: String,
    val placesJson: String,
    val eventsJson: String,
    val moodSuggestion: String?,
    val summary: String?,
    val version: String,
    val analyzedAt: Long,
    val userEdited: Boolean,
    val hidden: Boolean,
    val failed: Boolean,
    val moodDismissed: Boolean
)

@Dao
interface MemoryInsightDao {
    @Query("SELECT * FROM memory_insights")
    fun observeAll(): Flow<List<MemoryInsightEntity>>

    @Query("SELECT * FROM memory_insights")
    suspend fun getAll(): List<MemoryInsightEntity>

    @Query("SELECT * FROM memory_insights WHERE journalId = :journalId LIMIT 1")
    suspend fun get(journalId: String): MemoryInsightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryInsightEntity)

    @Query("DELETE FROM memory_insights WHERE journalId IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM memory_insights")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM memory_insights")
    suspend fun count(): Int
}
