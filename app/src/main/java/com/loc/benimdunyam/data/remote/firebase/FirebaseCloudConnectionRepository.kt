package com.loc.benimdunyam.data.remote.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.domain.model.CloudConnectionStatus
import com.loc.benimdunyam.domain.repository.CloudConnectionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Sadece özel test belgesini sunucudan GET eder; hiç yazma işlemi yapmaz. */
class FirebaseCloudConnectionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : CloudConnectionRepository {

    override suspend fun checkReadAccess(): CloudConnectionStatus {
        val uid = auth.currentUser?.uid ?: return CloudConnectionStatus.SignInRequired
        return try {
            // Belgenin var olması gerekmez; sunucu yanıtı ve izin kontrolü yeterlidir.
            firestore.collection("users")
                .document(uid)
                .collection("_meta")
                .document("connection")
                .get(Source.SERVER)
                .await()
            CloudConnectionStatus.Reachable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: FirebaseFirestoreException) {
            when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> CloudConnectionStatus.PermissionDenied
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CloudConnectionStatus.NetworkUnavailable
                else -> CloudConnectionStatus.UnknownError
            }
        } catch (error: FirebaseNetworkException) {
            CloudConnectionStatus.NetworkUnavailable
        } catch (error: Exception) {
            CloudConnectionStatus.UnknownError
        }
    }
}
