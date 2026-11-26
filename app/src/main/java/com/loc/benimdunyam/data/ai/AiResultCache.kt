package com.loc.benimdunyam.data.ai

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Yapay zekâ sonuçları önbelleği: aynı girdi için aynı isteği tekrar ödememek için.
 * Yeniden üretilebilir veridir; yedeğe ve senkrona girmez.
 */
interface AiResultStore {
    suspend fun get(task: AiTask, inputHash: String): AiResponse?
    /** Kaydeder ve aynı görevin farklı sürümle (model/talimat) üretilmiş eski sonuçlarını siler. */
    suspend fun put(inputHash: String, response: AiResponse, now: Long)
    suspend fun clear()
    suspend fun count(): Int
}

@Entity(tableName = "ai_results")
data class AiResultEntity(
    /** "görev|girdiÖzeti" */
    @PrimaryKey val key: String,
    val task: String,
    val inputHash: String,
    val version: String,
    val kind: String,
    val payload: String,
    val createdAt: Long
)

@Dao
interface AiResultDao {
    @Query("SELECT * FROM ai_results WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AiResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiResultEntity)

    @Query("DELETE FROM ai_results WHERE task = :task AND version != :version")
    suspend fun deleteOtherVersions(task: String, version: String)

    @Query("DELETE FROM ai_results")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM ai_results")
    suspend fun count(): Int
}

@Database(
    entities = [
        AiResultEntity::class,
        com.loc.benimdunyam.data.ai.insight.MemoryInsightEntity::class,
        com.loc.benimdunyam.data.ai.history.AskedQuestionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AiCacheDatabase : RoomDatabase() {
    abstract fun dao(): AiResultDao
    abstract fun insightDao(): com.loc.benimdunyam.data.ai.insight.MemoryInsightDao
    abstract fun questionDao(): com.loc.benimdunyam.data.ai.history.AskedQuestionDao
}

/** Her çalışma alanının (hesap) kendi yapay zekâ veritabanı olur; ana veritabanı adından türetilir. */
@Singleton
class AiCacheDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ActiveWorkspaceSession
) {
    val db: AiCacheDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val scope = MessageDigest.getInstance("SHA-256")
            .digest(session.databaseNames()[0].toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        Room.databaseBuilder(context.applicationContext, AiCacheDatabase::class.java, "bd_ai_cache_v1_$scope.db")
            .addMigrations(com.loc.benimdunyam.data.ai.history.AI_CACHE_MIGRATION_1_2)
            // Yalnızca yeniden üretilebilir veri: bilinmeyen bir sürümde sıfırdan kurulur.
            .fallbackToDestructiveMigration()
            .build()
    }
}

/** Sonucun yalnızca içerik alanları saklanır; görev ve sürüm ayrı sütunlardadır. */
internal data class StoredAiPayload(
    val text: String? = null,
    val json: Map<String, Any?>? = null,
    val sources: Map<String, String> = emptyMap(),
    val model: String = "",
    val truncated: Boolean = false
)

@Singleton
class RoomAiResultStore @Inject constructor(
    private val provider: AiCacheDatabaseProvider
) : AiResultStore {
    private val gson = Gson()
    private val db get() = provider.db

    private fun key(task: AiTask, hash: String) = "${task.wireName}|$hash"

    override suspend fun get(task: AiTask, inputHash: String): AiResponse? = withContext(Dispatchers.IO) {
        val e = db.dao().get(key(task, inputHash)) ?: return@withContext null
        val payload = runCatching {
            gson.fromJson<StoredAiPayload>(e.payload, object : TypeToken<StoredAiPayload>() {}.type)
        }.getOrNull() ?: return@withContext null
        AiResponse(
            task = task,
            kind = runCatching { AiResultKind.valueOf(e.kind) }.getOrDefault(AiResultKind.TEXT),
            text = payload.text,
            json = payload.json,
            sources = payload.sources,
            version = e.version,
            model = payload.model,
            truncated = payload.truncated,
            fromCache = true
        )
    }

    override suspend fun put(inputHash: String, response: AiResponse, now: Long) = withContext(Dispatchers.IO) {
        val payload = StoredAiPayload(response.text, response.json, response.sources, response.model, response.truncated)
        db.dao().upsert(
            AiResultEntity(
                key = key(response.task, inputHash),
                task = response.task.wireName,
                inputHash = inputHash,
                version = response.version,
                kind = response.kind.name,
                payload = gson.toJson(payload),
                createdAt = now
            )
        )
        db.dao().deleteOtherVersions(response.task.wireName, response.version)
    }

    /** Önbellekle birlikte anı analizlerini de siler ("tüm yapay zekâ sonuçları"). */
    override suspend fun clear() = withContext(Dispatchers.IO) {
        db.dao().clear()
        db.insightDao().clear()
        db.questionDao().clear()
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) { db.dao().count() + db.insightDao().count() }
}
