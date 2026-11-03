package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun JournalDetailDatePolaroid(
    day: String,
    monthYear: String,
    time: String
) {
    Surface(
        modifier = Modifier
            .width(132.dp)
            .height(157.dp)
            .rotate(-3f),
        shape = RoundedCornerShape(2.dp),
        color = JournalDetailColors.Polaroid,
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                JournalDetailColors.PolaroidImageTop,
                                JournalDetailColors.PolaroidImageBottom
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = day,
                        color = JournalDetailColors.Ink,
                        fontSize = 49.sp,
                        lineHeight = 52.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = "GÜNÜNDE",
                        color = JournalDetailColors.MutedInk,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "✦",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = JournalDetailColors.Accent,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = monthYear,
                color = JournalDetailColors.Ink,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = time,
                color = JournalDetailColors.MutedInk,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
