package com.loc.benimdunyam.feature.yearreview.featured

/** Android, Room ve Compose'dan bağımsız seçim kuralları. */
object YearFeaturedMemoryRules {
    const val LIMIT: Int = 5

    fun canAdd(existingIds: List<String>, requestedId: String): Boolean =
        requestedId.isNotBlank() && requestedId !in existingIds && existingIds.size < LIMIT

    /** -1: bir basamak öne, +1: bir basamak arkaya. Sınırda değişmez. */
    fun move(ids: List<String>, requestedId: String, direction: Int): List<String> {
        if (direction != -1 && direction != 1) return ids
        val at = ids.indexOf(requestedId)
        val to = at + direction
        if (at !in ids.indices || to !in ids.indices) return ids
        return ids.toMutableList().apply {
            val other = this[to]
            this[to] = this[at]
            this[at] = other
        }
    }
}
