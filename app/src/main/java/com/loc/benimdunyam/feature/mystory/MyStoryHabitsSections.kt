package com.loc.benimdunyam.feature.mystory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale.forLanguageTag("tr-TR")

/** Seri + son 26 haftanın yazma takvimi. */
@Composable
internal fun WritingRhythmCard(report: MyStoryReport) {
    val streak = report.streak
    val calendar = report.writingCalendar
    StoryCard(
        "✍️ Yazma ritmin",
        "Anılarını yazdığın günler (son ${MyStoryHabitsCalculator.CALENDAR_WEEKS} hafta)."
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StreakBox(
                modifier = Modifier.weight(1f),
                value = streak.current,
                label = "günlük seri",
                hint = when {
                    streak.current == 0 -> "Bugün yazarak yeni bir seri başlatabilirsin."
                    streak.writtenToday -> "Bugün de yazdın 🌿"
                    else -> "Bugün yazarsan seri sürer."
                }
            )
            StreakBox(
                modifier = Modifier.weight(1f),
                value = streak.longest,
                label = "en uzun seri",
                hint = when {
                    streak.longest <= 1 || streak.longestEndMillis == null -> ""
                    streak.current == streak.longest -> "Şu an en uzun serindesin"
                    else -> "${dayMonthYear(streak.longestEndMillis)} tarihinde bitti"
                }
            )
        }
        Heatmap(calendar)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "${calendar.activeDays} gün yazdın",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text("Az ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0, 1, 2, 3).forEach { level ->
                Box(
                    Modifier.padding(horizontal = 1.dp).width(10.dp).height(10.dp)
                        .clip(RoundedCornerShape(2.dp)).background(levelColor(level))
                )
            }
            Text(" Çok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StreakBox(modifier: Modifier, value: Int, label: String, hint: String) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        Text("🔥 $value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (hint.isNotBlank()) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun levelColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.primary
}

private fun level(count: Int, max: Int): Int = when {
    count <= 0 -> 0
    max <= 1 -> 3
    else -> (1 + (count - 1) * 3 / max).coerceIn(1, 3)
}

/** Sütunlar haftalar (eskiden yeniye), satırlar Pazartesi → Pazar. */
@Composable
private fun Heatmap(calendar: WritingCalendar) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        calendar.weeks.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { day ->
                    val description = if (day.count < 0) "" else "${dayMonthYear(day.dayStartMillis)}: ${day.count} anı"
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (day.count < 0) Color.Transparent else levelColor(level(day.count, calendar.maxCount)))
                            .semantics { contentDescription = description }
                    )
                }
            }
        }
    }
}

/** Hangi gün ve günün hangi saatinde yazıldığı. */
@Composable
internal fun WritingHabitsCard(report: MyStoryReport) {
    val habits = report.habits
    if (!habits.hasEnoughData) return
    val summary = buildList {
        habits.topWeekday?.let { add("en çok ${MyStoryHabitsCalculator.WEEKDAY_LABELS[it].lowercase(TR)} günleri") }
        habits.topDayPart?.let { add("çoğunlukla ${it.label.lowercase(TR)}") }
    }.joinToString(", ")
    StoryCard(
        "🗓 Ne zaman yazıyorsun",
        if (summary.isBlank()) "Bu dönemdeki ${habits.sampleSize} anına göre."
        else "Bu dönemde ${summary} yazmışsın."
    ) {
        val max = (habits.weekdayCounts.maxOrNull() ?: 0).coerceAtLeast(1)
        val short = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            habits.weekdayCounts.forEachIndexed { index, count ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (count > 0) Text("$count", style = MaterialTheme.typography.labelSmall)
                    Box(
                        Modifier.fillMaxWidth()
                            .height((56f * count / max).dp.coerceAtLeast(if (count > 0) 4.dp else 1.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (index == habits.topWeekday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            )
                    )
                    Text(short[index], style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayPart.entries.forEach { part ->
                val count = habits.dayPartCounts[part] ?: 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(
                            if (part == habits.topDayPart) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(part.emoji)
                    Text("%${count * 100 / habits.sampleSize}", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(part.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** Ay ay baskın ruh hali şeridi. */
@Composable
internal fun MoodTrendCard(report: MyStoryReport) {
    val months = report.moodTrend
    if (months.isEmpty()) return
    StoryCard("🌤 Ruh halinin yolculuğu", "Son 12 ayda her ayın en sık ruh hali.") {
        Row(Modifier.fillMaxWidth()) {
            months.forEach { month ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            month.taggedCount == 0 -> "·"
                            month.dominant == null -> "🔀"
                            else -> month.dominant.emoji
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(month.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "🔀 birden fazla ruh hali eşit sıklıktaydı · boş aylarda ruh hali seçilmemiş.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun dayMonthYear(millis: Long): String = SimpleDateFormat("d MMMM yyyy", TR).format(Date(millis))
