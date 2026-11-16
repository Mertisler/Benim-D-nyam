package com.loc.benimdunyam.data.local.isolation

import android.content.Context
import com.loc.benimdunyam.core.auth.LocalArchiveAccessStore
import com.loc.benimdunyam.domain.model.CloudLocalArchiveOwnerState
import com.loc.benimdunyam.domain.model.LocalAccountFolderKey
import com.loc.benimdunyam.domain.model.LocalStorageIsolationReport
import com.loc.benimdunyam.domain.model.LocalStoragePart
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hesap bazlı fiziksel ayrımdan ÖNCE yapılan salt-okunur depolama taraması.
 *
 * Veritabanını açmaz veya kapatmaz, WAL dosyalarını kopyalamaz ve hiçbir dosya
 * oluşturup silmez. Bu yüzden çalışan uygulamada risksiz biçimde kullanılabilir.
 * Tarama boyutları işlem sırasında değişebilir; sonuç kesin bir yedek değildir.
 */
@Singleton
class LocalStoragePreflightRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessStore: LocalArchiveAccessStore,
    private val workspace: ActiveWorkspaceSession
) {
    suspend fun inspect(uid: String): LocalStorageIsolationReport = withContext(Dispatchers.IO) {
        require(uid.isNotBlank()) { "Önce Firebase hesabına giriş yapılmalı." }

        // Aynı SharedPreferences sahipliğini kullandığımız için iki çelişkili
        // hesap kimliği kaynağı oluşturmuyoruz.
        val ownership = if (workspace.mode == WorkspaceMode.ACCOUNT && workspace.isReadyFor(uid)) {
            CloudLocalArchiveOwnerState.CURRENT_ACCOUNT
        } else {
            CloudLocalArchiveOwnerState.from(accessStore.ownerUidOrNull(), uid)
        }
        // Yalnızca bu süreçte seçilmiş hesabın dört Room dosyasını incele.
        val databases = listOf(
            "Ana günlük veritabanı", "Ruh halleri", "Anı taslakları", "Öne çıkan anılar"
        ).zip(workspace.databaseNames())
            .map { (title, filename) -> inspectDatabase(title, context.getDatabasePath(filename)) }

        val media = listOf(
            "Anı fotoğrafları" to "benim_dunyam_journal_photos",
            "Taslak fotoğrafları" to "benim_dunyam_journal_photo_drafts",
            "Dönem kapakları" to "seasons",
            "Kişi görselleri" to "people"
        ).map { (name, folder) -> inspectDirectory(name, File(workspace.mediaRoot(), folder)) }

        val notes = buildList {
            if (ownership != CloudLocalArchiveOwnerState.CURRENT_ACCOUNT) {
                add("Yerel arşivin bu hesapla sahiplik eşleşmesi doğrulanmadı. Otomatik taşıma yasak.")
            }
            if (databases.any { it.hasActiveJournal }) {
                add("Aktif SQLite WAL/journal dosyaları bulundu. Açık veritabanı dosyaları elle kopyalanmamalı.")
            }
            if ((databases + media).any { it.scanIncomplete }) {
                add("Bazı klasörler tarama sınırına ulaştı. Dosya kapsamı tam sayılamadı.")
            }
            add("Fotoğraf ve kapak yolları mutlak cihaz yolu içeriyor olabilir; taşınırken tek tek eşlenmelidir.")
            add(".bdbackup biçimi değiştirilmedi. Fotoğraflar için ayrıca doğrulanmış bir aktarım yolu gerekir.")
            add("Bu rapor yalnızca o anda açık çalışma alanını inceler; başka hesapların dosyalarını taramaz.")
        }
        LocalStorageIsolationReport(
            checkedAtMillis = System.currentTimeMillis(),
            ownerStatus = ownership,
            accountFolderCode = LocalAccountFolderKey.forUid(uid),
            parts = databases + media,
            notes = notes
        )
    }

    private fun inspectDatabase(title: String, mainFile: File): LocalStoragePart {
        // WAL ve SHM ana dosyadan ayrı olduğu için görünürlüklerini raporlarız.
        // Bu dosyaları uygulama çalışırken taşımak veriyi bozabilir.
        val wal = File(mainFile.path + "-wal")
        val shm = File(mainFile.path + "-shm")
        val journal = File(mainFile.path + "-journal")
        val group = listOf(mainFile, wal, shm, journal).filter { it.isFile }
        return LocalStoragePart(
            title = title,
            isDatabase = true,
            exists = group.isNotEmpty(),
            fileCount = group.size,
            byteCount = group.fold(0L) { acc, file -> safeSum(acc, file.length()) },
            hasActiveJournal = wal.isFile && wal.length() > 0L || journal.isFile && journal.length() > 0L
        )
    }

    private fun inspectDirectory(title: String, root: File): LocalStoragePart {
        if (!root.exists()) return LocalStoragePart(title, false, false, 0, 0L)
        if (!root.isDirectory) return LocalStoragePart(title, false, true, 0, 0L, scanIncomplete = true)

        // Sembolik bağlantılarla uygulama dizini dışına çıkmayız.
        // Büyük arşivlerde UI'yi kilitlememek için taramaya sınır koyarız.
        val safeRoot = runCatching { root.canonicalFile }.getOrNull()
            ?: return LocalStoragePart(title, false, true, 0, 0L, scanIncomplete = true)
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.add(root to 0)
        var bytes = 0L
        var count = 0
        var incomplete = false
        val maxFiles = 20_000
        val maxDepth = 16
        while (stack.isNotEmpty()) {
            val (folder, depth) = stack.removeLast()
            val children = folder.listFiles()
            if (children == null) { incomplete = true; continue }
            for (child in children) {
                if (count >= maxFiles) { incomplete = true; break }
                val path = runCatching { child.canonicalFile }.getOrNull()
                if (path == null || !path.path.startsWith(safeRoot.path + File.separator)) {
                    incomplete = true
                    continue
                }
                if (child.isDirectory) {
                    if (depth < maxDepth) stack.add(child to depth + 1) else incomplete = true
                } else if (child.isFile) {
                    count++
                    bytes = safeSum(bytes, child.length())
                }
            }
            if (count >= maxFiles) { incomplete = true; break }
        }
        return LocalStoragePart(title, false, true, count, bytes, scanIncomplete = incomplete)
    }

    private fun safeSum(a: Long, b: Long): Long =
        if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}
