package com.loc.benimdunyam.feature.yearreview

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeSeason
import java.util.Calendar
import java.util.Locale

/** Anı sayıları gerçektir; bir kişinin aynı anıdaki tekrarları bir kez sayılır. */
data class YearReviewPerson(
    val id: String,
    val name: String,
    val journalCount: Int
)

data class YearReviewSeason(
    val id: String?,
    val title: String,
    val journalCount: Int
)

/** Tek bir ayın gerçek anı kayıtlarından türetilen, kalıcı veri gerektirmeyen özeti. */
data class YearReviewMonthStory(
    val monthIndex: Int, // 0 = Ocak, 11 = Aralık
    val journalCount: Int,
    val uniquePeopleCount: Int,
    val topPeople: List<YearReviewPerson>,
    val firstJournal: JournalEntry?,
    val lastJournal: JournalEntry?,
    val coverCandidates: List<JournalEntry>,
    val storyYear: Int? = null
)

data class YearReviewReport(
    val year: Int,
    val journalCount: Int,
    val monthlyCounts: List<Int>,
    val activeMonthCount: Int,
    val busiestMonthIndex: Int?,
    val uniquePeopleCount: Int,
    val topPeople: List<YearReviewPerson>,
    val seasonCount: Int,
    val topSeasons: List<YearReviewSeason>,
    val firstJournal: JournalEntry?,
    val lastJournal: JournalEntry?,
    val selectedMemories: List<JournalEntry>,
    val coverCandidates: List<JournalEntry>,
    val monthlyStories: List<YearReviewMonthStory> = emptyList(),
    /** null = yıl özeti; dolu = seçilen yaşam döneminin filmi. */
    val seasonId: String? = null,
    val seasonTitle: String? = null
)

/** Android framework'ünden bağımsız hesaplayıcı; kullanıcı cihazının yerel takvimi kullanılır. */
object YearReviewCalculator {
    private val turkish = Locale.forLanguageTag("tr-TR")

    fun availableYears(journals: List<JournalEntry>, currentYear: Int): List<Int> =
        (journals.map { localYear(it.entryDate) } + currentYear)
            .distinct()
            .sortedDescending()

    fun calculate(
        year: Int,
        journals: List<JournalEntry>,
        seasons: List<LifeSeason>
    ): YearReviewReport {
        val selected = journals.asSequence()
            .filter { localYear(it.entryDate) == year }
            .distinctBy { it.id }
            .sortedWith(compareBy<JournalEntry> { it.entryDate }.thenBy { it.id })
            .toList()
        return calculateEntries(year, selected, seasons)
    }

    /** Dönem kimliğine göre filtreler; tarih başka takvim yılına taşsa da anıyı kaybetmez. */
    fun calculateForSeason(season: LifeSeason, journals: List<JournalEntry>): YearReviewReport {
        val selected = journals.asSequence()
            .filter { it.seasonId == season.id }
            .distinctBy { it.id }
            .sortedWith(compareBy<JournalEntry> { it.entryDate }.thenBy { it.id })
            .toList()
        val startYear = localYear(season.startDate)
        return calculateEntries(startYear, selected, listOf(season), seasonSpecific = true).copy(
            seasonId = season.id,
            seasonTitle = season.title
        )
    }

    private fun calculateEntries(
        year: Int,
        selected: List<JournalEntry>,
        seasons: List<LifeSeason>,
        seasonSpecific: Boolean = false
    ): YearReviewReport {
        val months = MutableList(12) { 0 }
        selected.forEach { months[localMonth(it.entryDate)]++ }
        val busiestIndex = months.withIndex()
            .filter { it.value > 0 }
            .maxWithOrNull(compareBy<IndexedValue<Int>> { it.value }.thenBy { -it.index })
            ?.index

        val peopleById = linkedMapOf<String, YearReviewPerson>()
        selected.forEach { journal ->
            journal.mentionedPeople.distinctBy { it.id }.forEach { person ->
                val old = peopleById[person.id]
                peopleById[person.id] = YearReviewPerson(
                    id = person.id,
                    name = person.name,
                    journalCount = (old?.journalCount ?: 0) + 1
                )
            }
        }
        val sortedPeople = peopleById.values.sortedWith(
            compareByDescending<YearReviewPerson> { it.journalCount }
                .thenBy { it.name.lowercase(turkish) }
                .thenBy { it.id }
        )

        val seasonNames = seasons.associateBy { it.id }
        val countedSeasons = selected.groupBy { it.seasonId }
            .map { (id, entries) ->
                YearReviewSeason(
                    id = id,
                    title = if (id == null) "Sezonsuz Anılar" else seasonNames[id]?.title ?: "Diğer Sezon",
                    journalCount = entries.size
                )
            }.sortedWith(compareByDescending<YearReviewSeason> { it.journalCount }
                .thenBy { it.title.lowercase(turkish) })

        // Tam olarak 12 ay: anı olmayan ayların sayısı sıfırdır; sahte anı üretilmez.
        // Bir kişiyi aynı günlükte tekrar etiketlemek yalnızca bir kez sayılır.
        val byPeriod = selected.groupBy { localYear(it.entryDate) to localMonth(it.entryDate) }
        val monthKeys: List<Pair<Int, Int>> = if (seasonSpecific) {
            byPeriod.keys.sortedWith(compareBy({ it.first }, { it.second }))
        } else {
            (0..11).map { year to it }
        }
        val monthlyStories = monthKeys.map { (storyYear, monthIndex) ->
            val entries = byPeriod[storyYear to monthIndex].orEmpty()
            val monthlyPeople = linkedMapOf<String, YearReviewPerson>()
            entries.forEach { journal ->
                journal.mentionedPeople.distinctBy { it.id }.forEach { person ->
                    val current = monthlyPeople[person.id]
                    monthlyPeople[person.id] = YearReviewPerson(
                        id = person.id,
                        name = person.name,
                        journalCount = (current?.journalCount ?: 0) + 1
                    )
                }
            }
            val leadingPeople = monthlyPeople.values.sortedWith(
                compareByDescending<YearReviewPerson> { it.journalCount }
                    .thenBy { it.name.lowercase(turkish) }
                    .thenBy { it.id }
            )
            YearReviewMonthStory(
                monthIndex = monthIndex,
                journalCount = entries.size,
                uniquePeopleCount = leadingPeople.size,
                topPeople = leadingPeople.take(3),
                firstJournal = entries.firstOrNull(),
                lastJournal = entries.lastOrNull(),
                coverCandidates = entries.asReversed().take(3),
                storyYear = storyYear
            )
        }

        val memoryIndexes = when (selected.size) {
            0 -> emptyList()
            1 -> listOf(0)
            2 -> listOf(0, 1)
            else -> listOf(0, selected.size / 2, selected.lastIndex)
        }

        return YearReviewReport(
            year = year,
            journalCount = selected.size,
            monthlyCounts = months,
            activeMonthCount = months.count { it > 0 },
            busiestMonthIndex = busiestIndex,
            uniquePeopleCount = sortedPeople.size,
            topPeople = sortedPeople.take(5),
            seasonCount = countedSeasons.count { it.id != null },
            topSeasons = countedSeasons.take(4),
            firstJournal = selected.firstOrNull(),
            lastJournal = selected.lastOrNull(),
            selectedMemories = memoryIndexes.map { selected[it] }.distinctBy { it.id },
            coverCandidates = selected.asReversed().take(48),
            monthlyStories = monthlyStories
        )
    }

    private fun localYear(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

    private fun localMonth(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.MONTH)
}
