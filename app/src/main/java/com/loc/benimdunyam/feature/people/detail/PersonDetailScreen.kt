package com.loc.benimdunyam.feature.people.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.loc.benimdunyam.domain.model.PersonImageType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loc.benimdunyam.feature.people.detail.analytics.PersonAnalyticsScreen
import com.loc.benimdunyam.feature.people.detail.analytics.components.PersonAnalyticsPreviewCard
import com.loc.benimdunyam.feature.people.detail.insights.PersonStoryInsightsFeature
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.feature.people.detail.calculator.PersonTimelineGrouper
import com.loc.benimdunyam.feature.people.detail.components.connections.PersonConnectionsFeature
import com.loc.benimdunyam.feature.people.detail.components.AppearanceInfoCard
import com.loc.benimdunyam.feature.people.detail.components.CinematicBadgeCard
import com.loc.benimdunyam.feature.people.detail.components.EmptyProfileMessage
import com.loc.benimdunyam.feature.people.detail.components.HighlightInfoCard
import com.loc.benimdunyam.feature.people.detail.components.PersonProfileHero
import com.loc.benimdunyam.feature.people.detail.components.ProfileMetricCard
import com.loc.benimdunyam.feature.people.detail.components.ProfileSectionTitle
import com.loc.benimdunyam.feature.people.detail.components.SeasonBreakdownCard
import com.loc.benimdunyam.feature.people.detail.components.special.PersonSpecialMemoryFeature
import com.loc.benimdunyam.feature.people.detail.components.timeline.PersonTimelineItem
import com.loc.benimdunyam.feature.people.detail.components.timeline.PersonTimelineSeasonHeader
import com.loc.benimdunyam.feature.people.detail.components.timeline.PersonTimelineYearFilter
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
    onNavigateToPersonDetail: ((String) -> Unit)? = null,
    onNavigateToAnalytics: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAnalyticsDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTimelineYear by rememberSaveable { mutableStateOf<Int?>(null) }

    val dateFormat = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
    }

    // Seçilen fotoğraflar ViewModel tarafından uygulamanın özel depolamasına kopyalanır.
    val profileImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.updatePersonImage(
                sourceUri = it.toString(),
                imageType = PersonImageType.PROFILE
            )
        }
    }

    val coverImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.updatePersonImage(
                sourceUri = it.toString(),
                imageType = PersonImageType.COVER
            )
        }
    }

    val timelineYears = remember(uiState.appearances) {
        PersonTimelineGrouper.availableYears(uiState.appearances)
    }

    // If the selected year disappears after an entry is deleted/edited,
    // return the timeline to the all-years view.
    LaunchedEffect(timelineYears, selectedTimelineYear) {
        if (selectedTimelineYear != null && selectedTimelineYear !in timelineYears) {
            selectedTimelineYear = null
        }
    }

    val filteredAppearances = remember(uiState.appearances, selectedTimelineYear) {
        PersonTimelineGrouper.filterByYear(
            appearances = uiState.appearances,
            year = selectedTimelineYear
        )
    }

    val timelineGroups = remember(filteredAppearances) {
        PersonTimelineGrouper.groupBySeason(filteredAppearances)
    }

    val firstAppearanceJournalId = uiState.profileStats.firstAppearance?.journal?.id
    val lastAppearanceJournalId = uiState.profileStats.lastAppearance?.journal?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.person?.name ?: "Karakter Profili")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                actions = {
                    uiState.person?.let { person ->
                        IconButton(onClick = { onNavigateToEdit(person.id) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Düzenle"
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Kişiyi sil",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.person == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Kişi bulunamadı.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                val person = requireNotNull(uiState.person)
                val stats = uiState.profileStats

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        PersonProfileHero(
                            person = person,
                            memoryCount = stats.totalMemoryCount,
                            isSavingImage = uiState.isSavingImage,
                            onPickProfileImage = {
                                profileImagePicker.launch(arrayOf("image/*"))
                            },
                            onPickCoverImage = {
                                coverImagePicker.launch(arrayOf("image/*"))
                            }
                        )
                    }

                    uiState.errorMessage?.let { error ->
                        item {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileSectionTitle(
                                title = "İlişki İstatistikleri",
                                subtitle = "Hikâyende ne kadar yer aldı?"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProfileMetricCard(
                                    title = "Ortak Anı",
                                    value = stats.totalMemoryCount.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileMetricCard(
                                    title = "Yaşam Dönemi",
                                    value = stats.seasonCount.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProfileMetricCard(
                                    title = "Farklı Bölüm",
                                    value = stats.episodeCount.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                                ProfileMetricCard(
                                    title = "Farklı Yıl",
                                    value = stats.activeYearCount.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // A compact preview opens the standalone analytics screen.
                    // When NavHost is not wired yet, it opens in a full-screen dialog.
                    item(key = "person-analytics-preview") {
                        PersonAnalyticsPreviewCard(
                            appearances = uiState.appearances,
                            onOpenAnalytics = {
                                if (onNavigateToAnalytics != null) {
                                    onNavigateToAnalytics(person.id)
                                } else {
                                    showAnalyticsDialog = true
                                }
                            }
                        )
                    }

                    // Offline, evidence-based story insights. No new database or ViewModel.
                    item(key = "person-story-insights") {
                        PersonStoryInsightsFeature(
                            personName = person.name,
                            appearances = uiState.appearances,
                            specialMemories = uiState.specialMemories,
                            onOpenJournal = onNavigateToJournalDetail
                        )
                    }

                    // Isolated feature: picker, editor and confirmation live in their own component.
                    item(key = "person-special-memories") {
                        PersonSpecialMemoryFeature(
                            journals = uiState.associatedJournals,
                            memories = uiState.specialMemories,
                            isSaving = uiState.isSavingSpecialMemory,
                            errorMessage = uiState.specialMemoryErrorMessage,
                            onOpenJournal = onNavigateToJournalDetail,
                            onSave = { journalId, category, note, onSaved ->
                                viewModel.saveSpecialMemory(journalId, category, note, onSaved)
                            },
                            onRemove = { journalId, onRemoved ->
                                viewModel.removeSpecialMemory(journalId, onRemoved)
                            },
                            onClearError = viewModel::clearSpecialMemoryError
                        )
                    }

                    // Character co-appearances + full-screen shared-memory viewer.
                    // Existing timeline, special memories and hero remain unchanged.
                    item(key = "person-connections") {
                        PersonConnectionsFeature(
                            personName = person.name,
                            connections = uiState.connections,
                            onOpenJournal = onNavigateToJournalDetail,
                            onNavigateToPersonDetail = onNavigateToPersonDetail
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileSectionTitle(
                                title = "Öne Çıkan Görünümler",
                                subtitle = "En çok birlikte anı biriktirdiğiniz yerler."
                            )
                            stats.topSeason?.let { topSeason ->
                                HighlightInfoCard(
                                    label = "EN ÇOK YER ALDIĞI SEZON",
                                    title = topSeason.seasonTitle,
                                    description = "${topSeason.memoryCount} ortak anı",
                                    emoji = "🎬"
                                )
                            }
                            stats.topEpisode?.let { topEpisode ->
                                HighlightInfoCard(
                                    label = "EN ÇOK YER ALDIĞI BÖLÜM",
                                    title = topEpisode.episodeTitle,
                                    description = buildString {
                                        append("${topEpisode.memoryCount} ortak anı")
                                        topEpisode.seasonTitle?.let { append(" • $it") }
                                    },
                                    emoji = "📺"
                                )
                            }
                            if (stats.topSeason == null && stats.topEpisode == null) {
                                EmptyProfileMessage(
                                    "Henüz bir sezon veya bölüm bağlantısı bulunmuyor."
                                )
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileSectionTitle(
                                title = "Sinematik Unvanlar",
                                subtitle = "Anılarına göre kazandığı unvanlar."
                            )
                            if (stats.badges.isEmpty()) {
                                EmptyProfileMessage(
                                    "Bu karakter henüz bir unvan kazanmadı. Yeni anılar ekledikçe unvanları burada belirecek."
                                )
                            } else {
                                stats.badges.forEach { badge ->
                                    CinematicBadgeCard(badge)
                                }
                            }
                            Text(
                                text = "Unvanlar, arşivindeki anı verilerine göre oluşturulan eğlenceli rozetlerdir. Kişisel ilişkilerin niteliğini ölçmez.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileSectionTitle(
                                title = "İlk ve Son Görünme",
                                subtitle = "Hikâyenizdeki ilk ve son karşılaşmalar."
                            )
                            AppearanceInfoCard(
                                title = "İlk Görünme",
                                emoji = "🌱",
                                appearance = stats.firstAppearance,
                                dateFormat = dateFormat,
                                onClick = {
                                    stats.firstAppearance?.let {
                                        onNavigateToJournalDetail(it.journal.id)
                                    }
                                }
                            )
                            AppearanceInfoCard(
                                title = "Son Görünme",
                                emoji = "✨",
                                appearance = stats.lastAppearance,
                                dateFormat = dateFormat,
                                onClick = {
                                    stats.lastAppearance?.let {
                                        onNavigateToJournalDetail(it.journal.id)
                                    }
                                }
                            )
                        }
                    }

                    item {
                        ProfileSectionTitle(
                            title = "Sezon ve Bölüm Dağılımı",
                            subtitle = "Hangi yaşam dönemlerinde daha fazla yer aldı?"
                        )
                    }

                    if (stats.seasonBreakdown.isEmpty()) {
                        item {
                            EmptyProfileMessage(
                                "Henüz sezon veya bölüm istatistiği oluşturulamadı."
                            )
                        }
                    } else {
                        items(
                            items = stats.seasonBreakdown,
                            key = { it.seasonId ?: "unassigned-season" }
                        ) { seasonStat ->
                            SeasonBreakdownCard(seasonStat)
                        }
                    }

                    // --------------------------------------------------
                    // CHRONOLOGICAL PERSON STORY
                    // --------------------------------------------------

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileSectionTitle(
                                title = "${person.name}'in Hikâyesi",
                                subtitle = "Birlikte yaşadığınız anılar, en eskiden en yeniye."
                            )
                            Text(
                                text = "Bir anıya dokunarak günlüğün tamamını açabilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item {
                        PersonTimelineYearFilter(
                            years = timelineYears,
                            selectedYear = selectedTimelineYear,
                            onYearSelected = { selectedTimelineYear = it }
                        )
                    }

                    if (timelineGroups.isEmpty()) {
                        item {
                            EmptyProfileMessage(
                                text = if (uiState.appearances.isEmpty()) {
                                    "Bu kişiyle henüz ilişkilendirilmiş bir anı bulunmuyor. Günlüklerine bu kişiyi eklediğinde hikâyesi burada oluşacak."
                                } else {
                                    "Seçilen yılda bu kişiye ait bir anı bulunmuyor."
                                }
                            )
                        }
                    } else {
                        timelineGroups.forEach { group ->
                            item(key = "timeline-season-${group.key}") {
                                PersonTimelineSeasonHeader(group = group)
                            }

                            items(
                                items = group.appearances,
                                key = { appearance ->
                                    "timeline-journal-${appearance.journal.id}"
                                }
                            ) { appearance ->
                                PersonTimelineItem(
                                    appearance = appearance,
                                    dateFormat = dateFormat,
                                    isFirstAppearance = appearance.journal.id == firstAppearanceJournalId,
                                    isLastAppearance = appearance.journal.id == lastAppearanceJournalId,
                                    onClick = {
                                        onNavigateToJournalDetail(appearance.journal.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAnalyticsDialog) {
        Dialog(
            onDismissRequest = { showAnalyticsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                PersonAnalyticsScreen(
                    onNavigateBack = { showAnalyticsDialog = false }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Karakteri Sil") },
            text = {
                Text(
                    "Bu kişiyi silmek istediğine emin misin? Günlüklerin silinmez, yalnızca kişi bağlantıları temizlenir."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePerson(onDeleted = onNavigateBack)
                    }
                ) {
                    Text(
                        text = "Sil",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
