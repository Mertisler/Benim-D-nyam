package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.PersonImageType

/** Stores person images in app-private storage and returns the absolute file path. */
interface PersonImageRepository {
    suspend fun saveImage(
        personId: String,
        sourceUri: String,
        imageType: PersonImageType
    ): String

    suspend fun deleteImage(path: String?)
}
