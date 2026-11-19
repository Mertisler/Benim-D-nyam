package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.media.EncryptedMediaLocalCount
import com.loc.benimdunyam.data.sync.media.EncryptedMediaSyncRepository
import com.loc.benimdunyam.data.sync.work.AutomaticMediaSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Yalnızca cihazdaki medya SAYILARINI tutar; fotoğraf veya şifre içermez. */
data class SparkLocalMediaUiState(
    val uid: String = "",
    val loading: Boolean = false,
    val count: EncryptedMediaLocalCount? = null,
    val notice: String? = null,
    val error: String? = null
)

@HiltViewModel
class SparkLocalMediaViewModel @Inject constructor(
    private val repository: EncryptedMediaSyncRepository,
    private val mediaScheduler: AutomaticMediaSyncScheduler
) : ViewModel() {
    private val _ui = MutableStateFlow(SparkLocalMediaUiState())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    /**
     * Hesap değişince eski hesabın medya raporu gösterilmez. Varsa eski otomatik
     * medya iznini yerelde iptal ederiz; yeni Worker zaten Spark kilidini kontrol eder.
     * Bu işlem hiçbir Firebase okuması/yazması başlatmaz.
     */
    fun refresh(uid: String) {
        require(uid.isNotBlank())
        job?.cancel()
        _ui.value = SparkLocalMediaUiState(uid = uid, loading = true)
        job = viewModelScope.launch {
            var note: String? = null
            try {
                try {
                    mediaScheduler.disable(uid)
                } catch (_: Exception) {
                    // İş iptali başarısız olsa bile Worker katmanındaki Spark kilidi geçerlidir.
                    note = "Eski medya işi iptal edilemedi; koruma Worker üzerinden sürüyor."
                }
                val local = repository.localCount(uid) // Sadece Room / uygulama özel dosyaları.
                if (_ui.value.uid == uid) {
                    _ui.value = SparkLocalMediaUiState(uid = uid, count = local, notice = note)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_ui.value.uid == uid) _ui.value = SparkLocalMediaUiState(
                    uid = uid,
                    error = "Yerel medya sayıları okunamadı. Dosyalar silinmedi."
                )
            }
        }
    }
}
