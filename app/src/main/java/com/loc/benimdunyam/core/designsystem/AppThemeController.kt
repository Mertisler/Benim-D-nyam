package com.loc.benimdunyam.core.designsystem

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

enum class AppThemeCategory(val label: String, val emoji: String) {
    HOT("HOT", "🔥"),
    LIGHT("LIGHT", "☀️"),
    DARK("DARK", "🌙")
}

/** Kullanıcının seçebileceği daha karakterli ve estetik 10 tema. */
enum class AppThemePreset(
    val storedValue: String,
    val title: String,
    val subtitle: String,
    val category: AppThemeCategory
) {
    SUNSET_REEDS("sunset_reeds", "Pembe Gün Batımı", "Çiçekler & gün ışığı", AppThemeCategory.HOT),
    ROSE_DESERT("rose_desert", "Gül Kumları", "Pudra & mercan", AppThemeCategory.HOT),
    HONEY_STORY("honey_story", "Bal Hatırası", "Bal köpüğü & tarçın", AppThemeCategory.HOT),

    CLOVER_GARDEN("clover_garden", "Yonca Baharı", "Yeşil & krem", AppThemeCategory.LIGHT),
    SKY_LEAVES("sky_leaves", "Mavi Yaprak", "Gökyüzü & filiz", AppThemeCategory.LIGHT),
    LAVENDER_CLOUD("lavender_cloud", "Lavanta Bulutu", "Lila & şeftali", AppThemeCategory.LIGHT),
    MINT_COAST("mint_coast", "Mint Sahili", "Mint & kum", AppThemeCategory.LIGHT),

    JELLYFISH_NIGHT("jellyfish_night", "Okyanus Rüyası", "Işıltılı denizanası", AppThemeCategory.DARK),
    FOREST_LANTERN("forest_lantern", "Masal Ormanı", "Işıklar & orman evi", AppThemeCategory.DARK),
    MIDNIGHT_WHALE("midnight_whale", "Ay Balinası", "Derin mavi & turkuaz", AppThemeCategory.DARK);

    companion object {
        fun fromStoredValue(value: String?): AppThemePreset =
            entries.firstOrNull { it.storedValue == value } ?: CLOVER_GARDEN
    }
}

data class AppDesignPalette(
    val isDark: Boolean,
    val background: Color,
    val backgroundEnd: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val softPrimary: Color,
    val tape: Color,
    val border: Color
)

fun appPalette(preset: AppThemePreset): AppDesignPalette = when (preset) {
    AppThemePreset.SUNSET_REEDS -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFFCEBDE),
        backgroundEnd = Color(0xFFF4D4C2),
        paper = Color(0xFFFFFAF6),
        ink = Color(0xFF4A3026),
        muted = Color(0xFF8C6A5C),
        primary = Color(0xFFB53F5E),
        onPrimary = Color.White,
        secondary = Color(0xFFC57A5A),
        softPrimary = Color(0xFFFFDCE3),
        tape = Color(0xFFF5E3D5),
        border = Color(0xFFEBCBBD)
    )

    AppThemePreset.ROSE_DESERT -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFF9E4E4),
        backgroundEnd = Color(0xFFF1D2D8),
        paper = Color(0xFFFFFAFC),
        ink = Color(0xFF4B323A),
        muted = Color(0xFF8B6971),
        primary = Color(0xFFF55E7A),
        onPrimary = Color.White,
        secondary = Color(0xFFD48D77),
        softPrimary = Color(0xFFFFD8E1),
        tape = Color(0xFFF2DEE2),
        border = Color(0xFFE9CBD3)
    )

    AppThemePreset.HONEY_STORY -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFF7E7D2),
        backgroundEnd = Color(0xFFEED3B2),
        paper = Color(0xFFFFFCF8),
        ink = Color(0xFF433328),
        muted = Color(0xFF7E6756),
        primary = Color(0xFFD78732),
        onPrimary = Color.White,
        secondary = Color(0xFFB78253),
        softPrimary = Color(0xFFF7E3BF),
        tape = Color(0xFFF2E4CE),
        border = Color(0xFFE5D0B1)
    )

    AppThemePreset.CLOVER_GARDEN -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFF1F7E4),
        backgroundEnd = Color(0xFFE2EECD),
        paper = Color(0xFFFFFEFB),
        ink = Color(0xFF2F392C),
        muted = Color(0xFF6B7864),
        primary = Color(0xFF7EA053),
        onPrimary = Color.White,
        secondary = Color(0xFF93B96E),
        softPrimary = Color(0xFFE5EFD5),
        tape = Color(0xFFE8EBD5),
        border = Color(0xFFD8E1C1)
    )

    AppThemePreset.SKY_LEAVES -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFEFF7FF),
        backgroundEnd = Color(0xFFDDEBF9),
        paper = Color(0xFFFFFEFE),
        ink = Color(0xFF273646),
        muted = Color(0xFF627890),
        primary = Color(0xFF4B95EE),
        onPrimary = Color.White,
        secondary = Color(0xFF78B3FF),
        softPrimary = Color(0xFFDCEEFF),
        tape = Color(0xFFE3EDF3),
        border = Color(0xFFD1DFEC)
    )

    AppThemePreset.LAVENDER_CLOUD -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFF6EFF9),
        backgroundEnd = Color(0xFFF1E2EF),
        paper = Color(0xFFFFFCFF),
        ink = Color(0xFF44384C),
        muted = Color(0xFF7F6D86),
        primary = Color(0xFFCC69A7),
        onPrimary = Color.White,
        secondary = Color(0xFFDFA487),
        softPrimary = Color(0xFFF6DCEF),
        tape = Color(0xFFEFE2EE),
        border = Color(0xFFE3D0E1)
    )

    AppThemePreset.MINT_COAST -> AppDesignPalette(
        isDark = false,
        background = Color(0xFFF0FAF6),
        backgroundEnd = Color(0xFFE1F2ED),
        paper = Color(0xFFFFFEFC),
        ink = Color(0xFF2A3B3A),
        muted = Color(0xFF62807B),
        primary = Color(0xFF3BB7A5),
        onPrimary = Color.White,
        secondary = Color(0xFF79D0C2),
        softPrimary = Color(0xFFD8F0EA),
        tape = Color(0xFFE8EFEA),
        border = Color(0xFFD2E4E1)
    )

    AppThemePreset.JELLYFISH_NIGHT -> AppDesignPalette(
        isDark = true,
        background = Color(0xFF071122),
        backgroundEnd = Color(0xFF0D1B33),
        paper = Color(0xFF0E1A31),
        ink = Color(0xFFECF5FF),
        muted = Color(0xFF97B2D3),
        primary = Color(0xFF2867D0),
        onPrimary = Color.White,
        secondary = Color(0xFF69B5FF),
        softPrimary = Color(0xFF13284B),
        tape = Color(0xFF10223F),
        border = Color(0xFF21477A)
    )

    AppThemePreset.FOREST_LANTERN -> AppDesignPalette(
        isDark = true,
        background = Color(0xFF08140D),
        backgroundEnd = Color(0xFF112317),
        paper = Color(0xFF0F2015),
        ink = Color(0xFFEAF8E9),
        muted = Color(0xFFA0BE9F),
        primary = Color(0xFF64C96B),
        onPrimary = Color(0xFF062E0C),
        secondary = Color(0xFF8ADE73),
        softPrimary = Color(0xFF163220),
        tape = Color(0xFF132C1A),
        border = Color(0xFF2E5B39)
    )

    AppThemePreset.MIDNIGHT_WHALE -> AppDesignPalette(
        isDark = true,
        background = Color(0xFF081B2B),
        backgroundEnd = Color(0xFF11324D),
        paper = Color(0xFF0C2438),
        ink = Color(0xFFEAF7FF),
        muted = Color(0xFF99BDD6),
        primary = Color(0xFF36C8E8),
        onPrimary = Color(0xFF042D38),
        secondary = Color(0xFF69D6E6),
        softPrimary = Color(0xFF123350),
        tape = Color(0xFF102A40),
        border = Color(0xFF1F5074)
    )
}

