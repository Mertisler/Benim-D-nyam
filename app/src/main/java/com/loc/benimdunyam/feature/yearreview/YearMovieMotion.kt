package com.loc.benimdunyam.feature.yearreview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Filmin kendi hareket erişilebilirliği ayarı.
 * Sadece film oynatıcısının CompositionLocalProvider'ı içinde geçerlidir.
 */
internal val LocalMovieReducedMotion = compositionLocalOf { false }

/** Görselin kendi kırpılmış kapsayıcısında, sahne ilerledikçe küçük bir kamera hareketi. */
internal fun Modifier.movieKenBurns(
    progress: Float,
    reducedMotion: Boolean,
    direction: Int = 1
): Modifier = this.graphicsLayer {
    if (reducedMotion) {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    } else {
        val p = progress.coerceIn(0f, 1f)
        scaleX = MovieMotionRules.photoScale(p, false)
        scaleY = MovieMotionRules.photoScale(p, false)
        translationX = (p - 0.5f) * size.width * 0.025f * direction
        translationY = (p - 0.5f) * size.height * 0.014f
    }
}

/** Başlıkları ve seçilmiş kartları hafifçe aşağıdan getirir; tekrar oynatmaya uyumludur. */
@Composable
internal fun MovieReveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 140,
    durationMillis: Int = 740,
    content: @Composable () -> Unit
) {
    val fraction = movieRevealFraction(delayMillis, durationMillis)
    Box(
        modifier = modifier.graphicsLayer {
            alpha = fraction
            translationY = (1f - fraction) * 18.dp.toPx()
        }
    ) { content() }
}

/** Sadece sahne ilk kez compose edildiğinde tetiklenir; azaltılmış harekette animasyon yoktur. */
@Composable
internal fun movieRevealFraction(delayMillis: Int = 140, durationMillis: Int = 740): Float {
    val reducedMotion = LocalMovieReducedMotion.current
    val value = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            value.snapTo(1f)
        } else {
            value.snapTo(0f)
            delay(delayMillis.coerceAtLeast(0).toLong())
            value.animateTo(
                1f,
                animationSpec = tween(durationMillis.coerceAtLeast(1), easing = FastOutSlowInEasing)
            )
        }
    }
    return value.value
}

/** Hafif ışık, vinyet ve sabit film dokusu; ağ, bitmap ve rastgele hafıza tahsisi yok. */
@Composable
internal fun MovieAtmosphere(
    chapter: YearMovieChapter,
    progress: Float,
    reducedMotion: Boolean
) {
    val warm = chapter == YearMovieChapter.OPENING || chapter == YearMovieChapter.FINALE ||
            chapter == YearMovieChapter.FEATURED || chapter == YearMovieChapter.MONTH_STORY
    val gold = if (warm) Color(0xFFE7C68A) else Color(0xFFA7D1B4)
    Canvas(Modifier.fillMaxSize()) {
        val p = if (reducedMotion) 0.5f else progress.coerceIn(0f, 1f)
        val center = Offset(size.width * (0.25f + p * 0.08f), size.height * 0.29f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(gold.copy(alpha = 0.105f), Color.Transparent),
                center = center,
                radius = maxOf(size.width, size.height) * 0.72f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFF06140F).copy(alpha = 0.30f)),
                center = Offset(size.width * 0.50f, size.height * 0.48f),
                radius = maxOf(size.width, size.height) * 0.90f
            )
        )
        val dustColor = Color.White.copy(alpha = 0.036f)
        val dustRadius = 0.56.dp.toPx()
        for (i in 1..110) {
            drawCircle(
                color = dustColor,
                radius = dustRadius,
                center = Offset(
                    size.width * MovieMotionRules.filmDustCoordinate(i, 137),
                    size.height * MovieMotionRules.filmDustCoordinate(i, 509)
                )
            )
        }
        val inset = 15.dp.toPx()
        val lineLength = 15.dp.toPx()
        val edgeColor = gold.copy(alpha = 0.14f)
        val stroke = 1.dp.toPx()
        // Projeksiyon karesini hatırlatan dört küçük köşe işareti.
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        drawLine(edgeColor, Offset(left, top), Offset(left + lineLength, top), stroke)
        drawLine(edgeColor, Offset(left, top), Offset(left, top + lineLength), stroke)
        drawLine(edgeColor, Offset(right, top), Offset(right - lineLength, top), stroke)
        drawLine(edgeColor, Offset(right, top), Offset(right, top + lineLength), stroke)
        drawLine(edgeColor, Offset(left, bottom), Offset(left + lineLength, bottom), stroke)
        drawLine(edgeColor, Offset(left, bottom), Offset(left, bottom - lineLength), stroke)
        drawLine(edgeColor, Offset(right, bottom), Offset(right - lineLength, bottom), stroke)
        drawLine(edgeColor, Offset(right, bottom), Offset(right, bottom - lineLength), stroke)
    }
}
