package com.loc.benimdunyam.feature.yearreview.featured

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.loc.benimdunyam.domain.model.JournalEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Her yıl en çok 5 anı. Bütün değişiklikler DB transaction içinde yapılır. */
enum class FeaturedToggleResult { ADDED, REMOVED, LIMIT_REACHED }

@Singleton
class YearFeaturedMemoryRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val database: YearFeaturedMemoryDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            YearFeaturedMemoryDatabase::class.java,
            "benim_dunyam_featured_memories.db"
        ).build()
    }

    fun observeAll(): Flow<List<YearFeaturedMemoryEntity>> = database.dao().observeAll()

    suspend fun toggle(journalId: String, year: Int): FeaturedToggleResult {
        require(journalId.isNotBlank()) { "Anı kimliği boş olamaz." }
        return database.withTransaction {
            val dao = database.dao()
            val selected = dao.getById(journalId)
            if (selected != null) {
                dao.delete(journalId)
                compactYear(dao, selected.year)
                FeaturedToggleResult.REMOVED
            } else {
                val current = dao.getForYear(year)
                if (!YearFeaturedMemoryRules.canAdd(current.map { it.journalId }, journalId)) {
                    FeaturedToggleResult.LIMIT_REACHED
                } else {
                    dao.insert(YearFeaturedMemoryEntity(
                        journalId = journalId,
                        year = year,
                        position = current.size,
                        addedAt = System.currentTimeMillis()
                    ))
                    FeaturedToggleResult.ADDED
                }
            }
        }
    }

    suspend fun remove(journalId: String) {
        database.withTransaction {
            val dao = database.dao()
            val item = dao.getById(journalId) ?: return@withTransaction
            dao.delete(journalId)
            compactYear(dao, item.year)
        }
    }

    /** direction: -1 yukarı, +1 aşağı. */
    suspend fun move(journalId: String, direction: Int) {
        require(direction == -1 || direction == 1)
        database.withTransaction {
            val dao = database.dao()
            val current = dao.getById(journalId) ?: return@withTransaction
            val list = dao.getForYear(current.year)
            val newOrder = YearFeaturedMemoryRules.move(
                list.map { it.journalId }, journalId, direction
            )
            newOrder.forEachIndexed { index, id ->
                if (list[index].journalId != id) dao.updatePosition(id, index)
            }
        }
    }

    /**
     * Ana Room'dan anı silinmişse seçimi de temizler; anının tarihi başka yıla
     * taşınmışsa yıl bilgisini senkronize eder. Her şey aynı transaction'da.
     */
    suspend fun reconcile(journals: List<JournalEntry>) {
        val actualYears = journals.associate { it.id to yearOf(it.entryDate) }
        database.withTransaction {
            val dao = database.dao()
            val saved = dao.getAll()
            saved.forEach { entry ->
                val actualYear = actualYears[entry.journalId]
                when {
                    actualYear == null -> dao.delete(entry.journalId)
                    actualYear != entry.year -> dao.updateYear(entry.journalId, actualYear)
                }
            }
            // Sıraları normalize et. Başka bir yıla taşınan ve 5 sınırını aşan
            // anılar için yalnızca ilk 5 seçim tutulur.
            dao.getAll().groupBy { it.year }.forEach { (year, _) ->
                val entries = dao.getForYear(year)
                entries.drop(MAX_PER_YEAR).forEach { dao.delete(it.journalId) }
                compactYear(dao, year)
            }
        }
    }

    private suspend fun compactYear(dao: YearFeaturedMemoryDao, year: Int) {
        dao.getForYear(year).forEachIndexed { index, item ->
            if (item.position != index) dao.updatePosition(item.journalId, index)
        }
    }

    companion object {
        const val MAX_PER_YEAR = YearFeaturedMemoryRules.LIMIT

        fun yearOf(timestamp: Long): Int =
            Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
    }
}
