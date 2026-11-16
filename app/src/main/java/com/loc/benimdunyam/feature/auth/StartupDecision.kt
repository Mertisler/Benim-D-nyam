package com.loc.benimdunyam.feature.auth

/** Başlangıç ekranı için iki olası yön. */
enum class StartupDestination { HOME, WELCOME }

/**
 * Saf karar fonksiyonu: Android/Firebase bağımlılığı yoktur.
 * Bu sayede hesaplı ve hesapsız giriş senaryolarını kolayca test edebiliriz.
 */
fun determineStartupDestination(
    isSignedIn: Boolean,
    hasCompletedIntro: Boolean
): StartupDestination = if (isSignedIn || hasCompletedIntro) {
    StartupDestination.HOME
} else {
    StartupDestination.WELCOME
}
