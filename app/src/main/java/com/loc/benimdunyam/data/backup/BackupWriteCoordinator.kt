package com.loc.benimdunyam.data.backup

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Geri yükleme ve bu koordinatörü kullanan normal yazma işlemlerini sıralar.
 *
 * İç içe UseCase çağrıları aynı kilit bağlamını paylaşır. Bağlam başka bir
 * coroutine'e taşınırsa, dış işlem bittikten sonra eski izin geçerli olmaz.
 *
 * DİKKAT: Bu izin aktifken alt coroutine başlatılması, onun da kilit altında
 * işlem yapmasına izin verebilir. Bu yüzden birden çok yazmayı paralel
 * başlatmayın; tek işlem içinde suspend çağrılarını sıralı yürütün.
 */
@Singleton
class BackupWriteCoordinator @Inject constructor() {

    private val mutex = Mutex()
    private val _isRestoring = MutableStateFlow(false)

    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private class WriteLease(
        val coordinator: BackupWriteCoordinator,
        val active: AtomicBoolean = AtomicBoolean(true)
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<WriteLease>
    }

    suspend fun <T> withRegularWrite(
        action: suspend () -> T
    ): T {
        val inherited = currentCoroutineContext()[WriteLease]

        // Yalnızca aktif kilidin içindeki çağrılar yeniden girişli olabilir.
        if (inherited?.coordinator === this && inherited.active.get()) {
            return action()
        }

        return mutex.withLock {
            val lease = WriteLease(this@BackupWriteCoordinator)
            try {
                withContext(lease) {
                    action()
                }
            } finally {
                // Eski coroutine context'i daha sonra yeniden kullanılamaz.
                lease.active.set(false)
            }
        }
    }

    suspend fun <T> withExclusiveRestore(
        action: suspend () -> T
    ): T {
        val inherited = currentCoroutineContext()[WriteLease]
        check(inherited?.coordinator !== this || !inherited.active.get()) {
            "Geri yükleme korumalı bir yazma işleminin içinden başlatılamaz."
        }

        return mutex.withLock {
            val lease = WriteLease(this@BackupWriteCoordinator)
            _isRestoring.value = true
            try {
                withContext(lease) {
                    action()
                }
            } finally {
                lease.active.set(false)
                _isRestoring.value = false
            }
        }
    }
}
