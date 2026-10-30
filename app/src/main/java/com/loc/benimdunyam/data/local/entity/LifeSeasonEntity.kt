package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.loc.benimdunyam.domain.model.LifeSeason

@Entity(tableName = "life_seasons")
data class LifeSeasonEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val startDate: Long,
    val endDate: Long?,
    val colorHex: String,
    val coverImagePath: String? = null, // Kapak resmi yolu
    val createdAt: Long
) {
    fun toDomain() = LifeSeason(
        id = id,
        title = title,
        description = description,
        startDate = startDate,
        endDate = endDate,
        colorHex = colorHex,
        coverImagePath = coverImagePath, // Eksiksiz aktarılıyor
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(model: LifeSeason) = LifeSeasonEntity(
            id = model.id,
            title = model.title,
            description = model.description,
            startDate = model.startDate,
            endDate = model.endDate,
            colorHex = model.colorHex,
            coverImagePath = model.coverImagePath, // Eksiksiz aktarılıyor
            createdAt = model.createdAt
        )
    }
}