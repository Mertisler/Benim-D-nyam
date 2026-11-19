package com.loc.benimdunyam.data.backup

import android.content.Context
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * .bdbackup şifreleme formatının temel güvenlik garantilerini sınar:
 * doğru parolayla birebir geri dönüş, yanlış parola / kurcalanmış /
 * kesilmiş dosyanın reddi ve başarısızlıkta geçici dosya bırakılmaması.
 *
 * Not: JVM testlerinde Build.VERSION.SDK_INT = 0 olduğundan şifreleme
 * eski cihaz yolunu (PBKDF2-SHA1, 1.4M tur) kullanır. Bu yüzden her
 * anahtar türetme ~1 sn sürebilir.
 */
class BackupCryptoManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var stagingDir: File
    private lateinit var manager: BackupCryptoManager

    private val password = "dogru-parola-123"

    @Before
    fun setUp() {
        stagingDir = tmp.newFolder("backup_staging")
        val workspace = mockk<ActiveWorkspaceSession>()
        every { workspace.backupStagingRoot() } returns stagingDir
        manager = BackupCryptoManager(mockk<Context>(relaxed = true), workspace)
    }

    private fun zipWith(bytes: ByteArray): File =
        tmp.newFile("arsiv_${System.nanoTime()}.zip").apply { writeBytes(bytes) }

    private fun encrypt(bytes: ByteArray): File = runBlocking {
        manager.encryptArchive(zipWith(bytes), password.toCharArray())
    }

    private fun decrypt(file: File, pass: String = password): File = runBlocking {
        manager.decryptArchive(file, pass.toCharArray())
    }

    private fun stagingFiles(): List<String> =
        stagingDir.listFiles().orEmpty().map { it.name }

    @Test
    fun dogruParolaIleVeriBirebirGeriDoner() {
        // 64 KB parça sınırını aşan, son parçası yarım kalan veri.
        val original = Random(42).nextBytes(64 * 1024 * 3 + 517)

        val encrypted = encrypt(original)
        val decrypted = decrypt(encrypted)

        assertArrayEquals(original, decrypted.readBytes())
    }

    @Test
    fun tekParcalikKucukVeriGeriDoner() {
        val original = "merhaba dünya".toByteArray()
        assertArrayEquals(original, decrypt(encrypt(original)).readBytes())
    }

    @Test
    fun sifreliDosyaDuzMetniIcermez() {
        val marker = "GIZLI_GUNLUK_ICERIGI_".repeat(50).toByteArray()
        val encrypted = encrypt(marker).readBytes()

        assertTrue(
            String(encrypted, Charsets.ISO_8859_1).startsWith("BDGCM001")
        )
        assertFalse(
            String(encrypted, Charsets.ISO_8859_1).contains("GIZLI_GUNLUK")
        )
    }

    @Test
    fun ayniVeriIkiKezSifrelenincefarkliCiktiUretir() {
        val data = Random(1).nextBytes(1000)
        val first = encrypt(data).readBytes()
        val second = encrypt(data).readBytes()

        // Her yedekte yeni salt ve nonce kullanılmalı.
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun yanlisParolaReddedilirVeGeciciZipSilinir() {
        val encrypted = encrypt(Random(2).nextBytes(5000))
        val before = stagingFiles()

        assertCryptoFailure { decrypt(encrypted, "yanlis-parola-999") }

        assertEquals(before, stagingFiles())
    }

    @Test
    fun icerikteTekBaytDegisirseReddedilir() {
        val encrypted = encrypt(Random(3).nextBytes(10_000))
        val bytes = encrypted.readBytes()
        // Başlığın (48 bayt) ve ilk uzunluk alanının ötesinde bir bayt.
        bytes[100] = (bytes[100].toInt() xor 0x01).toByte()
        encrypted.writeBytes(bytes)

        assertCryptoFailure { decrypt(encrypted) }
    }

    @Test
    fun baslikDegisirseReddedilir() {
        val encrypted = encrypt(Random(4).nextBytes(2000))
        val bytes = encrypted.readBytes()
        // Salt'ın ilk baytı (offset 24) — AAD ile bağlı olmalı.
        bytes[24] = (bytes[24].toInt() xor 0x01).toByte()
        encrypted.writeBytes(bytes)

        assertCryptoFailure { decrypt(encrypted) }
    }

    @Test
    fun bitisKaydiKesilmisDosyaReddedilir() {
        // Saldırgan/bozuk aktarım dosyanın sonunu keserse
        // eksik yedek "başarılı" sayılmamalı.
        val encrypted = encrypt(Random(5).nextBytes(64 * 1024 * 2))
        val bytes = encrypted.readBytes()
        encrypted.writeBytes(bytes.copyOf(bytes.size - 20))
        val before = stagingFiles()

        assertCryptoFailure { decrypt(encrypted) }
        assertEquals(before, stagingFiles())
    }

    @Test
    fun sonaFazladanVeriEklenirseReddedilir() {
        val encrypted = encrypt(Random(6).nextBytes(3000))
        encrypted.appendBytes(byteArrayOf(1, 2, 3))

        assertCryptoFailure { decrypt(encrypted) }
    }

    @Test
    fun yabanciDosyaReddedilir() {
        val foreign = tmp.newFile("baska.bdbackup").apply {
            writeBytes(Random(7).nextBytes(500))
        }
        assertCryptoFailure { decrypt(foreign) }
    }

    @Test
    fun kisaParolaIleSifrelemeYapilmaz() {
        val zip = zipWith(byteArrayOf(1, 2, 3))
        try {
            runBlocking { manager.encryptArchive(zip, "kisa".toCharArray()) }
            fail("Kısa parola kabul edilmemeli")
        } catch (expected: IllegalArgumentException) {
            // beklenen
        }
        assertTrue(stagingFiles().isEmpty())
    }

    @Test
    fun parolaDizisiIslemSonundaTemizlenir() {
        val pass = password.toCharArray()
        val encrypted = runBlocking {
            manager.encryptArchive(zipWith(byteArrayOf(9, 9, 9)), pass)
        }
        assertTrue(pass.all { it == '\u0000' })

        val pass2 = password.toCharArray()
        runBlocking { manager.decryptArchive(encrypted, pass2) }
        assertTrue(pass2.all { it == '\u0000' })
    }

    /** Yanlış parola, bozulma veya kesilme: hepsi IOException ailesinden gelmeli. */
    private fun assertCryptoFailure(block: () -> Unit) {
        try {
            block()
            fail("Bozuk veya yanlış parolalı yedek kabul edilmemeli")
        } catch (expected: IOException) {
            // BackupCryptoException veya EOFException — ikisi de IOException.
        }
    }
}
