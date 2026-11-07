package com.loc.benimdunyam.feature.people.network.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.LifeSeason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleNetworkFilters(
    seasons: List<LifeSeason>,
    seasonId: String?,
    onlyWithMemories: Boolean,
    searchQuery: String,
    onSeasonSelected: (String?) -> Unit,
    onOnlyWithMemoriesChanged: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Haritadaki karakteri ara") }
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = seasonId == null,
                    onClick = { onSeasonSelected(null) },
                    label = { Text("Tüm sezonlar") }
                )
            }
            items(seasons, key = { it.id }) { season ->
                FilterChip(
                    selected = seasonId == season.id,
                    onClick = { onSeasonSelected(season.id) },
                    label = { Text(season.title, maxLines = 1) }
                )
            }
        }
        FilterChip(
            selected = onlyWithMemories,
            onClick = { onOnlyWithMemoriesChanged(!onlyWithMemories) },
            label = { Text(if (onlyWithMemories) "Yalnızca anısı olanlar" else "Tüm karakterler") }
        )
    }
}
