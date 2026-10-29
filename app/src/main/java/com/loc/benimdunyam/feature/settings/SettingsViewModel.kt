package com.loc.benimdunyam.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.backup.CryptoHelper
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isBiometricEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val appDatabase: AppDatabase
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState())
        private set

    private val gson = Gson()

    // 1. Biyometrik Tercihini Yükleme
    fun loadBiometricPreference(context: Context) {
        val prefs = context.getSharedPreferences("benim_dunyam_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("biometric_lock", false)
        uiState = uiState.copy(isBiometricEnabled = isEnabled)
    }

    // 2. Biyometrik Tercihini Kaydetme
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("benim_dunyam_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("biometric_lock", enabled).apply()
        uiState = uiState.copy(isBiometricEnabled = enabled)
    }

    // 3. Şifreli Olarak Dışa Aktar (Encrypted Export)
    fun exportDatabaseToJsonEncrypted(
        context: Context,
        uri: Uri,
        passphrase: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(isLoading = true)
            try {
                val backupData = backupRepository.createBackupData()
                val jsonString = gson.toJson(backupData)
                val encryptedBytes = CryptoHelper.encrypt(jsonString, passphrase.toCharArray())

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(encryptedBytes)
                }
                uiState = uiState.copy(isLoading = false)
                onResult(true, null)
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = e.localizedMessage)
                onResult(false, e.localizedMessage)
            }
        }
    }

    // 4. Şifreli Dosyadan Geri Yükle (Encrypted Import)
    fun importDatabaseFromJsonEncrypted(
        context: Context,
        uri: Uri,
        passphrase: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(isLoading = true)
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                } ?: throw IllegalArgumentException("Dosya okunamadı.")

                val jsonString = CryptoHelper.decrypt(bytes, passphrase.toCharArray())
                val backupData = gson.fromJson(jsonString, BackupData::class.java)

                if (backupData != null) {
                    backupRepository.restoreBackupData(backupData)
                    uiState = uiState.copy(isLoading = false)
                    onResult(true, null)
                } else {
                    uiState = uiState.copy(isLoading = false, errorMessage = "Geçersiz yedek dosyası.")
                    onResult(false, "Geçersiz yedek dosyası.")
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = "Hatalı şifre veya bozuk dosya.")
                onResult(false, "Hatalı şifre veya bozuk dosya.")
            }
        }
    }

    // 5. Tüm Veritabanını Temizle ve Sıfırla
    fun clearAllData(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(isLoading = true)
            try {
                appDatabase.clearAllTables()
                uiState = uiState.copy(isLoading = false)
                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = e.localizedMessage)
            }
        }
    }
}