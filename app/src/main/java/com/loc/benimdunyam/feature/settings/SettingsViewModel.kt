package com.loc.benimdunyam.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.backup.BackupHealthReport
import com.loc.benimdunyam.data.backup.BackupMaintenanceManager
import com.loc.benimdunyam.data.backup.BackupDatabaseIntegrityVerifier
import com.loc.benimdunyam.data.backup.DatabaseIntegrityReport
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.backup.BackupSelfCheckManager
import com.loc.benimdunyam.data.backup.BackupSelfCheckReport
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.domain.usecase.backup.BackupPreview
import com.loc.benimdunyam.domain.usecase.backup.CreateBackupUseCase
import com.loc.benimdunyam.domain.usecase.backup.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isBiometricEnabled: Boolean = false,
    val restorePreview: BackupPreview? = null,
    val backupHealthReport: BackupHealthReport? = null,
    val databaseIntegrityReport: DatabaseIntegrityReport? = null,
    val isBackupHealthLoading: Boolean = false,
    val isBackupSelfCheckLoading: Boolean = false,
    val backupSelfCheckReport: BackupSelfCheckReport? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val appDatabase: AppDatabase,
    private val backupMaintenanceManager: BackupMaintenanceManager,
    private val databaseIntegrityVerifier: BackupDatabaseIntegrityVerifier,
    private val writeCoordinator: BackupWriteCoordinator,
    private val backupSelfCheckManager: BackupSelfCheckManager
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState())
        private set

    // Önizlemesi doğrulanmış kaynak dosya.
    // Şifresiz ZIP veya parola burada saklanmaz.
    private var selectedRestoreUri: Uri? = null

    // ----------------------------------------
    // BIOMETRIC PREFERENCE
    // ----------------------------------------

    fun loadBiometricPreference(context: Context) {
        val prefs = context.getSharedPreferences(
            "benim_dunyam_prefs",
            Context.MODE_PRIVATE
        )

        uiState = uiState.copy(
            isBiometricEnabled =
                prefs.getBoolean("biometric_lock", false)
        )
    }

    fun setBiometricEnabled(
        context: Context,
        enabled: Boolean
    ) {
        val prefs = context.getSharedPreferences(
            "benim_dunyam_prefs",
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putBoolean("biometric_lock", enabled)
            .apply()

        uiState = uiState.copy(
            isBiometricEnabled = enabled
        )
    }

    // ----------------------------------------
    // CREATE ENCRYPTED BACKUP
    // ----------------------------------------

    fun exportEncryptedBackup(
        destinationUri: Uri,
        password: String
    ) {
        if (uiState.isLoading || uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading) return

        viewModelScope.launch {

            uiState = uiState.copy(
                isLoading = true,
                loadingMessage = "Şifreli yedek oluşturuluyor...",
                errorMessage = null,
                successMessage = null
            )

            try {
                createBackupUseCase(
                    destinationUri = destinationUri,
                    passphrase = password.toCharArray()
                )

                uiState = uiState.copy(
                    successMessage =
                        "Şifreli yedek oluşturuldu ve doğrulandı."
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage =
                        "Yedekleme başarısız: " +
                                (e.localizedMessage ?: "Bilinmeyen hata")
                )

            } finally {
                uiState = uiState.copy(
                    isLoading = false,
                    loadingMessage = null
                )
            }
        }
    }

    // ----------------------------------------
    // PREVIEW ENCRYPTED BACKUP
    // ----------------------------------------

    fun previewRestore(
        sourceUri: Uri,
        password: String
    ) {
        if (uiState.isLoading || uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading) return

        viewModelScope.launch {

            selectedRestoreUri = null

            uiState = uiState.copy(
                isLoading = true,
                loadingMessage = "Yedek doğrulanıyor...",
                errorMessage = null,
                successMessage = null,
                restorePreview = null
            )

            try {
                val preview = restoreBackupUseCase.inspect(
                    sourceUri = sourceUri,
                    passphrase = password.toCharArray()
                )

                selectedRestoreUri = sourceUri

                uiState = uiState.copy(
                    restorePreview = preview
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                selectedRestoreUri = null

                uiState = uiState.copy(
                    errorMessage =
                        "Yedek okunamadı: " +
                                (e.localizedMessage ?: "Geçersiz dosya")
                )

            } finally {
                uiState = uiState.copy(
                    isLoading = false,
                    loadingMessage = null
                )
            }
        }
    }

    // ----------------------------------------
    // CONFIRM RESTORE + SAFETY BACKUP
    // ----------------------------------------

    fun confirmRestore(
        recoveryDestinationUri: Uri,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (uiState.isLoading || uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading) return

        val sourceUri = selectedRestoreUri
        val preview = uiState.restorePreview

        if (sourceUri == null || preview == null) {
            uiState = uiState.copy(
                errorMessage =
                    "Önce geri yüklenecek yedeği doğrulayın."
            )
            return
        }

        viewModelScope.launch {

            uiState = uiState.copy(
                isLoading = true,
                loadingMessage =
                    "Güvenlik yedeği oluşturuluyor ve veriler geri yükleniyor...",
                errorMessage = null,
                successMessage = null
            )

            try {
                val summary =
                    restoreBackupUseCase.restoreWithSafetyBackup(
                        sourceUri = sourceUri,
                        recoveryDestinationUri = recoveryDestinationUri,
                        expectedSha256 = preview.encryptedSha256,
                        passphrase = password.toCharArray()
                    )

                selectedRestoreUri = null

                uiState = uiState.copy(
                    restorePreview = null,
                    backupHealthReport = null,
                    databaseIntegrityReport = null,
                    backupSelfCheckReport = null,
                    successMessage =
                        "Geri yükleme tamamlandı. " +
                                "${summary.journalCount} anı, " +
                                "${summary.personCount} kişi ve " +
                                "${summary.imageCount} fotoğraf aktarıldı."
                )

                // Navigasyon ve UI işlemleri Main thread'de.
                onSuccess()

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage =
                        "Geri yükleme tamamlanamadı: " +
                                (e.localizedMessage ?: "Bilinmeyen hata")
                )

            } finally {
                uiState = uiState.copy(
                    isLoading = false,
                    loadingMessage = null
                )
            }
        }
    }

    fun cancelRestorePreview() {
        if (uiState.isLoading || uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading) return

        selectedRestoreUri = null

        uiState = uiState.copy(
            restorePreview = null
        )
    }

    fun dismissMessage() {
        uiState = uiState.copy(
            errorMessage = null,
            successMessage = null
        )
    }


    /**
     * Room kayıtlarını ve fotoğraf yollarını
     * değiştirmeden kontrol eder.
     */
    fun checkBackupHealth() {

        if (
            uiState.isLoading ||
            uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading
        ) return

        viewModelScope.launch {

            uiState = uiState.copy(
                isBackupHealthLoading = true,
                errorMessage = null,
                backupHealthReport = null,
                databaseIntegrityReport = null
            )

            try {
                // İlişkiler/fotoğraflar ve SQLite sağlığı aynı kilitte incelenir.
                val (report, sqliteReport) = writeCoordinator.withRegularWrite {
                    backupMaintenanceManager.inspect() to
                            databaseIntegrityVerifier.inspect()
                }

                uiState = uiState.copy(
                    backupHealthReport = report,
                    databaseIntegrityReport = sqliteReport
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage =
                        "Veri kontrolü yapılamadı: " +
                                (e.localizedMessage ?: "Bilinmeyen hata")
                )

            } finally {
                uiState = uiState.copy(
                    isBackupHealthLoading = false
                )
            }
        }
    }

    /**
     * Yalnızca eski ve kullanılmayan yedekleme
     * dosyalarını temizler.
     */
    fun cleanBackupLeftovers() {

        if (
            uiState.isLoading ||
            uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading
        ) return

        viewModelScope.launch {

            uiState = uiState.copy(
                isBackupHealthLoading = true,
                errorMessage = null,
                successMessage = null
            )

            try {
                val result = writeCoordinator.withRegularWrite {
                    backupMaintenanceManager.cleanupOldFiles()
                }

                val (updatedReport, sqliteReport) = writeCoordinator.withRegularWrite {
                    backupMaintenanceManager.inspect() to
                            databaseIntegrityVerifier.inspect()
                }

                uiState = uiState.copy(
                    backupHealthReport = updatedReport,
                    databaseIntegrityReport = sqliteReport,
                    successMessage =
                        "${result.removedTemporaryEntries} geçici kayıt ve " +
                                "${result.removedOrphanImages} " +
                                "kullanılmayan eski görsel temizlendi."
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage =
                        "Temizlik tamamlanamadı: " +
                                (e.localizedMessage ?: "Bilinmeyen hata")
                )

            } finally {
                uiState = uiState.copy(
                    isBackupHealthLoading = false
                )
            }
        }
    }


    /**
     * Kullanıcının mevcut verilerini değiştirmeden şifreli yedek,
     * şifre çözme, arşiv ve geçici Room geri yükleme provası yapar.
     * Bu fonksiyon gerçek SAF/cihazdan cihaza geri yükleme testi değildir.
     */
    fun runBackupSelfCheck() {
        if (
            uiState.isLoading ||
            uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading
        ) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isBackupSelfCheckLoading = true,
                backupSelfCheckReport = null,
                errorMessage = null,
                successMessage = null
            )

            try {
                val report = backupSelfCheckManager.runSelfCheck()
                uiState = uiState.copy(
                    backupSelfCheckReport = report
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage = "Yedekleme provası başarısız: " +
                            (e.localizedMessage ?: "Bilinmeyen hata")
                )
            } finally {
                uiState = uiState.copy(
                    isBackupSelfCheckLoading = false
                )
            }
        }
    }

    // ----------------------------------------
    // CLEAR ALL DATABASE DATA
    // ----------------------------------------

    fun clearAllData(onSuccess: () -> Unit) {
        if (uiState.isLoading || uiState.isBackupHealthLoading ||
            uiState.isBackupSelfCheckLoading) return

        viewModelScope.launch {

            uiState = uiState.copy(
                isLoading = true,
                loadingMessage = "Veriler temizleniyor...",
                errorMessage = null,
                successMessage = null
            )

            try {
                withContext(Dispatchers.IO) {
                    writeCoordinator.withRegularWrite {
                        appDatabase.clearAllTables()
                    }
                }

                selectedRestoreUri = null

                uiState = uiState.copy(
                    restorePreview = null,
                    backupHealthReport = null,
                    databaseIntegrityReport = null,
                    backupSelfCheckReport = null
                )

                onSuccess()

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage =
                        "Veriler silinemedi: " +
                                (e.localizedMessage ?: "Bilinmeyen hata")
                )

            } finally {
                uiState = uiState.copy(
                    isLoading = false,
                    loadingMessage = null
                )
            }
        }
    }
}
