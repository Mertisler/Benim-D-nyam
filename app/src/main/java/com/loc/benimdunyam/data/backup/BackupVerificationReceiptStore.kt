package com.loc.benimdunyam.data.backup

import android.content.Context
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.isolation.WorkspaceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yedek dosyası değil, geçmişteki başarılı incelemenin KÜÇÜK tarih kaydı.
 * URI, dosya adı, parola, hash veya çözülen anı içeriği saklanmaz.
 * Kaydın varlığı yedek dosyasının hâlâ erişilebilir olduğunu KANITLAMAZ.
 */
data class LastBackupVerification(
    val normalCreatedAt: Long? = null,
    val normalCheckedAt: Long? = null,
    val mediaCreatedAt: Long? = null,
    val mediaCheckedAt: Long? = null
)

@Singleton
class BackupVerificationReceiptStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ActiveWorkspaceSession
) {
    private val prefs = context.getSharedPreferences("bd_backup_verification_v1", Context.MODE_PRIVATE)

    /** Çalışma alanları karışmasın diye kimlik yalnızca anahtar üretilirken kullanılır. */
    private fun prefix(): String {
        check(!session.needsRestart()) { "Hesap alanı değişti; uygulamayı yeniden aç." }
        val owner = when (session.mode) {
            WorkspaceMode.ACCOUNT -> "account:${session.accountUid ?: error("Hesap yok") }"
            WorkspaceMode.LEGACY -> "legacy"
            WorkspaceMode.LOCKED -> error("Arşiv kilitli")
        }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(owner.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "w_${hash}_"
    }

    fun load(): LastBackupVerification {
        val p = prefix()
        return LastBackupVerification(
            normalCreatedAt = prefs.getLong(p + "normal_created", 0L).takeIf { it > 0 },
            normalCheckedAt = prefs.getLong(p + "normal_checked", 0L).takeIf { it > 0 },
            mediaCreatedAt = prefs.getLong(p + "media_created", 0L).takeIf { it > 0 },
            mediaCheckedAt = prefs.getLong(p + "media_checked", 0L).takeIf { it > 0 }
        )
    }

    fun saveNormal(createdAt: Long, inspectedAt: Long) {
        val p = prefix()
        check(createdAt > 0 && inspectedAt > 0)
        check(prefs.edit().putLong(p + "normal_created", createdAt)
            .putLong(p + "normal_checked", inspectedAt).commit()) {
            "Yedek doğrulama geçmişi kaydedilemedi."
        }
    }

    fun saveMedia(createdAt: Long, inspectedAt: Long) {
        val p = prefix()
        check(createdAt > 0 && inspectedAt > 0)
        check(prefs.edit().putLong(p + "media_created", createdAt)
            .putLong(p + "media_checked", inspectedAt).commit()) {
            "Yedek doğrulama geçmişi kaydedilemedi."
        }
    }
}
