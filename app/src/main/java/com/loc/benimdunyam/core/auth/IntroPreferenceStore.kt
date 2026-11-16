package com.loc.benimdunyam.core.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * İlk açılış tercihini cihazda saklar.
 *
 * Burada kullanıcı e-postası, UID, şifresi veya anı içeriği tutulmaz.
 * Bu tercih yalnızca karşılama ekranını her açılışta tekrar göstermemek içindir.
 */
@Singleton
class IntroPreferenceStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        "benim_dunyam_giris_tercihi",
        Context.MODE_PRIVATE
    )

    fun isCompleted(): Boolean = preferences.getBoolean(KEY_COMPLETED, false)

    /** Kullanıcı hesapla veya hesapsız devam etmeyi seçtikten sonra çağrılır. */
    fun markCompleted() {
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    private companion object {
        const val KEY_COMPLETED = "giris_tercihi_tamamlandi"
    }
}
