package com.loc.benimdunyam.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Eski sürümlerde (5–8) oluşturulmuş gerçek bir veritabanının, uygulamanın
 * kullandığı migration zinciriyle sürüm 9'a yükseltildiğini sınar.
 *
 * Room açılışta şemayı varlık (entity) tanımlarıyla karşılaştırır; bir sütun,
 * indeks veya yabancı anahtar uyuşmazsa açılış hata verir. Bu yüzden
 * "Room açıldı" kendi başına migration'ın doğru olduğunun kanıtıdır;
 * ek olarak kullanıcı verisinin kaybolmadığını da kontrol ederiz.
 *
 * Sürüm 5 şeması git geçmişindeki cdb5ada commit'inin entity'lerinden
 * çıkarılmıştır (o sürümde exportSchema kapalıydı).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    /** Uygulamadaki üç Room.databaseBuilder çağrısının kullandığı zincirle aynı. */
    private val allMigrations = arrayOf(
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9
    )

    private var room: AppDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    // ------------------------------------------------------------------
    // Eski sürüm veritabanı kurulumu
    // ------------------------------------------------------------------

    private val v5Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `entryDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `seasonId` TEXT, `episodeId` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `journal_person_mentions` (`journalId` TEXT NOT NULL, `personId` TEXT NOT NULL, `mentionIndex` INTEGER NOT NULL, `startOffset` INTEGER, `endOffset` INTEGER, PRIMARY KEY(`journalId`, `personId`))",
        "CREATE TABLE IF NOT EXISTS `life_seasons` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `colorHex` TEXT NOT NULL, `coverImagePath` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `life_episodes` (`id` TEXT NOT NULL, `seasonId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'eski-surum-5')"
    )

    /** Sürüm 5'te bir kullanıcının sahip olabileceği tipik arşiv. */
    private fun insertV5UserData(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO life_seasons VALUES ('s1','Üniversite','Ankara yılları',1000,NULL,'#FFAA00','/data/kapak.jpg',1000)")
        db.execSQL("INSERT INTO life_episodes VALUES ('e1','s1','1. sınıf','',0,1000,NULL,1000)")
        db.execSQL("INSERT INTO people VALUES ('p1','Ayşe','Çocukluk arkadaşı',1500,1500)")
        db.execSQL("INSERT INTO people VALUES ('p2','Mehmet',NULL,1600,1600)")
        db.execSQL("INSERT INTO journal_entries VALUES ('j1','İlk gün','@Ayşe ile tanıştım 😊',2000,2000,2000,'s1','e1')")
        db.execSQL("INSERT INTO journal_entries VALUES ('j2','Serbest','Yağmur',3000,3000,3000,NULL,NULL)")
        db.execSQL("INSERT INTO journal_person_mentions VALUES ('j1','p1',0,0,5)")
        db.execSQL("INSERT INTO journal_person_mentions VALUES ('j2','p2',0,NULL,NULL)")
    }

    /**
     * Sürüm 5 veritabanı oluşturur, sonra uygulamanın kendi migration'larıyla
     * [startVersion]'a yükseltir. Böylece 6, 7, 8 sürümlerinde kalmış
     * kullanıcıları da taklit edebiliriz.
     */
    private fun createDatabaseAt(startVersion: Int) {
        val callback = object : SupportSQLiteOpenHelper.Callback(startVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                v5Schema.forEach(db::execSQL)
                insertV5UserData(db)
                allMigrations
                    .filter { it.endVersion <= startVersion }
                    .forEach { it.migrate(db) }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("Beklenmeyen yükseltme")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()
    }

    private fun openWithRoom(vararg migrations: Migration = allMigrations): SupportSQLiteDatabase {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*migrations)
            .allowMainThreadQueries()
            .build()
        room = db
        // Bu çağrı migration'ları çalıştırır ve şemayı doğrular.
        return db.openHelper.writableDatabase
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.string(sql: String): String? =
        query(sql).use { it.moveToFirst(); if (it.isNull(0)) null else it.getString(0) }

    private fun assertUserDataPreserved(db: SupportSQLiteDatabase) {
        assertEquals(2, db.count("journal_entries"))
        assertEquals(2, db.count("people"))
        assertEquals(2, db.count("journal_person_mentions"))
        assertEquals(1, db.count("life_seasons"))
        assertEquals(1, db.count("life_episodes"))

        assertEquals("@Ayşe ile tanıştım 😊", db.string("SELECT content FROM journal_entries WHERE id='j1'"))
        assertEquals("e1", db.string("SELECT episodeId FROM journal_entries WHERE id='j1'"))
        assertEquals("Çocukluk arkadaşı", db.string("SELECT note FROM people WHERE id='p1'"))
        assertEquals("/data/kapak.jpg", db.string("SELECT coverImagePath FROM life_seasons WHERE id='s1'"))
        assertEquals(9, db.version)
    }

    // ------------------------------------------------------------------
    // Testler
    // ------------------------------------------------------------------

    @Test
    fun surum5ten9aVeriKaybolmadanYukselir() {
        createDatabaseAt(5)
        val db = openWithRoom()

        assertUserDataPreserved(db)
        // 5→6: yeni fotoğraf sütunları eski kişilerde boş başlamalı.
        assertNull(db.string("SELECT profileImagePath FROM people WHERE id='p1'"))
        assertNull(db.string("SELECT coverImagePath FROM people WHERE id='p1'"))
        // Yeni tablolar boş olmalı; eski kayıtlar buluta otomatik kuyruğa ALINMAMALI.
        assertEquals(0, db.count("person_special_memories"))
        assertEquals(0, db.count("journal_sync_events"))
        assertEquals(0, db.count("pending_mood_merges"))
    }

    @Test
    fun surum6dan9aYukselir() {
        createDatabaseAt(6)
        assertUserDataPreserved(openWithRoom())
    }

    @Test
    fun surum7den9aOzelAnilarKorunarakYukselir() {
        createDatabaseAt(7)
        // Sürüm 7'de oluşturulmuş bir özel anı.
        insertRaw("INSERT INTO person_special_memories VALUES ('p1','j1','ilk_tanisma','Not',2100,2100)")

        val db = openWithRoom()
        assertUserDataPreserved(db)
        assertEquals("ilk_tanisma", db.string("SELECT category FROM person_special_memories WHERE personId='p1'"))
    }

    @Test
    fun surum8den9aSyncKuyruguKorunarakYukselir() {
        createDatabaseAt(8)
        insertRaw("INSERT INTO journal_sync_events VALUES ('j1','uid-1','UPSERT','PENDING',3,4000)")

        val db = openWithRoom()
        assertUserDataPreserved(db)
        assertEquals("PENDING", db.string("SELECT state FROM journal_sync_events WHERE journalId='j1'"))
    }

    @Test
    fun yeniKurulumSurum9OlarakAcilir() {
        val db = openWithRoom()
        assertEquals(9, db.version)
        assertEquals(0, db.count("journal_entries"))
    }

    @Test
    fun migrationSonrasiOzelAniYabanciAnahtarlariCalisir() {
        createDatabaseAt(5)
        val db = openWithRoom()

        // Geçerli bahsetmeye bağlı özel anı eklenebilmeli.
        db.execSQL("INSERT INTO person_special_memories VALUES ('p1','j1','ilk_tanisma',NULL,1,1)")

        // Bahsetmesi olmayan (j2-p1) özel anı reddedilmeli.
        try {
            db.execSQL("INSERT INTO person_special_memories VALUES ('p1','j2','x',NULL,1,1)")
            fail("Bahsetmesi olmayan özel anı eklenmemeli")
        } catch (expected: SQLiteConstraintException) {
        }

        // Bahsetme silinince özel anı da otomatik silinmeli (CASCADE).
        db.execSQL("DELETE FROM journal_person_mentions WHERE journalId='j1' AND personId='p1'")
        assertEquals(0, db.count("person_special_memories"))
    }

    @Test
    fun eksikMigrationZinciriVeriyiSessizceSilmez() {
        createDatabaseAt(5)
        // 7→8 unutulmuş bir derleme: Room yıkıcı geri dönüş yapmak yerine
        // hata vermeli; kullanıcı arşivi dosyada olduğu gibi kalmalı.
        try {
            openWithRoom(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_8_9)
            fail("Eksik migration zinciri fark edilmeliydi")
        } catch (expected: IllegalStateException) {
        }
        room?.close()
        room = null

        openRaw().use { db ->
            assertEquals(5, db.version)
            db.rawQuery("SELECT COUNT(*) FROM journal_entries", null).use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Sürüm numarasına dokunmadan dosyayı doğrudan açar
     * (SupportSQLiteOpenHelper, sürüm farkında yükseltme/düşürme yapardı).
     */
    private fun openRaw(): SQLiteDatabase = SQLiteDatabase.openDatabase(
        context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE
    )

    /** Room açılmadan önce eski sürüm veritabanına doğrudan satır ekler. */
    private fun insertRaw(sql: String) {
        openRaw().use { it.execSQL(sql) }
    }
}
