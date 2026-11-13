
package com.loc.benimdunyam.feature.journal.components

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val DeleteRed get() = themedColor(Color(0xFFB84C43))
private val PaperColor get() = themedColor(Color(0xFFFAF6EE))
private val InkColor get() = themedColor(Color(0xFF33271F))
private val MutedColor get() = themedColor(Color(0xFF756755))
private val GreenColor get() = themedColor(Color(0xFF365A43))

@Composable
fun SwipeToDeleteJournalItem(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    // Silme alanının genişliği
    val revealWidth = 60.dp
    val revealWidthPx = with(LocalDensity.current) {
        revealWidth.toPx()
    }

    // Yalnızca sola kaymaya izin veren sürükleme durumu
    val draggableState = rememberDraggableState { delta ->

        offsetX = (offsetX + delta).coerceIn(
            minimumValue = -revealWidthPx,
            maximumValue = 0f
        )
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {

        // KARTIN ARKASINDAKİ SİLME ALANI
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {

            Surface(
                onClick = {
                    showDeleteDialog = true
                },
                modifier = Modifier
                    .width(revealWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                color = DeleteRed
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Günlüğü sil",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }

        // ÖNDEKİ GÜNLÜK KARTI
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = 0
                    )
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {

                        // Yeterince sola kaydırıldıysa açık kalsın.
                        // Aksi durumda eski konumuna dönsün.
                        offsetX = if (offsetX <= -revealWidthPx * 0.45f) {
                            -revealWidthPx
                        } else {
                            0f
                        }
                    }
                )
        ) {
            content()
        }
    }

    // SİLME ONAY DİYALOĞU
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                offsetX = 0f
            },
            containerColor = PaperColor,
            titleContentColor = InkColor,
            textContentColor = MutedColor,

            title = {
                Text(
                    text = "Bu anı silinsin mi?",
                    fontSize = 21.sp
                )
            },

            text = {
                Text(
                    text = "Bu anı defterinden kaldırılacak. Bu işlem geri alınamaz.",
                    fontSize = 15.sp
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        offsetX = 0f
                        onDelete()
                    }
                ) {
                    Text(
                        text = "Sil",
                        color = DeleteRed
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        offsetX = 0f
                    }
                ) {
                    Text(
                        text = "Vazgeç",
                        color = GreenColor
                    )
                }
            }
        )
    }
}
