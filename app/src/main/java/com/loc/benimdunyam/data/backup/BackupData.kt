package com.loc.benimdunyam.data.backup

import com.loc.benimdunyam.data.local.entity.*

data class BackupData(
    val version: Int = 2,
    val exportTimestamp: Long = System.currentTimeMillis(),

    val journals: List<JournalEntryEntity>,
    val people: List<PersonEntity>,
    val mentions: List<JournalPersonMentionEntity>,
    val seasons: List<LifeSeasonEntity>,

    // Yeni eklenen tablolar
    val episodes: List<LifeEpisodeEntity>,
    val specialMemories: List<PersonSpecialMemoryEntity>
)