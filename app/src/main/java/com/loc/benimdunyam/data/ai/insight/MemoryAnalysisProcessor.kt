package com.loc.benimdunyam.data.ai.insight

import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiException
import com.loc.benimdunyam.data.ai.AiInputHasher
import com.loc.benimdunyam.data.ai.AiMemoryInput
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiTask
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** Analiz için gereken yerel veriler; testlerde sahteyle değiştirilir. */
interface MemoryAnalysisSources {
    suspend fun journals(): List<JournalEntry>
    /** dönem kimliği → başlık */
    suspend fun seasonTitles(): Map<String, String>
    /** Kullanıcı "arşivimi analiz et" dediyse eski anılar da işlenir. */
    fun archiveRequested(): Boolean
}

enum class AnalysisStop {
    /** Bekleyen anı kalmadı ya da parti doldu. */
    NONE,
    /** İzin yok ya da hesap yok: tekrar denemenin anlamı yok. */
    NOT_ALLOWED,
    /** Bağlantı, yoğunluk ya da günlük sınır: daha sonra tekrar denenir. */
    RETRY_LATER
}

data class AnalysisBatchResult(val processed: Int, val remaining: Int, val stop: AnalysisStop)

data class AnalysisProgress(
    val totalJournals: Int,
    val analyzed: Int,
    /** İzinden sonra yazılan/değişen ve henüz işlenmemiş anılar. */
    val pendingAutomatic: Int,
    /** Yalnızca "arşivimi analiz et" ile işlenecek eski anılar. */
    val pendingArchive: Int
)

/**
 * Anı analizi işleyicisi.
 *
 * - İzinden sonra yazılan ya da değiştirilen anılar kendiliğinden işlenir.
 * - Daha eski anılar yalnızca kullanıcı arşiv analizini başlattıysa işlenir.
 * - Anı değişirse (başlık, metin, tarih, kişiler, dönem) yeniden analiz edilir.
 * - Kullanıcının sildiği ya da sunucunun işleyemediği analiz, anı değişene kadar tekrar üretilmez.
 * - Silinen anıların analizleri temizlenir.
 */
@Singleton
class MemoryAnalysisProcessor @Inject constructor(
    private val sources: MemoryAnalysisSources,
    private val store: MemoryInsightStore,
    private val ai: AiRepository,
    private val consent: AiConsentSource
) {
    private data class Candidate(val journal: JournalEntry, val input: AiMemoryInput, val fingerprint: String, val automatic: Boolean)

    private fun dateText(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(millis))

    private fun inputFor(journal: JournalEntry, seasons: Map<String, String>) = AiMemoryInput(
        id = journal.id,
        date = dateText(journal.entryDate),
        title = journal.title.take(200),
        content = journal.content.take(6000),
        people = journal.mentionedPeople.map { it.name.take(80) }.distinct().take(30),
        seasonTitle = journal.seasonId?.let(seasons::get)?.take(120)
    )

    fun fingerprint(input: AiMemoryInput): String =
        AiInputHasher.hash(AiTask.MEMORY_ANALYSIS, mapOf("ani" to input.toWire()))

    private suspend fun candidates(): Pair<List<Candidate>, List<Candidate>> {
        val acceptedAt = consent.consent.value.acceptedAt
        val seasons = sources.seasonTitles()
        val journals = sources.journals().filter { it.title.isNotBlank() || it.content.isNotBlank() }
        val stored = store.getAll().associateBy { it.journalId }

        // Silinen anıların analizlerini temizle.
        val ids = journals.mapTo(HashSet()) { it.id }
        stored.keys.filterNot { it in ids }.takeIf { it.isNotEmpty() }?.let { store.delete(it) }

        val automatic = mutableListOf<Candidate>()
        val archive = mutableListOf<Candidate>()
        for (journal in journals) {
            val input = inputFor(journal, seasons)
            val fp = fingerprint(input)
            val existing = stored[journal.id]
            if (existing != null && existing.fingerprint == fp) continue
            // Daha önce analiz edilmiş ama değişmiş anı ya da izinden sonra yazılmış anı: otomatik.
            val isAutomatic = existing != null || maxOf(journal.createdAt, journal.updatedAt) >= acceptedAt
            val c = Candidate(journal, input, fp, isAutomatic)
            if (isAutomatic) automatic += c else archive += c
        }
        val newestFirst = compareByDescending<Candidate> { maxOf(it.journal.createdAt, it.journal.updatedAt) }
        return automatic.sortedWith(newestFirst) to archive.sortedWith(newestFirst)
    }

    suspend fun progress(): AnalysisProgress {
        val (automatic, archive) = candidates()
        val journals = sources.journals().count { it.title.isNotBlank() || it.content.isNotBlank() }
        val analyzed = store.getAll().count { it.isVisible || it.hidden }
        return AnalysisProgress(journals, analyzed.coerceAtMost(journals), automatic.size, archive.size)
    }

    suspend fun processBatch(limit: Int = 20, now: () -> Long = System::currentTimeMillis): AnalysisBatchResult {
        if (!consent.consent.value.isValid) return AnalysisBatchResult(0, 0, AnalysisStop.NOT_ALLOWED)
        val (automatic, archive) = candidates()
        val queue = automatic + if (sources.archiveRequested()) archive else emptyList()
        var processed = 0
        for (c in queue.take(limit)) {
            val insight = try {
                val r = ai.run(AiTask.MEMORY_ANALYSIS, mapOf("ani" to c.input.toWire()))
                parse(c.journal.id, c.fingerprint, r.json.orEmpty(), r.version, now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                when (e) {
                    is AiException.NotEnabled, is AiException.NoAccount ->
                        return AnalysisBatchResult(processed, queue.size - processed, AnalysisStop.NOT_ALLOWED)
                    is AiException.Invalid, is AiException.Rejected ->
                        MemoryInsight(c.journal.id, c.fingerprint, failed = true, analyzedAt = now())
                    else -> return AnalysisBatchResult(processed, queue.size - processed, AnalysisStop.RETRY_LATER)
                }
            }
            store.upsert(insight)
            processed++
        }
        return AnalysisBatchResult(processed, queue.size - processed, AnalysisStop.NONE)
    }

    companion object {
        private fun tags(value: Any?, max: Int): List<String> =
            (value as? List<*>).orEmpty()
                .mapNotNull { (it as? String)?.trim()?.take(40)?.takeIf(String::isNotEmpty) }
                .distinctBy { it.lowercase(Locale.forLanguageTag("tr-TR")) }
                .take(max)

        fun parse(journalId: String, fingerprint: String, json: Map<String, Any?>, version: String, now: Long) = MemoryInsight(
            journalId = journalId,
            fingerprint = fingerprint,
            topics = tags(json["konular"], 6).map { it.lowercase(Locale.forLanguageTag("tr-TR")) },
            places = tags(json["yerler"], 6),
            events = tags(json["olaylar"], 6),
            moodSuggestion = JournalMood.entries.firstOrNull { it.name == json["ruhHaliOnerisi"] },
            summary = (json["ozet"] as? String)?.trim()?.take(240)?.takeIf(String::isNotEmpty),
            version = version,
            analyzedAt = now
        )
    }
}
