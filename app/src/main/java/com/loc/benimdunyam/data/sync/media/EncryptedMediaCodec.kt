package com.loc.benimdunyam.data.sync.media

import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import java.nio.CharBuffer
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Faz 2G.1: Her görselin BİNARY içeriği AES-256-GCM ile şifrelenir.
 * Eski .bdbackup ve metin arşivinin şifreleme biçimi değiştirilmez.
 * Kısmi GCM çıktısı bütünlük doğrulanmadan asla gerçek görsel klasörüne yazılmaz.
 */
@Singleton
class EncryptedMediaCodec @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class MediaInfo(
        val ownerUid: String,
        val kind: String,
        val ownerId: String,
        val fileName: String,
        val mimeType: String,
        val plainSize: Long,
        val plainSha256: String
    ) {
        fun toJson(): ByteArray = JSONObject().apply {
            put("formatVersion", 1)
            put("ownerUid", ownerUid)
            put("kind", kind)
            put("ownerId", ownerId)
            put("fileName", fileName)
            put("mimeType", mimeType)
            put("plainSize", plainSize)
            put("plainSha256", plainSha256)
        }.toString().toByteArray(Charsets.UTF_8)

        companion object {
            fun fromJson(bytes: ByteArray): MediaInfo {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                require(json.getInt("formatVersion") == 1) { "Desteklenmeyen medya biçimi." }
                return MediaInfo(
                    json.getString("ownerUid"), json.getString("kind"),
                    json.getString("ownerId"), json.getString("fileName"),
                    json.getString("mimeType"), json.getLong("plainSize"),
                    json.getString("plainSha256")
                )
            }
        }
    }

    data class Decoded(val info: MediaInfo, val file: File)

    companion object {
        const val MAX_PLAIN_BYTES = 25L * 1024 * 1024
        const val MAX_ENCRYPTED_BYTES = MAX_PLAIN_BYTES + 32 * 1024
        private const val ITERATIONS = 210_000
        private const val META_LIMIT = 4_096
        private const val MAX_PIXELS = 60_000_000L
        private val HEADER = byteArrayOf(66, 68, 77, 49) // BDM1
        private val AAD = "benimdunyam-media-v1".toByteArray(Charsets.UTF_8)
        private val SECURE_RANDOM = SecureRandom()
    }

    /** Parola yalnızca çağrı süresince RAM'dedir; türetilen anahtar baytları temizlenir. */
    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        require(password.size in 12..128) { "Medya şifresi 12–128 karakter olmalı." }
        // Android 24-25'te PBKDF2WithHmacSHA256 SecretKeyFactory bulunmaz.
        // HmacSHA256 (API 23+) ile aynı PBKDF2 blok hesabını standart şekilde yapıyoruz.
        // Böylece minSdk=24 korunur ve tüm cihazlarda AYNI anahtar üretilir.
        val encoded = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
        val passBytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        if (encoded.hasArray()) encoded.array().fill(0)
        val keyBytes = try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(passBytes, "HmacSHA256"))
            val firstBlock = salt + byteArrayOf(0, 0, 0, 1)
            var u = mac.doFinal(firstBlock)
            val derived = u.copyOf() // SHA-256 çıkışı 32 bayt, AES-256 için yeterli.
            repeat(ITERATIONS - 1) {
                val next = mac.doFinal(u)
                for (i in derived.indices) {
                    derived[i] = (derived[i].toInt() xor next[i].toInt()).toByte()
                }
                u.fill(0)
                u = next
            }
            u.fill(0)
            derived
        } finally {
            passBytes.fill(0)
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                updateAAD(AAD)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    /** Şifreleme kaynaktaki dosyaya dokunmaz; çıktı yalnızca cacheDir içindedir. */
    fun encrypt(source: File, info: MediaInfo, password: CharArray): File {
        require(source.isFile && source.length() in 1..MAX_PLAIN_BYTES)
        require(source.length() == info.plainSize)
        val meta = info.toJson()
        require(meta.size in 1..META_LIMIT)
        val salt = ByteArray(16).also(SECURE_RANDOM::nextBytes)
        val iv = ByteArray(12).also(SECURE_RANDOM::nextBytes)
        val output = File.createTempFile("bd_enc_", ".part", context.cacheDir)
        try {
            FileOutputStream(output).use { raw ->
                raw.write(HEADER)
                raw.write(salt)
                raw.write(iv)
                CipherOutputStream(BufferedOutputStream(raw), cipher(Cipher.ENCRYPT_MODE, password, salt, iv))
                    .use { encrypted ->
                        DataOutputStream(encrypted).use { payload ->
                            payload.writeInt(meta.size)
                            payload.write(meta)
                            FileInputStream(source).use { it.copyTo(payload, 32 * 1024) }
                        }
                    }
            }
            require(output.length() in 1..MAX_ENCRYPTED_BYTES)
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    /**
     * AES-GCM doğrulamasını doğrudan Cipher.doFinal ile yaparız.
     * CipherInputStream bazı sağlayıcılarda doğrulama hatalarını yutabildiğinden kullanılmaz.
     */
    fun decrypt(encrypted: File, password: CharArray, expectedUid: String): Decoded {
        require(encrypted.isFile && encrypted.length() in 49..MAX_ENCRYPTED_BYTES) {
            "Bulut medya boyutu geçersiz."
        }
        val intermediate = File.createTempFile("bd_plain_payload_", ".part", context.cacheDir)
        val plain = File.createTempFile("bd_plain_media_", ".part", context.cacheDir)
        try {
            FileInputStream(encrypted).use { input ->
                val header = ByteArray(4)
                require(input.read(header) == 4 && header.contentEquals(HEADER)) {
                    "Şifreli medya başlığı geçersiz."
                }
                val salt = ByteArray(16)
                val iv = ByteArray(12)
                DataInputStream(input).readFully(salt)
                DataInputStream(input).readFully(iv)
                val decryptor = cipher(Cipher.DECRYPT_MODE, password, salt, iv)
                FileOutputStream(intermediate).use { out ->
                    val chunk = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        val bytes = decryptor.update(chunk, 0, read)
                        if (bytes != null && bytes.isNotEmpty()) out.write(bytes)
                        require(intermediate.length() <= MAX_PLAIN_BYTES + META_LIMIT + 8) {
                            "Şifreli medya sınırı aşıldı."
                        }
                    }
                    // Etiket doğrulanmazsa exception çıkar; staging dosyası silinir.
                    out.write(decryptor.doFinal())
                }
            }
            val info = DataInputStream(BufferedInputStream(FileInputStream(intermediate))).use { data ->
                val length = data.readInt()
                require(length in 1..META_LIMIT) { "Medya üstbilgisi boyutu geçersiz." }
                val metadataBytes = ByteArray(length)
                data.readFully(metadataBytes)
                val meta = MediaInfo.fromJson(metadataBytes)
                require(meta.ownerUid == expectedUid) { "Medya başka hesaba ait." }
                require(meta.plainSize in 1..MAX_PLAIN_BYTES)
                require(meta.plainSha256.matches(Regex("[0-9a-f]{64}")))
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                FileOutputStream(plain).use { out ->
                    val bytes = ByteArray(32 * 1024)
                    while (true) {
                        val read = data.read(bytes)
                        if (read == -1) break
                        copied += read
                        require(copied <= MAX_PLAIN_BYTES)
                        digest.update(bytes, 0, read)
                        out.write(bytes, 0, read)
                    }
                }
                require(copied == meta.plainSize &&
                        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == meta.plainSha256) {
                    "Medyanın doğrulanan boyutu veya SHA-256 özeti eşleşmiyor."
                }
                meta
            }
            // Görsel dosyası gerçek bir resim değilse kalıcı alana yerleştirilmez.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(plain.absolutePath, bounds)
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            require(pixels in 1..MAX_PIXELS) { "Fotoğraf okunamadı veya çözünürlük sınırı aşıldı." }
            require(info.mimeType in setOf("image/jpeg", "image/png", "image/webp"))
            return Decoded(info, plain)
        } catch (error: Exception) {
            plain.delete()
            throw error
        } finally {
            intermediate.delete()
        }
    }

    /** Dosya kimliğini ve manifest anahtarını hesaplamak için düz SHA-256. */
    fun sha256(input: String): String = sha256(input.toByteArray(Charsets.UTF_8))
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
