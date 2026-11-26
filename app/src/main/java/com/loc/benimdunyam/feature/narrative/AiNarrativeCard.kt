package com.loc.benimdunyam.feature.narrative

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.ai.narrative.NarrativeScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bir dönemin, yılın ya da kişinin yapay zekâyla yazılmış hikâyesi.
 * Yapay zekâ kapalıysa hiçbir şey çizmez. Yazı yalnızca kullanıcı isteyince üretilir.
 */
@Composable
fun AiNarrativeCard(
    scope: NarrativeScope,
    onOpenJournal: (String) -> Unit,
    modifier: Modifier = Modifier,
    heading: String = defaultHeading(scope),
    viewModel: AiNarrativeViewModel = hiltViewModel(key = "narrative-${scope.key}")
) {
    LaunchedEffect(scope) { viewModel.bind(scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    var showSources by remember(scope.key) { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("tr-TR")) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer)

            when {
                state.generating -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Anıların okunuyor, hikâye yazılıyor…", style = MaterialTheme.typography.bodyMedium)
                }
                state.text != null -> {
                    Text(state.text!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    if (state.stale) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bu yazıdan sonra anıların değişti.", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
                            TextButton(onClick = viewModel::generate, enabled = state.enough) { Text("Yenile") }
                        }
                    }
                    if (state.sources.isNotEmpty()) {
                        TextButton(onClick = { showSources = !showSources }) {
                            Text(if (showSources) "Kaynakları gizle" else "Kaynak anılar (${state.sources.size})")
                        }
                        if (showSources) {
                            HorizontalDivider()
                            state.sources.forEach { s ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenJournal(s.journalId) }.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("[${s.number}]", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    Column(Modifier.weight(1f)) {
                                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        s.dateMillis?.let {
                                            Text(dateFormat.format(Date(it)), style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                !state.enough -> Text(
                    "Hikâye yazılabilmesi için burada en az 2 anı olmalı.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                else -> {
                    Text(
                        "${state.memoryCount} anını okuyup ${subject(scope)} kısa bir yazıyla anlatabilirim. " +
                            "İlgili anılar Claude yapay zekâsına gönderilir; yazı yalnızca bu cihazda saklanır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Button(onClick = viewModel::generate) { Text("Hikâyesini yaz") }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun defaultHeading(scope: NarrativeScope): String = when (scope) {
    is NarrativeScope.Period -> "✨ Bu dönemin hikâyesi"
    is NarrativeScope.Year -> "✨ ${scope.year} yılının hikâyesi"
    is NarrativeScope.Person -> "✨ ${scope.name} ile hikâyeniz"
}

private fun subject(scope: NarrativeScope): String = when (scope) {
    is NarrativeScope.Period -> "bu dönemi"
    is NarrativeScope.Year -> "bu yılı"
    is NarrativeScope.Person -> "${scope.name} ile ortak hikâyenizi"
}
