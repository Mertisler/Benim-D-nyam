package com.loc.benimdunyam.feature.people.detail.components.special

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory
import com.loc.benimdunyam.feature.people.detail.model.PersonSpecialMemoryItem

/** Owns dialog state; ViewModel and database work stay outside the component. */
@Composable
fun PersonSpecialMemoryFeature(
    journals: List<JournalEntry>,
    memories: List<PersonSpecialMemoryItem>,
    isSaving: Boolean,
    errorMessage: String?,
    onOpenJournal: (String) -> Unit,
    onSave: (String, PersonSpecialMemoryCategory, String?, () -> Unit) -> Unit,
    onRemove: (String, () -> Unit) -> Unit,
    onClearError: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var selectedJournal by remember { mutableStateOf<JournalEntry?>(null) }
    var editingItem by remember { mutableStateOf<PersonSpecialMemoryItem?>(null) }
    var removingItem by remember { mutableStateOf<PersonSpecialMemoryItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonSpecialMemorySection(
            memories = memories,
            hasAnyJournal = journals.isNotEmpty(),
            isSaving = isSaving,
            onAdd = {
                onClearError()
                showPicker = true
            },
            onOpenJournal = onOpenJournal,
            onEdit = { item ->
                onClearError()
                editingItem = item
            },
            onRemove = { item ->
                onClearError()
                removingItem = item
            }
        )
        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showPicker) {
        SelectSpecialMemoryDialog(
            journals = journals,
            alreadyMarkedIds = memories.map { it.journal.id }.toSet(),
            onChoose = { journal ->
                selectedJournal = journal
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    val editorJournal = selectedJournal ?: editingItem?.journal
    if (editorJournal != null) {
        EditSpecialMemoryDialog(
            journal = editorJournal,
            existing = editingItem?.memory,
            isSaving = isSaving,
            errorMessage = errorMessage,
            onSave = { category, note ->
                onSave(editorJournal.id, category, note) {
                    selectedJournal = null
                    editingItem = null
                }
            },
            onDismiss = {
                selectedJournal = null
                editingItem = null
                onClearError()
            }
        )
    }

    removingItem?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!isSaving) removingItem = null },
            title = { Text("Unutulmaz işaretini kaldır") },
            text = {
                Text("\"${selected.journal.title}\" artık bu kişinin özel anılarında görünmeyecek. Günlük anısı silinmez.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        onRemove(selected.journal.id) {
                            removingItem = null
                        }
                    }
                ) { Text("İşareti kaldır", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !isSaving, onClick = { removingItem = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
