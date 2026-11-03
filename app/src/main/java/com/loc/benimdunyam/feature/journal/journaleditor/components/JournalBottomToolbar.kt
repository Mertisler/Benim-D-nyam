package com.loc.benimdunyam.feature.journal.journaleditor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp


@Composable
fun JournalBottomToolbar() {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val tornTop = Path().apply {

                moveTo(0f, 9.dp.toPx())

                val segments = 34

                for (index in 0..segments) {

                    val x = size.width * index / segments

                    val wobble = when (index % 4) {
                        0 -> 3.dp.toPx()
                        1 -> 0.dp.toPx()
                        2 -> 5.dp.toPx()
                        else -> 1.dp.toPx()
                    }

                    lineTo(
                        x,
                        7.dp.toPx() + wobble
                    )
                }

                lineTo(size.width, size.height)
                lineTo(0f, size.height)

                close()
            }

            drawPath(
                path = tornTop,
                color = JournalEditorPalette.TapeColor
            )

            drawLine(
                color = Color(0xFFB7A78F).copy(alpha = 0.45f),

                start = Offset(
                    0f,
                    9.dp.toPx()
                ),

                end = Offset(
                    size.width,
                    9.dp.toPx()
                ),

                strokeWidth = 1.dp.toPx()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .padding(
                    top = 6.dp,
                    bottom = 1.dp
                ),

            verticalAlignment = Alignment.CenterVertically
        ) {

            ToolbarIcon(
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.TextFields,
                    contentDescription = "Metin biçimi",
                    tint = JournalEditorPalette.PaperInk
                )
            }

            ToolbarIcon(
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Görsel ekle",
                    tint = JournalEditorPalette.PaperInk
                )
            }

            ToolbarIcon(
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = "Yıldız ekle",
                    tint = JournalEditorPalette.PaperInk
                )
            }

            ToolbarIcon(
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEmotions,
                    contentDescription = "Emoji ekle",
                    tint = JournalEditorPalette.PaperInk
                )
            }

            ToolbarIcon(
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.TextFields,
                    contentDescription = "Metin ekle",
                    tint = JournalEditorPalette.PaperInk
                )
            }
        }
    }
}


@Composable
private fun ToolbarIcon(
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit
) {

    IconButton(
        onClick = {
            // Araçların işlevleri daha sonra eklenecek.
        },

        modifier = modifier.size(42.dp)
    ) {

        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {

            image()
        }
    }
}