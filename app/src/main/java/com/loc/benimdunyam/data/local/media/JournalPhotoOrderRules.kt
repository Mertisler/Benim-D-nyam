package com.loc.benimdunyam.data.local.media

/**
 * Pure ordering rules shared by the editor and the published gallery.
 * Only filenames already present on disk can appear in the effective order.
 */
object JournalPhotoOrderRules {
    fun reconcile(availableIds: List<String>, storedOrder: List<String>): List<String> {
        val available = availableIds.distinct()
        val allowed = available.toSet()
        val preferred = storedOrder.distinct().filter { it in allowed }
        return preferred + available.filterNot { it in preferred.toSet() }
    }

    fun move(ids: List<String>, photoId: String, delta: Int): List<String> {
        require(delta == -1 || delta == 1) { "Sadece bir adım taşıma desteklenir." }
        val from = ids.indexOf(photoId)
        if (from < 0) return ids
        val to = from + delta
        if (to !in ids.indices) return ids
        return ids.toMutableList().apply {
            val value = removeAt(from)
            add(to, value)
        }
    }

    fun cover(requested: String?, orderedIds: List<String>): String? =
        requested?.takeIf { it in orderedIds } ?: orderedIds.firstOrNull()
}
