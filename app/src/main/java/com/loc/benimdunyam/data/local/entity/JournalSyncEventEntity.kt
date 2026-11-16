package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Anı değişikliğini ANA Room içinde kalıcı olarak saklayan işlem kaydı.
 *
 * Sadece kayıt kimliği ve işlem türü bulunur; günlük metni, fotoğraf, şifre,
 * erişim anahtarı veya Firebase token'ı bu tabloya KESİNLİKLE yazılmaz.
 *
 * Tek journalId için tek satır vardır. Art arda yapılan düzenlemeler sürümü
 * artırır. Silme işleminde günlük artık mevcut olmasa da DELETE kaydı kalır.
 * Böylece gelecekte sunucudan geri indirilirken yanlışlıkla canlanması önlenebilir.
 *
 * DİKKAT: Henüz hiçbir değişiklik buluta gönderilmez; PENDING kayıtları
 * sonraki senkronizasyon fazına kadar yerel veritabanında bekler.
 */
@Entity(
    tableName = "journal_sync_events",
    indices = [Index(value = ["ownerUid", "state"])]
)
data class JournalSyncEventEntity(
    @PrimaryKey val journalId: String,
    val ownerUid: String,
    val changeType: String,
    val state: String,
    val changeVersion: Long,
    val updatedAtMillis: Long
)
