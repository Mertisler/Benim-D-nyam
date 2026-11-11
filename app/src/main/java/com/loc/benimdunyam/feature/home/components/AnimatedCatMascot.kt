package com.loc.benimdunyam.feature.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs

/**
 * Benim Dünyam'ın güler yüzlü kedisi.
 *
 * Tamamen Compose Canvas ile çizilir: drawable, Lottie veya yeni bağımlılık gerekmez.
 * Kocaman bir yüz, parlak gözler ve minik bir gülümseme kullanır.
 * Kol / kuyruk hareketi, nazikçe sallanma ve doğal göz kırpma içerir.
 *
 * [animated] false olduğunda ikon sabittir; küçük FAB'da sürekli animasyon
 * çalıştırmayarak gereksiz yeniden çizim ve güç tüketimi azaltılır.
 */
@Composable
fun AnimatedCatMascot(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    if (!animated) {
        CatArtwork(
            modifier = modifier,
            pawWave = -6f,
            tailWave = 3f,
            bob = 0f,
            tilt = 0f,
            eyeOpenness = 1f
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "HappyCatTransition")

    val pawWave by transition.animateFloat(
        initialValue = -13f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HelloPaw"
    )
    val tailWave by transition.animateFloat(
        initialValue = -11f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HappyTail"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = -1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GentleBounce"
    )
    val tilt by transition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CuriousHeadTilt"
    )
    // 3,6 saniyelik döngüde bir kez hızlı ve yumuşak göz kırpma.
    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "NaturalBlink"
    )

    val blinkDistance = abs(blinkPhase - 0.33f)
    val eyeOpenness = (blinkDistance / 0.025f).coerceIn(0.08f, 1f)

    CatArtwork(
        modifier = modifier,
        pawWave = pawWave,
        tailWave = tailWave,
        bob = bob,
        tilt = tilt,
        eyeOpenness = eyeOpenness
    )
}

