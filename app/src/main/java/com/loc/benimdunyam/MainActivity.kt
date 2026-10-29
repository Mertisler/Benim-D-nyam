package com.loc.benimdunyam

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.loc.benimdunyam.core.designsystem.BenimDunyamTheme
import com.loc.benimdunyam.core.navigation.BenimDunyamNavGraph
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executor

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // ComponentActivity yerine FragmentActivity yapıldı
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("benim_dunyam_prefs", MODE_PRIVATE)
        val isBiometricEnabled = prefs.getBoolean("biometric_lock", false)

        if (isBiometricEnabled) {
            showBiometricPrompt {
                setContentUI()
            }
        } else {
            setContentUI()
        }
    }

    private fun setContentUI() {
        setContent {
            BenimDunyamTheme {
                BenimDunyamNavGraph()
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        finish()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Benim Dünyam Kilitli")
            .setSubtitle("Devam etmek için parmak izinizi veya yüzünüzü doğrulayın")
            .setNegativeButtonText("İptal")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}