package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Faz 2F.7: Ana Room'a anı ve ilişkileri EKLENİRKEN aynı transaction içinde
 * oluşturulan tamamlanmamış ruh hali işareti.
 *
 * Ruh halleri farklı bir Room dosyasında bulunduğundan iki veritabanı arasında
 * tek transaction kurulamaz. Bu satır uygulama kapanırsa kurtarma için kalır.
 * Bu kayıt hiçbir zaman uzaktan gelen mevcut anının üzerine yazma izni değildir.
 */
@Entity(
    tableName = "pending_mood_merges",
    indices = [Index(value = ["ownerUid"])]
)
data class PendingMoodMergeEntity(
    @PrimaryKey val journalId: String,
    val ownerUid: String,
    val moodName: String,
    val moodUpdatedAt: Long,
    val remoteRevision: Long,
    val remoteChangedAt: Long
)
