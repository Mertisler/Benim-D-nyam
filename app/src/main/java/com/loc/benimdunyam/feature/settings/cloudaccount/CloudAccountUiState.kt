package com.loc.benimdunyam.feature.settings.cloudaccount

import com.loc.benimdunyam.domain.model.CloudAccount
import com.loc.benimdunyam.domain.model.CloudConnectionStatus
import com.loc.benimdunyam.domain.model.CloudArchiveSummary
import com.loc.benimdunyam.domain.model.CloudRestorePreview
import com.loc.benimdunyam.domain.model.CloudLocalCoverage
import com.loc.benimdunyam.domain.model.CloudSyncAuditReport

/** Yalnızca bulut hesabı ekranının durumu. Room arşivi bu sınıfa bağlı değildir. */
data class CloudAccountUiState(
    val isCheckingSession: Boolean = true,
    val account: CloudAccount? = null,
    val isSubmitting: Boolean = false,
    val isCheckingConnection: Boolean = false,
    val connectionStatus: CloudConnectionStatus? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val isArchiveBusy: Boolean = false,
    val archiveAction: String? = null,
    val latestArchive: CloudArchiveSummary? = null,
    val archiveMessage: String? = null,
    val archiveError: String? = null,
    val restorePreview: CloudRestorePreview? = null,
    // Denetim raporları yalnızca sayıları taşır; günlük içeriği UI state'e alınmaz.
    val localCoverage: CloudLocalCoverage? = null,
    val syncAudit: CloudSyncAuditReport? = null
)
