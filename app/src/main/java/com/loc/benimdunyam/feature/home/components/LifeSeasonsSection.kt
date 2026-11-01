package com.loc.benimdunyam.feature.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loc.benimdunyam.domain.model.LifeSeason

@Composable
fun LifeSeasonsSection(
    seasons: List<LifeSeason>,
    activeSeasonId: String? = null,
    onSeeAllClick: () -> Unit,
    onSeasonClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yaşam Dönemleri",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF332D27)
            )

            TextButton(
                onClick = onSeeAllClick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 2.dp,
                    vertical = 0.dp
                )
            ) {
                Text(
                    text = "Yönet / Ekle",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF63816C)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF63816C),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        if (seasons.isEmpty()) {
            Surface(
                onClick = onSeeAllClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFEDE6DC),
                border = BorderStroke(1.dp, Color(0xFFE3D9CC))
            ) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz bir yaşam dönemin yok. İlk dönemini oluşturmak için dokun ✨",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF756D62)
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(seasons, key = { it.id }) { season ->
                    val selected = season.id == activeSeasonId
                    MinimalSeasonCard(
                        season = season,
                        isSelected = selected,
                        hasActiveSelection = activeSeasonId != null,
                        onClick = { onSeasonClick(season.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalSeasonCard(
    season: LifeSeason,
    isSelected: Boolean = false,
    hasActiveSelection: Boolean = false,
    onClick: () -> Unit
) {
    val cardWidth = animateDpAsState(
        targetValue = when {
            isSelected -> 160.dp
            hasActiveSelection -> 122.dp
            else -> 142.dp
        },
        label = "seasonCardWidth"
    )
    val cardHeight = animateDpAsState(
        targetValue = if (isSelected) 112.dp else 100.dp,
        label = "seasonCardHeight"
    )

    val shape = RoundedCornerShape(if (isSelected) 22.dp else 19.dp)
    val borderColor = if (isSelected) Color(0xFFE2B76F) else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth.value)
            .height(cardHeight.value)
            .animateContentSize(),
        shape = shape,
        color = Color.Transparent,
        contentColor = Color.White,
        border = if (isSelected) BorderStroke(2.5.dp, borderColor) else null,
        shadowElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            if (!season.coverImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = season.coverImagePath,
                    contentDescription = "${season.title} kapak görseli",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (isSelected) {
                                    listOf(
                                        Color(0xFFD9D4C6),
                                        Color(0xFF8D927B),
                                        Color(0xFF465647)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFFE9E3EB),
                                        Color(0xFFBDB5C1),
                                        Color(0xFF69646D)
                                    )
                                }
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = if (isSelected) 0.08f else 0.02f),
                                Color.Black.copy(alpha = if (isSelected) 0.64f else 0.58f)
                            )
                        )
                    )
            )

            if (isSelected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFFFF8E8).copy(alpha = 0.96f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF52745B),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "ŞU AN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF52745B)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = season.title,
                    style = if (isSelected) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    },
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) {
                    Text(
                        text = "Yaşam hikâyenin bu bölümü",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
