package com.loc.benimdunyam.feature.ask

import com.loc.benimdunyam.data.ai.insight.MemoryInsight
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.journal.filter.JournalTextSearch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Modelin sorudan çıkardığı arama filtresi (sunucudaki soru_filtresi şeması). */
data class QuestionFilter(
    val personIds: List<String> = emptyList(),
    /** yyyy-MM-dd */
    val start: String? = null,
    val end: String? = null,
    val keywords: List<String> = emptyList(),
    val topics: List<String> = emptyList()
) {
    companion object {
        fun from(json: Map<String, Any?>): QuestionFilter {
            fun strings(key: String) = (json[key] as? List<*>).orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            val date = Regex("^\\d{4}-\\d{2}-\\d{2}$")
            return QuestionFilter(
                personIds = strings("kisiIdleri").distinct(),
                start = (json["baslangic"] as? String)?.takeIf { date.matches(it) },
                end = (json["bitis"] as? String)?.takeIf { date.matches(it) },
                keywords = strings("anahtarKelimeler").take(30),
                topics = strings("konular").take(10)
            )
        }
    }
}

/**
 * Sorunun cevabı için cihazdaki anılardan en ilgilileri seçer. Anılar dışarı gönderilmeden
 * önce burada daraltılır; modele yalnızca seçilenler gider.
 *
 * - Kişi ve tarih filtresi önce uygulanır; hiç anı kalmazsa önce tarih, sonra kişi gevşetilir.
 * - Anahtar kelimeler Türkçe eklere duyarlı biçimde başlıkta, metinde, kişilerde ve
 *   yapay zekâ analizinin konu/yer/olay/özetinde aranır.
 */
object MemoryRetriever {

    const val LIMIT = 12

    private fun dayStart(text: String, tz: TimeZone): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz; isLenient = false }.parse(text)?.time
    }.getOrNull()

    private fun dayEnd(text: String, tz: TimeZone): Long? = dayStart(text, tz)?.let {
        Calendar.getInstance(tz).apply { timeInMillis = it; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1
    }

    private fun words(text: String): List<String> =
        JournalTextSearch.normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun stems(filter: QuestionFilter): List<String> =
        (filter.keywords + filter.topics)
            .flatMap { words(it) }
            .map { JournalTextSearch.stem(it) }
            .filter { it.length >= 3 }
            .distinct()

    private fun count(words: List<String>, stem: String) = words.count { it.startsWith(stem) }

    fun retrieve(
        journals: List<JournalEntry>,
        filter: QuestionFilter,
        insights: Map<String, MemoryInsight> = emptyMap(),
        limit: Int = LIMIT,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<JournalEntry> {
        val start = filter.start?.let { dayStart(it, timeZone) }
        val end = filter.end?.let { dayEnd(it, timeZone) }
        val people = filter.personIds.toSet()

        fun byPeople(j: JournalEntry) = people.isEmpty() || j.mentionedPeople.any { it.id in people }
        fun byDate(j: JournalEntry) = (start == null || j.entryDate >= start) && (end == null || j.entryDate <= end)

        val usable = journals.filter { it.title.isNotBlank() || it.content.isNotBlank() }
        val pool = usable.filter { byPeople(it) && byDate(it) }.ifEmpty {
            usable.filter { byPeople(it) }.ifEmpty { usable.filter { byDate(it) } }
        }
        val narrowed = pool.size < usable.size
        val terms = stems(filter)

        if (terms.isEmpty()) {
            // Yalnızca kişi/tarih sorusu ("Ayşe ile ilk anımız"): filtre daralttıysa en eskiden yeniye.
            return if (narrowed) pool.sortedBy { it.entryDate }.take(limit) else emptyList()
        }

        val scored = pool.map { j ->
            val insight = insights[j.id]?.takeIf { it.isVisible }
            val title = words(j.title)
            val content = words(j.content)
            val names = j.mentionedPeople.flatMap { words(it.name) }
            val tags = insight?.let { words((it.topics + it.places + it.events).joinToString(" ")) }.orEmpty()
            val summary = insight?.summary?.let(::words).orEmpty()
            var score = 0
            var matched = 0
            for (t in terms) {
                val s = 3 * minOf(count(title, t), 1) + minOf(count(content, t), 3) +
                    2 * minOf(count(names, t), 1) + 3 * minOf(count(tags, t), 1) + minOf(count(summary, t), 1)
                if (s > 0) matched++
                score += s
            }
            if (people.isNotEmpty() && j.mentionedPeople.any { it.id in people }) score += 2
            // Birden fazla kelimeyi birlikte içeren anı önce gelsin.
            Triple(j, score + matched * 2, matched)
        }
        val relevant = scored.filter { it.third > 0 }
        val chosen = relevant.ifEmpty { if (narrowed) scored else emptyList() }
        return chosen
            .sortedWith(compareByDescending<Triple<JournalEntry, Int, Int>> { it.second }.thenByDescending { it.first.entryDate })
            .take(limit)
            .map { it.first }
    }
}

/** Cevaptaki [a3] işaretlerini görünme sırasına göre [1], [2]… yapar ve kaynak listesini çıkarır. */
object CitationFormatter {
    data class Formatted(val text: String, val journalIds: List<String>)

    private val marker = Regex("\\[(a\\d{1,3})]")

    fun format(text: String, sources: Map<String, String>): Formatted {
        val order = mutableListOf<String>()
        val formatted = marker.replace(text) { m ->
            val id = sources[m.groupValues[1]] ?: return@replace ""
            if (id !in order) order += id
            "[${order.indexOf(id) + 1}]"
        }
        // Aynı kaynağın art arda tekrarını ("[1][1]") sadeleştir; işaret silinince kalan boşlukları toparla.
        val cleaned = formatted.replace(Regex("(\\[\\d+])(\\1)+"), "$1").replace(Regex(" +([.,;:!?])"), "$1").trim()
        return Formatted(cleaned, order)
    }
}
