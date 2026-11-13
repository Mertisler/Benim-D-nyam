package com.loc.benimdunyam.feature.journal.journaleditor.components

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun JournalSaveButton(
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(
                width = 94.dp,
                height = 44.dp
            )
            .clickable(
                onClick = onClick
            ),

        contentAlignment = Alignment.Center
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            drawRoundRect(
                color = JournalEditorPalette.SaveGreen,

                cornerRadius = CornerRadius(
                    5.dp.toPx(),
                    5.dp.toPx()
                )
            )

            val roughBorder = Path().apply {

                moveTo(size.width * 0.04f, size.height * 0.18f)
                lineTo(size.width * 0.10f, size.height * 0.08f)
                lineTo(size.width * 0.92f, size.height * 0.06f)
                lineTo(size.width * 0.97f, size.height * 0.20f)
                lineTo(size.width * 0.95f, size.height * 0.82f)
                lineTo(size.width * 0.87f, size.height * 0.94f)
                lineTo(size.width * 0.08f, size.height * 0.91f)
                lineTo(size.width * 0.03f, size.height * 0.76f)

                close()
            }

            drawPath(
                path = roughBorder,
                color = themedColor(Color(0xFFE7E0CB)),
                style = Stroke(
                    width = 2.dp.toPx()
                )
            )

            drawLine(
                color = themedColor(Color(0xFFE7E0CB)).copy(alpha = 0.55f),

                start = Offset(
                    size.width * 0.18f,
                    size.height * 0.17f
                ),

                end = Offset(
                    size.width * 0.82f,
                    size.height * 0.15f
                ),

                strokeWidth = 0.7.dp.toPx()
            )
        }

        Text(
            text = "Kaydet",
            color = themedColor(Color(0xFFF9F5E9)),
            fontSize = 17.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold
        )
    }
}