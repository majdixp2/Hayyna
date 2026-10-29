package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HayynaOrder
import com.example.ui.MerchantViewModel
import kotlinx.coroutines.delay

private val Navy = Color(0xFF10318F)
private val Gold = Color(0xFFF4B400)
private val ReadyGreen = Color(0xFF15803D)

/** كم يبقى الشريط ظاهر بعد ما يكتمل الطلب أو ينلغي (عشان العميل يلحق يشوف النتيجة). */
private const val FINISHED_LINGER_MS = 8000L

private fun HayynaOrder.isReadyish() = status == "ready" || status == "delivering"
private fun HayynaOrder.progress(): Float = when (status) {
    "done" -> 1f
    "cancelled", "noshow" -> 1f
    else -> (stepIndex + 1f) / steps.size.coerceAtLeast(1)
}
private fun HayynaOrder.emoji(): String = when {
    status == "sent" -> "📨"; status == "received" -> "👨‍🍳"
    status == "ready" && isCurbside -> "🚗"; status == "ready" -> "🛍️"
    status == "delivering" -> "🚚"; status == "done" -> "✅"
    else -> "✕"
}
private fun HayynaOrder.shortStatus(): String = when (status) {
    "sent" -> "بانتظار المحل"; "received" -> "يتجهّز"
    "ready" -> if (isCurbside && arrived) "المحل جايك" else "جاهز ✅"
    "delivering" -> "بالطريق"; "done" -> "تم 👍"
    "cancelled" -> "ملغي"; "noshow" -> "ما انستلم"; else -> status
}

private fun vibrate(ctx: Context) = runCatching {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    else @Suppress("DEPRECATION") v?.vibrate(300)
}

/**
 * متابعة الطلب الحي في كل الشاشات: شريط أسفل الشاشة أو دائرة عائمة (حسب إعداد الأدمن catalog.tracker).
 * الضغط عليه يفتح «طلباتي». يظهر بس لما يكون عند العميل طلب شغّال (أو انتهى قبل ثواني).
 */
@Composable
fun BoxScope.OrderTracker(vm: MerchantViewModel, bottomPadding: Dp, onOpen: () -> Unit) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val orders by vm.myOrders.collectAsStateWithLifecycle()
    val mode = cfg.tracker
    val ctx = LocalContext.current

    // نتذكر الحالة السابقة لكل طلب: نهتز لما يصير جاهز، ونخلي المنتهي ظاهر ثواني
    val prevStatus = remember { mutableStateMapOf<String, String>() }
    val finishedAt = remember { mutableStateMapOf<String, Long>() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(orders) {
        val t = System.currentTimeMillis()
        orders.forEach { o ->
            val was = prevStatus[o.id]
            if (was != null && was != o.status) {
                if (o.isReadyish()) vibrate(ctx)
                if (!o.isLive) finishedAt[o.id] = t
            }
            prevStatus[o.id] = o.status
        }
        now = t
    }
    LaunchedEffect(finishedAt.size) { if (finishedAt.isNotEmpty()) { delay(FINISHED_LINGER_MS + 200); now = System.currentTimeMillis() } }

    val shown = orders.filter { it.isLive || (finishedAt[it.id]?.let { at -> now - at < FINISHED_LINGER_MS } == true) }
        .sortedWith(compareByDescending<HayynaOrder> { it.isReadyish() }.thenByDescending { it.createdAtMillis })
    val visible = mode != "off" && shown.isNotEmpty()
    val top = shown.firstOrNull()

    if (mode == "bubble") {
        AnimatedVisibility(
            visible = visible, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
        ) { if (top != null) TrackerBubble(top, shown.size, onOpen) }
    } else {
        AnimatedVisibility(
            visible = visible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = bottomPadding)
        ) { if (top != null) TrackerBar(top, shown.size, vm, onOpen) }
    }
}

@Composable
private fun TrackerBar(o: HayynaOrder, count: Int, vm: MerchantViewModel, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val ready = o.isReadyish()
    val bg = if (ready) ReadyGreen else Navy
    val prog by animateFloatAsState(o.progress(), tween(600), label = "trackerProgress")
    Surface(
        shape = RoundedCornerShape(18.dp), color = bg, shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(o.emoji(), fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.storeName.ifBlank { "طلبك" }, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(o.statusText(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (count > 1) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Gold) {
                        Text("$count طلبات", color = Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (o.isCurbside && !o.arrived && o.status in listOf("received", "ready")) {
                    Button(
                        onClick = { vm.markArrived(o) { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } } },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) { Text("🚗 وصلت", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                } else Text("‹", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { prog },
                color = Gold, trackColor = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
        }
    }
}

@Composable
private fun TrackerBubble(o: HayynaOrder, count: Int, onOpen: () -> Unit) {
    val ready = o.isReadyish()
    val prog by animateFloatAsState(o.progress(), tween(600), label = "bubbleProgress")
    val pulse = rememberInfiniteTransition(label = "bubblePulse")
    val s by pulse.animateFloat(1f, if (ready) 1.12f else 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "bubbleScale")
    Box(Modifier.size(72.dp).scale(s)) {
        Surface(
            shape = CircleShape, color = if (ready) ReadyGreen else Navy, shadowElevation = 8.dp,
            modifier = Modifier.size(66.dp).align(Alignment.Center).clickable(onClick = onOpen)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { prog }, color = Gold, trackColor = Color.White.copy(alpha = 0.22f),
                    strokeWidth = 4.dp, modifier = Modifier.size(58.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(o.emoji(), fontSize = 20.sp)
                    Text(o.shortStatus(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        if (count > 1) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(22.dp)
                    .background(Gold, CircleShape).border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$count", color = Navy, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        }
    }
}
