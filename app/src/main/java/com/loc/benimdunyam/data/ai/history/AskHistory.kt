package com.loc.benimdunyam.data.ai.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
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

/** "Anılarına sor" geçmişindeki bir soru. [sources] cevapta [1], [2]… sırasıyla anı kimlikleri. */
data class AskedQuestion(
    val id: Long = 0,
    val question: String,
    val answer: String,
    val sources: List<String>,
    val notFound: Boolean,
    val askedAt: Long
)

@Entity(tableName = "ai_questions")
data class AskedQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String,
    val sourcesJson: String,
    val notFound: Boolean,
    val askedAt: Long
)

@Dao
interface AskedQuestionDao {
    @Query("SELECT * FROM ai_questions ORDER BY askedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AskedQuestionEntity>>

    @Insert
    suspend fun insert(entity: AskedQuestionEntity): Long

    @Query("DELETE FROM ai_questions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ai_questions WHERE id NOT IN (SELECT id FROM ai_questions ORDER BY askedAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM ai_questions")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM ai_questions")
    suspend fun count(): Int
}

/** Sürüm 1 → 2: soru geçmişi tablosu. */
val AI_CACHE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_questions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question` TEXT NOT NULL, " +
                "`answer` TEXT NOT NULL, `sourcesJson` TEXT NOT NULL, `notFound` INTEGER NOT NULL, " +
                "`askedAt` INTEGER NOT NULL)"
        )
    }
}

interface AskHistoryStore {
    fun observeRecent(limit: Int = KEEP): Flow<List<AskedQuestion>>
    suspend fun add(question: AskedQuestion): Long
    suspend fun delete(id: Long)
    suspend fun clear()

    companion object {
        const val KEEP = 50
    }
}

/** Soru geçmişi yalnızca bu cihazda tutulur; yapay zekâ kapatılınca silinir. */
@Singleton
class RoomAskHistoryStore @Inject constructor(
    private val provider: AiCacheDatabaseProvider
) : AskHistoryStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val dao get() = provider.db.questionDao()

    override fun observeRecent(limit: Int): Flow<List<AskedQuestion>> = dao.observeRecent(limit).map { rows ->
        rows.map { e ->
            AskedQuestion(
                id = e.id,
                question = e.question,
                answer = e.answer,
                sources = runCatching { gson.fromJson<List<String>>(e.sourcesJson, listType) }.getOrNull().orEmpty(),
                notFound = e.notFound,
                askedAt = e.askedAt
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun add(question: AskedQuestion): Long = withContext(Dispatchers.IO) {
        val id = dao.insert(
            AskedQuestionEntity(
                question = question.question,
                answer = question.answer,
                sourcesJson = gson.toJson(question.sources),
                notFound = question.notFound,
                askedAt = question.askedAt
            )
        )
        dao.trim(AskHistoryStore.KEEP)
        id
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    override suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}
