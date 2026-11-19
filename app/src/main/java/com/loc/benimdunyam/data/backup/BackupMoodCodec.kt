package com.loc.benimdunyam.data.backup

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.domain.model.JournalMood
import java.io.IOException

/**
 * .bdbackup arşiv sürüm 2'deki `data/moods.json` girişinin biçimi.
 *
 * Ruh halleri ayrı bir Room veritabanında tutulduğu için BackupData'ya
 * (veri sürüm 2) eklenmez; böylece BackupValidator, bulut kataloğu ve
 * eski yedekler etkilenmez. Giriş, manifestte boyut + SHA-256 ile bağlanır.
 *
 * Saf Kotlin: Android/Room bağımlılığı yoktur, doğrudan birim testlenir.
 */
object BackupMoodCodec {

    const val ENTRY = "data/moods.json"
    const val FORMAT_VERSION = 1
    const val MAX_BYTES = 8L * 1024L * 1024L

    private val validNames = JournalMood.entries.map { it.name }.toSet()
    private val gson = Gson()

    private data class Payload(
        val version: Int,
        val moods: List<JournalMoodRecord>
    )

    /**
     * Yedeğe girecek kayıtları seçer.
     *
     * Günlüğü artık bulunmayan (yetim) ve uygulamanın tanımadığı ruh halleri
     * geri yüklenemeyeceği için yedeğe alınmaz. Sıra kararlı tutulur.
     */
    fun selectForBackup(
        records: List<JournalMoodRecord>,
        journalIds: Set<String>
    ): List<JournalMoodRecord> =
        records
            .filter { it.journalId in journalIds && it.moodName in validNames }
            .sortedBy { it.journalId }

    fun encode(records: List<JournalMoodRecord>): ByteArray =
        gson.toJson(Payload(FORMAT_VERSION, records)).toByteArray(Charsets.UTF_8)

    /**
     * Girişi katı biçimde okur. Herhangi bir tutarsızlıkta [IOException]
     * fırlatır; böylece geri yükleme gerçek verilere dokunmadan durur.
     */
    fun decodeAndValidate(
        bytes: ByteArray,
        journalIds: Set<String>
    ): List<JournalMoodRecord> {
        val root = try {
            JsonParser.parseString(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IOException("Ruh hali verisi geçerli JSON içermiyor.", e)
        }
        if (!root.isJsonObject) throw IOException("Ruh hali verisinin yapısı geçersiz.")
        val obj = root.asJsonObject

        if (readLong(obj, "version") != FORMAT_VERSION.toLong()) {
            throw IOException("Desteklenmeyen ruh hali verisi sürümü.")
        }

        val array = obj.get("moods")
        if (array == null || !array.isJsonArray) {
            throw IOException("Ruh hali listesi bulunamadı.")
        }

        val seen = mutableSetOf<String>()
        return array.asJsonArray.map { element ->
            if (!element.isJsonObject) throw IOException("Ruh hali kaydı geçersiz.")
            val item = element.asJsonObject

            val journalId = readString(item, "journalId")
            val moodName = readString(item, "moodName")
            val updatedAt = readLong(item, "updatedAt")

            if (journalId.isBlank() || journalId !in journalIds) {
                throw IOException("Ruh hali kaydının anısı yedekte bulunamadı.")
            }
            if (moodName !in validNames) {
                throw IOException("Yedekte bilinmeyen bir ruh hali var.")
            }
            if (updatedAt <= 0) {
                throw IOException("Ruh hali tarihi geçersiz.")
            }
            if (!seen.add(journalId)) {
                throw IOException("Aynı anı için birden fazla ruh hali var.")
            }
            JournalMoodRecord(journalId, moodName, updatedAt)
        }
    }

    private fun primitive(obj: JsonObject, name: String): JsonElement {
        val value = obj.get(name)
        if (value == null || !value.isJsonPrimitive) {
            throw IOException("Ruh hali verisinde $name alanı eksik.")
        }
        return value
    }

    private fun readString(obj: JsonObject, name: String): String {
        val value = primitive(obj, name)
        if (!value.asJsonPrimitive.isString) throw IOException("Ruh hali $name alanı geçersiz.")
        return value.asString
    }

    private fun readLong(obj: JsonObject, name: String): Long {
        val value = primitive(obj, name)
        if (!value.asJsonPrimitive.isNumber) throw IOException("Ruh hali $name alanı geçersiz.")
        return value.asString.toLongOrNull() ?: throw IOException("Ruh hali $name sayısı okunamadı.")
    }
}
