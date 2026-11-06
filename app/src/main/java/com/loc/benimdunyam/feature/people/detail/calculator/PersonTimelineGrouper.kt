package com.loc.benimdunyam.feature.people.detail.calculator

import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import com.loc.benimdunyam.feature.people.detail.model.PersonTimelineSeasonGroup
import java.util.Calendar

/**
 * Pure helpers for preparing the chronological person timeline.
 * This class has no Android or Compose dependencies.
 */
object PersonTimelineGrouper {

    private const val UNASSIGNED_SEASON_KEY = "__unassigned_season__"

    fun availableYears(appearances: List<PersonAppearance>): List<Int> {
        return appearances
            .map { appearance -> yearOf(appearance.journal.entryDate) }
            .distinct()
            .sortedDescending()
    }

    fun filterByYear(
        appearances: List<PersonAppearance>,
        year: Int?
    ): List<PersonAppearance> {
        return appearances
            .asSequence()
            .filter { appearance ->
                year == null || yearOf(appearance.journal.entryDate) == year
            }
            .sortedWith(
                compareBy<PersonAppearance> { it.journal.entryDate }
                    .thenBy { it.journal.createdAt }
                    .thenBy { it.journal.id }
            )
            .toList()
    }

    /**
     * Groups chronological entries by season while preserving each group's
     * chronological order and sorting groups by their first appearance.
     */
    fun groupBySeason(
        appearances: List<PersonAppearance>
    ): List<PersonTimelineSeasonGroup> {
        val chronological = filterByYear(appearances, year = null)

        return chronological
            .groupBy { appearance ->
                appearance.seasonId ?: UNASSIGNED_SEASON_KEY
            }
            .map { (key, groupAppearances) ->
                val seasonId = groupAppearances.firstOrNull()?.seasonId
                val seasonTitle = groupAppearances
                    .firstNotNullOfOrNull(PersonAppearance::seasonTitle)
                    ?: "Yaşam dönemi belirtilmemiş"

                PersonTimelineSeasonGroup(
                    key = key,
                    seasonId = seasonId,
                    seasonTitle = seasonTitle,
                    appearances = groupAppearances
                )
            }
            .sortedWith(
                compareBy<PersonTimelineSeasonGroup> { group ->
                    group.appearances.firstOrNull()?.journal?.entryDate
                        ?: Long.MAX_VALUE
                }.thenBy { it.seasonTitle }
            )
    }

    private fun yearOf(timestamp: Long): Int {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
        }.get(Calendar.YEAR)
    }
}
