package com.loc.benimdunyam.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.loc.benimdunyam.R

/** Paylaşılan, tam ekran dekoratif tema görseli. Kaydırılabilen içerik bu katmanın üzerindedir. */
fun scenicDrawable(preset: AppThemePreset): Int? = when (preset) {
    AppThemePreset.SUNSET_REEDS -> R.drawable.pink_sunset_hero
    AppThemePreset.JELLYFISH_NIGHT -> R.drawable.dream_ocean_hero
    AppThemePreset.FOREST_LANTERN -> R.drawable.storybook_forest_hero
    else -> null
}

@Composable
fun ThemeScenicBackground(
    modifier: Modifier = Modifier,
    // Eski çağrılarla uyumluluk: artık yükseklik sınırı uygulanmaz.
    @Suppress("UNUSED_PARAMETER") heroHeightDp: Int = 338
) {
    val palette = AppThemeController.palette
    val illustration = scenicDrawable(AppThemeController.selected)
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(palette.background, palette.backgroundEnd))
        )
    ) {
        if (illustration != null) {
            Image(
                painter = painterResource(id = illustration),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
            // Yalnızca görüntüyü yumuşatır, düğmeler ve metinler bunun üstünde ayrı çizilir.
            // Yüksek kontrast gerektiğinde bu saydamlık değerleri ayarlanabilir.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            if (palette.isDark) {
                                listOf(
                                    Color(0xFF041526).copy(alpha = 0.12f),
                                    Color(0xFF041526).copy(alpha = 0.23f),
                                    Color(0xFF030C1C).copy(alpha = 0.38f)
                                )
                            } else {
                                listOf(
                                    palette.background.copy(alpha = 0.24f),
                                    palette.background.copy(alpha = 0.43f),
                                    palette.backgroundEnd.copy(alpha = 0.68f)
                                )
                            }
                        )
                    )
            )
        }
    }
}

/** Tema seçiminde kullanılan kırpılabilir gerçek görsel önizlemesi. */
@Composable
fun ThemeSceneThumbnail(
    preset: AppThemePreset,
    modifier: Modifier = Modifier
) {
    val illustration = scenicDrawable(preset) ?: return
    Image(
        painter = painterResource(illustration),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter
    )
}
