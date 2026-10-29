package com.loc.benimdunyam.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 16

    fun encrypt(plainText: String, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_SIZE).apply { random.nextBytes(this) }

        val keySpec = PBEKeySpec(passphrase, salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encryptedData = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Salt + IV + EncryptedData birleştirilir
        return salt + iv + encryptedData
    }

    fun decrypt(encryptedBytes: ByteArray, passphrase: CharArray): String {
        if (encryptedBytes.size < SALT_SIZE + IV_SIZE) {
            throw IllegalArgumentException("Geçersiz şifreli veri boyutu.")
        }

        val salt = encryptedBytes.copyOfRange(0, SALT_SIZE)
        val iv = encryptedBytes.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val cipherText = encryptedBytes.copyOfRange(SALT_SIZE + IV_SIZE, encryptedBytes.size)

        val keySpec = PBEKeySpec(passphrase, salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        val decryptedData = cipher.doFinal(cipherText)

        return String(decryptedData, Charsets.UTF_8)
    }
}