package com.loc.benimdunyam.feature.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.core.auth.IntroPreferenceStore
import com.loc.benimdunyam.domain.model.CloudAuthError
import com.loc.benimdunyam.domain.model.CloudAuthResult
import com.loc.benimdunyam.domain.model.CloudPasswordResetStatus
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Giriş, kayıt ve şifre sıfırlama için tek iş mantığı.
 * Compose ekranları Firebase SDK'sını doğrudan kullanmaz.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val cloudAuthRepository: CloudAuthRepository,
    private val introPreferenceStore: IntroPreferenceStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Hesapsız kullanımda yalnızca karşılama tercihi kaydedilir. */
    fun continueAsGuest() {
        introPreferenceStore.markCompleted()
    }

    fun signIn(email: String, password: String) = submit(email, password, isRegistration = false)

    fun register(email: String, password: String, confirmation: String) {
        if (password != confirmation) {
            showError("Şifreler eşleşmiyor.")
            return
        }
        if (password.length < 6) {
            showError("Şifre en az 6 karakter olmalı.")
            return
        }
        submit(email, password, isRegistration = true)
    }

    private fun submit(email: String, password: String, isRegistration: Boolean) {
        if (_uiState.value.isLoading || _uiState.value.isAuthenticated) return
        val normalizedEmail = email.trim()
        if (!validEmail(normalizedEmail)) {
            showError("Geçerli bir e-posta adresi gir.")
            return
        }
        if (password.isEmpty()) {
            showError("Şifreni gir.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val result = if (isRegistration) {
                    cloudAuthRepository.registerWithEmail(normalizedEmail, password)
                } else {
                    cloudAuthRepository.signInWithEmail(normalizedEmail, password)
                }
                when (result) {
                    is CloudAuthResult.Success -> {
                        // Giriş yapmak mevcut Room verilerini otomatik olarak buluta göndermez.
                        introPreferenceStore.markCompleted()
                        _uiState.update { it.copy(isAuthenticated = true) }
                    }
                    is CloudAuthResult.Failure -> {
                        showError(result.reason.toTurkishMessage())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError("İşlem tamamlanamadı. Tekrar dene.")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Sıfırlama sonucunda e-posta var/yok bilgisini kullanıcıya açıklamayız. */
    fun sendPasswordReset(email: String) {
        if (_uiState.value.isLoading) return
        val normalizedEmail = email.trim()
        if (!validEmail(normalizedEmail)) {
            showError("Geçerli bir e-posta adresi gir.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val status = cloudAuthRepository.requestPasswordReset(normalizedEmail)
                when (status) {
                    CloudPasswordResetStatus.SENT -> _uiState.update {
                        it.copy(infoMessage = "Bu adres için bir hesap varsa şifre sıfırlama bağlantısı e-postana gönderildi.")
                    }
                    CloudPasswordResetStatus.NETWORK_ERROR -> showError("İnternet bağlantını kontrol edip tekrar dene.")
                    CloudPasswordResetStatus.RATE_LIMITED -> showError("Çok fazla deneme yapıldı. Bir süre sonra tekrar dene.")
                    CloudPasswordResetStatus.FAILED -> showError("Şifre sıfırlama isteği gönderilemedi. Biraz sonra yeniden dene.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError("İşlem tamamlanamadı. Tekrar dene.")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun validEmail(email: String) = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message, infoMessage = null) }
    }
}

/** Firebase'e özgü hata ayrıntılarını kullanıcıya Türkçe ve güvenli şekilde gösterir. */
private fun CloudAuthError.toTurkishMessage(): String = when (this) {
    CloudAuthError.INVALID_EMAIL_OR_PASSWORD -> "E-posta veya şifre hatalı."
    CloudAuthError.EMAIL_ALREADY_IN_USE -> "Bu e-posta adresi zaten kayıtlı. Giriş yapmayı dene."
    CloudAuthError.WEAK_PASSWORD -> "Daha güçlü bir şifre belirle."
    CloudAuthError.NETWORK -> "İnternet bağlantını kontrol et."
    CloudAuthError.RATE_LIMITED -> "Çok fazla deneme yapıldı. Daha sonra tekrar dene."
    CloudAuthError.UNKNOWN -> "İşlem sırasında bir hata oluştu. Tekrar dene."
}
