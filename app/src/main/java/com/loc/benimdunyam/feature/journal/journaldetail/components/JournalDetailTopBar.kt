package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun JournalDetailTopBar(
    showActions: Boolean,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Geri",
                tint = JournalDetailColors.Ink,
                modifier = Modifier.size(27.dp)
            )
        }

        Text(
            text = "Anı Defteri",
            modifier = Modifier.weight(1f),
            color = JournalDetailColors.Ink,
            fontSize = 21.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.SemiBold
        )

        if (showActions) {
            JournalDetailActionIcon(
                onClick = onEdit,
                description = "Düzenle",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = JournalDetailColors.Ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            JournalDetailActionIcon(
                onClick = onDelete,
                description = "Sil",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = JournalDetailColors.Delete,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun JournalDetailActionIcon(
    onClick: () -> Unit,
    description: String,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(38.dp)
            .semantics { this.contentDescription = description },
        shape = CircleShape,
        color = JournalDetailColors.Tape.copy(alpha = 0.78f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
