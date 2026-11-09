package com.loc.benimdunyam.feature.journal.cover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Yalnızca arşivde ekrana giren anıların kapak dosyasını okur.
 * Fotoğrafın kendisi Room'a yazılmaz; mevcut JournalPhotoStore'dan alınır.
 * JournalPhotoStore.coverPhoto() kendi içinde Dispatchers.IO kullanır.
 */
@HiltViewModel
class JournalArchiveCoverViewModel @Inject constructor(
    private val photoStore: JournalPhotoStore
) : ViewModel() {

    private val _coverPaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val coverPaths: StateFlow<Map<String, String>> = _coverPaths.asStateFlow()

    private val _refreshVersion = MutableStateFlow(0L)
    val refreshVersion: StateFlow<Long> = _refreshVersion.asStateFlow()

    // Aynı satırın bir sürümde tekrar tekrar dosyadan okunmasını engeller.
    // Tüm erişim viewModelScope'un Main bağlamında gerçekleşir.
    private val alreadyRequested = mutableSetOf<Pair<String, Long>>()

    /** Detay / düzenlemeden geri dönüşte kapak metadata'sı değişmiş olabilir. */
    fun invalidateVisibleCovers() {
        alreadyRequested.clear()
        _refreshVersion.update { it + 1L }
    }

    fun ensureCoverLoaded(journalId: String, version: Long) {
        if (journalId.isBlank() || version != _refreshVersion.value) return
        if (!alreadyRequested.add(journalId to version)) return

        viewModelScope.launch {
            val path = try {
                photoStore.coverPhoto(journalId)?.absolutePath
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Tek bozuk fotoğraf başka anıların açılmasını engellemesin.
                Log.w("JournalArchiveCover", "Kapak yüklenemedi: $journalId", error)
                null
            }

            // Eski isteğin sonucu yeni kapak seçimini ezmesin.
            if (version == _refreshVersion.value) {
                _coverPaths.update { existing ->
                    if (path == null) existing - journalId
                    else existing + (journalId to path)
                }
            }
        }
    }
}
