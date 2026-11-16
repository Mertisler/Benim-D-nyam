package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.CloudAccount
import com.loc.benimdunyam.domain.model.CloudAuthResult
import com.loc.benimdunyam.domain.model.CloudPasswordResetStatus
import com.loc.benimdunyam.domain.model.CloudProfileResult
import kotlinx.coroutines.flow.Flow

/**
 * Bulut hesabı için tek sözleşme.
 * Bu arayüz Room veritabanını, fotoğrafları veya bulut arşivlerini değiştirmez.
 */
interface CloudAuthRepository {
    val account: Flow<CloudAccount?>

    suspend fun signInWithEmail(email: String, password: String): CloudAuthResult
    suspend fun registerWithEmail(email: String, password: String): CloudAuthResult
    suspend fun requestPasswordReset(email: String): CloudPasswordResetStatus
    /** Doğrulama e-postasını gönderir; e-postanın açıldığını garanti etmez. */
    suspend fun sendVerificationEmail(): CloudProfileResult

    /** Kullanıcı bağlantıya tıkladıktan sonra sunucudan güncel hesap bilgisini alır. */
    suspend fun refreshProfile(): CloudProfileResult

    /** Yalnızca Firebase hesabındaki görünen adı günceller. Room etkilenmez. */
    suspend fun updateDisplayName(displayName: String): CloudProfileResult

    fun signOut()
}
