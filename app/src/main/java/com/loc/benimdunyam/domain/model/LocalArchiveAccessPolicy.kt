package com.loc.benimdunyam.domain.model

/**
 * Tek bir Room arşivinin hangi oturumda gösterilebileceğini belirler.
 *
 * Bu karar sınıfı yalnızca UID eşitliğini kontrol eder; Firebase'e bağlanmaz,
 * Room'a yazmaz ve yerel dosyaları silmez. Birim testlerinde de kullanılabilir.
 *
 * UYARI: Bu uygulama düzeyindeki erişim kapısıdır. Veritabanını hesap başına
 * fiziksel olarak ayırmak veya dosyaları şifrelemek yerine geçmez.
 */
enum class LocalArchiveAccessDecision {
    /** Kullanıcının oturum durumu henüz belirlenmedi. */
    CHECKING,
    /** Henüz bulut hesabına bağlanmamış misafir arşivine erişim açık. */
    ALLOWED,
    /** Açık Firebase hesabına bağlamak için kullanıcının onayı gerekiyor. */
    CONSENT_REQUIRED,
    /** Arşiv, başka bir hesaba bağlı veya arşiv sahibi oturum açmamış. */
    LOCKED
}

object LocalArchiveAccessPolicy {
    /**
     * Eski Faz 2A/2E arşiv sahibi kaydıyla uyumludur.
     * Sahip yok ve Firebase oturumu yoksa misafir erişimi korunur.
     */
    fun decide(ownerUid: String?, activeUid: String?): LocalArchiveAccessDecision = when {
        ownerUid.isNullOrBlank() && activeUid.isNullOrBlank() -> LocalArchiveAccessDecision.ALLOWED
        ownerUid.isNullOrBlank() -> LocalArchiveAccessDecision.CONSENT_REQUIRED
        ownerUid == activeUid -> LocalArchiveAccessDecision.ALLOWED
        else -> LocalArchiveAccessDecision.LOCKED
    }
}
