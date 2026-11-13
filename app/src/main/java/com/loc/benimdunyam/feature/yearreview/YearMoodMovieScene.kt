package com.loc.benimdunyam.feature.yearreview

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodReport
import java.util.Locale
import kotlin.math.max

private val Gold get() = themedColor(Color(0xFFE7C68A))
private val White get() = themedColor(Color(0xFFFAF5E9))
private val Pale get() = themedColor(Color(0xFFBDD3C6))
private val Muted get() = themedColor(Color(0xFF7E998D))
private val GraphColors = listOf(
    themedColor(Color(0xFF547C67)), themedColor(Color(0xFFCE9B58)), themedColor(Color(0xFF7E91B4)),
    themedColor(Color(0xFFB77E83)), themedColor(Color(0xFF8A7DB1)), themedColor(Color(0xFFCB8A5D)),
    themedColor(Color(0xFF71A6A0)), themedColor(Color(0xFFA1A168))
)
private val Months = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")

/**
 * Yalnızca kullanıcının kendi seçtiği mood etiketlerinin görselleştirilmesi.
 * Duygu değişimi, psikolojik çıkarım veya olumlu/olumsuz yargı yapılmaz.
 * Bu ekran yalnızca uygulama içi filmde görünür; MP4 dışa aktarımı bu veriyi almaz.
 */
@Composable
internal fun YearMoodMovieScene(report: YearMoodReport, sceneProgress: Float, seasonTitle: String? = null) {
    val reducedMotion = LocalMovieReducedMotion.current
    val progress = if (reducedMotion) 1f else (sceneProgress * 2.7f).coerceIn(0.02f, 1f)
    val peak = max(1, report.monthlyTaggedCounts.maxOrNull() ?: 0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        MovieReveal(delayMillis = 40) {
            Text(if (seasonTitle == null) "BİR YIL • BİRÇOK HİS" else "BİR DÖNEM • BİRÇOK HİS", color = Gold, fontWeight = FontWeight.Bold,
                fontSize = 10.sp, letterSpacing = 2.3.sp)
        }
        MovieReveal(delayMillis = 160) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duygu Yolculuğum", color = White, fontFamily = FontFamily.Serif,
                    fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.SemiBold)
                Text(if (seasonTitle == null) "${report.year} boyunca anılarına bıraktığın duygu izleri."
                else "$seasonTitle döneminde anılarına bıraktığın duygu izleri.",
                    color = Pale, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        MovieReveal(delayMillis = 260) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoodFilmMetric(report.taggedJournalCount.toString(), "Duygu seçilen", Modifier.weight(1f))
                MoodFilmMetric(report.untaggedJournalCount.toString(), "Seçilmeyen", Modifier.weight(1f))
                MoodFilmMetric(report.moods.size.toString(), "Farklı duygu", Modifier.weight(1f))
            }
        }

        report.dominantMood?.let { dominant ->
            MovieReveal(delayMillis = 410) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(themedColor(Color(0xFF2B5044)))
                        .padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("EN ÇOK SEÇİLEN DUYGU", color = Pale, fontSize = 10.sp,
                        letterSpacing = 1.3.sp)
                    Text(moodMovieLabel(dominant.name), color = Gold,
                        fontSize = 27.sp, fontFamily = FontFamily.Serif)
                    Text("${dominant.total} ayrı anıda işaretlendi", color = White, fontSize = 12.sp)
                }
            }
        }

        MovieReveal(delayMillis = 550) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("12 AYIN DUYGU İZLERİ", color = Gold, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                // Burada y ekseni duygu şiddetini değil, etiketlenen anı sayısını temsil eder.
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    repeat(12) { month ->
                        val count = report.monthlyTaggedCounts[month]
                        val scaledRatio = count.toFloat() / peak
                        Column(
                            modifier = Modifier.width(27.dp).semantics {
                                contentDescription = "${Months[month]}: $count duygu etiketli anı, " +
                                        "${report.monthlyJournalCounts[month]} toplam anı"
                            },
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.width(19.dp).height(92.dp), contentAlignment = Alignment.BottomCenter) {
                                Canvas(Modifier.width(19.dp).height(92.dp)) {
                                    if (count == 0) {
                                        drawRect(Muted.copy(alpha = 0.4f),
                                            topLeft = Offset(0f, size.height - 3.dp.toPx()),
                                            size = Size(size.width, 3.dp.toPx()))
                                    } else {
                                        val barHeight = size.height * scaledRatio * progress
                                        var y = size.height
                                        report.moods.forEachIndexed { colorIndex, mood ->
                                            val n = mood.months[month]
                                            if (n > 0) {
                                                val segment = barHeight * n.toFloat() / count
                                                y -= segment
                                                drawRect(
                                                    color = GraphColors[colorIndex % GraphColors.size],
                                                    topLeft = Offset(0f, y),
                                                    size = Size(size.width, segment + 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(Months[month], color = Pale, fontSize = 10.sp)
                        }
                    }
                }
                Text("Sütunlar duygu yoğunluğunu değil, ruh hali seçilmiş anı sayısını gösterir.",
                    color = Pale.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        MovieReveal(delayMillis = 660) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                report.moods.take(4).forEachIndexed { index, mood ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape)
                            .background(GraphColors[index % GraphColors.size]))
                        Text(moodMovieLabel(mood.name), modifier = Modifier.weight(1f),
                            color = White, fontSize = 13.sp)
                        Text("${mood.total} anı", color = Pale, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Bu sahne yalnızca senin kaydettiğin duyguları gösterir; kişilik veya ruh sağlığı yorumu yapmaz.",
            color = Pale.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun MoodFilmMetric(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(13.dp))
            .background(themedColor(Color(0xFF254539))).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(value, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Pale, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

private fun moodMovieLabel(key: String): String = when (key.uppercase(Locale.ROOT)) {
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
