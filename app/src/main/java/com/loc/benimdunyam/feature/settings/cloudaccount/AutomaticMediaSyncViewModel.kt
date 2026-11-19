package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.work.AutomaticMediaSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI durumunda hiçbir parola veya fotoğrafın ham içeriği saklanmaz. */
data class AutomaticMediaSyncUiState(
    val uid: String = "",
    val busy: Boolean = false,
    val enabled: Boolean = false,
    val prerequisitesReady: Boolean = false,
    val lastRunAtMillis: Long = 0,
    val lastResult: String = "Henüz çalışmadı.",
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class AutomaticMediaSyncViewModel @Inject constructor(
    private val scheduler: AutomaticMediaSyncScheduler
) : ViewModel() {
    private val mutable = MutableStateFlow(AutomaticMediaSyncUiState())
    val state = mutable.asStateFlow()

    fun refresh(uid: String) {
        if (mutable.value.busy) return
        mutable.value = if (mutable.value.uid == uid) mutable.value else AutomaticMediaSyncUiState(uid = uid)
        val status = scheduler.status(uid)
        mutable.value = mutable.value.copy(
            enabled = status.enabled,
            prerequisitesReady = status.prerequisitesReady,
            lastRunAtMillis = status.lastRunAtMillis,
            lastResult = status.lastResult
        )
    }

    fun enable(uid: String) = action(uid) {
        val sampleVerified = scheduler.enable(uid)
        mutable.value = mutable.value.copy(info =
            if (sampleVerified) "Buluttaki örnek görselle şifre doğrulandı. Wi-Fi işi planlandı."
            else "Bulutta henüz görsel bulunmadı; kayıtlı cihaz şifresiyle Wi-Fi işi planlandı.")
    }

    fun disable(uid: String) = action(uid) {
        scheduler.disable(uid)
        mutable.value = mutable.value.copy(info = "Otomatik medya aktarımı durduruldu.")
    }

    fun runNow(uid: String) = action(uid) {
        scheduler.runNow(uid)
        mutable.value = mutable.value.copy(info =
            "Görsel aktarımı Wi-Fi ve pil koşulları uygunsa WorkManager sırasına alındı.")
    }

    private fun action(uid: String, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        if (mutable.value.uid != uid) mutable.value = AutomaticMediaSyncUiState(uid = uid)
        mutable.value = mutable.value.copy(busy = true, info = null, error = null)
        viewModelScope.launch {
            try {
                block()
                val status = scheduler.status(uid)
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(
                    enabled = status.enabled,
                    prerequisitesReady = status.prerequisitesReady,
                    lastRunAtMillis = status.lastRunAtMillis,
                    lastResult = status.lastResult
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(
                    error = error.message ?: "Medya eşitleme işlemi başlatılamadı."
                )
            } finally {
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
