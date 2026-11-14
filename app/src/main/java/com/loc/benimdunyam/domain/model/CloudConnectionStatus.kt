package com.loc.benimdunyam.domain.model

/** Sunucudan yanıt alınarak yapılan, salt okunur bağlantı kontrolü. */
sealed interface CloudConnectionStatus {
    data object SignInRequired : CloudConnectionStatus
    data object Reachable : CloudConnectionStatus
    data object PermissionDenied : CloudConnectionStatus
    data object NetworkUnavailable : CloudConnectionStatus
    data object UnknownError : CloudConnectionStatus
}
