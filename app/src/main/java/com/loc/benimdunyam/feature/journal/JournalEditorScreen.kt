package com.loc.benimdunyam.feature.journal

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.loc.benimdunyam.domain.model.Person
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

    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
    val formattedDate = dateFormat.format(Date(uiState.entryDate))

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
                title = { Text(text = "Günlük Düzenle / Yaz") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveJournal(onSuccess = onNavigateBack) }) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Kaydet", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hata mesajı varsa göster
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

            // Tarih Seçici Çipi
            Surface(
                modifier = Modifier
                    .clickable { datePickerDialog.show() }
                    .wrapContentWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Başlık Alanı
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChanged,
                placeholder = { Text("Başlık ekle...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // İçerik Alanı (@kişi mention destekli)
            OutlinedTextField(
                value = uiState.content,
                onValueChange = viewModel::onContentChanged,
                placeholder = { Text("Bugün neler yaşadın? '@' yazarak kişi etiketleyebilirsin...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            // @ Mention Öneri Listesi (Popup / LazyRow)
            if (uiState.isMentionPopupVisible) {
                val filteredPeople = uiState.allPeople.filter {
                    it.name.contains(uiState.mentionQuery, ignoreCase = true)
                }

                if (filteredPeople.isNotEmpty()) {
                    Text(
                        text = "Kişi Önerileri (@):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredPeople, key = { it.id }) { person ->
                            SuggestionChip(
                                onClick = { viewModel.selectPersonForMention(person) },
                                label = { Text(person.name) }
                            )
                        }
                    }
                }
            }

            // Etiketlenen Kişiler Listesi (Çipler)
            if (uiState.mentionedPeople.isNotEmpty()) {
                Text(
                    text = "Bu Günlükte Anılan Kişiler:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
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