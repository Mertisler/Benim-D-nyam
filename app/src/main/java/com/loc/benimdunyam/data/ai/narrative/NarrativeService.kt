package com.loc.benimdunyam.data.ai.narrative

import com.loc.benimdunyam.data.ai.AiInputHasher
import com.loc.benimdunyam.data.ai.AiMemoryInput
import com.loc.benimdunyam.data.ai.AiRepository
import com.loc.benimdunyam.data.ai.AiTask
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.ask.AskSources
import com.loc.benimdunyam.feature.ask.CitationFormatter
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import com.loc.benimdunyam.feature.mystory.MyStoryCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Hikâye yazısının kapsamı. [key] kayıt anahtarıdır. */
sealed class NarrativeScope {
    abstract val key: String
    abstract val title: String
    abstract val task: AiTask

    data class Period(val period: LocalAnalyticsPeriod) : NarrativeScope() {
        override val key get() = "period:${period.name}"
        override val title get() = when (period) {
            LocalAnalyticsPeriod.LAST_30_DAYS -> "Son 30 gün"
            LocalAnalyticsPeriod.LAST_12_MONTHS -> "Son 12 ay"
            LocalAnalyticsPeriod.ALL -> "Tüm zamanlar"
        }
        override val task get() = AiTask.PERIOD_SUMMARY
    }

    data class Year(val year: Int) : NarrativeScope() {
        override val key get() = "year:$year"
        override val title get() = "$year yılı"
        override val task get() = AiTask.PERIOD_SUMMARY
    }

    data class Person(val personId: String, val name: String) : NarrativeScope() {
        override val key get() = "person:$personId"
        override val title get() = name
        override val task get() = AiTask.PERSON_STORY
    }
}

data class NarrativeStatus(
    /** Kapsamdaki anı sayısı (gelecek tarihliler hariç). */
    val memoryCount: Int,
    /** Bugünkü anılarla hesaplanan girdi özeti. */
    val currentHash: String?
) {
    val enough: Boolean get() = memoryCount >= NarrativeService.MIN_MEMORIES
}

/**
 * Dönem, yıl ve kişi hikâyesi yazıları.
 *
 * Yazı yalnızca kullanıcı isteyince üretilir ve saklanır. Anılar değişirse saklanan yazı
 * "güncel değil" olarak gösterilir; yenilemek yine kullanıcının elindedir.
 * Çok anılı kapsamlarda en dolu 60 anı seçilir ve metinler toplam sınıra göre kısaltılır.
 */
@Singleton
class NarrativeService @Inject constructor(
    private val ai: AiRepository,
    private val sources: AskSources,
    private val store: NarrativeStore
) {
    private class Prepared(val input: Map<String, Any?>, val hash: String, val count: Int)

    private fun journalsFor(scope: NarrativeScope, all: List<JournalEntry>, now: Long, tz: TimeZone): List<JournalEntry> {
        val past = all.filter { it.entryDate in 1..now && (it.title.isNotBlank() || it.content.isNotBlank()) }
        return when (scope) {
            is NarrativeScope.Period -> {
                val start = MyStoryCalculator.periodStart(scope.period, now, tz)
                past.filter { it.entryDate >= start }
            }
            is NarrativeScope.Year -> past.filter {
                Calendar.getInstance(tz).apply { timeInMillis = it.entryDate }.get(Calendar.YEAR) == scope.year
            }
            is NarrativeScope.Person -> past.filter { j -> j.mentionedPeople.any { it.id == scope.personId } }
        }
    }

    private suspend fun prepare(scope: NarrativeScope, now: Long, tz: TimeZone): Prepared {
        val journals = journalsFor(scope, sources.journals(), now, tz)
        val moods = sources.moods()
        val seasons = sources.seasonTitles()
        val selected = NarrativeInputBuilder.select(journals)
        val budget = NarrativeInputBuilder.contentBudget(selected.size)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val memories = selected.map { j ->
            AiMemoryInput(
                id = j.id,
                date = format.format(Date(j.entryDate)),
                title = j.title.take(200),
                content = j.content.take(budget),
                people = j.mentionedPeople.map { it.name.take(80) }.distinct().take(30),
                seasonTitle = j.seasonId?.let(seasons::get)?.take(120),
                moodName = moods[j.id]
            ).toWire()
        }
        val nameKey = if (scope is NarrativeScope.Person) "kisiAdi" else "donemAdi"
        val input = mapOf(nameKey to scope.title.take(80), "anilar" to memories)
        // "Bugün" her gün değiştiği için güncellik özetine katılmaz.
        return Prepared(input, AiInputHasher.hash(scope.task, input), journals.size)
    }

    suspend fun status(scope: NarrativeScope, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): NarrativeStatus {
        val p = prepare(scope, now, tz)
        return NarrativeStatus(p.count, p.hash.takeIf { p.count > 0 })
    }

    suspend fun generate(scope: NarrativeScope, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): StoredNarrative {
        val p = prepare(scope, now, tz)
        require(p.count >= MIN_MEMORIES) { "Hikâye için en az $MIN_MEMORIES anı gerekir." }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }.format(Date(now))
        val response = ai.run(scope.task, p.input + ("bugun" to today))
        val formatted = CitationFormatter.format(response.text.orEmpty(), response.sources)
        val narrative = StoredNarrative(scope.key, p.hash, formatted.text, formatted.journalIds, response.version, now)
        store.save(narrative)
        return narrative
    }

    companion object {
        const val MIN_MEMORIES = 2
    }
}

/** Kapsam çok büyükse modele gidecek anıları seçer; sunucu sınırlarının altında kalır. */
object NarrativeInputBuilder {
    const val MAX_MEMORIES = 60
    /** Sunucu sınırı 120.000 karakter; başlıklar ve pay için 100.000. */
    const val MAX_TOTAL_CHARS = 100_000
    private const val MAX_PER_MEMORY = 6000

    /** En dolu anılar (uzunluk, kişi sayısı) seçilir, sonra kronolojik sıralanır. */
    fun select(journals: List<JournalEntry>): List<JournalEntry> {
        val chosen = if (journals.size <= MAX_MEMORIES) journals else journals
            .sortedWith(compareByDescending<JournalEntry> { weight(it) }.thenByDescending { it.entryDate })
            .take(MAX_MEMORIES)
        return chosen.sortedWith(compareBy<JournalEntry> { it.entryDate }.thenBy { it.id })
    }

    fun contentBudget(count: Int): Int =
        if (count <= 0) MAX_PER_MEMORY else minOf(MAX_PER_MEMORY, MAX_TOTAL_CHARS / count)

    private fun weight(j: JournalEntry): Int =
        minOf(j.content.length, 2000) / 20 + 15 * j.mentionedPeople.distinctBy { it.id }.size + if (j.title.isNotBlank()) 5 else 0
}
