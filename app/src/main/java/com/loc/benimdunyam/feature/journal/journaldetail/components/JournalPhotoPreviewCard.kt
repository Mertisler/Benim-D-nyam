package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.loc.benimdunyam.data.local.media.JournalPhoto
import java.io.File

/** Başlıksız tek satırlık önizleme: fotoğraflar solda, minimal Yönet düğmesi sağda. */
@Composable
fun JournalPhotoPreviewCard(
    photos: List<JournalPhoto>,
    onPhotoClick: (JournalPhoto) -> Unit,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        TextButton(
            onClick = onManageClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = JournalDetailColors.Green
            )
            Spacer(Modifier.width(6.dp))
            Text("Fotoğraf ekle", color = JournalDetailColors.Green, fontSize = 12.sp)
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF7F0E5),
        border = BorderStroke(1.dp, Color(0xFFE5D7C5))
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Boyutlar önceki sürümdeki gibi 72 dp kalır.
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE4D9C7))
                            .clickable { onPhotoClick(photo) }
                            .semantics {
                                contentDescription = "Anı fotoğrafını tam ekranda aç"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(photo.absolutePath),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = onManageClick,
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Text("Yönet", fontSize = 12.sp, color = JournalDetailColors.Green)
            }
        }
    }
}
