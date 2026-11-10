package com.loc.benimdunyam.feature.yearreview.mood

import com.loc.benimdunyam.domain.model.JournalEntry
import java.util.Calendar

/** Yalnızca gerçekten kaydedilen ruh halleri sayılır; boş olanlar tahmin edilmez. */
data class YearMoodCount(
    val name: String,
    val total: Int,
    val months: List<Int>
)

data class YearMoodReport(
    val year: Int,
    val journalCount: Int,
    val taggedJournalCount: Int,
    val untaggedJournalCount: Int,
    val monthlyJournalCounts: List<Int>,
    val monthlyTaggedCounts: List<Int>,
    val moods: List<YearMoodCount>
) {
    val dominantMood: YearMoodCount? get() = moods.firstOrNull()
}

object YearMoodCalculator {
    fun calculate(
        year: Int,
        journals: List<JournalEntry>,
        moodNamesByJournalId: Map<String, String>
    ): YearMoodReport {
        val yearEntries = journals.asSequence()
            .distinctBy { it.id }
            .filter { localYear(it.entryDate) == year }
            .toList()

        val monthlyAll = MutableList(12) { 0 }
        val monthlyTagged = MutableList(12) { 0 }
        val moodMonths = linkedMapOf<String, MutableList<Int>>()

        for (journal in yearEntries) {
            val monthIndex = localMonth(journal.entryDate)
            monthlyAll[monthIndex]++
            val name = moodNamesByJournalId[journal.id]?.takeIf { it.isNotBlank() }
                ?: continue
            monthlyTagged[monthIndex]++
            moodMonths.getOrPut(name) { MutableList(12) { 0 } }[monthIndex]++
        }

        val counts = moodMonths.map { (name, months) ->
            YearMoodCount(name, months.sum(), months.toList())
        }.sortedWith(compareByDescending<YearMoodCount> { it.total }.thenBy { it.name })

        val tagged = monthlyTagged.sum()
        return YearMoodReport(
            year = year,
            journalCount = yearEntries.size,
            taggedJournalCount = tagged,
            untaggedJournalCount = yearEntries.size - tagged,
            monthlyJournalCounts = monthlyAll.toList(),
            monthlyTaggedCounts = monthlyTagged.toList(),
            moods = counts
        )
    }

    private fun localYear(time: Long): Int =
        Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)

    private fun localMonth(time: Long): Int =
        Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.MONTH)
}
