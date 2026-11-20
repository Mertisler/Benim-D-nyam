package com.loc.benimdunyam.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.trash.JournalTrashRepository
import com.loc.benimdunyam.domain.sync.RemoteDeletePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Listede gösterilen tek kayıt; anı metninin tamamı UI durumuna alınmaz. */
data class RecentlyDeletedItem(
    val journalId: String,
    val title: String,
    val trashedAt: Long,
    val daysLeft: Long
)

data class RecentlyDeletedUiState(
    val busyJournalId: String? = null,
    val message: String? = null,
    val error: String? = null
)

/** Faz B — "Son silinenler" listesi, geri getirme ve kalıcı silme. */
@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    private val repository: JournalTrashRepository
) : ViewModel() {

    val items: StateFlow<List<RecentlyDeletedItem>> = repository.observeAll()
        .map { rows ->
            val now = System.currentTimeMillis()
            rows.map {
                RecentlyDeletedItem(
                    journalId = it.journalId,
                    title = it.title.ifBlank { "Başlıksız anı" },
                    trashedAt = it.trashedAt,
                    daysLeft = RemoteDeletePolicy.daysLeft(it.expiresAt, now)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutable = MutableStateFlow(RecentlyDeletedUiState())
    val state = mutable.asStateFlow()

    fun restore(journalId: String) = run(journalId) {
        val result = repository.restore(journalId)
        buildString {
            append("\"${result.title}\" geri getirildi.")
            if (result.skippedMentions > 0) {
                append(" ${result.skippedMentions} kişi bu cihazda artık olmadığı için etiketi eklenemedi.")
            }
            if (result.detachedSeason) append(" Dönemi artık olmadığı için dönemsiz eklendi.")
            if (result.willSync) append(" Bir sonraki eşitlemede diğer cihazlara da geri gelecek.")
        }
    }

    /** Ekranda ayrıca onaylanmış olmalıdır: geri alınamaz. */
    fun deletePermanently(journalId: String, confirmed: Boolean) {
        if (!confirmed) return
        run(journalId) {
            repository.deletePermanently(journalId)
            "Anı ve fotoğrafları kalıcı olarak silindi."
        }
    }

    fun clearMessage() {
        mutable.value = mutable.value.copy(message = null, error = null)
    }

    private fun run(journalId: String, action: suspend () -> String) {
        if (mutable.value.busyJournalId != null) return
        mutable.value = RecentlyDeletedUiState(busyJournalId = journalId)
        viewModelScope.launch {
            mutable.value = try {
                RecentlyDeletedUiState(message = action())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                RecentlyDeletedUiState(
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: "İşlem tamamlanamadı. Hiçbir kayıt silinmedi."
                )
            }
        }
    }
}
