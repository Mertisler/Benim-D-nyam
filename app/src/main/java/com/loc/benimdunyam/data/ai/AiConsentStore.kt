package com.loc.benimdunyam.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kullanıcının yapay zekâ izni. Varsayılan kapalı. */
data class AiConsent(
    val enabled: Boolean = false,
    val acceptedVersion: Int = 0,
    val acceptedAt: Long = 0L
) {
    /** İzin metni değişirse kullanıcıdan yeniden onay istenir. */
    val isValid: Boolean get() = enabled && acceptedVersion >= AiConsentStore.CONSENT_VERSION
}

interface AiConsentSource {
    val consent: StateFlow<AiConsent>
    fun accept(now: Long = System.currentTimeMillis())
    fun revoke()
}

@Singleton
class AiConsentStore @Inject constructor(
    @ApplicationContext context: Context
) : AiConsentSource {
    private val prefs = context.getSharedPreferences("bd_ai_consent_v1", Context.MODE_PRIVATE)
    private val _consent = MutableStateFlow(read())
    override val consent: StateFlow<AiConsent> = _consent.asStateFlow()

    private fun read() = AiConsent(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        acceptedVersion = prefs.getInt(KEY_VERSION, 0),
        acceptedAt = prefs.getLong(KEY_AT, 0L)
    )

    override fun accept(now: Long) {
        prefs.edit().putBoolean(KEY_ENABLED, true).putInt(KEY_VERSION, CONSENT_VERSION).putLong(KEY_AT, now).apply()
        _consent.value = read()
    }

    override fun revoke() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        _consent.value = read()
    }

    companion object {
        /** İzin ekranındaki açıklama esaslı olarak değişirse artırılır. */
        const val CONSENT_VERSION = 1
        private const val KEY_ENABLED = "enabled"
        private const val KEY_VERSION = "accepted_version"
        private const val KEY_AT = "accepted_at"
    }
}
