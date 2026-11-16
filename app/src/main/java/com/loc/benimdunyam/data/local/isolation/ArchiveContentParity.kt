package com.loc.benimdunyam.data.local.isolation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Taşıma öncesinde iki mantıksal JSON arşivinin AYNI verileri taşıdığını doğrular.
 *
 * BackupData.exportTimestamp her yedek oluşturulduğunda değiştiği için sadece
 * bu alan karşılaştırmanın dışında kalır. Kayıt listeleri DAO sorgu sırasından
 * bağımsız sıralanır; kayıtların alanları ve içerikleri aynen karşılaştırılır.
 * İç içe listelerin sırası değiştirilmez (örneğin taslak etiket sırası).
 */
internal object ArchiveContentParity {
    fun equivalent(reference: String, actual: String, isMainBackup: Boolean = false): Boolean =
        normalized(reference, isMainBackup) == normalized(actual, isMainBackup)

    private fun normalized(json: String, main: Boolean): JsonElement {
        val parsed = JsonParser.parseString(json)
        if (parsed.isJsonArray) return sorted(parsed.asJsonArray)
        require(parsed.isJsonObject) { "Arşiv bölümü JSON nesnesi veya dizisi olmalı." }
        val obj = parsed.asJsonObject.deepCopy()
        if (main) obj.remove("exportTimestamp")
        // Sadece üst seviye tablo listeleri sıralanır, iç listeler korunur.
        for ((name, value) in obj.entrySet().toList()) {
            if (value.isJsonArray) obj.add(name, sorted(value.asJsonArray))
        }
        return obj
    }

    private fun sorted(input: JsonArray): JsonArray = JsonArray().apply {
        input.map { it.deepCopy() }.sortedBy { it.toString() }.forEach { add(it) }
    }
}
