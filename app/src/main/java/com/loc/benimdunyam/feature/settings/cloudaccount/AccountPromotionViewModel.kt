package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.isolation.AccountPromotionRepository
import com.loc.benimdunyam.data.local.isolation.AccountPromotionStatus
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Geri yükleme, silme veya otomatik senkronizasyon yapmayan aktivasyon arayüz durumu. */
data class AccountPromotionUiState(
    val isBusy: Boolean = false,
    val report: AccountPromotionStatus? = null,
    val success: String? = null,
    val error: String? = null
)

/**
 * Faz 2E.6: Önce kontrol, sonra kullanıcının açık onayıyla çalışma alanı seçimi.
 * Hesap değişirse daha önce verilmiş uygunluk kararı iptal edilir.
 */
@HiltViewModel
class AccountPromotionViewModel @Inject constructor(
    private val repository: AccountPromotionRepository,
    private val auth: CloudAuthRepository,
    val workspace: ActiveWorkspaceSession
) : ViewModel() {
    private val mutable = MutableStateFlow(AccountPromotionUiState())
    val uiState: StateFlow<AccountPromotionUiState> = mutable.asStateFlow()
    private var currentUid: String? = null

    val showSection: Boolean get() = workspace.mode == WorkspaceMode.LEGACY ||
            (workspace.mode == WorkspaceMode.ACCOUNT && workspace.isPromotedLegacy(workspace.accountUid))
    val isPromoted: Boolean get() = workspace.mode == WorkspaceMode.ACCOUNT &&
            workspace.isPromotedLegacy(workspace.accountUid)

    init {
        viewModelScope.launch {
            auth.account.collect { account ->
                if (currentUid != account?.uid) {
                    currentUid = account?.uid
                    mutable.value = AccountPromotionUiState()
                }
            }
        }
    }

    fun inspect() = operate(Action.CHECK)
    fun activate() = operate(Action.ACTIVATE)
    fun rollback() = operate(Action.ROLLBACK)

    private enum class Action { CHECK, ACTIVATE, ROLLBACK }

    private fun operate(action: Action) {
        if (mutable.value.isBusy) return
        if (action != Action.CHECK && mutable.value.report?.canProceed != true) return
        val startedUid = currentUid
        viewModelScope.launch {
            mutable.value = mutable.value.copy(isBusy = true, error = null, success = null)
            try {
                when (action) {
                    Action.CHECK -> {
                        val status = repository.inspect()
                        if (startedUid == currentUid) mutable.value = AccountPromotionUiState(report = status)
                    }
                    Action.ACTIVATE -> {
                        repository.activateWithConsent()
                        if (startedUid == currentUid) mutable.value = AccountPromotionUiState(
                            success = "Yeni arşiv seçildi. Uygulamayı son uygulamalardan kapatıp yeniden aç.")
                    }
                    Action.ROLLBACK -> {
                        repository.returnToLegacyWithConsent()
                        if (startedUid == currentUid) mutable.value = AccountPromotionUiState(
                            success = "Eski arşiv yeniden seçildi. Uygulamayı tamamen kapatıp yeniden aç.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (startedUid == currentUid) mutable.value = AccountPromotionUiState(
                    error = error.message ?: "Taşıma güvenlik kontrolü tamamlanamadı. Veri değiştirilmedi."
                )
            } finally {
                mutable.value = mutable.value.copy(isBusy = false)
            }
        }
    }
}
