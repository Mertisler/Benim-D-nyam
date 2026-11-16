package com.loc.benimdunyam.core.auth

import android.content.Context
import com.loc.benimdunyam.domain.model.LocalArchiveAccessDecision
import com.loc.benimdunyam.domain.model.LocalArchiveAccessPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yerel anı arşivinin hangi Firebase hesabına bağlı olduğunu saklayan sınıf.
 *
 * Önceki sürümlerde kullanılan aynı SharedPreferences dosyasını okur.
 * Yeni bir sahiplik alanı oluşturmaz; mevcut kullanıcı verilerini değiştirmez.
 *
 * ÖNEMLİ: Bu kayıt uygulama içi hesap kontrolü sağlar. Room veritabanını
 * şifrelemez veya hesaplara ait fiziksel veritabanlarını ayrı tutmaz.
 */
@Singleton
class LocalArchiveAccessStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        "benimdunyam_cloud_archive_owner_v1",
        Context.MODE_PRIVATE
    )

    private companion object {
        const val OWNER_KEY = "archive_owner_uid"
    }

    /**
     * Faz 2E.2/2E.3 depolama denetimi ve taşıma hazırlığı için
     * yerel arşiv sahibinin tam UID değerini okur.
     *
     * Bu değer yalnızca hesap eşleşmesini kontrol etmek içindir;
     * kullanıcı arayüzünde tam UID gösterilmemelidir.
     * Kayıt yoksa veya boşsa null döner.
     */
    fun ownerUidOrNull(): String? = preferences.getString(OWNER_KEY, null)
        ?.takeIf { it.isNotBlank() }

    /**
     * Kilit ekranında doğru Firebase hesabını ayırt etmeye yardımcı olur.
     * Tam UID yerine son 6 karakteri verir; sahiplik kaydını değiştirmez.
     */
    fun ownerUidSuffix(): String? = ownerUidOrNull()?.takeLast(6)

    /**
     * Açık Firebase hesabının yerel arşive erişip erişemeyeceğini belirler.
     * Kararı domain katmanındaki mevcut güvenlik politikasına bırakır.
     */
    fun accessFor(activeUid: String?): LocalArchiveAccessDecision =
        LocalArchiveAccessPolicy.decide(ownerUidOrNull(), activeUid)

    /**
     * Yerel arşivi yalnızca ilk kez ve kullanıcının açık onayıyla hesaba bağlar.
     * Başka bir hesap zaten kayıtlıysa sahiplik bilgisi değiştirilmez.
     *
     * commit() sayesinde yazmanın başarılı olup olmadığını kontrol ederiz.
     */
    @Synchronized
    fun bindOnceTo(uid: String): Boolean {
        require(uid.isNotBlank()) { "Geçerli bir hesap bulunamadı." }
        val current = preferences.getString(OWNER_KEY, null)
        if (current != null) return current == uid
        return preferences.edit().putString(OWNER_KEY, uid).commit()
    }
}
