package com.loc.benimdunyam.data.backup

import com.loc.benimdunyam.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Veritabanının fiziksel ve yabancı anahtar bütünlüğünün salt-okunur özeti.
 * Bu rapor eksik fotoğrafı veya uygulama düzeyindeki mantıksal hataları
 * tek başına tespit edemez.
 */
data class DatabaseIntegrityReport(
    val structureOk: Boolean,
    val foreignKeyViolationCount: Int,
    val problems: List<String>,
    val checkedAtMillis: Long
) {
    val isHealthy: Boolean
        get() = structureOk && foreignKeyViolationCount == 0
}

@Singleton
class BackupDatabaseIntegrityVerifier @Inject constructor(
    private val appDatabase: AppDatabase,
    private val coordinator: BackupWriteCoordinator
) {
    suspend fun inspect(): DatabaseIntegrityReport =
        coordinator.withRegularWrite {
            withContext(Dispatchers.IO) {
                val sqlite = appDatabase.openHelper.readableDatabase
                val problems = mutableListOf<String>()
                var structureOk = true

                // Herhangi bir hata tespit edilirse 'ok' yerine hata satırları döner.
                sqlite.query("PRAGMA quick_check(20)").use { cursor ->
                    var rowCount = 0
                    while (cursor.moveToNext()) {
                        rowCount++
                        val message = cursor.getString(0).orEmpty()
                        if (!message.equals("ok", ignoreCase = true)) {
                            structureOk = false
                            if (problems.size < 10) {
                                problems += "SQLite: $message"
                            }
                        }
                    }
                    if (rowCount == 0) {
                        structureOk = false
                        problems += "SQLite kontrolü sonuç döndürmedi."
                    }
                }

                var foreignKeyViolations = 0
                sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                    while (cursor.moveToNext()) {
                        foreignKeyViolations++
                        if (problems.size < 10) {
                            val table = cursor.getString(0).orEmpty()
                            val rowId = if (cursor.isNull(1)) "?" else cursor.getString(1)
                            problems += "Yabancı anahtar: $table / satır $rowId"
                        }
                    }
                }

                DatabaseIntegrityReport(
                    structureOk = structureOk,
                    foreignKeyViolationCount = foreignKeyViolations,
                    problems = problems,
                    checkedAtMillis = System.currentTimeMillis()
                )
            }
        }
}
