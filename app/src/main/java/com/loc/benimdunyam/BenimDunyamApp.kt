package com.loc.benimdunyam

import com.loc.benimdunyam.data.trash.JournalTrashRepository
import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
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

    @Inject
    lateinit var journalTrashRepository: JournalTrashRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Faz 2G.4C: Cloud Functions App Check doğrulaması için cihaz kimliği.
        // Firebase Console'da Play Integrity kaydı yapılmadan temizleme açılmaz.
        FirebaseApp.initializeApp(this)
        // Faz 2H.1: Debug sağlayıcısı yalnızca debug derlemesinde bulunur.
        // Release derlemesi her zaman Play Integrity kullanır; dağıtımdaki
        // Firebase App Check korumasını debug token'ıyla zayıflatmayız.
        AppCheckProviderInstaller.install()

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
            // Faz B: 30 günü dolan "Son silinenler" kayıtları.
            try {
                val purged = journalTrashRepository.purgeExpired()
                if (purged > 0) Log.d("JournalTrash", "Süresi dolan silinen anı: $purged")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("JournalTrash", "Son silinenler temizliği çalıştırılamadı.", e)
            }
        }
    }
}
