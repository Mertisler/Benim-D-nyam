package com.loc.benimdunyam.feature.yearreview

import android.content.Context
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodReport
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val MovieGreen = Color(0xFF11291F)
private val MovieGold = Color(0xFFE7C68A)
private val MovieCream = Color(0xFFFAF5E9)

/**
 * Bilerek yeni bir NavHost route'u veya ViewModel açmaz: YearReviewScreen'in
 * güncel raporunu kullanır. Geri / kapat mevcut yıl özetini gösterir.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun YearMovieScreen(
    report: YearReviewReport,
    coverPaths: Map<String, String>,
    featuredMemories: List<JournalEntry> = emptyList(),
    moodReport: YearMoodReport? = null,
    onClose: () -> Unit,
    onJournalClick: (String) -> Unit
) {
    // Filmin başında gelen snapshot sabittir: yükleme sırasında sahne sayısı zıplamaz.
    val frames = remember(report, coverPaths, featuredMemories, moodReport) {
        YearMovieStoryboard.build(report, coverPaths, featuredMemories, moodReport)
    }
    if (frames.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    var frameIndex by remember(report.seasonId ?: report.year.toString()) { mutableIntStateOf(0) }
    var replayVersion by remember(report.seasonId ?: report.year.toString()) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var paused by remember(report.seasonId ?: report.year.toString()) {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    // Film içi tercihtir; kalıcı ayar veya yeni DataStore bağımlılığı istemez.
    var reducedMotion by remember(report.seasonId ?: report.year.toString()) {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    val progress = remember(report.seasonId ?: report.year.toString()) { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val safeIndex = frameIndex.coerceIn(0, frames.lastIndex)
    val frame = frames[safeIndex]

    fun previous() {
        if (frameIndex > 0) frameIndex-- else onClose()
    }
    fun next() {
        if (frameIndex < frames.lastIndex) {
            frameIndex++
        } else {
            onClose()
        }
    }
    fun replay() {
        paused = true
        frameIndex = 0
        replayVersion++
        paused = false
    }

    BackHandler(onBack = onClose)

    // Tek coroutine: sahne sıfırlama ve sayaç asla birbirleriyle yarışmaz.
    // Duraklat / arka plana geç / devam et: aynı sahnede kalan süre korunur.
    var lastPlaybackKey by remember(report.seasonId ?: report.year.toString()) { mutableStateOf<Pair<Int, Int>?>(null) }
    val playbackKey = safeIndex to replayVersion
    LaunchedEffect(playbackKey, paused, foreground) {
        if (lastPlaybackKey != playbackKey) {
            progress.snapTo(0f)
            lastPlaybackKey = playbackKey
        }
        if (paused || !foreground) return@LaunchedEffect
        val remaining = MovieMotionRules.remainingMillis(frame.durationMillis, progress.value)
        progress.animateTo(1f, animationSpec = tween(remaining, easing = LinearEasing))
        // Kullanıcı sahne değiştirmişse eski coroutine başka sahneyi atlatamaz.
        if (playbackKey == (frameIndex to replayVersion)) {
            if (frameIndex < frames.lastIndex) frameIndex++ else paused = true
        }
    }
    val visibleProgress = if (lastPlaybackKey == playbackKey) progress.value else 0f

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                when (frame.chapter) {
                    YearMovieChapter.OPENING, YearMovieChapter.FINALE ->
                        listOf(Color(0xFF10261F), Color(0xFF244A36), Color(0xFF0E221B))
                    YearMovieChapter.MOOD ->
                        listOf(Color(0xFF152C29), Color(0xFF35524D), Color(0xFF162C29))
                    YearMovieChapter.MONTH_STORY ->
                        listOf(Color(0xFF142D24), Color(0xFF36513C), Color(0xFF12251C))
                    YearMovieChapter.PHOTOS ->
                        listOf(Color(0xFF16251F), Color(0xFF384534), Color(0xFF15221C))
                    else -> listOf(MovieGreen, Color(0xFF253E31), MovieGreen)
                }
            )
        )
    ) {
        MovieAtmosphere(frame.chapter, visibleProgress, reducedMotion)
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding()
                .padding(top = 12.dp, bottom = 14.dp)
        ) {
            // Uzun filmde 20+ ince çizgi yerine aktif sahnenin çevresindeki 7 adım.
            val firstVisible = (safeIndex - 3).coerceIn(
                0, (frames.size - 7).coerceAtLeast(0)
            )
            val visibleFrameIndices = (firstVisible until
                    (firstVisible + 7).coerceAtMost(frames.size))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visibleFrameIndices.forEach { index ->
                    val filled = when {
                        index < safeIndex -> 1f
                        index == safeIndex -> visibleProgress
                        else -> 0f
                    }.coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).height(3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.28f))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(filled).fillMaxHeight()
                                .background(MovieGold)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("BENİM DÜNYAM • ${report.year}", color = MovieGold,
                        fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Text("${safeIndex + 1} / ${frames.size} · ${frame.title}",
                        color = MovieCream.copy(alpha = 0.80f), fontSize = 12.sp)
                }
                TextButton(onClick = {
                    // Finalde tamamlanmış ilerleme yeniden oynatılabilir.
                    if (paused && safeIndex == frames.lastIndex && progress.value >= 0.999f) replay()
                    else paused = !paused
                }) {
                    Text(
                        if (paused) "Oynat" else "Duraklat",
                        color = MovieCream, fontSize = 12.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Filmi kapat", tint = MovieCream)
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .then(if (frame.chapter == YearMovieChapter.PHOTOS) Modifier else Modifier.pointerInput(safeIndex, frames.size) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                total += dragAmount
                            },
                            onDragEnd = {
                                if (total > 85f) previous()
                                else if (total < -85f) next()
                                total = 0f
                            }
                        )
                    })
            ) {
                AnimatedContent(
                    // index kullanıyoruz: ardışık FEATURED sahneleri aynı enum olsa da geçiş yapmalı.
                    targetState = safeIndex,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(120)).togetherWith(fadeOut(tween(120)))
                        } else {
                            (fadeIn(tween(620)) + scaleIn(initialScale = 0.955f, animationSpec = tween(620)) +
                                    slideInVertically(tween(620)) { it / 14 })
                                .togetherWith(
                                    fadeOut(tween(320)) + scaleOut(targetScale = 1.025f, animationSpec = tween(320))
                                )
                        }
                    },
                    label = "Yılın filmi sahne geçişi"
                ) { visibleIndex ->
                    val visibleFrame = frames[visibleIndex.coerceIn(0, frames.lastIndex)]
                    CompositionLocalProvider(LocalMovieReducedMotion provides reducedMotion) {
                        YearMovieSceneContent(
                            chapter = visibleFrame.chapter,
                            featuredMemory = featuredMemories.firstOrNull { it.id == visibleFrame.journalId },
                            moodReport = moodReport,
                            monthStory = report.monthlyStories.firstOrNull {
                                it.monthIndex == visibleFrame.monthIndex &&
                                        it.storyYear == visibleFrame.monthYear
                            },
                            sceneProgress = if (visibleIndex == safeIndex) visibleProgress else 0f,
                            report = report,
                            coverPaths = coverPaths,
                            onJournalClick = onJournalClick,
                            onReplay = ::replay,
                            onClose = onClose
                        )
                    }
                }

                // Ortadaki içerik/dokunulabilir anı kartları engellenmesin diye
                // sadece ince kenar bölgeleri sahne değiştirmek için kullanılır.
                Box(
                    modifier = Modifier.align(Alignment.CenterStart).width(33.dp)
                        .fillMaxHeight().clickable(onClickLabel = "Önceki sahne") { previous() }
                )
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).width(33.dp)
                        .fillMaxHeight().clickable(onClickLabel = "Sonraki sahne") { next() }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = ::previous,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MovieCream.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MovieCream)
                ) {
                    Text(if (safeIndex == 0) "Özete dön" else "Önceki")
                }
                Button(
                    onClick = ::next,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MovieGold, contentColor = MovieGreen
                    )
                ) {
                    Text(if (safeIndex == frames.lastIndex) "Bitti" else "Sonraki",
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Kaydır • Dokun • Duraklat",
                    modifier = Modifier.weight(1f),
                    color = MovieCream.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
                TextButton(onClick = { reducedMotion = !reducedMotion }) {
                    Text(
                        text = if (reducedMotion) "Efektleri aç" else "Efektleri azalt",
                        color = MovieGold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
