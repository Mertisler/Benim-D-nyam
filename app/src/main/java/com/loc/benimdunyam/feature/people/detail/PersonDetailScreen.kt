package com.loc.benimdunyam.feature.people.detail

import androidx.compose.material3.TopAppBarDefaults
import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loc.benimdunyam.domain.model.PersonImageType
import com.loc.benimdunyam.feature.people.detail.calculator.PersonTimelineGrouper
import com.loc.benimdunyam.feature.people.detail.components.CinematicBadgeCard
import com.loc.benimdunyam.feature.people.detail.components.EmptyProfileMessage
import com.loc.benimdunyam.feature.people.detail.components.PersonProfileHero
import com.loc.benimdunyam.feature.people.detail.components.SeasonBreakdownCard
import com.loc.benimdunyam.feature.people.detail.components.connections.PersonConnectionsFeature
import com.loc.benimdunyam.feature.people.detail.components.special.PersonSpecialMemoryFeature
import com.loc.benimdunyam.feature.people.detail.components.timeline.PersonTimelineItem
import com.loc.benimdunyam.feature.people.detail.components.timeline.PersonTimelineYearFilter
import com.loc.benimdunyam.feature.people.detail.presentation.JournalMentionSnapshot
import com.loc.benimdunyam.feature.people.detail.presentation.PersonCoMentionCalculator
import com.loc.benimdunyam.feature.people.detail.presentation.PersonCoMentionSummary
import com.loc.benimdunyam.feature.people.detail.presentation.PersonMentionSnapshot
import com.loc.benimdunyam.feature.people.detail.mood.PersonMoodViewModel
import com.loc.benimdunyam.feature.people.detail.mood.PersonMoodInsightsCalculator
import com.loc.benimdunyam.feature.people.detail.mood.PersonMoodInsightsSummary
import com.loc.benimdunyam.feature.people.detail.mood.PersonMoodTimelineBadge
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

private val ProfileGreen get() = themedColor(Color(0xFF496C5D))
private val ProfileGold get() = themedColor(Color(0xFFB58A3A))

/**
 * Tek kişi profili, iki sekme:
 * - Anılar: unutulmaz anı seçimi + TEK kronolojik zaman çizelgesi.
 * - İçgörüler: tekrar etmeyen dönem, kişiler ve sinematik unvan bilgileri.
 *
 * Var olan PersonDetailViewModel'i kullanır. Room ya da navigation rotası değiştirmez.
 */
