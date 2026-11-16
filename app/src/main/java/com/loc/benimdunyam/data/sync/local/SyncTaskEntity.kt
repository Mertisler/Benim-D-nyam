package com.loc.benimdunyam.data.sync.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Senkronizasyon hazırlık kuyruğundaki kalıcı iş kaydı.
 *
 * Faz 2F.1'de YALNIZCA DIAGNOSTIC işleri oluşturulur. Bu kayıtların içinde
 * günlük metni, fotoğraf, şifre veya Firebase token'ı kesinlikle bulunmaz.
 * İşlerin ait olduğu UID ayrıca saklanır; kuyruk dosyası da hesaba özeldir.
 */
@Entity(
    tableName = "sync_tasks",
    indices = [Index(value = ["ownerUid", "state"])]
)
data class SyncTaskEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val taskType: String,
    val state: String,
    val createdAtMillis: Long,
    val finishedAtMillis: Long? = null
)
