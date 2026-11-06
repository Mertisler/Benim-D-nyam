package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory
import com.loc.benimdunyam.domain.repository.PersonSpecialMemoryRepository
import javax.inject.Inject

class SavePersonSpecialMemoryUseCase @Inject constructor(
    private val repository: PersonSpecialMemoryRepository
) {
    suspend operator fun invoke(
        personId: String,
        journalId: String,
        category: PersonSpecialMemoryCategory,
        note: String?
    ) {
        require(personId.isNotBlank() && journalId.isNotBlank()) {
            "Kişi ve anı seçilmelidir."
        }
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        require(normalized == null || normalized.length <= 280) {
            "Özel not en fazla 280 karakter olabilir."
        }
        repository.save(personId, journalId, category, normalized)
    }
}
