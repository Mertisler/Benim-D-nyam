
package com.loc.benimdunyam.feature.journal

import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess

import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.loc.benimdunyam.domain.model.JournalMood


private val MoodPaper get() = themedColor(Color(0xFFEFE6D8))
private val MoodInk get() = themedColor(Color(0xFF33271F))
private val MoodMuted get() = themedColor(Color(0xFF756755))
private val MoodGreen get() = themedColor(Color(0xFF365A43))
private val MoodSelected get() = themedColor(Color(0xFFDCE6D7))


@Composable
fun JournalMoodPicker(
    modifier: Modifier = Modifier,
    selectedMood: JournalMood?,

    // Seçilen duygu + tıklanan emojinin ekran konumu
    onMoodSelected: (JournalMood, Offset) -> Unit,

    onDismiss: () -> Unit
) {

    Surface(
        modifier = modifier.fillMaxWidth(),

        shape = RoundedCornerShape(15.dp),

        color = MoodPaper,

        shadowElevation = 1.dp
    ) {

        Column(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 7.dp
            ),

            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // BAŞLIK

            Row(
                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "BUGÜN NASIL HİSSETTİN?",

                    modifier = Modifier.weight(1f),

                    color = MoodInk,

                    fontSize = 11.sp,

                    fontWeight = FontWeight.Bold,

                    letterSpacing = 0.5.sp
                )

                Icon(
                    imageVector = Icons.Default.ExpandLess,

                    contentDescription = "Duygu panelini kapat",

                    tint = MoodMuted,

                    modifier = Modifier
                        .size(23.dp)
                        .clickable {
                            onDismiss()
                        }
                )
            }


            // EMOJİLER

            JournalMood.entries
                .toList()
                .chunked(4)
                .forEach { moodRow ->

                    Row(
                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        moodRow.forEach { mood ->

                            MoodItem(
                                mood = mood,

                                selected = selectedMood == mood,

                                modifier = Modifier.weight(1f),

                                onClick = { sourcePosition ->

                                    onMoodSelected(
                                        mood,
                                        sourcePosition
                                    )
                                }
                            )
                        }

                        // Son satırda eksik kalan hücreleri doldur.
                        repeat(4 - moodRow.size) {

                            Box(
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
        }
    }
}


// =====================================================
// MOOD ITEM
// =====================================================

@Composable
private fun MoodItem(
    mood: JournalMood,

    selected: Boolean,

    modifier: Modifier = Modifier,

    onClick: (Offset) -> Unit
) {

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,

        animationSpec = tween(180),

        label = "MoodScale"
    )

    // Emojinin gerçek ekran koordinatı.
    var itemCenter by remember {
        mutableStateOf(Offset.Zero)
    }

    Surface(
        onClick = {
            onClick(itemCenter)
        },

        modifier = modifier
            .height(48.dp)
            .onGloballyPositioned { coordinates ->

                val position = coordinates.positionInRoot()

                itemCenter = Offset(
                    x = position.x + coordinates.size.width / 2f,
                    y = position.y + coordinates.size.height / 2f
                )
            },

        shape = RoundedCornerShape(10.dp),

        color = if (selected) {
            MoodSelected
        } else {
            themedColor(Color(0xFFFFFCF7))
        },

        border = if (selected) {
            BorderStroke(1.5.dp, MoodGreen)
        } else {
            BorderStroke(1.dp, themedColor(Color(0xFFDED2C0)))
        }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },

            horizontalAlignment = Alignment.CenterHorizontally,

            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = mood.emoji,

                fontSize = 21.sp
            )

            Text(
                text = mood.label,

                color = if (selected) MoodGreen else MoodMuted,

                fontSize = 9.sp,

                fontWeight = FontWeight.Medium
            )
        }
    }
}


// =====================================================
// SELECTED MOOD BADGE
// =====================================================

@Composable
fun SelectedMoodBadge(
    mood: JournalMood,

    onClick: () -> Unit
) {

    Surface(
        onClick = onClick,

        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),

        color = MoodSelected,

        shape = RoundedCornerShape(5.dp),

        border = BorderStroke(
            1.dp,
            MoodGreen.copy(alpha = 0.45f)
        )
    ) {

        Row(
            modifier = Modifier.padding(horizontal = 9.dp),

            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {

            Text(
                text = mood.emoji,

                fontSize = 19.sp
            )

            Text(
                text = mood.label,

                color = MoodGreen,

                fontSize = 12.sp,

                fontWeight = FontWeight.SemiBold,

                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.CheckCircle,

                contentDescription = "Seçilen duygu",

                tint = MoodGreen,

                modifier = Modifier.size(16.dp)
            )
        }
    }
}
