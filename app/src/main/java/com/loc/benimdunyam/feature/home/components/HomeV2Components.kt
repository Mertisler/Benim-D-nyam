package com.loc.benimdunyam.feature.home.components

import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.home.insights.LifeStoryInsightsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink get() = AppThemeController.palette.ink
private val SoftInk get() = AppThemeController.palette.muted
private val Pine get() = AppThemeController.palette.primary
private val CardPaper get() = AppThemeController.palette.paper
private val Line get() = AppThemeController.palette.border

/** Sade karşılama. Anı Yaz yalnızca ekranda kayan FriendlyActionFab üzerinden açılır. */
@Composable
fun HomeCompactHeader(
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hoş Geldin 👋",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(
                    "Bugün dünyanda neler oldu?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftInk
                )
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Anılarda ara", tint = Ink)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Ink)
            }
        }

    }
}

/** SmartMemoryCarousel'in doğrudan altında: eski büyük kartların işlevsel kısa yolları. */
@Composable
fun HomeQuickActions(
    hasSmartMemories: Boolean,
    onDiscoveries: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (!hasSmartMemories) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hikâyeni keşfet", color = Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Biriken her anı, zamanla hikâyenin bir parçası olur.",
                    style = MaterialTheme.typography.bodySmall, color = SoftInk)
            }
        }
        HomeQuickAction(
            title = "Keşifler",
            icon = { Icon(Icons.Default.Star, null, tint = Pine) },
            onClick = onDiscoveries,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HomeQuickAction(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(56.dp).clickable(onClick = onClick),
        color = CardPaper,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, color = Ink,
                modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Pine)
        }
    }
}

/** Yatay ve yüksek kartlar yerine, ekrandan taşmayan üç kısa anı satırı. */
@Composable
fun HomeRecentMemories(
    journals: List<JournalEntry>,
    onOpenJournal: (String) -> Unit,
    onWriteJournal: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Son Anılarım", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = Ink)

        if (journals.isEmpty()) {
            Surface(color = CardPaper, shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Line)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onWriteJournal)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = Pine)
                    Spacer(Modifier.width(12.dp))
                    Text("İlk anını yazarak hikâyene başla.", color = SoftInk,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, tint = Pine)
                }
            }
            return@Column
        }

        journals.take(3).forEachIndexed { index, journal ->
            HomeRecentJournalRow(
                journal = journal,
                onClick = { onOpenJournal(journal.id) }
            )
        }
    }
}

@Composable
private fun HomeRecentJournalRow(journal: JournalEntry, onClick: () -> Unit) {
    val date = remember(journal.entryDate) {
        SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr-TR"))
            .format(Date(journal.entryDate))
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = CardPaper,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = themedColor(Color(0xFFE6EEE7))) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Pine)
                }
            }
            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(date, style = MaterialTheme.typography.labelSmall, color = Pine)
                Text(
                    text = journal.title.ifBlank { "İsimsiz Anı" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (journal.content.isNotBlank()) {
                    Text(journal.content.replace("\n", " "), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = SoftInk)
                }
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null,
                tint = SoftInk, modifier = Modifier.size(17.dp))
        }
    }
}

/** Uzun Hikâyenden Keşifler kartı, kullanıcı istemedikçe ana sayfayı uzatmaz. */
@Composable
fun HomeDiscoveryDialog(
    onDismiss: () -> Unit,
    onOpenJournal: (String) -> Unit,
    viewModel: LifeStoryInsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val insights = state.report.insights.filterNot { it.id == "on_this_day" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = themedColor(Color(0xFFFFFBF5))
        ) {
            Column(modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hikâyenden Keşifler", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text("Anılarından çıkan küçük gerçekler",
                            style = MaterialTheme.typography.bodySmall, color = SoftInk)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Keşifleri kapat")
                    }
                }
                when {
                    state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(22.dp),
                        contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.errorMessage != null -> Text(
                        state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                    state.report.totalJournals == 0 -> Text(
                        "Anı yazdıkça hikâyenden keşifler burada birikecek.", color = SoftInk)
                    insights.isEmpty() -> Text(
                        "Henüz yeni bir içgörü bulunmuyor.", color = SoftInk)
                    else -> {
                        Text(
                            "${state.report.totalJournals} anı · Son 30 günde ${state.report.recentJournals}",
                            style = MaterialTheme.typography.labelMedium, color = Pine
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 480.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(insights, key = { it.id }) { insight ->
                                Row(modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = insight.journalId != null) {
                                        insight.journalId?.let(onOpenJournal)
                                    }
                                    .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(insight.emoji, style = MaterialTheme.typography.titleMedium)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(insight.title, fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium, color = Ink)
                                        Text(insight.description,
                                            style = MaterialTheme.typography.bodySmall, color = SoftInk)
                                        if (insight.journalId != null) {
                                            Text("Anıyı aç ›", color = Pine,
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                HorizontalDivider(color = Line)
                            }
                        }
                    }
                }
            }
        }
    }
}
