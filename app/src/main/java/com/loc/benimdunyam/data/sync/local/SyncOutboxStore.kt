package com.loc.benimdunyam.data.sync.local

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.domain.model.AccountMigrationRules
import com.loc.benimdunyam.domain.sync.SyncQueuePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aktif hesabın kalıcı senkronizasyon kuyruğuna erişir.
 *
 * Hesap seçimi çalışma süreci boyunca sabit olduğundan bu veritabanı da
 * yalnızca mevcut hesaba ait olabilir. Hesap değişimi için uygulama yeniden
 * başlamalıdır; kilitli oturumda veritabanını açmayız.
 */
@Singleton
class SyncOutboxStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val session: ActiveWorkspaceSession
) {
    private var openDatabase: SyncOutboxDatabase? = null
    private var openUid: String? = null

    fun isAccountReady(uid: String?): Boolean =
        SyncQueuePolicy.canAccess(uid, auth.currentUser?.uid, session.isReadyFor(uid)) &&
                !session.needsRestart()

    /** Dosya ismine açık Firebase UID yazılmaz; hesap anahtarının özeti kullanılır. */
    fun databaseFileName(uid: String): String =
        "sync_outbox_${AccountMigrationRules.folderKey(uid).take(24)}.db"

    @Synchronized
    private fun dao(uid: String): SyncTaskDao {
        check(isAccountReady(uid)) { "Aktif hesap ve yerel arşiv eşleşmiyor." }
        if (openDatabase == null) {
            openDatabase = Room.databaseBuilder(
                context, SyncOutboxDatabase::class.java, databaseFileName(uid)
            ).build()
            openUid = uid
        }
        check(openUid == uid) { "Hesap değişti: eski kuyruğa erişim kapatıldı." }
        return requireNotNull(openDatabase).taskDao()
    }

    /**
     * Faz 2F.1'de tek yazılan görev bir tanılama kaydıdır.
     * Gerçek günlük UPSERT/DELETE kuyruğunu 2F.2'de, çakışma/silme kurallarıyla
     * birlikte ekleyeceğiz; bu sürümde hiçbir günlük olayı yakalanmıyor.
     */
    suspend fun enqueueDiagnostic(uid: String) {
        dao(uid).insert(
            SyncTaskEntity(
                id = UUID.randomUUID().toString(),
                ownerUid = uid,
                taskType = "DIAGNOSTIC",
                state = "PENDING",
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun counts(uid: String): Pair<Int, Int> {
        val taskDao = dao(uid)
        return taskDao.pendingCount(uid) to taskDao.doneCount(uid)
    }

    /** Çalışmadan hemen önce ve sonra hesap yetkisi tekrar kontrol edilir. */
    suspend fun completeDiagnostics(uid: String): Int {
        check(isAccountReady(uid)) { "İş yürürken hesap değişti." }
        val taskDao = dao(uid)
        check(isAccountReady(uid)) { "İş yürürken oturum değişti." }
        return taskDao.completeDiagnostics(uid, System.currentTimeMillis())
    }
}
