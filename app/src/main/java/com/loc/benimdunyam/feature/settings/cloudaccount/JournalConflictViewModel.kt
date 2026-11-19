package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.JournalConflictReport
import com.loc.benimdunyam.data.sync.remote.JournalConflictRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Parola ve tam anı metni kalıcı UI durumunda tutulmaz; yalnızca kısa önizlemeler. */
data class JournalConflictUiState(
    val accountUid: String? = null,
    val loading: Boolean = false,
    val report: JournalConflictReport? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class JournalConflictViewModel @Inject constructor(
    private val repository: JournalConflictRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(JournalConflictUiState())
    val state = mutable.asStateFlow()

    /** Hesap değişikliği önceki kullanıcıya ait verileri ekrandan temizler. */
    fun selectAccount(uid: String) {
        if (mutable.value.accountUid != uid) {
            mutable.value = JournalConflictUiState(accountUid = uid)
        }
    }

    /** Bu işlem buluttan OKUR; mevcut anılara ve iş kuyruğuna dokunmaz. */
    fun inspect(uid: String, password: String) {
        if (mutable.value.loading || mutable.value.accountUid != uid) return
        val secret = password.toCharArray()
        mutable.value = JournalConflictUiState(accountUid = uid, loading = true)
        viewModelScope.launch {
            try {
                val report = repository.inspect(uid, secret)
                if (mutable.value.accountUid == uid) {
                    mutable.value = JournalConflictUiState(
                        accountUid = uid,
                        report = report,
                        message = "İnceleme tamamlandı. Hiçbir anı değiştirilmedi."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(uid, error)
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /**
     * İkinci kullanıcı onayı ve tekrar girilmiş aktarım şifresi zorunludur.
     * İşlem tek bir ayrı metin kopyası oluşturur; orijinali değiştirmez.
     */
    fun preserveCopy(uid: String, password: String, remoteDocumentId: String, confirmed: Boolean) {
        val current = mutable.value
        if (current.loading || current.accountUid != uid || !confirmed) return
        val card = current.report?.cards?.firstOrNull { it.remoteDocumentId == remoteDocumentId }
            ?: return
        if (card.savedAsSeparateCopy || !card.canPreserveCopy) return
        val secret = password.toCharArray()
        mutable.value = current.copy(loading = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val result = repository.preserveSeparateCopy(
                    uid = uid,
                    password = secret,
                    documentId = card.remoteDocumentId,
                    expectedConfirmationKey = card.confirmationKey,
                    remoteHeadCount = card.simultaneousRemoteHeads,
                    confirmed = true
                )
                if (mutable.value.accountUid == uid) {
                    val previous = mutable.value
                    mutable.value = previous.copy(
                        loading = false,
                        report = previous.report?.copy(
                            cards = previous.report.cards.map {
                                if (it.remoteDocumentId == remoteDocumentId)
                                    it.copy(savedAsSeparateCopy = true) else it
                            }
                        ),
                        message = if (result.alreadyPresent)
                            "Bu çakışmanın koruma kopyası zaten bulunuyor. Üzerine yazılmadı."
                        else "Metin, Günlükler ekranına ayrı bir anı olarak kaydedildi. " +
                                "Orijinal anı ve silme geçmişi değiştirilmedi."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(uid, error)
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    private fun onError(uid: String, error: Exception) {
        if (mutable.value.accountUid != uid) return
        val message = when (error) {
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Firestore izinleri bu hesap için okumayı reddetti."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Sunucuya erişilemedi. İnternet bağlantısını kontrol et."
                else -> "Bulut okuması başarısız (${error.code})."
            }
            is IllegalArgumentException, is IllegalStateException ->
                "İşlem durduruldu: Hesap, şifre, olay ya da yerel sürüm doğrulanamadı. " +
                        "Önizlemeyi yenileyip tekrar dene."
            else -> "İşlem güvenli biçimde durduruldu. Mevcut anıya dokunulmadı."
        }
        mutable.value = JournalConflictUiState(accountUid = uid, error = message)
    }

    /** Kullanıcının aynı ekranda önizlemeyi temizlemesi. */
    fun clearFor(uid: String) {
        if (mutable.value.accountUid == uid && !mutable.value.loading) {
            mutable.value = JournalConflictUiState(accountUid = uid)
        }
    }

    /**
     * Ekrandan çıkıldığında veya hesap değiştiğinde eski önizlemeyi bırakma.
     * Ağ isteği daha sonra tamamlanırsa accountUid boş olduğu için sonuç gösterilmez.
     */
    fun onExit(uid: String) {
        if (mutable.value.accountUid == uid) {
            mutable.value = JournalConflictUiState()
        }
    }
}
