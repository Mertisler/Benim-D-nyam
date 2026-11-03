
package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NavigationInk = Color(0xFF33271F)
private val NavigationMutedInk = Color(0xFF756755)
private val NavigationGreen = Color(0xFF365A43)
private val NavigationBackground = Color(0xFFE8DDC9)
private val NavigationDisabled = Color(0xFFECE6DC)

@Composable
fun JournalDetailNavigationBar(
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    currentPosition: Int,
    totalJournals: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationArrow(
            icon = Icons.Default.ChevronLeft,
            description = "Önceki günlük",
            enabled = canGoPrevious,
            onClick = onPrevious
        )

        Text(
            text = if (totalJournals > 0 && currentPosition > 0) {
                "%02d / %02d".format(
                    currentPosition,
                    totalJournals
                )
            } else {
                "— / —"
            },
            color = NavigationMutedInk,
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )

        NavigationArrow(
            icon = Icons.Default.ChevronRight,
            description = "Sonraki günlük",
            enabled = canGoNext,
            onClick = onNext
        )
    }
}

@Composable
private fun NavigationArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = if (enabled) {
            NavigationBackground
        } else {
            NavigationDisabled
        }
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) {
                    NavigationGreen
                } else {
                    NavigationMutedInk.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
