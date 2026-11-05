package com.loc.benimdunyam.feature.people.detail.calculator

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import com.loc.benimdunyam.feature.people.detail.model.PersonEpisodeStat
import com.loc.benimdunyam.feature.people.detail.model.PersonProfileBadge
import com.loc.benimdunyam.feature.people.detail.model.PersonProfileStats
import com.loc.benimdunyam.feature.people.detail.model.PersonSeasonStat
import java.util.Calendar

/**
 * Pure calculations for a person profile.
 * This class has no Android, Room, or Compose dependencies and is easy to unit test.
 */
object PersonProfileCalculator {

    fun buildAppearances(
        journals: List<JournalEntry>,
        seasons: List<LifeSeason>,
        episodes: List<LifeEpisode>
    ): List<PersonAppearance> {
        val seasonsById = seasons.associateBy(LifeSeason::id)
        val episodesById = episodes.associateBy(LifeEpisode::id)

        return journals.map { journal ->
            val explicitSeason = journal.seasonId?.let(seasonsById::get)
            val directEpisode = journal.episodeId?.let(episodesById::get)
            val episodeSeason = directEpisode?.seasonId?.let(seasonsById::get)

            // Legacy journal entries may not have seasonId/episodeId. In that case,
            // use the date range as a best-effort fallback.
            val dateMatchedSeason = seasons
                .asSequence()
                .filter { season ->
                    journal.entryDate >= season.startDate &&
                            (season.endDate == null || journal.entryDate <= season.endDate)
                }
                .maxByOrNull(LifeSeason::startDate)

            val resolvedSeason = explicitSeason ?: episodeSeason ?: dateMatchedSeason

            val validDirectEpisode = directEpisode?.takeIf { episode ->
                resolvedSeason == null || episode.seasonId == resolvedSeason.id
            }

            val dateMatchedEpisode = resolvedSeason?.let { season ->
                episodes
                    .asSequence()
                    .filter { episode ->
                        episode.seasonId == season.id &&
                                journal.entryDate >= episode.startDate &&
                                (episode.endDate == null || journal.entryDate <= episode.endDate)
                    }
                    .maxByOrNull(LifeEpisode::startDate)
            }

            val resolvedEpisode = validDirectEpisode ?: dateMatchedEpisode

            PersonAppearance(
                journal = journal,
                seasonId = resolvedSeason?.id,
                seasonTitle = resolvedSeason?.title,
                episodeId = resolvedEpisode?.id,
                episodeTitle = resolvedEpisode?.title,
                episodeOrderIndex = resolvedEpisode?.orderIndex
            )
        }
    }

