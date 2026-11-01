package com.loc.benimdunyam.feature.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun HomeHeader(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
    ) {
        BotanicalCornerDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 82.dp, height = 76.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = "Hoş Geldin 👋",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF29251F)
                )
                Text(
                    text = "Bugün dünyanda neler oldu?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color(0xFF716A60)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = Color(0xFF514B43),
                        modifier = Modifier.size(23.dp)
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = Color(0xFF514B43),
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BotanicalCornerDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stemColor = Color(0xFF8B9675).copy(alpha = 0.42f)
        val leafColor = Color(0xFF9BA783).copy(alpha = 0.35f)

        val stem = Path().apply {
            moveTo(size.width * 0.98f, size.height * 0.92f)
            cubicTo(
                size.width * 0.72f,
                size.height * 0.68f,
                size.width * 0.80f,
                size.height * 0.30f,
                size.width * 0.46f,
                size.height * 0.04f
            )
        }
        drawPath(stem, color = stemColor, style = Stroke(width = 1.4.dp.toPx()))

        drawOval(
            color = leafColor,
            topLeft = Offset(size.width * 0.57f, size.height * 0.46f),
            size = Size(size.width * 0.22f, size.height * 0.10f)
        )
        drawOval(
            color = leafColor,
            topLeft = Offset(size.width * 0.74f, size.height * 0.62f),
            size = Size(size.width * 0.20f, size.height * 0.09f)
        )
        drawOval(
            color = leafColor,
            topLeft = Offset(size.width * 0.34f, size.height * 0.19f),
            size = Size(size.width * 0.22f, size.height * 0.10f)
        )
        drawOval(
            color = leafColor,
            topLeft = Offset(size.width * 0.17f, size.height * 0.34f),
            size = Size(size.width * 0.19f, size.height * 0.09f)
        )

        drawCircle(
            color = Color(0xFFC6A77A).copy(alpha = 0.42f),
            radius = 2.7.dp.toPx(),
            center = Offset(size.width * 0.42f, size.height * 0.10f)
        )
        drawCircle(
            color = Color(0xFFC6A77A).copy(alpha = 0.36f),
            radius = 2.dp.toPx(),
            center = Offset(size.width * 0.29f, size.height * 0.16f)
        )
    }
}
