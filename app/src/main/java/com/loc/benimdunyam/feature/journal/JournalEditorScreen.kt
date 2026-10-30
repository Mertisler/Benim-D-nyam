package com.loc.benimdunyam.feature.journal

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: JournalEditorViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current

    val parchmentGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFAF7F2),
            Color(0xFFF3ECE1)
        )
    )

    val dayFormat = SimpleDateFormat("dd", Locale("tr"))
    val monthYearFormat = SimpleDateFormat("MMM yyyy\nEEEE", Locale("tr"))

    val dayNumberStr = dayFormat.format(Date(uiState.entryDate))
    val monthYearStr = monthYearFormat.format(Date(uiState.entryDate))

    val datePickerDialog = remember {
        val calendar = Calendar.getInstance().apply { timeInMillis = uiState.entryDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                viewModel.onDateChanged(selectedCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveJournal(onSuccess = onNavigateBack) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Kaydetmek",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }) {
                        Icon(imageVector = Icons.Outlined.TextFields, contentDescription = "Biçim", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { }) {
                        Icon(imageVector = Icons.Outlined.Image, contentDescription = "Görsel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { }) {
                        Icon(imageVector = Icons.Outlined.Star, contentDescription = "Yıldız", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { }) {
                        Icon(imageVector = Icons.Outlined.EmojiEmotions, contentDescription = "Emoji", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { }) {
                        Icon(imageVector = Icons.Outlined.TextFields, contentDescription = "Metin", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parchmentGradient)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (uiState.errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Tarih Alanı
                Row(
                    modifier = Modifier
                        .clickable { datePickerDialog.show() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = dayNumberStr,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 36.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = monthYearStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Tarih Seç",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Yaşam Dönemleri
                if (uiState.allSeasons.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedSeasonId == null,
                                onClick = { viewModel.onSeasonSelected(null) },
                                label = { Text("Dönemsiz") }
                            )
                        }
                        items(uiState.allSeasons, key = { it.id }) { season ->
                            FilterChip(
                                selected = uiState.selectedSeasonId == season.id,
                                onClick = {
                                    if (uiState.selectedSeasonId == season.id) {
                                        viewModel.onSeasonSelected(null)
                                    } else {
                                        viewModel.onSeasonSelected(season.id)
                                    }
                                },
                                label = { Text(season.title) }
                            )
                        }
                    }
                }

                // Başlık Alanı
                BasicTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChanged,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (uiState.title.isEmpty()) {
                                Text(
                                    text = "Başlığı girin...",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            innerTextField()
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // İçerik Alanı ve Sağ Ortada Açılan Yüzen (Overlay) Kişi Önerileri
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    BasicTextField(
                        value = uiState.content,
                        onValueChange = viewModel::onContentChanged,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (uiState.content.isEmpty()) {
                                    Text(
                                        text = "Buraya daha fazla yazın...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // --- SAĞ ORTADA KONUMLANDIRILMIŞ ÖNERİ KARTI ---
                    if (uiState.isMentionPopupVisible) {
                        val filteredPeople = uiState.allPeople.filter {
                            it.name.contains(uiState.mentionQuery, ignoreCase = true)
                        }

                        if (filteredPeople.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd) // Sağ üst/ortada hizalama
                                    .width(220.dp)
                                    .heightIn(max = 180.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(filteredPeople, key = { it.id }) { person ->
                                        Surface(
                                            onClick = { viewModel.selectPersonForMention(person) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Transparent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = person.name.take(1).uppercase(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "@${person.name}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Etiketlenen Kişiler Çipleri
                if (uiState.mentionedPeople.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(uiState.mentionedPeople, key = { it.id }) { person ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeMentionedPerson(person) },
                                label = { Text(person.name) },
                                avatar = {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = person.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}