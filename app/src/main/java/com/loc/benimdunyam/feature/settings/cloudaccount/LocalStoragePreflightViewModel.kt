package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.isolation.LocalStoragePreflightRepository
import com.loc.benimdunyam.domain.model.LocalStorageIsolationReport
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Denetim yalnızca hesap sahibinin açık oturumunda yapılabilir. */
data class LocalStoragePreflightUiState(
    val loading: Boolean = false,
    val report: LocalStorageIsolationReport? = null,
    val error: String? = null
)

@HiltViewModel
class LocalStoragePreflightViewModel @Inject constructor(
    private val auth: CloudAuthRepository,
    private val repository: LocalStoragePreflightRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LocalStoragePreflightUiState())
    val state: StateFlow<LocalStoragePreflightUiState> = _state.asStateFlow()
    private var inspectionJob: Job? = null
    private var lastUid: String? = null

    init {
        viewModelScope.launch {
            auth.account.collect { account ->
                if (account?.uid != lastUid) {
                    inspectionJob?.cancel()
                    lastUid = account?.uid
                    // Eski hesaba ait raporun yeni oturumda görünmesine izin vermeyiz.
                    _state.value = LocalStoragePreflightUiState()
                }
            }
        }
    }

    /** Hiçbir Room tablosuna kayıt yazmaz; yalnızca dosya varlığını inceler. */
    fun inspect() {
        if (_state.value.loading) return
        inspectionJob?.cancel()
        inspectionJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, report = null) }
            try {
                val uid = auth.account.first()?.uid
                    ?: error("Önce bulut hesabına giriş yapmalısın.")
                val report = repository.inspect(uid)
                // Tarama sürerken kullanıcı hesap değiştirmiş olabilir.
                if (auth.account.first()?.uid == uid) {
                    _state.value = LocalStoragePreflightUiState(report = report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = LocalStoragePreflightUiState(
                    error = "Depolama kapsamı okunamadı. Hiçbir verin değiştirilmedi."
                )
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
