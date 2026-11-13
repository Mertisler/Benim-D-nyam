
package com.loc.benimdunyam.feature.journal.journaleditor.components

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Image

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage

import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.feature.journal.SelectedMoodBadge


@Composable
fun JournalEditorHeader(
    selectedSeasonTitle: String?,
    selectedSeasonImagePath: String?,
    selectedEpisodeTitle: String?,
    selectedMood: JournalMood?,
    dateLabel: String,

    // Domain modelleri
    seasonOptions: List<LifeSeason>,
    episodeOptions: List<LifeEpisode>,

    // ID işlemleri
    onSeasonSelected: (String?) -> Unit,
    onEpisodeSelected: (String?) -> Unit,

    // Diğer işlemler
    onDateClick: () -> Unit,
    onMoodBadgeClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onSaveClick: () -> Unit,

    // Emoji animasyonunun hedef konumu
    onMoodTargetPositioned: (Offset) -> Unit,

    modifier: Modifier = Modifier
) {

    // =================================================
    // MENÜ DURUMLARI
    // =================================================

    var seasonMenuExpanded by remember {
        mutableStateOf(false)
    }

    var episodeMenuExpanded by remember {
        mutableStateOf(false)
    }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(244.dp)
    ) {

        // =================================================
        // FOTOĞRAF + SEZON + BÖLÜM
        // =================================================

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 56.dp,
                    end = 12.dp,
                    top = 28.dp
                ),

            verticalAlignment = Alignment.Top
        ) {

            // ---------------------------------------------
            // POLAROID TARİH KARTI
            // ---------------------------------------------

            PolaroidDateCard(
                imagePath = selectedSeasonImagePath,

                imageDescription = selectedSeasonTitle
                    ?: "Sezon fotoğrafı",

                dateLabel = dateLabel,

                onClick = onDateClick
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )


            // ---------------------------------------------
            // SEZON VE BÖLÜM ALANI
            // ---------------------------------------------

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 88.dp),

                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                // =========================================
                // SEZON SEÇİMİ
                // =========================================

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    TapeLabel(
                        text = selectedSeasonTitle
                            ?: "Dönem seçin",

                        icon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = JournalEditorPalette.TapeInk,
                                modifier = Modifier.size(16.dp)
                            )
                        },

                        rotation = 1.2f,

                        onClick = {
                            seasonMenuExpanded = true
                        }
                    )

                    DropdownMenu(
                        expanded = seasonMenuExpanded,

                        onDismissRequest = {
                            seasonMenuExpanded = false
                        }
                    ) {

                        // Sezon seçimini kaldır.

                        DropdownMenuItem(
                            text = {
                                Text("Dönem yok")
                            },

                            onClick = {
                                onSeasonSelected(null)

                                episodeMenuExpanded = false

                                seasonMenuExpanded = false
                            }
                        )

                        seasonOptions.forEach { season ->

                            DropdownMenuItem(
                                text = {
                                    Text(season.title)
                                },

                                onClick = {
                                    onSeasonSelected(season.id)

                                    episodeMenuExpanded = false

                                    seasonMenuExpanded = false
                                }
                            )
                        }
                    }
                }


                // =========================================
                // BÖLÜM SEÇİMİ
                // =========================================

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    TapeLabel(
                        text = selectedEpisodeTitle
                            ?: "Bölüm seçin",

                        icon = {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,

                                tint = JournalEditorPalette.TapeInk.copy(
                                    alpha = if (selectedSeasonTitle != null) {
                                        1f
                                    } else {
                                        0.45f
                                    }
                                ),

                                modifier = Modifier.size(16.dp)
                            )
                        },

                        rotation = -1f,

                        enabled = selectedSeasonTitle != null,

                        onClick = {
                            episodeMenuExpanded = true
                        }
                    )

                    DropdownMenu(
                        expanded = episodeMenuExpanded,

                        onDismissRequest = {
                            episodeMenuExpanded = false
                        }
                    ) {

                        // Bölüm seçimini kaldır.

                        DropdownMenuItem(
                            text = {
                                Text("Bölüm yok")
                            },

                            onClick = {
                                onEpisodeSelected(null)

                                episodeMenuExpanded = false
                            }
                        )

                        episodeOptions.forEach { episode ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${episode.orderIndex}. Bölüm: ${episode.title}"
                                    )
                                },

                                onClick = {
                                    onEpisodeSelected(episode.id)

                                    episodeMenuExpanded = false
                                }
                            )
                        }
                    }
                }


                // =========================================
                // SEÇİLEN DUYGU ROZETİ
                // =========================================

                AnimatedVisibility(
                    visible = selectedMood != null,

                    enter = expandVertically(
                        expandFrom = Alignment.Top,

                        animationSpec = tween(250)

                    ) + fadeIn(
                        animationSpec = tween(250)
                    ),

                    exit = shrinkVertically(
                        animationSpec = tween(180)

                    ) + fadeOut(
                        animationSpec = tween(150)
                    )
                ) {

                    selectedMood?.let { mood ->

                        Box(
                            modifier = Modifier.onGloballyPositioned { coordinates ->

                                // Rozetin ekran üzerindeki başlangıç konumu.
                                val position = coordinates.positionInRoot()

                                // Rozetin merkez koordinatı.
                                val targetPosition = Offset(
                                    x = position.x + coordinates.size.width / 2f,

                                    y = position.y + coordinates.size.height / 2f
                                )

                                // Ekrana hedef koordinatını bildir.
                                onMoodTargetPositioned(targetPosition)
                            }
                        ) {

                            SelectedMoodBadge(
                                mood = mood,

                                onClick = onMoodBadgeClick
                            )
                        }
                    }
                }
            }
        }


        // =================================================
        // GERİ BUTONU + KAYDET
        // =================================================

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    end = 12.dp,
                    top = 38.dp
                ),

            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(44.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Geri",
                    tint = JournalEditorPalette.PaperInk,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            JournalSaveButton(
                onClick = onSaveClick
            )
        }
    }
}


