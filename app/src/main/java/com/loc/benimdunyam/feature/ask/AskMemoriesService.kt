package com.loc.benimdunyam.feature.ask

import com.loc.benimdunyam.data.ai.AiMemoryInput
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiTask
import com.loc.benimdunyam.data.ai.history.AskHistoryStore
import com.loc.benimdunyam.data.ai.history.AskedQuestion
import com.loc.benimdunyam.data.ai.insight.MemoryInsight
import com.loc.benimdunyam.data.ai.insight.MemoryInsightStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Soru için gereken yerel veriler; testlerde sahteyle değiştirilir. */
interface AskSources {
    suspend fun journals(): List<JournalEntry>
    suspend fun moods(): Map<String, String>
    suspend fun seasonTitles(): Map<String, String>
    suspend fun insights(): Map<String, MemoryInsight>
}

@Singleton
class DefaultAskSources @Inject constructor(
    private val observeJournals: ObserveJournalsUseCase,
    private val observeSeasons: ObserveLifeSeasonsUseCase,
    private val moods: JournalMoodRepository,
    private val insights: MemoryInsightStore
) : AskSources {
    override suspend fun journals() = observeJournals().first()
    override suspend fun moods() = moods.observeAll().first().associate { it.journalId to it.moodName }
    override suspend fun seasonTitles() = observeSeasons().first().associate { it.id to it.title }
    override suspend fun insights() = runCatching { insights.getAll().associateBy { it.journalId } }.getOrDefault(emptyMap())
}

enum class AskStep { SEARCHING, WRITING }

data class AskResult(
    val question: String,
    /** [1], [2]… işaretli cevap. */
    val answer: String,
    /** Cevaptaki işaret sırasıyla kaynak anı kimlikleri. */
    val sourceIds: List<String>,
    val notFound: Boolean
)

/**
 * "Anılarına sor" akışı:
 * 1. Soru → filtre (modele yalnızca soru ve kişi adları gider).
 * 2. Cihazda en ilgili anılar seçilir.
 * 3. Yalnızca seçilen anılar gönderilir, kaynaklı cevap alınır.
 * İlgili anı yoksa ikinci istek hiç yapılmaz.
 */
@Singleton
class AskMemoriesService @Inject constructor(
    private val ai: AiRepository,
    private val sources: AskSources,
    private val history: AskHistoryStore
) {
    private fun today(now: Long, tz: TimeZone) =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }.format(Date(now))

    suspend fun ask(
        question: String,
        onStep: (AskStep) -> Unit = {},
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): AskResult {
        val q = question.trim().take(MAX_QUESTION)
        require(q.isNotEmpty()) { "Soru boş olamaz." }
        val bugun = today(now, timeZone)
        onStep(AskStep.SEARCHING)

        val journals = sources.journals()
        // Kişi listesi: anılarda geçen kişiler, en çok geçenden başlayarak.
        val people = journals.flatMap { it.mentionedPeople }
            .groupBy { it.id }
            .map { (id, list) -> Triple(id, list.first().name, list.size) }
            .sortedByDescending { it.third }
            .take(MAX_PEOPLE)
            .map { mapOf("id" to it.first, "ad" to it.second.take(80)) }

        val filterJson = ai.run(
            AiTask.QUESTION_FILTER,
            mapOf("soru" to q, "bugun" to bugun, "kisiler" to people)
        ).json.orEmpty()
        val insights = sources.insights()
        val found = MemoryRetriever.retrieve(journals, QuestionFilter.from(filterJson), insights, timeZone = timeZone)

        val result = if (found.isEmpty()) {
            AskResult(q, NOT_FOUND, emptyList(), notFound = true)
        } else {
            onStep(AskStep.WRITING)
            val moods = sources.moods()
            val seasons = sources.seasonTitles()
            val zone = timeZone
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = zone }
            val memories = found.map { j ->
                AiMemoryInput(
                    id = j.id,
                    date = dateFormat.format(Date(j.entryDate)),
                    title = j.title.take(200),
                    content = j.content.take(6000),
                    people = j.mentionedPeople.map { it.name.take(80) }.distinct().take(30),
                    seasonTitle = j.seasonId?.let(seasons::get)?.take(120),
                    moodName = moods[j.id]
                ).toWire()
            }
            val response = ai.run(AiTask.QUESTION_ANSWER, mapOf("soru" to q, "bugun" to bugun, "anilar" to memories))
            val formatted = CitationFormatter.format(response.text.orEmpty(), response.sources)
            val notFound = formatted.journalIds.isEmpty() &&
                formatted.text.lowercase(Locale.forLanguageTag("tr-TR")).contains("bulamad")
            AskResult(q, formatted.text, formatted.journalIds, notFound)
        }
        runCatching {
            history.add(AskedQuestion(question = q, answer = result.answer, sources = result.sourceIds,
                notFound = result.notFound, askedAt = now))
        }
        return result
    }

    companion object {
        const val MAX_QUESTION = 500
        private const val MAX_PEOPLE = 300
        const val NOT_FOUND = "Bununla ilgili bir anı bulamadım."
    }
}
