package com.loc.benimdunyam.data.backup

import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity

data class BackupData(
    val version: Int = 1,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val journals: List<JournalEntryEntity>,
    val people: List<PersonEntity>,
    val mentions: List<JournalPersonMentionEntity>
)