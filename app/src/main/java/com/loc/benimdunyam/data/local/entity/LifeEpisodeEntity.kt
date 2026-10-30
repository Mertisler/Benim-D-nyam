package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.loc.benimdunyam.domain.model.LifeEpisode

@Entity(tableName = "life_episodes")
data class LifeEpisodeEntity(
    @PrimaryKey val id: String,
    val seasonId: String,
    val title: String,
    val description: String,
    val orderIndex: Int,
    val startDate: Long,
    val endDate: Long?,
    val createdAt: Long
) {
    fun toDomain() = LifeEpisode(
        id = id,
        seasonId = seasonId,
        title = title,
        description = description,
        orderIndex = orderIndex,
        startDate = startDate,
        endDate = endDate,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(model: LifeEpisode) = LifeEpisodeEntity(
            id = model.id,
            seasonId = model.seasonId,
            title = model.title,
            description = model.description,
            orderIndex = model.orderIndex,
            startDate = model.startDate,
            endDate = model.endDate,
            createdAt = model.createdAt
        )
    }
}