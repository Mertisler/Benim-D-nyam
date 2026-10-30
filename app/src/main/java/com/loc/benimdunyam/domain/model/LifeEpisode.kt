package com.loc.benimdunyam.domain.model

data class LifeEpisode(
    val id: String,
    val seasonId: String,    // Hangi sezona (yaşam dönemine) ait olduğu
    val title: String,       // Bölüm Adı (Örn: Bölüm 1: Eve Çıkış)
    val description: String, // Bölüm açıklaması veya özeti
    val orderIndex: Int,     // Sezon içindeki kronolojik sıra numarası
    val startDate: Long,
    val endDate: Long?,
    val createdAt: Long
)