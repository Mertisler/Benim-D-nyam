package com.loc.benimdunyam.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Giriş sayfası ve yönlendirme geri çağrıları. */
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    AuthForm(
        mode = AuthFormMode.SIGN_IN,
        viewModel = viewModel,
        onBack = onBack,
        onSwitchMode = onRegister,
        onForgotPassword = onForgotPassword,
        onAuthenticated = onAuthenticated
    )
}

/** Kayıt sayfası. Başarılı kayıt Firebase tarafından oturumu da açar. */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    AuthForm(
        mode = AuthFormMode.REGISTER,
        viewModel = viewModel,
        onBack = onBack,
        onSwitchMode = onSignIn,
        onForgotPassword = {},
        onAuthenticated = onAuthenticated
    )
}

/** E-posta üzerinden şifre sıfırlama. Uygulama içinde şifre değiştirme yapılmaz. */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    AuthForm(
        mode = AuthFormMode.RESET,
        viewModel = viewModel,
        onBack = onBack,
        onSwitchMode = onBack,
        onForgotPassword = {},
        onAuthenticated = {}
    )
}

private enum class AuthFormMode { SIGN_IN, REGISTER, RESET }

/**
 * Tekrarlanan e-posta/şifre alanlarını paylaşan arayüz.
 * Şifreler remember ile tutulur; rememberSaveable ve DataStore'a yazılmaz.
 */
@Composable
private fun AuthForm(
    mode: AuthFormMode,
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onForgotPassword: () -> Unit,
    onAuthenticated: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // ViewModel başarılı sonucu bildirdiğinde ekranı geride bırakırız.
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            password = ""
            confirmation = ""
            onAuthenticated()
        }
    }

    val title = when (mode) {
        AuthFormMode.SIGN_IN -> "Yeniden hoş geldin"
        AuthFormMode.REGISTER -> "Hikâyeni başlat"
        AuthFormMode.RESET -> "Şifreni yenile"
    }
    val description = when (mode) {
        AuthFormMode.SIGN_IN -> "Anılarına kaldığın yerden devam et."
        AuthFormMode.REGISTER -> "Kendi dünyanı oluşturmak için birkaç adım kaldı."
        AuthFormMode.RESET -> "E-postana bir şifre sıfırlama bağlantısı göndereceğiz."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !state.isLoading) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
            }
            Text("Benim Dünyam", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; viewModel.dismissMessage() },
                    label = { Text("E-posta") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (mode == AuthFormMode.RESET) ImeAction.Done else ImeAction.Next
                    ),
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mode != AuthFormMode.RESET) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; viewModel.dismissMessage() },
                        label = { Text("Şifre") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Şifreyi gizle" else "Şifreyi göster"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (mode == AuthFormMode.REGISTER) ImeAction.Next else ImeAction.Done
                        ),
                        singleLine = true,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (mode == AuthFormMode.REGISTER) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it; viewModel.dismissMessage() },
                        label = { Text("Şifre tekrar") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        singleLine = true,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "En az 6 karakter kullan. Daha uzun ve benzersiz bir şifre tercih et.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (mode == AuthFormMode.SIGN_IN) {
                    TextButton(onClick = onForgotPassword, enabled = !state.isLoading) {
                        Text("Şifremi unuttum")
                    }
                }
                state.errorMessage?.let { error ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            error,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                state.infoMessage?.let { info ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(
                            info,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Button(
                    onClick = {
                        when (mode) {
                            AuthFormMode.SIGN_IN -> viewModel.signIn(email, password)
                            AuthFormMode.REGISTER -> viewModel.register(email, password, confirmation)
                            AuthFormMode.RESET -> viewModel.sendPasswordReset(email)
                        }
                    },
                    enabled = !state.isLoading && email.isNotBlank() &&
                            (mode == AuthFormMode.RESET || password.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(when (mode) {
                        AuthFormMode.SIGN_IN -> "Giriş yap"
                        AuthFormMode.REGISTER -> "Hesap oluştur"
                        AuthFormMode.RESET -> "Sıfırlama bağlantısı gönder"
                    })
                }
                if (mode != AuthFormMode.RESET) {
                    TextButton(onClick = onSwitchMode, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mode == AuthFormMode.SIGN_IN) "Hesabın yok mu? Kayıt ol" else "Zaten hesabın var mı? Giriş yap")
                    }
                } else {
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Giriş ekranına dön")
                    }
                }
            }
        }
        Text(
            "Hesabına giriş yapman, cihazındaki anıları otomatik olarak buluta yüklemez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
