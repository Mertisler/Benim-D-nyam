package com.loc.benimdunyam.feature.journal.journaleditor

import com.loc.benimdunyam.data.local.draft.JournalDraftEntity
import com.loc.benimdunyam.data.local.draft.JournalDraftStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface DraftSaveState {
    data object Idle : DraftSaveState
    data object Pending : DraftSaveState
    data object Saving : DraftSaveState
    data object Saved : DraftSaveState
    data class Error(val message: String) : DraftSaveState
}

/**
 * Her editor ViewModel'i icin bir tane olusturulur.
 *
 * schedule: kullanici son degisikligini yaptiktan 1000 ms sonra kaydeder.
 * flush: ekrandan normal cikmadan once bekleyen degisiklikleri tamamlar.
 * clearAfterPublish: normal gunluk basariyla kaydedildikten sonra taslagi siler.
 */
class JournalDraftAutosave(
    private val store: JournalDraftStore,
    private val scope: CoroutineScope,
    private val draftKey: String,
    private val debounceMillis: Long = 1_000L
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DraftSaveState>(DraftSaveState.Idle)
    val state: StateFlow<DraftSaveState> = _state.asStateFlow()

    private var latestSnapshot: JournalDraftEntity? = null
    private var writeJob: Job? = null
    private var published = false

    suspend fun load(): JournalDraftEntity? = store.load(draftKey)

    /** Diskteki taslak geri yuklendiginde "kaydedildi" durumunu gosterir. */
    fun markRestored(draft: JournalDraftEntity) {
        require(draft.draftKey == draftKey)
        latestSnapshot = draft
        _state.value = DraftSaveState.Saved
    }

    fun schedule(draft: JournalDraftEntity) {
        require(draft.draftKey == draftKey) { "Yanlis taslak anahtari" }
        if (published) return

        latestSnapshot = draft
        writeJob?.cancel()
        _state.value = DraftSaveState.Pending
        writeJob = scope.launch {
            delay(debounceMillis)
            persist(draft)
        }
    }

    /** Basarisizlikta false: ekrandan ayrilma; kullaniciya hata goster. */
    suspend fun flush(): Boolean {
        writeJob?.cancelAndJoin()
        writeJob = null
        return latestSnapshot?.let { persist(it) } ?: true
    }

    /** Asil gunluk kaydedilmeden cagirilmaz. */
    suspend fun clearAfterPublish() {
        published = true
        writeJob?.cancelAndJoin()
        writeJob = null
        mutex.withLock {
            store.delete(draftKey)
            latestSnapshot = null
            _state.value = DraftSaveState.Idle
        }
    }

    /** Kullanici taslagi bilerek sildiginde. */
    suspend fun discard() {
        writeJob?.cancelAndJoin()
        writeJob = null
        mutex.withLock {
            store.delete(draftKey)
            latestSnapshot = null
            published = false
            _state.value = DraftSaveState.Idle
        }
    }

    private suspend fun persist(draft: JournalDraftEntity): Boolean = mutex.withLock {
        // Eski bir debounce yazisi, yeni taslagin veya normal kaydin ustune yazamaz.
        if (published || latestSnapshot !== draft) return@withLock true

        _state.value = DraftSaveState.Saving
        try {
            store.save(draft)
            if (!published && latestSnapshot === draft) {
                _state.value = DraftSaveState.Saved
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _state.value = DraftSaveState.Error(
                error.localizedMessage ?: "Taslak kaydedilemedi."
            )
            false
        }
    }
}
