package com.loc.benimdunyam.feature.auth

/**
 * Giriş/kayıt ekranlarının yalnızca görünüm durumları.
 * Şifreler burada veya SavedStateHandle'da saklanmaz.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)
