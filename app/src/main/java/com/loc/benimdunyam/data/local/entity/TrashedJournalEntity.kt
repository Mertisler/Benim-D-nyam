package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Faz B — "Son silinenler". Silinen bir anının geri getirilebilmesi için
 * tam anlık görüntüsü (metin, kişi etiketleri, unutulmaz anılar, ruh hali).
 *
 * Anı ana tablodan gerçekten kaldırılır; böylece listeler, arama ve analizler
 * değişmeden çalışır. Fotoğraf dosyaları bu süre boyunca yerinde kalır.
 * [expiresAt] geçince kayıt ve fotoğrafları kalıcı olarak silinir.
 *
 * Bu tablo .bdbackup yedeğine dahil DEĞİLDİR: çöp kutusu geçici bir güvencedir.
 */
@Entity(
    tableName = "trashed_journals",
    indices = [Index(value = ["expiresAt"])]
)
data class TrashedJournalEntity(
    @PrimaryKey val journalId: String,
    val ownerUid: String?,
    /** [TrashReason] adı. */
    val reason: String,
    val trashedAt: Long,
    val expiresAt: Long,
    /** Uzak silmede buluttaki DELETE olayının sürümü; geri getirmede aşılması gerekir. */
    val remoteRevision: Long?,
    /** Listede göstermek için; anlık görüntüyü açmadan. */
    val title: String,
    /** [com.loc.benimdunyam.data.trash.TrashedJournalSnapshot] JSON'u. */
    val snapshotJson: String
)

enum class TrashReason {
    /** Başka cihazda silindi ve bu cihazda değiştirilmemişti. */
    REMOTE_DELETE
}
