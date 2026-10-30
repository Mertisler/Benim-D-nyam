
package com.loc.benimdunyam.feature.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedCatMascot(
    modifier: Modifier = Modifier
) {
    val animation = rememberInfiniteTransition(
        label = "CatAnimation"
    )

    val pawRotation by animation.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 450,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PawWave"
    )

    val tailRotation by animation.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TailWave"
    )

    val bob by animation.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CatBounce"
    )

    val orange = Color(0xFFFFBE69)
    val dark = Color(0xFF49352D)
    val pink = Color(0xFFED8D9C)
    val white = Color(0xFFFFF4E8)

    val leftEar = Path().apply {
        moveTo(15f, 15f)
        lineTo(18f, 2f)
        lineTo(29f, 11f)
        close()
    }

    val rightEar = Path().apply {
        moveTo(32f, 11f)
        lineTo(43f, 2f)
        lineTo(46f, 16f)
        close()
    }

    val tail = Path().apply {
        moveTo(41f, 46f)
        cubicTo(53f, 45f, 48f, 33f, 55f, 29f)
    }

    Canvas(modifier = modifier) {
        val unit = size.minDimension / 64f

        scale(
            scaleX = unit,
            scaleY = unit,
            pivot = Offset.Zero
        ) {
            translate(top = bob) {

                // Kuyruk
                rotate(
                    degrees = tailRotation,
                    pivot = Offset(42f, 45f)
                ) {
                    drawPath(
                        path = tail,
                        color = orange,
                        style = Stroke(
                            width = 5f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }

                // Gövde
                drawRoundRect(
                    color = orange,
                    topLeft = Offset(18f, 29f),
                    size = Size(28f, 27f),
                    cornerRadius = CornerRadius(12f)
                )

                // Ayaklar
                drawRoundRect(
                    color = orange,
                    topLeft = Offset(17f, 46f),
                    size = Size(15f, 10f),
                    cornerRadius = CornerRadius(5f)
                )

                drawRoundRect(
                    color = orange,
                    topLeft = Offset(33f, 46f),
                    size = Size(15f, 10f),
                    cornerRadius = CornerRadius(5f)
                )

                // Selam veren pati
                rotate(
                    degrees = pawRotation,
                    pivot = Offset(39f, 35f)
                ) {
                    drawRoundRect(
                        color = orange,
                        topLeft = Offset(35f, 17f),
                        size = Size(8f, 19f),
                        cornerRadius = CornerRadius(4f)
                    )

                    drawCircle(
                        color = orange,
                        radius = 4.5f,
                        center = Offset(39f, 19f)
                    )
                }

                // Diğer ön pati
                drawRoundRect(
                    color = orange,
                    topLeft = Offset(20f, 34f),
                    size = Size(9f, 13f),
                    cornerRadius = CornerRadius(4f)
                )

                // Kulaklar
                drawPath(leftEar, orange)
                drawPath(rightEar, orange)

                // Kulak içleri
                drawCircle(
                    color = pink,
                    radius = 3f,
                    center = Offset(20f, 9f)
                )

                drawCircle(
                    color = pink,
                    radius = 3f,
                    center = Offset(40f, 9f)
                )

                // Kafa
                drawCircle(
                    color = orange,
                    radius = 17f,
                    center = Offset(31f, 20f)
                )

                // Yanaklar ve burun
                drawCircle(
                    color = white,
                    radius = 5f,
                    center = Offset(26f, 25f)
                )

                drawCircle(
                    color = white,
                    radius = 5f,
                    center = Offset(35f, 25f)
                )

                drawCircle(
                    color = pink,
                    radius = 1.8f,
                    center = Offset(30.5f, 23f)
                )

                // Gözler
                drawCircle(
                    color = dark,
                    radius = 1.8f,
                    center = Offset(25f, 19f)
                )

                drawCircle(
                    color = dark,
                    radius = 1.8f,
                    center = Offset(36f, 19f)
                )

                // Bıyıklar
                drawLine(
                    dark,
                    Offset(19f, 26f),
                    Offset(9f, 23f),
                    strokeWidth = 1f
                )

                drawLine(
                    dark,
                    Offset(19f, 29f),
                    Offset(9f, 30f),
                    strokeWidth = 1f
                )

                drawLine(
                    dark,
                    Offset(42f, 26f),
                    Offset(52f, 23f),
                    strokeWidth = 1f
                )

                drawLine(
                    dark,
                    Offset(42f, 29f),
                    Offset(52f, 30f),
                    strokeWidth = 1f
                )

                // Küçük ağız
                drawLine(
                    dark,
                    Offset(30.5f, 25f),
                    Offset(28f, 28f),
                    strokeWidth = 1.2f
                )

                drawLine(
                    dark,
                    Offset(30.5f, 25f),
                    Offset(33f, 28f),
                    strokeWidth = 1.2f
                )
            }
        }
    }
}
