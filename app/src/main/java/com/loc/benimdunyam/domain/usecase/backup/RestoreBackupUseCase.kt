package com.loc.benimdunyam.domain.usecase.backup

import android.net.Uri
import com.loc.benimdunyam.data.backup.BackupArchiveReader
import com.loc.benimdunyam.data.backup.BackupCryptoManager
import com.loc.benimdunyam.data.backup.BackupDocumentStore
import com.loc.benimdunyam.data.backup.BackupDryRunVerifier
import com.loc.benimdunyam.data.backup.BackupFileVerifier
import com.loc.benimdunyam.data.backup.BackupRestoreManager
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.backup.RestoreSummary
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BackupPreview(
    val createdAt: Long,
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val imageCount: Int,
    val encryptedSha256: String
)

@Singleton
class RestoreBackupUseCase @Inject constructor(
    private val documentStore: BackupDocumentStore,
    private val cryptoManager: BackupCryptoManager,
    private val archiveReader: BackupArchiveReader,
    private val restoreManager: BackupRestoreManager,
    private val createBackupUseCase: CreateBackupUseCase,
    private val backupFileVerifier: BackupFileVerifier,
    private val dryRunVerifier: BackupDryRunVerifier,
    private val writeCoordinator: BackupWriteCoordinator
) {
    private val operationMutex = Mutex()

    /** Yalnızca okur; mevcut Room verilerini değiştirmez. */
    suspend fun inspect(sourceUri: Uri, passphrase: CharArray): BackupPreview {
        try {
            return operationMutex.withLock {
                val verified = backupFileVerifier.verify(sourceUri, passphrase.copyOf())
                BackupPreview(
                    createdAt = verified.createdAt,
                    journalCount = verified.journalCount,
                    personCount = verified.personCount,
                    seasonCount = verified.seasonCount,
                    episodeCount = verified.episodeCount,
                    mentionCount = verified.mentionCount,
                    specialMemoryCount = verified.specialMemoryCount,
                    imageCount = verified.imageCount,
                    encryptedSha256 = verified.encryptedSha256
                )
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Kaynağı yeniden doğrular, geçici Room denemesi yapar,
     * mevcut verilerin ayrı güvenlik yedeğini oluşturur ve gerçek geri yüklemeyi yapar.
     * Aynı kilidi kullanan repository yazmaları bu işlem sırasında bekler.
     */
    suspend fun restoreWithSafetyBackup(
        sourceUri: Uri,
        recoveryDestinationUri: Uri,
        expectedSha256: String,
        passphrase: CharArray
    ): RestoreSummary {
        try {
            return operationMutex.withLock {
                writeCoordinator.withExclusiveRestore {
                    require(sourceUri != recoveryDestinationUri) {
                        "Kaynak yedek ve güvenlik yedeği aynı belge olamaz."
                    }
                    require(expectedSha256.matches(Regex("[a-f0-9]{64}"))) {
                        "Yedek önizleme özeti geçersiz."
                    }

                    var encryptedFile: File? = null
                    var decryptedZip: File? = null
                    val sourcePassword = passphrase.copyOf()
                    val safetyPassword = passphrase.copyOf()

                    try {
                        val staged = documentStore.stageEncryptedBackup(sourceUri)
                        encryptedFile = staged.file
                        require(staged.sha256 == expectedSha256) {
                            "Kaynak yedek önizlemeden sonra değişmiş."
                        }
                        decryptedZip = cryptoManager.decryptArchive(staged.file, sourcePassword)

                        archiveReader.readAndPrepare(decryptedZip).use { prepared ->
                            // Veri yazılabilir mi? Gerçek Room'a dokunmadan sınanır.
                            dryRunVerifier.verify(prepared)

                            // Güvenlik yedeği başaramazsa restore başlamaz.
                            createBackupUseCase(recoveryDestinationUri, safetyPassword)

                            // Son adım: fotoğraflar + tek Room transaction.
                            restoreManager.restore(prepared)
                        }
                    } finally {
                        decryptedZip?.delete()
                        encryptedFile?.delete()
                        sourcePassword.fill('\u0000')
                        safetyPassword.fill('\u0000')
                    }
                }
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }
}
