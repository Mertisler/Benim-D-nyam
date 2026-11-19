package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import com.loc.benimdunyam.data.sync.work.AutomaticJournalSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kullanıcıya gösterilen bilgiler; parola veya günlük metni içermez. */
data class AutomaticJournalSyncUiState(
    val uid: String = "",
    val busy: Boolean = false,
    val enabled: Boolean = false,
    val devicePermission: Boolean = false,
    val remoteVerified: Boolean = false,
    val lastSuccessfulScanAtMillis: Long = 0L,
    val nextEarliestScanAtMillis: Long = 0L,
    val manualScanPending: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class AutomaticJournalSyncViewModel @Inject constructor(
    private val scheduler: AutomaticJournalSyncScheduler,
    private val permission: SyncAutoAuthorizationRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AutomaticJournalSyncUiState())
    val state = mutable.asStateFlow()

    fun refresh(uid: String) = execute(uid) {
        val authorization = permission.status(uid)
        val scan = scheduler.scanStatus(uid) // Yerel metadata, ağ sorgusu yok.
        mutable.value = mutable.value.copy(
            enabled = scheduler.isStarted(uid),
            devicePermission = authorization.enabled,
            remoteVerified = authorization.remoteSampleVerified,
            lastSuccessfulScanAtMillis = scan.lastSuccessfulScanAtMillis,
            nextEarliestScanAtMillis = scan.nextEarliestScanAtMillis,
            manualScanPending = scan.manualRequestPending
        )
    }

    fun start(uid: String) = execute(uid) {
        scheduler.start(uid)
        val scan = scheduler.scanStatus(uid)
        mutable.value = mutable.value.copy(
            enabled = true,
            manualScanPending = scan.manualRequestPending,
            message = "Eşitleme planlandı. İlk kapsam kontrolü sıraya alındı."
        )
    }

    fun stop(uid: String) = execute(uid) {
        scheduler.stop(uid)
        mutable.value = mutable.value.copy(enabled = false,
            manualScanPending = false,
            message = "Yeni otomatik eşitlemeler durduruldu.")
    }

    fun runNow(uid: String) = execute(uid) {
        scheduler.runNow(uid)
        mutable.value = mutable.value.copy(
            manualScanPending = true,
            message = "Manuel tam kontrol istendi. İş sırası ve ağ koşullarını WorkManager belirler."
        )
    }

    private fun execute(uid: String, action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(uid = uid, busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutable.value.uid == uid) {
                    mutable.value = mutable.value.copy(error = error.message ?: "İşlem tamamlanamadı.")
                }
            } finally {
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
