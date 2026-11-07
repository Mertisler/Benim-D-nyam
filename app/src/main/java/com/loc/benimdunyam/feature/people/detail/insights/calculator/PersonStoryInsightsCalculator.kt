package com.loc.benimdunyam.feature.people.detail.insights.calculator

import com.loc.benimdunyam.feature.people.detail.insights.model.PersonStoryInsightsReport
import com.loc.benimdunyam.feature.people.detail.insights.model.StoryInsightPeakMonth
import com.loc.benimdunyam.feature.people.detail.insights.model.StoryInsightPeakSeason
import com.loc.benimdunyam.feature.people.detail.insights.model.StoryInsightPeakYear
import com.loc.benimdunyam.feature.people.detail.insights.model.StoryInsightRevisitMemory
import com.loc.benimdunyam.feature.people.detail.insights.model.StoryInsightTheme
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import com.loc.benimdunyam.feature.people.detail.model.PersonSpecialMemoryItem
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * An entirely OFFLINE, rule-based insight engine.
 * - No API, network, AI model or private text upload.
 * - Every recognized theme is supported by matching text from saved journal entries.
 * - Counts distinct journals, not word occurrences.
 * - Completely deterministic for the same input and `nowMillis`.
 * - Does not infer feelings, personal traits or relationship quality.
 */
object PersonStoryInsightsCalculator {

    private val tr = Locale.forLanguageTag("tr-TR")
    private val wordRegex = Regex("[\\p{L}]+")
    private const val OLD_MEMORY_DAYS = 90L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private data class Topic(
        val id: String,
        val title: String,
        val emoji: String,
        /** Exact word starts, including Turkish suffix forms. */
        val stems: Set<String>
    )

    private val dictionary = listOf(
        Topic("study", "Okul ve öğrenme", "🎓", setOf(
            "üniversite", "kampüs", "okul", "sınav", "ders", "mezuniyet", "ödev"
        )),
        Topic("travel", "Yolculuklar", "🧳", setOf(
            "seyahat", "yolculuk", "tatil", "gezi", "kamp", "otobüs", "uçak", "tren"
        )),
        Topic("friends", "Arkadaşlık ve buluşmalar", "🤝", setOf(
            "arkadaş", "dost", "sohbet", "buluş", "tanış"
        )),
        Topic("celebration", "Kutlamalar ve özel günler", "🎉", setOf(
            "kutlama", "doğum", "düğün", "bayram", "nişan", "parti"
        )),
        Topic("family", "Aile", "🏡", setOf(
            "aile", "annem", "annemle", "babam", "babamla", "kardeş", "kuzen", "akraba"
        )),
        Topic("work", "İş ve projeler", "💼", setOf(
            "çalışma", "çalışt", "mesai", "staj", "kariyer", "proje", "ofis", "toplantı"
        )),
        Topic("nature", "Doğa ve açık hava", "🌿", setOf(
            "orman", "deniz", "sahil", "yürüyüş", "doğa", "piknik", "göl", "dağ"
        )),
        Topic("culture", "Sanat ve kültür", "🎭", setOf(
            "sinema", "film", "tiyatro", "konser", "müze", "kitap", "müzik"
        ))
    )

    fun calculate(
        appearances: List<PersonAppearance>,
        specialMemories: List<PersonSpecialMemoryItem>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PersonStoryInsightsReport {
        // A malformed or duplicated person-mention record must never inflate counts.
        val unique = appearances.distinctBy { it.journal.id }
        if (unique.isEmpty()) return PersonStoryInsightsReport()

        val validIds = unique.map { it.journal.id }.toHashSet()
        val markedCount = specialMemories.asSequence()
            .map { it.journal.id }
            .filter { it in validIds }
            .distinct()
            .count()

        val years = unique.groupBy { localYear(it.journal.entryDate, timeZone) }
        val peakYear = years.entries
            .sortedWith(compareByDescending<Map.Entry<Int, List<PersonAppearance>>> { it.value.size }
                .thenByDescending { it.key })
            .firstOrNull()
            ?.let { StoryInsightPeakYear(it.key, it.value.size) }

        val months = unique.groupBy {
            val calendar = calendarAt(it.journal.entryDate, timeZone)
            calendar.get(Calendar.YEAR) to (calendar.get(Calendar.MONTH) + 1)
        }
        val peakMonth = months.entries
            .sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, List<PersonAppearance>>> { it.value.size }
                .thenByDescending { it.key.first }.thenByDescending { it.key.second })
            .firstOrNull()
            ?.let { StoryInsightPeakMonth(it.key.first, it.key.second, it.value.size) }

        // A season can be resolved by the existing PersonProfileCalculator's
        // explicit ID / episode / date-matching rules; no new inference is performed here.
        val peakSeason = unique.filter { it.seasonId != null }
            .groupBy { it.seasonId!! }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<PersonAppearance>>> { it.value.size }
                .thenBy { it.key })
            .firstOrNull()
            ?.let { (seasonId, records) ->
                StoryInsightPeakSeason(
                    seasonId = seasonId,
                    seasonTitle = records.firstNotNullOfOrNull { it.seasonTitle }
                        ?: "Yaşam dönemi",
                    journalCount = records.size
                )
            }

        val journalWords = unique.map { appearance ->
            val words = wordRegex.findAll(
                appearance.journal.title + " " + appearance.journal.content
            ).map { it.value.lowercase(tr) }.toSet()
            appearance.journal.id to words
        }

        val themes = dictionary.mapNotNull { topic ->
            val journalIds = journalWords.asSequence()
                .filter { (_, words) ->
                    words.any { word ->
                        topic.stems.any { stem -> word.startsWith(stem) }
                    }
                }
                .map { it.first }
                .toList()
            if (journalIds.isEmpty()) null
            else StoryInsightTheme(
                id = topic.id,
                title = topic.title,
                emoji = topic.emoji,
                journalCount = journalIds.size,
                journalIds = journalIds
            )
        }.sortedWith(compareByDescending<StoryInsightTheme> { it.journalCount }.thenBy { it.title })
            .take(5)

        val cutoff = nowMillis - OLD_MEMORY_DAYS * DAY_MS
        val revisits = unique.asSequence()
            .filter { it.journal.entryDate <= cutoff }
            .sortedWith(compareBy<PersonAppearance> { it.journal.entryDate }
                .thenBy { it.journal.id })
            .map {
                StoryInsightRevisitMemory(
                    journalId = it.journal.id,
                    title = it.journal.title.ifBlank { "Başlıksız anı" },
                    entryDate = it.journal.entryDate,
                    seasonTitle = it.seasonTitle
                )
            }
            .take(20)
            .toList()

        return PersonStoryInsightsReport(
            totalJournalCount = unique.size,
            specialJournalCount = markedCount,
            activeYearCount = years.size,
            themes = themes,
            peakYear = peakYear,
            peakMonth = peakMonth,
            peakSeason = peakSeason,
            revisitMemories = revisits
        )
    }

    private fun localYear(epochMillis: Long, timeZone: TimeZone): Int =
        calendarAt(epochMillis, timeZone).get(Calendar.YEAR)

    private fun calendarAt(epochMillis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone, tr).apply { timeInMillis = epochMillis }
}
