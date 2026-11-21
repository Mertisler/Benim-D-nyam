package com.loc.benimdunyam.feature.people.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.data.model.DeviceContactItem

/**
 * Mevcut çoklu rehber içe aktarmayı tek kompakt dialog içinde tutar.
 * Seçimler filtre araması değiştiğinde kaybolmaz. "Tümünü seç" tüm rehberi seçer.
 */
@Composable
fun SelectContactsDialog(
    contacts: List<DeviceContactItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var query by remember(contacts) { mutableStateOf("") }
    var selectedIds by remember(contacts) { mutableStateOf<Set<String>>(emptySet()) }

    val visibleContacts = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val allSelected = contacts.isNotEmpty() && contacts.all { it.id in selectedIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rehberden kişi seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Rehberde ara") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${selectedIds.size} seçili")
                    TextButton(onClick = {
                        selectedIds = if (allSelected) emptySet() else contacts.map { it.id }.toSet()
                    }) {
                        Text(if (allSelected) "Seçimi kaldır" else "Tümünü seç")
                    }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    if (visibleContacts.isEmpty()) {
                        item(key = "no-contacts-match") {
                            Text("Eşleşen kişi yok.", modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
                    items(visibleContacts, key = { it.id }) { contact ->
                        val isSelected = contact.id in selectedIds
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (isSelected) selectedIds - contact.id
                                else selectedIds + contact.id
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + contact.id
                                    else selectedIds - contact.id
                                }
                            )
                            Text(
                                contact.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty(),
                onClick = {
                    onConfirm(contacts.filter { it.id in selectedIds }.map { it.name })
                }
            ) { Text("Seçilenleri ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
