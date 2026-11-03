package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DetailTapeLabel(
    text: String,
    rotation: Float
) {
    Box(
        modifier = Modifier
            .rotate(rotation)
            .background(JournalDetailColors.Tape, RoundedCornerShape(3.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = JournalDetailColors.Ink,
            fontSize = 14.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
internal fun ScrapbookDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
    ) {
        val path = Path().apply {
            moveTo(0f, size.height / 2f)
            val segments = 38
            for (index in 1..segments) {
                val x = size.width * index / segments
                val y = size.height / 2f + if (index % 2 == 0) -2.dp.toPx() else 2.dp.toPx()
                lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = JournalDetailColors.Divider.copy(alpha = 0.6f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
internal fun PersonPaperTag(name: String) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 11.dp,
            bottomEnd = 4.dp,
            bottomStart = 11.dp
        ),
        color = JournalDetailColors.PersonTag,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.size(23.dp),
                shape = CircleShape,
                color = JournalDetailColors.Green
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = name,
                color = JournalDetailColors.Ink,
                fontSize = 13.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
