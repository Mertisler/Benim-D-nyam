package com.loc.benimdunyam.feature.journal.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val FabGreen = Color(0xFF496B5B)
private val FabBubble = Color(0xFFFFFEFA)
private val FabBubbleBorder = Color(0xFFDDE8DF)
private val FabHalo = Color(0xFFE6F1EA)
private val DogBrown = Color(0xFFC98B58)
private val DogDark = Color(0xFF4A3528)
private val DogCream = Color(0xFFFDF0DE)
private val DogPink = Color(0xFFF3A8AA)
private val DogEar = Color(0xFF9C603D)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun JournalDogActionFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGreeting by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3600L)
        showGreeting = false
    }

    val infiniteTransition = rememberInfiniteTransition(label = "JournalDogFabTransition")
    val fabScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "JournalDogFabScale"
    )

    val fabColor by animateColorAsState(
        targetValue = if (showGreeting) Color.Transparent else FabGreen,
        animationSpec = tween(420),
        label = "JournalDogFabColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (showGreeting) FabGreen else Color.White,
        animationSpec = tween(420),
        label = "JournalDogFabContentColor"
    )

    val fabShadow by animateDpAsState(
        targetValue = if (showGreeting) 0.dp else 8.dp,
        animationSpec = tween(420),
        label = "JournalDogFabShadow"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.scale(fabScale),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 8.dp, bottomEnd = 26.dp),
        color = fabColor,
        contentColor = contentColor,
        tonalElevation = if (showGreeting) 0.dp else 6.dp,
        shadowElevation = fabShadow
    ) {
        AnimatedContent(
            targetState = showGreeting,
            transitionSpec = {
                (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(animationSpec = tween(360)))
                    .togetherWith(
                        slideOutVertically(targetOffsetY = { -it / 3 }) + fadeOut(animationSpec = tween(260))
                    )
            },
            label = "JournalDogFabContent",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) { isGreeting ->
            if (isGreeting) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DogSpeechBubble(text = "Yeni anı?")
                    Box(
                        modifier = Modifier.size(78.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .background(color = FabHalo, shape = CircleShape)
                        )
                        AnimatedDogMascot(modifier = Modifier.size(70.dp), isCompact = false)
                        Text(
                            text = "✦",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 4.dp),
                            color = Color(0xFFE2A96A),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "✧",
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 4.dp, y = (-8).dp),
                            color = Color(0xFF82A897),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.defaultMinSize(minHeight = 28.dp)
                ) {
                    CompactDogBadge()
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yeni anı",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDogBadge() {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedDogMascot(
                modifier = Modifier.size(24.dp),
                isCompact = true
            )
        }
    }
}

@Composable
private fun DogSpeechBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FabBubble,
            contentColor = FabGreen,
            border = BorderStroke(1.dp, FabBubbleBorder),
            shadowElevation = 2.dp
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
            )
        }

        Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path = tail, color = FabBubble)
            drawPath(path = tail, color = FabBubbleBorder, style = Stroke(width = 1f))
        }
    }
}

