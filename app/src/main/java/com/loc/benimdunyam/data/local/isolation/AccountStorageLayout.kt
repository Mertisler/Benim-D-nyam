package com.loc.benimdunyam.data.local.isolation

import android.content.Context
import com.loc.benimdunyam.domain.model.AccountMigrationRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Her Firebase UID'si için birbirinden bağımsız uygulama-özel çalışma alanını tanımlar.
 *
 * Dosyalar `noBackupFilesDir` altında kalır: sistemin otomatik Android yedeğine
 * dahil edilmez. Bu alanı başka bir telefona taşımak için TASARLANMAMIŞTIR.
 *
 * Faz 2E.5'ten itibaren YENİ hesaplar kendi BOŞ Room dosyalarıyla açılabilir.
 * Faz 2E.4'ün eski arşivden hazırlanmış kopyaları ise otomatik aktif edilmez.
 * Aktif seçim süreç başlangıcında ActiveWorkspaceSession tarafından yapılır.
 */
@Singleton
class AccountStorageLayout @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun accountRoot(uid: String): File = File(
        File(context.noBackupFilesDir, "account_storage_v1"),
        AccountMigrationRules.folderKey(uid)
    )

    fun stagingFolder(uid: String): File = File(accountRoot(uid), "staging")

    fun verifiedSnapshot(uid: String): File = File(stagingFolder(uid), "local_snapshot_v1.enc")

    /** Gelecekte kullanılacak hesap bazlı Room dosya adlarının sözleşmesi. */
    fun plannedDatabaseNames(uid: String): List<String> {
        val prefix = AccountMigrationRules.folderKey(uid).take(24)
        return listOf(
            "${prefix}_benim_dunyam_db",
            "${prefix}_benimdunyam_journal_moods_v1.db",
            "${prefix}_benim_dunyam_journal_drafts.db",
            "${prefix}_benim_dunyam_featured_memories.db"
        )
    }
}
