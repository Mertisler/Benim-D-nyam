package com.loc.benimdunyam.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Faz B — "Son silinenler".
 *
 * Başka cihazda silinen ve bu cihazda değiştirilmemiş anılar 30 gün burada
 * tutulur. Liste boşsa bölüm yalnızca kısa bir açıklama gösterir.
 */
@Composable
fun RecentlyDeletedSection(viewModel: RecentlyDeletedViewModel) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDeleteFor by remember { mutableStateOf<RecentlyDeletedItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("tr-TR"))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Son silinenler", style = MaterialTheme.typography.titleMedium)
            Text(
                "Başka bir cihazda silinen anılar burada 30 gün tutulur, sonra fotoğraflarıyla " +
                        "birlikte kalıcı olarak silinir. Geri getirdiğin anı diğer cihazlara da döner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (items.isEmpty()) {
                Text("Silinen anı yok.", style = MaterialTheme.typography.bodyMedium)
            }

            val visible = if (expanded) items else items.take(5)
            visible.forEach { item ->
                HorizontalDivider()
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Silinme: ${dateFormat.format(Date(item.trashedAt))} · " +
                            if (item.daysLeft > 0) "${item.daysLeft} gün sonra kalıcı silinecek"
                            else "Bugün kalıcı silinecek",
                    style = MaterialTheme.typography.bodySmall
                )
                val busy = state.busyJournalId != null
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { viewModel.restore(item.journalId) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Geri getir") }
                    TextButton(
                        onClick = { confirmDeleteFor = item },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Kalıcı sil", color = MaterialTheme.colorScheme.error) }
                }
            }
            if (items.size > 5) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Daha az göster" else "Tümünü göster (${items.size})")
                }
            }

            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    confirmDeleteFor?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            title = { Text("Kalıcı olarak silinsin mi?") },
            text = {
                Text("\"${item.title}\" ve fotoğrafları bu cihazdan kalıcı olarak silinecek. " +
                        "Bu işlem geri alınamaz.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanently(item.journalId, confirmed = true)
                    confirmDeleteFor = null
                }) { Text("Kalıcı sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFor = null }) { Text("Vazgeç") }
            }
        )
    }
}