    fun calculateProfileStats(
        appearances: List<PersonAppearance>
    ): PersonProfileStats {
        if (appearances.isEmpty()) return PersonProfileStats()

        val chronological = appearances.sortedWith(
            compareBy<PersonAppearance> { it.journal.entryDate }
                .thenBy { it.journal.createdAt }
        )

        val firstAppearance = chronological.firstOrNull()
        val lastAppearance = chronological.lastOrNull()

        val seasonStats = appearances
            .groupBy(PersonAppearance::seasonId)
            .map { (seasonId, seasonAppearances) ->
                val seasonTitle = seasonAppearances.firstNotNullOfOrNull(PersonAppearance::seasonTitle)
                    ?: "Yaşam dönemi belirtilmemiş"

                val episodeStats = seasonAppearances
                    .groupBy(PersonAppearance::episodeId)
                    .map { (episodeId, episodeAppearances) ->
                        PersonEpisodeStat(
                            episodeId = episodeId,
                            episodeTitle = episodeAppearances.firstNotNullOfOrNull(PersonAppearance::episodeTitle)
                                ?: "Bölüm belirtilmemiş",
                            memoryCount = episodeAppearances.size,
                            orderIndex = episodeAppearances.firstNotNullOfOrNull(PersonAppearance::episodeOrderIndex),
                            seasonTitle = seasonTitle
                        )
                    }
                    .sortedWith(
                        compareByDescending<PersonEpisodeStat> { it.memoryCount }
                            .thenBy { it.orderIndex ?: Int.MAX_VALUE }
                    )

                PersonSeasonStat(
                    seasonId = seasonId,
                    seasonTitle = seasonTitle,
                    memoryCount = seasonAppearances.size,
                    episodeStats = episodeStats
                )
            }
            .sortedWith(
                compareByDescending<PersonSeasonStat> { it.memoryCount }
                    .thenBy { it.seasonTitle }
            )

        val distinctSeasonCount = appearances.mapNotNull(PersonAppearance::seasonId).distinct().size
        val distinctEpisodeCount = appearances.mapNotNull(PersonAppearance::episodeId).distinct().size

        val topSeason = seasonStats
            .filter { it.seasonId != null }
            .maxByOrNull(PersonSeasonStat::memoryCount)

        val topEpisode = appearances
            .filter { it.episodeId != null }
            .groupBy(PersonAppearance::episodeId)
            .map { (episodeId, episodeAppearances) ->
                PersonEpisodeStat(
                    episodeId = episodeId,
                    episodeTitle = episodeAppearances.firstNotNullOfOrNull(PersonAppearance::episodeTitle)
                        ?: "Bölüm",
                    memoryCount = episodeAppearances.size,
                    orderIndex = episodeAppearances.firstNotNullOfOrNull(PersonAppearance::episodeOrderIndex),
                    seasonTitle = episodeAppearances.firstNotNullOfOrNull(PersonAppearance::seasonTitle)
                )
            }
            .maxByOrNull(PersonEpisodeStat::memoryCount)

        val distinctYears = appearances
            .map { appearance ->
                Calendar.getInstance().apply {
                    timeInMillis = appearance.journal.entryDate
                }.get(Calendar.YEAR)
            }
            .distinct()
            .size

        val badges = createBadges(
            totalMemoryCount = appearances.size,
            seasonCount = distinctSeasonCount,
            episodeCount = distinctEpisodeCount,
            activeYearCount = distinctYears,
            topSeason = topSeason
        )

        return PersonProfileStats(
            totalMemoryCount = appearances.size,
            seasonCount = distinctSeasonCount,
            episodeCount = distinctEpisodeCount,
            activeYearCount = distinctYears,
            seasonBreakdown = seasonStats,
            topSeason = topSeason,
            topEpisode = topEpisode,
            firstAppearance = firstAppearance,
            lastAppearance = lastAppearance,
            badges = badges
        )
    }

    private fun createBadges(
        totalMemoryCount: Int,
        seasonCount: Int,
        episodeCount: Int,
        activeYearCount: Int,
        topSeason: PersonSeasonStat?
    ): List<PersonProfileBadge> = buildList {
        if (totalMemoryCount in 1..2) {
            add(
                PersonProfileBadge(
                    title = "Gizemli Konuk Oyuncu",
                    description = "Hikâyende henüz az sayıda anıda göründü.",
                    emoji = "🎭"
                )
            )
        }

        if (topSeason != null && topSeason.memoryCount >= 3) {
            add(
                PersonProfileBadge(
                    title = "Sezonun Başrol Yan Karakteri",
                    description = "${topSeason.seasonTitle} döneminde ${topSeason.memoryCount} anıda yer aldı.",
                    emoji = "🎬"
                )
            )
        }

        // This is a playful archive badge, not an assessment of relationship quality.
        if (totalMemoryCount >= 8 && seasonCount >= 2 && activeYearCount >= 2) {
            add(
                PersonProfileBadge(
                    title = "En Sadık Dost",
                    description = "En az iki farklı yıla ve sezona yayılan anılarda yer aldı.",
                    emoji = "🏆"
                )
            )
        }

        if (activeYearCount >= 3) {
            add(
                PersonProfileBadge(
                    title = "Yılların Eşlikçisi",
                    description = "$activeYearCount farklı yılda anılarında yer aldı.",
                    emoji = "⏳"
                )
            )
        }

        if (seasonCount >= 2) {
            add(
                PersonProfileBadge(
                    title = "Sezonlar Arası Karakter",
                    description = "$seasonCount farklı yaşam döneminde yer aldı.",
                    emoji = "🌟"
                )
            )
        }

        if (episodeCount >= 5) {
            add(
                PersonProfileBadge(
                    title = "Bölümden Bölüme",
                    description = "$episodeCount farklı bölümde kendine yer buldu.",
                    emoji = "📺"
                )
            )
        }
    }
}
