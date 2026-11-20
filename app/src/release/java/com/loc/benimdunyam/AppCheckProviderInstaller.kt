package com.loc.benimdunyam

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release derlemesi: App Check her zaman Play Integrity ile çalışır.
 * Debug sağlayıcısı ve debug token'ı bu APK'ye girmez
 * (karşılığı src/debug altındadır).
 */
internal object AppCheckProviderInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
