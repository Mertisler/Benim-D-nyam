package com.loc.benimdunyam.data.local.isolation

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.core.auth.LocalArchiveAccessStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulama açıldığında seçilen yerel çalışma alanını sabitler.
 *
 * Hilt'in Singleton Room bağlantıları çalışırken hesap değiştirilemez.
 * Bu yüzden seçimi ancak uygulama TAMAMEN kapatılıp yeniden açıldığında uygularız.
 * Varsayılan LEGACY modu eski kullanıcıların veritabanı adlarını ve fotoğraflarını korur.
 */
@Singleton
class ActiveWorkspaceSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val layout: AccountStorageLayout,
    private val legacyOwner: LocalArchiveAccessStore
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Bir süreçte Hilt'e verdiğimiz hesap değişmez; oturum daha sonra değişse bile. */
    val bootUid: String? = firebaseAuth.currentUser?.uid
    private val savedUid: String? = prefs.getString(ACTIVE_UID, null)
    // Hesaba özel KOPYANIN aktivasyonu kaydedilmişse, veritabanlarını açmadan önce
    // tamamlama işaretini ve dört SQLite dosyasını kontrol ederiz. Eksikse kilit.
    private val requestedMode = WorkspaceStartupPolicy.mode(savedUid, bootUid)
    val mode: WorkspaceMode = if (requestedMode == WorkspaceMode.ACCOUNT &&
        isPromotedLegacy(savedUid) && !preparedCopyFilesExist(requireNotNull(savedUid))) {
        WorkspaceMode.LOCKED
    } else requestedMode
    private val _selectionVersion = MutableStateFlow(0)
    /** Seçim değişince navigasyon kapısını yeniden değerlendirir. */
    val selectionVersion: StateFlow<Int> = _selectionVersion.asStateFlow()
    val accountUid: String? = if (mode == WorkspaceMode.ACCOUNT) savedUid else null

    /** Room için kullanılacak adlar yalnızca süreç oluşturulurken seçilir. */
    fun databaseNames(): List<String> = when (mode) {
        WorkspaceMode.LEGACY -> LEGACY_DATABASES
        WorkspaceMode.ACCOUNT -> layout.plannedDatabaseNames(requireNotNull(accountUid))
        // Başka hesabın veritabanını kilitli oturumda yanlışlıkla açma.
        WorkspaceMode.LOCKED -> LOCKED_DATABASES
    }

    /** Dört Room ve medya aynı hesap alanına yönlendirilir. */
    fun mediaRoot(): File = when (mode) {
        WorkspaceMode.LEGACY -> context.filesDir
        WorkspaceMode.ACCOUNT -> File(layout.accountRoot(requireNotNull(accountUid)), "media")
        WorkspaceMode.LOCKED -> File(context.noBackupFilesDir, "locked_workspace_media")
    }

    /** Yedek açma ve temizleme işlemleri başka hesabın geçici ZIP dosyasına dokunamaz. */
    fun backupStagingRoot(): File = when (mode) {
        WorkspaceMode.ACCOUNT -> File(layout.accountRoot(requireNotNull(accountUid)), "backup_staging")
        WorkspaceMode.LEGACY -> File(context.noBackupFilesDir, "backup_staging")
        WorkspaceMode.LOCKED -> File(context.noBackupFilesDir, "locked_backup_staging")
    }

    /** Geçerli süreçte seçili hesapla Firebase oturumunun birbirine uyup uymadığı. */
    fun isReadyFor(uid: String?): Boolean = when (mode) {
        WorkspaceMode.LEGACY -> prefs.getString(ACTIVE_UID, null) == null &&
                legacyOwner.accessFor(uid) == com.loc.benimdunyam.domain.model.LocalArchiveAccessDecision.ALLOWED
        WorkspaceMode.ACCOUNT -> uid != null && uid == accountUid &&
                firebaseAuth.currentUser?.uid == uid && prefs.getString(ACTIVE_UID, null) == uid &&
                (!isPromotedLegacy(uid) || preparedCopyFilesExist(uid))
        WorkspaceMode.LOCKED -> false
    }

    /** Kayıt değiştiyse Hilt nesneleri yeniden kurulmadan anı ekranlarını açmıyoruz. */
    fun needsRestart(): Boolean = prefs.getString(ACTIVE_UID, null) != savedUid ||
            (mode == WorkspaceMode.LOCKED && bootUid != firebaseAuth.currentUser?.uid)

    fun canUseFreshAccount(uid: String?): Boolean =
        firebaseAuth.currentUser?.uid == uid &&
                WorkspaceStartupPolicy.canCreateFresh(legacyOwner.ownerUidOrNull(), uid)

    /**
     * Yalnızca yeni/önceden bu uygulamada oluşturulmuş HESABA ÖZEL BOŞ alanı seçer.
     * Faz 2E.4'te hazırlanan kopyaları burada aktifleştirmek YASAKTIR.
     */
    @Synchronized
    fun selectFreshAccount(uid: String) {
        check(canUseFreshAccount(uid)) { "Bu hesap için yeni alan açılamıyor." }
        val known = prefs.getStringSet(KNOWN_UIDS, emptySet()).orEmpty().toSet()
        val names = layout.plannedDatabaseNames(uid)
        if (uid !in known) {
            val unexpected = names.any { name ->
                val f = context.getDatabasePath(name)
                f.exists() || File(f.path + "-wal").exists() || File(f.path + "-shm").exists()
            }
            check(!unexpected) {
                "Bu hesap için daha önce oluşturulmuş veriler bulundu. Üzerine yazılmadı."
            }
            // Hazır/shadow arşivinin veya yarım kalmış medyanın boş alan diye
            // açılmasını engelle. Hiçbir dosyayı otomatik olarak silmiyoruz.
            val accountRoot = layout.accountRoot(uid)
            check(listOf("shadow_room_v1.complete", "media", "media_preparing", "staging")
                .none { File(accountRoot, it).exists() }) {
                "Bu hesaba ait mevcut veya yarım kalmış hazırlık dosyaları var. Üzerine yazılmadı."
            }
        }
        val editor = prefs.edit()
            .putString(ACTIVE_UID, uid)
            .putStringSet(KNOWN_UIDS, known + uid)
        check(editor.commit()) { "Hesap alanı tercihi kaydedilemedi." }
        _selectionVersion.value += 1
    }

    /** Eski arşive sadece gerçekten o hesaba ait Firebase oturumuyla dönülebilir. */
    @Synchronized
    fun selectLegacy(uid: String) {
        check(firebaseAuth.currentUser?.uid == uid && legacyOwner.ownerUidOrNull() == uid) {
            "Eski arşivin sahibi olan hesapla giriş yapmalısın."
        }
        check(prefs.edit().remove(ACTIVE_UID).commit()) {
            "Eski arşiv seçimi kaydedilemedi."
        }
        _selectionVersion.value += 1
    }

    /** Bu UID daha önce eski arşivden doğrulanmış bir kopyayla etkinleştirilmiş mi? */
    fun isPromotedLegacy(uid: String?): Boolean = uid != null &&
            uid == legacyOwner.ownerUidOrNull() &&
            uid in prefs.getStringSet(PROMOTED_UIDS, emptySet()).orEmpty()

    /**
     * Faz 2E.6: yalnızca kayıtları karşılaştırılmış ve TAMAMLANMIŞ eski arşiv
     * kopyasının seçimini yazar. Kaynak SQLite dosyaları silinmez/değiştirilmez.
     * Hilt bağlantılarının karışmaması için yeniden başlatma ZORUNLUDUR.
     */
    @Synchronized
    fun selectPreparedLegacy(uid: String) {
        check(mode == WorkspaceMode.LEGACY && bootUid == uid &&
                firebaseAuth.currentUser?.uid == uid && legacyOwner.ownerUidOrNull() == uid) {
            "Taşıma için eski arşiv sahibi hesabıyla giriş yapılmalı."
        }
        check(preparedCopyFilesExist(uid)) { "Hesap kopyası eksik veya tamamlanmamış." }
        val knownPromotions = prefs.getStringSet(PROMOTED_UIDS, emptySet()).orEmpty().toSet()
        check(prefs.edit()
            .putStringSet(PROMOTED_UIDS, knownPromotions + uid)
            .putString(ACTIVE_UID, uid)
            .commit()
        ) { "Aktif hesap seçimi kaydedilemedi." }
        _selectionVersion.value += 1
    }

    /** Yanlış/eksik hazırlık dosyası üzerine Room'un boş DB oluşturmasını önler. */
    private fun preparedCopyFilesExist(uid: String): Boolean = runCatching {
        val marker = File(layout.accountRoot(uid), "shadow_room_v1.complete")
        if (!marker.isFile) return@runCatching false
        val json = JSONObject(marker.readText())
        json.getInt("version") == 1 &&
                json.getString("accountKey") ==
                com.loc.benimdunyam.domain.model.AccountMigrationRules.folderKey(uid) &&
                layout.plannedDatabaseNames(uid).all { context.getDatabasePath(it).isFile } &&
                File(layout.accountRoot(uid), "media").isDirectory
    }.getOrDefault(false)

    companion object {
        private const val PREFS_NAME = "benimdunyam_active_workspace_v1"
        private const val ACTIVE_UID = "active_account_uid"
        private const val KNOWN_UIDS = "created_empty_workspaces"
        private const val PROMOTED_UIDS = "verified_legacy_promotions"
        private val LEGACY_DATABASES = listOf(
            "benim_dunyam_db", "benimdunyam_journal_moods_v1.db",
            "benim_dunyam_journal_drafts.db", "benim_dunyam_featured_memories.db"
        )
        private val LOCKED_DATABASES = listOf(
            "benimdunyam_locked_main.db", "benimdunyam_locked_moods.db",
            "benimdunyam_locked_drafts.db", "benimdunyam_locked_featured.db"
        )
    }
}
