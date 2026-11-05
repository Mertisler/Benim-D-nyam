package com.loc.benimdunyam.feature.seasons.episodes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSeasonDialogWithCover(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form alanları
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    // Bu alanda artık content:// URI değil, uygulama içindeki
    // kopyalanmış fotoğrafın mutlak dosya yolu tutulur.
    var coverImagePath by rememberSaveable { mutableStateOf<String?>(null) }

    var imagePickerError by remember { mutableStateOf<String?>(null) }
    var isCopyingImage by remember { mutableStateOf(false) }

    fun dismissDialog() {
        // Henüz Room'a kaydedilmemiş geçici resmi temizle.
        deleteSeasonCoverFromInternalStorage(
            context = context,
            path = coverImagePath
        )
        onDismiss()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isCopyingImage = true
                imagePickerError = null

                try {
                    // URI erişimi yalnızca kopyalama süresince gerekir.
                    // Kopyalama uygulamanın özel alanına yapıldığı için
                    // kalıcı galeri URI izni saklamamız gerekmez.
                    val newPath = withContext(Dispatchers.IO) {
                        copySeasonCoverToInternalStorage(
                            context = context,
                            sourceUri = uri
                        )
                    }

                    val previousPath = coverImagePath
                    coverImagePath = newPath

                    // Yeni resim başarıyla kopyalandıktan sonra
                    // önceki seçimin geçici kopyasını sil.
                    withContext(Dispatchers.IO) {
                        deleteSeasonCoverFromInternalStorage(
                            context = context,
                            path = previousPath
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    imagePickerError =
                        "Fotoğraf uygulamaya kaydedilemedi. Lütfen tekrar deneyin."
                } finally {
                    isCopyingImage = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isCopyingImage) {
                dismissDialog()
            }
        },
        title = {
            Text("Yeni Yaşam Dönemi Ekle")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text("Dönem Adı (Örn: Üniversite Yılları)")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(arrayOf("image/*"))
                    },
                    enabled = !isCopyingImage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Kapak fotoğrafı seç"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (coverImagePath == null) {
                            "Kapak Resmi Seç"
                        } else {
                            "Kapak Resmini Değiştir"
                        }
                    )
                }

                if (isCopyingImage) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Fotoğraf uygulamaya kaydediliyor...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (coverImagePath != null && !isCopyingImage) {
                    Text(
                        text = "Kapak fotoğrafı hazır ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                imagePickerError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        // Dosya burada silinmez. Dosya yolu ViewModel'e
                        // gönderilir ve LifeSeason ile Room'a kaydedilir.
                        onConfirm(
                            title.trim(),
                            description.trim(),
                            coverImagePath
                        )
                    }
                },
                enabled = title.isNotBlank() && !isCopyingImage
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { dismissDialog() },
                enabled = !isCopyingImage
            ) {
                Text("İptal")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
