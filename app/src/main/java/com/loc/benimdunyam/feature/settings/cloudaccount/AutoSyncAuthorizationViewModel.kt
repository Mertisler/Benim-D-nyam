package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.security.SyncAutoAuthorizationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Yalnızca izin durumu ve açıklamalar; şifre asla StateFlow'da tutulmaz. */
data class AutoSyncAuthorizationUiState(
    val uid: String? = null,
    val busy: Boolean = false,
    val enabled: Boolean = false,
    val grantedAtMillis: Long? = null,
    val remoteSampleVerified: Boolean = false,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class AutoSyncAuthorizationViewModel @Inject constructor(
    private val repository: SyncAutoAuthorizationRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AutoSyncAuthorizationUiState())
    val state = mutable.asStateFlow()

    fun setAccount(uid: String) {
        if (mutable.value.uid != uid) {
            mutable.value = AutoSyncAuthorizationUiState(uid = uid)
        }
    }

    fun refresh(uid: String) {
        if (mutable.value.uid != uid || mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val report = repository.status(uid)
                if (mutable.value.uid == uid) mutable.value = AutoSyncAuthorizationUiState(
                    uid = uid,
                    enabled = report.enabled,
                    grantedAtMillis = report.grantedAtMillis,
                    remoteSampleVerified = report.remoteSampleVerified,
                    info = report.note
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                showError(uid, error)
            }
        }
    }

    /** Ekrandan alınan şifrenin CharArray kopyasını iş bittiğinde temizleriz. */
    fun enable(uid: String, password: String, userConfirmed: Boolean) {
        if (mutable.value.uid != uid || mutable.value.busy || !userConfirmed) return
        val secret = password.toCharArray()
        mutable.value = mutable.value.copy(busy = true, info = null, error = null)
        viewModelScope.launch {
            try {
                val report = repository.enable(uid, secret)
                if (mutable.value.uid == uid) mutable.value = AutoSyncAuthorizationUiState(
                    uid = uid, enabled = true,
                    grantedAtMillis = report.grantedAtMillis,
                    remoteSampleVerified = report.remoteSampleVerified,
                    info = report.note
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                showError(uid, error)
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    fun disable(uid: String) {
        if (mutable.value.uid != uid || mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, info = null, error = null)
        viewModelScope.launch {
            try {
                val report = repository.disable(uid)
                if (mutable.value.uid == uid) mutable.value = AutoSyncAuthorizationUiState(
                    uid = uid, info = report.note
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                showError(uid, error)
            }
        }
    }

    private fun showError(uid: String, error: Exception) {
        if (mutable.value.uid == uid) mutable.value = mutable.value.copy(
            busy = false,
            error = error.message ?: "Cihaz eşitleme izni işlemi başarısız."
        )
    }
}
