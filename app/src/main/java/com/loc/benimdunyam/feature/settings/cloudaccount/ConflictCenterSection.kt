package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.data.sync.remote.JournalConflictCard
import com.loc.benimdunyam.domain.sync.ConflictChoice
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Aşama 2 — kullanıcının gördüğü çakışma ekranı.
 *
 * Şifre istemez (eşitleme anahtarı cihazda saklıdır), açılınca kendisi tarar.
 * Her çakışma iki sütunda gösterilir: "Bu telefondaki" ve "Diğer cihazdaki".
 * Kullanıcı birini seçer; istersen ikisi de saklanır. Hiçbir metin onaysız kaybolmaz.
 */
@Composable
internal fun ConflictCenterSection(
    uid: String,
    viewModel: ConflictCenterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<JournalConflictCard, ConflictChoice>?>(null) }

    LaunchedEffect(uid) { viewModel.select(uid) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            state.loading && state.cards.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Anıların karşılaştırılıyor…")
            }
            state.loaded && state.cards.isEmpty() && state.error == null -> Card(
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Her şey eşit. Karar vermen gereken bir anı yok.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.cards.forEach { card ->
            ConflictCompareCard(
                card = card,
                working = state.workingOn == card.journalId,
                enabled = state.workingOn == null,
                onChoose = { choice ->
                    // Metni ortadan kaldıran seçimler için kısa bir onay; diğerleri doğrudan.
                    if (choice == ConflictChoice.TAKE_OTHER_DEVICE && !card.localDeleted ||
                        choice == ConflictChoice.MOVE_TO_TRASH) pending = card to choice
                    else viewModel.choose(card, choice)
                }
            )
        }

        if (state.technicalOnly > 0) {
            Text(
                "${state.technicalOnly} kayıt teknik bir uyuşmazlık içeriyor ve burada çözülemiyor. " +
                        "Anıların cihazında güvende; bir sonraki eşitlemede yeniden denenecek.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = viewModel::dismissMessage))
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = viewModel::dismissMessage))
        }
        if (state.loaded && !state.loading) {
            TextButton(onClick = viewModel::load, enabled = state.workingOn == null,
                modifier = Modifier.fillMaxWidth()) { Text("Yeniden kontrol et") }
        }
    }

    pending?.let { (card, choice) ->
        val title = card.localTitle ?: card.remoteTitle ?: "Bu anı"
        AlertDialog(
            onDismissRequest = { pending = null },
            title = {
                Text(if (choice == ConflictChoice.MOVE_TO_TRASH) "Son silinenler'e taşınsın mı?"
                else "Diğer cihazdaki sürüm alınsın mı?")
            },
            text = {
                Text(
                    if (choice == ConflictChoice.MOVE_TO_TRASH)
                        "\"$title\" bu telefondan kaldırılıp 30 gün Son silinenler'de tutulacak. " +
                                "Bu süre içinde geri getirebilirsin."
                    else
                        "\"$title\" bu telefondaki metni, diğer cihazdaki metinle değişecek. " +
                                "Bu telefondaki metni de saklamak istersen \"İkisini de sakla\"yı seç."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.choose(card, choice); pending = null }) {
                    Text(if (choice == ConflictChoice.MOVE_TO_TRASH) "Taşı" else "Diğerini al")
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun ConflictCompareCard(
    card: JournalConflictCard,
    working: Boolean,
    enabled: Boolean,
    onChoose: (ConflictChoice) -> Unit
) {
    val remoteDeleted = card.remoteTitle == null && card.remoteExcerpt == null
    val headline = when {
        remoteDeleted -> "Diğer cihazda silindi"
        card.localDeleted -> "Bu telefonda sildin, diğer cihazda düzenlendi"
        else -> "İki cihazda farklı düzenlendi"
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                card.localTitle ?: card.remoteTitle ?: "Başlıksız anı",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(headline, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VersionColumn(
                    label = "Bu telefondaki",
                    deleted = card.localDeleted,
                    excerpt = card.localExcerpt,
                    updatedAt = card.localUpdatedAt,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                VersionColumn(
                    label = "Diğer cihazdaki",
                    deleted = remoteDeleted,
                    excerpt = card.remoteExcerpt,
                    updatedAt = card.remoteUpdatedAt,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Uygulanıyor…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                ChoiceButtons(card = card, enabled = enabled, onChoose = onChoose)
            }
        }
    }
}

@Composable
private fun VersionColumn(
    label: String,
    deleted: Boolean,
    excerpt: String?,
    updatedAt: Long?,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (deleted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Silindi", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            } else {
                Text(
                    excerpt?.let { "“$it…”" } ?: "Metin yok",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6, overflow = TextOverflow.Ellipsis
                )
            }
            updatedAt?.takeIf { it > 0 }?.let {
                Text(formatDate(it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoiceButtons(card: JournalConflictCard, enabled: Boolean, onChoose: (ConflictChoice) -> Unit) {
    val choices = card.choices
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (ConflictChoice.KEEP_THIS_DEVICE in choices) {
                Button(onClick = { onChoose(ConflictChoice.KEEP_THIS_DEVICE) }, enabled = enabled,
                    modifier = Modifier.weight(1f)) {
                    Text(if (card.localDeleted) "Silinmiş kalsın" else "Bunu tut")
                }
            }
            if (ConflictChoice.TAKE_OTHER_DEVICE in choices) {
                OutlinedButton(onClick = { onChoose(ConflictChoice.TAKE_OTHER_DEVICE) }, enabled = enabled,
                    modifier = Modifier.weight(1f)) {
                    Text(if (card.localDeleted) "Geri getir" else "Bunu tut")
                }
            }
            if (ConflictChoice.MOVE_TO_TRASH in choices) {
                OutlinedButton(onClick = { onChoose(ConflictChoice.MOVE_TO_TRASH) }, enabled = enabled,
                    modifier = Modifier.weight(1f)) { Text("Ben de sileyim") }
            }
        }
        if (ConflictChoice.KEEP_BOTH in choices) {
            TextButton(onClick = { onChoose(ConflictChoice.KEEP_BOTH) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) { Text("İkisini de sakla") }
        }
    }
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("tr-TR"))
        .format(Date(millis))
