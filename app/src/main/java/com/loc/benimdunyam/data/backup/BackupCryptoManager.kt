
package com.loc.benimdunyam.data.backup

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class BackupCryptoException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * AES-256-GCM ile ZIP arşivlerini şifreler ve çözer.
 *
 * Format:
 * Header:
 *   MAGIC (8 byte)
 *   VERSION (Int)
 *   KDF_ID (Int)
 *   ITERATIONS (Int)
 *   CHUNK_SIZE (Int)
 *   SALT (16 byte)
 *   NONCE_PREFIX (8 byte)
 *
 * Records:
 *   plaintextLength (Int)
 *   ciphertext + GCM tag
 *
 * Son kayıt 0 uzunluklu, kimliği doğrulanmış
 * bir bitiş kaydıdır.
 */
@Singleton
class BackupCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {

        const val MAGIC = "BDGCM001"
        const val FORMAT_VERSION = 1

        const val KDF_SHA256 = 1
        const val KDF_SHA1 = 2

        const val SHA256_ITERATIONS = 600_000
        const val SHA1_ITERATIONS = 1_400_000

        const val KEY_LENGTH_BITS = 256
        const val SALT_SIZE = 16
        const val NONCE_PREFIX_SIZE = 8

        const val CHUNK_SIZE = 64 * 1024
        const val TAG_SIZE_BYTES = 16
        const val TAG_SIZE_BITS = 128

        const val HEADER_SIZE =
            8 + 4 + 4 + 4 + 4 + SALT_SIZE + NONCE_PREFIX_SIZE

        const val MIN_PASSWORD_LENGTH = 12

        const val MAX_ARCHIVE_BYTES =
            600L * 1024L * 1024L

