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
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.draft.JournalDraftDatabase
import com.loc.benimdunyam.data.local.draft.JournalDraftStore
import com.loc.benimdunyam.data.local.mood.JournalMoodDatabase
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.repository.BackupRepository
import com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryDatabase
import com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Ekranda gösterilecek değerlendirme; günlük içerikleri veya UID taşımaz. */
data class AccountPromotionStatus(
    val canProceed: Boolean,
    val message: String,
    val isPromoted: Boolean = false
)

/**
 * Faz 2E.6: Hazırlanan dört Room kopyasını aktif çalışma alanı yapmadan önce
 * KAYNAK, ŞİFRELİ HAZIRLIK ve HEDEF verilerini karşılaştırır.
 *
 * Bu sınıf hiçbir anıyı silmez veya mevcut veritabanlarını taşımaz.
 * Aktivasyon yalnızca yeniden başlatma için bir seçim kaydı yazar.
 * Güncelliği kanıtlanamıyorsa güvenli varsayılan: işlem durdurulur.
 *
 * Sınır: Dört ayrı Room + medya arasında sistem genelinde atomik snapshot yoktur.
 * Yazmaları koordine eden işlemler BackupWriteCoordinator tarafından durdurulur;
 * koordinatör dışındaki yazarların da üretim öncesi denetlenmesi gerekir.
 */
