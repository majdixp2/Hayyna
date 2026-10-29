package com.example.ui.components

import com.example.data.model.effectiveBranches
import com.example.data.model.priceLabel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.data.model.gallery
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.ui.theme.CalmSagePrimary
import com.example.ui.theme.SoftTerracotta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOfferDetailSheet(
    offer: RestaurantOffer,
    onDismiss: () -> Unit,
    isFavorite: Boolean = false,
    isOwner: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onReport: (String) -> Unit = {},
    onEdit: () -> Unit = {},
    // نفس العرض في كل فرع (مرتّب من الأقرب). فاضي = فرع واحد.
    branchOffers: List<RestaurantOffer> = emptyList(),
    onOpenStore: (() -> Unit)? = null,
    isVerified: Boolean = false,
    // 🛒 اطلب العرض (باقة / 1+1 / خصم المنيو) — null = العرض ما ينطلب
    orderLabel: String? = null,
    onOrder: () -> Unit = {},
    // 📷 عرض من إعلان: «أنا صاحب المحل»
    onClaimAd: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val orderScope = androidx.compose.runtime.rememberCoroutineScope()
    // الفرع المعروض حالياً (افتراضياً الأقرب أو اللي ضغط عليه في الخريطة)
    // صورة العرض بملء الشاشة (رقم الصورة المفتوحة)
    var zoomIndex by androidx.compose.runtime.remember(offer.id) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var shown by androidx.compose.runtime.remember(offer.id, offer.selectedBranchId) {
        androidx.compose.runtime.mutableStateOf(offer)
    }
    val branchName = offer.branches.firstOrNull { it.id == shown.selectedBranchId }?.displayName
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("restaurant_offer_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Top Drag Down Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clickable { onDismiss() }
                ) {}
            }

            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = offer.cuisineType,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تصغير للخريطة 🔽",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advertisement Media Player: Plays video ad or displays offer photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // الفيديو طولي مثل الريلز، فنعطيه مساحة أطول
                    .height(if (offer.mediaType == MediaType.VIDEO) 420.dp else 320.dp)
                    .then(if (offer.spotlight && com.example.data.model.CatalogFlags.superGlow) Modifier.superFrame() else Modifier)
            ) {
                val gallery = offer.gallery
                if (zoomIndex != null) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { zoomIndex = null },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
                    ) {
                        FullScreenImageViewer(images = gallery, startIndex = zoomIndex ?: 0, onClose = { zoomIndex = null })
                    }
                }
                if (offer.mediaType == MediaType.IMAGE && gallery.size > 1) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState { gallery.size }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        ProductMediaViewer(
                            mediaUri = gallery[page],
                            mediaType = MediaType.IMAGE,
                            // الصورة كاملة بدون قص، والضغط يفتحها بملء الشاشة مع تكبير
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clickable { zoomIndex = page }
                        )
                    }
                    // نقاط الصور
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        repeat(gallery.size) { i ->
                            Box(
                                Modifier
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .background(
                                        if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.5f),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }
                } else {
                    ProductMediaViewer(
                        mediaUri = offer.mediaUri,
                        mediaType = offer.mediaType,
                        isPromotionalVideo = offer.isPromotionalVideo,
                        autoPlayVideo = offer.mediaType == MediaType.VIDEO,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().then(
                            if (offer.mediaType == MediaType.IMAGE) Modifier.clickable { zoomIndex = 0 } else Modifier
                        )
                    )
                }
                if (offer.spotlight && com.example.data.model.CatalogFlags.superGlow) SuperOverlay()
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Restaurant Name & Verified Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (offer.logoUrl.startsWith("http")) {
                    StoreLogo(logoUrl = offer.logoUrl, size = 32.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = offer.restaurantName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedBadge()
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onOpenStore != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CalmSagePrimary.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { onOpenStore() }
                    ) {
                        Text(
                            "🏪 صفحة المتجر ›",
                            color = CalmSagePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (!isOwner) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ReportIcon(onReport = onReport)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (offer.source == "ad") AdSourceBanner(onClaim = if (isOwner) null else onClaimAd)

            // Offer Title
            Text(
                text = offer.offerTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                OpenNowChip(offer = shown)
                OfferExpiryChip(offer = offer)
            }

            Spacer(modifier = Modifier.height(10.dp))
            OfferActionsRow(
                offer = offer,
                isFavorite = isFavorite,
                isOwner = isOwner,
                onToggleFavorite = onToggleFavorite,
                onReport = onReport,
                onEdit = onEdit
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Price & Discount
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = offer.priceLabel(withScope = true),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )

                if (offer.originalPrice != null && offer.originalPrice > offer.price) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${moneyText(offer.originalPrice)} ${offer.currency}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                }

            }
            // الشارات في سطر لحالها تحت السعر (عرض النسبة: السعر نفسه هو الخصم، فما نكررها)
            OfferBadgeChips(offer, skipDiscount = com.example.data.model.OfferPricing.isPercentOnly(offer), modifier = Modifier.padding(top = 8.dp))

            // تفاصيل الخصم: متدرّج / يبدأ متى / افتتاح
            run {
                val tiers = com.example.data.model.OfferPricing.tiersText(offer)
                val starts = com.example.data.model.OfferPricing.startsText(offer)
                if (tiers != null || starts != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF4E0), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            if (starts != null) Text("🔜 $starts", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1D4ED8))
                            if (tiers != null) Text("📅 $tiers", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF8A5A00))
                        }
                    }
                }
            }

            // التوصيل والمَعلَم والأصناف
            run {
                val del = deliveryText(offer.delivery, offer.deliveryFee, offer.deliveryMinOrder, offer.deliveryFreeOver)
                val lm = (offer.branches.firstOrNull { it.id == shown.selectedBranchId } ?: offer.effectiveBranches.firstOrNull())
                    ?.landmark.orEmpty()
                if (del != null || lm.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (del != null) InfoPill(del)
                        if (lm.isNotBlank()) InfoPill("📍 $lm", bg = Color(0xFFF3F4F6), fg = Color(0xFF374151))
                    }
                }
                if (offer.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TagsRow(offer.tags)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = offer.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // الفروع: لو العرض متوفر في أكثر من فرع
            if (branchOffers.size > 1) {
                Text(
                    text = "متوفر في ${branchOffers.size} فروع 📍",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                branchOffers.forEachIndexed { index, b ->
                    val name = offer.branches.firstOrNull { it.id == b.selectedBranchId }?.displayName ?: b.district
                    val isShown = b.selectedBranchId == shown.selectedBranchId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isShown) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { shown = b }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text(
                                    text = listOfNotNull(
                                        distanceText(b),
                                        if (index == 0 && b.distanceKm > 0) "الأقرب لك" else null
                                    ).joinToString(" • ").ifBlank { b.region },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                com.example.data.OfferTracker.directions(offer.id)
                                openDirectionsInGoogleMaps(context, b.latitude, b.longitude)
                            }) {
                                Icon(Icons.Default.Directions, contentDescription = "الاتجاه", tint = CalmSagePrimary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Prominent Distance Banner (كم يبعد المحل عن العميل)
            if (shown.distanceKm > 0) Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0FDF4),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (branchName != null) "كم يبعد $branchName عنك:" else "كم يبعد المحل عنك الآن:",
                                fontSize = 11.sp,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "${distanceText(shown)} فقط",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "🚗 " + minutesText(shown.driveTimeMinutes),
                            maxLines = 1,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (orderLabel != null && !isOwner) {
                Button(
                    onClick = onOrder,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(orderLabel, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) }
                Spacer(modifier = Modifier.height(10.dp))
            }
            // Big CTA: التوجه إلى المطعم (Google Maps Directions)
            Button(
                onClick = {
                    com.example.data.OfferTracker.directions(offer.id)
                        openDirectionsInGoogleMaps(context, shown.latitude, shown.longitude)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("navigate_to_restaurant_cta_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CalmSagePrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "الاتجاهات إلى المحل",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Location Map Card showing district and region
            LocationMapCard(
                district = shown.district,
                region = shown.region,
                latitude = shown.latitude,
                longitude = shown.longitude,
                storeOrItemName = offer.restaurantName,
                // زر الاتجاهات الكبير فوق يكفي
                isInteractive = false
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Contact Buttons: Call & WhatsApp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val message = "مرحباً، أود طلب العرض: ${offer.offerTitle} (${offer.priceLabel()}) المعلن عنه في تطبيق لكوفر."
                        com.example.data.OfferTracker.whatsapp(offer.id)
                            orderScope.launch { com.example.data.OfferSharer.orderViaWhatsApp(context, shown, shown.phone, message) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("whatsapp_restaurant_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (offer.spotlight && com.example.data.model.CatalogFlags.superGlow) Color(0xFFE08A0B) else Color(0xFF25D366) // عرض سوبر: زر ذهبي
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طلب عبر واتساب", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        callPhone(context, shown.phone)
                    },
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("call_restaurant_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اتصال هاتفي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/** "850 متر" أو "3.2 كم" (null إذا المسافة غير معروفة). */
private fun distanceText(offer: RestaurantOffer): String? = when {
    offer.distanceKm <= 0.0 -> null
    offer.distanceKm < 1.0 -> "${(offer.distanceKm * 1000).toInt()} متر"
    else -> "${"%.1f".format(java.util.Locale.US, offer.distanceKm)} كم"
}

/** "دقيقة" / "دقيقتين" / "5 دقائق" / "15 دقيقة" قيادة. */
private fun minutesText(m: Int): String = when {
    m <= 1 -> "دقيقة قيادة"
    m == 2 -> "دقيقتين قيادة"
    m in 3..10 -> "$m دقائق قيادة"
    else -> "$m دقيقة قيادة"
}
