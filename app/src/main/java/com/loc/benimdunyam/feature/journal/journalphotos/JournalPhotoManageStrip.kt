package com.loc.benimdunyam.feature.journal.journalphotos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loc.benimdunyam.data.local.media.JournalPhoto
import java.io.File

/** Same ordering / cover affordances in editor and detail gallery. */
@Composable
fun JournalPhotoManageStrip(
    photos: List<JournalPhoto>,
    enabled: Boolean,
    onPreview: (JournalPhoto) -> Unit,
    onMove: (String, Int) -> Unit,
    onSelectCover: (String) -> Unit,
    onDelete: ((JournalPhoto) -> Unit)? = null,
    canDelete: (JournalPhoto) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
            Column(
                modifier = Modifier.width(152.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.width(152.dp).height(105.dp)) {
                    AsyncImage(
                        model = File(photo.absolutePath),
                        contentDescription = "Anı fotoğrafı ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(152.dp)
                            .height(105.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPreview(photo) }
                    )
                    if (photo.isCover || photo.isDraft) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (photo.isCover) {
                                Surface(color = Color(0xEAF8EBD3), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "★ Kapak",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF54412C)
                                    )
                                }
                            }
                            if (photo.isDraft) {
                                Surface(color = Color(0xEAF8F8F8), shape = RoundedCornerShape(6.dp)) {
                                    Text("Taslak", Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = enabled && index > 0,
                        onClick = { onMove(photo.id, -1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Fotoğrafı sola taşı", modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        enabled = enabled && index < photos.lastIndex,
                        onClick = { onMove(photo.id, +1) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Fotoğrafı sağa taşı", modifier = Modifier.size(19.dp))
                    }
                    IconButton(
                        enabled = enabled && !photo.isCover,
                        onClick = { onSelectCover(photo.id) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = if (photo.isCover) "Kapak fotoğrafı" else "Kapak yap",
                            tint = if (photo.isCover) Color(0xFFC39A47) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (onDelete != null && canDelete(photo)) {
                        IconButton(
                            enabled = enabled,
                            onClick = { onDelete(photo) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Fotoğrafı sil", modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
