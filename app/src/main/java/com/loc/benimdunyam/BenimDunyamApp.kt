package com.loc.benimdunyam

import android.app.Application
import android.util.Log
import com.loc.benimdunyam.data.backup.RestoreRecoveryManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hilt uygulama giriş noktası ve düşük riskli eski geçici dosya temizliği. */
@HiltAndroidApp
class BenimDunyamApp : Application() {

    @Inject
    lateinit var restoreRecoveryManager: RestoreRecoveryManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            try {
                val result = restoreRecoveryManager.cleanupAbandonedParts()
                Log.d(
                    "BackupRecovery",
                    "Temizlenen eski geçici dosya: ${result.removedPartialFiles}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("BackupRecovery", "Kurtarma temizliği çalıştırılamadı.", e)
            }
        }
    }
}
