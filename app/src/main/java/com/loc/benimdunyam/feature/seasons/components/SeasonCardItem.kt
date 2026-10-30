package com.loc.benimdunyam.feature.seasons.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // 1. Clickable import edildi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loc.benimdunyam.feature.seasons.LifeSeasonSummary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SeasonCardItem(
    summary: LifeSeasonSummary,
    onClick: () -> Unit // <-- 2. onClick parametresi eklendi
) {
    val season = summary.season
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
    val startDateStr = dateFormat.format(Date(season.startDate))
    val endDateStr = season.endDate?.let { dateFormat.format(Date(it)) } ?: "Devam Ediyor"

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }, // <-- 3. Kart tıklanabilir yapıldı
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Kapak Görseli veya Varsayılan Arka Plan
            if (!season.coverImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = season.coverImagePath,
                    contentDescription = season.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // 2. Alttan Koyu Degrade (Yazıların net okunması için Scrim)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    )
            )

            // 3. İçerik (Başlık, Tarih, İstatistikler)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = season.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                // <-- 4. Tarih string hatası tek satırda düzeltildi
                Text(
                    text = "📅 $startDateStr - $endDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )

                if (season.description.isNotBlank()) {
                    Text(
                        text = season.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }

                // İstatistik Rozetleri
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "${summary.journalCount} Günlük",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "${summary.totalWordCount} Kelime",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}