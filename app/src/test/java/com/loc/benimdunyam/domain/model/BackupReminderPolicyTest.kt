package com.loc.benimdunyam.domain.model

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aşama 3: "yeni yedek al" hatırlatması yalnızca gerçekten gerektiğinde çıkar. */
class BackupReminderPolicyTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_000 * day

    @Test
    fun hicYedekYoksaVeAniVarsaHatirlatir() {
        assertTrue(BackupReminderPolicy.shouldRemind(null, journalCount = 3, now = now))
    }

    @Test
    fun aniYoksaHatirlatmaz() {
        assertFalse(BackupReminderPolicy.shouldRemind(null, journalCount = 0, now = now))
    }

    @Test
    fun yeniYedekVarsaHatirlatmaz() {
        assertFalse(BackupReminderPolicy.shouldRemind(now - 3 * day, journalCount = 10, now = now))
        assertFalse(BackupReminderPolicy.shouldRemind(now - 14 * day + 1, journalCount = 10, now = now))
    }

    @Test
    fun ondortGunuGecenYedekIcinHatirlatir() {
        assertTrue(BackupReminderPolicy.shouldRemind(now - 14 * day, journalCount = 10, now = now))
        assertTrue(BackupReminderPolicy.shouldRemind(now - 60 * day, journalCount = 10, now = now))
    }
}
