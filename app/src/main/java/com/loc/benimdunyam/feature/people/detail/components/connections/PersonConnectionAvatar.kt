package com.loc.benimdunyam.feature.people.detail.components.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import java.io.File
import java.util.Locale

@Composable
fun PersonConnectionAvatar(
    name: String,
    imagePath: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val file = imagePath?.takeIf { it.isNotBlank() }?.let(::File)
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (file != null && file.isFile) {
            AsyncImage(
                model = file,
                contentDescription = "$name profil fotoğrafı",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape)
            )
        } else {
            Text(
                text = name.take(1).uppercase(Locale("tr", "TR")),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