object AppThemeController {
    private const val PREF_FILE = "benim_dunyam_appearance"
    private const val KEY_PRESET = "theme_preset"

    var selected by mutableStateOf(AppThemePreset.CLOVER_GARDEN)
        private set

    val palette: AppDesignPalette
        get() = appPalette(selected)

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        selected = AppThemePreset.fromStoredValue(prefs.getString(KEY_PRESET, null))
    }

    fun choose(context: Context, preset: AppThemePreset) {
        if (selected == preset) return
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESET, preset.storedValue)
            .apply()
        selected = preset
    }
}

/**
 * Eski sabit renkleri yeni palet semantiğine uyarlar.
 * Duygu/uyarı renklerinin kimliğini mümkün olduğunca korur.
 */
fun themedColor(original: Color): Color {
    if (original.alpha <= 0f) return original

    val palette = AppThemeController.palette
    val r = original.red
    val g = original.green
    val b = original.blue
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val saturation = if (high == 0f) 0f else (high - low) / high
    val brightness = 0.2126f * r + 0.7152f * g + 0.0722f * b

    // Çok canlı uyarı/duygu renklerini koru.
    if (saturation >= 0.42f && (
                (r > g * 1.13f && r > b * 0.94f) ||
                        (b > g * 1.18f) ||
                        (g > r * 1.2f && g > b * 1.08f)
                )
    ) {
        return original
    }

    val mapped = if (!palette.isDark) {
        when {
            g > r * 1.045f && g >= b * 0.92f && saturation >= 0.10f -> when {
                brightness >= 0.79f -> palette.softPrimary
                brightness >= 0.58f -> palette.secondary
                else -> palette.primary
            }

            brightness >= 0.965f -> palette.paper
            brightness >= 0.885f -> if (saturation < 0.09f) palette.paper else palette.background
            brightness >= 0.795f -> if (saturation < 0.11f) palette.border else palette.tape
            brightness >= 0.69f -> palette.tape
            brightness >= 0.37f -> palette.muted
            else -> palette.ink
        }
    } else {
        when {
            g > r * 1.045f && g >= b * 0.92f && saturation >= 0.10f -> when {
                brightness >= 0.79f -> palette.secondary.copy(alpha = 0.95f)
                brightness >= 0.58f -> palette.primary
                else -> palette.softPrimary
            }

            brightness >= 0.965f -> palette.paper
            brightness >= 0.885f -> palette.tape
            brightness >= 0.735f -> palette.softPrimary
            brightness >= 0.57f -> palette.border
            brightness >= 0.37f -> palette.muted
            else -> palette.ink
        }
    }

    return mapped.copy(alpha = original.alpha)
}
