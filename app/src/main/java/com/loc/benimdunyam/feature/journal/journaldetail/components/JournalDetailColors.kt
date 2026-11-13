package com.loc.benimdunyam.feature.journal.journaldetail.components

import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.ui.graphics.Color

internal object JournalDetailColors {
    val PaperLight get() = AppThemeController.palette.background
    val Paper get() = AppThemeController.palette.paper
    val Ink get() = AppThemeController.palette.ink
    val MutedInk get() = AppThemeController.palette.muted
    val Tape get() = AppThemeController.palette.tape
    val Green get() = AppThemeController.palette.primary
    val Delete get() = themedColor(Color(0xFF9B453B))
    val Polaroid get() = AppThemeController.palette.paper
    val PolaroidImageTop get() = AppThemeController.palette.tape
    val PolaroidImageBottom get() = AppThemeController.palette.border
    val Accent get() = AppThemeController.palette.secondary
    val Divider get() = AppThemeController.palette.border
    val PersonTag get() = AppThemeController.palette.softPrimary
}
