package com.loc.benimdunyam.feature.yearreview

/**
 * Oynatıcı süresi ve görsel kamera hareketleri için saf Kotlin kuralları.
 * Animasyonlar kapalıyken fotoğraf sabit kalır, ilerleme 0..1 aralığına çekilir.
 */
internal object MovieMotionRules {
    fun remainingMillis(duration: Int, progress: Float): Int =
        (duration.coerceAtLeast(1).toFloat() * (1f - safeProgress(progress)))
            .toInt().coerceAtLeast(1)

    fun photoScale(progress: Float, reducedMotion: Boolean): Float =
        if (reducedMotion) 1f else 1f + 0.085f * safeProgress(progress)

    private fun safeProgress(progress: Float): Float =
        if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

    fun filmDustCoordinate(index: Int, multiplier: Int): Float =
        ((index.toLong() * multiplier) % 997L).toFloat() / 997f
}
