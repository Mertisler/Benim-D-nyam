package com.loc.benimdunyam.data.sync.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Cihazda tutulan otomatik eşitleme izninin salt-okunur özeti; şifre içermez. */
data class SyncDevicePermission(
    val grantedAtMillis: Long,
    val remoteVerified: Boolean
)

/**
 * Faz 2F.10: Kullanıcının açık izniyle olay/katalog şifresini cihazda korur.
 *
 * - Şifrenin kendisi dosyada, SharedPreferences'ta veya WorkManager Data içinde bulunmaz.
 * - Dosyada yalnızca AES-256-GCM ile sarılmış veri ve açıkça gizli olmayan metadata vardır.
 * - Anahtar AndroidKeyStore'dan DIŞARI çıkarılamaz; dosya noBackupFilesDir altındadır.
 * - AAD, Firebase UID'sini ve Hilt'in açtığı yerel çalışma alanını bağlar.
 * - Android Keystore anahtarı kullanıcı etkileşimi GEREKTİRMEDEN çözer: bu özellik
 *   gelecekte arka plan eşitlemesi için şarttır; cihazdaki uygulama ele geçirilirse
 *   bu izin Android Keystore'u sihirli biçimde aşılmaz yapmaz.
 * - Tek cihazda aynı anda yalnızca bir hesap için izin saklanır.
 *
 * Şifreleme biçimi: JSON v1 { uid, workspace, nonce, ciphertext, ... }.
 * Bulutun mevcut BDCLD1 şifreli olay biçimi hiçbir şekilde değiştirilmez.
 */
