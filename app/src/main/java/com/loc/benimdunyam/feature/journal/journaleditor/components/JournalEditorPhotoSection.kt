package com.loc.benimdunyam.feature.journal.journaleditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.loc.benimdunyam.data.local.media.JournalPhoto
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import java.io.File

/**
 * Yeni veya mevcut anıyı yazarken fotoğraf seçimi.
 * Seçilen görseller anı kaydedilinceye dek geçici klasörde kalır.
 * Önceden kaydedilmiş görseller okunabilir; onları silmek için detay galerisi kullanılır.
 */
@Composable
fun JournalEditorPhotoSection(
    savedPhotos: List<JournalPhoto>,
    draftPhotos: List<JournalPhoto>,
    isWorking: Boolean,
    enabled: Boolean,
    errorMessage: String?,
    onPickPhotos: () -> Unit,
    onRemoveDraftPhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<JournalPhoto?>(null) }
    var pendingDelete by remember { mutableStateOf<JournalPhoto?>(null) }

    val count = savedPhotos.size + draftPhotos.size
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Anı Fotoğrafları",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF496B5B)
                )
                Text(
                    text = "$count/${JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL} fotoğraf",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onPickPhotos,
                enabled = enabled && !isWorking && count < JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Fotoğraf Ekle")
            }
        }

        if (isWorking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Fotoğraflar hazırlanıyor...", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (count > 0) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedPhotos, key = { "saved:${it.id}" }) { photo ->
                    PhotoThumbnail(photo, isDraft = false,
                        onClick = { preview = photo }, onDelete = null)
                }
                items(draftPhotos, key = { "draft:${it.id}" }) { photo ->
                    PhotoThumbnail(
                        photo,
                        isDraft = true,
                        onClick = { preview = photo },
                        onDelete = if (enabled && !isWorking) ({ pendingDelete = photo }) else null
                    )
                }
            }
        }
    }

    preview?.let { photo ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(photo.absolutePath),
                    contentDescription = "Anı fotoğrafı",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                TextButton(
                    onClick = { preview = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Text("Kapat", color = Color.White)
                }
            }
        }
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Fotoğrafı kaldır") },
            text = { Text("Henüz kaydedilmemiş bu fotoğraf taslaktan silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveDraftPhoto(photo.id)
                    pendingDelete = null
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun PhotoThumbnail(
    photo: JournalPhoto,
    isDraft: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Box(modifier = Modifier.size(90.dp)) {
        AsyncImage(
            model = File(photo.absolutePath),
            contentDescription = if (isDraft) "Taslak fotoğrafı" else "Kaydedilmiş fotoğraf",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
        )
        if (isDraft) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                color = Color(0xDDFFFFFF),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "Taslak",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Taslak fotoğrafını kaldır",
                    tint = Color.White,
                    modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(30.dp))
                )
            }
        }
    }
}
