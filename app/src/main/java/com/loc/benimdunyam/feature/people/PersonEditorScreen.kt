package com.loc.benimdunyam.feature.people

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonEditorViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (viewModel.isEditing) "Kişiyi Düzenle" else "Yeni Kişi Ekle") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.savePerson(onSuccess = onNavigateBack) }) {
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

            // İsim Alanı (Zorunlu)
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                placeholder = { Text("İsim ve Soyisim *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Ayırt Edici Kişisel Not (İsteğe bağlı)[cite: 4, 6, 7]
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChanged,
                placeholder = { Text("Kişisel not / hatırlatıcı (isteğe bağlı)...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}