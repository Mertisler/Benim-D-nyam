package com.loc.benimdunyam.feature.journal

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val PaperInk = Color(0xFF33271F)
private val TapeInk = Color(0xFF493B2B)
private val TapeColor = Color(0xFFE8DDC9)
private val SaveGreen = Color(0xFF365A43)

@Composable
fun JournalEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: JournalEditorViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current

    var seasonMenuExpanded by remember { mutableStateOf(false) }
    var episodeMenuExpanded by remember { mutableStateOf(false) }

    val selectedSeason = uiState.allSeasons.find { it.id == uiState.selectedSeasonId }
    val selectedEpisode = uiState.currentSeasonEpisodes.find { it.id == uiState.selectedEpisodeId }

    // Tarih değiştiğinde DatePicker da güncel tarihi başlangıç olarak kullanır.
    val datePickerDialog = remember(context, uiState.entryDate) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = uiState.entryDate
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                viewModel.onDateChanged(selectedCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val dayFormat = remember { SimpleDateFormat("dd", Locale("tr", "TR")) }
    val fullDateFormat = remember { SimpleDateFormat("MMM yyyy EEEE", Locale("tr", "TR")) }
    val entryDate = Date(uiState.entryDate)
    val dateLabel = "${dayFormat.format(entryDate)} ${fullDateFormat.format(entryDate)}"

    Box(modifier = Modifier.fillMaxSize()) {
        // Düz renk yerine kâğıt dokusu, hafif eskime izleri ve botanik çizimler.
        ParchmentBackground(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // İlk görseldeki gibi fotoğraf, etiketler ve üst aksiyonlar aynı alanda.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(244.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 56.dp, end = 12.dp, top = 28.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        PolaroidDateCard(
                            imagePath = selectedSeason?.coverImagePath,
                            imageDescription = selectedSeason?.title ?: "Sezon fotoğrafı",
                            dateLabel = dateLabel,
                            onClick = { datePickerDialog.show() }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                TapeLabel(
                                    text = selectedSeason?.title ?: "Dönem seçin",
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = TapeInk,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    rotation = 1.2f,
                                    onClick = { seasonMenuExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = seasonMenuExpanded,
                                    onDismissRequest = { seasonMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Dönem yok") },
                                        onClick = {
                                            viewModel.onSeasonSelected(null)
                                            seasonMenuExpanded = false
                                        }
                                    )
                                    uiState.allSeasons.forEach { season ->
                                        DropdownMenuItem(
                                            text = { Text(season.title) },
                                            onClick = {
                                                viewModel.onSeasonSelected(season.id)
                                                seasonMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                TapeLabel(
                                    text = selectedEpisode?.let {
                                        "Bölüm ${it.orderIndex}: ${it.title}"
                                    } ?: "Bölüm seçin",
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = if (uiState.selectedSeasonId != null) TapeInk else TapeInk.copy(alpha = 0.45f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    rotation = -1f,
                                    enabled = uiState.selectedSeasonId != null,
                                    onClick = { episodeMenuExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = episodeMenuExpanded,
                                    onDismissRequest = { episodeMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Bölüm yok") },
                                        onClick = {
                                            viewModel.onEpisodeSelected(null)
                                            episodeMenuExpanded = false
                                        }
                                    )
                                    uiState.currentSeasonEpisodes.forEach { episode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${episode.orderIndex}. Bölüm: ${episode.title}")
                                            },
                                            onClick = {
                                                viewModel.onEpisodeSelected(episode.id)
                                                episodeMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Geri ve kaydet kontrolleri fotoğraf kartının üzerine yerleşir.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 12.dp, top = 38.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Geri",
                                tint = PaperInk,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        RoughSaveButton(
                            onClick = {
                                viewModel.saveJournal(onSuccess = onNavigateBack)
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                ) {
                    if (uiState.errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(7.dp),
                            color = Color(0xFFF0DAD3)
                        ) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                color = Color(0xFF7B3028),
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    BasicTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 38.dp),
                        singleLine = true,
                        cursorBrush = SolidColor(PaperInk),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = PaperInk,
                            fontSize = 25.sp,
                            lineHeight = 33.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (uiState.title.isEmpty()) {
                                    Text(
                                        text = "Başlığı girin...",
                                        color = PaperInk,
                                        fontSize = 25.sp,
                                        lineHeight = 33.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Yazı alanı boşken ilk görseldeki koyu, kitap serif yazı kullanılır.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        BasicTextField(
                            value = uiState.content,
                            onValueChange = viewModel::onContentChanged,
                            modifier = Modifier.fillMaxSize(),
                            cursorBrush = SolidColor(PaperInk),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = PaperInk,
                                fontSize = 17.sp,
                                lineHeight = 26.sp,
                                fontFamily = FontFamily.Serif
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (uiState.content.isEmpty()) {
                                        Text(
                                            text = "Buraya daha fazla yazın...",
                                            color = PaperInk,
                                            fontSize = 17.sp,
                                            lineHeight = 26.sp,
                                            fontFamily = FontFamily.Serif
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // @ ile kişi yazılırken öneriler sağ üstte açılır.
                        if (uiState.isMentionPopupVisible) {
                            val filteredPeople = uiState.allPeople.filter {
                                it.name.contains(uiState.mentionQuery, ignoreCase = true)
                            }

                            if (filteredPeople.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .width(220.dp)
                                        .heightIn(max = 180.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF4EDE2),
                                    shadowElevation = 7.dp
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        items(filteredPeople, key = { it.id }) { person ->
                                            Surface(
                                                onClick = {
                                                    viewModel.selectPersonForMention(person)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = SaveGreen,
                                                        modifier = Modifier.size(27.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = person.name.take(1).uppercase(),
                                                                color = Color.White,
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "@${person.name}",
                                                        color = PaperInk,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.mentionedPeople.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.mentionedPeople, key = { it.id }) { person ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        viewModel.removeMentionedPerson(person)
                                    },
                                    label = { Text(person.name) },
                                    avatar = {
                                        Surface(
                                            shape = CircleShape,
                                            color = SaveGreen,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = person.name.take(1).uppercase(),
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
                }
            }

            JournalBottomToolbar()
        }
    }
}

@Composable
private fun PolaroidDateCard(
    imagePath: String?,
    imageDescription: String,
    dateLabel: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(170.dp)
            .height(200.dp)
            .graphicsLayer { rotationZ = -3f },
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFBF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 7.dp, top = 7.dp, end = 7.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFFE9E1D5))
            ) {
                if (!imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = imageDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "Sezon görseli seçilmedi",
                        tint = Color(0xFF9A8D7A),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = dateLabel,
                color = Color(0xFF3E342C),
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun TapeLabel(
    text: String,
    icon: @Composable () -> Unit,
    rotation: Float,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .graphicsLayer { rotationZ = rotation }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val tapePath = Path().apply {
                moveTo(0f, size.height * 0.08f)
                lineTo(size.width * 0.96f, 0f)
                lineTo(size.width, size.height * 0.78f)
                lineTo(size.width * 0.91f, size.height)
                lineTo(size.width * 0.04f, size.height * 0.88f)
                close()
            }
            drawPath(
                path = tapePath,
                color = TapeColor.copy(alpha = if (enabled) 0.96f else 0.68f)
            )
            drawLine(
                color = Color(0xFFB6A78E).copy(alpha = 0.24f),
                start = Offset(size.width * 0.08f, size.height * 0.83f),
                end = Offset(size.width * 0.88f, size.height * 0.91f),
                strokeWidth = 1.dp.toPx()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = TapeInk.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Seçenekleri aç",
                tint = TapeInk.copy(alpha = if (enabled) 0.9f else 0.45f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun RoughSaveButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 94.dp, height = 44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = SaveGreen,
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            )

            val roughBorder = Path().apply {
                moveTo(size.width * 0.04f, size.height * 0.18f)
                lineTo(size.width * 0.10f, size.height * 0.08f)
                lineTo(size.width * 0.92f, size.height * 0.06f)
                lineTo(size.width * 0.97f, size.height * 0.20f)
                lineTo(size.width * 0.95f, size.height * 0.82f)
                lineTo(size.width * 0.87f, size.height * 0.94f)
                lineTo(size.width * 0.08f, size.height * 0.91f)
                lineTo(size.width * 0.03f, size.height * 0.76f)
                close()
            }
            drawPath(
                path = roughBorder,
                color = Color(0xFFE7E0CB),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = Color(0xFFE7E0CB).copy(alpha = 0.55f),
                start = Offset(size.width * 0.18f, size.height * 0.17f),
                end = Offset(size.width * 0.82f, size.height * 0.15f),
                strokeWidth = 0.7.dp.toPx()
            )
        }

        Text(
            text = "Kaydet",
            color = Color(0xFFF9F5E9),
            fontSize = 17.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun JournalBottomToolbar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val tornTop = Path().apply {
                moveTo(0f, 9.dp.toPx())
                val segments = 34
                for (index in 0..segments) {
                    val x = size.width * index / segments
                    val wobble = when (index % 4) {
                        0 -> 3.dp.toPx()
                        1 -> 0.dp.toPx()
                        2 -> 5.dp.toPx()
                        else -> 1.dp.toPx()
                    }
                    lineTo(x, 7.dp.toPx() + wobble)
                }
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = tornTop, color = Color(0xFFE8DECC))
            drawLine(
                color = Color(0xFFB7A78F).copy(alpha = 0.45f),
                start = Offset(0f, 9.dp.toPx()),
                end = Offset(size.width, 9.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp, bottom = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIcon(
                image = { Icon(Icons.Outlined.TextFields, contentDescription = "Metin biçimi", tint = PaperInk) }
            )
            ToolbarIcon(
                image = { Icon(Icons.Outlined.Image, contentDescription = "Görsel ekle", tint = PaperInk) }
            )
            ToolbarIcon(
                image = { Icon(Icons.Outlined.Star, contentDescription = "Yıldız ekle", tint = PaperInk) }
            )
            ToolbarIcon(
                image = { Icon(Icons.Outlined.EmojiEmotions, contentDescription = "Emoji ekle", tint = PaperInk) }
            )
            ToolbarIcon(
                image = { Icon(Icons.Outlined.TextFields, contentDescription = "Metin ekle", tint = PaperInk) }
            )
        }
    }
}

@Composable
private fun ToolbarIcon(image: @Composable () -> Unit) {
    IconButton(
        onClick = { /* Araçların işlevleri sonraki geliştirme adımında bağlanabilir. */ },
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            modifier = Modifier.size(27.dp),
            contentAlignment = Alignment.Center
        ) {
            image()
        }
    }
}

@Composable
private fun ParchmentBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF4EBDD),
                    Color(0xFFF7F0E5),
                    Color(0xFFF0E6D8)
                )
            )
        )

        // Kenarlardaki hafif yaşlanma ve kâğıt dokusu.
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF9C8868).copy(alpha = 0.13f),
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF9C8868).copy(alpha = 0.10f)
                )
            )
        )

        val random = Random(37)
        repeat(260) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val radius = random.nextFloat() * 1.4f + 0.25f
            val alpha = random.nextFloat() * 0.055f + 0.012f
            drawCircle(
                color = Color(0xFF806F55).copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }

        // Eski defter sayfasındaki belli belirsiz yatay kat izi.
        drawLine(
            color = Color(0xFF9C8C73).copy(alpha = 0.10f),
            start = Offset(0f, size.height * 0.51f),
            end = Offset(size.width, size.height * 0.51f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(0f, size.height * 0.51f + 2.dp.toPx()),
            end = Offset(size.width, size.height * 0.51f + 2.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // Kenarlardaki ince botanik çizimler.
        drawSprig(
            baseX = 20.dp.toPx(),
            baseY = size.height * 0.98f,
            scale = 150.dp.toPx(),
            direction = 1f
        )
        drawSprig(
            baseX = size.width - 20.dp.toPx(),
            baseY = size.height * 0.98f,
            scale = 170.dp.toPx(),
            direction = -1f
        )
        drawSprig(
            baseX = 4.dp.toPx(),
            baseY = size.height * 0.29f,
            scale = 90.dp.toPx(),
            direction = 1f
        )
    }
}

private fun DrawScope.drawSprig(
    baseX: Float,
    baseY: Float,
    scale: Float,
    direction: Float
) {
    val botanicalInk = Color(0xFF81745F).copy(alpha = 0.42f)
    val stem = Path().apply {
        moveTo(baseX, baseY)
        cubicTo(
            baseX + direction * scale * 0.06f,
            baseY - scale * 0.28f,
            baseX + direction * scale * 0.32f,
            baseY - scale * 0.57f,
            baseX + direction * scale * 0.22f,
            baseY - scale
        )
    }
    drawPath(stem, botanicalInk, style = Stroke(width = 1.15.dp.toPx()))

    for (index in 1..5) {
        val progress = index / 6f
        val leafX = baseX + direction * scale * (0.02f + progress * 0.22f)
        val leafY = baseY - scale * progress * 0.88f
        val leafSize = scale * (0.075f - progress * 0.004f)

        drawOval(
            color = botanicalInk,
            topLeft = Offset(leafX - leafSize * 0.75f, leafY - leafSize * 0.28f),
            size = Size(leafSize, leafSize * 0.38f)
        )
        drawOval(
            color = botanicalInk.copy(alpha = 0.8f),
            topLeft = Offset(
                leafX - direction * leafSize * 0.30f,
                leafY - leafSize * 0.65f
            ),
            size = Size(leafSize, leafSize * 0.38f)
        )
    }

    val flowerX = baseX + direction * scale * 0.22f
    val flowerY = baseY - scale
    drawCircle(
        color = botanicalInk,
        radius = scale * 0.025f,
        center = Offset(flowerX, flowerY)
    )
    for (petal in 0..4) {
        val angle = petal * (Math.PI * 2.0 / 5.0)
        val petalX = flowerX + kotlin.math.cos(angle).toFloat() * scale * 0.045f
        val petalY = flowerY + kotlin.math.sin(angle).toFloat() * scale * 0.045f
        drawCircle(
            color = botanicalInk.copy(alpha = 0.8f),
            radius = scale * 0.025f,
            center = Offset(petalX, petalY)
        )
    }
}
