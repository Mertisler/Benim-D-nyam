package com.loc.benimdunyam.feature.journal.journaleditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.InputChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme


@Composable
fun <Person : Any> JournalMentionedPeople(
    people: List<Person>,

    personKey: (Person) -> Any,
    personName: (Person) -> String,

    onRemove: (Person) -> Unit
) {

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 6.dp,
                bottom = 8.dp
            )
            .padding(horizontal = 0.dp)
    ) {

        items(
            items = people,
            key = personKey
        ) { person ->

            val name = personName(person)

            InputChip(
                selected = true,

                onClick = {
                    onRemove(person)
                },

                label = {
                    Text(name)
                },

                avatar = {

                    Surface(
                        shape = CircleShape,

                        color = JournalEditorPalette.SaveGreen,

                        modifier = Modifier.size(20.dp)
                    ) {

                        Box(
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = name
                                    .take(1)
                                    .uppercase(),

                                color = Color.White,

                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        }
    }
}