@Suppress("UNUSED_PARAMETER") // Eski NavHost çağrıları kırılmasın diye korunan opsiyonel callback.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToJournalDetail: (String) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
    onNavigateToPersonDetail: ((String) -> Unit)? = null,
    onNavigateToAnalytics: ((String) -> Unit)? = null,
    moodViewModel: PersonMoodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val moodState by moodViewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var manageSpecialMemories by rememberSaveable { mutableStateOf(false) }
    var showAllSeasons by rememberSaveable { mutableStateOf(false) }
    var showAllBadges by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr-TR")) }

    val profileImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.updatePersonImage(it.toString(), PersonImageType.PROFILE)
        }
    }
    val coverImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.updatePersonImage(it.toString(), PersonImageType.COVER)
        }
    }

    val availableYears = remember(uiState.appearances) {
        PersonTimelineGrouper.availableYears(uiState.appearances)
    }
    LaunchedEffect(availableYears, selectedYear) {
        if (selectedYear != null && selectedYear !in availableYears) {
            selectedYear = null
        }
    }
    val visibleAppearances = remember(uiState.appearances, selectedYear) {
        PersonTimelineGrouper.filterByYear(uiState.appearances, selectedYear)
            .sortedWith(compareByDescending<com.loc.benimdunyam.feature.people.detail.model.PersonAppearance> {
                it.journal.entryDate
            }.thenByDescending { it.journal.createdAt }.thenBy { it.journal.id })
    }

    val personId = uiState.person?.id.orEmpty()
    val sharedPeopleFromJournals = remember(uiState.associatedJournals, personId) {
        PersonCoMentionCalculator.calculate(
            currentPersonId = personId,
            journalMentions = uiState.associatedJournals.map { journal ->
                JournalMentionSnapshot(
                    journalId = journal.id,
                    mentionedPeople = journal.mentionedPeople.map { mention ->
                        PersonMentionSnapshot(mention.id, mention.name)
                    }
                )
            }
        )
    }

    // Duygular yalnızca BU kişiye bağlı anılardan hesaplanır.
    // Bir günlükteki aynı kişi/aynı anı tekrarlarını tek sayar.
    val personMoodSummary = remember(uiState.associatedJournals, moodState.moodsByJournalId) {
        PersonMoodInsightsCalculator.calculate(
            journalIds = uiState.associatedJournals.map { it.id },
            moodByJournalId = moodState.moodsByJournalId
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("Kişi Profili") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    uiState.person?.let { person ->
                        IconButton(onClick = { onNavigateToEdit(person.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Kişiyi düzenle")
                        }
                        Box {
                            IconButton(onClick = { moreMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Diğer işlemler")
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Kişiyi sil", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            uiState.person == null -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        uiState.errorMessage ?: "Kişi bulunamadı.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                val person = requireNotNull(uiState.person)
                val stats = uiState.profileStats
                val firstJournalId = stats.firstAppearance?.journal?.id
                val lastJournalId = stats.lastAppearance?.journal?.id
                val hasInsights = stats.seasonBreakdown.isNotEmpty() ||
                        stats.badges.isNotEmpty() || uiState.connections.isNotEmpty() ||
                        sharedPeopleFromJournals.isNotEmpty() ||
                        personMoodSummary.taggedMemories > 0

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "profile-hero") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            // Anı sayısı zaten PersonProfileHero içinde gösteriliyor.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallProfileMetric("${stats.seasonCount}", "Sezon", Modifier.weight(1f))
                                SmallProfileMetric("${stats.episodeCount}", "Bölüm", Modifier.weight(1f))
                                SmallProfileMetric("${stats.activeYearCount}", "Yıl", Modifier.weight(1f))
                            }
                            uiState.errorMessage?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    item(key = "profile-tabs") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = ProfileGreen,
                                divider = {}
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = {
                                        selectedTab = 0
                                        scope.launch { listState.animateScrollToItem(1) }
                                    },
                                    text = { Text("Anılar", fontWeight = FontWeight.SemiBold) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        selectedTab = 1
                                        scope.launch { listState.animateScrollToItem(1) }
                                    },
                                    text = { Text("İçgörüler", fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }

                    if (selectedTab == 0) {
                        item(key = "memories-intro") {
                            ProfileSectionIntro(
                                title = "Birlikte biriktirdikleriniz",
                                subtitle = "Küçük anlar, büyük hikâyeler."
                            )
                        }

                        item(key = "special-memories-control") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = ProfileGold)
                                    Column(Modifier.weight(1f)) {
                                        Text("Unutulmaz Anılar", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (uiState.specialMemories.isEmpty())
                                                "En özel anılarınızı seçin"
                                            else "${uiState.specialMemories.size} özel anı işaretli",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = {
                                        manageSpecialMemories = !manageSpecialMemories
                                    }) {
                                        Text(if (manageSpecialMemories) "Kapat" else "Yönet")
                                    }
                                }
                            }
                        }

                        if (manageSpecialMemories) {
                            item(key = "special-memory-editor") {
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
                        }

                        if (availableYears.size > 1) {
                            item(key = "timeline-year-filter") {
                                PersonTimelineYearFilter(
                                    years = availableYears,
                                    selectedYear = selectedYear,
                                    onYearSelected = { selectedYear = it }
                                )
                            }
                        }

                        if (visibleAppearances.isEmpty()) {
                            item(key = "empty-memories") {
                                EmptyProfileMessage(
                                    if (uiState.appearances.isEmpty())
                                        "Bu kişiyle henüz kayıtlı anı yok. Bir günlüğe eklediğinde burada görünecek."
                                    else "Bu yılda bu kişiyle ilgili kayıtlı anı bulunamadı."
                                )
                            }
                        } else {
                            items(
                                items = visibleAppearances,
                                key = { "timeline-journal-${it.journal.id}" }
                            ) { appearance ->
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    PersonTimelineItem(
                                        appearance = appearance,
                                        dateFormat = dateFormat,
                                        isFirstAppearance = appearance.journal.id == firstJournalId,
                                        isLastAppearance = appearance.journal.id == lastJournalId,
                                        onClick = { onNavigateToJournalDetail(appearance.journal.id) }
                                    )
                                    moodState.moodsByJournalId[appearance.journal.id]
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { savedMood ->
                                            PersonMoodTimelineBadge(
                                                moodName = savedMood,
                                                modifier = Modifier.padding(start = 42.dp)
                                            )
                                        }
                                }
                            }
                        }
                    } else {
                        item(key = "insights-intro") {
                            ProfileSectionIntro(
                                title = "Hikâyenizin izleri",
                                subtitle = "Birlikte kaydedilen anılardan kısa keşifler."
                            )
                        }

                        if (!hasInsights) {
                            item(key = "empty-insights") {
                                EmptyProfileMessage(
                                    "Birlikte yeni anılar biriktirdikçe burada keşfedeceğin detaylar oluşacak."
                                )
                            }
                        }

                        // Kişinin hisleri değil, kullanıcı tarafından anılara seçilen ruh halleri.
                        // Hiç kayıt yoksa boş bir grafik veya tekrar eden kart göstermeyiz.
                        if (personMoodSummary.taggedMemories > 0) {
                            item(key = "insights-person-moods") {
                                PersonMoodInsightsSummary(summary = personMoodSummary)
                            }
                        }

                        if (stats.seasonBreakdown.isNotEmpty()) {
                            item(key = "insights-seasons") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Yaşam dönemleri",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold)
                                        if (stats.seasonBreakdown.size > 1) {
                                            TextButton(onClick = { showAllSeasons = !showAllSeasons }) {
                                                Text(if (showAllSeasons) "Daha az" else "Tümünü gör")
                                            }
                                        }
                                    }

                                    if (!showAllSeasons && stats.topSeason != null) {
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("En çok ortak anı biriktirdiğiniz sezon",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(stats.topSeason.seasonTitle,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium)
                                                    Text("${stats.topSeason.memoryCount} anı",
                                                        color = ProfileGreen,
                                                        fontWeight = FontWeight.SemiBold)
                                                }
                                                stats.topEpisode?.let { episode ->
                                                    Text("Öne çıkan bölüm: ${episode.episodeTitle}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (showAllSeasons || stats.topSeason == null || stats.seasonBreakdown.size == 1) {
                                // Ayrıntılar yalnızca istenince açılıyor; tek sezonda da bilgi kaybolmuyor.
                                if (showAllSeasons || stats.topSeason == null) {
                                    items(
                                        items = stats.seasonBreakdown,
                                        key = { "insight-season-${it.seasonId ?: "none"}" }
                                    ) { seasonStat -> SeasonBreakdownCard(seasonStat) }
                                }
                            }
                        }

                        if (uiState.connections.isNotEmpty()) {
                            item(key = "insights-shared-characters") {
                                PersonConnectionsFeature(
                                    personName = person.name,
                                    connections = uiState.connections,
                                    onOpenJournal = onNavigateToJournalDetail,
                                    onNavigateToPersonDetail = onNavigateToPersonDetail
                                )
                            }
                        } else if (sharedPeopleFromJournals.isNotEmpty()) {
                            item(key = "insights-shared-people-fallback") {
                                SharedPeopleCompactSection(
                                    people = sharedPeopleFromJournals,
                                    onPersonClick = onNavigateToPersonDetail
                                )
                            }
                        }

                        if (stats.badges.isNotEmpty()) {
                            item(key = "insights-badges-title") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sinematik unvanlar",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold)
                                    if (stats.badges.size > 2) {
                                        TextButton(onClick = { showAllBadges = !showAllBadges }) {
                                            Text(if (showAllBadges) "Daha az" else "Tümünü gör")
                                        }
                                    }
                                }
                            }
                            items(
                                items = if (showAllBadges) stats.badges else stats.badges.take(2)
                            ) { badge -> CinematicBadgeCard(badge) }
                        }

                        if (hasInsights) {
                            item(key = "insights-note") {
                                Text(
                                    "İçgörüler kayıtlı anılara dayanır; kişilerin gerçek hayattaki yakınlığını ölçmez.",
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item(key = "profile-bottom-spacer") {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Kişiyi Sil") },
            text = {
                Text("Bu kişi silinsin mi? Günlüklerin silinmez; kişiye ait bağlantılar temizlenir.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePerson(onDeleted = onNavigateBack)
                    }
                ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun SmallProfileMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = ProfileGreen)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileSectionIntro(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SharedPeopleCompactSection(
    people: List<PersonCoMentionSummary>,
    onPersonClick: ((String) -> Unit)?
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hikâyedeki diğer insanlar",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            if (people.size > 3) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Daha az" else "Tümünü gör")
                }
            }
        }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (if (showAll) people else people.take(3)).forEach { person ->
                    Row(
                        modifier = Modifier.fillMaxWidth().then(
                            if (onPersonClick == null) Modifier
                            else Modifier.clickable { onPersonClick(person.personId) }
                        ).padding(horizontal = 4.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(person.name, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("${person.sharedMemoryCount} anı",
                            color = ProfileGreen, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
