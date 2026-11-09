package com.loc.benimdunyam.feature.home.onthisday

import com.loc.benimdunyam.domain.model.JournalEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Geçmiş yılların BUGÜN ile aynı ay/gün tarihli anıları. Ağ veya yapay zekâ kullanmaz. */
data class OnThisDayMemory(
    val journal: JournalEntry,
    val yearsAgo: Int,
    val year: Int
)

data class OnThisDayReport(
    val todayLabel: String,
    val memories: List<OnThisDayMemory>
)

object OnThisDayCalculator {
    private val turkishLocale = Locale.forLanguageTag("tr-TR")

    /**
     * entryDate, günlüğün kullanıcı tarafından seçilen tarihidir; createdAt değildir.
     * Karşılaştırmalar cihazın yerel saat diliminde yapılır.
     * 29 Şubat anıları, artık yıl olmayan tarihlerde 28 Şubat / 1 Mart'a taşınmaz.
     */
    fun calculate(
        journals: List<JournalEntry>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): OnThisDayReport {
        val today = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val todayYear = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val formatter = SimpleDateFormat("d MMMM", turkishLocale).apply {
            this.timeZone = timeZone
        }

        val matching = journals.asSequence()
            .filter { it.entryDate > 0L && it.entryDate < nowMillis }
            .mapNotNull { journal ->
                val calendar = Calendar.getInstance(timeZone).apply {
                    timeInMillis = journal.entryDate
                }
                val year = calendar.get(Calendar.YEAR)
                if (
                    year < todayYear &&
                    calendar.get(Calendar.MONTH) == todayMonth &&
                    calendar.get(Calendar.DAY_OF_MONTH) == todayDay
                ) {
                    OnThisDayMemory(
                        journal = journal,
                        yearsAgo = todayYear - year,
                        year = year
                    )
                } else null
            }
            .distinctBy { it.journal.id }
            .sortedWith(
                compareByDescending<OnThisDayMemory> { it.journal.entryDate }
                    .thenBy { it.journal.id }
            )
            .toList()

        return OnThisDayReport(
            todayLabel = formatter.format(Date(nowMillis)),
            memories = matching
        )
    }
}
