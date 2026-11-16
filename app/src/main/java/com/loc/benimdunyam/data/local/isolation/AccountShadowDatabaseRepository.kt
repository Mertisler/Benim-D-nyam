package com.loc.benimdunyam.data.local.isolation

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.loc.benimdunyam.core.auth.LocalArchiveAccessStore
import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.backup.BackupValidator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.draft.JournalDraftDatabase
import com.loc.benimdunyam.data.local.draft.JournalDraftEntity
import com.loc.benimdunyam.data.local.mood.JournalMoodDatabase
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.domain.model.AccountMigrationRules
import com.loc.benimdunyam.domain.model.AccountShadowStorageRules
import com.loc.benimdunyam.domain.model.AccountShadowCopyInfo
import com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryDatabase
import com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Faz 2E.4: Doğrulanmış şifreli kopyayı dört BAĞIMSIZ Room veritabanına aktarır.
 *
 * Bunlar şu anda uygulamanın aktif veritabanları DEĞİLDİR. Hilt'in eski Room
 * sağlayıcıları, kullanıcının arşivi ve oturum yönlendirmesi aynı kalır.
 *
 * Akış:
 * 1) Firebase hesabı ve yerel arşiv sahibinin uyuştuğunu doğrula.
 * 2) Daha önce doğrulanmış, hesapla bağlı şifreli taşıma kopyasını aç.
 * 3) Özel medya dosyalarını hesap alanına kopyala ve fotoğraf yollarını eşle.
 * 4) Dört ayrı, yeni Room veritabanına kayıtları yaz.
 * 5) SQLite bütünlüğü, yabancı anahtarlar ve kayıt sayılarını doğrula.
 * 6) Tüm kontroller geçerse TAMAMLANDI işaretini en son yaz.
 *
 * Eksik/yarım kopya bulunan hesaba yeniden yazma yapılmaz: mevcut dosyalar
 * hiçbir koşulda otomatik silinmez. Önce güvenli kurtarma gerekir.
 */
