package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.Announcement
import com.example.ui.i18n.I18n

/** ألوان الإعلان: كحلي (خصم) / ذهبي (حدث، ميزة) / أخضر (عام) — (خلفية، شريط الزر، النص) */
private fun annColors(c: String): Triple<Color, Color, Color> = when (c) {
    "gold" -> Triple(Color(0xFFF4B400), Color(0xFFD99E00), Color(0xFF1B1B1B))
    "green" -> Triple(Color(0xFF15803D), Color(0xFF0F6630), Color.White)
    else -> Triple(Color(0xFF10318F), Color(0xFF0B2470), Color.White)
}

private fun Announcement.t(ar: String, en: String) = if (I18n.isEn && en.isNotBlank()) en else ar

/** الشارة: اللي كتبه الأدمن، وإلا تنحسب من تاريخ النهاية */
fun annBadge(a: Announcement, now: Long = System.currentTimeMillis()): String {
    if (a.badge.isNotBlank()) return a.badge
    val left = a.endsAt - now
    val d = Math.ceil(left / 86_400_000.0).toInt()
    val sameDay = java.util.Calendar.getInstance().let { c -> c.timeInMillis = a.endsAt; val e = c.get(java.util.Calendar.DAY_OF_YEAR); c.timeInMillis = now; e == c.get(java.util.Calendar.DAY_OF_YEAR) }
    return if (I18n.isEn) when { d <= 1 && left < 86_400_000L && sameDay -> "Last day"; d <= 1 -> "Ends tomorrow"; else -> "Ends in $d days" }
    else when { d <= 1 && left < 86_400_000L && sameDay -> "آخر يوم"; d <= 1 -> "ينتهي بكرة"; else -> "ينتهي خلال $d أيام" }
}

@Composable
private fun Pill(text: String) {
    Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE5484D)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** البطاقة الكاملة: قسمين (الرسالة + شريط الزر الأغمق) */
@Composable
fun AnnouncementCard(a: Announcement, onView: (String) -> Unit, onClick: (Announcement) -> Unit, modifier: Modifier = Modifier) {
    val (bg, strip, fg) = annColors(a.color)
    LaunchedEffect(a.id) { onView(a.id) }
    val cta = a.t(a.cta, a.ctaEn)
    Column(modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(bg)
        .clickable(enabled = a.action != "none") { onClick(a) }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("%", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
                Pill(annBadge(a))
            }
            Spacer(Modifier.size(8.dp))
            Text(a.t(a.title, a.titleEn), color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp)
            a.t(a.sub, a.subEn).takeIf { it.isNotBlank() }?.let { Text(it, color = fg.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (cta.isNotBlank() && a.action != "none") {
            Row(Modifier.fillMaxWidth().background(strip).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cta, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (I18n.isEn) "›" else "‹", color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

/** السطر فوق الخريطة: ضغطة = يتوسع، ✕ = يختفي لين بكرة */
@Composable
fun AnnouncementStrip(a: Announcement, expanded: Boolean, onToggle: () -> Unit, onClose: () -> Unit, onView: (String) -> Unit, onClick: (Announcement) -> Unit) {
    if (expanded) {
        Box {
            AnnouncementCard(a, onView, onClick, Modifier.padding(top = 6.dp))
            Text("✕", color = annColors(a.color).third, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(CircleShape).clickable { onToggle() }.padding(horizontal = 10.dp, vertical = 2.dp))
        }
        return
    }
    val (bg, _, fg) = annColors(a.color)
    LaunchedEffect(a.id) { onView(a.id) }
    Row(Modifier.padding(top = 6.dp).fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(bg)
        .clickable { onToggle() }.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(annBadge(a))
        Spacer(Modifier.width(8.dp))
        Text(a.t(a.title, a.titleEn), color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("⌄", color = fg, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Text("✕", color = fg.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
