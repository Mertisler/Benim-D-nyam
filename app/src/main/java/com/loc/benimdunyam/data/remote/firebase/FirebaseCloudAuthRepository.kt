package com.loc.benimdunyam.data.remote.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.loc.benimdunyam.domain.model.CloudAccount
import com.loc.benimdunyam.domain.model.CloudAuthError
import com.loc.benimdunyam.domain.model.CloudAuthResult
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Giriş yapılmadıkça hiçbir hesap açmaz ve hiçbir veriyi buluta yüklemez. */
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

    override fun signOut() {
        // Yalnızca Firebase oturumunu kapatır; yerel anılar korunur.
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
    } catch (error: FirebaseAuthUserCollisionException) {
        CloudAuthResult.Failure(CloudAuthError.EMAIL_ALREADY_IN_USE)
    } catch (error: FirebaseAuthWeakPasswordException) {
        CloudAuthResult.Failure(CloudAuthError.WEAK_PASSWORD)
    } catch (error: FirebaseAuthInvalidCredentialsException) {
        CloudAuthResult.Failure(CloudAuthError.INVALID_EMAIL_OR_PASSWORD)
    } catch (error: FirebaseTooManyRequestsException) {
        CloudAuthResult.Failure(CloudAuthError.RATE_LIMITED)
    } catch (error: FirebaseNetworkException) {
        CloudAuthResult.Failure(CloudAuthError.NETWORK)
    } catch (error: Exception) {
        CloudAuthResult.Failure(CloudAuthError.UNKNOWN)
    }
}
