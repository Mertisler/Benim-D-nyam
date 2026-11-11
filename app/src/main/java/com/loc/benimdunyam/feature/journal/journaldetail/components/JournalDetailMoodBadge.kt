package com.loc.benimdunyam.feature.journal.journaldetail.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.journal.journaldetail.JournalDetailMoodViewModel

/** Tarih bilgilerinin sağında yalnızca emoji; ekran okuyucuya duygu adı da verilir. */
@Composable
fun JournalDetailMoodBadge(
    journalId: String,
    modifier: Modifier = Modifier,
    viewModel: JournalDetailMoodViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(journalId) { viewModel.showMoodFor(journalId) }

    // Önceki/sonraki anıda geçici olarak eski emojiyi göstermeyiz.
    if (state.journalId != journalId || state.isUnavailable) return
    val mood = state.moodName?.takeIf(String::isNotBlank)
        ?.let(::journalMoodDisplay) ?: return

    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Bu anının ruh hali: ${mood.label}"
        },
        shape = RoundedCornerShape(13.dp),
        color = Color(0xFFF0E8DC)
    ) {
        Box(Modifier.size(45.dp), contentAlignment = Alignment.Center) {
            Text(text = mood.emoji, fontSize = 25.sp)
        }
    }
}
