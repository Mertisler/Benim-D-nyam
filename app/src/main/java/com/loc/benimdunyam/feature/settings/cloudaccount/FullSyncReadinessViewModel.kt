package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.FullSyncCoverageReport
import com.loc.benimdunyam.data.sync.remote.SyncDependencyCoverageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Yalnızca özet raporu saklarız; şifre veya çözülmüş günlük metni state'e yazılmaz. */
data class FullSyncReadinessUiState(
    val accountUid: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val report: FullSyncCoverageReport? = null
)

@HiltViewModel
class FullSyncReadinessViewModel @Inject constructor(
    private val repository: SyncDependencyCoverageRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(FullSyncReadinessUiState())
    val state = mutable.asStateFlow()

    /** Hesap değişince önceki hesabın raporlarını hemen temizler. */
    fun selectAccount(uid: String) {
        if (uid != mutable.value.accountUid) {
            mutable.value = FullSyncReadinessUiState(accountUid = uid)
        }
    }

    fun upload(uid: String, passphrase: String) = execute(uid, passphrase, upload = true)
    fun inspect(uid: String, passphrase: String) = execute(uid, passphrase, upload = false)

    private fun execute(uid: String, passphrase: String, upload: Boolean) {
        if (mutable.value.busy || uid != mutable.value.accountUid) return
        // Parola ViewModel'in kalıcı alanına değil, yalnızca işin yerel kapsamına alınır.
        val secret = passphrase.toCharArray()
        mutable.value = FullSyncReadinessUiState(accountUid = uid, busy = true)
        viewModelScope.launch {
            try {
                if (upload) {
                    val count = repository.uploadCatalog(uid, secret)
                    if (mutable.value.accountUid == uid) {
                        mutable.value = FullSyncReadinessUiState(
                            accountUid = uid,
                            message = "İlişki kataloğu $count şifreli parçayla buluta yüklendi. " +
                                    "Anılar ve fotoğraflar değiştirilmedi."
                        )
                    }
                } else {
                    val report = repository.audit(uid, secret)
                    if (mutable.value.accountUid == uid) {
                        mutable.value = FullSyncReadinessUiState(
                            accountUid = uid,
                            report = report,
                            message = "Bulut olayları sayfalı okunup doğrulandı; Room'a kayıt yapılmadı."
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (mutable.value.accountUid == uid) {
                    // Hata ayrıntılarında kişisel içerik olabileceği için Logcat'e veya UI'ya basmayız.
                    val error = when (e) {
                        is FirebaseFirestoreException -> when (e.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "Firestore kuralları işleme izin vermedi; Faz 2F.5 kurallarını kontrol et."
                            FirebaseFirestoreException.Code.UNAVAILABLE,
                            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                                "Sunucuya erişilemedi. İnternet bağlantını kontrol et."
                            else -> "Firestore işlemi tamamlanamadı (${e.code})."
                        }
                        is IllegalArgumentException, is IllegalStateException ->
                            "Katalog veya olaylar doğrulanamadı: Eksik ilişki, farklı şifre, " +
                                    "büyük arşiv ya da hesap uyuşmazlığı olabilir."
                        else -> "Kapsam denetimi tamamlanamadı; şifre ve bağlantıyı kontrol et."
                    }
                    mutable.value = FullSyncReadinessUiState(accountUid = uid, error = error)
                }
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /** Önceki denetimin sonucunu ekrandan kaldırır. */
    fun clear() {
        if (!mutable.value.busy) mutable.value =
            FullSyncReadinessUiState(accountUid = mutable.value.accountUid)
    }
}
