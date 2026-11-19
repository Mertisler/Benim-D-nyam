package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.InitialBootstrapOverview
import com.loc.benimdunyam.data.sync.remote.InitialJournalBootstrapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Faz 2F.9: UI durumunda hiçbir zaman aktarım parolası veya anı metni bulunmaz.
 * Hesap değişince eski hesaba ait sayılar ve onaylar sıfırlanır.
 */
data class InitialBootstrapUiState(
    val accountUid: String? = null,
    val busy: Boolean = false,
    val overview: InitialBootstrapOverview? = null,
    val status: String? = null,
    val error: String? = null
)

@HiltViewModel
class InitialJournalBootstrapViewModel @Inject constructor(
    private val repository: InitialJournalBootstrapRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(InitialBootstrapUiState())
    val state = mutable.asStateFlow()

    fun selectAccount(uid: String) {
        if (mutable.value.accountUid != uid) {
            mutable.value = InitialBootstrapUiState(accountUid = uid)
        }
    }

    /** Salt okuma: internet veya parola gerekmez. */
    fun refresh(uid: String) {
        if (mutable.value.busy || mutable.value.accountUid != uid) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val report = repository.overview(uid)
                if (mutable.value.accountUid == uid) {
                    mutable.value = mutable.value.copy(busy = false, overview = report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                showError(uid, e)
            }
        }
    }

    /** Sunucu denetimi sonrasında yalnızca yerel PENDING kayıtları oluşturulur. */
    fun prepare(uid: String, confirmed: Boolean) {
        if (mutable.value.busy || mutable.value.accountUid != uid || !confirmed) return
        mutable.value = mutable.value.copy(busy = true, error = null, status = null)
        viewModelScope.launch {
            try {
                val result = repository.prepareNext(uid, confirmed)
                val report = repository.overview(uid)
                if (mutable.value.accountUid == uid) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        overview = report,
                        status = "${result.prepared} eski anı kuyruğa alındı. " +
                                "${result.changedWhileChecking} anı denetimde değişti, " +
                                "${result.alreadyTracked} anı zaten izleniyor. " +
                                "Kalan eski anı: ${result.remainingUntracked}. Henüz buluta gönderilmedi."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                showError(uid, e)
            }
        }
    }

    /**
     * Şifre sadece çağrının yerel CharArray kopyasında bulunur, iş bittiğinde silinir.
     * En fazla beş PENDING olay gönderilir; bunlar DELETE de içerebilir.
     */
    fun upload(uid: String, passphrase: String, confirmed: Boolean) {
        if (mutable.value.busy || mutable.value.accountUid != uid || !confirmed) return
        val secret = passphrase.toCharArray()
        mutable.value = mutable.value.copy(busy = true, error = null, status = null)
        viewModelScope.launch {
            try {
                val report = repository.uploadEncryptedBatch(uid, secret, confirmed)
                val overview = repository.overview(uid)
                if (mutable.value.accountUid == uid) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        overview = overview,
                        status = "${report.encryptedCatalogParts} katalog parçası oluşturuldu, " +
                                "${report.uploadedEvents} olay doğrulandı. " +
                                "Gönderim sırasında değişen: ${report.changedDuringUpload}. " +
                                "Bekleyen toplam: ${report.remainingPending}."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                showError(uid, e)
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    private fun showError(uid: String, e: Exception) {
        if (mutable.value.accountUid != uid) return
        val reason = when (e) {
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Firestore kuralları bu hesabın işlemini reddetti."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Sunucuya ulaşılamıyor. İnternet bağlantısını kontrol et."
                else -> "Firestore isteği başarısız: ${e.code}."
            }
            is IllegalArgumentException, is IllegalStateException ->
                e.message ?: "Doğrulama başarısız."
            else -> "İşlem tamamlanamadı: ${e.message ?: "Bilinmeyen hata"}"
        }
        mutable.value = mutable.value.copy(busy = false, error = reason, status = null)
    }
}
