package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
internal fun JournalDetailBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    JournalDetailColors.PaperLight,
                    JournalDetailColors.Paper,
                    Color(0xFFF0E6D8)
                )
            )
        )

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF9C8868).copy(alpha = 0.12f),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF9C8868).copy(alpha = 0.10f)
                )
            )
        )

        val random = Random(41)
        repeat(230) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val radius = random.nextFloat() * 1.3f + 0.2f
            val alpha = random.nextFloat() * 0.045f + 0.01f
            drawCircle(
                color = Color(0xFF806F55).copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }

        drawLine(
            color = Color(0xFF9C8C73).copy(alpha = 0.09f),
            start = Offset(0f, size.height * 0.54f),
            end = Offset(size.width, size.height * 0.54f),
            strokeWidth = 1.dp.toPx()
        )

        drawBotanicalStem(
            start = Offset(10.dp.toPx(), size.height * 0.98f),
            height = 145.dp.toPx(),
            direction = 1f
        )
        drawBotanicalStem(
            start = Offset(size.width - 8.dp.toPx(), size.height * 0.98f),
            height = 165.dp.toPx(),
            direction = -1f
        )
    }
}

private fun DrawScope.drawBotanicalStem(
    start: Offset,
    height: Float,
    direction: Float
) {
    val ink = Color(0xFF81745F).copy(alpha = 0.34f)
    val stem = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(
            start.x + direction * height * 0.05f,
            start.y - height * 0.32f,
            start.x + direction * height * 0.27f,
            start.y - height * 0.65f,
            start.x + direction * height * 0.22f,
            start.y - height
        )
    }
    drawPath(stem, ink, style = Stroke(width = 1.dp.toPx()))

    for (index in 1..5) {
        val progress = index / 6f
        val leafX = start.x + direction * height * (0.02f + progress * 0.20f)
        val leafY = start.y - height * progress * 0.88f
        val leafWidth = height * 0.075f
        drawOval(
            color = ink,
            topLeft = Offset(leafX - leafWidth * 0.6f, leafY - leafWidth * 0.18f),
            size = Size(leafWidth, leafWidth * 0.34f)
        )
        drawOval(
            color = ink.copy(alpha = 0.8f),
            topLeft = Offset(leafX - direction * leafWidth * 0.25f, leafY - leafWidth * 0.52f),
            size = Size(leafWidth, leafWidth * 0.34f)
        )
    }
}
