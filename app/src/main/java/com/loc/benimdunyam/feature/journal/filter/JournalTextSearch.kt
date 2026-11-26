package com.loc.benimdunyam.feature.journal.filter

import com.loc.benimdunyam.domain.model.JournalEntry

/** Bulunan anı için vurgulanacak yerler. Aralıklar özgün metnin indeksleridir (son dahil değil). */
data class JournalSearchHighlight(
    val titleRanges: List<IntRange> = emptyList(),
    /** Eşleşmenin geçtiği bölümden kısa alıntı; yalnızca başlıkta/kişide eşleşme varsa null. */
    val snippet: String? = null,
    val snippetRanges: List<IntRange> = emptyList(),
    /** Aramaya kişi adından giren eşleşmeler (ör. "Ayşe"). */
    val matchedPeople: List<String> = emptyList()
)

data class JournalSearchResult(
    val journals: List<JournalEntry>,
    val highlights: Map<String, JournalSearchHighlight> = emptyMap(),
    /** Tüm kelimeleri içeren anı olmadığı için kelimelerden bazılarını içerenler gösteriliyor. */
    val isPartialMatch: Boolean = false
)

/**
 * Türkçe için arşiv metin araması.
 *
 * - Büyük/küçük harf ve Türkçe karakter farkı yok sayılır: "ayse" → "Ayşe", "yalniz" → "yalnız".
 * - Ekler tolere edilir: "denizde" → "deniz", "denize"; "ameliyatı" → "ameliyat"; "Ayşe'yle" → "Ayşe".
 *   Bunun için aranan kelimenin yaygın ekleri atılır ve metindeki kelimelerin bu kökle
 *   başlaması yeterli sayılır.
 * - Birden fazla kelimede önce hepsini içeren anılar aranır; hiç yoksa bazılarını içerenler
 *   gösterilir ve bu durum işaretlenir.
 * - Sonuçlar alakaya göre sıralanır: başlık ve kişi eşleşmesi metin içindeki eşleşmeden önemlidir;
 *   eşitlikte yeni anı önce gelir.
 *
 * Saf Kotlin, cihazda çalışır; hiçbir veri dışarı gönderilmez.
 */
object JournalTextSearch {

    private const val MIN_STEM = 4
    private const val SNIPPET_BEFORE = 40
    private const val SNIPPET_LENGTH = 160
    private val WHITESPACE = Regex("\\s+")

    /** Tek başına aranmadığı sürece yok sayılan bağlaçlar ve ekler. */
    private val STOP_WORDS = setOf(
        "ve", "ile", "bir", "bu", "o", "da", "de", "ki", "mi", "mu", "ne", "icin", "gibi", "ya", "ama", "en", "cok"
    )

    /**
     * Normalleştirilmiş (aksansız) biçimde yazılmış yaygın ekler. Uzundan kısaya denenir;
     * kök en az [MIN_STEM] harf kalacak şekilde en uzun ek atılır.
     */
    private val SUFFIXES = listOf(
        // çoğul + iyelik + hâl
        "larimizdan", "lerimizden", "larindan", "lerinden", "larinda", "lerinde", "larina", "lerine",
        "larini", "lerini", "larim", "lerim", "lari", "leri", "lar", "ler",
        "imizden", "imizde", "mizde", "imizi", "mizi", "imize", "mize", "imiz", "umuz", "miz", "muz", "iniz", "niz",
        // fiil: geçmiş, şimdiki, mastar
        "mistik", "mistim", "misti", "iyorum", "uyorum", "iyor", "uyor", "yor",
        "dik", "dim", "din", "tik", "tim", "tin", "duk", "dum", "tuk", "tum",
        "mak", "mek", "ma", "me",
        // hâl ekleri
        "nden", "ndan", "inden", "indan", "unden", "undan", "nde", "nda", "inde", "inda", "unde", "unda",
        "dan", "den", "tan", "ten", "da", "de", "ta", "te",
        "yla", "yle", "la", "le", "nin", "nun", "in", "un", "na", "ne",
        "yi", "yu", "ya", "ye", "im", "um", "i", "u", "a", "e", "m"
    )

    // -----------------------------------------------------------------------
    // Normalleştirme
    // -----------------------------------------------------------------------

    /** Karakter başına dönüştürür; çıktı girdiyle aynı uzunluktadır (vurgu indeksleri için). */
    fun normalize(text: String): String {
        val out = CharArray(text.length)
        for (i in text.indices) out[i] = fold(text[i])
        return String(out)
    }

