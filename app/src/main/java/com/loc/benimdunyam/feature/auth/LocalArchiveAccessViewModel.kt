package com.loc.benimdunyam.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.core.auth.LocalArchiveAccessStore
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import com.loc.benimdunyam.domain.model.LocalArchiveAccessDecision
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Anıların hangi hesaba ait olduğunu, hangi Room alanının açılacağını denetler.
 *
 * Hilt singleton'ları çalışan süreçte değişmediğinden, başka bir çalışma alanı
 * seçildikten sonra uygulamanın tam kapatılıp yeniden açılmasını ister.
 */
data class LocalArchiveAccessUiState(
    val decision: LocalArchiveAccessDecision = LocalArchiveAccessDecision.CHECKING,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val currentAccountEmail: String? = null,
    val ownerUidSuffix: String? = null,
    val sessionGeneration: Int = 0,
    val restartRequired: Boolean = false,
    val canCreateEmptyWorkspace: Boolean = false,
    val canReturnLegacyWorkspace: Boolean = false,
    val isAccountWorkspace: Boolean = false
)

@HiltViewModel
class LocalArchiveAccessViewModel @Inject constructor(
    private val authRepository: CloudAuthRepository,
    private val store: LocalArchiveAccessStore,
    private val workspace: ActiveWorkspaceSession
) : ViewModel() {
    private val mutable = MutableStateFlow(LocalArchiveAccessUiState())
    val uiState: StateFlow<LocalArchiveAccessUiState> = mutable.asStateFlow()
    private var activeUid: String? = null
    private var firstEvent = true

    init {
        viewModelScope.launch {
            authRepository.account.collect { account ->
                val uid = account?.uid
                val changed = firstEvent || uid != activeUid
                firstEvent = false
                activeUid = uid
                refreshState(
                    email = account?.email,
                    generation = if (changed) mutable.value.sessionGeneration + 1
                    else mutable.value.sessionGeneration
                )
            }
        }
    }

    private fun refreshState(email: String? = mutable.value.currentAccountEmail,
                             generation: Int = mutable.value.sessionGeneration) {
        val uid = activeUid
        val decision = when (workspace.mode) {
            WorkspaceMode.LEGACY -> if (workspace.isReadyFor(uid))
                LocalArchiveAccessDecision.ALLOWED
            else if (workspace.needsRestart()) LocalArchiveAccessDecision.LOCKED
            else store.accessFor(uid)
            WorkspaceMode.ACCOUNT -> if (workspace.isReadyFor(uid))
                LocalArchiveAccessDecision.ALLOWED else LocalArchiveAccessDecision.LOCKED
            WorkspaceMode.LOCKED -> LocalArchiveAccessDecision.LOCKED
        }
        mutable.update { previous ->
            previous.copy(
                decision = decision,
                currentAccountEmail = email,
                ownerUidSuffix = store.ownerUidSuffix(),
                restartRequired = workspace.needsRestart(),
                canCreateEmptyWorkspace = workspace.canUseFreshAccount(uid),
                canReturnLegacyWorkspace = uid != null && store.ownerUidOrNull() == uid &&
                        workspace.mode != WorkspaceMode.LEGACY,
                isAccountWorkspace = workspace.mode == WorkspaceMode.ACCOUNT,
                sessionGeneration = generation
            )
        }
    }

    /** Henüz hesap sahibi bulunmayan eski arşivi açık onayla ilk hesaba bağlar. */
    fun confirmLinkToCurrentAccount() {
        if (mutable.value.isBusy || mutable.value.decision != LocalArchiveAccessDecision.CONSENT_REQUIRED) return
        val uid = activeUid ?: return
        viewModelScope.launch {
            mutable.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                val result = withContext(Dispatchers.IO) { store.bindOnceTo(uid) }
                check(result && uid == activeUid) { "Hesap değişti veya sahiplik kaydı oluşturulamadı." }
                refreshState()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.update { it.copy(errorMessage = error.message) } }
            finally { mutable.update { it.copy(isBusy = false) } }
        }
    }

    /** Başkasının eski anılarını taşımadan bu hesap için boş dört Room alanı seçer. */
    fun createEmptyWorkspace() {
        if (mutable.value.isBusy || !mutable.value.canCreateEmptyWorkspace) return
        val uid = activeUid ?: return
        viewModelScope.launch {
            mutable.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                withContext(Dispatchers.IO) { workspace.selectFreshAccount(uid) }
                check(uid == activeUid) { "Hesap işlem sırasında değişti." }
                refreshState()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.update { it.copy(errorMessage = error.message) } }
            finally { mutable.update { it.copy(isBusy = false) } }
        }
    }

    /** Önceki hesabın eski veritabanına dönmek için oturumu ve sahipliği doğrular. */
    fun returnToLegacyWorkspace() {
        if (mutable.value.isBusy || !mutable.value.canReturnLegacyWorkspace) return
        val uid = activeUid ?: return
        viewModelScope.launch {
            mutable.update { it.copy(isBusy = true, errorMessage = null) }
            try {
                withContext(Dispatchers.IO) { workspace.selectLegacy(uid) }
                check(uid == activeUid) { "Hesap işlem sırasında değişti." }
                refreshState()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { mutable.update { it.copy(errorMessage = error.message) } }
            finally { mutable.update { it.copy(isBusy = false) } }
        }
    }

    fun continueAsGuest() {
        if (mutable.value.decision != LocalArchiveAccessDecision.CONSENT_REQUIRED) return
        if (store.accessFor(activeUid) != LocalArchiveAccessDecision.CONSENT_REQUIRED) return
        authRepository.signOut()
    }
}
