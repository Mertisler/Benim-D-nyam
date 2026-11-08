
package com.loc.benimdunyam.feature.journal.journaldetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel

import com.loc.benimdunyam.feature.journal.journaldetail.components.*
import com.loc.benimdunyam.feature.journal.journalphotos.JournalPhotoGallery

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

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    var showPhotoGallery by remember {
        mutableStateOf(false)
    }

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

    val currentJournal = uiState.journal

    // Önceki/sonraki anıya geçildiğinde
    // açık olan fotoğraf penceresini kapat.
    LaunchedEffect(currentJournal?.id) {
        showPhotoGallery = false
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        JournalDetailBackground(
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            // ÜST MENÜ
            JournalDetailTopBar(
                showActions = currentJournal != null,

                onNavigateBack = onNavigateBack,

                onEdit = {
                    currentJournal?.let { journal ->
                        onNavigateToEdit(journal.id)
                    }
                },

                onDelete = {
                    showDeleteDialog = true
                }
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

                currentJournal != null -> {

                    val journal = currentJournal

                    val entryDate = remember(journal.entryDate) {
                        Date(journal.entryDate)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {

                        // GÜNLÜK İÇERİĞİ
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

                        // FOTOĞRAF GALERİSİNİ AÇ
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),

                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showPhotoGallery = true
                                }
                            ) {
                                Text(
                                    text = "📷 Anı Fotoğrafları",
                                    color = JournalDetailColors.Green
                                )
                            }
                        }

                        // ÖNCEKİ / SONRAKİ ANI
                        JournalDetailNavigationBar(
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = 10.dp
                            ),

                            canGoPrevious =
                                uiState.previousJournalId != null,

                            canGoNext =
                                uiState.nextJournalId != null,

                            currentPosition = uiState.currentPosition,

                            totalJournals = uiState.totalJournals,

                            onPrevious = {
                                uiState.previousJournalId?.let(
                                    viewModel::openJournal
                                )
                            },

                            onNext = {
                                uiState.nextJournalId?.let(
                                    viewModel::openJournal
                                )
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
                                text = uiState.errorMessage
                                    ?: "Günlük bulunamadı.",

                                color = JournalDetailColors.Ink,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }

        // FOTOĞRAF GALERİSİ PENCERESİ
        if (
            showPhotoGallery &&
            currentJournal != null
        ) {
            Dialog(
                onDismissRequest = {
                    showPhotoGallery = false
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "Anı Fotoğrafları",
                                style = MaterialTheme.typography.titleLarge
                            )

                            TextButton(
                                onClick = {
                                    showPhotoGallery = false
                                }
                            ) {
                                Text("Kapat")
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                )
                        ) {

                            JournalPhotoGallery(
                                journalId = currentJournal.id,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // GÜNLÜK SİLME PENCERESİ
    if (showDeleteDialog) {
        JournalDeleteDialog(
            onDismiss = {
                showDeleteDialog = false
            },

            onConfirm = {
                showDeleteDialog = false

                viewModel.deleteJournal(
                    onDeleted = onNavigateBack
                )
            }
        )
    }
}
