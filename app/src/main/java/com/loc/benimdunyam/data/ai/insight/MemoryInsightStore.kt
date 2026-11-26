package com.loc.benimdunyam.data.ai.insight

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.loc.benimdunyam.data.ai.AiCacheDatabaseProvider
import com.loc.benimdunyam.domain.model.JournalMood
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Anı analizleri. Yeniden üretilebilir; yedeğe ve senkrona girmez. */
interface MemoryInsightStore {
    fun observeAll(): Flow<List<MemoryInsight>>
    suspend fun getAll(): List<MemoryInsight>
    suspend fun get(journalId: String): MemoryInsight?
    suspend fun upsert(insight: MemoryInsight)
    suspend fun delete(journalIds: List<String>)
}

@Singleton
class RoomMemoryInsightStore @Inject constructor(
    private val provider: AiCacheDatabaseProvider
) : MemoryInsightStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val dao get() = provider.db.insightDao()

    private fun list(json: String): List<String> =
        runCatching { gson.fromJson<List<String>>(json, listType) }.getOrNull().orEmpty()

    private fun MemoryInsightEntity.toModel() = MemoryInsight(
        journalId = journalId,
        fingerprint = fingerprint,
        topics = list(topicsJson),
        places = list(placesJson),
        events = list(eventsJson),
        moodSuggestion = JournalMood.entries.firstOrNull { it.name == moodSuggestion },
        summary = summary,
        version = version,
        analyzedAt = analyzedAt,
        userEdited = userEdited,
        hidden = hidden,
        failed = failed,
        moodDismissed = moodDismissed
    )

    private fun MemoryInsight.toEntity() = MemoryInsightEntity(
        journalId = journalId,
        fingerprint = fingerprint,
        topicsJson = gson.toJson(topics),
        placesJson = gson.toJson(places),
        eventsJson = gson.toJson(events),
        moodSuggestion = moodSuggestion?.name,
        summary = summary,
        version = version,
        analyzedAt = analyzedAt,
        userEdited = userEdited,
        hidden = hidden,
        failed = failed,
        moodDismissed = moodDismissed
    )

    override fun observeAll(): Flow<List<MemoryInsight>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }.flowOn(Dispatchers.IO)

    override suspend fun getAll(): List<MemoryInsight> = withContext(Dispatchers.IO) { dao.getAll().map { it.toModel() } }

    override suspend fun get(journalId: String): MemoryInsight? = withContext(Dispatchers.IO) { dao.get(journalId)?.toModel() }

    override suspend fun upsert(insight: MemoryInsight) = withContext(Dispatchers.IO) { dao.upsert(insight.toEntity()) }

    override suspend fun delete(journalIds: List<String>) = withContext(Dispatchers.IO) {
        journalIds.chunked(500).forEach { dao.delete(it) }
    }
}
