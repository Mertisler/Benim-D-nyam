package com.loc.benimdunyam.domain.usecase.backup

import android.net.Uri
import com.loc.benimdunyam.data.backup.BackupDocumentStore
import com.loc.benimdunyam.data.backup.BackupFileVerifier
import com.loc.benimdunyam.data.backup.BackupVerificationResult
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.backup.EncryptedBackupManager
import java.io.File
import javax.inject.Inject

/**
 * Altı tabloyu ve fotoğrafları birlikte yakalayan, şifreleyen,
 * seçilen belgeye kaydeden ve tekrar açıp doğrulayan iş akışı.
 *
 * Sadece ZIP'in ve şifreli geçici çıktının hazırlanması sırasında
 * ortak yazma kilidi tutulur. Böylece diğer KORUNAN yazma işlemleri
 * fotoğrafları yakalama sırasında değiştiremez.
 *
 * Bu UseCase, geri yüklemenin özel kilidi içinden de çağrılabilir:
 * güncellenmiş BackupWriteCoordinator aynı coroutine bağlamındaki
 * iç içe çağrıları destekler.
 */
class CreateBackupUseCase @Inject constructor(
    private val encryptedBackupManager: EncryptedBackupManager,
    private val documentStore: BackupDocumentStore,
    private val backupFileVerifier: BackupFileVerifier,
    private val writeCoordinator: BackupWriteCoordinator
) {

    suspend operator fun invoke(
        destinationUri: Uri,
        passphrase: CharArray
    ): BackupVerificationResult {
        var encryptedFile: File? = null

        try {
            require(passphrase.size >= 12) {
                "Yedek parolası en az 12 karakter olmalıdır."
            }

            // DB + fotoğraf dosyası snapshot'ı tek kilitte hazırlanır.
            // Henüz coordinator kullanmayan yazma noktaları bu kilitten
            // etkilenmez; onların da entegrasyonu ayrıca tamamlanmalıdır.
            encryptedFile = writeCoordinator.withRegularWrite {
                encryptedBackupManager.createEncryptedBackup(
                    passphrase = passphrase.copyOf()
                )
            }

            val completedFile = requireNotNull(encryptedFile) {
                "Şifreli yedek dosyası oluşturulamadı."
            }

            // Kaydetme ve tekrar okuma artık mevcut DB/fotoğrafları
            // kullanmaz; kilidi gereksiz yere tutmuyoruz.
            documentStore.saveEncryptedBackup(
                sourceFile = completedFile,
                destinationUri = destinationUri
            )

            return backupFileVerifier.verify(
                uri = destinationUri,
                passphrase = passphrase.copyOf()
            )

        } finally {
            encryptedFile?.delete()
            passphrase.fill('\u0000')
        }
    }
}
