package com.loc.benimdunyam.feature.journal.journaleditor.components

import com.loc.benimdunyam.core.designsystem.scenicDrawable
import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random


@Composable
fun ParchmentBackground(
    modifier: Modifier = Modifier
) {

    val themePreset = AppThemeController.selected
    // Özel illüstrasyonlar defter editöründe tam ekran görünür.
    // Diğer temalar için mevcut parşömen dokusu korunur.
    if (scenicDrawable(themePreset) != null) {
        // Ortak sahne zaten NavGraph altında çiziliyor; burada tekrar çizilmez.
        return
    }
    val palette = AppThemeController.palette
    val textureInk = themedColor(Color(0xFF806F55))
    Canvas(
        modifier = modifier
    ) {

        // ANA KAĞIT RENGİ

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.background, palette.paper, palette.backgroundEnd)
            )
        )

        // KENAR GÖLGELERİ

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    themedColor(Color(0xFF9C8868)).copy(alpha = 0.13f),
                    Color.Transparent,
                    Color.Transparent,
                    themedColor(Color(0xFF9C8868)).copy(alpha = 0.10f)
                )
            )
        )

        // KAĞIT DOKUSU

        val random = Random(37)

        repeat(260) {

            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height

            val radius = random.nextFloat() * 1.4f + 0.25f
            val alpha = random.nextFloat() * 0.055f + 0.012f

            drawCircle(
                color = textureInk.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }

        // YATAY KAT İZİ

        drawLine(
            color = themedColor(Color(0xFF9C8C73)).copy(alpha = 0.10f),

            start = Offset(
                0f,
                size.height * 0.51f
            ),

            end = Offset(
                size.width,
                size.height * 0.51f
            ),

            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.White.copy(alpha = 0.20f),

            start = Offset(
                0f,
                size.height * 0.51f + 2.dp.toPx()
            ),

            end = Offset(
                size.width,
                size.height * 0.51f + 2.dp.toPx()
            ),

            strokeWidth = 1.dp.toPx()
        )

        // BOTANİK ÇİZİMLER

        drawSprig(
            baseX = 20.dp.toPx(),
            baseY = size.height * 0.98f,
            scale = 150.dp.toPx(),
            direction = 1f
        )

        drawSprig(
            baseX = size.width - 20.dp.toPx(),
            baseY = size.height * 0.98f,
            scale = 170.dp.toPx(),
            direction = -1f
        )

        drawSprig(
            baseX = 4.dp.toPx(),
            baseY = size.height * 0.29f,
            scale = 90.dp.toPx(),
            direction = 1f
        )
    }
}


// -------------------------------------------------
// BOTANİK ÇİZİM
// -------------------------------------------------

private fun DrawScope.drawSprig(
    baseX: Float,
    baseY: Float,
    scale: Float,
    direction: Float
) {

    val botanicalInk = themedColor(Color(0xFF81745F)).copy(alpha = 0.42f)

    val stem = Path().apply {

        moveTo(baseX, baseY)

        cubicTo(
            baseX + direction * scale * 0.06f,
            baseY - scale * 0.28f,

            baseX + direction * scale * 0.32f,
            baseY - scale * 0.57f,

            baseX + direction * scale * 0.22f,
            baseY - scale
        )
    }

    drawPath(
        path = stem,
        color = botanicalInk,
        style = Stroke(
            width = 1.15.dp.toPx()
        )
    )

    for (index in 1..5) {

        val progress = index / 6f

        val leafX =
            baseX + direction * scale * (0.02f + progress * 0.22f)

        val leafY =
            baseY - scale * progress * 0.88f

        val leafSize =
            scale * (0.075f - progress * 0.004f)

        drawOval(
            color = botanicalInk,

            topLeft = Offset(
                leafX - leafSize * 0.75f,
                leafY - leafSize * 0.28f
            ),

            size = Size(
                leafSize,
                leafSize * 0.38f
            )
        )

        drawOval(
            color = botanicalInk.copy(alpha = 0.8f),

            topLeft = Offset(
                leafX - direction * leafSize * 0.30f,
                leafY - leafSize * 0.65f
            ),

            size = Size(
                leafSize,
                leafSize * 0.38f
            )
        )
    }

    val flowerX =
        baseX + direction * scale * 0.22f

    val flowerY =
        baseY - scale

    drawCircle(
        color = botanicalInk,
        radius = scale * 0.025f,
        center = Offset(flowerX, flowerY)
    )

    for (petal in 0..4) {

        val angle = petal * (Math.PI * 2.0 / 5.0)

        val petalX =
            flowerX + cos(angle).toFloat() * scale * 0.045f

        val petalY =
            flowerY + sin(angle).toFloat() * scale * 0.045f

        drawCircle(
            color = botanicalInk.copy(alpha = 0.8f),
            radius = scale * 0.025f,
            center = Offset(petalX, petalY)
        )
    }
}