    private fun fold(c: Char): Char = when (c) {
        'I', 'İ', 'ı', 'i', 'Î', 'î', 'Ì', 'ì', 'Í', 'í' -> 'i'
        'Ç', 'ç' -> 'c'
        'Ğ', 'ğ' -> 'g'
        'Ö', 'ö', 'Ô', 'ô' -> 'o'
        'Ş', 'ş' -> 's'
        'Ü', 'ü', 'Û', 'û' -> 'u'
        'Â', 'â', 'À', 'à', 'Á', 'á' -> 'a'
        'É', 'é', 'È', 'è', 'Ê', 'ê' -> 'e'
        else -> c.lowercaseChar()
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit()

    /** Normalleştirilmiş metindeki kelimelerin [başlangıç, bitiş) aralıkları. Kesme işareti kelimeyi böler. */
    private fun words(normalized: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        for (i in normalized.indices) {
            if (isWordChar(normalized[i])) {
                if (start < 0) start = i
            } else if (start >= 0) {
                out += start until i
                start = -1
            }
        }
        if (start >= 0) out += start until normalized.length
        return out
    }

    // -----------------------------------------------------------------------
    // Sorgu
    // -----------------------------------------------------------------------

    /** Aranan bir kelime: kök + kullanıcının yazdığı tam hali. */
    data class Term(val stem: String, val full: String)

    /** Tek bir ek atılır; birden fazla tur kısa kelimeleri fazla kırpıyor ("ameliyatı" → "ameli"). */
    fun stem(word: String): String {
        val suffix = SUFFIXES.filter { word.endsWith(it) && word.length - it.length >= MIN_STEM }
            .maxByOrNull { it.length } ?: return word
        return word.dropLast(suffix.length)
    }

    fun parseQuery(query: String): List<Term> {
        // Kesme işaretinden sonrası ektir: "Ayşe'yle" → "ayşe".
        val normalized = normalize(query).split(WHITESPACE)
            .joinToString(" ") { it.substringBefore('\'').substringBefore('’') }
        val raw = words(normalized).map { normalized.substring(it) }
        val meaningful = raw.filterNot { it in STOP_WORDS }.ifEmpty { raw }
        return meaningful.distinct().map { Term(stem(it), it) }.distinctBy { it.stem }
    }

    // -----------------------------------------------------------------------
    // Arama
    // -----------------------------------------------------------------------

    private class Field(val original: String) {
        val normalized = normalize(original)
        val words = words(normalized)

        fun matches(term: Term): List<IntRange> = words.filter { range ->
            range.last - range.first + 1 >= term.stem.length &&
                normalized.startsWith(term.stem, range.first)
        }
    }

    private class Scored(
        val journal: JournalEntry,
        val matchedTerms: Int,
        val score: Int,
        val highlight: JournalSearchHighlight
    )

    /** [journals] diğer filtrelerden geçmiş anılardır; boş sorguda sıra korunur. */
    fun search(journals: List<JournalEntry>, query: String): JournalSearchResult {
        val terms = parseQuery(query)
        if (terms.isEmpty()) return JournalSearchResult(journals)

        val scored = journals.map { score(it, terms) }
        val all = scored.filter { it.matchedTerms == terms.size }
        val chosen = all.ifEmpty { if (terms.size > 1) scored.filter { it.matchedTerms > 0 } else emptyList() }
        val sorted = chosen.sortedWith(
            compareByDescending<Scored> { it.matchedTerms }
                .thenByDescending { it.score }
                .thenByDescending { it.journal.entryDate }
                .thenBy { it.journal.id }
        )
        return JournalSearchResult(
            journals = sorted.map { it.journal },
            highlights = sorted.associate { it.journal.id to it.highlight },
            isPartialMatch = all.isEmpty() && sorted.isNotEmpty()
        )
    }

    private fun score(journal: JournalEntry, terms: List<Term>): Scored {
        val title = Field(journal.title)
        val content = Field(journal.content)
        val people = journal.mentionedPeople.distinctBy { it.id }.map { it.name to Field(it.name) }

        var matched = 0
        var score = 0
        val titleRanges = mutableListOf<IntRange>()
        val contentRanges = mutableListOf<IntRange>()
        val matchedPeople = mutableListOf<String>()

        for (term in terms) {
            val inTitle = title.matches(term)
            val inContent = content.matches(term)
            val inPeople = people.filter { (_, field) -> field.matches(term).isNotEmpty() }.map { it.first }
            if (inTitle.isEmpty() && inContent.isEmpty() && inPeople.isEmpty()) continue
            matched++
            titleRanges += inTitle
            contentRanges += inContent
            matchedPeople += inPeople
            score += (if (inTitle.isNotEmpty()) 6 else 0) +
                (if (inPeople.isNotEmpty()) 5 else 0) +
                minOf(inContent.size, 3)
            // Kullanıcının yazdığı kelimenin aynısı geçiyorsa küçük bir öncelik.
            val exact = (inTitle.map { title.normalized.substring(it) } + inContent.map { content.normalized.substring(it) })
                .any { it == term.full }
            if (exact) score += 1
        }

        val (snippet, snippetRanges) = snippet(journal.content, contentRanges)
        return Scored(
            journal = journal,
            matchedTerms = matched,
            score = score,
            highlight = JournalSearchHighlight(
                titleRanges = titleRanges.sortedBy { it.first },
                snippet = snippet,
                snippetRanges = snippetRanges,
                matchedPeople = matchedPeople.distinct()
            )
        )
    }

    /** İlk eşleşmenin çevresinden kısa bir alıntı; vurgular alıntıya göre kaydırılır. */
    private fun snippet(content: String, ranges: List<IntRange>): Pair<String?, List<IntRange>> {
        if (ranges.isEmpty()) return null to emptyList()
        val first = ranges.minOf { it.first }
        var start = (first - SNIPPET_BEFORE).coerceAtLeast(0)
        // Kelime ortasından başlamamak için bir önceki boşluğa kadar ilerle.
        if (start > 0) {
            val space = content.indexOf(' ', start)
            if (space in start until first) start = space + 1
        }
        val end = (start + SNIPPET_LENGTH).coerceAtMost(content.length)
        val prefix = if (start > 0) "…" else ""
        val text = prefix + content.substring(start, end).replace('\n', ' ') + if (end < content.length) "…" else ""
        val shift = prefix.length - start
        val shifted = ranges.filter { it.first >= start && it.last < end }
            .sortedBy { it.first }
            .map { (it.first + shift)..(it.last + shift) }
        return text to shifted
    }
}
