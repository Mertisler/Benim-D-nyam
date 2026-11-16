package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.sync.local.SyncOutboxStore
import com.loc.benimdunyam.data.sync.work.SyncFoundationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ekran yalnızca kuyruk/iş durumunu gösterir; günlük içeriği UI state'e taşınmaz.
 * İnternet veya Firebase yazma işlemi başlatılmaz.
 */
data class SyncFoundationUiState(
    val busy: Boolean = false,
    val pending: Int = 0,
    val finished: Int = 0,
    val pendingJournalUpserts: Int = 0,
    val pendingJournalDeletes: Int = 0,
    val workerState: String = "Henüz kontrol edilmedi",
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class SyncFoundationViewModel @Inject constructor(
    private val store: SyncOutboxStore,
    private val scheduler: SyncFoundationScheduler,
    private val journalEvents: JournalSyncEventDao
) : ViewModel() {
    private val _state = MutableStateFlow(SyncFoundationUiState())
    val state = _state.asStateFlow()

    /** Hesap değişince eski hesabın sayıları ekranda tutulmaz. */
    fun refresh(uid: String) = viewModelScope.launch {
        _state.value = SyncFoundationUiState(busy = true)
        try {
            check(store.isAccountReady(uid)) { "Önce doğru hesap alanını açmalısın." }
            val counts = withContext(Dispatchers.IO) { store.counts(uid) }
            // Ana Room'da anı değişiklikleri atomik olarak tutulur.
            val journalCounts = withContext(Dispatchers.IO) {
                journalEvents.pendingUpserts(uid) to journalEvents.pendingDeletes(uid)
            }
            // WorkManager 2.9+ Flow API'siyle iş durumunu asenkron okuyoruz.
            // ListenableFuture.get() kullanmak yerine Flow.first() beklemek,
            // UI'yi bloklamadan Coroutine iptallerini de doğru yönetir.
            val stateName = withContext(Dispatchers.IO) {
                val workInfos = scheduler.workManager()
                    .getWorkInfosForUniqueWorkFlow(scheduler.uniqueWorkName(uid))
                    .first()
                workInfos.firstOrNull()?.state?.name ?: "YOK"
            }
            _state.value = SyncFoundationUiState(
                pending = counts.first,
                finished = counts.second,
                pendingJournalUpserts = journalCounts.first,
                pendingJournalDeletes = journalCounts.second,
                workerState = stateName
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = SyncFoundationUiState(error = e.message ?: "Durum okunamadı.")
        }
    }

    /**
     * Butona basılmadan hiçbir iş oluşturulmaz. İş yalnızca yerel kayıt
     * hazırlığı ve WorkManager koşullarını doğrular; buluta aktarım yapmaz.
     */
    fun runDiagnostic(uid: String) = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, error = null, message = null) }
        try {
            withContext(Dispatchers.IO) { scheduler.scheduleDiagnostic(uid) }
            val counts = withContext(Dispatchers.IO) { store.counts(uid) }
            val journalCounts = withContext(Dispatchers.IO) {
                journalEvents.pendingUpserts(uid) to journalEvents.pendingDeletes(uid)
            }
            _state.update {
                it.copy(
                    busy = false, pending = counts.first, finished = counts.second,
                    pendingJournalUpserts = journalCounts.first,
                    pendingJournalDeletes = journalCounts.second,
                    workerState = "ENQUEUED / koşullar bekleniyor olabilir",
                    message = "Tanılama işi sıraya alındı. Hiçbir anı buluta gönderilmedi."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "İş planlanamadı.") }
        }
    }
}
