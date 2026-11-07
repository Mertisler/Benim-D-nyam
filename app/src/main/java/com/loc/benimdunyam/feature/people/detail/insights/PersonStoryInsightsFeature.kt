package com.loc.benimdunyam.feature.people.detail.insights

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.loc.benimdunyam.feature.people.detail.insights.calculator.PersonStoryInsightsCalculator
import com.loc.benimdunyam.feature.people.detail.insights.components.PersonStoryInsightsPreviewCard
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import com.loc.benimdunyam.feature.people.detail.model.PersonSpecialMemoryItem

/**
 * Drop-in feature entry. No new ViewModel, Room table or navigation route is needed.
 * The parent PersonDetailViewModel already provides all reactive input data.
 */
@Composable
fun PersonStoryInsightsFeature(
    personName: String,
    appearances: List<PersonAppearance>,
    specialMemories: List<PersonSpecialMemoryItem>,
    onOpenJournal: (String) -> Unit
) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    val report = remember(appearances, specialMemories) {
        PersonStoryInsightsCalculator.calculate(
            appearances = appearances,
            specialMemories = specialMemories
        )
    }

    PersonStoryInsightsPreviewCard(
        personName = personName,
        report = report,
        onClick = { isOpen = true }
    )

    if (isOpen) {
        Dialog(
            onDismissRequest = { isOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                PersonStoryInsightsScreen(
                    personName = personName,
                    report = report,
                    onNavigateBack = { isOpen = false },
                    onOpenJournal = { journalId ->
                        isOpen = false
                        onOpenJournal(journalId)
                    }
                )
            }
        }
    }
}
