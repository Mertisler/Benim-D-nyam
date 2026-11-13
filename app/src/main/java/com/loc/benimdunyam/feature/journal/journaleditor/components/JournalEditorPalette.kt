package com.loc.benimdunyam.feature.journal.journaleditor.components

import com.loc.benimdunyam.core.designsystem.AppThemeController
import com.loc.benimdunyam.core.designsystem.themedColor

import androidx.compose.ui.graphics.Color

internal object JournalEditorPalette {

    val PaperInk get() = AppThemeController.palette.ink

    val TapeInk get() = AppThemeController.palette.ink

    val TapeColor get() = AppThemeController.palette.tape

    val SaveGreen get() = AppThemeController.palette.primary

    val PaperWhite get() = AppThemeController.palette.paper

    val PaperText get() = AppThemeController.palette.ink

    val PaperPlaceholder get() = AppThemeController.palette.muted

    val MentionBackground get() = AppThemeController.palette.softPrimary

    val ErrorBackground get() = Color(0xFFF0DAD3)

    val ErrorText get() = Color(0xFF7B3028)
}