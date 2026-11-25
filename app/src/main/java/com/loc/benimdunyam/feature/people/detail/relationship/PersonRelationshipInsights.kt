package com.loc.benimdunyam.feature.people.detail.relationship

import com.loc.benimdunyam.domain.model.JournalMood
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Hesaplayıcıya verilen tek anı; Android/Room bağımlılığı yoktur. */
data class RelationshipMemory(
    val journalId: String,
    val title: String,
    val content: String,
    val entryDate: Long
)

/** İki anı arasındaki en uzun boşluk. */
data class QuietPeriod(val fromMillis: Long, val toMillis: Long, val months: Int)

/** Bu kişiyle en belirgin ruh hali ve genel ortalamayla karşılaştırması. */
data class MoodComparison(
    val mood: JournalMood,
    /** Bu kişinin ruh hali seçilmiş anılarındaki yüzde. */
    val personPercent: Int,
    /** Tüm ruh hali seçilmiş anılardaki yüzde. */
    val overallPercent: Int
)

data class LongestMemory(val journalId: String, val title: String, val words: Int)

data class PersonRelationshipInsights(
    val memoryCount: Int = 0,
    val firstMemoryAt: Long? = null,
    val lastMemoryAt: Long? = null,
    /** İlk anıdan bugüne geçen tam yıl. */
    val yearsTogether: Int = 0,
    val daysSinceLastMemory: Long? = null,
    /** Bugün ilk anının yıl dönümüyse kaçıncı yıl. */
    val anniversaryYearToday: Int? = null,
    val longestQuietPeriod: QuietPeriod? = null,
    val moodComparison: MoodComparison? = null,
    /** Hafta sonuna denk gelen anıların yüzdesi (yeterli veri varsa). */
    val weekendPercent: Int? = null,
    /** En çok anının olduğu mevsim (belirgin baskınsa). */
    val dominantSeasonOfYear: String? = null,
    val totalWords: Int = 0,
    val longestMemory: LongestMemory? = null,
    /** Kullanıcıya gösterilen kısa hikâye paragrafı. */
    val summary: String = ""
) {
    val isEmpty: Boolean get() = memoryCount == 0
}

/**
 * Kişi detayının "İçgörüler" sekmesi için yeni içgörüler.
 *
 * İlke: kayıtlı anıları anlatır, ilişkiyi PUANLAMAZ. Az veri varken grafik ya da
 * genelleme yerine sade cümleler kurar. Saf Kotlin; zaman ve saat dilimi
 * parametre olarak alınır, böylece birim testleri tekrarlanabilir.
 */
object PersonRelationshipInsightsCalculator {

    private val DAY = TimeUnit.DAYS.toMillis(1)
    /** Bundan kısa boşluklar "sessiz dönem" sayılmaz. */
    private val MIN_QUIET_DAYS = 90L
    private const val MIN_MOOD_SAMPLES = 3
    private const val MIN_HABIT_SAMPLES = 4

    fun calculate(
        personName: String,
        memories: List<RelationshipMemory>,
        /** Tüm anıların ruh halleri (journalId -> JournalMood.name); genel ortalama için. */
        moodByJournalId: Map<String, String>,
        topSeasonTitle: String?,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PersonRelationshipInsights {
        val sorted = memories.distinctBy { it.journalId }.sortedBy { it.entryDate }
        if (sorted.isEmpty()) {
            return PersonRelationshipInsights(
                summary = "$personName ile henüz kayıtlı bir anın yok. Bir günlükte etiketlediğinde hikâyeniz burada başlayacak."
            )
        }
        val first = sorted.first().entryDate
        val last = sorted.last().entryDate
        val years = fullYearsBetween(first, now, timeZone)

        val words = sorted.associate { it.journalId to wordCount(it.content) }
        val longest = sorted.maxWithOrNull(compareBy<RelationshipMemory> { words.getValue(it.journalId) }
            .thenBy { it.entryDate })
            ?.takeIf { words.getValue(it.journalId) > 0 }
            ?.let { LongestMemory(it.journalId, it.title, words.getValue(it.journalId)) }

        val insights = PersonRelationshipInsights(
            memoryCount = sorted.size,
            firstMemoryAt = first,
            lastMemoryAt = last,
            yearsTogether = years,
            daysSinceLastMemory = ((now - last).coerceAtLeast(0)) / DAY,
            anniversaryYearToday = anniversaryYear(first, now, timeZone),
            longestQuietPeriod = longestQuietPeriod(sorted),
            moodComparison = moodComparison(sorted.map { it.journalId }, moodByJournalId),
            weekendPercent = weekendPercent(sorted, timeZone),
            dominantSeasonOfYear = dominantSeasonOfYear(sorted, timeZone),
            totalWords = words.values.sum(),
            longestMemory = longest
        )
        return insights.copy(summary = summary(personName, insights, topSeasonTitle, timeZone))
    }

    // -----------------------------------------------------------------------

    internal fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    private fun calendar(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone, Locale.ROOT).apply { timeInMillis = millis }

    private fun fullYearsBetween(from: Long, to: Long, timeZone: TimeZone): Int {
        if (to <= from) return 0
        val a = calendar(from, timeZone)
        val b = calendar(to, timeZone)
        var years = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
        val beforeAnniversary = b.get(Calendar.MONTH) < a.get(Calendar.MONTH) ||
                (b.get(Calendar.MONTH) == a.get(Calendar.MONTH) &&
                        b.get(Calendar.DAY_OF_MONTH) < a.get(Calendar.DAY_OF_MONTH))
        if (beforeAnniversary) years--
        return years.coerceAtLeast(0)
    }

    private fun anniversaryYear(first: Long, now: Long, timeZone: TimeZone): Int? {
        val a = calendar(first, timeZone)
        val b = calendar(now, timeZone)
        val years = b.get(Calendar.YEAR) - a.get(Calendar.YEAR)
        return years.takeIf {
            it >= 1 && a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
                    a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)
        }
    }

