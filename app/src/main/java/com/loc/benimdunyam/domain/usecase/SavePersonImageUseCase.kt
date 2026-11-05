package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.PersonImageType
import com.loc.benimdunyam.domain.repository.PersonImageRepository
import javax.inject.Inject

class SavePersonImageUseCase @Inject constructor(
    private val repository: PersonImageRepository
) {
    suspend operator fun invoke(
        personId: String,
        sourceUri: String,
        imageType: PersonImageType
    ): String = repository.saveImage(personId, sourceUri, imageType)
}
