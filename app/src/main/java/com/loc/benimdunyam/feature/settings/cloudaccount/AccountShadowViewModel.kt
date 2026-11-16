package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.isolation.AccountShadowDatabaseRepository
import com.loc.benimdunyam.domain.model.AccountShadowCopyInfo
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Hesaba özel Room kopyasının sadece denetim ve hazırlık durumunu taşır. */
data class AccountShadowUiState(
    val busy: Boolean = false,
    val info: AccountShadowCopyInfo? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * Mevcut aktif Room veritabanlarına dokunmadan yapılan kopyalama işlemini yönetir.
 * Oturum değişiminde eski hesabın raporunu ekranda göstermemek için state temizlenir.
 */
@HiltViewModel
class AccountShadowViewModel @Inject constructor(
    private val repository: AccountShadowDatabaseRepository,
    private val auth: CloudAuthRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountShadowUiState())
    val uiState: StateFlow<AccountShadowUiState> = mutable.asStateFlow()
    private var currentUid: String? = null
    private var running: Job? = null

    init {
        viewModelScope.launch {
            auth.account.collect { account ->
                if (currentUid != account?.uid) {
                    running?.cancel()
                    currentUid = account?.uid
                    mutable.value = AccountShadowUiState()
                }
            }
        }
    }

    fun inspect() = runOperation(create = false)

    /** Kullanıcının onay kutusunu işaretlemesi ardından çağrılır. */
    fun prepare() = runOperation(create = true)

    private fun runOperation(create: Boolean) {
        if (mutable.value.busy) return
        running?.cancel()
        val startUid = currentUid
        running = viewModelScope.launch {
            mutable.value = AccountShadowUiState(busy = true)
            try {
                val result = if (create) repository.prepareWithConsent() else repository.inspect()
                if (startUid == currentUid) {
                    mutable.value = AccountShadowUiState(
                        info = result,
                        message = when {
                            result == null -> "Henüz hesaba özel Room kopyası bulunmuyor. Önce şifreli hazırlık kopyasını doğrula."
                            create -> "Dört ayrı Room veritabanı doğrulandı. Canlı geçiş hâlâ devre dışı."
                            else -> "Hesaba özel Room kopyası yeniden kontrol edildi."
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (startUid == currentUid) mutable.value = AccountShadowUiState(
                    error = error.message ?: "Hesaba özel kopya hazırlığı tamamlanamadı."
                )
            }
        }
    }
}
