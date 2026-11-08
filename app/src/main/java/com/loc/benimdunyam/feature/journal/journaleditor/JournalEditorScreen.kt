
package com.loc.benimdunyam.feature.journal.journaleditor

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.data.local.media.JournalPhotoStore

import com.loc.benimdunyam.feature.journal.JournalMoodPicker

import com.loc.benimdunyam.feature.journal.journaleditor.components.JournalBottomToolbar
import com.loc.benimdunyam.feature.journal.journaleditor.components.JournalEditorHeader
import com.loc.benimdunyam.feature.journal.journaleditor.components.JournalEditorPhotoSection
import com.loc.benimdunyam.feature.journal.journaleditor.components.JournalEditorTextSection
import com.loc.benimdunyam.feature.journal.journaleditor.components.ParchmentBackground

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@Composable
fun JournalEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: JournalEditorViewModel = hiltViewModel()
) {

    // =================================================
    // VIEWMODEL STATE
    // =================================================

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Tek bir galeri seçicisi: hem editör içindeki düğme hem alt fotoğraf ikonu kullanır.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) viewModel.addPhotos(selectedUris)
    }

    val canAddPhotos = !uiState.isLoading && !uiState.isSaving &&
            !uiState.isLeaving && !uiState.isSaved && !uiState.isPhotoWorking &&
            !uiState.showRestoreDraftDialog &&
            (uiState.savedPhotos.size + uiState.draftPhotos.size < JournalPhotoStore.MAX_PHOTOS_PER_JOURNAL)

    val openPhotoPicker = {
        if (canAddPhotos) {
            photoPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
    }

    // Sistem geri tusu: bekleyen taslak diske yazilmadan ekran kapanmaz.
    BackHandler {
        viewModel.requestExit(onNavigateBack)
    }

    // Uygulama arka plana giderken son degisiklikleri kaydetmeyi dener.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushOnBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    // =================================================
    // LOCAL UI STATE
    // =================================================

    // Duygu seçim panelinin açılıp kapanması.
    var showMoodPicker by remember {
        mutableStateOf(true)
    }


    // =================================================
    // MOOD ANIMATION STATE
    // =================================================

    // Ana ekranın root koordinatı.
    var screenOrigin by remember {
        mutableStateOf(Offset.Zero)
    }

    // Duygu rozetinin merkez koordinatı.
    var moodTargetCenter by remember {
        mutableStateOf<Offset?>(null)
    }

    // Hedef koordinat henüz hazır değilse bekleyen emoji.
    var pendingMoodFlight by remember {
        mutableStateOf<Pair<JournalMood, Offset>?>(null)
    }

    // Devam eden emoji animasyonu.
    var moodFlight by remember {
        mutableStateOf<MoodFlight?>(null)
    }


    // =================================================
    // SEÇİLİ SEZON VE BÖLÜM
    // =================================================

    val selectedSeason = uiState.allSeasons.find { season ->

        season.id == uiState.selectedSeasonId
    }

    val selectedEpisode = uiState.currentSeasonEpisodes.find { episode ->

        episode.id == uiState.selectedEpisodeId
    }


    // =================================================
    // TARİH BİLGİSİ
    // =================================================

    val context = LocalContext.current

    val entryDate = Date(uiState.entryDate)

    val dayFormat = remember {
        SimpleDateFormat(
            "dd",
            Locale("tr", "TR")
        )
    }

    val fullDateFormat = remember {
        SimpleDateFormat(
            "MMM yyyy EEEE",
            Locale("tr", "TR")
        )
    }

    val dateLabel = remember(uiState.entryDate) {

        "${dayFormat.format(entryDate)} ${
            fullDateFormat.format(entryDate)
        }"
    }


    // =================================================
    // TARİH SEÇİCİ
    // =================================================

    val datePickerDialog = remember(
        context,
        uiState.entryDate
    ) {

        val calendar = Calendar.getInstance().apply {
            timeInMillis = uiState.entryDate
        }

        DatePickerDialog(

            context,

            { _, year, month, dayOfMonth ->

                val selectedCalendar = Calendar.getInstance().apply {

                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }

                viewModel.onDateChanged(
                    selectedCalendar.timeInMillis
                )
            },

            calendar.get(Calendar.YEAR),

            calendar.get(Calendar.MONTH),

            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }


    // =================================================
    // ANA EKRAN
    // =================================================

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->

                // Root Box'ın ekran içerisindeki konumu.
                screenOrigin = coordinates.positionInRoot()
            }
    ) {

        // -------------------------------------------------
        // PARŞÖMEN ARKA PLAN
        // -------------------------------------------------

        ParchmentBackground(
            modifier = Modifier.matchParentSize()
        )


        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            // =============================================
            // ANA İÇERİK
            // =============================================

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {

                // -----------------------------------------
                // ÜST ALAN
                // -----------------------------------------

                JournalEditorHeader(

                    selectedSeasonTitle = selectedSeason?.title,

                    selectedSeasonImagePath = selectedSeason?.coverImagePath,

                    selectedEpisodeTitle = selectedEpisode?.let { episode ->
                        "Bölüm ${episode.orderIndex}: ${episode.title}"
                    },

                    selectedMood = uiState.selectedMood,

                    dateLabel = dateLabel,

                    seasonOptions = uiState.allSeasons,

                    episodeOptions = uiState.currentSeasonEpisodes,

                    onSeasonSelected = viewModel::onSeasonSelected,

                    onEpisodeSelected = viewModel::onEpisodeSelected,

                    // TARİH SEÇİMİ
                    onDateClick = {
                        datePickerDialog.show()
                    },

                    // DUYGU ROZETİNE TIKLAMA
                    onMoodBadgeClick = {
                        showMoodPicker = true
                    },

                    // =====================================
                    // EMOJİNİN HEDEF KONUMU
                    // =====================================

                    onMoodTargetPositioned = { target ->

                        // Root koordinatını yerel koordinata çevir.
                        val localTarget = target - screenOrigin

                        if (moodTargetCenter != localTarget) {
                            moodTargetCenter = localTarget
                        }

                        // Hedef henüz hazır değilken emoji seçildiyse
                        // animasyonu şimdi başlat.
                        val pending = pendingMoodFlight

                        if (pending != null && moodFlight == null) {

                            moodFlight = MoodFlight(
                                mood = pending.first,
                                source = pending.second,
                                target = localTarget
                            )

                            pendingMoodFlight = null
                        }
                    },

                    // GERİ DÖN
                    onNavigateBack = {
                        viewModel.requestExit(onNavigateBack)
                    },

                    // GÜNLÜĞÜ KAYDET
                    onSaveClick = {

                        viewModel.saveJournal(
                            onSuccess = onNavigateBack
                        )
                    }
                )

                // Taslak durumu: var olan header tasarimini degistirmeden kucuk bilgi.
                val draftLabel = when (val status = uiState.draftSaveState) {
                    DraftSaveState.Idle -> null
                    DraftSaveState.Pending -> "Taslak kaydedilecek..."
                    DraftSaveState.Saving -> "Taslak kaydediliyor..."
                    DraftSaveState.Saved -> "Taslak kaydedildi"
                    is DraftSaveState.Error -> "Taslak kaydedilemedi: ${status.message}"
                }
                if (draftLabel != null && !uiState.isSaved) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.draftSaveState == DraftSaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(12.dp).height(12.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = draftLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.draftSaveState is DraftSaveState.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }


                // -----------------------------------------
                // GÜNLÜK YAZMA ALANI
                // -----------------------------------------

                JournalEditorTextSection(

                    modifier = Modifier.weight(1f),

                    title = uiState.title,

                    content = uiState.content,

                    errorMessage = uiState.errorMessage,

                    isMentionPopupVisible = uiState.isMentionPopupVisible,

                    mentionQuery = uiState.mentionQuery,

                    allPeople = uiState.allPeople,

                    mentionedPeople = uiState.mentionedPeople,

                    // Kişi ID
                    personKey = { person ->
                        person.id
                    },

                    // Kişi adı
                    personName = { person ->
                        person.name
                    },

                    // BAŞLIK
                    onTitleChanged = viewModel::onTitleChanged,

                    // İÇERİK
                    onContentChanged = viewModel::onContentChanged,

                    // KİŞİ ETİKETLEME
                    onPersonSelected = viewModel::selectPersonForMention,

                    // ETİKETLENEN KİŞİYİ KALDIRMA
                    onMentionRemoved = viewModel::removeMentionedPerson
                )

                // Fotoğraf seçimi artık Anı Yazma / Düzenleme ekranında.
                // Günlük henüz oluşturulmamışsa görseller taslakta korunur.
                JournalEditorPhotoSection(
                    savedPhotos = uiState.savedPhotos,
                    draftPhotos = uiState.draftPhotos,
                    isWorking = uiState.isPhotoWorking,
                    enabled = !uiState.isLoading && !uiState.isSaving &&
                            !uiState.isLeaving && !uiState.isSaved &&
                            !uiState.showRestoreDraftDialog,
                    errorMessage = uiState.photoErrorMessage,
                    onPickPhotos = openPhotoPicker,
                    onRemoveDraftPhoto = viewModel::removeDraftPhoto,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }


            // =============================================
            // DUYGU SEÇİM PANELİ
            // =============================================

            AnimatedVisibility(

                visible = showMoodPicker,

                enter = slideInVertically(
                    initialOffsetY = { it }
                ) + fadeIn(),

                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut()
            ) {

                JournalMoodPicker(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 5.dp),

                    selectedMood = uiState.selectedMood,

                    // =====================================
                    // EMOJİ SEÇİLDİ
                    // =====================================

                    onMoodSelected = { mood, sourcePosition ->

                        // Seçilen duyguyu ViewModel'e aktar.
                        viewModel.onMoodSelected(mood)

                        // Paneli kapat.
                        showMoodPicker = false

                        // Kaynak koordinatını ana Box'a göre hesapla.
                        val localSource = sourcePosition - screenOrigin

                        // Hedef koordinatını kontrol et.
                        val target = moodTargetCenter

                        if (target != null) {

                            // Hedef hazırsa uçuşu başlat.
                            moodFlight = MoodFlight(
                                mood = mood,
                                source = localSource,
                                target = target
                            )

                            pendingMoodFlight = null

                        } else {

                            // Hedef henüz ölçülmediyse beklet.
                            pendingMoodFlight = mood to localSource
                        }
                    },

                    // PANELİ KAPAT
                    onDismiss = {
                        showMoodPicker = false
                    }
                )
            }


            // =============================================
            // ALT ARAÇ ÇUBUĞU
            // =============================================

            JournalBottomToolbar(
                onImageClick = openPhotoPicker,
                imageEnabled = canAddPhotos
            )
        }


        // =================================================
        // UÇAN EMOJİ ANİMASYONU
        // =================================================

        moodFlight?.let { flight ->

            FlyingMoodBubble(

                flight = flight,

                onFinished = {

                    // Animasyon tamamlandığında
                    // uçan emoji bileşenini kaldır.
                    if (moodFlight == flight) {
                        moodFlight = null
                    }
                }
            )
        }
    }


    if (uiState.showRestoreDraftDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.requestExit(onNavigateBack) },
            title = { Text("Yarım kalan anın bulundu") },
            text = {
                Column {
                    Text("Daha önce yazdığın, henüz yayınlamadığın bir anı var. Kaldığın yerden devam edebilirsin.")
                    uiState.errorMessage?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::restoreDraft, enabled = !uiState.isLoading) {
                    Text("Devam Et")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardDraft, enabled = !uiState.isLoading) {
                    Text("Taslağı Sil")
                }
            }
        )
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
