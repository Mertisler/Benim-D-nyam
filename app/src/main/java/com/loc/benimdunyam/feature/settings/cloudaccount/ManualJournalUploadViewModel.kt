package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.remote.ManualJournalEventUploader
import com.loc.benimdunyam.data.sync.remote.PartialJournalUploadException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Yalnızca görüntülenecek sayı ve işlem durumları; şifre/anı metni İÇERMEZ. */
data class ManualJournalUploadUiState(
    val busy: Boolean = false,
    val pending: Int = 0,
    val status: String? = null,
    val error: String? = null
)

@HiltViewModel
class ManualJournalUploadViewModel @Inject constructor(
    private val uploader: ManualJournalEventUploader
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManualJournalUploadUiState())
    val uiState = _uiState.asStateFlow()

    /** Hesap değişince önceki kullanıcıya ait sayıları gösterme. */
    fun refresh(uid: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = ManualJournalUploadUiState(busy = true)
            try {
                _uiState.value = ManualJournalUploadUiState(pending = uploader.pending(uid))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.value = ManualJournalUploadUiState(error = e.message ?: "Kuyruk okunamadı.")
            }
        }
    }

    /**
     * Kullanıcı şifreyi bu ekranda girer; otomatik/background senkronizasyon yoktur.
     * CharArray iş bitince sıfırlanır. Şifre StateFlow, SavedStateHandle veya
     * WorkManager verilerine kaydedilmez.
     */
    fun upload(uid: String, passphrase: String) {
        if (_uiState.value.busy) return
        val password = passphrase.toCharArray()
        _uiState.value = _uiState.value.copy(busy = true, status = null, error = null)
        viewModelScope.launch {
            try {
                val report = uploader.uploadPending(uid, password)
                _uiState.value = ManualJournalUploadUiState(
                    pending = report.pending,
                    status = "${report.confirmed} şifreli olay sunucuda doğrulandı. " +
                            "${report.changedDuringUpload} olay işlem sırasında değişti. " +
                            "Kalan: ${report.pending}."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val success = (error as? PartialJournalUploadException)?.confirmed ?: 0
                val message = error.cause?.message ?: error.message ?: "İşlem tamamlanamadı."
                val pending = runCatching { uploader.pending(uid) }.getOrDefault(0)
                _uiState.value = ManualJournalUploadUiState(
                    pending = pending,
                    error = "$success olay doğrulandı. İşlem durdu: $message"
                )
            } finally {
                // Bellekte uzun süre şifre taşımamak için diziyi temizle.
                password.fill('\u0000')
            }
        }
    }
}
