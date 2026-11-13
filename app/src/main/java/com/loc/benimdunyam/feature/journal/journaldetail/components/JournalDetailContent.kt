package com.loc.benimdunyam.feature.journal.journaldetail.components

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class JournalDetailPersonUi(
    val id: String,
    val name: String
)

@Composable
internal fun JournalDetailContent(
    title: String,
    content: String,
    day: String,
    monthYear: String,
    time: String,
    fullDate: String,
    mentionedPeople: List<JournalDetailPersonUi>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            JournalDetailDatePolaroid(
                day = day,
                monthYear = monthYear,
                time = time
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailTapeLabel(text = "ANI DEFTERİ", rotation = 1.4f)
                Text(
                    text = "Bir gün dönüp baktığında, bu an yine burada olacak.",
                    color = JournalDetailColors.MutedInk,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.Cursive
                )
                Text(
                    text = fullDate,
                    color = JournalDetailColors.Ink.copy(alpha = 0.76f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = title.ifBlank { "Başlıksız anı" },
            color = JournalDetailColors.Ink,
            fontSize = 29.sp,
            lineHeight = 37.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal
        )

        ScrapbookDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .width(2.dp)
                    .height(110.dp)
            ) {
                drawLine(
                    color = themedColor(Color(0xFFB99D78)).copy(alpha = 0.55f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            Text(
                text = content.ifBlank { "Bu anıya henüz bir not eklenmemiş." },
                modifier = Modifier.weight(1f),
                color = JournalDetailColors.Ink,
                fontSize = 18.sp,
                lineHeight = 30.sp,
                fontFamily = FontFamily.Serif
            )
        }

        if (mentionedPeople.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            ScrapbookDivider()

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✦",
                        color = JournalDetailColors.Green,
                        fontSize = 19.sp
                    )
                    Text(
                        text = "Bu Anıda Yer Alanlar",
                        color = JournalDetailColors.Ink,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mentionedPeople, key = { it.id }) { person ->
                        PersonPaperTag(name = person.name)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Küçük bir anı, koca bir hikâyenin parçası.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = JournalDetailColors.MutedInk.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontFamily = FontFamily.Cursive,
            textAlign = TextAlign.Center
        )
    }
}