    private fun longestQuietPeriod(sorted: List<RelationshipMemory>): QuietPeriod? =
        sorted.zipWithNext()
            .map { (a, b) -> a.entryDate to b.entryDate }
            .maxByOrNull { (a, b) -> b - a }
            ?.takeIf { (a, b) -> (b - a) / DAY >= MIN_QUIET_DAYS }
            ?.let { (a, b) -> QuietPeriod(a, b, ((b - a) / DAY / 30).toInt()) }

    private fun moodComparison(journalIds: List<String>, moods: Map<String, String>): MoodComparison? {
        fun parse(name: String?) = JournalMood.entries.firstOrNull { it.name == name }
        val personMoods = journalIds.mapNotNull { parse(moods[it]) }
        if (personMoods.size < MIN_MOOD_SAMPLES) return null
        val allMoods = moods.values.mapNotNull { parse(it) }
        if (allMoods.isEmpty()) return null
        val top = personMoods.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<JournalMood, Int>> { it.value }
                .thenBy { it.key.ordinal })
            .first()
        val personPercent = top.value * 100 / personMoods.size
        val overallPercent = allMoods.count { it == top.key } * 100 / allMoods.size
        return MoodComparison(top.key, personPercent, overallPercent)
    }

    private fun weekendPercent(sorted: List<RelationshipMemory>, timeZone: TimeZone): Int? {
        if (sorted.size < MIN_HABIT_SAMPLES) return null
        val weekend = sorted.count {
            val day = calendar(it.entryDate, timeZone).get(Calendar.DAY_OF_WEEK)
            day == Calendar.SATURDAY || day == Calendar.SUNDAY
        }
        return weekend * 100 / sorted.size
    }

    private fun seasonOf(month: Int): String = when (month) {
        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "kış"
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "ilkbahar"
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "yaz"
        else -> "sonbahar"
    }

    private fun dominantSeasonOfYear(sorted: List<RelationshipMemory>, timeZone: TimeZone): String? {
        if (sorted.size < MIN_HABIT_SAMPLES) return null
        val counts = sorted.groupingBy { seasonOf(calendar(it.entryDate, timeZone).get(Calendar.MONTH)) }
            .eachCount()
        val top = counts.maxByOrNull { it.value } ?: return null
        // Yalnızca belirgin bir baskınlık varsa söyleriz (eşit dağılımda genelleme yok).
        val runnerUp = counts.filterKeys { it != top.key }.values.maxOrNull() ?: 0
        return top.key.takeIf { top.value * 100 / sorted.size >= 40 && top.value > runnerUp }
    }

    private fun summary(
        name: String,
        insights: PersonRelationshipInsights,
        topSeasonTitle: String?,
        timeZone: TimeZone
    ): String {
        if (insights.memoryCount < 2) {
            return "$name ile ilk anın kayıtlı. Hikâyeniz yeni başlıyor."
        }
        val firstYear = calendar(requireNotNull(insights.firstMemoryAt), timeZone).get(Calendar.YEAR)
        val parts = mutableListOf("$name ile $firstYear yılından beri ${insights.memoryCount} anın var.")
        topSeasonTitle?.let { parts += "En yoğun dönemin: $it." }
        insights.dominantSeasonOfYear?.let { parts += "Anılarınız en çok ${it} aylarında." }
        insights.moodComparison?.let { parts += "Bu anıların çoğunda ${it.mood.label.lowercase(TR)} hissetmişsin." }
        return parts.joinToString(" ")
    }

    private val TR = Locale.forLanguageTag("tr-TR")
}
