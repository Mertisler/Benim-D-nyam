package com.loc.benimdunyam.feature.settings.cloudaccount

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.media.CloudPhotoLayoutChoice
import com.loc.benimdunyam.data.sync.media.EncryptedMediaDeletionRepository
import com.loc.benimdunyam.data.sync.media.MediaDeletionPreview
import com.loc.benimdunyam.data.sync.media.PhotoLayoutConflictResolutionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Kullanıcıya yalnızca özetler gösterilir; şifre UI StateFlow'unda saklanmaz. */
data class MediaConflictManagementUiState(
    val uid: String = "",
    val busy: Boolean = false,
    val deletions: MediaDeletionPreview? = null,
    val layoutChoices: List<CloudPhotoLayoutChoice> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

/** Faz 2G.3: Silme işaretlerini ve kapak/sıra çakışmalarını kullanıcıya sunar. */
@HiltViewModel
class MediaConflictManagementViewModel @Inject constructor(
    private val deleted: EncryptedMediaDeletionRepository,
    private val layouts: PhotoLayoutConflictResolutionRepository
) : ViewModel() {
    private val internal = MutableStateFlow(MediaConflictManagementUiState())
    val state = internal.asStateFlow()

    fun reset(uid: String) {
        if (!internal.value.busy && internal.value.uid != uid) {
            internal.value = MediaConflictManagementUiState(uid = uid)
        }
    }

    fun preview(uid: String, secret: CharArray) = perform(uid, secret) { password ->
        val remote = deleted.previewRemote(uid, password)
        val layoutsResult = layouts.preview(uid, password)
        internal.value = internal.value.copy(
            deletions = remote,
            layoutChoices = layoutsResult,
            message = "Bulut silme işaretleri ve fotoğraf düzenleri salt okunur incelendi."
        )
    }

    fun uploadDeletionMarkers(uid: String, secret: CharArray) = perform(uid, secret) { password ->
        val result = deleted.uploadPending(uid, password)
        internal.value = internal.value.copy(
            deletions = null,
            message = "${result.published} silme işareti buluta gönderildi; " +
                    "${result.alreadyPublished} kayıt zaten vardı. Fotoğraflar silinmedi."
        )
    }

    fun applyLayout(uid: String, secret: CharArray, documentId: String) = perform(uid, secret) { password ->
        val choice = internal.value.layoutChoices.firstOrNull { it.documentId == documentId }
            ?: error("Önizleme geçersiz; yeniden incele.")
        check(choice.canApply) { "Bu fotoğraf düzeni güvenle uygulanamıyor." }
        val applied = layouts.applyApproved(uid, password, choice)
        internal.value = internal.value.copy(
            layoutChoices = emptyList(),
            message = if (applied)
                "Seçilen fotoğraf sırası ve kapak uygulandı. Dosyalar değiştirilmedi."
            else "Yerel fotoğraf sırası değişmiş; güvenlik için uygulanmadı. Yeniden önizle."
        )
    }

    private fun perform(uid: String, password: CharArray, action: suspend (CharArray) -> Unit) {
        if (!SparkMediaPolicy.REMOTE_MEDIA_ENABLED) {
            password.fill('\u0000')
            internal.value = MediaConflictManagementUiState(uid = uid, error = SparkMediaPolicy.MESSAGE)
            return
        }
        if (internal.value.busy) {
            password.fill('\u0000')
            return
        }
        if (internal.value.uid != uid) internal.value = MediaConflictManagementUiState(uid = uid)
        internal.value = internal.value.copy(busy = true, message = null, error = null)
        viewModelScope.launch {
            try {
                require(password.size in 12..128) { "Şifre en az 12, en fazla 128 karakter olmalı." }
                action(password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (internal.value.uid == uid) internal.value = internal.value.copy(
                    error = error.message ?: "Medya çakışma kontrolü tamamlanamadı."
                )
            } finally {
                password.fill('\u0000')
                if (internal.value.uid == uid) internal.value = internal.value.copy(busy = false)
            }
        }
    }
}