@Composable
private fun AnimatedDogMascot(
    modifier: Modifier = Modifier,
    isCompact: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DogMascotTransition")

    val tailRotation by infiniteTransition.animateFloat(
        initialValue = -22f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isCompact) 820 else 540, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DogTail"
    )

    val earBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isCompact) 0.6f else -1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isCompact) 1300 else 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DogEarBounce"
    )

    val bodyBob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isCompact) -0.6f else -1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isCompact) 1450 else 920, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DogBodyBob"
    )

    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DogBlink"
    )

    val headTilt by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isCompact) 1800 else 1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DogHeadTilt"
    )

    val winkAmount by animateFloatAsState(
        targetValue = if (blinkProgress > 0.72f && blinkProgress < 0.84f) 1f else 0f,
        animationSpec = tween(durationMillis = 80),
        label = "DogWinkAmount"
    )

    val tongueVisible by animateFloatAsState(
        targetValue = if (blinkProgress > 0.30f && blinkProgress < 0.44f && !isCompact) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "DogTongueVisible"
    )

    Canvas(modifier = modifier) {
        val unit = size.minDimension / 64f

        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            translate(left = 2f, top = if (isCompact) 6f else 3f + bodyBob) {

                rotate(degrees = tailRotation, pivot = Offset(48f, 39f)) {
                    drawRoundRect(
                        color = DogEar,
                        topLeft = Offset(44f, 34f),
                        size = Size(4.8f, 16f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawCircle(
                        color = DogEar,
                        radius = 3.4f,
                        center = Offset(46.3f, 34.8f)
                    )
                }

                drawRoundRect(
                    color = DogBrown,
                    topLeft = Offset(18f, 28f),
                    size = Size(26f, 23f),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                drawRoundRect(
                    color = DogBrown,
                    topLeft = Offset(20f, 45f),
                    size = Size(8f, 11f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = DogBrown,
                    topLeft = Offset(34f, 45f),
                    size = Size(8f, 11f),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                drawRoundRect(
                    color = DogCream,
                    topLeft = Offset(24f, 36f),
                    size = Size(14f, 11f),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                rotate(degrees = headTilt, pivot = Offset(31f, 19f)) {
                    val earYOffset = earBounce
                    val leftEar = Path().apply {
                        moveTo(16f, 17f + earYOffset)
                        quadraticBezierTo(10f, 20f + earYOffset, 13f, 31f + earYOffset)
                        lineTo(20f, 26f + earYOffset)
                        close()
                    }
                    val rightEar = Path().apply {
                        moveTo(46f, 17f + earYOffset)
                        quadraticBezierTo(52f, 20f + earYOffset, 49f, 31f + earYOffset)
                        lineTo(42f, 26f + earYOffset)
                        close()
                    }
                    drawPath(leftEar, DogEar)
                    drawPath(rightEar, DogEar)

                    drawCircle(
                        color = DogBrown,
                        radius = 16f,
                        center = Offset(31f, 20f)
                    )

                    drawCircle(
                        color = DogCream,
                        radius = 7.6f,
                        center = Offset(31f, 25f)
                    )
                    drawCircle(
                        color = DogCream,
                        radius = 5.6f,
                        center = Offset(23.8f, 23.6f)
                    )
                    drawCircle(
                        color = DogCream,
                        radius = 5.6f,
                        center = Offset(38.2f, 23.6f)
                    )

                    val eyeY = 18.6f
                    val eyeRadius = if (winkAmount == 1f) 0.55f else if (isCompact) 1.5f else 1.9f
                    drawCircle(color = DogDark, radius = eyeRadius, center = Offset(24.7f, eyeY))
                    if (winkAmount == 1f) {
                        drawLine(
                            color = DogDark,
                            start = Offset(35.2f, eyeY),
                            end = Offset(38.8f, eyeY),
                            strokeWidth = 1.3f,
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawCircle(color = DogDark, radius = if (isCompact) 1.5f else 1.9f, center = Offset(37.2f, eyeY))
                    }

                    if (!isCompact) {
                        drawCircle(color = Color.White.copy(alpha = 0.92f), radius = 0.65f, center = Offset(24.1f, 18.0f))
                        drawCircle(color = Color.White.copy(alpha = 0.92f), radius = 0.65f, center = Offset(36.6f, 18.0f))
                    }

                    drawCircle(color = DogPink.copy(alpha = 0.65f), radius = 1.8f, center = Offset(20.4f, 24.8f))
                    drawCircle(color = DogPink.copy(alpha = 0.65f), radius = 1.8f, center = Offset(41.6f, 24.8f))

                    drawRoundRect(
                        color = DogDark,
                        topLeft = Offset(28.3f, 22.2f),
                        size = Size(5.4f, 3.8f),
                        cornerRadius = CornerRadius(2.2f, 2.2f)
                    )

                    drawLine(
                        color = DogDark,
                        start = Offset(31f, 25.3f),
                        end = Offset(31f, 27.4f),
                        strokeWidth = 1.2f,
                        cap = StrokeCap.Round
                    )
                    drawArc(
                        color = DogDark,
                        startAngle = 12f,
                        sweepAngle = 148f,
                        useCenter = false,
                        topLeft = Offset(27.1f, 26.2f),
                        size = Size(5.1f, 4.4f),
                        style = Stroke(width = 1.1f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = DogDark,
                        startAngle = 20f,
                        sweepAngle = 148f,
                        useCenter = false,
                        topLeft = Offset(30.6f, 26.2f),
                        size = Size(5.1f, 4.4f),
                        style = Stroke(width = 1.1f, cap = StrokeCap.Round)
                    )

                    if (tongueVisible > 0f) {
                        drawRoundRect(
                            color = DogPink,
                            topLeft = Offset(29.4f, 28.5f),
                            size = Size(3.2f, 4.7f * tongueVisible),
                            cornerRadius = CornerRadius(2f, 2f),
                            style = Fill
                        )
                    }
                }
            }
        }
    }
}
