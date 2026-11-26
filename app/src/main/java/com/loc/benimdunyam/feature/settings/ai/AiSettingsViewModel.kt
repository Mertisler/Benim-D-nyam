package com.loc.benimdunyam.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.data.ai.AiException
import com.loc.benimdunyam.data.ai.AiMemoryInput
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiTask
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import com.loc.benimdunyam.data.ai.insight.AnalysisProgress
import com.loc.benimdunyam.data.ai.insight.MemoryAnalysisProcessor
import com.loc.benimdunyam.data.ai.insight.MemoryAnalysisScheduler
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val enabled: Boolean = false,
    val hasAccount: Boolean = false,
    val cachedCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    /** Yalnızca debug: bağlantı denemesinin sonucu. */
    val testResult: String? = null,
    val progress: AnalysisProgress? = null,
    val analysisRunning: Boolean = false,
    val archiveRequested: Boolean = false
)

private data class Local(
    val cachedCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val testResult: String? = null,
    val progress: AnalysisProgress? = null,
    val archiveRequested: Boolean = false
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repository: AiRepository,
    private val auth: FirebaseAuth,
    private val processor: MemoryAnalysisProcessor,
    private val scheduler: MemoryAnalysisScheduler
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val state: StateFlow<AiSettingsUiState> = combine(
        repository.consent, local, scheduler.isRunning.onStart { emit(false) }
    ) { consent, l, running ->
        AiSettingsUiState(
            progress = l.progress,
            analysisRunning = running,
            archiveRequested = l.archiveRequested,
            enabled = consent.isValid,
            hasAccount = auth.currentUser != null,
            cachedCount = l.cachedCount,
            busy = l.busy,
            message = l.message,
            testResult = l.testResult
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUiState())

    init {
        refresh()
        // Analiz sürerken ilerleme sayıları güncellensin.
        viewModelScope.launch {
            scheduler.isRunning.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val progress = if (repository.consent.value.isValid) runCatching { processor.progress() }.getOrNull() else null
            local.update {
                it.copy(cachedCount = repository.cachedCount(), progress = progress,
                    archiveRequested = scheduler.archiveRequested())
            }
        }
    }

    fun enable() {
        repository.enable()
        local.update { it.copy(message = "Yapay zekâ özellikleri açıldı. Bundan sonra yazdığın anılar arka planda analiz edilir.") }
        refresh()
    }

    fun startArchiveAnalysis() {
        scheduler.startArchive()
        local.update { it.copy(archiveRequested = true, message = "Arşiv analizi başladı. Uygulamayı kapatsan da arka planda sürer.") }
    }

    fun stopArchiveAnalysis() {
        scheduler.stopArchive()
        local.update { it.copy(archiveRequested = false, message = "Arşiv analizi durduruldu. İstediğin zaman kaldığı yerden sürdürebilirsin.") }
        refresh()
    }

    fun disableAndClear() {
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            runCatching { scheduler.cancelAll() }
            runCatching { repository.disableAndClear() }
            local.update { it.copy(busy = false, cachedCount = 0, testResult = null, progress = null, archiveRequested = false,
                message = "Yapay zekâ kapatıldı; bu cihazdaki yapay zekâ sonuçları silindi.") }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    /**
     * Yalnızca debug: uydurma bir anıyla sunucuya gerçek bir istek gönderir (birkaç kuruşluk).
     * Gerçek anı gönderilmez; sonuç önbelleğe alınmaz.
     */
    fun runConnectionTest() {
        viewModelScope.launch {
            local.update { it.copy(busy = true, testResult = null) }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val sample = AiMemoryInput(
                id = "baglanti-testi",
                date = today,
                title = "Bağlantı testi",
                content = "Bu, yapay zekâ bağlantısını denemek için yazılmış uydurma bir anıdır. Sahilde yürüdüm ve deniz çok güzeldi.",
                people = emptyList()
            )
            val text = try {
                val r = repository.run(
                    AiTask.PERIOD_SUMMARY,
                    mapOf("donemAdi" to "Bağlantı testi", "bugun" to today, "anilar" to listOf(sample.toWire())),
                    useCache = false,
                    storeResult = false
                )
                "✓ ${r.model} (${r.version})\n${r.text}"
            } catch (c: CancellationException) {
                throw c
            } catch (e: AiException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ Beklenmeyen hata: ${e.javaClass.simpleName}"
            }
            local.update { it.copy(busy = false, testResult = text) }
        }
    }
}
