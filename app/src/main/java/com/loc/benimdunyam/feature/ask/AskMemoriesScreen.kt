package com.loc.benimdunyam.feature.ask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.ai.history.AskedQuestion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr-TR")

/**
 * "Anılarına sor": soruyu yaz, cevap kaynak anılarıyla gelsin.
 * Yapay zekâ kapalıysa Ayarlar'a yönlendirir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskMemoriesScreen(
    onNavigateBack: () -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AskMemoriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Anılarına sor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.enabled) {
                item(key = "off") { DisabledCard(state.hasAccount, onOpenSettings) }
                return@LazyColumn
            }
            if (!state.hasJournals) {
                item(key = "empty") {
                    Text("Henüz anın yok. Birkaç anı yazdıktan sonra onlara soru sorabilirsin.",
                        style = MaterialTheme.typography.bodyLarge)
                }
                return@LazyColumn
            }

            item(key = "input") { QuestionInput(state, viewModel) }

            state.error?.let { message ->
                item(key = "error") {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.shown?.let { shown ->
                item(key = "answer") { AnswerCard(shown, onOpenJournal, viewModel::closeAnswer) }
            }

            if (state.shown == null && state.input.isBlank() && !state.busy) {
                item(key = "examples") { Examples(state.examples, viewModel::useExample) }
            }

            if (state.history.isNotEmpty()) {
                item(key = "history-title") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Önceki soruların", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmClear = true }) { Text("Temizle") }
                    }
                }
                items(state.history, key = { "h-${it.id}" }) { item ->
                    HistoryRow(item, onClick = { viewModel.show(item) }, onDelete = { viewModel.delete(item) })
                }
            }

            item(key = "note") {
                Text(
                    "Sorun ve ilgili olduğu düşünülen en fazla 12 anı, cevap için Claude yapay zekâsına gönderilir. " +
                        "Cevaplar yalnızca bulunan anılara dayanır ve yanılabilir; kaynaklara dokunarak kontrol edebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Soru geçmişi silinsin mi?") },
            text = { Text("Bu cihazdaki tüm önceki soruların ve cevapları silinir. Anıların etkilenmez.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun DisabledCard(hasAccount: Boolean, onOpenSettings: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💬 Anılarına soru sor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "\"Geçen yaz Ayşe ile nereye gitmiştik?\" gibi sorular sorabilir, cevabı ilgili anılarınla birlikte görebilirsin. " +
                    "Bunun için Ayarlar'dan yapay zekâ özelliklerini açman gerekir.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!hasAccount) {
                Text("Yapay zekâ için bulut hesabınla giriş yapman da gerekir.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onOpenSettings) { Text("Ayarlar'a git") }
        }
    }
}

@Composable
private fun QuestionInput(state: AskMemoriesUiState, viewModel: AskMemoriesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::onInput,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            placeholder = { Text("Anılarına bir soru sor…") },
            minLines = 2,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.ask() })
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp).size(20.dp), strokeWidth = 2.dp)
                Text(
                    if (state.step == AskStep.SEARCHING) "Anıların aranıyor…" else "Cevap yazılıyor…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::cancel) { Text("Vazgeç") }
            } else {
                Spacer(Modifier.weight(1f))
                Button(onClick = viewModel::ask, enabled = state.input.isNotBlank()) { Text("Sor") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Examples(examples: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Örnek sorular", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { e -> AssistChip(onClick = { onPick(e) }, label = { Text(e) }) }
        }
    }
}

@Composable
private fun AnswerCard(shown: ShownAnswer, onOpenJournal: (String) -> Unit, onClose: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", TR) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(shown.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton(onClick = onClose) { Text("Kapat") }
            }
            Text(shown.answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (shown.sources.isNotEmpty()) {
                HorizontalDivider()
                Text("Kaynak anılar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                shown.sources.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenJournal(s.journalId) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[${s.number}]", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            s.dateMillis?.let {
                                Text(dateFormat.format(Date(it)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("›", color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (shown.notFound) {
                Text("Farklı kelimelerle ya da bir kişiyi adıyla anarak tekrar sormayı deneyebilirsin.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun HistoryRow(item: AskedQuestion, onClick: () -> Unit, onDelete: () -> Unit) {
    val format = remember { SimpleDateFormat("d MMM yyyy", TR) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.question, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(format.format(Date(item.askedAt)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onDelete) { Text("Sil") }
    }
}
