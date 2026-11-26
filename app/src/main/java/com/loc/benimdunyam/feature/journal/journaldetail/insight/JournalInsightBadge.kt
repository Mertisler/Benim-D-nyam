package com.loc.benimdunyam.feature.journal.journaldetail.insight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.ai.insight.InsightTagKind
import com.loc.benimdunyam.data.ai.insight.MemoryInsight

/**
 * Anı detayında ruh hali rozetinin yanında küçük "✨" rozeti. Yalnızca yapay zekâ açıksa
 * ve bu anının analizi varsa görünür; dokununca notlar alt pencerede açılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalInsightBadge(
    journalId: String,
    modifier: Modifier = Modifier,
    viewModel: JournalInsightViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(journalId) { viewModel.show(journalId) }
    var open by remember { mutableStateOf(false) }

    if (state.journalId != journalId) return
    val insight = state.insight ?: return
    val hasSuggestion = !state.hasMood && insight.moodSuggestion != null && !insight.moodDismissed

    Surface(
        onClick = { open = true },
        modifier = modifier.semantics { contentDescription = "Yapay zekâ notlarını aç" },
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFEDE7F6)
    ) {
        Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
            Text(if (hasSuggestion) "✨•" else "✨", fontSize = 22.sp)
        }
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            InsightSheet(
                insight = insight,
                showMoodSuggestion = hasSuggestion,
                onRemoveTag = viewModel::removeTag,
                onApplyMood = { insight.moodSuggestion?.let(viewModel::applyMood) },
                onDismissMood = viewModel::dismissMood,
                onHide = {
                    viewModel.hide()
                    open = false
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightSheet(
    insight: MemoryInsight,
    showMoodSuggestion: Boolean,
    onRemoveTag: (InsightTagKind, String) -> Unit,
    onApplyMood: () -> Unit,
    onDismissMood: () -> Unit,
    onHide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("✨ Yapay zekâ notları", style = MaterialTheme.typography.titleLarge)
        insight.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        if (showMoodSuggestion) {
            val mood = insight.moodSuggestion!!
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Bu anıda ruh hali seçilmemiş. Metne göre önerimiz: ${mood.emoji} ${mood.label}",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onApplyMood) { Text("Uygula") }
                        TextButton(onClick = onDismissMood) { Text("Önerme") }
                    }
                }
            }
        }

        TagGroup("Konular", insight.topics, InsightTagKind.TOPIC, onRemoveTag)
        TagGroup("Yerler", insight.places, InsightTagKind.PLACE, onRemoveTag)
        TagGroup("Olaylar", insight.events, InsightTagKind.EVENT, onRemoveTag)

        HorizontalDivider()
        Text(
            "Bu notlar anının metninden yapay zekâyla çıkarıldı ve yalnızca bu cihazda saklanır. " +
                "Yanlış bir etiketi üzerine dokunarak kaldırabilirsin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onHide) { Text("Bu anının notlarını sil") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(title: String, values: List<String>, kind: InsightTagKind, onRemove: (InsightTagKind, String) -> Unit) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { value ->
                AssistChip(
                    onClick = { onRemove(kind, value) },
                    label = { Text("$value  ✕") },
                    modifier = Modifier.semantics { contentDescription = "$value etiketini kaldır" }
                )
            }
        }
    }
}
