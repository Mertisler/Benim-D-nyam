package com.loc.benimdunyam.feature.seasons.summary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Only reads the selected journal covers; never copies or edits photo files. */
@HiltViewModel
class SeasonStoryCoverViewModel @Inject constructor(
    private val photoStore: JournalPhotoStore
) : ViewModel() {
    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    val covers: StateFlow<Map<String, String>> = _covers.asStateFlow()
    private var currentIds: List<String> = emptyList()
    private var loadJob: Job? = null

    fun load(journalIds: List<String>, forceRefresh: Boolean = false) {
        val unique = journalIds.filter { it.isNotBlank() }.distinct().take(36)
        if (!forceRefresh && unique == currentIds) return
        currentIds = unique
        loadJob?.cancel()
        _covers.value = emptyMap()
        if (unique.isEmpty()) return
        loadJob = viewModelScope.launch {
            var found = 0
            for (id in unique) {
                val path = try {
                    photoStore.coverPhoto(id)?.absolutePath
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("SeasonStoryCovers", "Anı kapağı okunamadı: $id", error)
                    null
                }
                if (path != null) {
                    _covers.update { it + (id to path) }
                    found++
                    if (found >= 5) break
                }
            }
        }
    }
}
