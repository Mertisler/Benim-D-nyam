package com.loc.benimdunyam.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveSeasonStore @Inject constructor() {
    private val _selectedSeasonId = MutableStateFlow<String?>(null)
    val selectedSeasonId: StateFlow<String?> = _selectedSeasonId.asStateFlow()

    private val _selectedEpisodeId = MutableStateFlow<String?>(null)
    val selectedEpisodeId: StateFlow<String?> = _selectedEpisodeId.asStateFlow()

    fun setActiveSeason(seasonId: String?) {
        _selectedSeasonId.value = seasonId
        // Sezon değiştiğinde bölümü sıfırlayabiliriz veya koruyabiliriz
        if (seasonId == null) {
            _selectedEpisodeId.value = null
        }
    }

    fun setActiveEpisode(episodeId: String?) {
        _selectedEpisodeId.value = episodeId
    }
}