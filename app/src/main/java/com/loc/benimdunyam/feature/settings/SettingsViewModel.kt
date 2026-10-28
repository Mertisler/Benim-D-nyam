package com.loc.benimdunyam.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDatabaseCleared: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appDatabase: AppDatabase
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState())
        private set

    fun clearAllData(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState = uiState.copy(isLoading = true)
            try {
                // Room veritabanındaki tüm tabloları temizle
                appDatabase.clearAllTables()
                uiState = uiState.copy(isLoading = false, isDatabaseCleared = true)
                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }
}