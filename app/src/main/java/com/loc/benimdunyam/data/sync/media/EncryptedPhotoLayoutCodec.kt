package com.loc.benimdunyam.data.sync.media

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fotoğraf dizilimi / kapak seçimi küçük JSON verileridir.
 * Bunlar ayrı anahtarlı AES-GCM zarfında saklanır; Firebase'de sıralama ve dosya adları görünmez.
 * Mevcut görsel şifreleme ve .bdbackup biçimlerinden bağımsızdır.
 */
@Singleton
class EncryptedPhotoLayoutCodec @Inject constructor() {
    private val random = SecureRandom()
    private val magic = byteArrayOf(66, 68, 76, 50) // "BDL2"
    private val aad = "benimdunyam-photo-layout-v2".toByteArray(Charsets.UTF_8)

    fun encrypt(json: String, password: CharArray): String {
        require(password.size in 12..128) { "Güvenli eşitleme şifresi gerekli." }
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..4096) { "Fotoğraf düzeni boyutu geçersiz." }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            val body = cipher.doFinal(bytes)
            return Base64.encodeToString(magic + salt + iv + body, Base64.NO_WRAP)
        } finally {
            bytes.fill(0)
        }
    }

    fun decrypt(encoded: String, password: CharArray): String {
        require(encoded.length in 64..10000) { "Şifreli düzen boyutu geçersiz." }
        val input = Base64.decode(encoded, Base64.NO_WRAP)
        require(input.size in 48..7500 && input.take(4).toByteArray().contentEquals(magic)) {
            "Fotoğraf düzeni biçimi geçersiz."
        }
        val key = derive(password, input.copyOfRange(4, 20))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, input.copyOfRange(20, 32)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(input.copyOfRange(32, input.size))
        try {
            require(plain.size in 1..4096)
            return plain.toString(Charsets.UTF_8)
        } finally {
            plain.fill(0)
            input.fill(0)
        }
    }

    /** minSdk 24'te çalışan PBKDF2 türetmesi; anahtar ve parola kopyalanmaz. */
    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 250_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }
}
