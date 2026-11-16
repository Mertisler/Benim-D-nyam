package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.isolation.AccountMigrationSnapshotRepository
import com.loc.benimdunyam.domain.model.AccountMigrationSnapshotInfo
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

/** Yalnızca hesabın yerel taşıma hazırlığına ait UI durumu. */
data class AccountMigrationUiState(
    val isBusy: Boolean = false,
    val verifiedSnapshot: AccountMigrationSnapshotInfo? = null,
    val error: String? = null,
    val message: String? = null
)

/**
 * Taşıma hazırlığının ViewModel'i. Şifreli dosya oluşturulması kullanıcı onayıyla
 * başlar. Bu sınıf Room dosya yollarını değiştirmez veya diğer hesaba geçmez.
 */
@HiltViewModel
class AccountMigrationViewModel @Inject constructor(
    private val repository: AccountMigrationSnapshotRepository,
    private val auth: CloudAuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AccountMigrationUiState())
    val state: StateFlow<AccountMigrationUiState> = _state.asStateFlow()
    private var activeUid: String? = null
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            auth.account.collect { account ->
                if (activeUid != account?.uid) {
                    operation?.cancel()
                    activeUid = account?.uid
                    // Önceki kullanıcının hazırlık raporu yeni oturumda görünmez.
                    _state.value = AccountMigrationUiState()
                }
            }
        }
    }

    /** Fiziksel kopyayı oluşturmadan önce mevcut olanı doğrular. */
    fun inspectSnapshot() {
        if (_state.value.isBusy) return
        operation?.cancel()
        val uid = activeUid
        operation = viewModelScope.launch {
            _state.value = AccountMigrationUiState(isBusy = true)
            try {
                val info = repository.inspect()
                if (uid == activeUid) _state.value = AccountMigrationUiState(
                    verifiedSnapshot = info,
                    message = if (info == null) "Bu hesap için henüz hazırlık kopyası yok."
                    else "Kopya yeniden doğrulandı. Canlı veri geçişi hâlâ kapalı."
                )
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                if (uid == activeUid) _state.value = AccountMigrationUiState(
                    error = e.message ?: "Hazırlık kopyası kontrol edilemedi."
                )
            }
        }
    }

    /** Ekranda açık onay alındıktan SONRA çağrılır. */
    fun prepareSnapshot() {
        if (_state.value.isBusy) return
        operation?.cancel()
        val uid = activeUid
        operation = viewModelScope.launch {
            _state.value = AccountMigrationUiState(isBusy = true)
            try {
                val info = repository.prepareWithConsent()
                if (uid == activeUid) _state.value = AccountMigrationUiState(
                    verifiedSnapshot = info,
                    message = "Hesaba özel şifreli hazırlık kopyası oluşturuldu ve doğrulandı."
                )
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                if (uid == activeUid) _state.value = AccountMigrationUiState(
                    error = e.message ?: "Hazırlık tamamlanamadı; mevcut anılar korunuyor."
                )
            }
        }
    }
}
