package com.loc.benimdunyam.feature.journal

import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.scenicDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.loc.benimdunyam.feature.journal.cover.JournalArchiveCoverViewModel
import java.io.File
import com.loc.benimdunyam.feature.journal.components.SwipeToDeleteJournalItem
import com.loc.benimdunyam.feature.journal.components.JournalArchiveFiltersSheet
import com.loc.benimdunyam.feature.journal.components.JournalDogActionFab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Günlükler arşivi ekranının kart ve boş durum bileşenleri (JournalsScreen.kt). */

// ---------------------------------------------------------
// GÜNLÜK KARTI
// ---------------------------------------------------------

@Composable
internal fun JournalArchiveCard(
    index: Int,
    date: String,
    title: String,
    content: String,
    seasonTitle: String?,
    episodeTitle: String?,
    coverPath: String?,
    onClick: () -> Unit
) {

    Surface(

        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),

        shape = RoundedCornerShape(22.dp),

        color = JournalPaper,

        border = BorderStroke(
            width = 1.dp,
            color = JournalBorder
        ),

        shadowElevation = 1.dp
    ) {

        Row(
            modifier = Modifier.padding(16.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            if (coverPath != null) {
                // Seçilen kapak görseli: dosya cihaz içindeki JournalPhotoStore'a aittir.
                Box(
                    modifier = Modifier
                        .width(94.dp)
                        .height(114.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(JournalTape)
                ) {
                    Text(
                        text = "✦",
                        color = JournalMuted,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    AsyncImage(
                        model = remember(coverPath) { File(coverPath) },
                        contentDescription = "Anının kapak fotoğrafı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                )
                            )
                    )
                    Text(
                        text = "✦ KAPAK",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.6.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 7.dp)
                    )
                }
            } else {
                // Fotoğrafı olmayan anıların eski defter şeridi korunur.
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(86.dp)
                        .background(
                            JournalGreen.copy(alpha = 0.75f),
                            RoundedCornerShape(50)
                        )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))


            Column(
                modifier = Modifier.weight(1f)
            ) {

                // TARİH VE SIRA NUMARASI
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = date,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,

                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),

                        color = JournalGreen
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Surface(
                        shape = RoundedCornerShape(50.dp),

                        color = JournalTape.copy(alpha = 0.65f)
                    ) {

                        Text(
                            text = "%02d".format(index + 1),

                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),

                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),

                            color = JournalMuted
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                // ANI BAŞLIĞI
                Text(
                    text = title.ifBlank {
                        "Başlıksız anı"
                    },

                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    ),

                    color = JournalInk,

                    maxLines = 1,

                    overflow = TextOverflow.Ellipsis
                )


                // ANI İÇERİĞİ
                if (content.isNotBlank()) {

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = content,

                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp
                        ),

                        color = JournalMuted,

                        maxLines = 2,

                        overflow = TextOverflow.Ellipsis
                    )
                }


                // Bilgi varsa tek satırlık, sade sezon/bölüm etiketleri göster.
                // İki etiket ayrı ayrı daralabildiği için uzun sezon adı bölüm bilgisini gizlemez.
                val seasonLabel = seasonTitle?.trim()?.takeIf { it.isNotEmpty() }
                val episodeLabel = episodeTitle?.trim()?.takeIf { it.isNotEmpty() }
                if (seasonLabel != null || episodeLabel != null) {
                    Spacer(modifier = Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val labelStyle = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.sp
                        )
                        if (seasonLabel != null) {
                            Text(
                                text = "Sezon: $seasonLabel",
                                modifier = Modifier.weight(1f, fill = false),
                                style = labelStyle,
                                color = JournalGreen.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (seasonLabel != null && episodeLabel != null) {
                            Text(text = "·", style = labelStyle, color = JournalMuted)
                        }
                        if (episodeLabel != null) {
                            Text(
                                text = "Bölüm: $episodeLabel",
                                modifier = Modifier.weight(1f, fill = false),
                                style = labelStyle,
                                color = JournalMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(11.dp))


                // ALT ETİKET
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "✦",

                        color = JournalGreen,

                        style = MaterialTheme.typography.labelMedium
                    )

                    Spacer(
                        modifier = Modifier.width(6.dp)
                    )

                    Text(
                        text = "ANIYI OKU",

                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),

                        color = JournalGreen
                    )

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "↗",

                        color = JournalGreen,

                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}


// ---------------------------------------------------------
// BOŞ GÜNLÜK EKRANI
// ---------------------------------------------------------

@Composable
internal fun JournalEmptyState(

    modifier: Modifier = Modifier,

    hasActiveSearch: Boolean,

    hasActiveDateFilter: Boolean,

    hasOtherFilters: Boolean,

    onCreateJournal: () -> Unit,

    onClearSearch: () -> Unit,

    onClearFilters: () -> Unit
) {

    val hasAnyCondition =
        hasActiveSearch || hasActiveDateFilter || hasOtherFilters

    Box(
        modifier = modifier,

        contentAlignment = Alignment.Center
    ) {

        Surface(

            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(28.dp),

            color = JournalPaper,

            border = BorderStroke(
                width = 1.dp,
                color = JournalBorder
            )
        ) {

            Column(

                modifier = Modifier.padding(
                    horizontal = 26.dp,
                    vertical = 36.dp
                ),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                // DEKORATİF İKON
                Surface(

                    shape = CircleShape,

                    color = JournalSoftGreen,

                    modifier = Modifier.size(76.dp)
                ) {

                    Box(
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "✦",

                            style = MaterialTheme.typography.displaySmall,

                            color = JournalGreen
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(22.dp)
                )


                // BAŞLIK
                Text(

                    text = when {

                        hasActiveSearch ->
                            "Aradığın anıyı bulamadık"

                        hasActiveDateFilter ->
                            "Bu tarihte bir anı yok"

                        hasOtherFilters ->
                            "Bu filtrelerde bir anı yok"

                        else ->
                            "Hikâyen burada başlıyor"
                    },

                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    ),

                    color = JournalInk
                )


                Spacer(
                    modifier = Modifier.height(10.dp)
                )


                // AÇIKLAMA
                Text(

                    text = when {

                        hasActiveSearch ->
                            "Farklı bir kelime deneyebilir veya aramanı temizleyebilirsin."

                        hasActiveDateFilter ->
                            "Seçilen tarih aralığında henüz bir günlük bulunmuyor."

                        hasOtherFilters ->
                            "Kişi, sezon veya bölüm filtrelerini değiştirmeyi deneyebilirsin."

                        else ->
                            "Güzel anları, küçük detayları ve unutmak istemediğin günleri burada biriktir."
                    },

                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 23.sp
                    ),

                    color = JournalMuted
                )


                Spacer(
                    modifier = Modifier.height(24.dp)
                )


                // BUTONLAR
                if (hasAnyCondition) {

                    OutlinedButton(

                        onClick = {

                            if (hasActiveSearch) {
                                onClearSearch()
                            }

                            if (hasActiveDateFilter) {
                                onClearFilters()
                            }
                        },

                        border = BorderStroke(
                            1.dp,
                            JournalGreen
                        ),

                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = JournalGreen
                        )
                    ) {

                        Text(

                            text = if (
                                hasActiveSearch && hasActiveDateFilter
                            ) {
                                "Aramayı ve filtreleri temizle"
                            } else if (hasActiveSearch) {
                                "Aramayı temizle"
                            } else {
                                "Filtreleri temizle"
                            }
                        )
                    }

                } else {

                    Button(

                        onClick = onCreateJournal,

                        shape = RoundedCornerShape(14.dp),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = JournalGreen
                        )
                    ) {

                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        Text("İlk anını oluştur")
                    }
                }
            }
        }
    }
}
