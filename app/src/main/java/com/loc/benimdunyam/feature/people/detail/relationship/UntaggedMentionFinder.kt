package com.loc.benimdunyam.feature.people.detail.relationship

import java.util.Locale

/** Kişinin adının geçtiği ama etiketlenmemiş bir anı. */
data class UntaggedMention(
    val journalId: String,
    val title: String,
    val entryDate: Long,
    /** Eşleşmenin çevresinden kısa bir alıntı. */
    val excerpt: String
)

/**
 * Kişinin adı metinde geçiyor ama @etiketi yoksa bunu bulur.
 *
 * Türkçe eklere duyarlıdır: "Ayşe", "Ayşe'yle", "Ayşe’nin", "AYŞE'ye" eşleşir;
 * "Ayşegül" ya da "Mehmetçik" gibi adın bir parçası olan başka kelimeler eşleşmez.
 * Büyük/küçük harf karşılaştırması Türkçe kurallarıyla yapılır (I/ı, İ/i).
 *
 * Yalnızca öneri üretir; hiçbir veri değiştirmez.
 */
object UntaggedMentionFinder {

    private val TR = Locale.forLanguageTag("tr-TR")
    private const val MIN_NAME_LENGTH = 3
    private const val EXCERPT_RADIUS = 40

    data class Candidate(
        val journalId: String,
        val title: String,
        val content: String,
        val entryDate: Long,
        val taggedPersonIds: Set<String>
    )

    fun find(
        personId: String,
        personName: String,
        journals: List<Candidate>,
        limit: Int = 20
    ): List<UntaggedMention> {
        val name = personName.trim()
        // Çok kısa adlar ("Ali" hariç değil ama "Al" gibi) çok fazla yanlış eşleşme üretir.
        if (name.length < MIN_NAME_LENGTH) return emptyList()
        val pattern = patternFor(name)
        return journals.asSequence()
            .filter { personId !in it.taggedPersonIds }
            .mapNotNull { journal ->
                val haystack = "${journal.title}\n${journal.content}"
                val match = pattern.find(haystack.lowercase(TR)) ?: return@mapNotNull null
                UntaggedMention(
                    journalId = journal.journalId,
                    title = journal.title,
                    entryDate = journal.entryDate,
                    excerpt = excerpt(haystack, match.range)
                )
            }
            .sortedByDescending { it.entryDate }
            .take(limit)
            .toList()
    }

    /** Ad + isteğe bağlı kesme işaretli ek; önünde ve sonunda harf olmamalı. */
    internal fun patternFor(name: String): Regex {
        val escaped = Regex.escape(name.lowercase(TR))
        return Regex("(?<![\\p{L}\\p{N}])$escaped(?:['’][\\p{L}]+)?(?![\\p{L}\\p{N}])")
    }

    private fun excerpt(text: String, range: IntRange): String {
        // lowercase(TR) metnin uzunluğunu değiştirebilir ("İ" → "i̇" değil, Türkçe'de "i");
        // Türkçe yerelde uzunluk korunur. Yine de güvenli sınırlarla keseriz.
        val start = (range.first - EXCERPT_RADIUS).coerceIn(0, text.length)
        val end = (range.last + 1 + EXCERPT_RADIUS).coerceIn(start, text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
    }
}
