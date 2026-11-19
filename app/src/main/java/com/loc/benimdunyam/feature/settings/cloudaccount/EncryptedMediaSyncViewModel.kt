package com.loc.benimdunyam.feature.settings.cloudaccount

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.media.EncryptedMediaLocalCount
import com.loc.benimdunyam.data.sync.media.EncryptedMediaSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Şifre ve görsel içeriği hiçbir zaman ViewModel state'inde tutulmaz. */
data class EncryptedMediaUiState(
    val uid: String? = null,
    val busy: Boolean = false,
    val localCount: EncryptedMediaLocalCount? = null,
    val info: String? = null,
    val error: String? = null
)

@HiltViewModel
class EncryptedMediaSyncViewModel @Inject constructor(
    private val repository: EncryptedMediaSyncRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(EncryptedMediaUiState())
    val state = mutable.asStateFlow()

    fun select(uid: String) {
        if (mutable.value.uid != uid) mutable.value = EncryptedMediaUiState(uid = uid)
        refresh(uid)
    }

    fun refresh(uid: String) {
        if (mutable.value.uid != uid || mutable.value.busy) return
        viewModelScope.launch {
            try {
                val count = repository.localCount(uid)
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(localCount = count)
            } catch (_: Exception) {
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(
                    error = "Yerel medya kontrol edilemedi; doğru Firebase hesabını kontrol et."
                )
            }
        }
    }

    fun transfer(uid: String, password: String, upload: Boolean) {
        if (!SparkMediaPolicy.REMOTE_MEDIA_ENABLED) {
            mutable.value = mutable.value.copy(error = SparkMediaPolicy.MESSAGE)
            return
        }
        if (uid != mutable.value.uid || mutable.value.busy) return
        val secret = password.toCharArray()
        mutable.value = mutable.value.copy(busy = true, info = null, error = null)
        viewModelScope.launch {
            try {
                val result = if (upload) repository.uploadNext(uid, secret)
                else repository.restoreNext(uid, secret)
                if (mutable.value.uid == uid) {
                    mutable.value = mutable.value.copy(
                        info = if (upload)
                            "Buluta ${result.completed} şifreli görsel eklendi; " +
                                    "${result.skipped} kayıt atlandı (${result.inspected} incelendi). " +
                                    "Kalan görseller için yeniden çalıştırabilirsin."
                        else
                            "Cihaza ${result.completed} eksik görsel eklendi; " +
                                    "${result.skipped} kayıt atlandı (${result.inspected} incelendi). " +
                                    "Mevcut fotoğraflara dokunulmadı."
                    )
                    refresh(uid)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(
                    error = "Aktarım tamamlanmadı. İnternet, Firebase Storage/Firestore izinleri, " +
                            "doğru medya şifresi ve hesap eşleşmesini kontrol et. " +
                            "Başarıyla yazılmış eski kayıtlar korunur."
                )
            } finally {
                secret.fill('\u0000')
                if (mutable.value.uid == uid) mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
