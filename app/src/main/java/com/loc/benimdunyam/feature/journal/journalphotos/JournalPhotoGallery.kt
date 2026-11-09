package com.loc.benimdunyam.feature.journal.journalphotos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.loc.benimdunyam.data.local.media.JournalPhoto
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import java.io.File

/**
 * Kaydedilmiş bir anının detay sayfasında çalışır.
 * Galeri açık olan günlüğün ID'sini parametre olarak alır.
 * Böylece önceki/sonraki anı gezinmesinde yanlış fotoğraf gösterilmez.
 */
@Composable
fun JournalPhotoGallery(
    journalId: String,
    modifier: Modifier = Modifier,
    viewModel: JournalPhotoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPhoto by remember { mutableStateOf<JournalPhoto?>(null) }
    var photoPendingDelete by remember { mutableStateOf<JournalPhoto?>(null) }

    LaunchedEffect(journalId) {
        selectedPhoto = null
        photoPendingDelete = null
        viewModel.loadForJournal(journalId)
    }

    val photos = if (uiState.journalId == journalId) uiState.photos else emptyList()
    val isLoading = uiState.journalId != journalId || uiState.isLoading
    val isWorking = uiState.journalId == journalId && uiState.isWorking

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selectedUris: List<Uri> ->
        viewModel.add(selectedUris, journalId)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Anı Fotoğrafları", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${photos.size}/${JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL} fotoğraf",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                enabled = !isWorking && !isLoading &&
                        photos.size < JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL,
                onClick = { picker.launch(arrayOf("image/*")) }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Fotoğraf Ekle")
            }
        }

        if (isLoading || isWorking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(if (isWorking) "Fotoğraflar işleniyor..." else "Fotoğraflar yükleniyor...")
            }
        }

        if (!isLoading && photos.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Bu anıya henüz fotoğraf eklemedin. Galerinden seçtiğin fotoğraflar cihaz içinde saklanır.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (photos.isNotEmpty()) {
            Text("Sırala: ← →  •  Kapak seç: ★",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            JournalPhotoManageStrip(
                photos = photos,
                enabled = !isWorking,
                onPreview = { selectedPhoto = it },
                onMove = { id, delta -> viewModel.move(id, journalId, delta) },
                onSelectCover = { id -> viewModel.setCover(id, journalId) },
                onDelete = { photoPendingDelete = it },
                canDelete = { true }
            )
        }

        uiState.errorMessage?.takeIf { uiState.journalId == journalId }?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        uiState.resultMessage?.takeIf { uiState.journalId == journalId }?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall)
        }
        // Önemli: kullanıcı verisinin güvenliği için yedek sınırlaması saklanmıyor.
        HorizontalDivider()
        Text(
            "Not: Bu fotoğraflar henüz .bdbackup yedeğine dahil değil. " +
                    "Telefon değiştirmeden veya geri yükleme yapmadan önce fotoğrafları ayrıca koru.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    selectedPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { selectedPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = File(photo.absolutePath),
                        contentDescription = "Büyütülmüş anı fotoğrafı",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { selectedPhoto = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, "Kapat", tint = Color.White)
                    }
                }
            }
        }
    }

    photoPendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoPendingDelete = null },
            title = { Text("Fotoğrafı sil?") },
            text = { Text("Bu fotoğraf yalnızca bu anıdan silinecek. İşlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(photo.id, journalId)
                    photoPendingDelete = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { photoPendingDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}
