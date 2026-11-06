package com.loc.benimdunyam.feature.people.detail.components.connections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.loc.benimdunyam.feature.people.detail.model.PersonConnectionItem

/**
 * Owns selected connection UI state. The parent screen only passes state and callbacks.
 * Other-profile navigation is optional to preserve existing NavHost integration.
 */
@Composable
fun PersonConnectionsFeature(
    personName: String,
    connections: List<PersonConnectionItem>,
    onOpenJournal: (String) -> Unit,
    onNavigateToPersonDetail: ((String) -> Unit)? = null
) {
    var selectedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = connections.firstOrNull { it.personId == selectedPersonId }

    LaunchedEffect(selectedPersonId, connections) {
        if (selectedPersonId != null && selected == null) selectedPersonId = null
    }

    PersonConnectionsSection(
        connections = connections,
        onSelect = { selectedPersonId = it }
    )

    if (selected != null) {
        SharedMemoriesScreen(
            personName = personName,
            connection = selected,
            onClose = { selectedPersonId = null },
            onOpenJournal = { journalId ->
                selectedPersonId = null
                onOpenJournal(journalId)
            },
            onOpenOtherProfile = onNavigateToPersonDetail?.let { navigate ->
                {
                    selectedPersonId = null
                    navigate(selected.personId)
                }
            }
        )
    }
}
