package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.repository.PersonImageRepository
import javax.inject.Inject

class DeletePersonImageUseCase @Inject constructor(
    private val repository: PersonImageRepository
) {
    suspend operator fun invoke(path: String?) {
        repository.deleteImage(path)
    }
}
