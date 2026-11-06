package com.loc.benimdunyam.feature.people.detail.components.special

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.components.ProfileSectionTitle
import com.loc.benimdunyam.feature.people.detail.model.PersonSpecialMemoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PersonSpecialMemorySection(
    memories: List<PersonSpecialMemoryItem>,
    hasAnyJournal: Boolean,
    isSaving: Boolean,
    onAdd: () -> Unit,
    onOpenJournal: (String) -> Unit,
    onEdit: (PersonSpecialMemoryItem) -> Unit,
    onRemove: (PersonSpecialMemoryItem) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR")) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                ProfileSectionTitle(
                    title = "⭐ Unutulmaz Anılar",
                    subtitle = "Bu kişiyle yaşadığın en özel anlar · ${memories.size} anı"
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAdd, enabled = hasAnyJournal && !isSaving) {
                Text("Ekle")
            }
        }

        if (memories.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (hasAnyJournal) {
                        "Henüz unutulmaz olarak işaretlenen bir anı yok. Bu karakterle yaşadığın anılardan birini seçebilirsin."
                    } else {
                        "Önce bu kişiyle ilişkili bir günlük anısı oluşturmalısın."
                    },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            memories.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenJournal(item.journal.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "${item.memory.category.symbol()} ${item.memory.category.displayName()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = item.journal.title.ifBlank { "Başlıksız Anı" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateFormat.format(Date(item.journal.entryDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        item.memory.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { onEdit(item) }, enabled = !isSaving) {
                                Text("Düzenle")
                            }
                            TextButton(onClick = { onRemove(item) }, enabled = !isSaving) {
                                Text("İşareti kaldır")
                            }
                        }
                    }
                }
            }
        }
    }
}
