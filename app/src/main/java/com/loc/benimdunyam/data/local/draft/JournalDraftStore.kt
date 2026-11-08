package com.loc.benimdunyam.data.local.draft

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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Kaydedilmemis editor degisiklikleri icin cihaz-ici, gecici veri.
 * Kullanici tarafindan "Aniyi Kaydet" ile olusturulan asil JournalEntry degildir.
 *
 * Not: entryDateIso = tam ISO zaman damgasi (tarih + saat + saat dilimi), moodName = JournalMood.name,
 * mentionedPeopleJson = kisi kimliklerinden olusan JSON dizesi (ornegin ["p1","p2"]).
 */
@Entity(tableName = "journal_drafts")
data class JournalDraftEntity(
    @PrimaryKey val draftKey: String,
    val title: String = "",
    val content: String = "",
    val entryDateIso: String? = null,
    val seasonId: String? = null,
    val episodeId: String? = null,
    val moodName: String? = null,
    val mentionedPeopleJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface JournalDraftDao {
    @Query("SELECT * FROM journal_drafts WHERE draftKey = :draftKey LIMIT 1")
    suspend fun findByKey(draftKey: String): JournalDraftEntity?

    @Query("SELECT * FROM journal_drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JournalDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: JournalDraftEntity)

    @Query("DELETE FROM journal_drafts WHERE draftKey = :draftKey")
    suspend fun delete(draftKey: String)

    @Query("DELETE FROM journal_drafts")
    suspend fun deleteAll()
}

/**
 * Ana AppDatabase'den bagimsiz tutuldugu icin mevcut .bdbackup semasini,
 * AppDatabase surumunu ve migrasyonlarini degistirmez.
 */
@Database(
    entities = [JournalDraftEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JournalDraftDatabase : RoomDatabase() {
    abstract fun journalDraftDao(): JournalDraftDao
}

@Singleton
class JournalDraftStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val database: JournalDraftDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Room.databaseBuilder(
            context.applicationContext,
            JournalDraftDatabase::class.java,
            "benim_dunyam_journal_drafts.db"
        ).build()
    }

    private val dao: JournalDraftDao
        get() = database.journalDraftDao()

    suspend fun load(key: String): JournalDraftEntity? = dao.findByKey(key)

    suspend fun save(draft: JournalDraftEntity) {
        require(draft.draftKey.isNotBlank()) { "draftKey cannot be blank" }
        dao.upsert(draft.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(key: String) = dao.delete(key)

    suspend fun deleteAll() = dao.deleteAll()

    fun observeAll(): Flow<List<JournalDraftEntity>> = dao.observeAll()

    companion object {
        /** Tek yeni ani taslagi, her kaydedilmis ani icin ayri duzenleme taslagi. */
        fun keyFor(journalId: String?): String =
            journalId?.takeIf { it.isNotBlank() }?.let { "edit:$it" } ?: "new"
    }
}
