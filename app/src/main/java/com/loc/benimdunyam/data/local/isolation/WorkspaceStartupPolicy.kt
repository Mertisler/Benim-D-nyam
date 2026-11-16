package com.loc.benimdunyam.data.local.isolation

/**
 * Hesap alanının uygulama AÇILIRKEN seçilmesini sağlayan saf Kotlin kararları.
 * Bu dosya Android/Firebase bağımlılığı içermediğinden birim testleri kolaydır.
 */
enum class WorkspaceMode { LEGACY, ACCOUNT, LOCKED }

object WorkspaceStartupPolicy {
    /** Önceki sürümlerde seçim yoksa eski depolama alanı kullanılmaya devam eder. */
    fun mode(selectedUid: String?, signedInAtBoot: String?): WorkspaceMode = when {
        selectedUid.isNullOrBlank() -> WorkspaceMode.LEGACY
        selectedUid == signedInAtBoot -> WorkspaceMode.ACCOUNT
        else -> WorkspaceMode.LOCKED
    }

    /** Eski arşive ait olmayan başka bir hesap kendi boş alanını oluşturabilir. */
    fun canCreateFresh(legacyOwnerUid: String?, signedInUid: String?): Boolean =
        !legacyOwnerUid.isNullOrBlank() && !signedInUid.isNullOrBlank() &&
                legacyOwnerUid != signedInUid
}
