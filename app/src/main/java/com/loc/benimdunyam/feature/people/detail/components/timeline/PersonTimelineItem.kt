package com.loc.benimdunyam.feature.people.detail.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun PersonTimelineItem(
    appearance: PersonAppearance,
    dateFormat: SimpleDateFormat,
    isFirstAppearance: Boolean,
    isLastAppearance: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val markerCenterColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
        ) {
            val centerX = size.width / 2f
            drawLine(
                color = lineColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = markerColor,
                radius = 5.dp.toPx(),
                center = Offset(centerX, 20.dp.toPx())
            )
            drawCircle(
                color = markerCenterColor,
                radius = 2.dp.toPx(),
                center = Offset(centerX, 20.dp.toPx())
            )
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dateFormat.format(Date(appearance.journal.entryDate)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    val appearanceLabel = when {
                        isFirstAppearance && isLastAppearance -> "İlk ve son anı"
                        isFirstAppearance -> "İlk görünme"
                        isLastAppearance -> "Son görünme"
                        else -> null
                    }

                    if (appearanceLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(50.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = appearanceLabel,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text(
                    text = appearance.journal.title.ifBlank { "Başlıksız anı" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (appearance.journal.content.isNotBlank()) {
                    Text(
                        text = appearance.journal.content.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }

                val episodeLabel = appearance.episodeTitle?.let { title ->
                    appearance.episodeOrderIndex?.let { order ->
                        "$order. Bölüm: $title"
                    } ?: title
                }

                val locationLabel = listOfNotNull(
                    appearance.seasonTitle,
                    episodeLabel
                ).joinToString(" • ")

                if (locationLabel.isNotBlank()) {
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
