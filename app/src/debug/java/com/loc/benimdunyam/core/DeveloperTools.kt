package com.loc.benimdunyam.core

/**
 * Debug derlemesi: geliştirici araçları (eşitleme ayrıntıları, arşiv sağlığı,
 * yedekleme provası, hesap taşıma, tanılama) görünür.
 *
 * Release karşılığı src/release altındadır ve ENABLED = false'tur. Değer derleme
 * zamanı sabiti olduğu için release'te bu araçlara giden kod R8 tarafından
 * tamamen çıkarılır; kullanıcının uygulamasında bulunmaz.
 */
object DeveloperTools {
    const val ENABLED: Boolean = true
}