@Singleton
class SyncDeviceCredentialVault @Inject constructor(
    @ApplicationContext context: Context,
    private val session: ActiveWorkspaceSession
) {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    /** Kullanıcı onayı yalnızca aynı çalışma alanında ve aynı hesapta geçerli sayılır. */
    @Synchronized
    fun status(uid: String): SyncDevicePermission? {
        val record = readRecord() ?: return null
        if (record.getString("uid") != uid || record.getString("workspace") != workspaceId()) {
            return null // Eski hesap/çalışma alanının izni yeni hesapta kullanılamaz.
        }
        check(record.getInt("version") == 1 && record.getBoolean("allowBackgroundSync")) {
            "Desteklenmeyen veya onaysız cihaz kimliği."
        }
        // Keystore anahtarı kaybolduysa 'izin var' diye göstermemek için çözümü dene.
        val secret = decrypt(record, uid)
        try {
            require(secret.size in 12..128) { "Cihazdaki eşitleme şifresi geçersiz." }
        } finally {
            secret.fill('\u0000')
        }
        return SyncDevicePermission(
            grantedAtMillis = record.getLong("grantedAtMillis"),
            remoteVerified = record.getBoolean("remoteVerified")
        )
    }

    /**
     * Kullanıcı şifreyi ve riskleri onaylamadan çağrılmamalı.
     * Hata durumunda daha önceki izin silinmez; dosya atomik biçimde yenilenir.
     */
    @Synchronized
    fun grant(uid: String, passphrase: CharArray, remoteVerified: Boolean): SyncDevicePermission {
        require(uid.isNotBlank() && passphrase.size in 12..128)
        val key = key(createIfAbsent = true)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad(uid))
        val clear = String(passphrase).toByteArray(StandardCharsets.UTF_8)
        val encrypted = try {
            cipher.doFinal(clear)
        } finally {
            clear.fill(0)
        }
        try {
            val record = JSONObject()
                .put("version", 1)
                .put("uid", uid)
                .put("workspace", workspaceId())
                .put("allowBackgroundSync", true)
                .put("grantedAtMillis", System.currentTimeMillis())
                .put("remoteVerified", remoteVerified)
                .put("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            val bytes = record.toString().toByteArray(StandardCharsets.UTF_8)
            try {
                check(bytes.size <= MAX_FILE_BYTES) { "Cihaz izni kaydı çok büyük." }
                val stream = atomicFile.startWrite()
                try {
                    stream.write(bytes)
                    atomicFile.finishWrite(stream)
                } catch (error: Exception) {
                    atomicFile.failWrite(stream)
                    throw error
                }
            } finally {
                bytes.fill(0)
            }
            return SyncDevicePermission(record.getLong("grantedAtMillis"), remoteVerified)
        } finally {
            encrypted.fill(0)
        }
    }

    /**
     * Sadece güvenilir uygulama kodunun kullanacağı dar kapsamlı erişim noktası.
     * Başarısız deşifrede işlem YAPILMAZ; şifre dizisi blok bitince temizlenir.
     */
    @Synchronized
    fun <T> withUnlockedPassphrase(uid: String, operation: (CharArray) -> T): T {
        val record = readRecord() ?: error("Bu cihazda otomatik eşitleme izni yok.")
        check(record.getString("uid") == uid && record.getString("workspace") == workspaceId()) {
            "Hesap veya yerel çalışma alanı değişti. Cihaz izni geçersiz."
        }
        check(record.getBoolean("allowBackgroundSync")) { "Otomatik eşitleme izni kapalı." }
        val clear = decrypt(record, uid)
        try {
            return operation(clear)
        } finally {
            clear.fill('\u0000')
        }
    }

    /** Kullanıcı izni iptal edince şifreli dosya ve onun Keystore anahtarı silinir. */
    @Synchronized
    fun revoke(uid: String) {
        val record = readRecord()
        check(record == null || record.getString("uid") == uid) {
            "Bu dosya başka bir hesaba ait; mevcut hesabın izniyle silinemez."
        }
        atomicFile.delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    private fun readRecord(): JSONObject? {
        if (!atomicFile.baseFile.exists() && !File(atomicFile.baseFile.path + ".bak").exists()) {
            return null
        }
        val backupFile = File(atomicFile.baseFile.path + ".bak")
        check(atomicFile.baseFile.length() <= MAX_FILE_BYTES &&
                backupFile.length() <= MAX_FILE_BYTES) {
            "Cihaz izni dosyası boyut sınırını aştı."
        }
        val bytes = atomicFile.openRead().use { it.readBytes() }
        try {
            require(bytes.size in 1..MAX_FILE_BYTES)
            return JSONObject(String(bytes, StandardCharsets.UTF_8))
        } finally {
            bytes.fill(0)
        }
    }

    private fun decrypt(record: JSONObject, uid: String): CharArray {
        check(record.getInt("version") == 1)
        val nonce = Base64.decode(record.getString("nonce"), Base64.NO_WRAP)
        val encrypted = Base64.decode(record.getString("ciphertext"), Base64.NO_WRAP)
        require(nonce.size == 12 && encrypted.size in 17..2048)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(createIfAbsent = false), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad(uid))
            val plain = cipher.doFinal(encrypted) // Yanlış AAD/şifreli veri → AEADBadTagException.
            try {
                return String(plain, StandardCharsets.UTF_8).toCharArray()
            } finally {
                plain.fill(0)
            }
        } finally {
            nonce.fill(0)
            encrypted.fill(0)
        }
    }

    private fun aad(uid: String): ByteArray =
        "BD-SYNC-CONSENT-V1|$uid|${workspaceId()}".toByteArray(StandardCharsets.UTF_8)

    private fun workspaceId(): String =
        session.mode.name + "|" + session.databaseNames().joinToString("|")

    private fun key(createIfAbsent: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        check(createIfAbsent) { "Keystore anahtarı bulunamadı. İzni kapatıp yeniden etkinleştir." }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false) // Arka planda kilitli cihazda da kullanılabilmeli.
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "benimdunyam_sync_consent_v1.json"
        const val KEY_ALIAS = "benimdunyam.sync.device.wrap.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAX_FILE_BYTES = 4096
    }
}