// =====================================================
// POLAROID TARİH KARTI
// =====================================================

@Composable
private fun PolaroidDateCard(
    imagePath: String?,
    imageDescription: String,
    dateLabel: String,
    onClick: () -> Unit
) {

    Card(
        onClick = onClick,

        modifier = Modifier
            .width(170.dp)
            .height(200.dp)
            .graphicsLayer {
                rotationZ = -3f
            },

        shape = RoundedCornerShape(2.dp),

        colors = CardDefaults.cardColors(
            containerColor = JournalEditorPalette.PaperWhite
        ),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 7.dp,
                    top = 7.dp,
                    end = 7.dp,
                    bottom = 6.dp
                ),

            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(themedColor(Color(0xFFE9E1D5)))
            ) {

                if (!imagePath.isNullOrBlank()) {

                    AsyncImage(
                        model = imagePath,
                        contentDescription = imageDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                } else {

                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "Sezon görseli seçilmedi",

                        tint = JournalEditorPalette.PaperPlaceholder,

                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = dateLabel,

                color = JournalEditorPalette.PaperText,

                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Medium,

                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}


// =====================================================
// KAĞIT BANT ETİKETİ
// =====================================================

@Composable
private fun TapeLabel(
    text: String,
    icon: @Composable () -> Unit,
    rotation: Float,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .graphicsLayer {
                rotationZ = rotation
            }
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
    ) {

        Canvas(
            modifier = Modifier.matchParentSize()
        ) {

            val tapePath = Path().apply {

                moveTo(0f, size.height * 0.08f)
                lineTo(size.width * 0.96f, 0f)
                lineTo(size.width, size.height * 0.78f)
                lineTo(size.width * 0.91f, size.height)
                lineTo(size.width * 0.04f, size.height * 0.88f)

                close()
            }

            drawPath(
                path = tapePath,

                color = JournalEditorPalette.TapeColor.copy(
                    alpha = if (enabled) 0.96f else 0.68f
                )
            )

            drawLine(
                color = themedColor(Color(0xFFB6A78E)).copy(alpha = 0.24f),

                start = Offset(
                    size.width * 0.08f,
                    size.height * 0.83f
                ),

                end = Offset(
                    size.width * 0.88f,
                    size.height * 0.91f
                ),

                strokeWidth = 1.dp.toPx()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),

            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            icon()

            Text(
                text = text,

                modifier = Modifier.weight(1f),

                color = JournalEditorPalette.TapeInk.copy(
                    alpha = if (enabled) 1f else 0.5f
                ),

                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Medium,

                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Seçenekleri aç",

                tint = JournalEditorPalette.TapeInk.copy(
                    alpha = if (enabled) 0.9f else 0.45f
                ),

                modifier = Modifier.size(17.dp)
            )
        }
    }
}