@Singleton
class AccountPromotionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val accessStore: LocalArchiveAccessStore,
    private val workspace: ActiveWorkspaceSession,
    private val layout: AccountStorageLayout,
    private val shadow: AccountShadowDatabaseRepository,
    private val snapshot: AccountMigrationSnapshotRepository,
    private val validator: BackupValidator,
    private val source: BackupRepository,
    private val moods: JournalMoodRepository,
    private val drafts: JournalDraftStore,
    private val featured: YearFeaturedMemoryRepository,
    private val coordinator: BackupWriteCoordinator
) {
    private val gson = Gson()
    private val mediaFolders = listOf(
        "benim_dunyam_journal_photos", "benim_dunyam_journal_photo_drafts", "seasons", "people"
    )

    /** Sadece inceler. Veritabanı seçimini veya kullanıcı kayıtlarını değiştirmez. */
    suspend fun inspect(): AccountPromotionStatus = withContext(Dispatchers.IO) {
        val uid = requireOwner()
        when {
            workspace.mode == WorkspaceMode.LEGACY && workspace.isReadyFor(uid) -> {
                shadow.inspect() ?: return@withContext AccountPromotionStatus(
                    false, "Hesaba özel tamamlanmış Room kopyası bulunmuyor."
                )
                checkSourceAndShadow(uid)
                AccountPromotionStatus(true,
                    "Kaynak arşiv, şifreli hazırlık ve hesaba özel dört Room kopyası eşleşiyor. " +
                            "Geçiş yalnızca açık onayla yapılabilir.")
            }
            workspace.mode == WorkspaceMode.ACCOUNT && workspace.isPromotedLegacy(uid) -> {
                shadow.inspect() ?: error("Aktif kopyanın tamamlama işareti bulunamadı.")
                checkShadowMatchesSnapshot(uid)
                AccountPromotionStatus(true,
                    "Aktif arşiv, taşıma anındaki kopyayla hâlâ aynı. " +
                            "İstersen eski arşivi yeniden seçebilirsin.", isPromoted = true)
            }
            else -> AccountPromotionStatus(false, "Bu çalışma alanında eski arşiv geçişi uygulanamaz.")
        }
    }

    /** Onay butonundan çağrılır. Kontroller yalnızca önceki önizlemeye dayanmaz, YENİDEN yapılır. */
    suspend fun activateWithConsent() = coordinator.withExclusiveRestore {
        withContext(Dispatchers.IO) {
            val uid = requireOwner()
            check(workspace.mode == WorkspaceMode.LEGACY && workspace.isReadyFor(uid)) {
                "Eski arşiv aktif değil. Başka bir hesap veya yeniden başlatma gerekiyor."
            }
            check(shadow.inspect() != null) { "Tamamlanmış Room kopyası bulunamadı." }
            checkSourceAndShadow(uid)
            requireOwner(uid)
            workspace.selectPreparedLegacy(uid)
            // Hilt'in eski Room bağlantıları yalnızca yeniden başlatmada değişir.
            // Bu işlemden sonra eski bağlantıya yeni veri yazılmamalıdır.
            coordinator.freezeWritesUntilRestart()
        }
    }

    /** Geri dönüşte YENİ hesaba özel alan değişmişse eski kopyaya sessizce dönmeyiz. */
    suspend fun returnToLegacyWithConsent() = coordinator.withExclusiveRestore {
        withContext(Dispatchers.IO) {
            val uid = requireOwner()
            check(workspace.mode == WorkspaceMode.ACCOUNT && workspace.isPromotedLegacy(uid)) {
                "Bu hesap hazırlanan eski arşivden etkinleştirilmemiş."
            }
            checkShadowMatchesSnapshot(uid)
            requireOwner(uid)
            workspace.selectLegacy(uid)
            coordinator.freezeWritesUntilRestart()
        }
    }

    private fun requireOwner(expected: String? = null): String {
        val uid = auth.currentUser?.uid
        check(uid != null && accessStore.ownerUidOrNull() == uid) {
            "Yerel arşivin sahibi olan Firebase hesabıyla giriş yapılmalı."
        }
        if (expected != null) check(uid == expected) { "İşlem sırasında Firebase hesabı değişti." }
        return uid
    }

    /** Eski arşiv, hazırlıktan sonra düzenlendiyse kopya GÜNCEL DEĞİLDİR. */
    private suspend fun checkSourceAndShadow(uid: String) {
        snapshot.useVerifiedPayload(uid) { payload ->
            val expected = requireNotNull(payload.jsonSections["room/main.json"]).toString(Charsets.UTF_8)
            val actual = gson.toJson(source.createBackupData())
            check(ArchiveContentParity.equivalent(expected, actual, isMainBackup = true)) {
                "Taşıma hazırlığından sonra anılar veya ilişkiler değişmiş. Geçiş kapalı; eski veriler korunuyor."
            }
            compare("ruh halleri", payload.jsonSections.getValue("room/moods.json"), gson.toJson(moods.observeAll().first()))
            compare("taslaklar", payload.jsonSections.getValue("room/drafts.json"), gson.toJson(drafts.observeAll().first()))
            compare("öne çıkan anılar", payload.jsonSections.getValue("room/featured.json"), gson.toJson(featured.observeAll().first()))
            compareMedia(context.filesDir, payload)
            checkShadowContents(uid, payload)
        }
    }

    /** Hem aktivasyon hem geri alma için HEDEF içerik ve fotoğraf doğrulaması. */
    private suspend fun checkShadowMatchesSnapshot(uid: String) {
        snapshot.useVerifiedPayload(uid) { payload ->
            checkShadowContents(uid, payload)
        }
    }

    private fun compare(name: String, reference: ByteArray, actual: String) {
        check(ArchiveContentParity.equivalent(reference.toString(Charsets.UTF_8), actual)) {
            "${name.replaceFirstChar { it.uppercase() }} değişmiş. Otomatik geçiş engellendi."
        }
    }

    private suspend fun checkShadowContents(uid: String, payload: AccountShadowInput) {
        val names = layout.plannedDatabaseNames(uid)
        check(names.all { context.getDatabasePath(it).isFile }) {
            "Hesaba özel veritabanlarından biri eksik; yeni boş veritabanı oluşturulamaz."
        }
        val opened = mutableListOf<RoomDatabase>()
        try {
            val main = Room.databaseBuilder(context, AppDatabase::class.java, names[0])
                // Faz 2F.2'de ana veritabanı 8. sürüme geçti.
                // Hazırlanmış 7. sürüm Room kopyasını doğrulayabilmek için
                // senkronizasyon tablosunu ekleyen migration da kayıtlı olmalıdır.
                .addMigrations(
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8,
                    AppDatabase.MIGRATION_8_9
                )
                .build().also(opened::add)
            val moodDb = Room.databaseBuilder(context, JournalMoodDatabase::class.java, names[1])
                .build().also(opened::add)
            val draftDb = Room.databaseBuilder(context, JournalDraftDatabase::class.java, names[2])
                .build().also(opened::add)
            val featuredDb = Room.databaseBuilder(context, YearFeaturedMemoryDatabase::class.java, names[3])
                .build().also(opened::add)

            val original = validator.parseAndValidate(payload.jsonSections.getValue("room/main.json").toString(Charsets.UTF_8))
            val mediaDest = File(layout.accountRoot(uid), "media")
            val mapped = original.copy(
                seasons = original.seasons.map { it.copy(coverImagePath = remap(it.coverImagePath, payload, mediaDest)) },
                people = original.people.map { it.copy(
                    profileImagePath = remap(it.profileImagePath, payload, mediaDest),
                    coverImagePath = remap(it.coverImagePath, payload, mediaDest)
                ) }
            )
            val result = main.withTransaction {
                BackupData(
                    version = 2, exportTimestamp = 0L,
                    journals = main.journalDao().getAllJournalEntities(),
                    people = main.personDao().getAllPersonEntities(),
                    mentions = main.journalPersonMentionDao().getAllMentionEntities(),
                    seasons = main.lifeSeasonDao().getAllSeasonEntities(),
                    episodes = main.lifeEpisodeDao().getAllEpisodeEntities(),
                    specialMemories = main.personSpecialMemoryDao().getAllSpecialMemoryEntities()
                )
            }
            check(ArchiveContentParity.equivalent(
                gson.toJson(mapped), gson.toJson(result), isMainBackup = true
            )) { "Hazırlanan ana Room'un içeriği şifreli kopyayla farklı; işlem durduruldu." }
            compare("hedef ruh halleri", payload.jsonSections.getValue("room/moods.json"), gson.toJson(moodDb.moodDao().observeAll().first()))
            compare("hedef taslaklar", payload.jsonSections.getValue("room/drafts.json"), gson.toJson(draftDb.journalDraftDao().observeAll().first()))
            compare("hedef öne çıkan anılar", payload.jsonSections.getValue("room/featured.json"), gson.toJson(featuredDb.dao().getAll()))
            compareMedia(mediaDest, payload)
        } finally {
            opened.asReversed().forEach { runCatching { it.close() } }
        }
    }

    /** Kapak görselleri hesabın medya köküne çevrilir; dış URL'ler taşınmaz. */
    private fun remap(path: String?, payload: AccountShadowInput, target: File): String? {
        if (path.isNullOrBlank()) return path
        val root = context.filesDir.canonicalFile
        val original = File(path).canonicalFile
        check(original.path.startsWith(root.path + File.separator)) { "Uygulama dışı kapak görseli bulunuyor." }
        val relative = original.path.removePrefix(root.path + File.separator).replace(File.separatorChar, '/')
        check("media/$relative" in payload.mediaPaths) { "Kapak görseli kopyada bulunmuyor." }
        return File(target, relative).absolutePath
    }

    /** Arşivde olmayan ek fotoğraflar ve bayt farkları dahil tüm medyayı denetler. */
    private fun compareMedia(root: File, payload: AccountShadowInput) {
        val actualPaths = mutableSetOf<String>()
        for (folder in mediaFolders) {
            val sub = File(root, folder)
            if (!sub.exists()) continue
            check(sub.isDirectory) { "Beklenen görsel klasörü dosya olarak bulundu." }
            sub.walkTopDown().filter { it.isFile }.forEach { file ->
                val canonical = file.canonicalFile
                check(canonical.path.startsWith(sub.canonicalPath + File.separator)) {
                    "Medya yolu güvenli değil."
                }
                val rel = canonical.path.removePrefix(root.canonicalPath + File.separator).replace(File.separatorChar, '/')
                val key = "media/$rel"
                check(actualPaths.add(key)) { "Yinelenen medya dosyası bulundu." }
                check(key in payload.mediaPaths) { "Hazırlıktan sonra yeni medya dosyası eklenmiş." }
                val archived = File(payload.temporaryMediaRoot, key)
                check(archived.isFile && archived.length() == file.length() && sha256(archived).contentEquals(sha256(file))) {
                    "Fotoğraf içeriği değişmiş veya eksik. Eski veriler korunuyor."
                }
            }
        }
        check(actualPaths == payload.mediaPaths) { "Arşivde bulunan bazı fotoğraflar yerel alanda eksik." }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest()
    }
}
