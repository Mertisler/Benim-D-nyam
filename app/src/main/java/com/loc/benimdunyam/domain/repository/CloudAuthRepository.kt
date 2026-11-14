package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.CloudAccount
import com.loc.benimdunyam.domain.model.CloudAuthResult
import kotlinx.coroutines.flow.Flow

/** Yalnızca uzak hesap durumunu yönetir. Room kayıtlarını değiştirmez. */
interface CloudAuthRepository {
    val account: Flow<CloudAccount?>

    suspend fun signInWithEmail(email: String, password: String): CloudAuthResult
    suspend fun registerWithEmail(email: String, password: String): CloudAuthResult
    fun signOut()
}
