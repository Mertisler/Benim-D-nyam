package com.loc.benimdunyam.domain.model

import java.util.concurrent.TimeUnit

/**
 * Aşama 3 — "Son yedek: 3 gün önce" ve hatırlatma kuralı.
 *
 * Hatırlatma yalnızca anı varken gösterilir: boş arşiv için yedek istemek anlamsızdır.
 */
object BackupReminderPolicy {

    /** Bu süreden eski yedek için "yeni yedek al" hatırlatması gösterilir. */
    val REMIND_AFTER_MILLIS: Long = TimeUnit.DAYS.toMillis(14)

    fun shouldRemind(lastBackupAt: Long?, journalCount: Int, now: Long): Boolean {
        if (journalCount <= 0) return false
        if (lastBackupAt == null || lastBackupAt <= 0) return true
        return now - lastBackupAt >= REMIND_AFTER_MILLIS
    }
}