@Composable
private fun CatArtwork(
    modifier: Modifier,
    pawWave: Float,
    tailWave: Float,
    bob: Float,
    tilt: Float,
    eyeOpenness: Float
) {
    val fur = Color(0xFFFFC677)
    val furLight = Color(0xFFFFDA9E)
    val furShadow = Color(0xFFEFA65B)
    val outline = Color(0xFFC78858)
    val ink = Color(0xFF50372F)
    val cream = Color(0xFFFFF1DC)
    val pink = Color(0xFFF5A0AD)
    val nose = Color(0xFFD9798B)

    Canvas(modifier = modifier) {
        // 64x64 özgün koordinatlar: küçük/büyük her ekranda aynı oran.
        val unit = size.minDimension / 64f
        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            translate(top = bob) {
                // Gövdenin altındaki minik yer gölgesi.
                drawOval(
                    color = ink.copy(alpha = 0.10f),
                    topLeft = Offset(17f, 57.5f),
                    size = Size(33f, 4f)
                )

                // Kuyruk, gövde ve başın arkasında hareket eder.
                rotate(
                    degrees = tailWave,
                    pivot = Offset(45f, 48f)
                ) {
                    val tail = Path().apply {
                        moveTo(43f, 48f)
                        cubicTo(53f, 49f, 57f, 42f, 55f, 36f)
                        cubicTo(53f, 32f, 55f, 28f, 59f, 29f)
                    }
                    drawPath(
                        path = tail,
                        color = outline,
                        style = Stroke(width = 9f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = tail,
                        color = fur,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                }

                // Tombul gövde ve krem renkli göbek.
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(17.5f, 33f),
                    size = Size(29f, 25.5f),
                    cornerRadius = CornerRadius(13f)
                )
                drawRoundRect(
                    color = fur,
                    topLeft = Offset(18.5f, 33.5f),
                    size = Size(27f, 23.5f),
                    cornerRadius = CornerRadius(12f)
                )
                drawOval(
                    color = cream,
                    topLeft = Offset(24f, 39f),
                    size = Size(16f, 16f)
                )

                // Top gibi yuvarlak ayaklar.
                drawRoundRect(
                    color = furShadow,
                    topLeft = Offset(16f, 51f),
                    size = Size(16f, 8f),
                    cornerRadius = CornerRadius(5f)
                )
                drawRoundRect(
                    color = fur,
                    topLeft = Offset(17f, 51f),
                    size = Size(14f, 7f),
                    cornerRadius = CornerRadius(5f)
                )
                drawRoundRect(
                    color = furShadow,
                    topLeft = Offset(33f, 51f),
                    size = Size(16f, 8f),
                    cornerRadius = CornerRadius(5f)
                )
                drawRoundRect(
                    color = fur,
                    topLeft = Offset(34f, 51f),
                    size = Size(14f, 7f),
                    cornerRadius = CornerRadius(5f)
                )

                // Başını merakla çok hafif sağa sola eğmesi.
                rotate(
                    degrees = tilt,
                    pivot = Offset(31.5f, 25f)
                ) {
                    // Kulaklar: önce dış kontur, sonra yumuşak pembe iç kısım.
                    val leftEar = Path().apply {
                        moveTo(12f, 20f)
                        cubicTo(10f, 14f, 9f, 3f, 11.5f, 2f)
                        cubicTo(14f, 0f, 24f, 7f, 26f, 11f)
                        close()
                    }
                    val rightEar = Path().apply {
                        moveTo(37f, 11f)
                        cubicTo(41f, 7f, 49f, 0f, 51.5f, 2f)
                        cubicTo(54f, 5f, 53f, 15f, 51f, 20f)
                        close()
                    }
                    drawPath(leftEar, color = outline)
                    drawPath(rightEar, color = outline)
                    val leftInner = Path().apply {
                        moveTo(14f, 16f)
                        lineTo(14.5f, 6f)
                        quadraticTo(20.5f, 9.5f, 22f, 13f)
                        close()
                    }
                    val rightInner = Path().apply {
                        moveTo(40.5f, 13f)
                        quadraticTo(45f, 9f, 49.5f, 6f)
                        lineTo(49f, 16f)
                        close()
                    }
                    drawPath(leftInner, color = pink)
                    drawPath(rightInner, color = pink)

                    // Yanakları tombul, kafası gövdesinden iri yüz silueti.
                    val face = Path().apply {
                        moveTo(12.5f, 24f)
                        cubicTo(12f, 13f, 20f, 9f, 31.5f, 9f)
                        cubicTo(43f, 9f, 51f, 14f, 50.5f, 25f)
                        cubicTo(51f, 36f, 44f, 43f, 31.5f, 43.5f)
                        cubicTo(19f, 44f, 11.5f, 37f, 12.5f, 24f)
                        close()
                    }
                    drawPath(face, color = fur)
                    drawPath(
                        face,
                        color = outline.copy(alpha = 0.50f),
                        style = Stroke(width = 0.8f)
                    )
                    // Alındaki hafif parlaklık.
                    drawOval(
                        color = furLight.copy(alpha = 0.72f),
                        topLeft = Offset(23f, 12f),
                        size = Size(17f, 5f)
                    )

                    // Yanaklara sıcak, gülümseyen bir hava.
                    drawOval(
                        color = pink.copy(alpha = 0.33f),
                        topLeft = Offset(15f, 29.5f),
                        size = Size(10f, 6.5f)
                    )
                    drawOval(
                        color = pink.copy(alpha = 0.33f),
                        topLeft = Offset(38.5f, 29.5f),
                        size = Size(10f, 6.5f)
                    )
                    drawOval(
                        color = cream.copy(alpha = 0.65f),
                        topLeft = Offset(24.2f, 30.2f),
                        size = Size(14.5f, 10.1f)
                    )

                    // Mutlu bakış: gözler kapanırken göz ovalinin boyu azalır.
                    val eyeHeight = 6.9f * eyeOpenness
                    for (eyeX in listOf(22.1f, 36.5f)) {
                        drawOval(
                            color = ink,
                            topLeft = Offset(eyeX, 25.7f - eyeHeight / 2f),
                            size = Size(5.3f, eyeHeight)
                        )
                        if (eyeOpenness > 0.6f) {
                            drawCircle(
                                color = Color.White,
                                radius = 1.15f,
                                center = Offset(eyeX + 1.9f, 24.5f)
                            )
                            drawCircle(
                                color = cream.copy(alpha = 0.95f),
                                radius = 0.48f,
                                center = Offset(eyeX + 3.8f, 27f)
                            )
                        }
                    }

                    // Tatlı, minik bir kalp / ters üçgen burun.
                    val nosePath = Path().apply {
                        moveTo(29.7f, 32.5f)
                        quadraticTo(31.4f, 31.3f, 33.1f, 32.5f)
                        quadraticTo(31.5f, 34.7f, 31.4f, 34.7f)
                        close()
                    }
                    drawPath(nosePath, color = nose)

                    // Öncekinden farklı olarak net bir "ω" gülümsemesi.
                    val smile = Path().apply {
                        moveTo(26.6f, 35.1f)
                        cubicTo(27.5f, 38.6f, 30.1f, 39f, 31.4f, 35.5f)
                        cubicTo(32.8f, 39f, 35.5f, 38.4f, 36.4f, 35.1f)
                    }
                    drawPath(
                        smile,
                        color = ink,
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                    )

                    // Bıyıkların uçları aşağı değil, hafif yukarı kıvrılsın.
                    val whiskers = Path().apply {
                        moveTo(17.2f, 32.8f)
                        quadraticTo(12.1f, 30.6f, 8.5f, 31.6f)
                        moveTo(17.4f, 35.2f)
                        quadraticTo(12.6f, 34.4f, 9.8f, 36.8f)
                        moveTo(45.4f, 32.8f)
                        quadraticTo(50.7f, 30.6f, 54f, 31.6f)
                        moveTo(45.1f, 35.2f)
                        quadraticTo(50f, 34.4f, 53f, 36.8f)
                    }
                    drawPath(
                        whiskers,
                        color = ink.copy(alpha = 0.58f),
                        style = Stroke(width = 0.85f, cap = StrokeCap.Round)
                    )
                }

                // Sağ patisiyle selam verir. Yüz ifadesini kapatmaması için kenardadır.
                rotate(
                    degrees = pawWave,
                    pivot = Offset(45.4f, 42f)
                ) {
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(43.6f, 29.6f),
                        size = Size(9.8f, 18f),
                        cornerRadius = CornerRadius(5.5f)
                    )
                    drawRoundRect(
                        color = fur,
                        topLeft = Offset(44.3f, 29.5f),
                        size = Size(8.7f, 17f),
                        cornerRadius = CornerRadius(5.1f)
                    )
                    drawCircle(
                        color = furLight,
                        radius = 4.2f,
                        center = Offset(48.6f, 30.4f)
                    )
                    drawOval(
                        color = pink.copy(alpha = 0.85f),
                        topLeft = Offset(46.7f, 29.2f),
                        size = Size(3.8f, 2.6f)
                    )
                }
            }
        }
    }
}
