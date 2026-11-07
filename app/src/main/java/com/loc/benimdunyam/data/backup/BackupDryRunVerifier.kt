
package com.loc.benimdunyam.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.google.gson.Gson
import com.loc.benimdunyam.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupDryRunResult(
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int
)

/**
 * Yedek içeriğini gerçek Room veritabanına
 * dokunmadan geçici veritabanında dener.
 *
 * Veri ekleme, okuma ve yabancı anahtar
 * kontrollerini gerçekleştirir.
 */
@Singleton
class BackupDryRunVerifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()

    suspend fun verify(
        preparedBackup: PreparedBackup
    ): BackupDryRunResult = withContext(Dispatchers.IO) {

        val data = preparedBackup.data

        // Gerçek AppDatabase ile aynı şema,
        // ancak tamamen ayrı ve geçici veritabanı.
        val temporaryDatabase = Room
            .inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            )
            .build()

        try {
            val dao = temporaryDatabase.backupRestoreDao()

            // 1. Altı tablodaki verileri Room'a yazmayı dene.
            temporaryDatabase.withTransaction {

                dao.insertSeasons(data.seasons)

                dao.insertPeople(data.people)

                dao.insertEpisodes(data.episodes)

                dao.insertJournals(data.journals)

                dao.insertMentions(data.mentions)

                dao.insertSpecialMemories(
                    data.specialMemories
                )
            }

            // 2. SQLite yabancı anahtar kontrolü.
            temporaryDatabase.openHelper
                .readableDatabase
                .query("PRAGMA foreign_key_check")
                .use { cursor ->

                    if (cursor.moveToFirst()) {
                        val tableName = cursor.getString(0)

                        throw IOException(
                            "Yedekte yabancı anahtar hatası: " +
                                    tableName
                        )
                    }
                }

            // 3. Yazılan verileri Room'dan tekrar oku
            // ve yedekteki kayıtlarla karşılaştır.

            compareRows(
                label = "Günlükler",
                expected = data.journals,
                actual = temporaryDatabase.journalDao()
                    .getAllJournalEntities(),
                keyOf = { it.id }
            )

            compareRows(
                label = "Kişiler",
                expected = data.people,
                actual = temporaryDatabase.personDao()
                    .getAllPersonEntities(),
                keyOf = { it.id }
            )

            compareRows(
                label = "Sezonlar",
                expected = data.seasons,
                actual = temporaryDatabase.lifeSeasonDao()
                    .getAllSeasonEntities(),
                keyOf = { it.id }
            )

            compareRows(
                label = "Bölümler",
                expected = data.episodes,
                actual = temporaryDatabase.lifeEpisodeDao()
                    .getAllEpisodeEntities(),
                keyOf = { it.id }
            )

            compareRows(
                label = "Kişi-anı bağlantıları",
                expected = data.mentions,
                actual = temporaryDatabase
                    .journalPersonMentionDao()
                    .getAllMentionEntities(),
                keyOf = {
                    "${it.journalId}|${it.personId}"
                }
            )

            compareRows(
                label = "Unutulmaz anılar",
                expected = data.specialMemories,
                actual = temporaryDatabase
                    .personSpecialMemoryDao()
                    .getAllSpecialMemoryEntities(),
                keyOf = {
                    "${it.personId}|${it.journalId}"
                }
            )

            BackupDryRunResult(
                journalCount = data.journals.size,
                personCount = data.people.size,
                seasonCount = data.seasons.size,
                episodeCount = data.episodes.size,
                mentionCount = data.mentions.size,
                specialMemoryCount =
                    data.specialMemories.size
            )

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e

        } catch (e: Exception) {
            throw IOException(
                "Yedek geçici Room doğrulamasından geçemedi: " +
                        (e.localizedMessage ?: "Bilinmeyen hata"),
                e
            )

        } finally {
            temporaryDatabase.close()
        }
    }

    /**
     * Room'a yazılıp yeniden okunan kayıtların
     * içeriğini ve sayısını karşılaştırır.
     *
     * Kayıt sırasını normalize ettiğimiz için
     * DAO'nun döndürdüğü sıra sonucu etkilemez.
     */
    private fun <T> compareRows(
        label: String,
        expected: List<T>,
        actual: List<T>,
        keyOf: (T) -> String
    ) {

        if (expected.size != actual.size) {
            throw IOException(
                "$label: Kayıt sayısı eşleşmiyor."
            )
        }

        val expectedJson = gson.toJson(
            expected.sortedBy { keyOf(it) }
        )

        val actualJson = gson.toJson(
            actual.sortedBy { keyOf(it) }
        )

        if (expectedJson != actualJson) {
            throw IOException(
                "$label: Room'a aktarılan veriler " +
                        "yedekle uyuşmuyor."
            )
        }
    }
}
