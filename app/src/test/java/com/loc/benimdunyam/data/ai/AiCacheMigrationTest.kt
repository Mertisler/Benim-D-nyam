package com.loc.benimdunyam.data.ai

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.loc.benimdunyam.data.ai.history.AI_CACHE_MIGRATION_1_2
import com.loc.benimdunyam.data.ai.history.AskedQuestionEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Yapay zekâ veritabanı 1 → 2: dışa aktarılan 1. sürüm şemasıyla kurulan veritabanı,
 * soru geçmişi tablosu eklenerek açılır ve mevcut analizler korunur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiCacheMigrationTest {

    @Test
    fun surum1den2yeGecisVeriyiKorur() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "ai-cache-migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }

        val schema = JSONObject(File("schemas/com.loc.benimdunyam.data.ai.AiCacheDatabase/1.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO memory_insights VALUES ('j-1','fp','[\"aile\"]','[]','[]','HAPPY','Özet','v',1,0,0,0,0)"
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AiCacheDatabase::class.java, name)
            .addMigrations(AI_CACHE_MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, room.insightDao().count())
            room.questionDao().insert(AskedQuestionEntity(question = "Ne?", answer = "Şu [1].", sourcesJson = "[\"j-1\"]",
                notFound = false, askedAt = 5))
            assertEquals(1, room.questionDao().count())
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
