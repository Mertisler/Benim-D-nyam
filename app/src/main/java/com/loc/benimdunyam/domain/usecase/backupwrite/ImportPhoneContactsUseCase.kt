package com.loc.benimdunyam.domain.usecase.backupwrite

import androidx.room.withTransaction
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.SavePersonUseCase
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Sonuclar yalnizca transaction basarili olursa dondurulur. */
data class ContactsImportResult(
    val added: Int,
    val duplicates: Int,
    val ignoredBlank: Int
)

/**
 * Telefon rehberinden secilen isimleri topluca kaydeder.
 *
 * 1. Ortak yedekleme kilidini alir.
 * 2. Room'un anlik kayitli kisilerini yeniden okur.
 * 3. Tum yeni kisileri tek transaction icinde kaydeder.
 *
 * SavePersonUseCase daha onceden kilit aliyorsa, mevcut
 * BackupWriteCoordinator'in ic ice kilit destegi gereklidir.
 */
class ImportPhoneContactsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val savePersonUseCase: SavePersonUseCase,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend operator fun invoke(
        selectedNames: List<String>
    ): ContactsImportResult = writeCoordinator.withRegularWrite {

        require(selectedNames.size <= 5_000) {
            "Tek seferde en fazla 5000 kişi aktarılabilir."
        }

        database.withTransaction {
            val knownNames = database.personDao()
                .getAllPersonEntities()
                .mapTo(mutableSetOf()) { normalizedName(it.name) }

            var added = 0
            var duplicates = 0
            var ignoredBlank = 0

            for (rawName in selectedNames) {
                val name = rawName.trim().replace(Regex("\\s+"), " ")

                if (name.isBlank()) {
                    ignoredBlank++
                    continue
                }

                val normalized = normalizedName(name)
                if (normalized in knownNames) {
                    duplicates++
                    continue
                }

                val now = System.currentTimeMillis()
                val person = Person(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    note = "Cihaz rehberinden toplu aktarıldı",
                    createdAt = now,
                    updatedAt = now
                )

                savePersonUseCase(person, isUpdate = false)
                knownNames.add(normalized)
                added++
            }

            ContactsImportResult(
                added = added,
                duplicates = duplicates,
                ignoredBlank = ignoredBlank
            )
        }
    }

    private fun normalizedName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
}
