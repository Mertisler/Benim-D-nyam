
package com.loc.benimdunyam.data.backup

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.loc.benimdunyam.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Doğrulanan yedek verileri ve geçici görseller.
 *
 * Bu nesne henüz Room'a veri yazmaz.
 *
 * Kullanımı tamamlandığında close() çağrılmalıdır.
 */
class PreparedBackup internal constructor(
    val data: BackupData,
    val manifest: BackupArchiveManifest,
    private val stagingDirectory: File
) : Closeable {

    private val knownPaths =
        manifest.mediaFiles.map { it.archivePath }.toSet()

    /**
     * Doğrulanmış bir görselin geçici dosyasını döndürür.
     */
    fun stagedImageFile(archivePath: String): File {
        require(archivePath in knownPaths) {
            "Yedekte bulunmayan görsel istendi."
        }

        val root = stagingDirectory.canonicalFile
        val file = File(root, archivePath).canonicalFile

        require(
            file.path.startsWith(
                root.path + File.separator
            ) && file.isFile
        ) {
            "Hazırlanmış görsel dosyası bulunamadı."
        }

        return file
    }

    /**
     * İşlem sonrasında geçici görselleri temizler.
     */
    override fun close() {
        stagingDirectory.deleteRecursively()
    }
}

/**
 * Şifresi çözülmüş ZIP arşivini doğrular
 * ve geçici geri yükleme ortamına hazırlar.
 */
