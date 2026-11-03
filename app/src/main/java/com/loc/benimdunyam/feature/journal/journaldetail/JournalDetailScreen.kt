package com.loc.benimdunyam.feature.journal.journaldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDeleteDialog
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailBackground
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailColors
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailContent
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailNavigationBar
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailPersonUi
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateFormat = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
    }
    val timeFormat = remember {
        SimpleDateFormat("HH:mm", Locale("tr", "TR"))
    }
    val dayFormat = remember {
        SimpleDateFormat("dd", Locale("tr", "TR"))
    }
    val monthFormat = remember {
        SimpleDateFormat("MMMM yyyy", Locale("tr", "TR"))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        JournalDetailBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            JournalDetailTopBar(
                showActions = uiState.journal != null,
                onNavigateBack = onNavigateBack,
                onEdit = {
                    uiState.journal?.let { journal ->
                        onNavigateToEdit(journal.id)
                    }
                },
                onDelete = { showDeleteDialog = true }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = JournalDetailColors.Green
                        )
                    }
                }

                uiState.journal != null -> {
                    val journal = uiState.journal!!
                    val entryDate = remember(journal.entryDate) {
                        Date(journal.entryDate)
                    }

                    // İçerik kaydırılabilir alanı kaplar; gezinme çubuğu ekranın altında sabit kalır.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        JournalDetailContent(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            title = journal.title,
                            content = journal.content,
                            day = dayFormat.format(entryDate),
                            monthYear = monthFormat.format(entryDate),
                            time = timeFormat.format(entryDate),
                            fullDate = dateFormat.format(entryDate),
                            mentionedPeople = journal.mentionedPeople.map { person ->
                                JournalDetailPersonUi(
                                    id = person.id.toString(),
                                    name = person.name
                                )
                            }
                        )

                        JournalDetailNavigationBar(
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = 10.dp
                            ),
                            canGoPrevious = uiState.previousJournalId != null,
                            canGoNext = uiState.nextJournalId != null,
                            currentPosition = uiState.currentPosition,
                            totalJournals = uiState.totalJournals,
                            onPrevious = {
                                uiState.previousJournalId?.let(viewModel::openJournal)
                            },
                            onNext = {
                                uiState.nextJournalId?.let(viewModel::openJournal)
                            }
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✧",
                                color = JournalDetailColors.Green,
                                fontSize = 34.sp
                            )
                            Text(
                                text = uiState.errorMessage ?: "Günlük bulunamadı.",
                                color = JournalDetailColors.Ink,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        JournalDeleteDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteJournal(onDeleted = onNavigateBack)
            }
        )
    }
}
