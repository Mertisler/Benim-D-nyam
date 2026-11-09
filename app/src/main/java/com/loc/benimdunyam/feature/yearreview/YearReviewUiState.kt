package com.loc.benimdunyam.feature.yearreview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class YearReviewUiState(
    val isLoading: Boolean = true,
    val years: List<Int> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val report: YearReviewReport? = null,
    val coverPaths: Map<String, String> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class YearReviewViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    private val photoStore: JournalPhotoStore
) : ViewModel() {
    // null: varsayılan olarak anı içeren en güncel yıl (yoksa mevcut yıl).
    private val requestedYear = MutableStateFlow<Int?>(null)
    private val refreshSignal = MutableStateFlow(0)

    fun selectYear(year: Int) { requestedYear.value = year }
    fun refreshCovers() { refreshSignal.value += 1 }

    val uiState: StateFlow<YearReviewUiState> = combine(
        observeJournalsUseCase(),
        observeLifeSeasonsUseCase(),
        requestedYear,
        refreshSignal
    ) { journals, seasons, requested, _ ->
        val years = YearReviewCalculator.availableYears(
            journals, Calendar.getInstance().get(Calendar.YEAR)
        )
        val selected = requested?.takeIf { it in years }
            ?: years.firstOrNull { yr ->
                journals.any { journal ->
                    Calendar.getInstance().apply { timeInMillis = journal.entryDate }
                        .get(Calendar.YEAR) == yr
                }
            }
            ?: years.first()
        YearReviewUiState(
            isLoading = false,
            years = years,
            selectedYear = selected,
            report = YearReviewCalculator.calculate(selected, journals, seasons)
        )
    }.flatMapLatest { base ->
        flow {
            emit(base) // Fotoğraflar yüklenmeden metin görünür.
            val candidates = base.report?.coverCandidates.orEmpty()
            val found = mutableMapOf<String, String>()
            for (journal in candidates) {
                if (found.size >= 8) break
                val path = try {
                    photoStore.coverPhoto(journal.id)?.absolutePath
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("YearReview", "Anı kapağı okunamadı: ${journal.id}", error)
                    null
                }
                if (path != null) {
                    found[journal.id] = path
                    emit(base.copy(coverPaths = found.toMap()))
                }
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("YearReview", "Yıl özeti yüklenemedi", error)
        emit(YearReviewUiState(isLoading = false, error = "Yıl hikâyesi şu anda yüklenemedi."))
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YearReviewUiState()
    )
}
