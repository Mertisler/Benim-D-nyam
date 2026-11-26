package com.loc.benimdunyam.data.ai.narrative

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.loc.benimdunyam.data.ai.AiCacheDatabaseProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Bir dönem, yıl ya da kişi için üretilmiş hikâye yazısı. [text] [1], [2]… işaretlidir. */
data class StoredNarrative(
    val scopeKey: String,
    /** Üretildiği anıların özeti; anılar değişince farklılaşır ("güncel değil"). */
    val inputHash: String,
    val text: String,
    val sources: List<String>,
    val version: String,
    val createdAt: Long
)

@Entity(tableName = "ai_narratives")
data class StoredNarrativeEntity(
    @PrimaryKey val scopeKey: String,
    val inputHash: String,
    val text: String,
    val sourcesJson: String,
    val version: String,
    val createdAt: Long
)

@Dao
interface StoredNarrativeDao {
    @Query("SELECT * FROM ai_narratives WHERE scopeKey = :key LIMIT 1")
    fun observe(key: String): Flow<StoredNarrativeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StoredNarrativeEntity)

    @Query("DELETE FROM ai_narratives WHERE scopeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM ai_narratives")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM ai_narratives")
    suspend fun count(): Int
}

/** Sürüm 2 → 3: hikâye yazıları tablosu. */
val AI_CACHE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_narratives` (" +
                "`scopeKey` TEXT NOT NULL, `inputHash` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`sourcesJson` TEXT NOT NULL, `version` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`scopeKey`))"
        )
    }
}

interface NarrativeStore {
    fun observe(scopeKey: String): Flow<StoredNarrative?>
    suspend fun save(narrative: StoredNarrative)
    suspend fun delete(scopeKey: String)
}

@Singleton
class RoomNarrativeStore @Inject constructor(
    private val provider: AiCacheDatabaseProvider
) : NarrativeStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val dao get() = provider.db.narrativeDao()

    override fun observe(scopeKey: String): Flow<StoredNarrative?> = dao.observe(scopeKey).map { e ->
        e?.let {
            StoredNarrative(
                scopeKey = it.scopeKey,
                inputHash = it.inputHash,
                text = it.text,
                sources = runCatching { gson.fromJson<List<String>>(it.sourcesJson, listType) }.getOrNull().orEmpty(),
                version = it.version,
                createdAt = it.createdAt
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun save(narrative: StoredNarrative) = withContext(Dispatchers.IO) {
        dao.upsert(
            StoredNarrativeEntity(
                scopeKey = narrative.scopeKey,
                inputHash = narrative.inputHash,
                text = narrative.text,
                sourcesJson = gson.toJson(narrative.sources),
                version = narrative.version,
                createdAt = narrative.createdAt
            )
        )
    }

    override suspend fun delete(scopeKey: String) = withContext(Dispatchers.IO) { dao.delete(scopeKey) }
}
