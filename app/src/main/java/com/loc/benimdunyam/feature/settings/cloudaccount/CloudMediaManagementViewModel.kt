package com.loc.benimdunyam.feature.settings.cloudaccount

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.media.CloudMediaInventory
import com.loc.benimdunyam.data.sync.media.CloudMediaInventoryRepository
import com.loc.benimdunyam.data.sync.media.PreparedMediaCleanup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloudMediaManagementUiState(
    val loading: Boolean = false,
    val inventory: CloudMediaInventory? = null,
    val verifiedCleanupServiceReady: Boolean = false,
    val pendingPlan: PreparedMediaCleanup? = null,
    val status: String? = null,
    val error: String? = null
)

/**
 * Faz 2G.4 — Önce keşif; sonra güvenilir kanıt; SONRA ve ayrı kullanıcı onayı varsa
 * koşullu silme. Görevler otomatik/sessiz biçimde tetiklenmez.
 */
@HiltViewModel
class CloudMediaManagementViewModel @Inject constructor(
    private val repository: CloudMediaInventoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CloudMediaManagementUiState())
    val uiState = _uiState.asStateFlow()
    private var scopeKey: Pair<String, String>? = null
    private var activeJob: Job? = null

    /** Hesap veya Firebase Storage kapsamı değişince eski hesabın raporu ekranda kalmaz. */
    fun setScope(uid: String, mediaPrefix: String) {
        val key = uid to mediaPrefix
        if (scopeKey == key) return
        scopeKey = key
        activeJob?.cancel()
        activeJob = null
        _uiState.value = CloudMediaManagementUiState()
    }

    fun inspect(uid: String, mediaPrefix: String) {
        if (!SparkMediaPolicy.REMOTE_MEDIA_ENABLED) {
            _uiState.value = CloudMediaManagementUiState(error = SparkMediaPolicy.MESSAGE)
            return
        }
        if (_uiState.value.loading) return
        _uiState.update {
            it.copy(loading = true, inventory = null, pendingPlan = null,
                verifiedCleanupServiceReady = false, error = null, status = null)
        }
        activeJob = viewModelScope.launch {
            try {
                val inventory = repository.inspect(uid, mediaPrefix)
                val ready = repository.cleanupGatewayReady(uid)
                _uiState.update {
                    it.copy(loading = false, inventory = inventory,
                        verifiedCleanupServiceReady = ready,
                        status = "Rapor tamamlandı. Hiçbir dosya silinmedi.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Bulut raporu tamamlanamadı.")
                }
            }
        }
    }

    fun prepare(passwordInput: String) {
        if (!SparkMediaPolicy.REMOTE_MEDIA_ENABLED) {
            _uiState.value = CloudMediaManagementUiState(error = SparkMediaPolicy.MESSAGE)
            return
        }
        val current = _uiState.value
        val inventory = current.inventory ?: return
        if (current.loading || !current.verifiedCleanupServiceReady) return
        if (passwordInput.length !in 12..128) {
            _uiState.update { it.copy(error = "Medya aktarım şifresini (12–128 karakter) gir.") }
            return
        }
        val password = passwordInput.toCharArray()
        _uiState.update { it.copy(loading = true, error = null, status = null, pendingPlan = null) }
        activeJob = viewModelScope.launch {
            try {
                val plan = repository.prepareCleanup(inventory, password)
                _uiState.update { it.copy(loading = false, pendingPlan = plan) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "Kanıtlar doğrulanamadı.") }
            } finally {
                // Şifreyi ViewModel state'ine veya depoya kaydetme.
                password.fill('\u0000')
            }
        }
    }

    fun dismissPlan() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(pendingPlan = null) }
    }

    /**
     * Bu ikinci onay normal UI kutucuğundan farklıdır: kullanıcı onay kutusunu
     * işaretler VE tam ifadeyi yazar. Repository ayrıca süresi geçmiş planı reddeder.
     */
    fun executeApproved(uid: String, checked: Boolean, typed: String) {
        if (!SparkMediaPolicy.REMOTE_MEDIA_ENABLED) {
            _uiState.value = CloudMediaManagementUiState(error = SparkMediaPolicy.MESSAGE)
            return
        }
        val current = _uiState.value
        val plan = current.pendingPlan ?: return
        if (current.loading) return
        if (!checked || typed != "TEMİZLE" || uid != plan.uid) {
            _uiState.update { it.copy(error = "Ayrı temizleme onayı doğrulanamadı.") }
            return
        }
        _uiState.update { it.copy(loading = true, pendingPlan = null, error = null, status = null) }
        activeJob = viewModelScope.launch {
            try {
                val result = repository.deleteAfterSeparateConfirmation(plan)
                val status = "Sunucu onayıyla silinen nesne: ${result.deletedPaths.size}." +
                        if (result.failures.isEmpty()) "" else " İlk hatada işlem durduruldu."
                _uiState.update {
                    // Gerçek sonucu yeniden taramadan eski kapasiteyi gösterme.
                    it.copy(loading = false, inventory = null,
                        verifiedCleanupServiceReady = false,
                        status = status,
                        error = result.failures.firstOrNull())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, inventory = null,
                        verifiedCleanupServiceReady = false,
                        error = "Temizleme durdu: ${e.message}. Durumu yeniden tara.")
                }
            }
        }
    }
}
