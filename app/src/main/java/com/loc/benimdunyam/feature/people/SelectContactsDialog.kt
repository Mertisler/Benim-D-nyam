package com.loc.benimdunyam.feature.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.data.model.DeviceContactItem

@Composable
fun SelectContactsDialog(
    contacts: List<DeviceContactItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var contactListState by remember { mutableStateOf(contacts) }

    // Tüm kişilerin seçili olup olmadığını kontrol et
    val allSelected = contactListState.isNotEmpty() && contactListState.all { it.isSelected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rehberden Kişi Seç") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                Text(
                    text = "İçe aktarmak istediğiniz kişileri işaretleyin:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Tümünü Seç / Kaldır Satırı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newState = !allSelected
                            contactListState = contactListState.map { it.copy(isSelected = newState) }
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (allSelected) "Tümünü Kaldır" else "Tümünü Seç",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            contactListState = contactListState.map { it.copy(isSelected = checked) }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(contactListState, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    contactListState = contactListState.map {
                                        if (it.id == contact.id) it.copy(isSelected = !it.isSelected) else it
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Checkbox(
                                checked = contact.isSelected,
                                onCheckedChange = { checked ->
                                    contactListState = contactListState.map {
                                        if (it.id == contact.id) it.copy(isSelected = checked) else it
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedNames = contactListState.filter { it.isSelected }.map { it.name }
                    onConfirm(selectedNames)
                }
            ) {
                Text("Seçilenleri Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
