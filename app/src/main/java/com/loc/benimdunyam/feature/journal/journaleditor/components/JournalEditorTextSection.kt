package com.loc.benimdunyam.feature.journal.journaleditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.BasicTextField

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun <Person : Any> JournalEditorTextSection(
    title: String,
    content: String,
    errorMessage: String?,

    isMentionPopupVisible: Boolean,
    mentionQuery: String,

    allPeople: List<Person>,
    mentionedPeople: List<Person>,

    personKey: (Person) -> Any,
    personName: (Person) -> String,

    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,

    onPersonSelected: (Person) -> Unit,
    onMentionRemoved: (Person) -> Unit,

    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {

        // HATA MESAJI

        errorMessage?.let { error ->

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),

                shape = RoundedCornerShape(7.dp),

                color = JournalEditorPalette.ErrorBackground
            ) {

                Text(
                    text = error,
                    color = JournalEditorPalette.ErrorText,

                    modifier = Modifier.padding(10.dp),

                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // BAŞLIK

        BasicTextField(
            value = title,
            onValueChange = onTitleChanged,

            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp),

            singleLine = true,

            cursorBrush = SolidColor(
                JournalEditorPalette.PaperInk
            ),

            textStyle = TextStyle(
                color = JournalEditorPalette.PaperInk,
                fontSize = 25.sp,
                lineHeight = 33.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal
            ),

            decorationBox = { innerTextField ->

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    if (title.isEmpty()) {

                        Text(
                            text = "Başlığı girin...",
                            color = JournalEditorPalette.PaperInk,
                            fontSize = 25.sp,
                            lineHeight = 33.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    innerTextField()
                }
            }
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        // GÜNLÜK İÇERİĞİ

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

            BasicTextField(
                value = content,
                onValueChange = onContentChanged,

                modifier = Modifier.fillMaxSize(),

                cursorBrush = SolidColor(
                    JournalEditorPalette.PaperInk
                ),

                textStyle = TextStyle(
                    color = JournalEditorPalette.PaperInk,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Serif
                ),

                decorationBox = { innerTextField ->

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        if (content.isEmpty()) {

                            Text(
                                text = "Buraya daha fazla yazın...",
                                color = JournalEditorPalette.PaperInk,
                                fontSize = 17.sp,
                                lineHeight = 26.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }

                        innerTextField()
                    }
                }
            )

            // KİŞİ ÖNERİLERİ

            if (isMentionPopupVisible) {

                JournalMentionSuggestions(
                    people = allPeople,
                    query = mentionQuery,
                    personKey = personKey,
                    personName = personName,
                    onPersonSelected = onPersonSelected,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                )
            }
        }

        // ETİKETLENMİŞ KİŞİLER

        if (mentionedPeople.isNotEmpty()) {

            JournalMentionedPeople(
                people = mentionedPeople,
                personKey = personKey,
                personName = personName,
                onRemove = onMentionRemoved
            )
        }
    }
}