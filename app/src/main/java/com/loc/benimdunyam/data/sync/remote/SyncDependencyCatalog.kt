package com.loc.benimdunyam.data.sync.remote

import com.loc.benimdunyam.data.backup.BackupData
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.domain.sync.FullSyncCoveragePolicy
import com.loc.benimdunyam.domain.sync.SyncRelationIndex

/**
 * Günlük metinleri ve fotoğraf baytları dışında kalan ilişkileri taşıyan şifreli katalog.
 * Kullanıcının şifresi olmadan Firestore'da yalnızca şifreli içerik saklanır.
 * .bdbackup biçimi DEĞİŞMEZ; bu, ayrı bir senkronizasyon veri biçimidir.
 */
data class SyncDependencyCatalog(
    val formatVersion: Int,
    val ownerUid: String,
    val capturedAtMillis: Long,
    val journalIds: List<String>,
    val people: List<PersonEntity>,
    val seasons: List<LifeSeasonEntity>,
    val episodes: List<LifeEpisodeEntity>,
    val moods: List<JournalMoodRecord>,
    val mentions: List<JournalPersonMentionEntity>,
    val specialMemories: List<PersonSpecialMemoryEntity>
) {
    /** Cihaz içi fotoğraf yolunu buluta taşımayız: ikinci cihazda bu yollar geçersizdir. */
    companion object {
        fun fromLocal(uid: String, archive: BackupData, moods: List<JournalMoodRecord>): SyncDependencyCatalog {
            // Eksik ruh hallerini sessizce elemek yerine yedeklemeyi durdururuz.
            val journalIds = archive.journals.map { it.id }.toSet()
            require(moods.all { it.journalId in journalIds }) {
                "Yerel ruh hali kayıtlarından birinin anısı bulunamadı."
            }
            return SyncDependencyCatalog(
                formatVersion = 1,
                ownerUid = uid,
                capturedAtMillis = System.currentTimeMillis(),
                journalIds = archive.journals.map { it.id },
                people = archive.people.map { it.copy(profileImagePath = null, coverImagePath = null) },
                seasons = archive.seasons.map { it.copy(coverImagePath = null) },
                episodes = archive.episodes,
                moods = moods,
                mentions = archive.mentions,
                specialMemories = archive.specialMemories
            )
        }
    }

    /** Şifresi çözülse bile kaydın ilişkileri tutarsızsa kullanıma alınmaz. */
    fun validatedIndex(expectedUid: String): SyncRelationIndex {
        require(formatVersion == 1 && ownerUid == expectedUid && capturedAtMillis > 0) {
            "Katalog sürümü veya hesabı uyuşmuyor."
        }
        val journalIdsSet = FullSyncCoveragePolicy.uniqueIds(journalIds) { it }
        val peopleIdsSet = FullSyncCoveragePolicy.uniqueIds(people) { it.id }
        val seasonIdsSet = FullSyncCoveragePolicy.uniqueIds(seasons) { it.id }
        FullSyncCoveragePolicy.uniqueIds(episodes) { it.id }
        FullSyncCoveragePolicy.uniqueIds(moods) { it.journalId }
        val episodeMap = episodes.associate { it.id to it.seasonId }
        require(episodes.all { it.seasonId in seasonIdsSet }) { "Bölümün dönemi bulunamadı." }
        require(moods.all { it.journalId in journalIdsSet }) { "Ruh hali anısı bulunamadı." }
        val pairs = mentions.map { it.journalId to it.personId }
        require(pairs.toSet().size == pairs.size && mentions.all {
            it.journalId in journalIdsSet && it.personId in peopleIdsSet
        }) { "Kişi-anı ilişkileri geçersiz." }
        val memoryPairs = specialMemories.map { it.journalId to it.personId }
        require(memoryPairs.toSet().size == memoryPairs.size && memoryPairs.all { it in pairs.toSet() }) {
            "Öne çıkan anı ilişkileri geçersiz."
        }
        return SyncRelationIndex(journalIdsSet, seasonIdsSet, episodeMap, peopleIdsSet,
            moods.map { it.journalId }.toSet())
    }
}
