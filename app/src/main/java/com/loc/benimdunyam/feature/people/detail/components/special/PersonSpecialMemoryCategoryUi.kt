package com.loc.benimdunyam.feature.people.detail.components.special

import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory

internal fun PersonSpecialMemoryCategory.displayName(): String = when (this) {
    PersonSpecialMemoryCategory.UNFORGETTABLE -> "Unutulmaz anı"
    PersonSpecialMemoryCategory.HAPPY_MOMENT -> "Mutlu an"
    PersonSpecialMemoryCategory.TURNING_POINT -> "Dönüm noktası"
    PersonSpecialMemoryCategory.SPECIAL_DAY -> "Özel gün"
    PersonSpecialMemoryCategory.LIFE_LESSON -> "Bana bir şey öğretti"
}

internal fun PersonSpecialMemoryCategory.symbol(): String = when (this) {
    PersonSpecialMemoryCategory.UNFORGETTABLE -> "⭐"
    PersonSpecialMemoryCategory.HAPPY_MOMENT -> "💛"
    PersonSpecialMemoryCategory.TURNING_POINT -> "🌱"
    PersonSpecialMemoryCategory.SPECIAL_DAY -> "🎉"
    PersonSpecialMemoryCategory.LIFE_LESSON -> "📖"
}
