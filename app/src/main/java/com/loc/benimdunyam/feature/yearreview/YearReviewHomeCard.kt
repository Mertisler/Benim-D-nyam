package com.loc.benimdunyam.feature.yearreview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ana sayfadan yıl hikâyesine giriş; veri yükü oluşturmaz. */
@Composable
fun YearReviewHomeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF335542)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("✦  KENDİ FİLMİNİN BAŞROLÜ", color = Color(0xFFE4CCA2),
                    fontSize = 10.sp, letterSpacing = 1.2.sp)
                Text("Yıl Sonu Hikâyem", color = Color.White, fontFamily = FontFamily.Serif,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Yıllarını, insanlarını ve unutulmaz karelerini keşfet.",
                    color = Color(0xFFDCE7DD), fontSize = 12.sp)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = "Yıl özetini aç", tint = Color.White)
        }
    }
}
