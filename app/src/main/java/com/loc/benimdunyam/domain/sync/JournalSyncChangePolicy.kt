package com.loc.benimdunyam.domain.sync

/**
 * Günlük değişikliği kaydının saf Kotlin kuralları.
 *
 * Firebase / Room bağımlılığı bulunmadığı için birim testinde doğrudan
 * doğrulanabilir. Aynı anı üst üste değiştiğinde en SON işlem türü korunur.
 */
object JournalSyncChangePolicy {
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    /** Faz C: diğer cihaz aynı tabandan düzenlemiş; kullanıcı kararı bekler, gönderilmez. */
    const val CONFLICT = "CONFLICT"

    fun requireChangeType(type: String): String {
        require(type == UPSERT || type == DELETE) { "Bilinmeyen anı işlemi: $type" }
        return type
    }

    /** Aynı anıya ait yeni değişikliğin sürümü kesinlikle ileri gider. */
    fun nextVersion(previous: Long?): Long {
        require(previous == null || previous in 1 until Long.MAX_VALUE) {
            "Anı değişiklik sürümü geçersiz veya sınırı aştı."
        }
        return (previous ?: 0L) + 1L
    }

    /**
     * Bağlı hesap yoksa yerel günlük yine saklanır ancak bulut kuyruğuna
     * UID'si belirsiz bir iş eklenmez. İlk yükleme için ayrıca kullanıcı
     * onaylı başlangıç aktarımı (bootstrap) gerekecektir.
     */
    fun shouldQueue(ownerUid: String?): Boolean = !ownerUid.isNullOrBlank()
}
