package com.loc.benimdunyam

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Faz 2H.1: Android Studio ve emülatör için debug App Check sağlayıcısı.
 * Logcat'te görüntülenebilen debug token'ı Firebase Console'a sadece test
 * uygulaması için kaydedilmeli; repo/ZIP/ekran görüntüsüne konulmamalıdır.
 * Bu sınıf release APK'ye DAHİL DEĞİLDİR.
 */
internal object AppCheckProviderInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
