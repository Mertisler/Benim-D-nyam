package com.loc.benimdunyam.feature.journal.journaleditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight


@Composable
fun <Person : Any> JournalMentionSuggestions(
    people: List<Person>,
    query: String,

    personKey: (Person) -> Any,
    personName: (Person) -> String,

    onPersonSelected: (Person) -> Unit,

    modifier: Modifier = Modifier
) {

    val filteredPeople = people.filter { person ->

        personName(person).contains(
            query,
            ignoreCase = true
        )
    }

    if (filteredPeople.isEmpty()) return

    Surface(
        modifier = modifier
            .width(220.dp)
            .heightIn(max = 180.dp),

        shape = RoundedCornerShape(12.dp),

        color = JournalEditorPalette.MentionBackground,

        shadowElevation = 7.dp
    ) {

        LazyColumn(
            modifier = Modifier.padding(
                vertical = 6.dp,
                horizontal = 4.dp
            ),

            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            items(
                items = filteredPeople,
                key = personKey
            ) { person ->

                val name = personName(person)

                Surface(
                    onClick = {
                        onPersonSelected(person)
                    },

                    shape = RoundedCornerShape(8.dp),

                    color = androidx.compose.ui.graphics.Color.Transparent,

                    modifier = Modifier.fillMaxWidth()
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical = 7.dp
                            ),

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Surface(
                            shape = CircleShape,

                            color = JournalEditorPalette.SaveGreen,

                            modifier = Modifier.size(27.dp)
                        ) {

                            Box(
                                contentAlignment = Alignment.Center
                            ) {

                                Text(
                                    text = name
                                        .take(1)
                                        .uppercase(),

                                    color = androidx.compose.ui.graphics.Color.White,

                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Text(
                            text = "@$name",

                            color = JournalEditorPalette.PaperInk,

                            style = MaterialTheme.typography.bodyMedium,

                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}