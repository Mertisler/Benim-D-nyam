package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.SafeJournalMergeRepository
import com.loc.benimdunyam.data.sync.remote.SafeMergePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Yalnızca rapor ve doğrulama anahtarı saklanır.
 * Aktarım parolası ve çözülen günlük metni StateFlow veya SavedStateHandle'a yazılmaz.
 */
data class SafeJournalMergeUiState(
    val accountUid: String? = null,
    val busy: Boolean = false,
    val report: SafeMergePreview? = null,
    val pendingMoodCount: Int = 0,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class SafeJournalMergeViewModel @Inject constructor(
    private val repository: SafeJournalMergeRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(SafeJournalMergeUiState())
    val state = mutable.asStateFlow()

    /** Kullanıcı değiştirilirse eski hesabın önizlemesi ve onayı geçersiz olur. */
    fun selectAccount(uid: String) {
        if (mutable.value.accountUid != uid) {
            mutable.value = SafeJournalMergeUiState(accountUid = uid)
        }
    }

    /**
     * Bu işlem sadece sunucuyu ve Room'u OKUR.
     * Şifre işlemin sonunda bellekte temizlenir.
     */
    fun preview(uid: String, password: String) {
        if (mutable.value.busy || mutable.value.accountUid != uid) return
        val secret = password.toCharArray()
        mutable.value = SafeJournalMergeUiState(accountUid = uid, busy = true)
        viewModelScope.launch {
            try {
                val report = repository.preview(uid, secret)
                if (mutable.value.accountUid == uid) {
                    mutable.value = SafeJournalMergeUiState(
                        accountUid = uid,
                        report = report,
                        pendingMoodCount = report.pendingMoodFinishes,
                        message = "Önizleme tamamlandı. Room'daki veriler değiştirilmedi."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(uid, error)
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /**
     * Kullanıcının ikinci onayı ve aynı aktarım şifresi zorunludur.
     * Sunucu kaydı değişmişse önceki önizlemeden işlem yapılamaz.
     */
    fun importMissing(uid: String, password: String, confirmed: Boolean) {
        val current = mutable.value
        if (current.busy || current.accountUid != uid || !confirmed) return
        val fingerprint = current.report?.fingerprint ?: return
        val secret = password.toCharArray()
        mutable.value = current.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val result = repository.importMissing(uid, secret, fingerprint, true)
                if (mutable.value.accountUid == uid) {
                    mutable.value = SafeJournalMergeUiState(
                        accountUid = uid,
                        pendingMoodCount = result.pendingMoodFinishes,
                        message = "${result.insertedJournals} yeni anı ve ilişkileri eklendi; " +
                                "${result.restoredMoods} ruh hali tamamlandı. " +
                                (if (result.pendingMoodFinishes > 0)
                                    "${result.pendingMoodFinishes} ruh hali beklemede: kurtarma butonunu kullan."
                                else "Ruh hali hazırlık kaydı kalmadı.") +
                                " Kalan anılar için yeniden önizleme yap."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Başarısız denemede önizleme onayını temizleriz.
                showError(uid, error)
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /** Şifre gerektirmeyen yerel kurtarma durumunu okur; veriye dokunmaz. */
    fun refreshPendingMoods(uid: String) {
        if (mutable.value.accountUid != uid || mutable.value.busy) return
        viewModelScope.launch {
            try {
                val count = repository.pendingMoodCount(uid)
                if (mutable.value.accountUid == uid && !mutable.value.busy) {
                    mutable.value = mutable.value.copy(pendingMoodCount = count)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Hesap henüz hazır değilse ilk okumada sadece mevcut durum korunur.
            }
        }
    }

    /** Kullanıcının önceden onayladığı, yarım kalan ruh hali işlemlerini tamamlar. */
    fun resumePendingMoods(uid: String) {
        if (mutable.value.busy || mutable.value.accountUid != uid) return
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val recovery = repository.resumePendingMoods(uid)
                if (mutable.value.accountUid == uid) {
                    mutable.value = SafeJournalMergeUiState(
                        accountUid = uid,
                        pendingMoodCount = recovery.remaining,
                        message = "${recovery.completed} ruh hali tamamlandı; " +
                                "${recovery.remaining} kayıt hâlâ bekliyor. " +
                                "Bekleyenler için yerel çakışmaların incelenmesi gerekebilir."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showError(uid, error)
            }
        }
    }

    private fun showError(uid: String, throwable: Exception) {
        if (mutable.value.accountUid != uid) return
        val text = when (throwable) {
            is FirebaseFirestoreException -> when (throwable.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Firestore güvenlik kuralları erişimi reddetti."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Sunucuya ulaşılamadı. İnterneti kontrol edip tekrar dene."
                else -> "Bulut okuması başarısız (${throwable.code})."
            }
            is IllegalArgumentException, is IllegalStateException ->
                "İşlem durduruldu: Şifre, güncellik, hesap veya kayıt ilişkileri doğrulanamadı. " +
                        "Kataloğu yenileyip yeniden önizleme yap."
            else -> "İşlem durduruldu. Ana Room kayıtları eklenmiş olabilir; " +
                    "yarım kalan ruh hallerini kontrol et. Mevcut anılar ezilmez."
        }
        mutable.value = SafeJournalMergeUiState(
            accountUid = uid, pendingMoodCount = mutable.value.pendingMoodCount,
            error = text
        )
    }

    fun clear() {
        if (!mutable.value.busy) {
            mutable.value = SafeJournalMergeUiState(
                accountUid = mutable.value.accountUid,
                pendingMoodCount = mutable.value.pendingMoodCount
            )
        }
    }
}
