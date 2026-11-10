package com.loc.benimdunyam.feature.yearreview.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max

private val Green = Color(0xFF395C4A)
private val Ink = Color(0xFF302A24)
private val Faint = Color(0xFF8B7C6D)
private val Soft = Color(0xFFE5DBCF)
private val MoodColors = listOf(
    Color(0xFF547C67), Color(0xFFCE9B58), Color(0xFF7E91B4),
    Color(0xFFB77E83), Color(0xFF8A7DB1), Color(0xFFCB8A5D),
    Color(0xFF71A6A0), Color(0xFFA1A168)
)
private val MonthAbbreviations = listOf(
    "Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"
)

private fun moodLabel(key: String): String = when (key.uppercase(Locale.ROOT)) {
    "VERY_GOOD", "COK_IYI", "COKIYI", "VERYGOOD" -> "Çok İyi"
    "HAPPY", "MUTLU" -> "Mutlu"
    "PEACEFUL", "CALM", "HUZURLU" -> "Huzurlu"
    "ENERGETIC", "ENERJIK", "ENERGIZED" -> "Enerjik"
    "TIRED", "YORGUN" -> "Yorgun"
    "TENSE", "STRESSED", "NERVOUS", "GERGIN" -> "Gergin"
    "SAD", "UZGUN" -> "Üzgün"
    "HURT", "BROKEN", "OFFENDED", "KIRGIN" -> "Kırgın"
    else -> key.replace('_', ' ').lowercase(Locale.forLanguageTag("tr-TR"))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) }
}

/** Kullanıcının etiketlediği ruh hallerinden oluşan yıllık özet; tıbbi yorum üretmez. */
@Composable
fun YearMoodMapSection(
    report: YearMoodReport,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4EDE2))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("SAHNE 06 • DUYGULARIM", color = Green, fontSize = 10.sp,
                letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            Text("Yılın Duygu Haritası", fontFamily = FontFamily.Serif,
                fontSize = 25.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("Her renk, bir anı yazarken seçtiğin duyguyu temsil ediyor.",
                color = Faint, fontSize = 12.sp)

            if (report.journalCount == 0) {
                Text("Bu yıl için henüz bir anı bulunmuyor.", color = Faint)
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                MoodMetric(report.taggedJournalCount.toString(), "DUYGULU ANI")
                MoodMetric(report.untaggedJournalCount.toString(), "SEÇİLMEDİ")
                MoodMetric(report.moods.size.toString(), "DUYGU TÜRÜ")
            }

            val coverage = report.taggedJournalCount / report.journalCount.toFloat()
            Box(
                Modifier.fillMaxWidth().height(7.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Soft)
            ) {
                Box(Modifier.fillMaxWidth(coverage).height(7.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Green))
            }
            Text("${(coverage * 100f).toInt()}% anıda ruh hali seçilmiş",
                fontSize = 12.sp, color = Faint)

            if (report.taggedJournalCount == 0) {
                HorizontalDivider(color = Soft)
                Text("Bu yıla ait anılarda henüz ruh hali seçilmemiş. Anı düzenleme ekranından bir duygu seçtiğinde harita oluşacak.",
                    color = Ink, fontSize = 13.sp)
                return@Column
            }

            report.dominantMood?.let { dominant ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("En çok işaretlenen duygu", color = Faint, fontSize = 12.sp)
                    Text("${moodLabel(dominant.name)} · ${dominant.total} anı",
                        fontFamily = FontFamily.Serif, color = Green,
                        fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider(color = Soft)
            Text("AYLARA GÖRE DUYGULAR", fontSize = 11.sp, color = Green,
                letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)

            val peak = max(1, report.monthlyJournalCounts.maxOrNull() ?: 0)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (monthIndex in 0..11) {
                    val monthTotal = report.monthlyJournalCounts[monthIndex]
                    val barHeight = if (monthTotal == 0) 5.dp
                    else (max(7f, monthTotal.toFloat() / peak.toFloat() * 100f)).dp
                    Column(
                        modifier = Modifier.width(30.dp)
                            .semantics {
                                contentDescription = "${MonthAbbreviations[monthIndex]}: $monthTotal anı, " +
                                        "${report.monthlyTaggedCounts[monthIndex]} ruh hali seçilmiş"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.height(104.dp).width(23.dp), contentAlignment = Alignment.BottomCenter) {
                            if (monthTotal == 0) {
                                Box(Modifier.width(19.dp).height(barHeight).background(Soft))
                            } else {
                                Column(
                                    modifier = Modifier.width(19.dp).height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                ) {
                                    report.moods.forEachIndexed { colorIndex, mood ->
                                        val count = mood.months[monthIndex]
                                        if (count > 0) {
                                            Box(Modifier.fillMaxWidth().weight(count.toFloat())
                                                .background(MoodColors[colorIndex % MoodColors.size]))
                                        }
                                    }
                                    val untagged = monthTotal - report.monthlyTaggedCounts[monthIndex]
                                    if (untagged > 0) {
                                        Box(Modifier.fillMaxWidth().weight(untagged.toFloat())
                                            .background(Soft))
                                    }
                                }
                            }
                        }
                        Text(MonthAbbreviations[monthIndex], fontSize = 10.sp,
                            color = Faint, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                report.moods.forEachIndexed { index, mood ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(11.dp).clip(CircleShape)
                            .background(MoodColors[index % MoodColors.size]))
                        Text(moodLabel(mood.name), modifier = Modifier.weight(1f),
                            color = Ink, fontSize = 13.sp)
                        Text("${mood.total} anı", color = Faint, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                if (report.untaggedJournalCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(11.dp).clip(CircleShape).background(Soft))
                        Text("Duygu seçilmedi", modifier = Modifier.weight(1f), color = Ink, fontSize = 13.sp)
                        Text("${report.untaggedJournalCount} anı", color = Faint, fontSize = 13.sp)
                    }
                }
            }
            HorizontalDivider(color = Soft)
            Text("Bu harita yalnızca kaydettiğin etiketleri sayar; duygularına dair tanı veya çıkarım yapmaz.",
                color = Faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MoodMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Green, fontSize = 23.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        Text(label, color = Faint, fontSize = 9.sp, letterSpacing = 0.4.sp)
    }
}
