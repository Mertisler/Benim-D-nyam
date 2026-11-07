
package com.loc.benimdunyam.data.backup

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loc.benimdunyam.data.local.entity.*
import javax.inject.Inject

class BackupValidator @Inject constructor() {

    private val gson = Gson()

    companion object {
        const val SUPPORTED_VERSION = 2
    }

    /**
     * Şifresi çözülmüş JSON verisini kontrol eder.
     * Geçerliyse BackupData döndürür.
     * Geçersizse hata fırlatır.
     */
    fun parseAndValidate(json: String): BackupData {

        require(json.isNotBlank()) {
            "Yedek dosyası boş."
        }

        val root = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Yedek dosyası geçerli JSON içermiyor.", e
            )
        }

        require(root.isJsonObject) {
            "Yedek dosyasının kök yapısı geçersiz."
        }

        val jsonObject = root.asJsonObject

        // 1. Format sürümünü doğrula
        val versionElement = jsonObject.get("version")

        require(
            versionElement != null &&
                    versionElement.isJsonPrimitive &&
                    versionElement.asJsonPrimitive.isNumber &&
                    versionElement.asString == SUPPORTED_VERSION.toString()
        ) {
            "Desteklenmeyen yedek sürümü. " +
                    "Bu doğrulayıcı yalnızca sürüm 2'yi destekler."
        }

        // 2. Altı tablonun JSON yapısını doğrula
        val requiredFields = mapOf(
            "journals" to listOf(
                "id", "title", "content",
                "entryDate", "createdAt", "updatedAt"
            ),
            "people" to listOf(
                "id", "name", "createdAt", "updatedAt"
            ),
            "mentions" to listOf(
                "journalId", "personId", "mentionIndex"
            ),
            "seasons" to listOf(
                "id", "title", "description",
                "startDate", "colorHex", "createdAt"
            ),
            "episodes" to listOf(
                "id", "seasonId", "title", "description",
                "orderIndex", "startDate", "createdAt"
            ),
            "specialMemories" to listOf(
                "personId", "journalId", "category",
                "createdAt", "updatedAt"
            )
        )

        for ((tableName, fields) in requiredFields) {
            val array = jsonObject.get(tableName)

            require(array != null && array.isJsonArray) {
                "$tableName tablosu eksik veya geçersiz."
            }

            array.asJsonArray.forEachIndexed { index, element ->
                require(element.isJsonObject) {
                    "$tableName tablosundaki $index numaralı kayıt geçersiz."
                }

                val row = element.asJsonObject

                for (field in fields) {
                    require(
                        row.has(field) && !row.get(field).isJsonNull
                    ) {
                        "$tableName tablosundaki $index numaralı " +
                                "kayıtta $field alanı eksik."
                    }
                }
            }
        }

        // 3. JSON -> BackupData
        val backupData = try {
            gson.fromJson(jsonObject, BackupData::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Yedek verileri okunamadı.", e
            )
        } ?: throw IllegalArgumentException(
            "Yedek verisi oluşturulamadı."
        )

        // 4. Kayıtlar arası ilişkileri kontrol et
        validateRelations(backupData)

        return backupData
    }

    private fun validateRelations(data: BackupData) {

        require(data.version == SUPPORTED_VERSION) {
            "Yedek sürümü geçersiz."
        }

        require(data.exportTimestamp > 0L) {
            "Yedek oluşturma tarihi geçersiz."
        }

        // Her tablodaki ID'ler benzersiz olmalı
        val journalIds = uniqueIds(
            "journals", data.journals.map { it.id }
        )

        val personIds = uniqueIds(
            "people", data.people.map { it.id }
        )

        val seasonIds = uniqueIds(
            "seasons", data.seasons.map { it.id }
        )

        uniqueIds(
            "episodes", data.episodes.map { it.id }
        )

        val episodeMap = data.episodes.associateBy { it.id }

        // 5. Bölümler mevcut sezonlara bağlı olmalı
        for (episode in data.episodes) {
            require(episode.seasonId in seasonIds) {
                "Bölüm '${episode.id}' için sezon bulunamadı."
            }
        }

        // 6. Günlüklerin sezon/bölüm ilişkilerini kontrol et
        for (journal in data.journals) {

            journal.seasonId?.let { seasonId ->
                require(seasonId in seasonIds) {
                    "Günlük '${journal.id}' için sezon bulunamadı."
                }
            }

            journal.episodeId?.let { episodeId ->

                val episode = episodeMap[episodeId]
                    ?: throw IllegalArgumentException(
                        "Günlük '${journal.id}' için bölüm bulunamadı."
                    )

                require(episode.seasonId == journal.seasonId) {
                    "Günlük '${journal.id}' için " +
                            "sezon ve bölüm uyuşmuyor."
                }
            }
        }

        // 7. Kişi-anı bağlantılarını doğrula
        val mentionPairs = mutableSetOf<Pair<String, String>>()

        for (mention in data.mentions) {

            require(mention.journalId in journalIds) {
                "Anılma kaydının günlüğü bulunamadı."
            }

            require(mention.personId in personIds) {
                "Anılma kaydının kişisi bulunamadı."
            }

            val pair = mention.journalId to mention.personId

            require(mentionPairs.add(pair)) {
                "Tekrarlanan kişi-anı bağlantısı tespit edildi."
            }
        }

        // 8. Unutulmaz anılar geçerli bir kişi-anı
        // bağlantısına sahip olmalı
        val specialMemoryPairs =
            mutableSetOf<Pair<String, String>>()

        for (memory in data.specialMemories) {

            require(memory.category.isNotBlank()) {
                "Unutulmaz anı kategorisi boş."
            }

            val pair = memory.journalId to memory.personId

            require(pair in mentionPairs) {
                "Unutulmaz anı geçersiz bir " +
                        "kişi-anı bağlantısına sahip."
            }

            require(specialMemoryPairs.add(pair)) {
                "Tekrarlanan unutulmaz anı tespit edildi."
            }
        }
    }

    private fun uniqueIds(
        tableName: String,
        ids: List<String>
    ): Set<String> {

        require(ids.none { it.isBlank() }) {
            "$tableName tablosunda boş ID bulundu."
        }

        val unique = ids.toSet()

        require(unique.size == ids.size) {
            "$tableName tablosunda tekrarlanan ID bulundu."
        }

        return unique
    }
}
