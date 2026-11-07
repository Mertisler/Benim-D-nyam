
package com.loc.benimdunyam.feature.people.network.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hayatımın İnsan Haritası
 *
 * Özellikler:
 * - Canvas üzerinde karakter bağlantıları
 * - Profil fotoğraflı karakter düğümleri
 * - Pinch-to-zoom
 * - Haritayı sürükleme
 * - Karakter düğümlerini taşıma
 * - Karakter seçme
 * - Aranan karakteri vurgulama
 * - Yakınlaştırma ve uzaklaştırma butonları
 * - Haritayı ortalama
 *
 * Düğümler Compose bileşenleri olarak çizilir.
 * Bağlantı çizgileri Canvas üzerinde oluşturulur.
 */
@Composable
fun PeopleNetworkCanvas(
    graph: PeopleNetworkGraph,
    selectedPersonId: String?,
    searchQuery: String,
    onSelectPerson: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    // =====================================================
    // 1. HARİTA DURUMLARI
    // =====================================================

    var zoom by remember {
        mutableFloatStateOf(1f)
    }

    var pan by remember {
        mutableStateOf(Offset.Zero)
    }

    // Kullanıcının sürüklediği karakterlerin konum farkları.
    val draggedNodes = remember {
        mutableStateMapOf<String, Offset>()
    }

    // =====================================================
    // 2. GRAF DEĞİŞTİĞİNDE HARİTAYI SIFIRLA
    // =====================================================

    LaunchedEffect(graph.nodes.map { it.id }) {

        zoom = 1f

        pan = Offset.Zero

        draggedNodes.clear()
    }

    // =====================================================
    // 3. HARİTA ALANI
    // =====================================================

    BoxWithConstraints(
        modifier = modifier
            .clip(
                RoundedCornerShape(20.dp)
            )
            .background(
                Color(0xFF142033)
            )
            .pointerInput(Unit) {

                detectTransformGestures { centroid, panChange, zoomChange, _ ->

                    val previousZoom = zoom

                    val nextZoom = (
                            previousZoom * zoomChange
                            ).coerceIn(
                            minimumValue = 0.65f,
                            maximumValue = 3.5f
                        )

                    val factor = nextZoom / previousZoom

                    val gestureCenter = Offset(
                        x = size.width / 2f,
                        y = size.height / 2f
                    )

                    // Yakınlaştırma, parmakların bulunduğu
                    // konum esas alınarak gerçekleştirilir.
                    pan = centroid -
                            gestureCenter -
                            (centroid - gestureCenter - pan) * factor +
                            panChange

                    zoom = nextZoom
                }
            }
    ) {

        // =================================================
        // 4. EKRAN ÖLÇÜLERİ
        // =================================================

        val density = LocalDensity.current

        /*
         * BoxWithConstraints kapsamını doğrudan kullanıyoruz.
         *
         * maxWidth ve maxHeight Dp cinsindedir.
         * Canvas koordinatları için piksele çeviriyoruz.
         */

        val availableWidth = maxWidth

        val availableHeight = maxHeight

        val viewWidthPx = with(density) {
            availableWidth.toPx()
            val viewWidthPx = with(density) {
                this@BoxWithConstraints.maxWidth.toPx()
            }

            val viewHeightPx = with(density) {
                this@BoxWithConstraints.maxHeight.toPx()
            }

            // =================================================
            // 5. HARİTA MERKEZİ
            // =================================================

            val center = Offset(
                x = viewWidthPx / 2f,
                y = viewHeightPx / 2f
            ) + pan

            // =================================================
            // 6. GRAF SINIRLARINI HESAPLA
            // =================================================

            val xBound = max(
                130f,
                graph.nodes.maxOfOrNull { node ->
                    abs(node.x)
                } ?: 130f
            )

            val yBound = max(
                130f,
                graph.nodes.maxOfOrNull { node ->
                    abs(node.y)
                } ?: 130f
            )

            // =================================================
            // 7. HARİTAYI EKRANA SIĞDIR
            // =================================================

            val horizontalPaddingPx = with(density) {
                100.dp.toPx()
            }

            val verticalPaddingPx = with(density) {
                110.dp.toPx()
            }

            val horizontalFit = (
                    viewWidthPx - horizontalPaddingPx
                    ).coerceAtLeast(150f) / (2f * xBound)

            val verticalFit = (
                    viewHeightPx - verticalPaddingPx
                    ).coerceAtLeast(150f) / (2f * yBound)

            val fit = min(
                horizontalFit,
                verticalFit
            ).coerceIn(
                minimumValue = 0.22f,
                maximumValue = 1.1f
            )

            val effectiveScale = fit * zoom

            // =================================================
            // 8. KARAKTERLERİN EKRAN KONUMUNU HESAPLA
            // =================================================

            val positions = graph.nodes.associate { node ->

                val nodePosition = Offset(
                    x = node.x * effectiveScale,
                    y = node.y * effectiveScale
                )

                val dragOffset = draggedNodes[node.id]
                    ?: Offset.Zero

                node.id to (
                        center + nodePosition + dragOffset
                        )
            }

            // =================================================
            // 9. DÜĞÜM BOYUTLARI
            // =================================================

            val nodeRadius = with(density) {
                26.dp.toPx()
            }

            val centerRadius = with(density) {
                29.dp.toPx()
            }

            // =================================================
            // 10. ÇİZGİ RENKLERİ
            // =================================================

            val edgeColor = Color(0xFF92A4BF)

            val strongEdgeColor = Color(0xFFE8B87A)

            val centerEdgeColor = Color(0xFF59728C).copy(
                alpha = 0.38f
            )

            // =================================================
            // 11. CANVAS BAĞLANTI ÇİZGİLERİ
            // =================================================

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {

                // ---------------------------------------------
                // MERKEZ İLE KARAKTERLER ARASINDAKİ ÇİZGİLER
                // ---------------------------------------------

                for (node in graph.nodes) {

                    if (node.memoryCount > 0) {

                        val position = positions[node.id]

                        if (position != null) {

                            drawLine(
                                color = centerEdgeColor,
                                start = center,
                                end = position,
                                strokeWidth = 1.1.dp.toPx()
                            )
                        }
                    }
                }

                // ---------------------------------------------
                // KARAKTERLER ARASINDAKİ ORTAK ANILAR
                // ---------------------------------------------

                for (edge in graph.edges) {

                    val start = positions[edge.firstPersonId]

                    val end = positions[edge.secondPersonId]

                    if (start != null && end != null) {

                        val isFocused =
                            selectedPersonId == edge.firstPersonId ||
                                    selectedPersonId == edge.secondPersonId

                        // Ortak anı sayısına göre çizgi kalınlığı.
                        val sharedMemoryFactor = sqrt(
                            edge.sharedMemoryCount.toFloat()
                        ).coerceAtMost(4f)

                        val lineWidthDp = (
                                1.4f + sharedMemoryFactor * 0.75f
                                )

                        val lineColor = if (isFocused) {

                            strongEdgeColor.copy(
                                alpha = 0.94f
                            )

                        } else {

                            edgeColor.copy(
                                alpha = 0.30f
                            )
                        }

                        drawLine(
                            color = lineColor,
                            start = start,
                            end = end,
                            strokeWidth = lineWidthDp.dp.toPx()
                        )
                    }
                }
            }

            // =================================================
            // 12. MERKEZ DÜĞÜM
            // =================================================

            Box(
                modifier = Modifier.offset {

                    IntOffset(
                        x = (center.x - centerRadius).roundToInt(),
                        y = (center.y - centerRadius).roundToInt()
                    )
                }
            ) {

                NetworkCenterNode()
            }

            // =================================================
            // 13. KARAKTER DÜĞÜMLERİ
            // =================================================

            /*
         * Önceki D8 hatasını önlemek amacıyla
         * return@key / return@forEach kullanılmıyor.
         */

            for (node in graph.nodes) {

                val position = positions[node.id]

                if (position != null) {

                    key(node.id) {

                        Box(
                            modifier = Modifier.offset {

                                IntOffset(
                                    x = (
                                            position.x - nodeRadius
                                            ).roundToInt(),

                                    y = (
                                            position.y - nodeRadius
                                            ).roundToInt()
                                )
                            }
                        ) {

                            NetworkPersonNode(
                                node = node,

                                selected = selectedPersonId == node.id,

                                highlighted = searchQuery.isNotBlank() &&
                                        node.name.contains(
                                            searchQuery,
                                            ignoreCase = true
                                        ),

                                onClick = {
                                    onSelectPerson(node.id)
                                },

                                onDrag = { delta ->

                                    val previousOffset =
                                        draggedNodes[node.id]
                                            ?: Offset.Zero

                                    draggedNodes[node.id] =
                                        previousOffset + delta
                                }
                            )
                        }
                    }
                }
            }

            // =================================================
            // 14. HARİTA KONTROL BUTONLARI
            // =================================================

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),

                verticalArrangement = Arrangement.spacedBy(
                    4.dp
                ),

                horizontalAlignment = Alignment.End
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        4.dp
                    )
                ) {

                    // -----------------------------------------
                    // YAKINLAŞTIR
                    // -----------------------------------------

                    OutlinedButton(
                        onClick = {

                            zoom = (
                                    zoom * 1.25f
                                    ).coerceAtMost(3.5f)
                        }
                    ) {

                        Text("+")
                    }

                    // -----------------------------------------
                    // UZAKLAŞTIR
                    // -----------------------------------------

                    OutlinedButton(
                        onClick = {

                            zoom = (
                                    zoom / 1.25f
                                    ).coerceAtLeast(0.65f)
                        }
                    ) {

                        Text("−")
                    }
                }

                // ---------------------------------------------
                // HARİTAYI ORTALA
                // ---------------------------------------------

                OutlinedButton(
                    onClick = {

                        zoom = 1f

                        pan = Offset.Zero
                    }
                ) {

                    Text("Ortala")
                }
            }

            // =================================================
            // 15. KULLANICI BİLGİLENDİRME YAZISI
            // =================================================

            Text(
                text = "Haritayı kaydır, düğümleri taşı, iki parmakla yakınlaştır",

                color = Color(0xFFD4DEEB),

                style = MaterialTheme.typography.labelSmall,

                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            )
        }
    }
}

