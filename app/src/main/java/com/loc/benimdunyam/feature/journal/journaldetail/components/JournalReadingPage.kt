package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loc.benimdunyam.domain.model.Person

/** Anının metni önceliklidir; kişi bağlantıları yalnızca bu anıya eklenmiş kişiler için oluşturulur. */
@Composable
fun JournalReadingPage(
    title: String,
    content: String,
    day: String,
    monthShort: String,
    fullDate: String,
    time: String,
    mentionedPeople: List<Person>,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    moodContent: @Composable () -> Unit = {}
) {
    val ink = Color(0xFF362D26)
    val secondaryInk = Color(0xFF796E62)
    val accent = Color(0xFF476C58)
    val scrollState = rememberScrollState()
    val displayedContent = content.ifBlank { "Bu anıya henüz bir not eklenmemiş." }

    // Gerçek metnin içinde geçen @isim veya isim ifadelerine profil bağlantısı eklenir.
    // Kişi adı metinde yoksa ayrıca bir etiket/kart gösterilmez.
    val linkedContent = remember(displayedContent, mentionedPeople, onPersonClick) {
        buildAnnotatedString {
            append(displayedContent)
            findPersonMentions(displayedContent, mentionedPeople).forEach { mention ->
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = mention.personId,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = LinkInteractionListener {
                            onPersonClick(mention.personId)
                        }
                    ),
                    start = mention.start,
                    end = mention.end
                )
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 21.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.width(49.dp).height(51.dp),
                shape = RoundedCornerShape(11.dp),
                color = Color(0xFFF0E6D6),
                border = BorderStroke(1.dp, Color(0xFFDED2BE))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day,
                        fontFamily = FontFamily.Serif,
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        color = ink
                    )
                    Text(
                        text = monthShort.uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 0.9.sp,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "BİR GÜN, BİR HİKÂYE",
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 0.7.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$fullDate · $time",
                    color = secondaryInk,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            // Ruh hali artık yazının altında geniş kart değil, tarih bilgilerinin sağındadır.
            moodContent()
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = title.ifBlank { "Başlıksız Anı" },
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.Normal,
            color = ink
        )

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(13.dp))

        SelectionContainer {
            Text(
                text = linkedContent,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 110.dp),
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Normal,
                color = ink
            )
        }

        // Fotoğraf önizlemesi bu kaydırılabilir metin alanının dışında,
        // JournalDetailScreen alt kısmında gösterilir.
        Spacer(Modifier.height(10.dp))
    }
}

/** Metinde olmayan veya anıyla ilişkilendirilmemiş kişilere bağlantı oluşturmaz. */
internal data class JournalPersonMentionRange(
    val personId: String,
    val start: Int,
    val end: Int
)

internal fun findPersonMentions(
    content: String,
    mentionedPeople: List<Person>
): List<JournalPersonMentionRange> {
    // Uzun isimler önce işlendiği için örneğin "Ali Can", "Ali" tarafından bölünmez.
    val matches = mentionedPeople
        .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        .distinctBy { it.id }
        .sortedByDescending { it.name.length }
        .flatMap { person ->
            // Sadece tam kişi adlarını tanı: "Can", "Cansu" kelimesinin parçası değildir.
            // @işaretli eski ve yeni anı içerikleri, boşluk içeren isimler desteklenir.
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}_@])@?${Regex.escape(person.name.trim())}(?![\\p{L}\\p{N}_])",
                RegexOption.IGNORE_CASE
            )
            pattern.findAll(content).map { match ->
                JournalPersonMentionRange(person.id, match.range.first, match.range.last + 1)
            }.toList()
        }

    val accepted = mutableListOf<JournalPersonMentionRange>()
    // Önce uzun eşleşmeler, sonra metindeki konum. Çakışan bağlantıları ekleme.
    matches.sortedWith(
        compareByDescending<JournalPersonMentionRange> { it.end - it.start }
            .thenBy { it.start }
    ).forEach { candidate ->
        if (accepted.none { candidate.start < it.end && it.start < candidate.end }) {
            accepted.add(candidate)
        }
    }
    return accepted.sortedBy { it.start }
}
