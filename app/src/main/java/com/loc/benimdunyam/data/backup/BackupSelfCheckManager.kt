package com.loc.benimdunyam.data.backup

import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uygulama içindeki yazma ve geri yükleme altyapısını değiştirmeden
 * şifreli yedek oluşturma -> çözme -> ZIP/içerik doğrulama -> geçici
 * Room veritabanına yükleme zincirini dener.
 *
 * ÖNEMLİ: Bu işlem gerçek Room veritabanına geri yükleme yapmaz.
 * Android SAF üzerinden kullanıcı tarafından kaydedilen dosyayı da
 * sınamaz. Bunlar ayrı uçtan uca senaryolar olarak kalır.
 */
data class BackupSelfCheckReport(
    val journalCount: Int,
    val personCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val mentionCount: Int,
    val specialMemoryCount: Int,
    val imageCount: Int,
    val encryptedBytes: Long,
    val elapsedMillis: Long,
    val checkedAtMillis: Long
)

@Singleton
class BackupSelfCheckManager @Inject constructor(
    private val encryptedBackupManager: EncryptedBackupManager,
    private val cryptoManager: BackupCryptoManager,
    private val archiveReader: BackupArchiveReader,
    private val dryRunVerifier: BackupDryRunVerifier,
    private val writeCoordinator: BackupWriteCoordinator
) {

    suspend fun runSelfCheck(): BackupSelfCheckReport =
        withContext(Dispatchers.IO) {
            // Örnek veri okunurken ve şifreli arşiv hazırlanırken,
            // koordinatöre dahil diğer kayıtların değişmesini önler.
            writeCoordinator.withRegularWrite {
                val startedAt = System.currentTimeMillis()
                val password = createEphemeralPassword()
                var encryptedFile: File? = null
                var decryptedZip: File? = null

                try {
                    // 1. Room + fotoğraf verilerinden gerçek şifreli
                    // .bdbackup dosyası oluştur. Parola bellekte üretilir.
                    val encryptionPassword = password.copyOf()
                    val encrypted = try {
                        encryptedBackupManager.createEncryptedBackup(
                            passphrase = encryptionPassword
                        )
                    } finally {
                        encryptionPassword.fill('\u0000')
                    }
                    encryptedFile = encrypted

                    require(encrypted.isFile && encrypted.length() > 0L) {
                        "Şifreli yedek dosyası oluşturulamadı."
                    }
                    val encryptedSize = encrypted.length()

                    // 2. Yedeği, oluşturulduğu dosyadan AES-GCM
                    // doğrulamasıyla tekrar çöz.
                    val decryptionPassword = password.copyOf()
                    val decrypted = try {
                        cryptoManager.decryptArchive(
                            encryptedFile = encrypted,
                            passphrase = decryptionPassword
                        )
                    } finally {
                        decryptionPassword.fill('\u0000')
                    }
                    decryptedZip = decrypted

                    // 3. ZIP girdileri, manifest, JSON, görsel
                    // uzunlukları ve SHA-256 değerleri incelenir.
                    archiveReader.readAndPrepare(decrypted).use { prepared ->
                        // 4. Kalıcı veritabanına dokunmadan gerçek
                        // Room şemasının bellek içi kopyasına aktar.
                        val dryRun = dryRunVerifier.verify(prepared)

                        BackupSelfCheckReport(
                            journalCount = dryRun.journalCount,
                            personCount = dryRun.personCount,
                            seasonCount = dryRun.seasonCount,
                            episodeCount = dryRun.episodeCount,
                            mentionCount = dryRun.mentionCount,
                            specialMemoryCount = dryRun.specialMemoryCount,
                            imageCount = prepared.manifest.mediaFiles.size,
                            encryptedBytes = encryptedSize,
                            elapsedMillis =
                                System.currentTimeMillis() - startedAt,
                            checkedAtMillis = System.currentTimeMillis()
                        )
                    }
                } finally {
                    // Başarılı/başarısız/iptal fark etmeksizin;
                    // parola ve oluşturulan geçici dosyalar bırakılmaz.
                    password.fill('\u0000')
                    decryptedZip?.delete()
                    encryptedFile?.delete()
                }
            }
        }

    /**
     * Kullanıcıdan parola istemeyen prova için güçlü geçici parola.
     * Parola hiçbir yere kaydedilmez, işlem sonrasında sıfırlanır.
     * Android API 24 ile uyumludur (java.util.Base64 gerekmez).
     */
    private fun createEphemeralPassword(): CharArray {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val hex = "0123456789abcdef"
        val result = CharArray(bytes.size * 2)
        try {
            bytes.forEachIndexed { index, value ->
                val unsigned = value.toInt() and 0xff
                result[index * 2] = hex[unsigned ushr 4]
                result[index * 2 + 1] = hex[unsigned and 0x0f]
            }
        } finally {
            bytes.fill(0)
        }
        return result
    }
}
