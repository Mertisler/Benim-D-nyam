package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.sync.CloudSyncCloudState
import com.loc.benimdunyam.data.sync.CloudSyncOverview

/**
 * Bulut Hesabım ana sayfasındaki tek eşitleme kartı.
 *
 * Kullanıcı yalnızca üç şey görür: eşitleme açık mı, son ne zaman eşitlendi,
 * ilgilenmesi gereken bir şey var mı. Teknik adımlar "Gelişmiş" bölümündedir.
 */
@Composable
internal fun CloudSyncHomeCard(
    uid: String,
    onOpenConflicts: () -> Unit,
    viewModel: CloudSyncHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDisable by remember { mutableStateOf(false) }

    LaunchedEffect(uid) { viewModel.select(uid) }
    // Ekrana geri dönüldüğünde sayılar tazelensin.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overview = state.overview
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                overview == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Eşitleme durumu okunuyor…")
                }
                !overview.enabled -> SyncOffContent(onEnable = viewModel::openSetup)
                else -> SyncOnContent(
                    overview = overview,
                    running = state.running,
                    busy = state.busy,
                    onSyncNow = viewModel::syncNow,
                    onOpenConflicts = onOpenConflicts,
                    onDisable = { confirmDisable = true }
                )
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = viewModel::dismissMessage))
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = viewModel::dismissMessage))
            }
        }
    }

    state.setup?.let { setup ->
        CloudSyncSetupDialogView(
            setup = setup,
            onEnable = viewModel::enable,
            onRetry = viewModel::openSetup,
            onDismiss = viewModel::closeSetup
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Eşitleme kapatılsın mı?") },
            text = {
                Text("Bu cihaz artık değişikliklerini göndermez ve almaz. Cihazındaki ve " +
                        "buluttaki anıların silinmez. Yeniden açarken eşitleme şifren gerekecek.")
            },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; viewModel.disable() }) { Text("Kapat") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun SyncOffContent(onEnable: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.CloudOff, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Bulut eşitleme kapalı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Anıların yalnızca bu cihazda.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        "Açarsan günlüklerin, kişilerin ve ruh hallerin diğer cihazlarınla şifreli olarak eşitlenir. " +
                "Şifreleme cihazında yapılır; eşitleme şifren sunucuya gönderilmez.",
        style = MaterialTheme.typography.bodySmall
    )
    Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text("Eşitlemeyi aç") }
}

@Composable
private fun SyncOnContent(
    overview: CloudSyncOverview,
    running: Boolean,
    busy: Boolean,
    onSyncNow: () -> Unit,
    onOpenConflicts: () -> Unit,
    onDisable: () -> Unit
) {
    val (icon, title, tint) = when {
        running -> Triple(Icons.Default.CloudSync, "Eşitleniyor…", MaterialTheme.colorScheme.primary)
        overview.failing -> Triple(Icons.Default.ErrorOutline, "Son eşitleme tamamlanamadı", MaterialTheme.colorScheme.error)
        else -> Triple(Icons.Default.CloudDone, "Bulut eşitleme açık", MaterialTheme.colorScheme.primary)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (running) CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
        else Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (overview.lastSuccessAtMillis > 0)
                    "Son eşitleme: ${relativeTime(overview.lastSuccessAtMillis)}"
                else "Henüz eşitlenmedi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (overview.failing && !running) {
        overview.lastFailureMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (overview.waitingToUpload > 0) {
        Text("${overview.waitingToUpload} anı değişikliği gönderilmeyi bekliyor.",
            style = MaterialTheme.typography.bodySmall)
    }
    if (overview.conflicts > 0) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConflicts).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                "${overview.conflicts} anı iki cihazda farklı düzenlendi. Hangisinin kalacağını seç.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
    if (overview.recentlyDeleted > 0) {
        Text(
            "Başka cihazda silinen ${overview.recentlyDeleted} anı 30 gün boyunca " +
                    "Ayarlar › Son silinenler bölümünde.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Button(onClick = onSyncNow, enabled = !running && !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Şimdi eşitle")
    }
    TextButton(onClick = onDisable, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Bu cihazda eşitlemeyi kapat")
    }
}

@Composable
private fun CloudSyncSetupDialogView(
    setup: CloudSyncSetupDialog,
    onEnable: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    // Şifre yalnızca bu pencere açıkken bellekte durur; kaydedilmez.
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    val cloud = when (setup) {
        is CloudSyncSetupDialog.Ready -> setup.cloud
        is CloudSyncSetupDialog.Failed -> setup.cloud
        else -> null
    }
    val isNew = cloud == CloudSyncCloudState.EMPTY
    val lengthOk = password.length in 12..128
    val canSubmit = cloud != null && lengthOk && (!isNew || password == repeat)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Eşitleme şifresi belirle" else "Eşitleme şifreni gir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (setup) {
                    CloudSyncSetupDialog.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Hesabın kontrol ediliyor…")
                    }
                    CloudSyncSetupDialog.Enabling -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (isNew) "Eşitleme hazırlanıyor…" else "Şifre doğrulanıyor…")
                    }
                    else -> Unit
                }
                if (setup is CloudSyncSetupDialog.Failed) {
                    Text(setup.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (cloud != null && setup != CloudSyncSetupDialog.Enabling) {
                    Text(
                        if (isNew)
                            "Diğer cihazlarında da bu şifreyi gireceksin. Şifren sunucuya gönderilmez; " +
                                    "unutursan buluttaki eşitleme verilerin açılamaz. Güvenli bir yere not et."
                        else
                            "Bu hesap başka bir cihazda eşitleniyor. O cihazda belirlediğin eşitleme şifresini gir.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Eşitleme şifresi (en az 12 karakter)") },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (visible) "Şifreyi gizle" else "Şifreyi göster")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isNew) {
                        OutlinedTextField(
                            value = repeat,
                            onValueChange = { repeat = it },
                            label = { Text("Şifre tekrar") },
                            singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = repeat.isNotEmpty() && repeat != password,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (password.isNotEmpty() && !lengthOk) {
                        Text("Şifre en az 12 karakter olmalı.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (setup is CloudSyncSetupDialog.Failed && cloud == null) {
                TextButton(onClick = onRetry) { Text("Tekrar dene") }
            } else {
                TextButton(
                    onClick = { onEnable(password); password = ""; repeat = "" },
                    enabled = canSubmit && setup != CloudSyncSetupDialog.Enabling
                ) { Text("Eşitlemeyi aç") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = setup != CloudSyncSetupDialog.Enabling) { Text("Vazgeç") }
        }
    )
}

/** "az önce", "12 dk önce", "3 saat önce", "2 gün önce". */
internal fun relativeTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - atMillis).coerceAtLeast(0)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "az önce"
        minutes < 60 -> "$minutes dk önce"
        minutes < 60 * 24 -> "${minutes / 60} saat önce"
        else -> "${minutes / (60 * 24)} gün önce"
    }
}
