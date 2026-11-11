package com.loc.benimdunyam.feature.journal.journaldetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDeleteDialog
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailColors
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailMoodBadge
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailNavigationBar
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalDetailTopBar
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalReadingPage
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalPhotoPreviewCard
import com.loc.benimdunyam.feature.journal.journaldetail.components.JournalPhotoFullscreenViewer
import com.loc.benimdunyam.feature.journal.journalphotos.JournalPhotoGallery
import com.loc.benimdunyam.feature.journal.journalphotos.JournalPhotoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Anı Detayı V4: fotoğraf şeridi altta sabit; metin içi kişi bağlantıları ve üstte ruh hali.
 * Mevcut ViewModel, fotoğraf diyaloğu, ruh hali kaydı ve önceki/sonraki geçişler korunur.
 */
@Composable
fun JournalDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel(),
    photoViewModel: JournalPhotoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val photoUiState by photoViewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhotoGallery by remember { mutableStateOf(false) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }

    val tr = remember { Locale.forLanguageTag("tr-TR") }
    val dateFormat = remember(tr) { SimpleDateFormat("dd MMMM yyyy", tr) }
    val timeFormat = remember(tr) { SimpleDateFormat("HH:mm", tr) }
    val dayFormat = remember(tr) { SimpleDateFormat("dd", tr) }
    val monthFormat = remember(tr) { SimpleDateFormat("MMM", tr) }
    val currentJournal = uiState.journal

    LaunchedEffect(currentJournal?.id) {
        // Oklarla yeni anıya geçince eski anının fotoğraf penceresi açık kalmaz.
        showPhotoGallery = false
        selectedPhotoId = null
        currentJournal?.id?.let(photoViewModel::loadForJournal)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFCF9F2), Color(0xFFF3EADC))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            JournalDetailTopBar(
                showActions = currentJournal != null,
                onNavigateBack = onNavigateBack,
                onEdit = { currentJournal?.id?.let(onNavigateToEdit) },
                onDelete = { showDeleteDialog = true }
            )

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JournalDetailColors.Green)
                    }
                }

                currentJournal != null -> {
                    val journal = currentJournal
                    val entryDate = remember(journal.entryDate) { Date(journal.entryDate) }
                    val photosReady = photoUiState.journalId == journal.id && !photoUiState.isLoading
                    val journalPhotos = if (photosReady) photoUiState.photos else emptyList()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // key sayesinde bir anıdan diğerine geçince okuma alanı en baştan açılır.
                        key(journal.id) {
                            JournalReadingPage(
                                title = journal.title,
                                content = journal.content,
                                day = dayFormat.format(entryDate),
                                monthShort = monthFormat.format(entryDate),
                                fullDate = dateFormat.format(entryDate),
                                time = timeFormat.format(entryDate),
                                mentionedPeople = journal.mentionedPeople,
                                onPersonClick = onNavigateToPersonDetail,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                moodContent = {
                                    JournalDetailMoodBadge(journalId = journal.id)
                                }
                            )
                        }

                        // Fotoğraf şeridi okuma alanının DIŞINDA: kısa veya uzun anılarda
                        // ekranın altında, önceki/sonraki anı oklarının hemen üstünde kalır.
                        // Yeni fotoğraf yüklenirken bir önceki anının fotoğrafları gösterilmez.
                        if (photosReady) {
                            JournalPhotoPreviewCard(
                                photos = journalPhotos,
                                onPhotoClick = { selectedPhotoId = it.id },
                                onManageClick = { showPhotoGallery = true },
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 3.dp,
                                    bottom = 4.dp
                                )
                            )
                        }

                        JournalDetailNavigationBar(
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 2.dp,
                                bottom = 8.dp
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
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Günlük bulunamadı.",
                            color = JournalDetailColors.Ink,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // Tek dokunuşla tam ekran fotoğraf görüntüleyici; sağa/sola kaydırılabilir.
        val openedJournalId = currentJournal?.id
        val openedPhotos = if (photoUiState.journalId == openedJournalId) photoUiState.photos else emptyList()
        selectedPhotoId?.takeIf { selectedId ->
            openedPhotos.any { it.id == selectedId }
        }?.let { photoId ->
            JournalPhotoFullscreenViewer(
                photos = openedPhotos,
                initialPhotoId = photoId,
                onDismiss = { selectedPhotoId = null }
            )
        }

        if (showPhotoGallery && currentJournal != null) {
            Dialog(
                onDismissRequest = { showPhotoGallery = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
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
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Anı Fotoğrafları", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { showPhotoGallery = false }) { Text("Kapat") }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            JournalPhotoGallery(
                                journalId = currentJournal.id,
                                modifier = Modifier.fillMaxWidth(),
                                viewModel = photoViewModel
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
