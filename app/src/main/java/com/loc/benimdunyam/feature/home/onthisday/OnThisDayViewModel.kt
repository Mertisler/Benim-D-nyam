package com.loc.benimdunyam.feature.home.onthisday

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Room günlüklerini izler, sadece bugünün geçmiş yıl anılarını görüntüler. */
data class OnThisDayUiState(
    val isLoading: Boolean = true,
    val todayLabel: String = "",
    val memories: List<OnThisDayMemory> = emptyList(),
    val coverPaths: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnThisDayViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val photoStore: JournalPhotoStore
) : ViewModel() {

    private val currentTime = MutableStateFlow(System.currentTimeMillis())

    /** Ana sayfaya yeniden dönülürse tarihi ve kapakları tazele. */
    fun refreshToday() {
        currentTime.value = System.currentTimeMillis()
    }

    val uiState: StateFlow<OnThisDayUiState> = combine(
        observeJournalsUseCase(),
        currentTime
    ) { journals, now ->
        OnThisDayCalculator.calculate(journals, now)
    }.flatMapLatest { report ->
        flow {
            var state = OnThisDayUiState(
                isLoading = false,
                todayLabel = report.todayLabel,
                memories = report.memories
            )
            emit(state) // Metinleri fotoğrafları beklemeden göster.

            // Sadece öne çıkan anıların kapaklarını oku; tam fotoğraf yüklemesi Coil'de.
            val paths = mutableMapOf<String, String>()
            for (memory in report.memories.take(30)) {
                val path = try {
                    photoStore.coverPhoto(memory.journal.id)?.absolutePath
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("OnThisDay", "Anı kapağı okunamadı: ${memory.journal.id}", error)
                    null
                }
                if (path != null) {
                    paths[memory.journal.id] = path
                    state = state.copy(coverPaths = paths.toMap())
                    emit(state)
                }
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("OnThisDay", "Geçmiş anılar yüklenemedi", error)
        emit(
            OnThisDayUiState(
                isLoading = false,
                errorMessage = "Geçmiş anılar şu anda yüklenemedi."
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OnThisDayUiState()
    )
}