@Singleton
class BackupArchiveReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupValidator: BackupValidator
) {

    private companion object {

        const val MAX_ZIP_BYTES =
            600L * 1024L * 1024L

        const val MAX_JSON_BYTES =
            32L * 1024L * 1024L

        const val MAX_MANIFEST_BYTES =
            2L * 1024L * 1024L

        const val MAX_IMAGE_BYTES =
            100L * 1024L * 1024L

        const val MAX_TOTAL_MEDIA_BYTES =
            500L * 1024L * 1024L

        const val MAX_MEDIA_COUNT = 2000

        const val DATA_ENTRY = "data/backup.json"
        const val MANIFEST_ENTRY = "manifest.json"
    }

    private val safeIdRegex =
        Regex("[A-Za-z0-9_-]{1,128}")

    private val shaRegex =
        Regex("[a-f0-9]{64}")

    private val mediaPathRegex = Regex(
        """^(?:media/seasons/[A-Za-z0-9_-]{1,128}/cover|media/people/[A-Za-z0-9_-]{1,128}/(?:profile|cover))\.[a-z0-9]{1,8}$"""
    )

    /**
     * ZIP dosyasını doğrular ve görselleri
     * yalnızca geçici klasöre çıkarır.
     *
     * Başarısızlıkta geçici klasör temizlenir.
     */
    suspend fun readAndPrepare(
        zipFile: File
    ): PreparedBackup = withContext(Dispatchers.IO) {

        if (!zipFile.isFile ||
            zipFile.length() !in 1..MAX_ZIP_BYTES
        ) {
            throw IOException(
                "Yedek ZIP dosyası bulunamadı veya çok büyük."
            )
        }

        ZipFile(zipFile).use { zip ->

            // 1. Bütün ZIP girişlerini incele.
            val entries = collectEntries(zip)

            val manifestEntry = entries[MANIFEST_ENTRY]
                ?: throw IOException(
                    "Yedek manifest dosyası bulunamadı."
                )

            val dataEntry = entries[DATA_ENTRY]
                ?: throw IOException(
                    "Yedek veri dosyası bulunamadı."
                )

            // 2. Manifest dosyasını oku ve doğrula.
            val manifestBytes = readLimited(
                zip = zip,
                entry = manifestEntry,
                maxBytes = MAX_MANIFEST_BYTES
            )

            val manifest = parseManifest(
                String(manifestBytes, Charsets.UTF_8)
            )

            // 3. ZIP içeriği manifest ile birebir eşleşmeli.
            val mediaPaths = manifest.mediaFiles
                .map { it.archivePath }
                .toSet()

            val expectedPaths = mediaPaths +
                    setOf(DATA_ENTRY, MANIFEST_ENTRY)

            if (entries.keys != expectedPaths) {
                throw IOException(
                    "ZIP içeriği ile manifest uyuşmuyor."
                )
            }

            // 4. JSON dosyasını sınırlandırarak oku.
            val jsonBytes = readLimited(
                zip = zip,
                entry = dataEntry,
                maxBytes = MAX_JSON_BYTES
            )

            if (jsonBytes.size.toLong() !=
                manifest.dataSizeBytes
            ) {
                throw IOException(
                    "Yedek veri dosyasının boyutu uyuşmuyor."
                )
            }

            if (sha256(jsonBytes) != manifest.dataSha256) {
                throw IOException(
                    "Yedek veri dosyasının özeti uyuşmuyor."
                )
            }

            // 5. JSON ve veritabanı ilişkilerini doğrula.
            val backupData = backupValidator.parseAndValidate(
                String(jsonBytes, Charsets.UTF_8)
            )

            if (backupData.version != manifest.dataVersion) {
                throw IOException(
                    "Yedek veri sürümü uyuşmuyor."
                )
            }

            if (backupData.exportTimestamp !=
                manifest.createdAt
            ) {
                throw IOException(
                    "Yedek oluşturma bilgileri uyuşmuyor."
                )
            }

            // 6. JSON'daki bütün görsel referanslarını kontrol et.
            validateMediaReferences(
                backupData = backupData,
                manifestPaths = mediaPaths
            )

            // 7. Doğrulanmış medya için geçici klasör.
            val stagingDirectory = createStagingDirectory()
            var completed = false

            try {
                var totalBytes = 0L

                for (media in manifest.mediaFiles) {
                    currentCoroutineContext().ensureActive()

                    val entry = entries[media.archivePath]
                        ?: throw IOException(
                            "Görsel ZIP içerisinde bulunamadı."
                        )

                    val remaining =
                        MAX_TOTAL_MEDIA_BYTES - totalBytes

                    if (media.sizeBytes > remaining) {
                        throw IOException(
                            "Toplam görsel boyutu sınırı aşıyor."
                        )
                    }

                    // Zip Slip koruması.
                    val destination = safeDestination(
                        stagingDirectory,
                        media.archivePath
                    )

                    if (!destination.parentFile!!.exists() &&
                        !destination.parentFile!!.mkdirs()
                    ) {
                        throw IOException(
                            "Geçici görsel klasörü oluşturulamadı."
                        )
                    }

                    val copiedBytes = extractVerifiedImage(
                        zip = zip,
                        entry = entry,
                        destination = destination,
                        media = media
                    )

                    totalBytes += copiedBytes
                }

                val preparedBackup = PreparedBackup(
                    data = backupData,
                    manifest = manifest,
                    stagingDirectory = stagingDirectory
                )

                completed = true
                preparedBackup

            } finally {
                if (!completed) {
                    stagingDirectory.deleteRecursively()
                }
            }
        }
    }

    /**
     * ZIP'te yalnızca beklenen dosyalar olmalı.
     * Kopya isimler ve klasör girişleri reddedilir.
     */
    private fun collectEntries(
        zip: ZipFile
    ): Map<String, ZipEntry> {

        val result = linkedMapOf<String, ZipEntry>()
        val enumeration = zip.entries()

        while (enumeration.hasMoreElements()) {

            val entry = enumeration.nextElement()
            val name = entry.name

            if (result.size >= MAX_MEDIA_COUNT + 2) {
                throw IOException(
                    "ZIP dosyasında çok fazla giriş var."
                )
            }

            if (entry.isDirectory ||
                !isAllowedEntryName(name)
            ) {
                throw IOException(
                    "ZIP içinde geçersiz dosya yolu bulundu."
                )
            }

            if (result.put(name, entry) != null) {
                throw IOException(
                    "ZIP içerisinde tekrarlanan dosya adı bulundu."
                )
            }
        }

        return result
    }

    private fun isAllowedEntryName(name: String): Boolean {
        return name == MANIFEST_ENTRY ||
                name == DATA_ENTRY ||
                mediaPathRegex.matches(name)
    }

    /**
     * Manifest yapısını ve limitlerini doğrular.
     */
    private fun parseManifest(
        json: String
    ): BackupArchiveManifest {

        val element = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            throw IOException(
                "Manifest geçerli JSON içermiyor.", e
            )
        }

        if (!element.isJsonObject) {
            throw IOException(
                "Manifest yapısı geçersiz."
            )
        }

        val root = element.asJsonObject

        val format = readString(root, "format")
        val archiveVersion = readInt(
            root, "archiveVersion"
        )
        val dataVersion = readInt(
            root, "dataVersion"
        )
        val createdAt = readLong(
            root, "createdAt"
        )
        val dataSize = readLong(
            root, "dataSizeBytes"
        )
        val dataHash = readString(
            root, "dataSha256"
        )

        if (format != "benim-dunyam" ||
            archiveVersion != 1 ||
            dataVersion != 2
        ) {
            throw IOException(
                "Desteklenmeyen yedek arşivi sürümü."
            )
        }

        if (createdAt <= 0 ||
            dataSize !in 1..MAX_JSON_BYTES ||
            !shaRegex.matches(dataHash)
        ) {
            throw IOException(
                "Manifest veri bilgileri geçersiz."
            )
        }

        val mediaElement = root.get("mediaFiles")

        if (mediaElement == null ||
            !mediaElement.isJsonArray
        ) {
            throw IOException(
                "Manifest görsel listesi bulunamadı."
            )
        }

        val mediaArray = mediaElement.asJsonArray

        if (mediaArray.size() > MAX_MEDIA_COUNT) {
            throw IOException(
                "Çok fazla görsel içeren yedek."
            )
        }

        val mediaFiles = mutableListOf<BackupMediaFile>()
        val names = mutableSetOf<String>()

        var totalMediaSize = 0L

        for (elementInArray in mediaArray) {

            if (!elementInArray.isJsonObject) {
                throw IOException(
                    "Manifest görsel kaydı geçersiz."
                )
            }

            val item = elementInArray.asJsonObject

            val path = readString(item, "archivePath")
            val size = readLong(item, "sizeBytes")
            val hash = readString(item, "sha256")

            if (!mediaPathRegex.matches(path) ||
                !shaRegex.matches(hash) ||
                size !in 1..MAX_IMAGE_BYTES
            ) {
                throw IOException(
                    "Manifest görsel bilgileri geçersiz."
                )
            }

            if (!names.add(path)) {
                throw IOException(
                    "Manifestte tekrarlanan görsel var."
                )
            }

            if (size >
                MAX_TOTAL_MEDIA_BYTES - totalMediaSize
            ) {
                throw IOException(
                    "Manifest toplam boyut sınırını aşıyor."
                )
            }

            totalMediaSize += size

            mediaFiles.add(
                BackupMediaFile(
                    archivePath = path,
                    sizeBytes = size,
                    sha256 = hash
                )
            )
        }

        return BackupArchiveManifest(
            format = format,
            archiveVersion = archiveVersion,
            dataVersion = dataVersion,
            createdAt = createdAt,
            dataSizeBytes = dataSize,
            dataSha256 = dataHash,
            mediaFiles = mediaFiles
        )
    }

    /**
     * JSON'daki görsel yolları,
     * manifestte bildirilen görsellerle eşleşmeli.
     */
    private fun validateMediaReferences(
        backupData: BackupData,
        manifestPaths: Set<String>
    ) {

        val referencedPaths = mutableSetOf<String>()

        fun register(
            path: String?,
            expectedBase: String
        ) {
            if (path == null) return

            if (!mediaPathRegex.matches(path) ||
                !path.startsWith("$expectedBase.")
            ) {
                throw IOException(
                    "Yedekte geçersiz görsel referansı bulundu."
                )
            }

            if (!referencedPaths.add(path)) {
                throw IOException(
                    "Tekrarlanan görsel referansı bulundu."
                )
            }
        }

        for (season in backupData.seasons) {
            requireSafeId(season.id)

            register(
                season.coverImagePath,
                "media/seasons/${season.id}/cover"
            )
        }

        for (person in backupData.people) {
            requireSafeId(person.id)

            register(
                person.profileImagePath,
                "media/people/${person.id}/profile"
            )

            register(
                person.coverImagePath,
                "media/people/${person.id}/cover"
            )
        }

        if (referencedPaths != manifestPaths) {
            throw IOException(
                "Yedekteki görsel referansları eksik veya fazla."
            )
        }
    }

    private fun requireSafeId(id: String) {
        if (!safeIdRegex.matches(id)) {
            throw IOException(
                "Yedekte geçersiz kayıt kimliği bulundu."
            )
        }
    }

    /**
     * ZIP girişini sınırlı miktarda belleğe alır.
     * Yalnızca JSON ve manifest için kullanılır.
     */
    private suspend fun readLimited(
        zip: ZipFile,
        entry: ZipEntry,
        maxBytes: Long
    ): ByteArray {

        if (entry.size > maxBytes) {
            throw IOException(
                "Yedekteki veri boyut sınırını aşıyor."
            )
        }

        val output = ByteArrayOutputStream()
        var total = 0L

        zip.getInputStream(entry).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                currentCoroutineContext().ensureActive()

                val count = input.read(buffer)
                if (count == -1) break
                if (count == 0) continue

                if (count.toLong() > maxBytes - total) {
                    throw IOException(
                        "Açılan veri boyut sınırını aşıyor."
                    )
                }

                total += count
                output.write(buffer, 0, count)
            }
        }

        if (entry.size >= 0 && entry.size != total) {
            throw IOException(
                "ZIP dosya boyutu uyuşmuyor."
            )
        }

        return output.toByteArray()
    }

    /**
     * Görseli doğrudan dosyaya çıkarır.
     * Hem boyut hem SHA-256 kontrol edilir.
     */
    private suspend fun extractVerifiedImage(
        zip: ZipFile,
        entry: ZipEntry,
        destination: File,
        media: BackupMediaFile
    ): Long {

        if (entry.size > media.sizeBytes) {
            throw IOException(
                "ZIP görsel boyutu manifest ile uyuşmuyor."
            )
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L

        zip.getInputStream(entry).buffered().use { input ->
            destination.outputStream().buffered().use { output ->

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (true) {
                    currentCoroutineContext().ensureActive()

                    val count = input.read(buffer)

                    if (count == -1) break
                    if (count == 0) continue

                    if (count.toLong() >
                        media.sizeBytes - total
                    ) {
                        throw IOException(
                            "Görsel boyutu manifesti aşıyor."
                        )
                    }

                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)

                    total += count
                }
            }
        }

        if (total != media.sizeBytes ||
            total == 0L
        ) {
            throw IOException(
                "Görsel dosyasının boyutu uyuşmuyor."
            )
        }

        val actualHash = toHex(digest.digest())

        if (actualHash != media.sha256) {
            throw IOException(
                "Görsel dosyasının özeti uyuşmuyor."
            )
        }

        return total
    }

    /**
     * Çıkarılan dosyanın geçici klasörün
     * dışına çıkmasını engeller.
     */
    private fun safeDestination(
        rootDirectory: File,
        archivePath: String
    ): File {

        if (!mediaPathRegex.matches(archivePath)) {
            throw IOException(
                "Güvensiz ZIP dosya yolu."
            )
        }

        val root = rootDirectory.canonicalFile
        val target = File(root, archivePath).canonicalFile

        if (!target.path.startsWith(
                root.path + File.separator
            )
        ) {
            throw IOException(
                "ZIP dosyası izin verilen klasörün dışında."
            )
        }

        return target
    }

    private fun createStagingDirectory(): File {

        val base = File(
            context.noBackupFilesDir,
            "backup_staging"
        )

        if (!base.exists() && !base.mkdirs()) {
            throw IOException(
                "Geçici yedek klasörü oluşturulamadı."
            )
        }

        val temporary = File.createTempFile(
            "restore_",
            ".tmp",
            base
        )

        if (!temporary.delete() || !temporary.mkdir()) {
            temporary.delete()
            throw IOException(
                "Geçici geri yükleme klasörü oluşturulamadı."
            )
        }

        return temporary
    }

    private fun readString(
        obj: JsonObject,
        name: String
    ): String {

        val value = obj.get(name)

        if (value == null ||
            !value.isJsonPrimitive ||
            !value.asJsonPrimitive.isString
        ) {
            throw IOException(
                "Manifestte $name alanı geçersiz."
            )
        }

        return value.asString
    }

    private fun readLong(
        obj: JsonObject,
        name: String
    ): Long {

        val value = obj.get(name)

        if (value == null ||
            !value.isJsonPrimitive ||
            !value.asJsonPrimitive.isNumber
        ) {
            throw IOException(
                "Manifestte $name sayısı geçersiz."
            )
        }

        return value.asString.toLongOrNull()
            ?: throw IOException(
                "Manifestte $name sayısı okunamadı."
            )
    }

    private fun readInt(
        obj: JsonObject,
        name: String
    ): Int {

        val value = obj.get(name)

        if (value == null ||
            !value.isJsonPrimitive ||
            !value.asJsonPrimitive.isNumber
        ) {
            throw IOException(
                "Manifestte $name alanı geçersiz."
            )
        }

        return value.asString.toIntOrNull()
            ?: throw IOException(
                "Manifestte $name sayısı okunamadı."
            )
    }

    private fun sha256(bytes: ByteArray): String {
        return toHex(
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
        )
    }

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}
