
package com.loc.benimdunyam.feature.home.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun FriendlyActionFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // true: Kedi ve konuşma balonu
    // false: Yeni Anı Yaz butonu
    var showGreeting by remember {
        mutableStateOf(true)
    }

    // 3,5 saniye sonra selamlama görünümünü değiştir.
    LaunchedEffect(Unit) {
        delay(3500L)
        showGreeting = false
    }

    // Butonun hafifçe büyüyüp küçülmesi.
    val infiniteTransition = rememberInfiniteTransition(
        label = "FabBreathingTransition"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FabScaleAnimation"
    )

    // Selamlama sırasında dış kart şeffaf olur.
    val fabColor by animateColorAsState(
        targetValue = if (showGreeting) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(400),
        label = "FabContainerColor"
    )

    // Selamlama sırasında dış gölge kaldırılır.
    val fabShadow by animateDpAsState(
        targetValue = if (showGreeting) 0.dp else 8.dp,
        animationSpec = tween(400),
        label = "FabShadow"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(28.dp),
        color = fabColor,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = if (showGreeting) 0.dp else 6.dp,
        shadowElevation = fabShadow
    ) {
        AnimatedContent(
            targetState = showGreeting,
            transitionSpec = {
                (
                        slideInVertically(
                            initialOffsetY = { height -> height / 3 }
                        ) + fadeIn(
                            animationSpec = tween(350)
                        )
                        ).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { height -> -height / 3 }
                        ) + fadeOut(
                            animationSpec = tween(250)
                        )
                    )
            },
            label = "FabContentTransition",
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        ) { isGreeting ->

            if (isGreeting) {

                // Kedi ve balon birbirinden bağımsız,
                // tek bir kart olmadan görüntülenir.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    // Kedinin üstündeki konuşma balonu
                    GreetingSpeechBubble()

                    // Kedinin arkasındaki yumuşak renk hâlesi
                    Box(
                        modifier = Modifier.size(76.dp),
                        contentAlignment = Alignment.Center
                    ) {

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = Color(0xFFDDF2E8),
                                    shape = CircleShape
                                )
                        )

                        // Mevcut hareketli kedi
                        AnimatedCatMascot(
                            modifier = Modifier.size(68.dp)
                        )

                        // Dekoratif parıltılar
                        Text(
                            text = "✦",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(
                                    x = (-4).dp,
                                    y = 6.dp
                                ),
                            color = Color(0xFF74A891),
                            fontSize = 14.sp
                        )

                        Text(
                            text = "✧",
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(
                                    x = 5.dp,
                                    y = (-8).dp
                                ),
                            color = Color(0xFFE3AD67),
                            fontSize = 13.sp
                        )
                    }
                }

            } else {

                // Normal anı oluşturma butonu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.defaultMinSize(
                        minHeight = 28.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = "Anı Yaz",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Genel amaçlı konuşma balonu.
 * Kedinin üzerinde "Merhaba!" göstermek için de kullanılır.
 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val bubbleColor = Color(0xFFFFFEFA)
    val borderColor = Color(0xFFDDE8DF)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Balon gövdesi
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            contentColor = Color(0xFF385449),
            border = BorderStroke(
                width = 1.dp,
                color = borderColor
            ),
            shadowElevation = 2.dp
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(
                    horizontal = 15.dp,
                    vertical = 8.dp
                )
            )
        }

        // Balonun aşağı bakan kuyruğu
        Canvas(
            modifier = Modifier.size(
                width = 14.dp,
                height = 7.dp
            )
        ) {
            val bubbleTail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(
                    size.width / 2f,
                    size.height
                )
                close()
            }

            drawPath(
                path = bubbleTail,
                color = bubbleColor
            )
        }
    }
}

/**
 * Kedinin üzerinde gösterilen selamlama balonu.
 */
@Composable
private fun GreetingSpeechBubble() {
    SpeechBubble(
        text = "Anı Biriktir"
    )
}
