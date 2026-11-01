package com.loc.benimdunyam.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loc.benimdunyam.domain.model.JournalEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentJournalsSection(
    recentJournals: List<JournalEntry>,
    onJournalClick: (String) -> Unit,
    onCreateJournalClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = "Son Anılar",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF332D27)
        )

        if (recentJournals.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF0EAE1),
                border = BorderStroke(1.dp, Color(0xFFE5DDD1))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Henüz bir anı yazmadın.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF514A41)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Günün içinden küçük bir anı bile burada güzel bir hatıraya dönüşebilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF81796F)
                    )
                    TextButton(onClick = onCreateJournalClick) {
                        Text(
                            text = "İlk anını yaz",
                            color = Color(0xFF52745B)
                        )
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recentJournals, key = { it.id }) { journal ->
                    HorizontalJournalCard(
                        journal = journal,
                        onClick = { onJournalClick(journal.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HorizontalJournalCard(
    journal: JournalEntry,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("d MMM, yyyy", Locale("tr", "TR"))
    val formattedDate = dateFormat.format(Date(journal.entryDate))

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(139.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0EAE1)
        ),
        border = BorderStroke(1.dp, Color(0xFFE5DDD1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF62816C)
                )
                Text(
                    text = journal.content.ifBlank { journal.title },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF514A41),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (journal.mentionedPeople.isNotEmpty()) {
                val firstPerson = journal.mentionedPeople.first()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF6E947B),
                        modifier = Modifier.size(21.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstPerson.name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    Text(
                        text = firstPerson.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF716A60),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
