package com.loc.benimdunyam.domain.sync

/**
 * Kuyruk işlemleri için değişmez hesap güvenliği kuralı.
 * Bu saf Kotlin fonksiyon Android ve Firebase olmadan da test edilebilir.
 */
object SyncQueuePolicy {
    fun canAccess(expectedUid: String?, signedInUid: String?, workspaceReady: Boolean): Boolean =
        !expectedUid.isNullOrBlank() &&
                expectedUid == signedInUid &&
                workspaceReady
}
