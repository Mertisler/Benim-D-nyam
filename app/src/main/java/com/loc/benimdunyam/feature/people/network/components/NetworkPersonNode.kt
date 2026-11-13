package com.loc.benimdunyam.feature.people.network.components

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphNode
import java.io.File
import java.util.Locale

@Composable
fun NetworkPersonAvatar(
    name: String,
    imagePath: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val photo = imagePath?.takeIf { it.isNotBlank() }?.let { File(it) }
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(Locale("tr", "TR")),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (photo != null && photo.isFile) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        }
    }
}

@Composable
fun NetworkPersonNode(
    node: NetworkGraphNode,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    Column(
        modifier = Modifier
            .pointerInput(node.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .clickable(
                onClickLabel = "${node.name} karakterinin bağlantılarını göster",
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        NetworkPersonAvatar(
            name = node.name,
            imagePath = node.profileImagePath,
            size = 52.dp,
            modifier = Modifier.border(
                border = BorderStroke(
                    width = if (selected) 3.dp else if (highlighted) 2.dp else 1.dp,
                    color = if (selected) themedColor(Color(0xFFEAC48D)) else if (highlighted) Color.White else themedColor(Color(0xFF75849D))
                ),
                shape = CircleShape
            )
        )
        Text(
            text = node.name,
            color = if (selected) themedColor(Color(0xFFF6D9AD)) else Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 85.dp)
        )
    }
}

@Composable
fun NetworkCenterNode() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(58.dp)
                .clip(CircleShape)
                .background(themedColor(Color(0xFFE8B87A)))
                .border(3.dp, themedColor(Color(0xFFFFF1D3)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", color = themedColor(Color(0xFF253045)), style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            "Benim Dünyam",
            color = themedColor(Color(0xFFFFE1B0)),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
