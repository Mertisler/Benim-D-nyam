
package com.loc.benimdunyam.data.backup

import android.net.Uri
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

data class BackupVerificationResult(
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

/**
 * Kaydedilen şifreli yedeğin:
 *
 * - AES-GCM
 * - ZIP
 * - Manifest
 * - JSON
 * - Fotoğraf
 * - Room aktarılabilirliği
 *
 * kontrollerini gerçekleştirir.
 *
 * Gerçek Room verilerini değiştirmez.
 */
class BackupFileVerifier @Inject constructor(
    private val documentStore: BackupDocumentStore,
    private val cryptoManager: BackupCryptoManager,
    private val archiveReader: BackupArchiveReader,
    private val dryRunVerifier: BackupDryRunVerifier
) {

    suspend fun verify(
        uri: Uri,
        passphrase: CharArray
    ): BackupVerificationResult {

        var encryptedCopy: File? = null
        var decryptedZip: File? = null

        try {
            // 1. Kaydedilen gerçek yedek dosyasını oku.
            val staged = documentStore
                .stageEncryptedBackup(uri)

            encryptedCopy = staged.file

            // 2. Şifreyi çöz.
            decryptedZip = cryptoManager.decryptArchive(
                encryptedFile = staged.file,
                passphrase = passphrase
            )

            // 3. ZIP, JSON ve görselleri doğrula.
            return archiveReader
                .readAndPrepare(decryptedZip)
                .use { prepared ->

                    // 4. Bellekteki Room'a aktarılabilir mi?
                    dryRunVerifier.verify(prepared)

                    // 5. Bütün kontroller başarılı.
                    val data = prepared.data

                    BackupVerificationResult(
                        createdAt = data.exportTimestamp,
                        journalCount = data.journals.size,
                        personCount = data.people.size,
                        seasonCount = data.seasons.size,
                        episodeCount = data.episodes.size,
                        mentionCount = data.mentions.size,
                        specialMemoryCount =
                            data.specialMemories.size,
                        imageCount =
                            prepared.manifest.mediaFiles.size,
                        encryptedSha256 = staged.sha256
                    )
                }

        } catch (e: CancellationException) {
            throw e

        } finally {
            decryptedZip?.delete()
            encryptedCopy?.delete()
            passphrase.fill('\u0000')
        }
    }
}
