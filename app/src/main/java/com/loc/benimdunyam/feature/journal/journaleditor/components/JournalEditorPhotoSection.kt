package com.loc.benimdunyam.feature.journal.journaleditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.loc.benimdunyam.data.local.media.JournalPhoto
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.feature.journal.journalphotos.JournalPhotoManageStrip
import java.io.File

/** New/published photo order and chosen cover are saved as draft until journal Save. */
@Composable
fun JournalEditorPhotoSection(
    photos: List<JournalPhoto>,
    coverPhotoId: String?,
    isWorking: Boolean,
    enabled: Boolean,
    errorMessage: String?,
    onPickPhotos: () -> Unit,
    onRemoveDraftPhoto: (String) -> Unit,
    onMovePhoto: (String, Int) -> Unit,
    onSelectCover: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<JournalPhoto?>(null) }
    var pendingDelete by remember { mutableStateOf<JournalPhoto?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Anı Fotoğrafları", style = MaterialTheme.typography.titleSmall, color = Color(0xFF496B5B))
                Text("${photos.size}/${JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL} fotoğraf",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onPickPhotos,
                enabled = enabled && !isWorking && photos.size < JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Fotoğraf Ekle")
            }
        }
        if (isWorking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Fotoğraflar düzenleniyor...", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (photos.isNotEmpty()) {
            Text("Sırala: ← →  •  Kapak seç: ★",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            JournalPhotoManageStrip(
                photos = photos.map { it.copy(isCover = it.id == coverPhotoId) },
                enabled = enabled && !isWorking,
                onPreview = { preview = it },
                onMove = onMovePhoto,
                onSelectCover = onSelectCover,
                onDelete = { pendingDelete = it },
                canDelete = { it.isDraft }
            )
        }
    }

    preview?.let { photo ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AsyncImage(model = File(photo.absolutePath), contentDescription = "Büyütülmüş anı fotoğrafı",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                TextButton(onClick = { preview = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Text("Kapat", color = Color.White)
                }
            }
        }
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Taslak fotoğrafını sil?") },
            text = { Text("Fotoğraf taslaktan kaldırılacak. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveDraftPhoto(photo.id)
                    pendingDelete = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") } }
        )
    }
}
