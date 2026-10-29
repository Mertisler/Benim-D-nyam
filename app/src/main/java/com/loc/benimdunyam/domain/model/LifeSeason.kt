package com.loc.benimdunyam.domain.model

data class LifeSeason(
    val id: String,
    val title: String,
    val description: String,
    val startDate: Long,
    val endDate: Long?,
    val colorHex: String,
    val createdAt: Long
)