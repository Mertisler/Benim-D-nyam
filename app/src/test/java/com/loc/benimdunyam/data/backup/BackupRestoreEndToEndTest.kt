package com.loc.benimdunyam.data.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.data.repository.BackupRepositoryImpl
import com.loc.benimdunyam.domain.repository.BackupRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

/**
 * .bdbackup akışının uçtan uca testi: "A cihazı"ndaki gerçek Room
 * veritabanı, ruh hali veritabanı ve fotoğraflardan şifreli yedek alınır,
 * "B cihazı"nda (farklı veritabanları, farklı medya klasörü) açılıp geri yüklenir.
 *
 * Uygulamadaki sıra birebir izlenir:
 *   EncryptedBackupManager (BackupArchiveManager + BackupCryptoManager)
 *   → BackupCryptoManager.decryptArchive
 *   → BackupArchiveReader.readAndPrepare
 *   → BackupDryRunVerifier.verify
 *   → BackupRestoreManager.restore
 *
 * Kapsam dışı: SAF belge seçimi (Uri / BackupDocumentStore), güvenlik
 * yedeği adımı ve .bdmedia (günlük fotoğrafları).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRestoreEndToEndTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val validator = BackupValidator()
    private val password = "uzun-ve-guclu-parola-42"

    private lateinit var deviceA: Device
    private lateinit var deviceB: Device

    /** Bir cihazın Room'ları, medya kökü ve geçici yedek alanı. */
    private inner class Device(name: String) {
        val mediaRoot: File = tmp.newFolder("$name-files")
        val stagingRoot: File = tmp.newFolder("$name-staging")
        val workspace: ActiveWorkspaceSession = mockk {
            every { mediaRoot() } returns mediaRoot
            every { backupStagingRoot() } returns stagingRoot
            every { databaseNames() } returns
                    listOf("$name-main.db", "$name-moods.db", "$name-drafts.db", "$name-featured.db")
        }
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = BackupRepositoryImpl(db, validator)
        val moods = JournalMoodRepository(context, workspace, BackupWriteCoordinator())
        val crypto = BackupCryptoManager(context, workspace)
        val encryptedBackups = EncryptedBackupManager(
            BackupArchiveManager(context, workspace, repository, validator, moods), crypto
        )
        val reader = BackupArchiveReader(context, workspace, validator)
        val restorer = BackupRestoreManager(context, workspace, repository, moods)

        fun snapshot(): BackupData = runBlocking { repository.createBackupData() }

        fun moodSnapshot(): List<JournalMoodRecord> = runBlocking { moods.snapshotForBackup() }

        fun image(folder: String, name: String, bytes: ByteArray): File =
            File(mediaRoot, folder).apply { mkdirs() }.resolve(name).apply { writeBytes(bytes) }
    }

    @Before
    fun setUp() {
        deviceA = Device("a")
        deviceB = Device("b")
    }

    @After
    fun tearDown() {
        deviceA.db.close()
        deviceB.db.close()
    }

    // ------------------------------------------------------------------
    // Test verisi
    // ------------------------------------------------------------------

    private val seasonCover = Random(1).nextBytes(20_000)
    private val ayseProfile = Random(2).nextBytes(150_000)
    private val ayseCover = Random(3).nextBytes(7_000)

    private val aMoods = listOf(
        JournalMoodRecord("j1", "HAPPY", 2_150),
        JournalMoodRecord("j2", "EMOTIONAL", 3_050)
    )
    private val bOldMoods = listOf(JournalMoodRecord("eski-ani", "ANGRY", 7))

    /** A cihazında gerçekçi bir arşiv: ilişkiler, fotoğraflar, ruh halleri, özel karakterler. */
    private fun seedDeviceA() = runBlocking {
        val cover = deviceA.image("seasons", "season_cover.jpg", seasonCover)
        val profile = deviceA.image("people/p1", "profile.png", ayseProfile)
        val personCover = deviceA.image("people/p1", "cover.webp", ayseCover)

        val dao = deviceA.db.backupRestoreDao()
        dao.insertSeasons(
            listOf(
                LifeSeasonEntity("s1", "Üniversite", "Ankara yılları", 1_000, 5_000, "#FFAA00", cover.path, 1_000),
                LifeSeasonEntity("s2", "İş hayatı", "", 6_000, null, "#0055FF", null, 6_000)
            )
        )
        dao.insertPeople(
            listOf(
                PersonEntity("p1", "Ayşe Çağlar", "Çocukluk arkadaşı", 1_500, 1_600, profile.path, personCover.path),
                PersonEntity("p2", "Mehmet", null, 1_700, 1_700)
            )
        )
        dao.insertEpisodes(listOf(LifeEpisodeEntity("e1", "s1", "1. sınıf", "Yurt", 0, 1_000, 2_000, 1_000)))
        dao.insertJournals(
            listOf(
                JournalEntryEntity("j1", "İlk gün", "@Ayşe ile tanıştım 😊\nYeni satır, \"tırnak\".", 2_000, 2_000, 2_100, "s1", "e1"),
                JournalEntryEntity("j2", "Serbest", "Yağmur vardı.", 3_000, 3_000, 3_000),
                JournalEntryEntity("j3", "Uzun anı", "ğüşıöç ".repeat(5_000), 4_000, 4_000, 4_000, "s2", null)
            )
        )
        dao.insertMentions(
            listOf(
                JournalPersonMentionEntity("j1", "p1", 0, 0, 5),
                JournalPersonMentionEntity("j2", "p1", 0, null, null),
                JournalPersonMentionEntity("j2", "p2", 1, 10, 16)
            )
        )
        dao.insertSpecialMemories(listOf(PersonSpecialMemoryEntity("p1", "j1", "ilk_tanisma", "Unutulmaz", 2_200, 2_200)))

        // j3'ün ruh hali yok; "silinmis" günlüğü artık olmayan yetim bir kayıt.
        deviceA.moods.replaceAllForVerifiedRestore(aMoods + JournalMoodRecord("silinmis", "SAD", 9_000))
    }

    /** B cihazında, geri yüklemeyle tamamen değiştirilmesi gereken eski kayıtlar. */
    private fun seedDeviceB() = runBlocking {
        val dao = deviceB.db.backupRestoreDao()
        dao.insertPeople(listOf(PersonEntity("eski-kisi", "Eski", null, 1, 1)))
        dao.insertJournals(listOf(JournalEntryEntity("eski-ani", "Eski", "B'deki eski anı", 1, 1, 1)))
        deviceB.moods.replaceAllForVerifiedRestore(bOldMoods)
    }

    private fun createBackupOnA(): File = runBlocking {
        deviceA.encryptedBackups.createEncryptedBackup(password.toCharArray())
    }

    /** Uygulamadaki geri yükleme sırası (güvenlik yedeği hariç). */
    private fun restoreOnB(backup: File, pass: String = password): RestoreSummary = runBlocking {
        val zip = deviceB.crypto.decryptArchive(backup, pass.toCharArray())
        try {
            deviceB.reader.readAndPrepare(zip).use { prepared ->
                BackupDryRunVerifier(context).verify(prepared)
                deviceB.restorer.restore(prepared)
            }
        } finally {
            zip.delete()
        }
    }

    /** Fotoğraf yolları cihaza özgü olduğu için karşılaştırmada çıkarılır. */
    private fun BackupData.withoutImagePaths(): BackupData = copy(
        exportTimestamp = 0,
        journals = journals.sortedBy { it.id },
        people = people.map { it.copy(profileImagePath = null, coverImagePath = null) }.sortedBy { it.id },
        mentions = mentions.sortedWith(compareBy({ it.journalId }, { it.personId })),
        seasons = seasons.map { it.copy(coverImagePath = null) }.sortedBy { it.id },
        episodes = episodes.sortedBy { it.id },
        specialMemories = specialMemories.sortedWith(compareBy({ it.journalId }, { it.personId }))
    )

    /** A'nın yedeğini çözer, ZIP girişlerini dönüştürüp şifresiz ZIP olarak yeniden paketler. */
    private fun rewriteArchive(
        backup: File,
        transform: (MutableMap<String, ByteArray>) -> Unit
    ): File = runBlocking {
        val zip = deviceA.crypto.decryptArchive(backup, password.toCharArray())
        val entries = linkedMapOf<String, ByteArray>()
        ZipFile(zip).use { z ->
            z.entries().asSequence().forEach { e -> entries[e.name] = z.getInputStream(e).readBytes() }
        }
        zip.delete()
        transform(entries)
        tmp.newFile("yeniden_${System.nanoTime()}.zip").apply {
            ZipOutputStream(outputStream()).use { out ->
                entries.forEach { (name, bytes) ->
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }

    private fun prepareOnB(zip: File): PreparedBackup = runBlocking { deviceB.reader.readAndPrepare(zip) }

    // ------------------------------------------------------------------
    // Temel akış
    // ------------------------------------------------------------------

    @Test
    fun yedekBaskaCihazdaBirebirGeriYuklenir() {
        seedDeviceA()
        seedDeviceB()

        val backup = createBackupOnA()
        val summary = restoreOnB(backup)

        val original = deviceA.snapshot()
        val restored = deviceB.snapshot()

        // Altı tablonun tüm alanları (fotoğraf yolları hariç) aynı.
        assertEquals(original.withoutImagePaths(), restored.withoutImagePaths())

        // B'deki eski kayıtlar tamamen değiştirildi.
        assertTrue(restored.journals.none { it.id == "eski-ani" })
        assertTrue(restored.people.none { it.id == "eski-kisi" })

        assertEquals(RestoreSummary(3, 2, 2, 1, 3, 1, 3, moodCount = 2), summary)
    }

    @Test
    fun ruhHalleriYedeklenirVeGeriYuklenir() {
        seedDeviceA()
        seedDeviceB()

        restoreOnB(createBackupOnA())

        // Ruh halleri birebir (tarih dahil) geldi; yetim kayıt yedeğe girmedi;
        // B'nin eski ruh halleri tamamen değiştirildi.
        assertEquals(aMoods, deviceB.moodSnapshot())
    }

    @Test
    fun fotograflarBaytBaytVeDogruKlasoreGeriYuklenir() {
        seedDeviceA()
        restoreOnB(createBackupOnA())

        val restored = deviceB.snapshot()
        val season = restored.seasons.single { it.id == "s1" }
        val ayse = restored.people.single { it.id == "p1" }

        val coverFile = File(requireNotNull(season.coverImagePath))
        val profileFile = File(requireNotNull(ayse.profileImagePath))
        val personCoverFile = File(requireNotNull(ayse.coverImagePath))

        assertArrayEquals(seasonCover, coverFile.readBytes())
        assertArrayEquals(ayseProfile, profileFile.readBytes())
        assertArrayEquals(ayseCover, personCoverFile.readBytes())

        // Yollar A'yı değil, B'nin kendi medya klasörünü göstermeli.
        val bRoot = deviceB.mediaRoot.canonicalFile
        assertEquals(File(bRoot, "seasons"), coverFile.canonicalFile.parentFile)
        assertEquals(File(bRoot, "people/p1"), profileFile.canonicalFile.parentFile)
        assertTrue(profileFile.name.endsWith(".png"))
        assertTrue(personCoverFile.name.endsWith(".webp"))

        // Fotoğrafı olmayan kayıtlar boş kalmalı.
        assertNull(restored.seasons.single { it.id == "s2" }.coverImagePath)
        assertNull(restored.people.single { it.id == "p2" }.profileImagePath)
    }

    @Test
    fun yedekAlmaSifresizGeciciDosyaBirakmaz() {
        seedDeviceA()
        val backup = createBackupOnA()

        // Staging klasöründe yalnızca şifreli .bdbackup kalmalı; şifresiz ZIP silinmiş olmalı.
        val leftovers = deviceA.stagingRoot.listFiles().orEmpty().map { it.name }
        assertEquals(listOf(backup.name), leftovers)
        assertTrue(backup.name.endsWith(".bdbackup"))

        // Şifreli dosyada günlük metni ve dosya adları düz olarak görünmemeli.
        val raw = String(backup.readBytes(), Charsets.ISO_8859_1)
        assertTrue(!raw.contains("Yağmur") && !raw.contains("backup.json") && !raw.contains("moods.json"))
    }

    @Test
    fun yanlisParolaHedefCihazaDokunmaz() {
        seedDeviceA()
        seedDeviceB()
        val before = deviceB.snapshot().withoutImagePaths()

        assertFailsWith<IOException> { restoreOnB(createBackupOnA(), "yanlis-parola-0000") }

        assertEquals(before, deviceB.snapshot().withoutImagePaths())
        assertEquals(bOldMoods, deviceB.moodSnapshot())
        assertTrue(deviceB.mediaRoot.walkTopDown().none { it.isFile })
    }

    @Test
    fun bozukYedekHedefCihazaDokunmaz() {
        seedDeviceA()
        seedDeviceB()
        val before = deviceB.snapshot().withoutImagePaths()

        val backup = createBackupOnA()
        val bytes = backup.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x40).toByte()
        backup.writeBytes(bytes)

        assertFailsWith<IOException> { restoreOnB(backup) }

        assertEquals(before, deviceB.snapshot().withoutImagePaths())
        assertEquals(bOldMoods, deviceB.moodSnapshot())
        assertTrue(deviceB.mediaRoot.walkTopDown().none { it.isFile })
    }

    @Test
    fun yedektenSonraFotografDegisirseYineDeYedektekiSurumDoner() {
        seedDeviceA()
        val backup = createBackupOnA()

        // Yedek alındıktan sonra A'daki fotoğraf değişse bile yedek etkilenmemeli.
        val profilePath = requireNotNull(deviceA.snapshot().people.single { it.id == "p1" }.profileImagePath)
        File(profilePath).writeBytes(byteArrayOf(1, 2, 3))

        restoreOnB(backup)
        val restoredProfile = File(requireNotNull(deviceB.snapshot().people.single { it.id == "p1" }.profileImagePath))
        assertArrayEquals(ayseProfile, restoredProfile.readBytes())
        assertNotEquals(profilePath, restoredProfile.path)
    }

    @Test
    fun bosArsivGeriYuklenirseHedefBosalir() {
        // Bilinçli davranış: boş yedek, hedef cihazı boş arşivle değiştirir.
        seedDeviceB()
        val summary = restoreOnB(createBackupOnA())

        assertEquals(RestoreSummary(0, 0, 0, 0, 0, 0, 0, moodCount = 0), summary)
        val restored = deviceB.snapshot()
        assertTrue(restored.journals.isEmpty() && restored.people.isEmpty())
        assertTrue(deviceB.moodSnapshot().isEmpty())
    }

    @Test
    fun uygulamaKlasoruDisindakiFotografYedeklenmez() = runBlocking<Unit> {
        // Room'da uygulama dışını gösteren bir yol varsa yedek sessizce eksik
        // alınmamalı; kullanıcı uyarılmalı.
        val outside = tmp.newFile("galeri.jpg").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        deviceA.db.backupRestoreDao().insertPeople(
            listOf(PersonEntity("p9", "Dışarıdan", null, 1, 1, outside.path, null))
        )

        assertFailsWith<IllegalArgumentException> { createBackupOnA() }
        assertTrue(deviceA.stagingRoot.listFiles().orEmpty().isEmpty())
    }

    // ------------------------------------------------------------------
    // Arşiv sürümleri ve ruh hali bütünlüğü
    // ------------------------------------------------------------------

    @Test
    fun yeniYedekArsivSurum2VeRuhHaliGirisiIcerir() {
        seedDeviceA()
        rewriteArchive(createBackupOnA()) { entries ->
            assertTrue(BackupMoodCodec.ENTRY in entries)
            val manifest = JsonParser
                .parseString(String(entries.getValue("manifest.json"), Charsets.UTF_8)).asJsonObject
            assertEquals(2, manifest.get("archiveVersion").asInt)
            assertTrue(manifest.has("moodsSha256") && manifest.has("moodsSizeBytes"))
        }
    }

    @Test
    fun eskiSurum1YedekHalaGeriYuklenirVeRuhHallerineDokunmaz() {
        seedDeviceA()
        seedDeviceB()

        // Sürüm 1 arşivi: ruh hali girişi ve manifest alanları yok.
        val legacyZip = rewriteArchive(createBackupOnA()) { entries ->
            entries.remove(BackupMoodCodec.ENTRY)
            val manifest = JsonParser
                .parseString(String(entries.getValue("manifest.json"), Charsets.UTF_8)).asJsonObject
            manifest.addProperty("archiveVersion", 1)
            manifest.remove("moodsSizeBytes")
            manifest.remove("moodsSha256")
            entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        }

        val summary = runBlocking {
            prepareOnB(legacyZip).use { prepared ->
                assertNull(prepared.moods)
                deviceB.restorer.restore(prepared)
            }
        }

        assertNull(summary.moodCount)
        assertEquals(3, summary.journalCount)
        // Eski yedek ruh hali taşımadığı için cihazdakiler olduğu gibi kalır.
        assertEquals(bOldMoods, deviceB.moodSnapshot())
    }

    @Test
    fun surum2YedektenRuhHaliGirisiSilinmisseReddedilir() {
        seedDeviceA()
        val zip = rewriteArchive(createBackupOnA()) { it.remove(BackupMoodCodec.ENTRY) }
        assertFailsWith<IOException> { prepareOnB(zip) }
    }

    @Test
    fun ruhHaliVerisiDegistirilmisseReddedilir() {
        seedDeviceA()
        // Manifest aynı kalıyor, ruh hali içeriği değişiyor: özet tutmamalı.
        val zip = rewriteArchive(createBackupOnA()) { entries ->
            entries[BackupMoodCodec.ENTRY] = String(entries.getValue(BackupMoodCodec.ENTRY), Charsets.UTF_8)
                .replace("HAPPY", "ANGRY").toByteArray(Charsets.UTF_8)
        }
        assertFailsWith<IOException> { prepareOnB(zip) }
    }

    @Test
    fun anaVeritabaniBasarisizOlursaRuhHalleriEskiHalineDoner() {
        seedDeviceA()
        seedDeviceB()

        // Ruh halleri yazıldıktan SONRA ana Room işlemi başarısız olsun.
        val failingRepository = mockk<BackupRepository> {
            coEvery { replaceDatabaseWithVerifiedBackup(any()) } throws IllegalStateException("disk dolu")
            // Temizlik adımı mevcut kayıtları okur: B'nin gerçek veritabanına yönlendir.
            coEvery { createBackupData() } coAnswers { deviceB.repository.createBackupData() }
        }
        val failingRestorer = BackupRestoreManager(context, deviceB.workspace, failingRepository, deviceB.moods)

        val zip = runBlocking { deviceB.crypto.decryptArchive(createBackupOnA(), password.toCharArray()) }
        assertFailsWith<IllegalStateException> {
            runBlocking { prepareOnB(zip).use { failingRestorer.restore(it) } }
        }

        // Telafi: B'nin ruh halleri geri yüklemeden önceki haline döndü.
        assertEquals(bOldMoods, deviceB.moodSnapshot())
        // Yarım kalan geri yüklemenin fotoğrafları da temizlendi.
        assertTrue(deviceB.mediaRoot.walkTopDown().none { it.isFile })
    }
}
