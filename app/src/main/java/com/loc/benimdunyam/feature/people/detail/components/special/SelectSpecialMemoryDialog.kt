package com.loc.benimdunyam.feature.people.detail.components.special

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.JournalEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SelectSpecialMemoryDialog(
    journals: List<JournalEntry>,
    alreadyMarkedIds: Set<String>,
    onChoose: (JournalEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")) }
    val available = remember(journals, alreadyMarkedIds, query) {
        journals.filter { journal ->
            journal.id !in alreadyMarkedIds &&
                    (query.isBlank() ||
                            journal.title.contains(query, ignoreCase = true) ||
                            journal.content.contains(query, ignoreCase = true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unutulmaz bir anı seç") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Anılarda ara") },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 330.dp),
                    state = rememberLazyListState()
                ) {
                    if (available.isEmpty()) {
                        item {
                            Text(
                                text = "Seçilebilecek başka anı bulunmuyor.",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    items(available, key = { it.id }) { journal ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(journal) }
                        ) {
                            Text(
                                text = journal.title.ifBlank { "Başlıksız Anı" },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = dateFormat.format(Date(journal.entryDate)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}
