
package com.loc.benimdunyam.data.backup

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tam şifreli yedek oluşturma sürecini yönetir.
 *
 * 1. Room ve görsellerden ZIP üretir.
 * 2. ZIP'i AES-GCM ile şifreler.
 * 3. Şifresiz geçici ZIP'i siler.
 *
 * Dönen şifreli dosyayı kullanıp silmek
 * çağıran sınıfın sorumluluğundadır.
 */
@Singleton
class EncryptedBackupManager @Inject constructor(
    private val archiveManager: BackupArchiveManager,
    private val cryptoManager: BackupCryptoManager
) {

    suspend fun createEncryptedBackup(
        passphrase: CharArray
    ): File {

        var temporaryZip: File? = null

        try {
            temporaryZip =
                archiveManager.createTemporaryArchive()

            return cryptoManager.encryptArchive(
                zipFile = temporaryZip,
                passphrase = passphrase
            )

        } finally {

            // Şifresiz ZIP kalıcı olarak tutulmaz.
            temporaryZip?.delete()

            // Hassas parola karakterlerini temizle.
            passphrase.fill('\u0000')
        }
    }
}
