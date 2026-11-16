package com.loc.benimdunyam.data.remote.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.loc.benimdunyam.domain.model.CloudAccount
import com.loc.benimdunyam.domain.model.CloudAuthError
import com.loc.benimdunyam.domain.model.CloudAuthResult
import com.loc.benimdunyam.domain.model.CloudPasswordResetStatus
import com.loc.benimdunyam.domain.model.CloudProfileError
import com.loc.benimdunyam.domain.model.CloudProfileResult
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Firebase kimlik doğrulamasının uzak veri katmanı.
 * Başarılı giriş, kendi başına Firestore arşiv yüklemesi BAŞLATMAZ.
 */
class FirebaseCloudAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : CloudAuthRepository {

    override val account: Flow<CloudAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toCloudAccount())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithEmail(email: String, password: String): CloudAuthResult =
        runAuthOperation {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user
        }

    override suspend fun registerWithEmail(email: String, password: String): CloudAuthResult =
        runAuthOperation {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user
        }

    /**
     * Firebase kullanıcının e-postasına şifre sıfırlama bağlantısı gönderir.
     * Var olmayan e-posta bilgisi dışarı sızmasın diye kullanıcıya genel bir
     * başarı mesajı gösterilecek biçimde sonuç döndürürüz.
     */
    override suspend fun requestPasswordReset(email: String): CloudPasswordResetStatus = try {
        firebaseAuth.setLanguageCode("tr")
        firebaseAuth.sendPasswordResetEmail(email.trim()).await()
        CloudPasswordResetStatus.SENT
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: FirebaseAuthInvalidUserException) {
        CloudPasswordResetStatus.SENT
    } catch (_: FirebaseTooManyRequestsException) {
        CloudPasswordResetStatus.RATE_LIMITED
    } catch (_: FirebaseNetworkException) {
        CloudPasswordResetStatus.NETWORK_ERROR
    } catch (_: Exception) {
        CloudPasswordResetStatus.FAILED
    }

    /**
     * Firebase e-posta doğrulama bağlantısını kullanıcıya gönderir.
     * İstek tamamlandığında e-posta otomatik olarak doğrulanmış sayılmaz.
     */
    override suspend fun sendVerificationEmail(): CloudProfileResult = profileOperation {
        val user = firebaseAuth.currentUser ?: return@profileOperation CloudProfileResult.Failure(
            CloudProfileError.SIGN_IN_REQUIRED
        )
        firebaseAuth.setLanguageCode("tr")
        user.sendEmailVerification().await()
        currentAccountAfterAction(user.uid)
    }

    /**
     * Doğrulama işleminin tamamlanıp tamamlanmadığını önbellekten değil
     * Firebase sunucusundan tekrar öğrenmek için reload() kullanılır.
     */
    override suspend fun refreshProfile(): CloudProfileResult = profileOperation {
        val user = firebaseAuth.currentUser ?: return@profileOperation CloudProfileResult.Failure(
            CloudProfileError.SIGN_IN_REQUIRED
        )
        user.reload().await()
        currentAccountAfterAction(user.uid)
    }

    /**
     * Görünen ad Firebase Authentication profilinde saklanır.
     * Kullanıcının günlük başlıklarını ve kişi kayıtlarını değiştirmez.
     */
    override suspend fun updateDisplayName(displayName: String): CloudProfileResult = profileOperation {
        val user = firebaseAuth.currentUser ?: return@profileOperation CloudProfileResult.Failure(
            CloudProfileError.SIGN_IN_REQUIRED
        )
        val request = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName.trim())
            .build()
        user.updateProfile(request).await()
        currentAccountAfterAction(user.uid)
    }

    /** Ağ isteği sırasında hesap değişirse eski hesabın sonucu kullanılmaz. */
    private fun currentAccountAfterAction(expectedUid: String): CloudProfileResult {
        val currentUser = firebaseAuth.currentUser
        return if (currentUser?.uid == expectedUid) {
            CloudProfileResult.Success(currentUser.toCloudAccount())
        } else {
            CloudProfileResult.Failure(CloudProfileError.ACCOUNT_CHANGED)
        }
    }

    /** Firebase istisnalarını Domain katmanına taşımadan anlamlı sonuç üretir. */
    private suspend fun profileOperation(
        block: suspend () -> CloudProfileResult
    ): CloudProfileResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: FirebaseAuthRecentLoginRequiredException) {
        CloudProfileResult.Failure(CloudProfileError.RECENT_LOGIN_REQUIRED)
    } catch (_: FirebaseAuthInvalidUserException) {
        CloudProfileResult.Failure(CloudProfileError.SIGN_IN_REQUIRED)
    } catch (_: FirebaseTooManyRequestsException) {
        CloudProfileResult.Failure(CloudProfileError.RATE_LIMITED)
    } catch (_: FirebaseNetworkException) {
        CloudProfileResult.Failure(CloudProfileError.NETWORK)
    } catch (_: Exception) {
        CloudProfileResult.Failure(CloudProfileError.UNKNOWN)
    }

    override fun signOut() {
        // Oturum kapanır, ancak cihazdaki Room kayıtları korunur.
        firebaseAuth.signOut()
    }

    private suspend fun runAuthOperation(
        operation: suspend () -> com.google.firebase.auth.FirebaseUser?
    ): CloudAuthResult = try {
        val firebaseUser = operation()
        if (firebaseUser == null) {
            CloudAuthResult.Failure(CloudAuthError.UNKNOWN)
        } else {
            CloudAuthResult.Success(firebaseUser.toCloudAccount())
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: FirebaseAuthUserCollisionException) {
        CloudAuthResult.Failure(CloudAuthError.EMAIL_ALREADY_IN_USE)
    } catch (_: FirebaseAuthWeakPasswordException) {
        CloudAuthResult.Failure(CloudAuthError.WEAK_PASSWORD)
    } catch (_: FirebaseAuthInvalidCredentialsException) {
        CloudAuthResult.Failure(CloudAuthError.INVALID_EMAIL_OR_PASSWORD)
    } catch (_: FirebaseAuthInvalidUserException) {
        CloudAuthResult.Failure(CloudAuthError.INVALID_EMAIL_OR_PASSWORD)
    } catch (_: FirebaseTooManyRequestsException) {
        CloudAuthResult.Failure(CloudAuthError.RATE_LIMITED)
    } catch (_: FirebaseNetworkException) {
        CloudAuthResult.Failure(CloudAuthError.NETWORK)
    } catch (_: Exception) {
        CloudAuthResult.Failure(CloudAuthError.UNKNOWN)
    }
}
