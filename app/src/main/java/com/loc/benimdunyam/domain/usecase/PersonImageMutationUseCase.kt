package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.model.PersonImageType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Kişi fotoğraflarını ve Room'daki dosya yollarını tek korumalı
 * yazma akışında değiştirir.
 *
 * Room ve dosya sistemi ortak bir transaction kullanmaz. Yeni fotoğraf
 * Room'a yazılamazsa ve referanssız olduğu doğrulanırsa temizlenir;
 * eski fotoğraf ise yalnızca Room güncellemesi başarılı olduktan
 * sonra silinmeye çalışılır.
 */
class PersonImageMutationUseCase @Inject constructor(
    private val getPersonByIdUseCase: GetPersonByIdUseCase,
    private val savePersonUseCase: SavePersonUseCase,
    private val deletePersonUseCase: DeletePersonUseCase,
    private val savePersonImageUseCase: SavePersonImageUseCase,
    private val deletePersonImageUseCase: DeletePersonImageUseCase,
    private val writeCoordinator: BackupWriteCoordinator
) {

    suspend fun updateImage(
        personId: String,
        sourceUri: String,
        imageType: PersonImageType
    ): Person = writeCoordinator.withRegularWrite {
        val existing = findPerson(personId)

        val oldPath = when (imageType) {
            PersonImageType.PROFILE -> existing.profileImagePath
            PersonImageType.COVER -> existing.coverImagePath
        }

        var newPath: String? = null
        var databaseUpdated = false

        try {
            val copiedPath = savePersonImageUseCase(
                personId = personId,
                sourceUri = sourceUri,
                imageType = imageType
            ) ?: error("Fotoğraf dosyası oluşturulamadı.")

            require(copiedPath.isNotBlank()) {
                "Fotoğraf dosya yolu boş olamaz."
            }
            newPath = copiedPath

            val updated = when (imageType) {
                PersonImageType.PROFILE ->
                    existing.copy(profileImagePath = copiedPath)

                PersonImageType.COVER ->
                    existing.copy(coverImagePath = copiedPath)
            }.copy(updatedAt = System.currentTimeMillis())

            // SavePersonUseCase de aynı kilidi kullanır; coordinator
            // bu coroutine içindeki iç içe çağrıyı destekler.
            savePersonUseCase(updated, isUpdate = true)
            databaseUpdated = true

            if (oldPath != copiedPath &&
                oldPath != updated.profileImagePath &&
                oldPath != updated.coverImagePath
            ) {
                deleteImageBestEffort(oldPath)
            }

            updated
        } finally {
            // Coroutine iptal edilse de yeni dosyanın DB'de kullanılıp
            // kullanılmadığını kontrol etmeden silme.
            if (!databaseUpdated && newPath != null) {
                deleteUnreferencedNewImage(personId, newPath)
            }
        }
    }

    suspend fun removeImage(
        personId: String,
        imageType: PersonImageType
    ): Person = writeCoordinator.withRegularWrite {
        val existing = findPerson(personId)

        val oldPath = when (imageType) {
            PersonImageType.PROFILE -> existing.profileImagePath
            PersonImageType.COVER -> existing.coverImagePath
        }

        val updated = when (imageType) {
            PersonImageType.PROFILE ->
                existing.copy(profileImagePath = null)

            PersonImageType.COVER ->
                existing.copy(coverImagePath = null)
        }.copy(updatedAt = System.currentTimeMillis())

        savePersonUseCase(updated, isUpdate = true)

        // Aynı dosya hâlâ diğer fotoğraf alanında kullanılıyorsa silinmez.
        if (oldPath != updated.profileImagePath &&
            oldPath != updated.coverImagePath
        ) {
            deleteImageBestEffort(oldPath)
        }
        updated
    }

    suspend fun deletePersonAndImages(personId: String) {
        writeCoordinator.withRegularWrite {
            val existing = getPersonByIdUseCase(personId)

            // Önce Room kaydı silinir; hata oluşursa fotoğraflar korunur.
            deletePersonUseCase(personId)

            deleteImageBestEffort(existing?.profileImagePath)
            deleteImageBestEffort(existing?.coverImagePath)
        }
    }

    private suspend fun findPerson(personId: String): Person =
        getPersonByIdUseCase(personId)
            ?: throw IllegalStateException(
                "Kişi bulunamadı veya geri yükleme sırasında değişti."
            )

    private suspend fun deleteImageBestEffort(path: String?) {
        if (path.isNullOrBlank()) return

        try {
            deletePersonImageUseCase(path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fotoğraf silinemezse daha sonra referanssız dosya
            // temizliği yapılabilir. Room güncellemesi geri alınmaz.
        }
    }

    private suspend fun deleteUnreferencedNewImage(
        personId: String,
        path: String
    ) = withContext(NonCancellable) {
        // DB okunamıyorsa silmek yerine dosyayı koruruz.
        val current = try {
            getPersonByIdUseCase(personId)
        } catch (_: Exception) {
            return@withContext
        }

        // Mevcut fotoğraf yolu bu dosyaya işaret ediyorsa silinmez.
        if (current?.profileImagePath == path ||
            current?.coverImagePath == path
        ) {
            return@withContext
        }

        // Kişi kaydı yoksa ya da artık bu görseli kullanmıyorsa,
        // yalnızca PersonImageRepository'nin güvenli silme işlemi çağrılır.
        try {
            deletePersonImageUseCase(path)
        } catch (_: Exception) {
            // Öncelik mevcut kayıtların güvenliğidir.
        }
    }
}
