package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun JournalDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JournalDetailColors.PaperLight,
        titleContentColor = JournalDetailColors.Ink,
        textContentColor = JournalDetailColors.MutedInk,
        title = {
            Text(
                text = "Bu anı silinsin mi?",
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp
            )
        },
        text = {
            Text(
                text = "Bu anı defterinden kaldırılacak. Bu işlem geri alınamaz.",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = JournalDetailColors.Delete
                )
            ) {
                Text("Sil", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = JournalDetailColors.Green)
            }
        }
    )
}