        const val MAX_ENCRYPTED_BYTES =
            650L * 1024L * 1024L
    }

    private val secureRandom = SecureRandom()

    private data class Header(
        val rawBytes: ByteArray,
        val kdfId: Int,
        val iterations: Int,
        val salt: ByteArray,
        val noncePrefix: ByteArray
    )

    /**
     * Şifresiz ZIP dosyasını geçici şifreli
     * .bdbackup dosyasına dönüştürür.
     *
     * Parola CharArray'i işlem sonunda temizlenir.
     *
     * Dönen dosyanın temizliği çağıran sınıfa aittir.
     */
    suspend fun encryptArchive(
        zipFile: File,
        passphrase: CharArray
    ): File = withContext(Dispatchers.IO) {

        var outputFile: File? = null

        try {
            require(passphrase.size >= MIN_PASSWORD_LENGTH) {
                "Yedek parolası en az 12 karakter olmalıdır."
            }

            require(
                zipFile.isFile &&
                        zipFile.length() in 1..MAX_ARCHIVE_BYTES
            ) {
                "Şifrelenecek ZIP bulunamadı veya çok büyük."
            }

            val header = createHeader()
            val key = deriveKey(passphrase, header)

            val destination = createTemporaryFile(".bdbackup")
            outputFile = destination

            DataOutputStream(
                BufferedOutputStream(destination.outputStream())
            ).use { output ->

                output.write(header.rawBytes)

                zipFile.inputStream().buffered().use { input ->

                    val buffer = ByteArray(CHUNK_SIZE)
                    var sequence = 0

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val count = input.read(buffer)

                        if (count == -1) break
                        if (count == 0) continue

                        val plain = buffer.copyOf(count)

                        val encrypted = encryptChunk(
                            key = key,
                            header = header,
                            sequence = sequence,
                            plain = plain
                        )

                        output.writeInt(count)
                        output.write(encrypted)

                        sequence++
                    }

                    // Kimliği doğrulanmış bitiş kaydı.
                    val finalRecord = encryptChunk(
                        key = key,
                        header = header,
                        sequence = sequence,
                        plain = ByteArray(0)
                    )

                    output.writeInt(0)
                    output.write(finalRecord)
                }
            }

            require(destination.length() > HEADER_SIZE) {
                "Şifreli yedek oluşturulamadı."
            }

            destination

        } catch (exception: Exception) {
            outputFile?.delete()
            throw exception
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Şifreli .bdbackup dosyasını çözer.
     *
     * Doğrulanmış ZIP'i uygulamanın özel
     * geçici alanına yazar.
     *
     * Parola yanlışsa veya veri bozulmuşsa
     * geçici çıktı silinir.
     *
     * Dönen ZIP henüz uygulamaya geri yüklenmez.
     */
    suspend fun decryptArchive(
        encryptedFile: File,
        passphrase: CharArray
    ): File = withContext(Dispatchers.IO) {

        var outputFile: File? = null

        try {
            require(passphrase.isNotEmpty()) {
                "Yedek parolası boş olamaz."
            }

            require(
                encryptedFile.isFile &&
                        encryptedFile.length() in
                        (HEADER_SIZE + TAG_SIZE_BYTES + 4L)..MAX_ENCRYPTED_BYTES
            ) {
                "Şifreli yedek dosyası bulunamadı veya geçersiz."
            }

            val destination = createTemporaryFile(".zip")
            outputFile = destination

            DataInputStream(
                BufferedInputStream(encryptedFile.inputStream())
            ).use { input ->

                val header = readHeader(input)
                val key = deriveKey(passphrase, header)

                BufferedOutputStream(
                    destination.outputStream()
                ).use { output ->

                    var sequence = 0
                    var totalBytes = 0L

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val plainLength = input.readInt()

                        if (plainLength !in 0..CHUNK_SIZE) {
                            throw BackupCryptoException(
                                "Şifreli yedekte geçersiz parça boyutu."
                            )
                        }

                        val encryptedBytes = ByteArray(
                            plainLength + TAG_SIZE_BYTES
                        )

                        input.readFully(encryptedBytes)

                        val plain = decryptChunk(
                            key = key,
                            header = header,
                            sequence = sequence,
                            plainLength = plainLength,
                            encrypted = encryptedBytes
                        )

                        // Bitiş kaydını doğruladıktan sonra
                        // dosyanın sonunda başka veri olmamalı.
                        if (plainLength == 0) {
                            if (input.read() != -1) {
                                throw BackupCryptoException(
                                    "Yedek dosyasında fazladan veri var."
                                )
                            }
                            break
                        }

                        totalBytes += plain.size

                        if (totalBytes > MAX_ARCHIVE_BYTES) {
                            throw BackupCryptoException(
                                "Açılan yedek boyut sınırını aşıyor."
                            )
                        }

                        output.write(plain)

                        sequence++
                    }
                }
            }

            if (destination.length() == 0L) {
                throw BackupCryptoException(
                    "Çözülen ZIP dosyası boş."
                )
            }

            destination

        } catch (exception: Exception) {
            outputFile?.delete()
            throw exception
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Yeni yedeğe özgü salt ve nonce öneki
     * oluşturur. KDF seçimi başlığa yazılır.
     */
    private fun createHeader(): Header {

        val salt = ByteArray(SALT_SIZE).also {
            secureRandom.nextBytes(it)
        }

        val noncePrefix = ByteArray(NONCE_PREFIX_SIZE).also {
            secureRandom.nextBytes(it)
        }

        val isModern = Build.VERSION.SDK_INT >= 26

        val kdfId = if (isModern) {
            KDF_SHA256
        } else {
            KDF_SHA1
        }

        val iterations = if (isModern) {
            SHA256_ITERATIONS
        } else {
            SHA1_ITERATIONS
        }

        val byteStream = ByteArrayOutputStream()

        DataOutputStream(byteStream).use { output ->
            output.write(MAGIC.toByteArray(Charsets.US_ASCII))
            output.writeInt(FORMAT_VERSION)
            output.writeInt(kdfId)
            output.writeInt(iterations)
            output.writeInt(CHUNK_SIZE)
            output.write(salt)
            output.write(noncePrefix)
        }

        return Header(
            rawBytes = byteStream.toByteArray(),
            kdfId = kdfId,
            iterations = iterations,
            salt = salt,
            noncePrefix = noncePrefix
        )
    }

    /**
     * Yedek formatının sabit uzunluklu başlığını okur.
     */
    private fun readHeader(
        input: DataInputStream
    ): Header {

        val raw = ByteArray(HEADER_SIZE)
        input.readFully(raw)

        val reader = DataInputStream(
            ByteArrayInputStream(raw)
        )

        val magicBytes = ByteArray(8)
        reader.readFully(magicBytes)

        val magic = String(
            magicBytes,
            Charsets.US_ASCII
        )

        if (magic != MAGIC) {
            throw BackupCryptoException(
                "Bu dosya desteklenen bir Benim Dünyam yedeği değil."
            )
        }

        val version = reader.readInt()
        val kdfId = reader.readInt()
        val iterations = reader.readInt()
        val chunkSize = reader.readInt()

        if (version != FORMAT_VERSION) {
            throw BackupCryptoException(
                "Desteklenmeyen şifreli yedek sürümü."
            )
        }

        val validKdf =
            (kdfId == KDF_SHA256 &&
                    iterations == SHA256_ITERATIONS) ||
                    (kdfId == KDF_SHA1 &&
                            iterations == SHA1_ITERATIONS)

        if (!validKdf || chunkSize != CHUNK_SIZE) {
            throw BackupCryptoException(
                "Yedek şifreleme parametreleri geçersiz."
            )
        }

        val salt = ByteArray(SALT_SIZE)
        reader.readFully(salt)

        val noncePrefix = ByteArray(NONCE_PREFIX_SIZE)
        reader.readFully(noncePrefix)

        return Header(
            rawBytes = raw,
            kdfId = kdfId,
            iterations = iterations,
            salt = salt,
            noncePrefix = noncePrefix
        )
    }

    /**
     * PBKDF2 ile 256-bit anahtar üretir.
     * Algoritma dosya başlığından belirlenir.
     */
    private fun deriveKey(
        passphrase: CharArray,
        header: Header
    ): SecretKeySpec {

        val algorithm = when (header.kdfId) {
            KDF_SHA256 -> "PBKDF2WithHmacSHA256"
            KDF_SHA1 -> "PBKDF2WithHmacSHA1"
            else -> throw BackupCryptoException(
                "Bilinmeyen anahtar türetme algoritması."
            )
        }

        val spec = PBEKeySpec(
            passphrase,
            header.salt,
            header.iterations,
            KEY_LENGTH_BITS
        )

        try {
            val factory = SecretKeyFactory.getInstance(
                algorithm
            )

            val keyBytes = factory.generateSecret(spec).encoded

            try {
                return SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }

        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Bir veri parçasını şifreler.
     * Her parça benzersiz bir nonce kullanır.
     */
    private fun encryptChunk(
        key: SecretKeySpec,
        header: Header,
        sequence: Int,
        plain: ByteArray
    ): ByteArray {

        val cipher = createCipher(
            mode = Cipher.ENCRYPT_MODE,
            key = key,
            header = header,
            sequence = sequence,
            plainLength = plain.size
        )

        return cipher.doFinal(plain)
    }

    /**
     * Bir veri parçasını doğrular ve çözer.
     *
     * GCM doğrulaması başarısızsa düz metin
     * dışarı verilmez.
     */
    private fun decryptChunk(
        key: SecretKeySpec,
        header: Header,
        sequence: Int,
        plainLength: Int,
        encrypted: ByteArray
    ): ByteArray {

        try {
            val cipher = createCipher(
                mode = Cipher.DECRYPT_MODE,
                key = key,
                header = header,
                sequence = sequence,
                plainLength = plainLength
            )

            val plain = cipher.doFinal(encrypted)

            if (plain.size != plainLength) {
                throw BackupCryptoException(
                    "Çözülen veri boyutu uyuşmuyor."
                )
            }

            return plain

        } catch (exception: GeneralSecurityException) {
            throw BackupCryptoException(
                "Yedek parolası yanlış veya dosya bozulmuş.",
                exception
            )
        }
    }

    /**
     * Header, parça sırası ve parça boyutu
     * GCM doğrulamasına bağlanır.
     */
    private fun createCipher(
        mode: Int,
        key: SecretKeySpec,
        header: Header,
        sequence: Int,
        plainLength: Int
    ): Cipher {

        require(sequence >= 0) {
            "Şifreleme parça sayısı sınırı aşıldı."
        }

        val nonce = ByteBuffer.allocate(12)
            .put(header.noncePrefix)
            .putInt(sequence)
            .array()

        val cipher = Cipher.getInstance(
            "AES/GCM/NoPadding"
        )

        cipher.init(
            mode,
            key,
            GCMParameterSpec(TAG_SIZE_BITS, nonce)
        )

        // Başlık değiştirilemez.
        cipher.updateAAD(header.rawBytes)

        // Parça sırası ve boyutu değiştirilemez.
        val recordMetadata = ByteBuffer.allocate(8)
            .putInt(sequence)
            .putInt(plainLength)
            .array()

        cipher.updateAAD(recordMetadata)

        return cipher
    }

    /**
     * Şifresiz ve şifreli geçici çıktılar
     * Auto Backup dışında saklanır.
     */
    private fun createTemporaryFile(
        extension: String
    ): File {

        val directory = File(
            context.noBackupFilesDir,
            "backup_staging"
        )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Geçici yedek klasörü oluşturulamadı."
            )
        }

        return File.createTempFile(
            "bd_",
            extension,
            directory
        )
    }
}
