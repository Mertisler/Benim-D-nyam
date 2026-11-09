package com.loc.benimdunyam.feature.yearreview.featured

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class YearFeaturedMemoryToggleViewModel @Inject constructor(
    private val repository: YearFeaturedMemoryRepository
) : ViewModel() {
    val selections = repository.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun toggle(journalId: String, entryDate: Long) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val result = repository.toggle(journalId, YearFeaturedMemoryRepository.yearOf(entryDate))
                message.value = when (result) {
                    FeaturedToggleResult.ADDED -> "Anın yılın filmine eklendi. ✨"
                    FeaturedToggleResult.REMOVED -> "Anı filmden kaldırıldı."
                    FeaturedToggleResult.LIMIT_REACHED -> "Bu yıl için en fazla 5 özel anı seçebilirsin."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Seçim kaydedilemedi. Tekrar dene."
            } finally {
                busy.value = false
            }
        }
    }
}

/**
 * Mevcut JournalDetailScreen'in gövdesine ekle:
 * YearFeaturedMemoryToggle(journalId = journal.id, entryDate = journal.entryDate)
 * Hilt'in mevcut Navigation back stack owner'ından ViewModel alır.
 */
@Composable
fun YearFeaturedMemoryToggle(
    journalId: String,
    entryDate: Long,
    modifier: Modifier = Modifier,
    viewModel: YearFeaturedMemoryToggleViewModel = hiltViewModel()
) {
    val items by viewModel.selections.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val selected = items.any { it.journalId == journalId }
    val year = YearFeaturedMemoryRepository.yearOf(entryDate)
    val count = items.count { it.year == year }
    val gold = Color(0xFFC8A36A)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { viewModel.toggle(journalId, entryDate) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = gold)
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = gold
            )
            Text(if (selected) "  Yılın filminden çıkar" else "  Unutulmaz anı olarak seç")
        }
        Text("$year filmi: $count / ${YearFeaturedMemoryRepository.MAX_PER_YEAR} özel anı")
        if (!message.isNullOrBlank()) {
            Text(text = message.orEmpty(), color = gold)
        }
    }
}
