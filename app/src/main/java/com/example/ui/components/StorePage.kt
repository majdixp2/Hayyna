package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CloudinaryUrls
import com.example.data.model.Branch
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.data.model.StoreProfile
import com.example.data.model.gallery
import com.example.ui.theme.CalmSagePrimary

/**
 * صفحة المتجر الدائمة للعميل: الغلاف، الشعار، النبذة، الأسعار، المنيو/المنتجات، الفروع، والعروض الحالية.
 */
@Composable
fun StorePage(
    store: StoreProfile,
    offers: List<RestaurantOffer>,
    onOfferClick: (RestaurantOffer) -> Unit,
    onClose: () -> Unit,
    // المنيو/المنتجات/الخدمات + السلة (من لوحة الأدمن)
    catalog: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val primary = store.branches.firstOrNull()

    BackHandler { if (viewerIndex != null) viewerIndex = null else onClose() }

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---------- الغلاف + الشعار ----------
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    if (store.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = CloudinaryUrls.image(store.coverUrl, 1000),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(CalmSagePrimary, CalmSagePrimary.copy(alpha = 0.7f)))
                            )
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(40.dp)
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF262A33))
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.offset(y = (-36).dp)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(3.dp, Color.White),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(84.dp)
                        ) {
                            if (store.logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = CloudinaryUrls.image(store.logoUrl, 240),
                                    contentDescription = "شعار المتجر",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.padding(20.dp))
                            }
                        }
                    }

                    Column(Modifier.offset(y = (-28).dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(store.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
                            if (store.verified) {
                                Spacer(Modifier.width(8.dp))
                                VerifiedBadge()
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (store.category.isNotBlank()) {
                                Surface(shape = RoundedCornerShape(8.dp), color = CalmSagePrimary.copy(alpha = 0.12f)) {
                                    Text(
                                        store.category,
                                        color = CalmSagePrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            primary?.let { b ->
                                OpenNowChip(offer = RestaurantOffer(openMinute = b.openMinute, closeMinute = b.closeMinute))
                            }
                        }
                        if (store.priceFrom > 0) {
                            Text(
                                "💰 الأسعار تبدأ من ${formatStorePrice(store.priceFrom)} ر.س",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF8A5A00)
                            )
                        }
                        deliveryText(store.delivery, store.deliveryFee, store.deliveryMinOrder, store.deliveryFreeOver)?.let { InfoPill(it) }
                        if (store.about.isNotBlank()) {
                            Text(store.about, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // تواصل سريع
                        if (store.phone.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { openWhatsApp(context, store.phone, "مرحباً ${store.name}، شفت متجركم في تطبيق لكوفر") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("واتساب", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { callPhone(context, store.phone) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("اتصال", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // ---------- العروض الحالية ----------
                        if (offers.isNotEmpty()) {
                            SectionTitle("🔥 العروض الحالية (${offers.size})")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(offers.size) { i ->
                                    val o = offers[i]
                                    Card(
                                        onClick = { onOfferClick(o) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.width(170.dp)
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = o.gallery.firstOrNull()?.let { CloudinaryUrls.image(it, 400) },
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().height(100.dp)
                                            )
                                            Column(Modifier.padding(8.dp)) {
                                                Text(o.offerTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    (if (com.example.data.model.OfferPricing.isPercentOnly(o)) "" else "${formatStorePrice(o.price)} ر.س ") + o.displayBadge,
                                                    fontSize = 12.sp,
                                                    color = CalmSagePrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        catalog()

                        // ---------- المنيو / المنتجات ----------
                        if (store.menuImages.isNotEmpty()) {
                            SectionTitle("📋 ${store.menuLabel}")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(store.menuImages.size) { i ->
                                    AsyncImage(
                                        model = CloudinaryUrls.image(store.menuImages[i], 500),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 130.dp, height = 175.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewerIndex = i }
                                    )
                                }
                            }
                            Text("اضغط على الصورة لتكبيرها", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // ---------- الفروع ----------
                        if (store.branches.isNotEmpty()) {
                            SectionTitle("📍 الفروع (${store.branches.size})")
                            store.branches.forEach { b -> BranchRow(b) { openDirectionsInGoogleMaps(context, b.latitude, b.longitude) } }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        // عارض الصور بكامل الشاشة مع التكبير بالأصابع
        viewerIndex?.let { start ->
            FullScreenImageViewer(
                images = store.menuImages,
                startIndex = start,
                onClose = { viewerIndex = null }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun BranchRow(branch: Branch, onDirections: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(branch.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                val hours = if (branch.openMinute in 0..1439) {
                    if (branch.openMinute == branch.closeMinute) "24 ساعة"
                    else "${OfferTime.formatMinute(branch.openMinute)} – ${OfferTime.formatMinute(branch.closeMinute)}"
                } else ""
                Text(
                    listOf(branch.district, branch.landmark, hours).filter { it.isNotBlank() }.joinToString(" • "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDirections) {
                Icon(Icons.Default.Directions, contentDescription = "الاتجاه", tint = CalmSagePrimary)
            }
        }
    }
}

/** عارض صور بكامل الشاشة: سحب بين الصور، وتكبير بالأصابع. */
@Composable
fun FullScreenImageViewer(images: List<String>, startIndex: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = startIndex) { images.size }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(CloudinaryUrls.image(images[page], 1600))
        }
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(40.dp)
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
        }
        Text(
            "${pager.currentPage + 1} / ${images.size}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // تكبير بإصبعين؛ السحب بإصبع واحد يقلّب الصور إلا إذا الصورة مكبّرة
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}

private fun formatStorePrice(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v)
