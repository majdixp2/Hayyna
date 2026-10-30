package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.data.model.isSoldOut

/** شارة العد التنازلي "⏰ باقي 3 ساعات". لا تظهر إذا العرض بلا وقت انتهاء. */
@Composable
fun OfferExpiryChip(
    offer: RestaurantOffer,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    // ❌ نفد اليوم (المحل أو موظفه علّمه) — يرجع لحاله بعد منتصف الليل
    if (offer.isSoldOut(nowMillis) && !OfferTime.isExpired(offer, nowMillis)) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFDE8E6), modifier = modifier) {
            Text("❌ نفد اليوم", color = Color(0xFFB42318), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        return
    }
    val text = OfferTime.remainingText(offer, nowMillis) ?: return
    val expired = OfferTime.isExpired(offer, nowMillis)
    val soon = OfferTime.isEndingSoon(offer, nowMillis)
    val upcoming = com.example.data.model.OfferPricing.isUpcoming(offer, nowMillis)
    val (bg, fg) = when {
        upcoming -> Color(0xFFE0ECFF) to Color(0xFF1D4ED8)
        expired -> Color(0xFFE5E7EB) to Color(0xFF6B7280)
        soon -> Color(0xFFFDE8E6) to Color(0xFFB42318)
        else -> Color(0xFFFFF4E0) to Color(0xFF8A5A00)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg, modifier = modifier) {
        Text(
            text = if (upcoming) "🔜 $text" else "⏰ $text",
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** شارة "مفتوح الآن" / "مغلق الآن". لا تظهر إذا أوقات الدوام غير محددة. */
@Composable
fun OpenNowChip(offer: RestaurantOffer, modifier: Modifier = Modifier) {
    val open = OfferTime.isOpenNow(offer) ?: return
    val (bg, fg, label) = if (open) {
        Triple(Color(0xFFE7F6EC), Color(0xFF15803D), "● مفتوح الآن")
    } else {
        Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), "● مغلق الآن")
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg, modifier = modifier) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * شارة العرض المعروضة للعميل: تُحسب من السعرين (خصم X%) بدل النص المحفوظ،
 * عشان ما تطلع شارات قديمة/عشوائية مثل "فيديو إعلاني حصري".
 */
val RestaurantOffer.displayBadge: String
    get() {
        val now = System.currentTimeMillis()
        val pct = com.example.data.model.OfferPricing.effectivePercent(this, now)
        val parts = buildList {
            if (spotlight) add("🚀 عرض سوبر")
            if (exclusive && ExclusiveBadge.label.isNotBlank()) add(ExclusiveBadge.label)
            if (postType == "new") add("✨ جديد")
            if (grandOpening) add("🎉 افتتاح")
            if (pct >= 1) add("خصم $pct% 🔥")
        }
        return parts.joinToString(" • ")
    }

/** نص شارة "حصري في لكوفر" (من إعدادات الأدمن؛ فاضي = موقفة). */
object ExclusiveBadge { @Volatile var label: String = "💎 حصري في لكوفر" }

/** شعار المتجر صغير ودائري (لا يظهر شي إذا ما عنده شعار). */
@Composable
fun StoreLogo(
    logoUrl: String,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    if (!logoUrl.startsWith("http")) return
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = modifier.size(size)
    ) {
        coil.compose.AsyncImage(
            model = com.example.data.CloudinaryUrls.image(logoUrl, 96),
            contentDescription = "شعار المتجر",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** شارات العرض كرقائق تحت العنوان (بدل ما تغطي الصورة). skipDiscount: لعرض النسبة (السعر نفسه هو الخصم). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OfferBadgeChips(offer: RestaurantOffer, skipDiscount: Boolean = false, modifier: Modifier = Modifier) {
    val parts = offer.displayBadge.split(" • ").filter { it.isNotBlank() && !(skipDiscount && it.startsWith("خصم")) }
    if (parts.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        parts.forEach { p ->
            val (bg, fg) = when {
                p.startsWith("🚀") -> Color(0xFF1F2937) to Color(0xFFFFD166)
                p == ExclusiveBadge.label -> Color(0xFFEDE4FF) to Color(0xFF6D28D9)
                p.startsWith("✨") -> Color(0xFFE7F6EC) to Color(0xFF15803D)
                else -> Color(0xFFFFF4E0) to Color(0xFF8A5A00)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = bg) {
                Text(p, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