@Singleton
class AccountShadowDatabaseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessStore: LocalArchiveAccessStore,
    private val auth: FirebaseAuth,
    private val layout: AccountStorageLayout,
    private val snapshotRepository: AccountMigrationSnapshotRepository,
    private val validator: BackupValidator
) {
    private val mutex = Mutex()
    private val gson = Gson()

    /** Yalnızca ilgili hesabın hazırlanmış kopyasını okur; veri yazmaz. */
    suspend fun inspect(): AccountShadowCopyInfo? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val uid = authorizedUid()
            val marker = completedMarker(uid)
            if (!marker.isFile) return@withContext null
            val result = markerInfo(uid, marker)
            verifyPreparedDatabases(uid, result)
            result
        }
    }

    /**
     * Kullanıcı ekrandaki uyarıyı onayladığında çağrılır.
     * Var olan hesaba ait bir kopyayı değiştirmez ve canlı veritabanını açmaz.
     */
    suspend fun prepareWithConsent(): AccountShadowCopyInfo = mutex.withLock {
        withContext(Dispatchers.IO) {
            val uid = authorizedUid()
            val marker = completedMarker(uid)
            if (marker.isFile) {
                val existing = markerInfo(uid, marker)
                verifyPreparedDatabases(uid, existing)
                return@withContext existing
            }
            val dbNames = layout.plannedDatabaseNames(uid)
            require(dbNames.distinct().size == 4 && dbNames.none { '/' in it || '\\' in it }) {
                "Hesap veritabanı adları güvenli değil."
            }
            val files = dbNames.map(context::getDatabasePath)
            // İşlem yarıda kalmışsa bunların yeni olduğunu varsayıp silmek YASAK.
            check(AccountShadowStorageRules.canPrepare(
                accessStore.ownerUidOrNull(), uid,
                files.any { it.exists() || File(it.path + "-wal").exists() || File(it.path + "-shm").exists() }
            )) {
                "Bu hesapta tamamlanmamış bir yerel kopya bulunuyor. " +
                        "Dosyalara dokunulmadı; güvenli kurtarma gerektirir."
            }
            val root = layout.accountRoot(uid)
            check(root.isDirectory || root.mkdirs()) { "Hesap alanı oluşturulamadı." }
            val mediaFinal = File(root, "media")
            check(!mediaFinal.exists()) { "Bu hesapta henüz tamamlanmamış görseller var." }
            val mediaPending = File(root, "media_preparing")
            check(!mediaPending.exists()) { "Önceki medya hazırlığı tamamlanmamış." }
            check(mediaPending.mkdirs()) { "Medya hazırlık klasörü oluşturulamadı." }

            // Her iki alana da yazılabilecek yeterli disk alanı olup olmadığını kontrol et.
            val encryptedSnapshot = layout.verifiedSnapshot(uid)
            check(root.usableSpace > encryptedSnapshot.length() * 2 + 16L * 1024L * 1024L) {
                "Ayrı veritabanları için yeterli disk alanı yok."
            }
            // Bu blok bittiğinde şifresi çözülmüş geçici medya otomatik temizlenir.
            snapshotRepository.useVerifiedPayload(uid) { payload ->
                val data = validator.parseAndValidate(payload.jsonSections.getValue("room/main.json").toString(Charsets.UTF_8))
                val moods = gson.fromJson(payload.jsonSections.getValue("room/moods.json").toString(Charsets.UTF_8), Array<JournalMoodRecord>::class.java).toList()
                val drafts = gson.fromJson(payload.jsonSections.getValue("room/drafts.json").toString(Charsets.UTF_8), Array<JournalDraftEntity>::class.java).toList()
                val featured = gson.fromJson(payload.jsonSections.getValue("room/featured.json").toString(Charsets.UTF_8), Array<YearFeaturedMemoryEntity>::class.java).toList()
                verifyCrossDatabaseReferences(data, moods, drafts, featured)
                copyMedia(payload, mediaPending)

                // Kaynak veritabanındaki mutlak görsel yolları yeni hesap alanına
                // uyarlanır. Harici content:// URI'leri taşınmadığı için işlem durur.
                val mappedData = data.copy(
                    seasons = data.seasons.map { season ->
                        season.copy(coverImagePath = mappedCoverPath(season.coverImagePath, payload.mediaPaths, mediaFinal))
                    },
                    people = data.people.map { person ->
                        person.copy(
                            profileImagePath = mappedCoverPath(person.profileImagePath, payload.mediaPaths, mediaFinal),
                            coverImagePath = mappedCoverPath(person.coverImagePath, payload.mediaPaths, mediaFinal)
                        )
                    }
                )

                // Kuruculardan biri hata verirse önce açılmış Room bağlantıları da kapanır.
                val opened = mutableListOf<RoomDatabase>()
                try {
                    val main = Room.databaseBuilder(context, AppDatabase::class.java, dbNames[0])
                        .addMigrations(
                            AppDatabase.MIGRATION_5_6,
                            AppDatabase.MIGRATION_6_7,
                            AppDatabase.MIGRATION_7_8
                        )
                        .build().also(opened::add)
                    val moodDb = Room.databaseBuilder(context, JournalMoodDatabase::class.java, dbNames[1])
                        .build().also(opened::add)
                    val draftDb = Room.databaseBuilder(context, JournalDraftDatabase::class.java, dbNames[2])
                        .build().also(opened::add)
                    val featuredDb = Room.databaseBuilder(context, YearFeaturedMemoryDatabase::class.java, dbNames[3])
                        .build().also(opened::add)
                    main.withTransaction {
                        // Yeni ve boş veritabanı: asla DELETE veya REPLACE ile eski veriler değiştirilmez.
                        val dao = main.backupRestoreDao()
                        dao.insertSeasons(mappedData.seasons)
                        dao.insertPeople(mappedData.people)
                        dao.insertEpisodes(mappedData.episodes)
                        dao.insertJournals(mappedData.journals)
                        dao.insertMentions(mappedData.mentions)
                        dao.insertSpecialMemories(mappedData.specialMemories)
                    }
                    moodDb.withTransaction { moodDb.moodDao().insertForCloudRestore(moods) }
                    draftDb.withTransaction { drafts.forEach { draftDb.journalDraftDao().upsert(it) } }
                    featuredDb.withTransaction { featured.forEach { featuredDb.dao().insert(it) } }
                    verifyDatabase(main, listOf(
                        "journal_entries" to mappedData.journals.size,
                        "people" to mappedData.people.size,
                        "life_seasons" to mappedData.seasons.size,
                        "life_episodes" to mappedData.episodes.size,
                        "journal_person_mentions" to mappedData.mentions.size,
                        "person_special_memories" to mappedData.specialMemories.size
                    ))
                    verifyDatabase(moodDb, listOf("journal_mood_records" to moods.size))
                    verifyDatabase(draftDb, listOf("journal_drafts" to drafts.size))
                    verifyDatabase(featuredDb, listOf("year_featured_memories" to featured.size))
                } finally {
                    // Bütün veritabanı bağlantıları marker yazılmadan önce kapatılır.
                    opened.asReversed().forEach { runCatching { it.close() } }
                }
                authorizedUid(uid)
                check(mediaPending.renameTo(mediaFinal)) { "Görseller tamamlanan alana alınamadı." }
                val result = AccountShadowCopyInfo(
                    preparedAtMillis = System.currentTimeMillis(),
                    journalCount = data.journals.size,
                    personCount = data.people.size,
                    seasonCount = data.seasons.size,
                    episodeCount = data.episodes.size,
                    moodCount = moods.size,
                    draftCount = drafts.size,
                    featuredCount = featured.size,
                    mediaCount = payload.mediaPaths.size
                )
                // İşlem ortada kesilirse marker oluşmaz: yarım kopya aktif sayılmaz.
                writeMarker(uid, result)
                result
            }
        }
    }

    private fun authorizedUid(expected: String? = null): String {
        val uid = auth.currentUser?.uid
        check(AccountMigrationRules.canPrepareSnapshot(accessStore.ownerUidOrNull(), uid)) {
            "Yerel arşivin bağlı olduğu Firebase hesabıyla giriş yapmalısın."
        }
        if (expected != null) check(expected == uid) { "İşlem sırasında hesap değişti." }
        return requireNotNull(uid)
    }

    /** Dosya yolu taşınabilir değilse durdurur; sessizce fotoğraf kaybettirmez. */
    private fun mappedCoverPath(path: String?, mediaPaths: Set<String>, dest: File): String? {
        if (path.isNullOrBlank()) return path
        val appFiles = context.filesDir.canonicalFile
        val input = File(path).canonicalFile
        check(input.path.startsWith(appFiles.path + File.separator)) {
            "Harici görsel URI'si veya uygulama dışı yol bulundu; görsel taşınmadan devam edilemez."
        }
        val relative = input.path.removePrefix(appFiles.path + File.separator).replace(File.separatorChar, '/')
        val name = "media/$relative"
        check(name in mediaPaths) { "Kayıtta referans verilen görsel hazırlık kopyasında yok: $relative" }
        return File(dest, relative).absolutePath
    }

    private fun verifyCrossDatabaseReferences(
        data: BackupData,
        moods: List<JournalMoodRecord>,
        drafts: List<JournalDraftEntity>,
        featured: List<YearFeaturedMemoryEntity>
    ) {
        val ids = data.journals.map { it.id }.toSet()
        check(moods.map { it.journalId }.distinct().size == moods.size && moods.all { it.journalId in ids }) {
            "Ruh hali kayıtlarında çakışma veya eksik anı bağlantısı var."
        }
        check(drafts.map { it.draftKey }.distinct().size == drafts.size) { "Taslak kimlikleri yineleniyor." }
        check(featured.map { it.journalId }.distinct().size == featured.size && featured.all { it.journalId in ids }) {
            "Öne çıkan anılar geçersiz veya yinelenen anı içeriyor."
        }
    }

    private fun copyMedia(payload: AccountShadowInput, mediaPending: File) {
        for (name in payload.mediaPaths) {
            val relative = name.removePrefix("media/")
            val source = File(payload.temporaryMediaRoot, name).canonicalFile
            val dest = File(mediaPending, relative).canonicalFile
            check(dest.path.startsWith(mediaPending.canonicalPath + File.separator)) { "Hedef medya yolu geçersiz." }
            check(dest.parentFile.mkdirs() || dest.parentFile.isDirectory) { "Medya klasörü açılamadı." }
            source.copyTo(dest, overwrite = false)
            check(source.length() == dest.length() && sha256(source) == sha256(dest)) {
                "Medya kopyalama bütünlüğü doğrulanamadı."
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun completedMarker(uid: String): File = File(layout.accountRoot(uid), "shadow_room_v1.complete")

    private fun writeMarker(uid: String, info: AccountShadowCopyInfo) {
        val marker = completedMarker(uid)
        check(!marker.exists()) { "Tamamlanmış kopya zaten bulunuyor." }
        val tmp = File(marker.parentFile, "shadow_room_v1.marker_pending")
        check(!tmp.exists()) { "Önceki tamamlama işlemi yarım kalmış." }
        val data = JSONObject().apply {
            put("version", 1)
            put("accountKey", AccountMigrationRules.folderKey(uid))
            put("time", info.preparedAtMillis)
            put("journals", info.journalCount)
            put("people", info.personCount)
            put("seasons", info.seasonCount)
            put("episodes", info.episodeCount)
            put("moods", info.moodCount)
            put("drafts", info.draftCount)
            put("featured", info.featuredCount)
            put("media", info.mediaCount)
        }
        FileOutputStream(tmp).use { output ->
            output.write(data.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(tmp.renameTo(marker)) { "Tamamlandı işareti kaydedilemedi." }
    }

    private fun markerInfo(uid: String, file: File): AccountShadowCopyInfo {
        val json = JSONObject(file.readText())
        check(json.getInt("version") == 1 && json.getString("accountKey") == AccountMigrationRules.folderKey(uid)) {
            "Kopya başka hesaba ait veya desteklenmeyen bir sürümde."
        }
        return AccountShadowCopyInfo(
            preparedAtMillis = json.getLong("time"), journalCount = json.getInt("journals"),
            personCount = json.getInt("people"), seasonCount = json.getInt("seasons"),
            episodeCount = json.getInt("episodes"), moodCount = json.getInt("moods"),
            draftCount = json.getInt("drafts"), featuredCount = json.getInt("featured"),
            mediaCount = json.getInt("media")
        )
    }

    /** Mevcut marker ile SQLite verilerini tekrar kontrol eder. */
    private fun verifyPreparedDatabases(uid: String, info: AccountShadowCopyInfo) {
        val names = layout.plannedDatabaseNames(uid)
        check(names.all { context.getDatabasePath(it).isFile }) { "Hazırlanan veritabanlarından biri eksik." }
        val opened = mutableListOf<RoomDatabase>()
        try {
            val main = Room.databaseBuilder(context, AppDatabase::class.java, names[0])
                .addMigrations(
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8
                )
                .build().also(opened::add)
            val mood = Room.databaseBuilder(context, JournalMoodDatabase::class.java, names[1])
                .build().also(opened::add)
            val draft = Room.databaseBuilder(context, JournalDraftDatabase::class.java, names[2])
                .build().also(opened::add)
            val featured = Room.databaseBuilder(context, YearFeaturedMemoryDatabase::class.java, names[3])
                .build().also(opened::add)
            verifyDatabase(main, listOf(
                "journal_entries" to info.journalCount, "people" to info.personCount,
                "life_seasons" to info.seasonCount, "life_episodes" to info.episodeCount
            ))
            verifyDatabase(mood, listOf("journal_mood_records" to info.moodCount))
            verifyDatabase(draft, listOf("journal_drafts" to info.draftCount))
            verifyDatabase(featured, listOf("year_featured_memories" to info.featuredCount))
            val mediaRoot = File(layout.accountRoot(uid), "media")
            val mediaCount = if (mediaRoot.isDirectory) mediaRoot.walkTopDown().count { it.isFile } else 0
            check(mediaCount == info.mediaCount) { "Bazı taşınan görseller eksik." }
        } finally {
            opened.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private fun verifyDatabase(database: RoomDatabase, expected: List<Pair<String, Int>>) {
        val sql = database.openHelper.readableDatabase
        sql.query("PRAGMA integrity_check").use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "SQLite bütünlük kontrolü başarısız." }
        }
        sql.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "Veritabanında geçersiz yabancı anahtar var." }
        }
        for ((table, count) in expected) {
            // Tablo adları yalnızca bu sınıfta sabit kodlanmıştır; kullanıcı girdisi değildir.
            sql.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == count) {
                    "$table için aktarım sayısı beklenen değerde değil."
                }
            }
        }
    }
}
