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

/** شارة العد التنازلي "⏰ باقي 3 ساعات". لا تظهر إذا العرض بلا وقت انتهاء. */
@Composable
fun OfferExpiryChip(
    offer: RestaurantOffer,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
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
            if (grandOpening) add("🎉 افتتاح")
            if (pct >= 1) add("خصم $pct% 🔥")
        }
        return parts.joinToString(" • ")
    }

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
