package com.loc.benimdunyam.feature.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.backup.JournalMediaArchiveManager
import com.loc.benimdunyam.data.backup.JournalMediaArchivePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Faz 2I.3: Parola ve ZIP içeriği hiçbir zaman UI state içine saklanmaz. */
data class JournalMediaBackupUiState(
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val preview: JournalMediaArchivePreview? = null
)

@HiltViewModel
class JournalMediaBackupViewModel @Inject constructor(
    private val archive: JournalMediaArchiveManager
) : ViewModel() {
    var state by mutableStateOf(JournalMediaBackupUiState())
        private set

    // SAF belgesinin URI'si özel alanda tutulur; parola veya çözülmüş ZIP saklanmaz.
    private var selectedUri: Uri? = null

    fun export(uri: Uri, password: String) {
        if (state.busy) return
        clearPreview()
        viewModelScope.launch {
            state = state.copy(busy = true, error = null, status = null)
            try {
                val count = archive.export(uri, password.toCharArray())
                state = state.copy(status = "Şifreli medya yedeği doğrulandı ve kaydedildi ($count fotoğraf). " +
                        "Bu dosyayı .bdbackup ile birlikte güvenli bir yerde sakla.")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                state = state.copy(error = "Medya yedeği oluşturulamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}")
            } finally { state = state.copy(busy = false) }
        }
    }

    fun inspect(uri: Uri, password: String) {
        if (state.busy) return
        clearPreview()
        viewModelScope.launch {
            state = state.copy(busy = true, error = null, status = null)
            try {
                val preview = archive.preview(uri, password.toCharArray())
                selectedUri = uri
                state = state.copy(preview = preview, status = "Medya yedeği doğrulandı; henüz içe aktarılmadı.")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                state = state.copy(error = "Medya arşivi doğrulanamadı: ${e.localizedMessage ?: "Geçersiz dosya"}")
            } finally { state = state.copy(busy = false) }
        }
    }

    fun restore(password: String) {
        val uri = selectedUri ?: return
        val preview = state.preview ?: return
        if (state.busy) return
        viewModelScope.launch {
            state = state.copy(busy = true, status = null, error = null)
            try {
                val result = archive.restore(uri, password.toCharArray(), preview)
                selectedUri = null
                state = state.copy(
                    preview = null,
                    status = "Medya geri yükleme tamamlandı: ${result.restoredCount} yeni fotoğraf, " +
                            "${result.alreadyPresentCount} zaten mevcut, " +
                            "${result.deletedPhotoBlockedCount} silme işaretiyle engellendi. " +
                            "${result.layoutRestoredCount} anının fotoğraf düzeni geri geldi."
                )
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                // Geri yükleme sırasında kısmi ekleme olmuş olabilir; yeni önizleme zorunlu.
                selectedUri = null
                state = state.copy(preview = null,
                    error = "Medya geri yükleme tamamlanmadı: ${e.localizedMessage ?: "Hata"}. " +
                            "Mevcut dosyalar silinmedi. Yeniden önizleme yap.")
            } finally { state = state.copy(busy = false) }
        }
    }

    fun clearPreview() {
        if (state.busy) return
        selectedUri = null
        state = state.copy(preview = null, error = null, status = null)
    }
}
