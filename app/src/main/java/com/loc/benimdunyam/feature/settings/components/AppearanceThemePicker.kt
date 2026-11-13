package com.loc.benimdunyam.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.loc.benimdunyam.core.designsystem.AppDesignPalette
import com.loc.benimdunyam.core.designsystem.AppThemeCategory
import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.AppThemePreset
import com.loc.benimdunyam.core.designsystem.appPalette
import com.loc.benimdunyam.core.designsystem.scenicDrawable
import com.loc.benimdunyam.core.designsystem.ThemeSceneThumbnail
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun AppearanceThemePicker(enabled: Boolean = true) {
    val context = LocalContext.current
    val current = AppThemeController.selected
    var selectedCategory by rememberSaveable { mutableStateOf(current.category.name) }
    var previewPreset by remember { mutableStateOf<AppThemePreset?>(null) }

    LaunchedEffect(current.category) {
        selectedCategory = current.category.name
    }

    val category = remember(selectedCategory) {
        AppThemeCategory.valueOf(selectedCategory)
    }

    val options = remember(category) {
        AppThemePreset.entries.filter { it.category == category }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Uygulama teması",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Bütün sayfalarda ortak görünüm sağlar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "İLLÜSTRASYONLU DÜNYALAR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                listOf(
                    AppThemePreset.JELLYFISH_NIGHT,
                    AppThemePreset.FOREST_LANTERN,
                    AppThemePreset.SUNSET_REEDS
                ).forEach { preset ->
                    ThemePreviewCard(
                        preset = preset,
                        selected = current == preset,
                        enabled = enabled,
                        onClick = { previewPreset = preset },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                text = "TÜM TEMALAR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            CategoryTabs(
                selected = category,
                onSelected = { selectedCategory = it.name }
            )

            ThemeGrid(
                presets = options,
                current = current,
                enabled = enabled,
                onSelect = { previewPreset = it }
            )

            Text(
                text = "Bir tasarıma dokun, büyük önizlemesini incele ve uygula. Seçimin cihazında kalır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    previewPreset?.let { preview ->
        val previewColors = appPalette(preview)
        Dialog(onDismissRequest = { previewPreset = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = preview.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = preview.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ThemePreviewCard(
                        preset = preview,
                        selected = current == preview,
                        enabled = false,
                        onClick = {},
                        modifier = Modifier.width(205.dp)
                    )
                    Button(
                        onClick = {
                            AppThemeController.choose(context, preview)
                            previewPreset = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewColors.primary,
                            contentColor = previewColors.onPrimary
                        )
                    ) {
                        Text(if (current == preview) "Seçili tema" else "Temayı uygula")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    selected: AppThemeCategory,
    onSelected: (AppThemeCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppThemeCategory.entries.forEach { category ->
            val active = selected == category
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelected(category) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${category.emoji} ${category.label}",
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun ThemeGrid(
    presets: List<AppThemePreset>,
    current: AppThemePreset,
    enabled: Boolean,
    onSelect: (AppThemePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { preset ->
                    ThemePreviewCard(
                        preset = preset,
                        selected = current == preset,
                        enabled = enabled,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    preset: AppThemePreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appPalette(preset)
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.58f)
                .border(BorderStroke(if (selected) 2.4.dp else 1.dp, borderColor), shape)
                .clip(shape)
                .clickable(enabled = enabled, onClick = onClick)
                .background(colors.background)
        ) {
            if (scenicDrawable(preset) != null) {
                ThemeSceneThumbnail(
                    preset = preset,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ThemeArtworkBackground(
                    preset = preset,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hi,",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (colors.isDark) Color.White.copy(alpha = 0.92f) else colors.ink
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (colors.isDark) Color.White.copy(alpha = 0.14f) else colors.paper.copy(alpha = 0.8f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                PreviewContentCards(colors = colors)

                PreviewBottomBar(colors = colors)
            }

            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 3.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Seçili",
                        modifier = Modifier.padding(3.dp).size(16.dp)
                    )
                }
            }
        }

        Text(
            text = preset.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = preset.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewContentCards(colors: AppDesignPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.paper.copy(alpha = if (colors.isDark) 0.82f else 0.92f), RoundedCornerShape(10.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 0) 0.84f else 0.77f)
                        .height(5.dp)
                        .background(colors.ink.copy(alpha = if (colors.isDark) 0.52f else 0.14f), RoundedCornerShape(50))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 0) 0.92f else 0.68f)
                        .height(4.dp)
                        .background(colors.ink.copy(alpha = if (colors.isDark) 0.28f else 0.10f), RoundedCornerShape(50))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(4.dp)
                        .background(colors.ink.copy(alpha = if (colors.isDark) 0.2f else 0.08f), RoundedCornerShape(50))
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .background(colors.paper.copy(alpha = if (colors.isDark) 0.72f else 0.9f), RoundedCornerShape(7.dp))
                )
            }
        }
    }
}

@Composable
private fun PreviewBottomBar(colors: AppDesignPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(colors.paper.copy(alpha = if (colors.isDark) 0.92f else 0.98f)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(8.dp)
                .background(colors.ink.copy(alpha = 0.72f), CircleShape)
        )
        Surface(
            shape = CircleShape,
            color = colors.primary,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(7.dp)
                    .background(colors.onPrimary.copy(alpha = 0.95f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(8.dp)
                .background(colors.ink.copy(alpha = 0.72f), CircleShape)
        )
    }
}

@Composable
private fun ThemeArtworkBackground(
    preset: AppThemePreset,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        fun moon(center: Offset, radius: Float, color: Color, alpha: Float = 1f) {
            drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center)
        }

        fun hill(color: Color, yBase: Float, controlShift: Float, alpha: Float = 1f) {
            val path = Path().apply {
                moveTo(0f, yBase)
                quadraticBezierTo(size.width * 0.35f, yBase - controlShift, size.width * 0.58f, yBase - controlShift * 0.18f)
                quadraticBezierTo(size.width * 0.82f, yBase + controlShift * 0.10f, size.width, yBase - controlShift * 0.12f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = path, color = color.copy(alpha = alpha), style = Fill)
        }

        fun clovers(tint: Color) {
            repeat(7) { index ->
                val cx = size.width * (0.12f + (index % 3) * 0.28f + (index / 3) * 0.06f)
                val cy = size.height * (0.18f + (index / 3) * 0.18f)
                repeat(4) { petal ->
                    val angle = petal * 90f
                    val dx = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * 8f
                    val dy = kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * 8f
                    drawCircle(tint.copy(alpha = 0.26f), radius = 8.5f, center = Offset(cx + dx, cy + dy))
                }
                drawRoundRect(
                    color = tint.copy(alpha = 0.18f),
                    topLeft = Offset(cx - 1.2f, cy + 6f),
                    size = Size(2.4f, 12f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = when (preset) {
                    AppThemePreset.SUNSET_REEDS -> listOf(Color(0xFFF6C9C0), Color(0xFFF2B48D), Color(0xFFE5AA7A))
                    AppThemePreset.ROSE_DESERT -> listOf(Color(0xFFF8CFD7), Color(0xFFF7C0BE), Color(0xFFECC39A))
                    AppThemePreset.HONEY_STORY -> listOf(Color(0xFFF8E0B1), Color(0xFFF2CE8D), Color(0xFFE7B06B))
                    AppThemePreset.CLOVER_GARDEN -> listOf(Color(0xFFCAE5A4), Color(0xFFDFEFC9), Color(0xFFF0F6E4))
                    AppThemePreset.SKY_LEAVES -> listOf(Color(0xFFB7D7F5), Color(0xFFD4E8FA), Color(0xFFF2F9FF))
                    AppThemePreset.LAVENDER_CLOUD -> listOf(Color(0xFFDCCBE8), Color(0xFFF2D6DF), Color(0xFFF7E9D5))
                    AppThemePreset.MINT_COAST -> listOf(Color(0xFF9DDED5), Color(0xFFCFEBDD), Color(0xFFF4E5CE))
                    AppThemePreset.JELLYFISH_NIGHT -> listOf(Color(0xFF071225), Color(0xFF0B1E45), Color(0xFF12386B))
                    AppThemePreset.FOREST_LANTERN -> listOf(Color(0xFF0A1A10), Color(0xFF16361D), Color(0xFF29552E))
                    AppThemePreset.MIDNIGHT_WHALE -> listOf(Color(0xFF08213A), Color(0xFF11486F), Color(0xFF1C6A8D))
                }
            ),
            size = size
        )

        when (preset) {
            AppThemePreset.SUNSET_REEDS -> {
                moon(Offset(size.width * 0.78f, size.height * 0.2f), 12f, Color(0xFFFFF5E2), 0.9f)
                repeat(7) {
                    val x = size.width * (0.08f + it * 0.13f)
                    drawLine(Color(0xFFB4674B).copy(alpha = 0.7f), Offset(x, size.height * 0.95f), Offset(x + 8f, size.height * 0.58f), 2.2f)
                    drawLine(Color(0xFFB4674B).copy(alpha = 0.55f), Offset(x + 7f, size.height * 0.68f), Offset(x + 16f, size.height * 0.61f), 1.6f)
                }
                hill(Color(0xFFE4B07E), size.height * 0.76f, 28f)
            }
            AppThemePreset.ROSE_DESERT -> {
                hill(Color(0xFFE0B994), size.height * 0.74f, 18f)
                hill(Color(0xFFD79A8F), size.height * 0.84f, 12f, 0.9f)
                repeat(5) {
                    val x = size.width * (0.1f + it * 0.18f)
                    drawLine(Color(0xFFB86E63).copy(alpha = 0.5f), Offset(x, size.height * 0.92f), Offset(x + 9f, size.height * 0.63f), 1.8f)
                }
            }
            AppThemePreset.HONEY_STORY -> {
                hill(Color(0xFFD7A66B), size.height * 0.78f, 24f)
                repeat(18) {
                    val x = size.width * ((it * 73) % 100 / 100f)
                    val y = size.height * (0.08f + ((it * 37) % 48) / 100f)
                    drawCircle(Color(0xFFFFEDB2).copy(alpha = 0.25f), radius = 3.3f + (it % 3), center = Offset(x, y))
                }
            }
            AppThemePreset.CLOVER_GARDEN -> clovers(Color(0xFF79A456))
            AppThemePreset.SKY_LEAVES -> {
                repeat(8) {
                    val x = size.width * (0.1f + it * 0.1f)
                    drawLine(Color(0xFF7DB05C).copy(alpha = 0.5f), Offset(x, size.height * 1.05f), Offset(x + 18f, size.height * (0.68f + (it % 2) * 0.05f)), 2f)
                    drawCircle(Color(0xFFA5D36A).copy(alpha = 0.4f), radius = 4.2f, center = Offset(x + 18f, size.height * (0.68f + (it % 2) * 0.05f)))
                    drawCircle(Color(0xFF7AB95B).copy(alpha = 0.28f), radius = 3.3f, center = Offset(x + 10f, size.height * (0.76f + (it % 2) * 0.04f)))
                }
            }
            AppThemePreset.LAVENDER_CLOUD -> {
                repeat(5) {
                    val cx = size.width * (0.18f + it * 0.16f)
                    val cy = size.height * (0.22f + (it % 2) * 0.11f)
                    drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.28f), radius = 16f, center = Offset(cx, cy))
                    drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.22f), radius = 13f, center = Offset(cx + 10f, cy + 2f))
                    drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.18f), radius = 11f, center = Offset(cx - 11f, cy + 3f))
                }
            }
            AppThemePreset.MINT_COAST -> {
                drawRect(Color(0xFF7AD3D4).copy(alpha = 0.66f), topLeft = Offset(0f, size.height * 0.58f), size = Size(size.width, size.height * 0.12f))
                hill(Color(0xFFEEDAB8), size.height * 0.83f, 8f)
                drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.55f), radius = 9f, center = Offset(size.width * 0.82f, size.height * 0.15f))
            }
            AppThemePreset.JELLYFISH_NIGHT -> {
                repeat(3) {
                    val cx = size.width * (0.22f + it * 0.25f)
                    val cy = size.height * (0.24f + (it % 2) * 0.1f)
                    drawCircle(Color(0xFF8EC0FF).copy(alpha = 0.16f), radius = 14f, center = Offset(cx, cy))
                    repeat(5) { t ->
                        drawLine(
                            Color(0xFF67AFFF).copy(alpha = 0.22f),
                            Offset(cx - 8f + t * 4f, cy + 8f),
                            Offset(cx - 10f + t * 5f, cy + 38f + t * 2f),
                            1.3f
                        )
                    }
                }
                repeat(22) {
                    val x = size.width * ((it * 31) % 100 / 100f)
                    val y = size.height * ((it * 17) % 100 / 100f)
                    drawCircle(Color.White.copy(alpha = 0.08f), radius = 1.4f, center = Offset(x, y))
                }
            }
            AppThemePreset.FOREST_LANTERN -> {
                repeat(14) {
                    val x = size.width * ((it * 11) % 100 / 100f)
                    val y = size.height * (0.12f + ((it * 23) % 64) / 100f)
                    drawCircle(Color(0xFFCFFF8A).copy(alpha = 0.18f), radius = 2.4f + (it % 3), center = Offset(x, y))
                }
                hill(Color(0xFF18311E), size.height * 0.78f, 24f)
                hill(Color(0xFF0E2414), size.height * 0.9f, 10f)
            }
            AppThemePreset.MIDNIGHT_WHALE -> {
                drawCircle(Color(0xFFE2F7FF).copy(alpha = 0.6f), radius = 8f, center = Offset(size.width * 0.88f, size.height * 0.16f))
                drawOval(
                    color = Color(0xFF75D7F2).copy(alpha = 0.35f),
                    topLeft = Offset(size.width * 0.36f, size.height * 0.18f),
                    size = Size(size.width * 0.34f, size.height * 0.12f)
                )
                drawOval(
                    color = Color(0xFF57B8D8).copy(alpha = 0.32f),
                    topLeft = Offset(size.width * 0.60f, size.height * 0.22f),
                    size = Size(size.width * 0.12f, size.height * 0.04f)
                )
                hill(Color(0xFF0D3550), size.height * 0.76f, 14f)
                drawRect(Color(0xFF5CCFEA).copy(alpha = 0.18f), topLeft = Offset(0f, size.height * 0.54f), size = Size(size.width, 5f))
            }
        }
    }
}
