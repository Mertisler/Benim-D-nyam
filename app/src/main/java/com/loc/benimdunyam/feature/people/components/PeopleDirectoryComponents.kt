package com.loc.benimdunyam.feature.people.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.loc.benimdunyam.domain.model.Person
import java.io.File
import java.util.Locale

/** Büyük tekrar eden kartlar yerine tek bir hafif satır. */
@Composable
fun PeoplePersonRow(person: Person, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PersonDirectoryAvatar(
            name = person.name,
            profileImagePath = person.profileImagePath
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (person.uniqueJournalCount > 0) {
                    "${person.uniqueJournalCount} ortak anı"
                } else {
                    "Henüz birlikte kaydedilmiş anı yok"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PersonDirectoryAvatar(name: String, profileImagePath: String?) {
    val photoFile = remember(profileImagePath) {
        profileImagePath?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }
    Surface(
        modifier = Modifier.size(54.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().take(1).uppercase(Locale.forLanguageTag("tr-TR")),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            if (photoFile != null) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = "$name profil fotoğrafı",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(CircleShape)
                )
            }
        }
    }
}

/** Arama boş sonucu ile gerçekten boş kişi arşivini ayırır. */
@Composable
fun PeopleDirectoryEmptyState(
    hasSearch: Boolean,
    isRestoring: Boolean,
    onClearSearch: () -> Unit,
    onAddPerson: () -> Unit,
    onImportContacts: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasSearch) Icons.Default.SearchOff else Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = if (hasSearch) "Aradığın kişiyi bulamadım" else "Hikâyendeki ilk insanı ekle",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (hasSearch) {
                "Başka bir isim deneyebilir veya aramayı temizleyebilirsin."
            } else {
                "Hayatında yer alan insanları ekle; birlikte yazılan anılar zamanla burada biriksin."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        if (hasSearch) {
            TextButton(onClick = onClearSearch) { Text("Aramayı temizle") }
        } else if (!isRestoring) {
            Button(onClick = onAddPerson) { Text("Kişi ekle") }
            OutlinedButton(onClick = onImportContacts) { Text("Rehberden aktar") }
        }
    }
}
