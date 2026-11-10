package com.loc.benimdunyam.feature.people.network.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Avatarlar Compose bileşenleri; çizgiler Canvas üzerinde çizilir.
 * Ekran koordinatlarında pinch-to-zoom + pan uygulanır; fotoğraflar ölçekle bulanıklaşmaz.
 */
@Composable
fun PeopleNetworkCanvas(
    graph: PeopleNetworkGraph,
    selectedPersonId: String?,
    searchQuery: String,
    onSelectPerson: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val draggedNodes = remember { mutableStateMapOf<String, Offset>() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(graph.nodes.map { it.id }.sorted()) {
        zoom = 1f
        pan = Offset.Zero
        draggedNodes.clear()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF142033))
            .onSizeChanged { viewportSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val previousZoom = zoom
                    val nextZoom = (previousZoom * zoomChange).coerceIn(0.65f, 3.5f)
                    val factor = nextZoom / previousZoom
                    val center = Offset(size.width / 2f, size.height / 2f)
                    pan = centroid - center - (centroid - center - pan) * factor + panChange
                    zoom = nextZoom
                }
            }
    ) {
        val density = LocalDensity.current
        val viewWidthPx = viewportSize.width.toFloat()
        val viewHeightPx = viewportSize.height.toFloat()
        val center = Offset(viewWidthPx / 2, viewHeightPx / 2) + pan
        val xBound = max(130f, graph.nodes.maxOfOrNull { abs(it.x) } ?: 130f)
        val yBound = max(130f, graph.nodes.maxOfOrNull { abs(it.y) } ?: 130f)
        val fit = min(
            (viewWidthPx - with(density) { 100.dp.toPx() }).coerceAtLeast(150f) / (2f * xBound),
            (viewHeightPx - with(density) { 110.dp.toPx() }).coerceAtLeast(150f) / (2f * yBound)
        ).coerceIn(0.22f, 1.1f)
        val effectiveScale = fit * zoom
        val positions = graph.nodes.associate { node ->
            node.id to (center + Offset(node.x * effectiveScale, node.y * effectiveScale) +
                    (draggedNodes[node.id] ?: Offset.Zero))
        }
        val nodeRadius = with(density) { 26.dp.toPx() }
        val edgeColor = Color(0xFF9FB2C8)
        val strongEdge = Color(0xFFE8B87A)

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Yalnızca gerçek ortak-anı bağlantıları çizilir.
            for (edge in graph.edges) {
                val start = positions[edge.firstPersonId]
                val end = positions[edge.secondPersonId]
                if (start != null && end != null) {
                    val focused = selectedPersonId == edge.firstPersonId ||
                            selectedPersonId == edge.secondPersonId
                    drawLine(
                        color = if (focused) strongEdge.copy(alpha = 0.94f)
                        else edgeColor.copy(alpha = 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = (1.4f + sqrt(edge.sharedMemoryCount.toFloat()).coerceAtMost(4f) * 0.75f).dp.toPx()
                    )
                }
            }
        }

        // Düğüm çizimi: null pozisyonlar atlanır; lambdadan erken dönüş yoktur.
        for (node in graph.nodes) {
            val position = positions[node.id]
            if (position != null) {
                key(node.id) {
                    Box(
                        modifier = Modifier.offset {
                            IntOffset(
                                (position.x - nodeRadius).roundToInt(),
                                (position.y - nodeRadius).roundToInt()
                            )
                        }
                    ) {
                        NetworkPersonNode(
                            node = node,
                            selected = selectedPersonId == node.id,
                            highlighted = searchQuery.isNotBlank() && node.name.contains(searchQuery, ignoreCase = true),
                            onClick = { onSelectPerson(node.id) },
                            onDrag = { delta ->
                                draggedNodes[node.id] = (draggedNodes[node.id] ?: Offset.Zero) + delta
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3.5f) }) {
                    Text("+")
                }
                OutlinedButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.65f) }) {
                    Text("−")
                }
            }
            OutlinedButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                Text("Ortala")
            }
        }
    }
}
