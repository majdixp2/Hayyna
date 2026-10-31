package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.example.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MerchantViewModel

private val Navy = Color(0xFF10318F)
private val Gold = Color(0xFFF4B400)

/** دبوس لكوفر (أبيض) بدون علامة % — العلامة تنرسم فوقه عشان تلف وقت التحديث. */
@Composable
private fun PinShape(modifier: Modifier, angle: Float = 0f) {
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        // نفس أبعاد الشعار: صندوق 100×120
        val s = minOf(size.width / 100f, size.height / 120f)
        val ox = (size.width - 100f * s) / 2f
        val oy = (size.height - 120f * s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        drawCircle(Color.White, 50f * s, p(50f, 50f))
        drawPath(Path().apply { moveTo(p(27f, 86f).x, p(27f, 86f).y); lineTo(p(50f, 120f).x, p(50f, 120f).y); lineTo(p(73f, 86f).x, p(73f, 86f).y); close() }, Color.White)
        drawCircle(Navy, 33f * s, p(50f, 50f))
        drawPath(Path().apply { moveTo(p(77.4f, 64.6f).x, p(77.4f, 64.6f).y); lineTo(p(80.7f, 82.9f).x, p(80.7f, 82.9f).y); lineTo(p(69.1f, 74.4f).x, p(69.1f, 74.4f).y); close() }, Navy)
        // علامة % في وسط الدائرة بالضبط (نوسّطها على حدود الرسم نفسه، مو على سطر النص)
        val c = p(50f, 50f)
        paint.textSize = 40f * s
        val b = android.graphics.Rect(); paint.getTextBounds("%", 0, 1, b)
        drawContext.canvas.nativeCanvas.apply {
            save(); rotate(angle, c.x, c.y)
            drawText("%", c.x, c.y - b.exactCenterY(), paint)
            restore()
        }
    }
}

/**
 * شعار لكوفر في شريط البحث: ضغطة = قائمة، ضغطة طويلة = تحديث سريع.
 * النقطة الذهبية = نزلت عروض جديدة حولك بعد آخر تحديث.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrandMenuButton(
    vm: MerchantViewModel,
    newCount: Int,
    isAdmin: Boolean,
    isScout: Boolean,
    onBrowse: () -> Unit
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val refreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val anim = rememberInfiniteTransition(label = "brand")
    val angle by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "pct")
    val pulse by anim.animateFloat(1f, 1.35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dot")

    Box {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Navy)
                .combinedClickable(
                    onClick = { open = true },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); open = false; vm.refreshOffers(newCount) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                PinShape(Modifier.size(34.dp), if (refreshing) angle else 0f)
            }
        }
        if (newCount > 0 && !refreshing) Box(
            Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(13.dp).scale(pulse)
                .background(Gold, CircleShape).border(2.dp, Color.White, CircleShape)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun close() { open = false }
            BrandItem("🔄", "تحديث العروض", if (newCount > 0) "نزل ${if (newCount == 1) "عرض جديد" else "$newCount عروض جديدة"} حولك" else null) { close(); vm.refreshOffers(newCount) }
            BrandItem("📍", "تصفّح حي") { close(); onBrowse() }
            BrandItem("↗️", "شارك لكوفر مع صديق") {
                close()
                val text = "لكوفر 📍 كل عروض المحلات اللي حولك على خريطة وحدة\n" + com.example.data.OfferSharer.WEB_BASE
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "شارك لكوفر"))
            }
            BrandItem("🌐", if (com.example.ui.i18n.I18n.isEn) "العربية" else "English") { close(); com.example.ui.i18n.switchLang(ctx) }
            BrandItem(com.example.ui.i18n.themeLabel().take(2), com.example.ui.i18n.themeLabel().drop(3)) { com.example.ui.i18n.nextTheme(ctx) }
            if (isScout || isAdmin) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("للفريق", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp))
                if (isScout) BrandItem("📷", "ارفع عرض من إعلان", "اكتب اسم المحل وارفع الإعلان") { close(); vm.adPickerOpen.value = true }
                if (isAdmin) BrandItem("🛠️", "لوحة الإدارة") { close(); vm.setRole(com.example.ui.AppRole.ADMIN_PORTAL) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("💡 ضغطة طويلة على الشعار = تحديث سريع", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun BrandItem(icon: String, label: String, sub: String? = null, action: () -> Unit) = DropdownMenuItem(
    leadingIcon = { Text(icon, fontSize = 18.sp) },
    text = {
        Column {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Color(0xFFB07D00))
        }
    },
    onClick = action
)

/** 📷 للفريق: دوّر على المحل في Google ثم ارفع له عرض من إعلان. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdShopPickerSheet(vm: MerchantViewModel) {
    val shops by vm.googleShops.collectAsStateWithLifecycle()
    val searching by vm.searchingShops.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { vm.adPickerOpen.value = false; vm.googleShops.value = null }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📷 ارفع عرض من إعلان", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text("اكتب اسم المحل (ومعه الحي أو المدينة لو الاسم منتشر)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, placeholder = { Text("مثال: تشيكن X الحمدانية") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(
                    onClick = { vm.searchGoogleShops(q) }, enabled = q.trim().length >= 2 && !searching,
                    colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 54.dp)
                ) { if (searching) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("بحث", fontWeight = FontWeight.Bold) }
            }
            shops?.let { list ->
                if (list.isEmpty()) Text("ما لقينا المحل في Google. جرّب اسم ثاني أو أضف المدينة.", fontSize = 13.sp)
                list.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.pickShopForAd(f) }
                            .border(1.dp, Navy.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 18.sp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            androidx.compose.material3.Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (f.address.isNotBlank()) androidx.compose.material3.Text(f.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text("ارفع ›", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Text("المعلومات من خرائط Google", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
