package com.loc.benimdunyam.feature.journal.journaleditor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex

import com.loc.benimdunyam.domain.model.JournalMood

import kotlin.math.roundToInt


data class MoodFlight(
    val mood: JournalMood,
    val source: Offset,
    val target: Offset
)


@Composable
fun FlyingMoodBubble(
    flight: MoodFlight,
    onFinished: () -> Unit
) {

    // Animasyon ilerleme değeri: 0 -> 1
    val progress = remember(flight) {
        Animatable(0f)
    }

    val density = LocalDensity.current

    val bubbleRadius = with(density) {
        22.dp.toPx()
    }

    val arcHeight = with(density) {
        55.dp.toPx()
    }

    // Animasyonu başlat.
    LaunchedEffect(flight) {

        progress.animateTo(
            targetValue = 1f,

            animationSpec = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            )
        )

        onFinished()
    }

    val t = progress.value

    // İkinci derece Bezier eğrisi.
    // Emoji düz bir çizgi yerine kavisli hareket eder.

    val inverseT = 1f - t

    val controlX = (
            flight.source.x + flight.target.x
            ) / 2f

    val controlY = (
            flight.source.y + flight.target.y
            ) / 2f - arcHeight

    val currentX =
        inverseT * inverseT * flight.source.x +
                2f * inverseT * t * controlX +
                t * t * flight.target.x

    val currentY =
        inverseT * inverseT * flight.source.y +
                2f * inverseT * t * controlY +
                t * t * flight.target.y

    // Son anda hafifçe küçülerek rozete oturur.
    val scale = 1.15f - (t * 0.25f)

    // Hedefe yaklaştığında yumuşakça kaybolur.
    var alpha = if (t > 0.88f) {
        ((1f - t) / 0.12f).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {

        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (currentX - bubbleRadius).roundToInt(),
                        y = (currentY - bubbleRadius).roundToInt()
                    )
                }
                .size(44.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape
                )
                .graphicsLayer {

                    scaleX = scale
                    scaleY = scale

                    alpha = alpha

                    rotationZ = (1f - t) * 10f
                },

            shape = CircleShape,

            color = Color(0xFFFFFCF7),

            border = BorderStroke(
                width = 1.dp,
                color = Color(0xFFDCE6D7)
            ),

            shadowElevation = 3.dp
        ) {

            Box(
                modifier = Modifier.fillMaxSize(),

                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = flight.mood.emoji,

                    fontSize = 27.sp
                )
            }
        }
    }
}
