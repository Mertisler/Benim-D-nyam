package com.loc.benimdunyam.feature.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.backup.BackupCoverageInspector
import com.loc.benimdunyam.data.backup.JournalMediaArchiveManager
import com.loc.benimdunyam.data.backup.JournalMediaArchivePreview
import com.loc.benimdunyam.data.backup.LocalBackupCoverage
import com.loc.benimdunyam.data.backup.LastBackupVerification
import com.loc.benimdunyam.data.backup.BackupVerificationReceiptStore
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import com.loc.benimdunyam.domain.usecase.backup.BackupPreview
import com.loc.benimdunyam.domain.usecase.backup.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Parolalar, SAF URI'leri ve çözülen arşivler kalıcı durum içine yazılmaz. */
data class BackupCoverageUiState(
    val busy: Boolean = false,
    val local: LocalBackupCoverage? = null,
    val normal: BackupPreview? = null,
    val media: JournalMediaArchivePreview? = null,
    val normalInspectedAt: Long? = null,
    val mediaInspectedAt: Long? = null,
    val history: LastBackupVerification = LastBackupVerification(),
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class BackupCoverageViewModel @Inject constructor(
    private val inspector: BackupCoverageInspector,
    private val normalVerifier: RestoreBackupUseCase,
    private val mediaVerifier: JournalMediaArchiveManager,
    private val session: ActiveWorkspaceSession,
    private val receiptStore: BackupVerificationReceiptStore
) : ViewModel() {
    var ui by mutableStateOf(BackupCoverageUiState())
        private set

    private var lastWorkspaceKey: String? = null

    /** Farklı hesaba geçildiğinde önceki dosya incelemelerinin yanlış kullanılmasını önler. */
    private fun verifiedWorkspaceKey(): String {
        check(!session.needsRestart()) { "Çalışma alanı değişmiş; uygulamayı yeniden aç." }
        return when (session.mode) {
            WorkspaceMode.ACCOUNT -> "account:${session.accountUid ?: error("Hesap bulunamadı") }"
            WorkspaceMode.LEGACY -> "legacy"
            WorkspaceMode.LOCKED -> error("Arşiv kilitli; doğru hesapla giriş yap.")
        }
    }

    private fun resetOnWorkspaceChange(): String {
        val key = verifiedWorkspaceKey()
        if (lastWorkspaceKey != key) {
            lastWorkspaceKey = key
            ui = BackupCoverageUiState(history = receiptStore.load())
        }
        return key
    }

    /** İşlemi başlatmadan kullanıcı çalışma alanını doğrula; kilitli alanda önizleme yok. */
    private fun prepareOperation(): String? {
        return try {
            resetOnWorkspaceChange()
        } catch (e: Exception) {
            ui = BackupCoverageUiState(error = e.localizedMessage ?: "Çalışma alanına erişilemiyor.")
            null
        }
    }

    /** Ayarlar ekranı açıldığında yalnızca geçmiş tarihleri okur; yeni dosya doğrulaması yapmaz. */
    fun onSectionOpened() {
        if (ui.busy) return
        lastWorkspaceKey = null
        prepareOperation()
    }

    fun refreshLocal() {
        if (ui.busy) return
        val key = prepareOperation() ?: return
        ui = ui.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val snapshot = inspector.scanLocal()
                check(verifiedWorkspaceKey() == key) { "Hesap değişti. Yeniden incele." }
                ui = ui.copy(local = snapshot, message = "Cihazdaki kayıtlar salt okunur tarandı.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Başarısız tarama sonrasında eski hesaptan kalan kayıt gösterilmez.
                ui = ui.copy(local = null, normal = null, media = null,
                    normalInspectedAt = null, mediaInspectedAt = null,
                    error = "Kapsam taraması yapılamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}")
            } finally {
                ui = ui.copy(busy = false)
            }
        }
    }

    fun inspectNormal(uri: Uri, password: String) {
        if (ui.busy) return
        val key = prepareOperation() ?: return
        ui = ui.copy(busy = true, normal = null, normalInspectedAt = null,
            error = null, message = null)
        viewModelScope.launch {
            try {
                val result = normalVerifier.inspect(uri, password.toCharArray())
                check(verifiedWorkspaceKey() == key) { "Hesap değişti. Yeniden incele." }
                val checked = System.currentTimeMillis()
                ui = ui.copy(normal = result, normalInspectedAt = checked,
                    message = ".bdbackup şifresi ve arşiv bütünlüğü doğrulandı; hiçbir veri geri yüklenmedi.")
                // Geçmiş kaydı başarısız olsa bile dosya bütünlüğü incelemesi geçerlidir.
                try {
                    val history = withContext(Dispatchers.IO) {
                        receiptStore.saveNormal(result.createdAt, checked)
                        receiptStore.load()
                    }
                    ui = ui.copy(history = history)
                } catch (_: Exception) {
                    ui = ui.copy(message = "Dosya doğrulandı fakat geçmiş tarih kaydı saklanamadı.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui = ui.copy(normal = null, normalInspectedAt = null,
                    error = ".bdbackup doğrulanamadı: ${e.localizedMessage ?: "Dosya geçersiz"}")
            } finally {
                ui = ui.copy(busy = false)
            }
        }
    }

    fun inspectMedia(uri: Uri, password: String) {
        if (ui.busy) return
        val key = prepareOperation() ?: return
        ui = ui.copy(busy = true, media = null, mediaInspectedAt = null,
            error = null, message = null)
        viewModelScope.launch {
            try {
                val result = mediaVerifier.preview(uri, password.toCharArray())
                check(verifiedWorkspaceKey() == key) { "Hesap değişti. Yeniden incele." }
                val checked = System.currentTimeMillis()
                ui = ui.copy(media = result, mediaInspectedAt = checked,
                    message = ".bdmedia şifresi, manifesti ve fotoğraf bütünlüğü doğrulandı; hiçbir fotoğraf eklenmedi.")
                try {
                    val history = withContext(Dispatchers.IO) {
                        receiptStore.saveMedia(result.createdAtMillis, checked)
                        receiptStore.load()
                    }
                    ui = ui.copy(history = history)
                } catch (_: Exception) {
                    ui = ui.copy(message = "Dosya doğrulandı fakat geçmiş tarih kaydı saklanamadı.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui = ui.copy(media = null, mediaInspectedAt = null,
                    error = ".bdmedia doğrulanamadı: ${e.localizedMessage ?: "Dosya geçersiz"}")
            } finally {
                ui = ui.copy(busy = false)
            }
        }
    }

    /** Hesap değişimi / dosya taşıma sonrası eski inceleme sonuçlarını geçersiz kılar. */
    fun clearResults() {
        if (ui.busy) return
        ui = BackupCoverageUiState(history = ui.history)
        lastWorkspaceKey = null
    }
}
