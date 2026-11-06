package com.loc.benimdunyam.feature.people.detail.components.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.components.EmptyProfileMessage
import com.loc.benimdunyam.feature.people.detail.components.ProfileSectionTitle
import com.loc.benimdunyam.feature.people.detail.model.PersonConnectionItem

@Composable
fun PersonConnectionsSection(
    connections: List<PersonConnectionItem>,
    onSelect: (String) -> Unit
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileSectionTitle(
            title = "Birlikte Göründüğü Karakterler",
            subtitle = "Günlüklerinde aynı anılarda geçen kişiler."
        )
        if (connections.isEmpty()) {
            EmptyProfileMessage("Henüz başka biriyle aynı anıda yer almıyor.")
        } else {
            (if (showAll) connections else connections.take(4)).forEach { item ->
                PersonConnectionCard(item = item, onClick = { onSelect(item.personId) })
            }
            if (connections.size > 4) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Daha az göster" else "Tüm ${connections.size} karakteri göster")
                }
            }
            Text(
                "Ortak görünme sayısı, kişiler arasındaki ilişkinin yakınlığını ölçmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
