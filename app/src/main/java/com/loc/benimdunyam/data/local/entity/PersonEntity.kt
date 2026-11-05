package com.loc.benimdunyam.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val profileImagePath: String? = null,
    val coverImagePath: String? = null
)
