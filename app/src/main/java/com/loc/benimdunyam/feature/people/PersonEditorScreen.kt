package com.loc.benimdunyam.feature.people

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditing) "Kişiyi Düzenle" else "Yeni Kişi Ekle"
                    )
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
                    IconButton(
                        onClick = { viewModel.savePerson(onSuccess = onNavigateBack) },
                        enabled = !uiState.isLoading && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Kaydet",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                placeholder = { Text("İsim ve Soyisim *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isLoading && !uiState.isSaving,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChanged,
                placeholder = {
                    Text("Kişisel not / hatırlatıcı (isteğe bağlı)...")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                enabled = !uiState.isLoading && !uiState.isSaving,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
