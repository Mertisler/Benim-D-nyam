package com.loc.benimdunyam.feature.people.detail.components.special

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.PersonSpecialMemory
import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory

@Composable
fun EditSpecialMemoryDialog(
    journal: JournalEntry,
    existing: PersonSpecialMemory?,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (PersonSpecialMemoryCategory, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryName by rememberSaveable(journal.id, existing?.category) {
        mutableStateOf((existing?.category ?: PersonSpecialMemoryCategory.UNFORGETTABLE).name)
    }
    var note by rememberSaveable(journal.id, existing?.updatedAt) {
        mutableStateOf(existing?.note.orEmpty())
    }
    val category = PersonSpecialMemoryCategory.values()
        .firstOrNull { it.name == categoryName }
        ?: PersonSpecialMemoryCategory.UNFORGETTABLE

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (existing == null) "Anıyı öne çıkar" else "Özel anıyı düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(journal.title.ifBlank { "Başlıksız Anı" }, fontWeight = FontWeight.Bold)
                Text(
                    text = "Bu işaret yalnızca bu kişi için geçerli olacak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Anının türü", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PersonSpecialMemoryCategory.values().toList()) { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { categoryName = option.name },
                            enabled = !isSaving,
                            label = { Text("${option.symbol()} ${option.displayName()}") }
                        )
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = note,
                    onValueChange = { if (it.length <= 280) note = it },
                    enabled = !isSaving,
                    label = { Text("Bu anı neden özel? (isteğe bağlı)") },
                    supportingText = { Text("${note.length}/280") },
                    minLines = 2,
                    maxLines = 4
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = { onSave(category, note.trim().takeIf { it.isNotBlank() }) }
            ) { Text(if (isSaving) "Kaydediliyor…" else "Kaydet") }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}
