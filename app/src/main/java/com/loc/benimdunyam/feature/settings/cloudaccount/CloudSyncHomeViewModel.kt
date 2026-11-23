package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.CloudSyncCloudState
import com.loc.benimdunyam.data.sync.CloudSyncOverview
import com.loc.benimdunyam.data.sync.CloudSyncSetupService
import com.loc.benimdunyam.data.sync.work.SyncFailureMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kurulum penceresinin durumu. Şifre burada TUTULMAZ. */
sealed interface CloudSyncSetupDialog {
    data object Checking : CloudSyncSetupDialog
    data class Ready(val cloud: CloudSyncCloudState) : CloudSyncSetupDialog
    data object Enabling : CloudSyncSetupDialog
    data class Failed(val message: String, val cloud: CloudSyncCloudState?) : CloudSyncSetupDialog
}

data class CloudSyncHomeUiState(
    val uid: String? = null,
    val overview: CloudSyncOverview? = null,
    val running: Boolean = false,
    val busy: Boolean = false,
    val setup: CloudSyncSetupDialog? = null,
    val message: String? = null,
    val error: String? = null
)

/** Sade bulut ekranının tek ViewModel'i; teknik ayrıntılar Gelişmiş bölümünde kalır. */
@HiltViewModel
class CloudSyncHomeViewModel @Inject constructor(
    private val service: CloudSyncSetupService
) : ViewModel() {

    private val mutable = MutableStateFlow(CloudSyncHomeUiState())
    val state = mutable.asStateFlow()
    private var runningJob: Job? = null

    fun select(uid: String) {
        if (mutable.value.uid == uid) {
            refresh()
            return
        }
        mutable.value = CloudSyncHomeUiState(uid = uid)
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            service.observeRunning(uid).distinctUntilChanged().collect { running ->
                mutable.update { it.copy(running = running) }
                // Bir çalışma bittiğinde sayıları ve "son eşitleme" bilgisini tazele.
                if (!running) refresh()
            }
        }
        refresh()
    }

    fun refresh() {
        val uid = mutable.value.uid ?: return
        viewModelScope.launch {
            val overview = runCatching { service.overview(uid) }.getOrNull()
            if (mutable.value.uid == uid && overview != null) {
                mutable.update { it.copy(overview = overview) }
            }
        }
    }

    /** Kurulum penceresini açar ve bulutta eşitleme verisi olup olmadığını öğrenir. */
    fun openSetup() {
        val uid = mutable.value.uid ?: return
        mutable.update { it.copy(setup = CloudSyncSetupDialog.Checking, error = null, message = null) }
        viewModelScope.launch {
            val next = try {
                CloudSyncSetupDialog.Ready(service.cloudState(uid))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CloudSyncSetupDialog.Failed(SyncFailureMessages.describe(error), null)
            }
            if (mutable.value.uid == uid && mutable.value.setup != null) {
                mutable.update { it.copy(setup = next) }
            }
        }
    }

    fun closeSetup() {
        if (mutable.value.setup != CloudSyncSetupDialog.Enabling) {
            mutable.update { it.copy(setup = null) }
        }
    }

    /** Parola diziye çevrilip servise verilir; servis işi bitince sıfırlar. */
    fun enable(password: String) {
        val uid = mutable.value.uid ?: return
        val cloud = (mutable.value.setup as? CloudSyncSetupDialog.Ready)?.cloud
            ?: (mutable.value.setup as? CloudSyncSetupDialog.Failed)?.cloud
            ?: return
        val secret = password.toCharArray()
        mutable.update { it.copy(setup = CloudSyncSetupDialog.Enabling) }
        viewModelScope.launch {
            try {
                service.enable(uid, secret)
                if (mutable.value.uid == uid) {
                    mutable.update {
                        it.copy(
                            setup = null,
                            message = "Eşitleme açıldı. Anıların arka planda şifreli olarak eşitlenecek."
                        )
                    }
                    refresh()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = if (cloud == CloudSyncCloudState.EXISTING &&
                    generateSequence<Throwable>(error) { it.cause }
                        .any { it is java.security.GeneralSecurityException || it is IllegalArgumentException }) {
                    "Bu şifre buluttaki eşitleme verilerini açamadı. Diğer cihazında belirlediğin " +
                            "eşitleme şifresini gir."
                } else SyncFailureMessages.describe(error)
                if (mutable.value.uid == uid) {
                    mutable.update { it.copy(setup = CloudSyncSetupDialog.Failed(message, cloud)) }
                }
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    fun syncNow() {
        val uid = mutable.value.uid ?: return
        runCatching { service.syncNow(uid) }
            .onSuccess { mutable.update { it.copy(message = "Eşitleme başlatıldı.", error = null) } }
            .onFailure { e -> mutable.update { it.copy(error = SyncFailureMessages.describe(e)) } }
    }

    fun disable() {
        val uid = mutable.value.uid ?: return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val result = runCatching { service.disable(uid) }
            mutable.update {
                it.copy(
                    busy = false,
                    message = if (result.isSuccess)
                        "Bu cihazda eşitleme kapatıldı. Cihazındaki ve buluttaki anılar silinmedi." else null,
                    error = result.exceptionOrNull()?.let(SyncFailureMessages::describe)
                )
            }
            refresh()
        }
    }

    fun dismissMessage() {
        mutable.update { it.copy(message = null, error = null) }
    }
}
