package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.example.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** كحلي لكوفر (لون الهوية الأساسي). الاسم القديم HayynaGreen باقي عشان ما ينكسر أي استخدام له. */
val LacoverNavy = Color(0xFF10318F)
val HayynaGreen = LacoverNavy
private val PinGold = Color(0xFFF4B400)

// مكان الرمز (حرف O) داخل صورة الكلمة (نِسَب من عرض/ارتفاع الصورة lacover_word.png)
private const val WORD_ASPECT = 1611f / 308f
private const val PIN_LEFT = 0.4254f
private const val PIN_TOP = 0.0660f
private const val PIN_WIDTH = 0.1382f
private const val PIN_HEIGHT = 0.8673f

/** مدة الحركة كاملة (الشاشة الافتتاحية تنتظرها قبل ما تختفي). */
const val SPLASH_DURATION_MS = 2100L

/**
 * الشاشة الافتتاحية المتحركة:
 * 1) كلمة "LACOVER" تظهر وتكبر بنعومة (بدون حرف O)
 * 2) رمز الدبوس يسقط من فوق ويرتد مكان حرف O
 * 3) موجة دائرية تنتشر من مكان الدبوس، ثم يظهر الشعار النصي
 */
@Composable
fun SplashLogo(modifier: Modifier = Modifier) {
    val wordAlpha = remember { Animatable(0f) }
    val wordScale = remember { Animatable(0.82f) }
    val pinDrop = remember { Animatable(-1f) }   // -1 = فوق الشاشة، 0 = مكانه
    val pinAlpha = remember { Animatable(0f) }
    val pinSquash = remember { Animatable(1f) }
    val ripple = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { wordAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch {
            wordScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        }
        delay(420)
        launch { pinAlpha.animateTo(1f, tween(150)) }
        pinDrop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f))
        // ارتطام خفيف + موجة
        launch {
            pinSquash.animateTo(0.82f, tween(70))
            pinSquash.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
        launch { ripple.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(150)
        taglineAlpha.animateTo(1f, tween(400))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LacoverNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // مواضع ثابتة من اليسار (الصورة نفسها ما تنعكس في الواجهة العربية)
            BoxWithConstraints(
                contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(WORD_ASPECT)
                    .graphicsLayer {
                        alpha = wordAlpha.value
                        scaleX = wordScale.value
                        scaleY = wordScale.value
                    }
            ) {
                val w = maxWidth
                val h = maxHeight
                Image(
                    painter = painterResource(id = R.drawable.lacover_word),
                    contentDescription = "LACOVER لكوفر",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // موجة تنتشر من رأس الدبوس عند هبوطه
                val pinCenterX = w * (PIN_LEFT + PIN_WIDTH / 2)
                val pinTipY = h * (PIN_TOP + PIN_HEIGHT)
                Canvas(
                    modifier = Modifier
                        .absoluteOffset(x = pinCenterX - w * 0.3f, y = pinTipY - w * 0.3f)
                        .size(w * 0.6f)
                ) {
                    val p = ripple.value
                    if (p > 0f && p < 1f) {
                        drawCircle(
                            color = PinGold.copy(alpha = (1f - p) * 0.7f),
                            radius = size.minDimension / 2 * p,
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(width = 3.dp.toPx() * (1f - p) + 1f)
                        )
                    }
                }

                // الدبوس
                Image(
                    painter = painterResource(id = R.drawable.lacover_pin),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .absoluteOffset(x = w * PIN_LEFT, y = h * PIN_TOP)
                        .size(width = w * PIN_WIDTH, height = h * PIN_HEIGHT)
                        .graphicsLayer {
                            alpha = pinAlpha.value
                            translationY = pinDrop.value * 260.dp.toPx()
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = pinSquash.value
                            scaleX = 2f - pinSquash.value
                        }
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "عروض حولك",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = taglineAlpha.value }
            )
        }
    }
}
