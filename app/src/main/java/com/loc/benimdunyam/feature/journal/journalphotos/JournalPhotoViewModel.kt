package com.loc.benimdunyam.feature.journal.journalphotos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhoto
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JournalPhotoUiState(
    val journalId: String? = null,
    val photos: List<JournalPhoto> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val resultMessage: String? = null
)

@HiltViewModel
class JournalPhotoViewModel @Inject constructor(
    private val store: JournalPhotoStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(JournalPhotoUiState())
    val uiState = _uiState.asStateFlow()

    private var currentJournalId: String? = null
    private var loadJob: Job? = null

    /** Navigation ekranı değişmeden önceki/sonraki anıya geçilebilir. */
    fun loadForJournal(journalId: String) {
        if (journalId.isBlank()) return
        if (currentJournalId == journalId) return
        currentJournalId = journalId
        loadJob?.cancel()
        _uiState.value = JournalPhotoUiState(journalId = journalId, isLoading = true)

        loadJob = viewModelScope.launch {
            try {
                val photos = store.list(journalId)
                if (currentJournalId == journalId) {
                    _uiState.update { it.copy(photos = photos, isLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentJournalId == journalId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Fotoğraflar açılamadı: ${error.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun add(uris: List<Uri>, journalId: String) {
        if (uris.isEmpty() || currentJournalId != journalId || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, resultMessage = null) }
            var imported = 0
            var failed = 0
            var firstError: String? = null
            try {
                for (uri in uris.distinct()) {
                    try {
                        store.importPhoto(journalId, uri)
                        imported++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failed++
                        if (firstError == null) firstError = error.localizedMessage
                    }
                }
                val updatedPhotos = store.list(journalId)
                if (currentJournalId == journalId) {
                    _uiState.update {
                        it.copy(
                            photos = updatedPhotos,
                            isWorking = false,
                            errorMessage = if (failed > 0) "$failed fotoğraf eklenemedi: ${firstError ?: "Bilinmeyen hata"}" else null,
                            resultMessage = if (imported > 0) "$imported fotoğraf eklendi." else null
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentJournalId == journalId) {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = "Fotoğraf ekleme başarısız: ${error.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun delete(photoId: String, journalId: String) {
        if (currentJournalId != journalId || _uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, resultMessage = null) }
            try {
                store.delete(journalId, photoId)
                val updatedPhotos = store.list(journalId)
                if (currentJournalId == journalId) {
                    _uiState.update {
                        it.copy(photos = updatedPhotos, isWorking = false, resultMessage = "Fotoğraf silindi.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentJournalId == journalId) {
                    _uiState.update {
                        it.copy(isWorking = false, errorMessage = "Fotoğraf silinemedi: ${error.localizedMessage}")
                    }
                }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(errorMessage = null, resultMessage = null) }
    }
}
