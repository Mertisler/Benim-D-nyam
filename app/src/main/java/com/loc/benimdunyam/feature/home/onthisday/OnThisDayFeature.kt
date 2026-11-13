package com.loc.benimdunyam.feature.home.onthisday

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.io.File

private val MemoryInk get() = themedColor(Color(0xFF4B392F))
private val MemoryMuted get() = themedColor(Color(0xFF826E5F))
private val MemoryPaper get() = themedColor(Color(0xFFFFFBF5))
private val MemoryAccent get() = themedColor(Color(0xFF6A806E))

/**
 * HomeScreen LazyColumn'ına tek item olarak eklenir.
 * Eski yıllarda aynı ay ve gün içinde anı yoksa kendini gizler.
 */
@Composable
fun OnThisDayFeature(
    onNavigateToJournalDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnThisDayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Ana sayfaya geri gelince (ve uygulama tekrar öne çıktığında) bugünü güncelle.
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val memories = uiState.memories
    // Tarih eşleşmesi yoksa kart gösterilmez; yeni kullanıcı için boş alan oluşturmaz.
    if (uiState.isLoading || uiState.errorMessage != null || memories.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MemoryPaper),
        border = BorderStroke(1.dp, themedColor(Color(0xFFE8DDCF)))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = themedColor(Color(0xFFE7EEE6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MemoryAccent,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bu Tarihte Ne Olmuştu?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MemoryInk
                    )
                    Text(
                        "${uiState.todayLabel} • Geçmişten ${memories.size} anı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MemoryMuted
                    )
                }
            }
            memories.take(2).forEachIndexed { index, memory ->
                if (index > 0) {
                    HorizontalDivider(color = MemoryInk.copy(alpha = 0.10f))
                }
                OnThisDayMemoryRow(
                    memory = memory,
                    coverPath = uiState.coverPaths[memory.journal.id],
                    onClick = { onNavigateToJournalDetail(memory.journal.id) }
                )
            }
            if (memories.size > 2) {
                TextButton(
                    onClick = { showAll = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tüm anıları gör (${memories.size})", color = MemoryAccent)
                }
            }
        }
    }

    if (showAll) {
        Dialog(onDismissRequest = { showAll = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MemoryPaper
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Bugünün Geçmiş Anıları",
                                style = MaterialTheme.typography.titleLarge,
                                color = MemoryInk,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(uiState.todayLabel, color = MemoryMuted)
                        }
                        IconButton(onClick = { showAll = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Kapat")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 540.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(memories, key = { it.journal.id }) { memory ->
                            OnThisDayMemoryRow(
                                memory = memory,
                                coverPath = uiState.coverPaths[memory.journal.id],
                                onClick = {
                                    showAll = false
                                    onNavigateToJournalDetail(memory.journal.id)
                                }
                            )
                            HorizontalDivider(color = MemoryInk.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnThisDayMemoryRow(
    memory: OnThisDayMemory,
    coverPath: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 70.dp, height = 82.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = "${memory.journal.title} anısının kapağı",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Surface(
                    color = themedColor(Color(0xFFF1E6D9)),
                    modifier = Modifier.matchParentSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${memory.year}", style = MaterialTheme.typography.titleSmall, color = MemoryMuted)
                    }
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${memory.yearsAgo} yıl önce bugün",
                color = MemoryAccent,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                memory.journal.title.ifBlank { "Başlıksız anı" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MemoryInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (memory.journal.content.isNotBlank()) {
                Text(
                    memory.journal.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MemoryMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
