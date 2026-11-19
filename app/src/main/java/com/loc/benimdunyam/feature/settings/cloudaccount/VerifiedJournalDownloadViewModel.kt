package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.loc.benimdunyam.data.sync.remote.RemoteJournalDownloadPreview
import com.loc.benimdunyam.data.sync.remote.VerifiedJournalEventDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Şifre ve günlük içeriği değil, yalnızca doğrulama sonucu tutulur. */
data class VerifiedJournalDownloadUiState(
    val accountUid: String? = null,
    val loading: Boolean = false,
    val preview: RemoteJournalDownloadPreview? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class VerifiedJournalDownloadViewModel @Inject constructor(
    private val downloader: VerifiedJournalEventDownloader
) : ViewModel() {
    private val mutable = MutableStateFlow(VerifiedJournalDownloadUiState())
    val uiState = mutable.asStateFlow()

    /** Hesap değişince eski hesabın olay adları ekranda tutulmaz. */
    fun selectAccount(uid: String) {
        if (mutable.value.accountUid != uid) {
            mutable.value = VerifiedJournalDownloadUiState(accountUid = uid)
        }
    }

    /**
     * Parola yalnızca bu kullanıcının başlattığı işlem sırasında kullanılır.
     * CharArray sonunda sıfırlanır; otomatik iş planlanmaz, Room'a yazılmaz.
     */
    fun inspect(uid: String, passphrase: String) {
        if (mutable.value.loading || uid != mutable.value.accountUid) return
        val secret = passphrase.toCharArray()
        mutable.value = VerifiedJournalDownloadUiState(accountUid = uid, loading = true)
        viewModelScope.launch {
            try {
                val result = downloader.preview(uid, secret)
                if (mutable.value.accountUid == uid) {
                    mutable.value = VerifiedJournalDownloadUiState(
                        accountUid = uid,
                        preview = result,
                        message = "${result.items.size} şifreli olay sunucudan okunup doğrulandı. " +
                                "Yerel anılar değiştirilmedi."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (mutable.value.accountUid == uid) {
                    // JSON / ağ hata detaylarını anı metni sızdırmamak için UI'ya basmıyoruz.
                    val message = when (failure) {
                        is FirebaseFirestoreException -> when (failure.code) {
                            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "Firestore erişimi reddedildi; kendi hesabının kurallarını kontrol et."
                            FirebaseFirestoreException.Code.UNAVAILABLE,
                            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                                "Sunucuya erişilemedi; internet bağlantını kontrol et."
                            else -> "Bulut olayları okunamadı (Firestore: ${failure.code})."
                        }
                        else -> "Olaylardan biri doğrulanamadı. Aktarım şifresini, " +
                                "buluttaki kayıtların bütünlüğünü ve hesap eşleşmesini kontrol et."
                    }
                    mutable.value = VerifiedJournalDownloadUiState(
                        accountUid = uid,
                        error = message
                    )
                }
            } finally {
                secret.fill('\u0000')
            }
        }
    }

    /** Ekran kapatılmadan önce sonucu kullanıcı isteğiyle kaldırabilir. */
    fun clearPreview() {
        if (!mutable.value.loading) {
            mutable.value = VerifiedJournalDownloadUiState(accountUid = mutable.value.accountUid)
        }
    }
}
