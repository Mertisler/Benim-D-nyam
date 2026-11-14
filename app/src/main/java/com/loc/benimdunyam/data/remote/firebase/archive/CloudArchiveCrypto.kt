package com.loc.benimdunyam.data.remote.firebase.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bağımsız bulut arşiv şifrelemesi; mevcut .bdbackup / CryptoHelper değiştirilmez.
 * MinSdk 24 uyumluluğu için PBKDF2-HMAC-SHA1 kullanılır (250.000 tur).
 * Verilerin gizliliği ve bütünlüğü AES-256-GCM ile sağlanır.
 * Biçim: magic(6) + salt(16) + nonce(12) + AES-GCM(gzip(JSON)).
 */
internal object CloudArchiveCrypto {
    private val MAGIC = byteArrayOf(66, 68, 67, 76, 68, 49) // "BDCLD1"
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 250_000
    private const val MAX_DECOMPRESSED_BYTES = 40 * 1024 * 1024

    fun encrypt(json: String, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Bulut arşiv şifresi en az 12 karakter olmalı." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(passphrase, salt)
        val plain = json.toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_DECOMPRESSED_BYTES) {
            "Arşiv metinleri bu sürümün boyut sınırını aşıyor."
        }
        val zipped = try { gzip(plain) } finally { plain.fill(0) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD(MAGIC)
            val encrypted = cipher.doFinal(zipped)
            return ByteArrayOutputStream().apply {
                write(MAGIC)
                write(salt)
                write(nonce)
                write(encrypted)
            }.toByteArray()
        } finally {
            zipped.fill(0)
        }
    }

    fun decrypt(payload: ByteArray, passphrase: CharArray): String {
        val headerSize = MAGIC.size + SALT_BYTES + NONCE_BYTES
        require(payload.size > headerSize + 16) { "Bulut arşiv verisi eksik." }
        require(payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Desteklenmeyen bulut arşiv biçimi."
        }
        val salt = payload.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val nonce = payload.copyOfRange(MAGIC.size + SALT_BYTES, headerSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        val zipped = cipher.doFinal(payload.copyOfRange(headerSize, payload.size))
        try {
            return ungzipWithLimit(zipped).toString(Charsets.UTF_8)
        } finally {
            zipped.fill(0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).encoded
            return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(input: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(input) }
        output.toByteArray()
    }

    private fun ungzipWithLimit(zipped: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(zipped)).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val bytes = input.read(buffer)
                    if (bytes == -1) break
                    total += bytes
                    require(total <= MAX_DECOMPRESSED_BYTES) { "Bulut arşivi boyut sınırını aştı." }
                    output.write(buffer, 0, bytes)
                }
                output.toByteArray()
            }
        }
}
