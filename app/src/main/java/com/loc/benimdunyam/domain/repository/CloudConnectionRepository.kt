package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.CloudConnectionStatus

interface CloudConnectionRepository {
    /** Mevcut kullanıcının /users/{uid}/_meta/connection belgesini sunucudan okur. */
    suspend fun checkReadAccess(): CloudConnectionStatus
}
