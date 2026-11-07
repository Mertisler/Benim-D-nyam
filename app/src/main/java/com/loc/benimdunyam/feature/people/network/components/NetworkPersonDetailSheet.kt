package com.loc.benimdunyam.feature.people.network.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphEdge
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphNode
import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPersonDetailSheet(
    node: NetworkGraphNode,
    graph: PeopleNetworkGraph,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenSharedMemories: (NetworkGraphEdge) -> Unit
) {
    val connections = graph.edgesFor(node.id)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NetworkPersonAvatar(node.name, node.profileImagePath, 64.dp)
                Column {
                    Text(node.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${node.memoryCount} anı · ${connections.size} ortak karakter",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Bağlantılar yalnızca aynı günlüğe eklenmeyi gösterir; gerçek hayattaki yakınlığı ölçmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { onOpenProfile(node.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Karakter Profilini Aç")
            }
            Text("Birlikte yer aldığı karakterler", style = MaterialTheme.typography.titleMedium)
            if (connections.isEmpty()) {
                Text("Bu filtrede başka karakterle ortak anı bulunmuyor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(connections, key = { "${it.firstPersonId}:${it.secondPersonId}" }) { edge ->
                        val otherId = edge.otherPersonId(node.id) ?: return@items
                        val other = graph.node(otherId) ?: return@items
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onOpenSharedMemories(edge)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NetworkPersonAvatar(other.name, other.profileImagePath, 38.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(other.name, fontWeight = FontWeight.Medium)
                                Text("${edge.sharedMemoryCount} ortak anı",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Görüntüle", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
