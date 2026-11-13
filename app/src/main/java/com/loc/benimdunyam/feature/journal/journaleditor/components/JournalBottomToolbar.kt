package com.loc.benimdunyam.feature.journal.journaleditor.components

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp

/**
 * Eski beş ikonlu araç çubuğuyla aynı çağrı biçimini korur.
 * Artık Görsel Ekle butonuna gerçek callback bağlanabilir.
 * Diğer ikonlar ileride kendi özelliklerine bağlanabilir.
 */
@Composable
fun JournalBottomToolbar(
    modifier: Modifier = Modifier,
    onImageClick: () -> Unit = {},
    imageEnabled: Boolean = true,
    onTextFormatClick: () -> Unit = {},
    onStarClick: () -> Unit = {},
    onEmojiClick: () -> Unit = {},
    onInsertTextClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val edge = 6.dp.toPx()
            val step = 12.dp.toPx()
            val path = Path().apply {
                moveTo(0f, edge)
                var x = 0f
                var high = false
                while (x < size.width) {
                    x = (x + step).coerceAtMost(size.width)
                    lineTo(x, if (high) edge * 0.3f else edge)
                    high = !high
                }
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, themedColor(Color(0xFFF5EEE3)))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarAction("Metin biçimi", onTextFormatClick) {
                Icon(Icons.Outlined.TextFields, contentDescription = "Metin biçimi", tint = themedColor(Color(0xFF496B5B)))
            }
            ToolbarAction("Görsel ekle", onImageClick, imageEnabled) {
                Icon(Icons.Outlined.Image, contentDescription = "Görsel ekle", tint = themedColor(Color(0xFF496B5B)))
            }
            ToolbarAction("Yıldız ekle", onStarClick) {
                Icon(Icons.Outlined.Star, contentDescription = "Yıldız ekle", tint = themedColor(Color(0xFF496B5B)))
            }
            ToolbarAction("Emoji ekle", onEmojiClick) {
                Icon(Icons.Outlined.EmojiEmotions, contentDescription = "Emoji ekle", tint = themedColor(Color(0xFF496B5B)))
            }
            ToolbarAction("Metin ekle", onInsertTextClick) {
                Icon(Icons.Outlined.TextFields, contentDescription = "Metin ekle", tint = themedColor(Color(0xFF496B5B)))
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        enabled = enabled
    ) {
        Box(modifier = Modifier.size(27.dp), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
