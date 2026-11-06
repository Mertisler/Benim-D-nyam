package com.loc.benimdunyam.domain.model

/** Stable enum names are persisted to Room; do not rename without migration. */
enum class PersonSpecialMemoryCategory {
    UNFORGETTABLE,
    HAPPY_MOMENT,
    TURNING_POINT,
    SPECIAL_DAY,
    LIFE_LESSON
}

/** A journal can be special for one person, but ordinary for another. */
data class PersonSpecialMemory(
    val personId: String,
    val journalId: String,
    val category: PersonSpecialMemoryCategory,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
)
