package com.loc.benimdunyam.data.ai.insight

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Anı analizini tetikler. Kayıt akışı (SaveJournalUseCase) yalnızca bu arayüzü bilir. */
interface MemoryAnalysisScheduler {
    /** Bir anı kaydedildi: kısa bir beklemeden sonra (art arda düzenlemeler birleşsin) işle. */
    fun requestSoon()
    /** "Arşivimi analiz et": eski anıları da işlemeye başla. */
    fun startArchive()
    /** Arşiv analizini durdur; yeni anılar yine kendiliğinden işlenir. */
    fun stopArchive()
    /** İzin kapatıldı: tüm işleri iptal et. */
    fun cancelAll()
    val isRunning: Flow<Boolean>
    fun archiveRequested(): Boolean
}

@Singleton
class WorkManagerMemoryAnalysisScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: com.loc.benimdunyam.data.ai.AiConsentSource
) : MemoryAnalysisScheduler {
    private val prefs = context.getSharedPreferences("bd_ai_analysis_v1", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)

    private fun enqueue(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<MemoryAnalysisWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        work.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun requestSoon() {
        // İzin yoksa iş kuyruğa hiç girmez.
        if (consent.consent.value.isValid) enqueue(20)
    }

    override fun startArchive() {
        prefs.edit().putBoolean(KEY_ARCHIVE, true).apply()
        if (consent.consent.value.isValid) enqueue(0)
    }

    override fun stopArchive() {
        prefs.edit().putBoolean(KEY_ARCHIVE, false).apply()
        work.cancelUniqueWork(UNIQUE)
    }

    override fun cancelAll() {
        prefs.edit().putBoolean(KEY_ARCHIVE, false).apply()
        work.cancelUniqueWork(UNIQUE)
    }

    override val isRunning: Flow<Boolean> = work.getWorkInfosForUniqueWorkFlow(UNIQUE)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    override fun archiveRequested(): Boolean = prefs.getBoolean(KEY_ARCHIVE, false)

    companion object {
        const val UNIQUE = "bd_ai_memory_analysis_v1"
        private const val KEY_ARCHIVE = "archive_requested"
    }
}

@Singleton
class DefaultMemoryAnalysisSources @Inject constructor(
    private val observeJournals: ObserveJournalsUseCase,
    private val observeSeasons: ObserveLifeSeasonsUseCase,
    private val scheduler: MemoryAnalysisScheduler
) : MemoryAnalysisSources {
    override suspend fun journals(): List<JournalEntry> = observeJournals().first()
    override suspend fun seasonTitles(): Map<String, String> = observeSeasons().first().associate { it.id to it.title }
    override fun archiveRequested(): Boolean = scheduler.archiveRequested()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemoryAnalysisEntryPoint {
    fun memoryAnalysisProcessor(): MemoryAnalysisProcessor
    fun memoryAnalysisScheduler(): MemoryAnalysisScheduler
}

/**
 * Bir çalışmada en çok 4 parti (80 anı) işler; kalan varsa kendini yeniden sıraya koyar.
 * Bağlantı/yoğunluk/günlük sınırda WorkManager geri çekilme süresiyle tekrar dener.
 */
class MemoryAnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, MemoryAnalysisEntryPoint::class.java)
        val processor = entry.memoryAnalysisProcessor()
        repeat(MAX_BATCHES) {
            val r = runCatching { processor.processBatch(limit = BATCH) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                return Result.retry()
            }
            when (r.stop) {
                AnalysisStop.NOT_ALLOWED -> return Result.success()
                AnalysisStop.RETRY_LATER -> return Result.retry()
                AnalysisStop.NONE -> if (r.remaining == 0 || r.processed == 0) return Result.success()
            }
        }
        // Parti sınırı doldu ama iş bitmedi: kısa bir aradan sonra devam.
        entry.memoryAnalysisScheduler().requestSoon()
        return Result.success()
    }

    companion object {
        private const val MAX_BATCHES = 4
        private const val BATCH = 20
    }
}
