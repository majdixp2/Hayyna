package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🚀 عرض سوبر: إطار ذهبي متوهج ينبض + لمعة تمر على الصورة + شريط «عرض سوبر»

private val Gold = listOf(Color(0xFFFFD166), Color(0xFFF59E0B), Color(0xFFFFE8A3), Color(0xFFD97706), Color(0xFFFFD166))

/** إطار ذهبي متحرك مع توهّج */
fun Modifier.superFrame(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "super")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "shift")
    val shape = RoundedCornerShape(18.dp)
    this
        .shadow((6 + glow * 14).dp, shape, ambientColor = Color(0xFFFFC107), spotColor = Color(0xFFFFC107))
        .border(BorderStroke(3.dp, Brush.linearGradient(Gold, start = Offset(shift * 1200f - 600f, 0f), end = Offset(shift * 1200f + 600f, 900f))), shape)
        .clip(shape)
}

/** اللمعة + الشريط (داخل صندوق الصورة) */
@Composable
fun BoxScope.SuperOverlay() {
    val t = rememberInfiniteTransition(label = "shine")
    val p by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "p")
    Canvas(Modifier.matchParentSize()) {
        val x = size.width * p
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
            start = Offset(x - size.width * 0.25f, 0f), end = Offset(x + size.width * 0.25f, size.height)))
    }
    Surface(shape = RoundedCornerShape(50), color = Color(0xFF111827), border = BorderStroke(1.dp, Color(0x99FFD166)),
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
        Text("🚀 عرض سوبر", color = Color(0xFFFFD166), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}
