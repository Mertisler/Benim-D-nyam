package com.loc.benimdunyam.feature.home.components

import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loc.benimdunyam.feature.home.SmartMemoryItem
import com.loc.benimdunyam.feature.home.insights.LifeStoryInsight

private val TunnelInk get() = AppThemeController.palette.ink
private val TunnelPine get() = AppThemeController.palette.primary
private val TunnelMuted get() = AppThemeController.palette.muted
private val TunnelBorder get() = AppThemeController.palette.border
private val TunnelPaper get() = AppThemeController.palette.paper

private sealed interface TunnelCard {
    data class Memory(val value: SmartMemoryItem) : TunnelCard
    data class Insight(val value: LifeStoryInsight) : TunnelCard
}

/**
 * Geçmiş anılar + önceki Keşifler penceresindeki tüm içgörüler aynı kaydırılabilir alanda.
 * İlk kart eski anıyı hatırlatır; ardından hikâye içgörüleri, en sonda dönem kartları gelir.
 * "Tümü" artık boş bir eylem değil, bu kartların okunabilir bir listesini açar.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmartMemoryCarousel(
    memories: List<SmartMemoryItem>,
    insights: List<LifeStoryInsight> = emptyList(),
    onJournalClick: (String) -> Unit,
    onSeasonClick: () -> Unit
) {
    val cards = remember(memories, insights) {
        buildList<TunnelCard> {
            memories.filterIsInstance<SmartMemoryItem.OnThisDay>().forEach {
                add(TunnelCard.Memory(it))
            }
            insights.forEach { add(TunnelCard.Insight(it)) }
            memories.filterIsInstance<SmartMemoryItem.SeasonInsight>().forEach {
                add(TunnelCard.Memory(it))
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { cards.size })
    var showAll by rememberSaveable { mutableStateOf(false) }

    // Kullanıcı anı sildiğinde geçerli sayfa sınırlar dışında kalmasın.
    LaunchedEffect(cards.size) {
        if (cards.isNotEmpty() && pagerState.currentPage >= cards.size) {
            pagerState.scrollToPage(cards.lastIndex)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "✨ Zaman Tüneli & Anılar",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TunnelInk
            )
            if (cards.isNotEmpty()) {
                TextButton(
                    onClick = { showAll = true },
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Tümü",
                        style = MaterialTheme.typography.labelMedium,
                        color = themedColor(Color(0xFF63816C))
                    )
                }
            }
        }

        if (cards.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(23.dp),
                color = themedColor(Color(0xFFF0EAE1)),
                border = BorderStroke(1.dp, TunnelBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🌱 Hikâyen burada filizlenecek", color = TunnelInk,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Anı biriktirdikçe eski günlerin ve hikâyenden küçük ipuçları burada görünecek.",
                        color = TunnelMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 2.dp),
                pageSpacing = 11.dp,
                modifier = Modifier.fillMaxWidth().height(137.dp)
            ) { page ->
                when (val item = cards[page]) {
                    is TunnelCard.Memory -> when (val memory = item.value) {
                        is SmartMemoryItem.OnThisDay -> OnThisDayCard(memory, onJournalClick)
                        is SmartMemoryItem.SeasonInsight -> SeasonMemoryCard(memory, onSeasonClick)
                    }
                    is TunnelCard.Insight -> StoryInsightCard(item.value, onJournalClick)
                }
            }

            if (cards.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cards.size <= 7) {
                        repeat(cards.size) { index ->
                            val selected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .width(if (selected) 15.dp else 5.dp)
                                    .height(5.dp)
                                    .background(
                                        color = if (selected) themedColor(Color(0xFF6F927B)) else themedColor(Color(0xFFCFC6B9)),
                                        shape = CircleShape
                                    )
                            )
                        }
                    } else {
                        // Çok sayıda keşif olduğunda uzun nokta dizisi ekrana taşmasın.
                        Text(
                            text = "${pagerState.currentPage + 1} / ${cards.size}",
                            color = TunnelMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    if (showAll && cards.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showAll = false },
            containerColor = themedColor(Color(0xFFFFFBF5))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Zaman Tüneli & Anılar",
                    color = TunnelInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${cards.size} hatıra ve hikâye ipucu",
                    color = TunnelMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(cards) { _, item ->
                        val title: String
                        val description: String
                        val emoji: String
                        val navigate: (() -> Unit)?
                        when (item) {
                            is TunnelCard.Insight -> {
                                title = item.value.title
                                description = item.value.description
                                emoji = item.value.emoji
                                navigate = item.value.journalId?.let { id ->
                                    { onJournalClick(id) }
                                }
                            }
                            is TunnelCard.Memory -> when (val memory = item.value) {
                                is SmartMemoryItem.OnThisDay -> {
                                    title = memory.title
                                    description = memory.dateText
                                    emoji = if (memory.yearsAgo > 0) "🕰️" else "📖"
                                    navigate = { onJournalClick(memory.journalId) }
                                }
                                is SmartMemoryItem.SeasonInsight -> {
                                    title = memory.season.title
                                    description = "Bu dönemde ${memory.journalCount} anı bulunuyor."
                                    emoji = "🎞️"
                                    navigate = onSeasonClick
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().then(
                                if (navigate != null) Modifier.clickable {
                                    showAll = false
                                    navigate()
                                } else Modifier
                            ),
                            shape = RoundedCornerShape(16.dp),
                            color = TunnelPaper,
                            border = BorderStroke(1.dp, TunnelBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleLarge)
                                Column(modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(title,
                                        color = TunnelInk,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(description,
                                        color = TunnelMuted,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (navigate != null) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Aç",
                                        tint = TunnelPine
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnThisDayCard(
    memory: SmartMemoryItem.OnThisDay,
    onJournalClick: (String) -> Unit
) {
    Card(
        onClick = { onJournalClick(memory.journalId) },
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = themedColor(Color(0xFFF0EAE1))),
        border = BorderStroke(1.dp, TunnelBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(50), color = themedColor(Color(0xFFD8EDE1))) {
                    Text(
                        text = if (memory.yearsAgo > 0)
                            "📅  ${memory.yearsAgo} yıl önce bugün" else "📖 Arşivden bir anı",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = themedColor(Color(0xFF365C49))
                    )
                }
                Text(memory.dateText,
                    style = MaterialTheme.typography.labelMedium,
                    color = themedColor(Color(0xFF888176)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(memory.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = themedColor(Color(0xFF4B443B)), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(memory.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TunnelMuted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StoryInsightCard(
    insight: LifeStoryInsight,
    onJournalClick: (String) -> Unit
) {
    // Kayıt bağlantısı bulunan içgörüler tıklanabilir; bilgi kartları pasiftir.
    Card(
        modifier = Modifier.fillMaxSize().then(
            insight.journalId?.let { journalId ->
                Modifier.clickable { onJournalClick(journalId) }
            } ?: Modifier
        ),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = themedColor(Color(0xFFE9F0E9))),
        border = BorderStroke(1.dp, themedColor(Color(0xFFD8E4D9))),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(50), color = themedColor(Color(0xFFD8E8DA))) {
                    Text(
                        "✦  HİKÂYENDEN",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = TunnelPine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    )
                }
                Text(insight.emoji, style = MaterialTheme.typography.titleMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(insight.title,
                    color = TunnelInk,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(insight.description,
                    color = TunnelMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SeasonMemoryCard(
    memory: SmartMemoryItem.SeasonInsight,
    onSeasonClick: () -> Unit
) {
    Card(
        onClick = onSeasonClick,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = themedColor(Color(0xFFE8E0D4))),
        border = BorderStroke(1.dp, themedColor(Color(0xFFE1D7C9))),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!memory.season.coverImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = memory.season.coverImagePath,
                    contentDescription = memory.season.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(themedColor(Color(0xFFE8E1D8)), themedColor(Color(0xFFB9B1A7))))
                    )
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.68f)
                        )
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(15.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.34f)
                ) {
                    Text(
                        text = "✦  ${memory.season.title}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val insightText = if (memory.topPersonName != null) {
                        "Bu dönemin en çok anılan kişisi: ${memory.topPersonName}"
                    } else {
                        "Bu dönemde ${memory.journalCount} günlük yazıldı."
                    }
                    Text(
                        text = insightText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Dönemi keşfet →",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}
