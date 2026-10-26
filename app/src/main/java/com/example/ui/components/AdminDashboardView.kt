package com.example.ui.components

import com.example.data.model.priceLabel
import com.example.data.repository.OfferReport
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary
import com.example.ui.theme.SoftTerracotta

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardView(
    offers: List<RestaurantOffer>,
    onToggleFeatured: (String, Boolean) -> Unit,
    onToggleApproved: (String, Boolean) -> Unit,
    onDeleteOffer: (String) -> Unit,
    reports: List<OfferReport> = emptyList(),
    onDismissReport: (String) -> Unit = {},
    merchantStatuses: List<com.example.data.repository.MerchantStatus> = emptyList(),
    onApprove: (RestaurantOffer) -> Unit = {},
    onTrustMerchant: (RestaurantOffer) -> Unit = {},
    onSuspendMerchant: (String, String) -> Unit = { _, _ -> },
    onUnsuspend: (com.example.data.repository.MerchantStatus) -> Unit = {},
    onKeepReported: (RestaurantOffer) -> Unit = {},
    pendingStores: List<com.example.data.model.StoreProfile> = emptyList(),
    onApproveStore: (com.example.data.model.StoreProfile) -> Unit = {},
    feedback: List<com.example.data.model.Feedback> = emptyList(),
    onReplyFeedback: (String, String, String) -> Unit = { _, _, _ -> },
    onFeedbackStatus: (String, String) -> Unit = { _, _ -> },
    onDeleteFeedback: (String) -> Unit = {},
    supportNumber: String = "",
    onSaveSupportNumber: (String) -> Unit = {},
    aiEnabled: Boolean = false,
    aiModel: String = "",
    onSaveAi: (Boolean, String) -> Unit = { _, _ -> },
    appCheckToken: String? = null,
    lastAiError: String? = null,
    onLoadAppCheckToken: () -> Unit = {},
    onUntrust: (com.example.data.repository.MerchantStatus) -> Unit = {},
    onOfferClick: (RestaurantOffer) -> Unit = {},
    poiConfig: com.example.data.repository.PoiConfig = com.example.data.repository.PoiConfig(),
    onSavePoiConfig: (com.example.data.repository.PoiConfig) -> Unit = {},
    topPoi: List<com.example.data.repository.PoiInterest> = emptyList(),
    pendingVerifications: List<com.example.data.model.VerificationRequest> = emptyList(),
    onDecideVerification: (com.example.data.model.VerificationRequest, Boolean, String) -> Unit = { _, _, _ -> },
    verifyConfig: com.example.data.model.VerifyConfig = com.example.data.model.VerifyConfig(),
    onSaveVerifyConfig: (com.example.data.model.VerifyConfig) -> Unit = {},
    categories: List<String> = emptyList(),
    onSaveCategories: (List<String>) -> Unit = {},
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var inboxType by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("الكل") }
    var showDone by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var viewerImage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val inbox = feedback.filter { f ->
        (inboxType == "الكل" || f.type == inboxType) &&
            (showDone || f.status != com.example.data.model.Feedback.STATUS_DONE)
    }
    val newCount = feedback.count { it.status == com.example.data.model.Feedback.STATUS_NEW }
    val pending = offers.filter { !it.isApproved && merchantStatuses.none { s -> s.uid == it.merchantId && s.suspended } }
    val hiddenByReports = offers.filter { it.isApproved && it.reportsCount >= com.example.ui.MerchantViewModel.AUTO_HIDE_REPORTS }
    val suspended = merchantStatuses.filter { it.suspended }
    var confirmSuspend by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<RestaurantOffer?>(null) }

    confirmSuspend?.let { o ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSuspend = null },
            title = { Text("إيقاف ${o.restaurantName}؟") },
            text = { Text("بتختفي كل عروضه، وما يقدر ينشر لين تلغي الإيقاف.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onSuspendMerchant(o.merchantId, o.restaurantName)
                    confirmSuspend = null
                }) { Text("أوقفه", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmSuspend = null }) { Text("إلغاء") } }
        )
    }

    viewerImage?.let { img ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewerImage = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            FullScreenImageViewer(images = listOf(img), startIndex = 0, onClose = { viewerImage = null })
        }
    }

    val nowMs = System.currentTimeMillis()
    val trusted = merchantStatuses.filter { it.trusted && !it.suspended }
    val visiblePendingStores = pendingStores.filter { st -> merchantStatuses.none { it.uid == st.uid && it.suspended } }
    val hiddenIds = hiddenByReports.map { it.id }.toSet()
    val otherReports = reports.filter { it.offerId !in hiddenIds }.groupBy { it.offerId }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var offerQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var offerFilter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("الكل") }

    val reviewCount = pending.size + hiddenByReports.size
    val storesCount = visiblePendingStores.size + pendingVerifications.size
    data class AdminTab(val title: String, val badge: Int)
    val tabs = listOf(
        AdminTab("🏠 الرئيسية", 0),
        AdminTab("⏳ المراجعة", reviewCount),
        AdminTab("🏪 المتاجر", storesCount),
        AdminTab("📨 الرسائل", newCount),
        AdminTab("📋 كل العروض", 0),
        AdminTab("⚙️ الإعدادات", if (lastAiError != null) 1 else 0)
    )

    Column(modifier = modifier.testTag("admin_dashboard_view")) {
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = CalmSagePrimary
        ) {
            tabs.forEachIndexed { i, t ->
                androidx.compose.material3.Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            if (t.badge > 0) {
                                Spacer(Modifier.width(4.dp))
                                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFE05A47)) {
                                    Text(
                                        "${t.badge}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                // ---------------- 🏠 الرئيسية ----------------
                0 -> {
                    item {
                        // الرصيد والأسعار والأكواد والفريق والسجل: في لوحة الويب (نفس حسابك ونفس الصلاحيات)
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.material3.Card(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(com.example.data.OfferSharer.WEB_BASE + "/admin/")))
                                }
                            },
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = CalmSagePrimary),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🖥️", fontSize = 26.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("لوحة الإدارة الكاملة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("الرصيد والأسعار، الأكواد، الفريق، وسجل العمليات", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                }
                                Text("‹", color = Color.White, fontSize = 22.sp)
                            }
                        }
                    }
                    item { SectionHeader("يحتاج انتباهك") }
                    val todo = listOf(
                        Triple("⏳ عروض تنتظر المراجعة", pending.size, 1),
                        Triple("🚩 عروض مخفية بسبب البلاغات", hiddenByReports.size, 1),
                        Triple("🏪 صفحات متاجر تنتظر الموافقة", visiblePendingStores.size, 2),
                        Triple("🛡️ طلبات توثيق", pendingVerifications.size, 2),
                        Triple("📨 رسائل جديدة", newCount, 3)
                    )
                    if (todo.all { it.second == 0 }) {
                        item { Text("كل شي تمام، ما فيه شي ينتظرك ✅", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(todo.filter { it.second > 0 }.size) { i ->
                        val (label, count, target) = todo.filter { it.second > 0 }[i]
                        Card(
                            onClick = { tab = target },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("$count ›", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF8A5A00))
                            }
                        }
                    }
                    item { SectionHeader("إحصائيات") }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminStatCard("المتاجر", "${offers.map { it.merchantId }.distinct().size}", Icons.Default.Restaurant, CalmSagePrimary, Modifier.weight(1f))
                            AdminStatCard(
                                "عروض نشطة",
                                "${offers.count { it.isApproved && !com.example.data.model.OfferTime.isExpired(it, nowMs) }}",
                                Icons.Default.CheckCircle,
                                Color(0xFF16A34A),
                                Modifier.weight(1f)
                            )
                            AdminStatCard("الأحياء", "${offers.map { it.district }.filter { it.isNotBlank() }.distinct().size}", Icons.Default.LocationOn, SoftTerracotta, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminStatCard("المشاهدات", "${offers.sumOf { it.viewsCount }}", Icons.Default.Visibility, CalmSagePrimary, Modifier.weight(1f))
                            AdminStatCard("⚡ ينشرون مباشرة", "${trusted.size}", Icons.Default.Star, Color(0xFFD97706), Modifier.weight(1f))
                            AdminStatCard("فيديوهات", "${offers.count { it.mediaType == MediaType.VIDEO }}", Icons.Default.Videocam, Color(0xFFE05A47), Modifier.weight(1f))
                        }
                    }
                }

                // ---------------- ⏳ المراجعة ----------------
                1 -> {
                    item { SectionHeader(if (pending.isEmpty()) "عروض جديدة: لا يوجد ✅" else "⏳ عروض جديدة تنتظر المراجعة (${pending.size})") }
                    items(pending, key = { "p_" + it.id }) { o ->
                        ReviewCard(
                            offer = o,
                            onClick = { onOfferClick(o) },
                            note = null,
                            primaryLabel = "اعتماد ✓",
                            onPrimary = { onApprove(o) },
                            secondaryLabel = "اعتماد + ⚡ ينشر مباشرة",
                            onSecondary = { onTrustMerchant(o) },
                            onDelete = { onDeleteOffer(o.id) },
                            onSuspend = { confirmSuspend = o }
                        )
                    }
                    item { SectionHeader(if (hiddenByReports.isEmpty()) "مخفية بسبب البلاغات: لا يوجد ✅" else "🚩 مخفية بسبب البلاغات (${hiddenByReports.size})") }
                    items(hiddenByReports, key = { "r_" + it.id }) { o ->
                        ReviewCard(
                            offer = o,
                            onClick = { onOfferClick(o) },
                            note = "${o.reportsCount} بلاغات: " + reports.filter { it.offerId == o.id }.joinToString("، ") { it.reason }.take(200),
                            primaryLabel = "سليم، رجّعه",
                            onPrimary = { onKeepReported(o) },
                            secondaryLabel = null,
                            onSecondary = {},
                            onDelete = { onDeleteOffer(o.id) },
                            onSuspend = { confirmSuspend = o }
                        )
                    }
                    if (otherReports.isNotEmpty()) {
                        item { SectionHeader("🚩 بلاغات أخرى (أقل من ${com.example.ui.MerchantViewModel.AUTO_HIDE_REPORTS})") }
                        items(otherReports.keys.toList(), key = { "or_" + it }) { offerId ->
                            val list = otherReports[offerId].orEmpty()
                            val first = list.first()
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(first.offerTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                                    Text("${first.restaurantName} • ${list.size} بلاغ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    list.take(5).forEach { r ->
                                        Text("• ${r.reason}", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                        androidx.compose.material3.TextButton(onClick = { list.forEach { onDismissReport(it.id) } }) {
                                            Text("تجاهل البلاغات")
                                        }
                                        androidx.compose.material3.TextButton(onClick = {
                                            onDeleteOffer(offerId)
                                            list.forEach { onDismissReport(it.id) }
                                        }) {
                                            Text("احذف العرض", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- 🏪 المتاجر ----------------
                2 -> {
                    item { SectionHeader(if (pendingVerifications.isEmpty()) "طلبات التوثيق: لا يوجد ✅" else "🛡️ طلبات التوثيق (${pendingVerifications.size})") }
                    items(pendingVerifications, key = { "v_" + it.uid }) { r ->
                        VerificationCard(r, onDecide = { ok, reason -> onDecideVerification(r, ok, reason) }, onOpenImage = { viewerImage = it })
                    }
                    item { SectionHeader(if (visiblePendingStores.isEmpty()) "صفحات تنتظر الموافقة: لا يوجد ✅" else "🏪 صفحات تنتظر الموافقة (${visiblePendingStores.size})") }
                    items(visiblePendingStores, key = { "st_" + it.uid }) { st ->
                        PendingStoreCard(st, onApprove = { onApproveStore(st) }, onSuspend = { onSuspendMerchant(st.uid, st.name) })
                    }
                    item { SectionHeader("⚡ ينشرون مباشرة (${trusted.size})") }
                    if (trusted.isEmpty()) {
                        item { Text("ما فيه للحين", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(trusted, key = { "t_" + it.uid }) { st ->
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(st.name.ifBlank { st.uid.take(8) }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = { onUntrust(st) }) { Text("إلغاء التوثيق") }
                                androidx.compose.material3.TextButton(onClick = { onSuspendMerchant(st.uid, st.name) }) {
                                    Text("إيقاف", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    item { SectionHeader("📍 محلات غير مسجّلة يطلبها العملاء") }
                    item {
                        Text(
                            "من نقاط خرائط Google: كم مرة ضغطها العملاء ودعوها. ابدأ بالأكثر طلباً 👇",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (topPoi.isEmpty()) {
                        item { Text("ما فيه بيانات للحين", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(topPoi, key = { "poi_" + it.placeId }) { p ->
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Card(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=place&query_place_id=${p.placeId}")
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("👆 ${p.taps}   📣 ${p.invites}   🏪 ${p.claims}", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("افتح في الخرائط ›", fontSize = 12.sp, color = CalmSagePrimary)
                            }
                        }
                    }

                    item { SectionHeader("⛔ حسابات موقوفة (${suspended.size})") }
                    if (suspended.isEmpty()) {
                        item { Text("ما فيه حسابات موقوفة ✅", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(suspended, key = { "s_" + it.uid }) { st ->
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(st.name.ifBlank { st.uid.take(8) }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = { onUnsuspend(st) }) { Text("إلغاء الإيقاف") }
                            }
                        }
                    }
                }

                // ---------------- 📨 الرسائل ----------------
                3 -> {
                    item {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val types = listOf("الكل") + com.example.data.model.Feedback.TYPES
                            items(types.size) { i ->
                                androidx.compose.material3.FilterChip(
                                    selected = inboxType == types[i],
                                    onClick = { inboxType = types[i] },
                                    label = { Text(types[i], fontSize = 11.sp) }
                                )
                            }
                            item {
                                androidx.compose.material3.FilterChip(
                                    selected = showDone,
                                    onClick = { showDone = !showDone },
                                    label = { Text("إظهار المنتهية", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    if (inbox.isEmpty()) {
                        item { Text("لا توجد رسائل ✅", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(inbox, key = { "fb_" + it.id }) { f ->
                        FeedbackCard(
                            f = f,
                            onReply = { r, st -> onReplyFeedback(f.id, r, st) },
                            onStatus = { st -> onFeedbackStatus(f.id, st) },
                            onDelete = { onDeleteFeedback(f.id) },
                            onOpenImage = { viewerImage = it }
                        )
                    }
                }

                // ---------------- 📋 كل العروض ----------------
                4 -> {
                    item {
                        androidx.compose.material3.OutlinedTextField(
                            value = offerQuery,
                            onValueChange = { offerQuery = it },
                            placeholder = { Text("ابحث باسم المتجر أو العرض أو الحي…") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val filters = listOf("الكل", "نشط", "بانتظار المراجعة", "منتهي", "مميز ⭐")
                            items(filters.size) { i ->
                                androidx.compose.material3.FilterChip(
                                    selected = offerFilter == filters[i],
                                    onClick = { offerFilter = filters[i] },
                                    label = { Text(filters[i], fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    val q = offerQuery.trim()
                    val shown = offers.filter { o ->
                        (q.isBlank() || o.restaurantName.contains(q, true) || o.offerTitle.contains(q, true) || o.district.contains(q, true)) &&
                            when (offerFilter) {
                                "نشط" -> o.isApproved && !com.example.data.model.OfferTime.isExpired(o, nowMs)
                                "بانتظار المراجعة" -> !o.isApproved
                                "منتهي" -> com.example.data.model.OfferTime.isExpired(o, nowMs)
                                "مميز ⭐" -> o.isFeatured
                                else -> true
                            }
                    }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    item { Text("${shown.size} عرض", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(shown, key = { it.id }) { offer ->
                        AdminOfferRow(
                            offer = offer,
                            onClick = { onOfferClick(offer) },
                            onToggleFeatured = { onToggleFeatured(offer.id, !offer.isFeatured) },
                            onToggleApproved = { onToggleApproved(offer.id, it) },
                            onDelete = { onDeleteOffer(offer.id) }
                        )
                    }
                }

                // ---------------- ⚙️ الإعدادات ----------------
                else -> {
                    item { SupportNumberEditor(current = supportNumber, onSave = onSaveSupportNumber) }
                    item { CategoriesSettingsCard(categories = categories, onSave = onSaveCategories, onRename = onRenameCategory) }
                    item { AiSettingsCard(enabled = aiEnabled, model = aiModel, onSave = onSaveAi) }
                    item { PoiSettingsCard(config = poiConfig, onSave = onSavePoiConfig) }
                    item { VerifySettingsCard(config = verifyConfig, onSave = onSaveVerifyConfig) }
                    if (lastAiError != null) {
                        item {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text("آخر خطأ في التعبئة الذكية:\n$lastAiError", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item { AppCheckTokenCard(token = appCheckToken, onLoad = onLoadAppCheckToken) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PendingStoreCard(st: com.example.data.model.StoreProfile, onApprove: () -> Unit, onSuspend: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreLogo(logoUrl = st.logoUrl, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.name, fontWeight = FontWeight.Bold)
                    Text(st.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (st.about.isNotBlank()) Text(st.about, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            val pics = listOf(st.coverUrl).filter { it.isNotBlank() } + st.menuImages
            if (pics.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    items(pics.size) { i ->
                        coil.compose.AsyncImage(
                            model = com.example.data.CloudinaryUrls.image(pics[i], 300),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                androidx.compose.material3.Button(
                    onClick = onApprove,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("اعتماد الصفحة ✓", fontSize = 12.sp) }
                OutlinedButton(onClick = onSuspend, shape = RoundedCornerShape(10.dp)) {
                    Text("إيقاف التاجر", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AdminOfferRow(
    offer: RestaurantOffer,
    onClick: () -> Unit,
    onToggleFeatured: () -> Unit,
    onToggleApproved: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("admin_offer_item_${offer.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductMediaViewer(mediaUri = offer.mediaUri, mediaType = offer.mediaType, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(offer.restaurantName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CalmSagePrimary, maxLines = 1)
                    Text(offer.offerTitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${offer.district} • ${offer.priceLabel()} • 👁 ${offer.viewsCount}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onToggleFeatured, shape = RoundedCornerShape(8.dp)) {
                    Icon(
                        imageVector = if (offer.isFeatured) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = null,
                        tint = if (offer.isFeatured) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (offer.isFeatured) "مميز ⭐" else "تمييز", fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (offer.isApproved) "نشط" else "معلق",
                        fontSize = 11.sp,
                        color = if (offer.isApproved) Color(0xFF16A34A) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(checked = offer.isApproved, onCheckedChange = onToggleApproved)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("admin_delete_offer_${offer.id}")) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** بطاقة مراجعة عرض: صورته وبياناته وأزرار الاعتماد/الحذف/الإيقاف. */
@Composable
private fun ReviewCard(
    offer: RestaurantOffer,
    onClick: () -> Unit = {},
    note: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    onDelete: () -> Unit,
    onSuspend: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductMediaViewer(
                    mediaUri = offer.mediaUri,
                    mediaType = offer.mediaType,
                    modifier = Modifier.size(90.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(offer.restaurantName, fontWeight = FontWeight.Bold, color = CalmSagePrimary, fontSize = 14.sp)
                    Text(offer.offerTitle, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${offer.priceLabel()} • ${offer.district}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (offer.description.isNotBlank()) {
                        Text(
                            offer.description,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (note != null) {
                Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                androidx.compose.material3.Button(
                    onClick = onPrimary,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) { Text(primaryLabel, fontSize = 12.sp, maxLines = 1) }
                if (secondaryLabel != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(secondaryLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف العرض", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onSuspend) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = "إيقاف التاجر",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    f: com.example.data.model.Feedback,
    onReply: (String, String) -> Unit,
    onStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    var reply by androidx.compose.runtime.remember(f.id) { androidx.compose.runtime.mutableStateOf(f.reply) }
    val isNew = f.status == com.example.data.model.Feedback.STATUS_NEW
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNew) Color(0xFFF0F7F3) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.type, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(com.example.data.model.Feedback.statusLabel(f.status), fontSize = 11.sp, color = CalmSagePrimary, fontWeight = FontWeight.Bold)
            }
            Text(
                listOf(f.role, f.userName, f.contact).filter { it.isNotBlank() }.joinToString(" • "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (f.createdAtMillis > 0) {
                Text(
                    java.text.SimpleDateFormat("d/M/yyyy h:mm a", java.util.Locale("ar")).format(java.util.Date(f.createdAtMillis)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(f.message, fontSize = 14.sp)
            if (f.screenshotUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = com.example.data.CloudinaryUrls.image(f.screenshotUrl, 400),
                    contentDescription = "صورة الشاشة",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(90.dp)
                        .clickable { onOpenImage(f.screenshotUrl) }
                )
            }
            Text("📱 ${f.device} • إصدار ${f.appVersion}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (f.uid != null) {
                androidx.compose.material3.OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    label = { Text("ردّك (يظهر له في التطبيق)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (f.contact.isBlank()) {
                Text("زائر بدون طريقة تواصل — ما يمديك ترد عليه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (f.uid != null) {
                    androidx.compose.material3.Button(
                        onClick = { onReply(reply, com.example.data.model.Feedback.STATUS_DONE) },
                        enabled = reply.isNotBlank(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) { Text("أرسل الرد", fontSize = 12.sp) }
                }
                if (f.status != com.example.data.model.Feedback.STATUS_PROGRESS && f.status != com.example.data.model.Feedback.STATUS_DONE) {
                    OutlinedButton(
                        onClick = { onStatus(com.example.data.model.Feedback.STATUS_PROGRESS) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) { Text("قيد المتابعة", fontSize = 11.sp) }
                }
                if (f.status != com.example.data.model.Feedback.STATUS_DONE) {
                    OutlinedButton(
                        onClick = { onStatus(com.example.data.model.Feedback.STATUS_DONE) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) { Text("تمت", fontSize = 11.sp) }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SupportNumberEditor(current: String, onSave: (String) -> Unit) {
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var value by androidx.compose.runtime.remember(current) { androidx.compose.runtime.mutableStateOf(current) }
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("⚙️ رقم واتساب الدعم", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(current, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { editing = !editing }) { Text(if (editing) "إلغاء" else "تغيير") }
            }
            if (editing) {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("الرقم الجديد") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.Button(
                    onClick = {
                        onSave(value)
                        editing = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) { Text("حفظ") }
            }
        }
    }
}

/** تصنيفات المتاجر: إضافة / تعديل الاسم / حذف / ترتيب. */
@Composable
private fun CategoriesSettingsCard(categories: List<String>, onSave: (List<String>) -> Unit, onRename: (String, String) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var newName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var renaming by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var renameText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var deleting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏷️ تصنيفات المتاجر (${categories.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("تظهر للتجار عند إعداد المتجر ونشر العروض، وللزبائن في الفلاتر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "إخفاء" else "إدارة") }
            }
            if (expanded) {
                categories.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(c, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(
                            onClick = { if (i > 0) onSave(categories.toMutableList().apply { add(i - 1, removeAt(i)) }) },
                            enabled = i > 0
                        ) { Text("↑", fontSize = 18.sp) }
                        androidx.compose.material3.IconButton(onClick = { renaming = c; renameText = c }) { Text("✎", fontSize = 16.sp) }
                        androidx.compose.material3.IconButton(onClick = { deleting = c }, enabled = categories.size > 1) { Text("🗑", fontSize = 16.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(40) },
                        label = { Text("تصنيف جديد (مع إيموجي)") },
                        placeholder = { Text("مثال: ورود وهدايا 💐") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val n = newName.trim()
                            if (n.isNotBlank() && n !in categories) { onSave(categories + n); newName = "" }
                        }
                    ) { Text("إضافة", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    renaming?.let { old ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("تعديل اسم التصنيف") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(value = renameText, onValueChange = { renameText = it.take(40) }, singleLine = true)
                    Text("العروض وصفحات المتاجر اللي على هذا التصنيف بتتحدّث للاسم الجديد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onRename(old, renameText); renaming = null }) { Text("حفظ") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = null }) { Text("إلغاء") } }
        )
    }
    deleting?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف \"$c\"؟") },
            text = { Text("ما بيظهر للتجار الجدد. العروض والمتاجر الحالية تبقى عليه لين يغيّرونه.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(categories - c); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
        )
    }
}

/** تفعيل/إيقاف التعبئة الذكية + اسم نموذج Gemini (يتغيّر بدون نسخة جديدة). */
@Composable
private fun AiSettingsCard(enabled: Boolean, model: String, onSave: (Boolean, String) -> Unit) {
    var modelText by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf(model) }
    var showModel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("✨ التعبئة الذكية للعروض", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (enabled) "مفعّلة — تظهر للتجار عند إضافة عرض بصورة" else "موقفة",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { onSave(it, modelText) })
            }
            androidx.compose.material3.TextButton(onClick = { showModel = !showModel }) {
                Text(if (showModel) "إخفاء الإعدادات المتقدمة" else "إعدادات متقدمة (اسم النموذج)", fontSize = 12.sp)
            }
            if (showModel) {
                androidx.compose.material3.OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("نموذج Gemini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.Button(
                    onClick = { onSave(enabled, modelText) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) { Text("حفظ") }
            }
        }
    }
}

/** يعرض رمز تجربة App Check مع زر نسخ (يُسجَّل في Firebase ← App Check ← Manage debug tokens). */
@Composable
private fun AppCheckTokenCard(token: String?, onLoad: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("🔐 رمز تجربة App Check", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "انسخه وسجّله في Firebase ← App Check ← Apps ← Manage debug tokens، عشان يشتغل الذكاء الاصطناعي على هذا الجوال.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (token == null) {
                androidx.compose.material3.TextButton(onClick = onLoad) { Text("اعرض الرمز") }
            } else {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(token, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                }
                androidx.compose.material3.TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(token))
                }) { Text("📋 نسخ") }
            }
        }
    }
}

/** نقاط محلات Google + بطاقة الدعوة: تشغيل/إيقاف ونص الدعوة ورابط التطبيق. */
@Composable
private fun PoiSettingsCard(
    config: com.example.data.repository.PoiConfig,
    onSave: (com.example.data.repository.PoiConfig) -> Unit
) {
    var message by androidx.compose.runtime.remember(config.inviteMessage) { androidx.compose.runtime.mutableStateOf(config.inviteMessage) }
    var link by androidx.compose.runtime.remember(config.appLink) { androidx.compose.runtime.mutableStateOf(config.appLink) }
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📍 محلات Google على الخريطة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("إظهار نقاط المحلات (باهتة)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = config.showGooglePoi, onCheckedChange = { onSave(config.copy(showGooglePoi = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بطاقة \"ادعه لحيّنا\" عند الضغط", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = config.inviteEnabled, onCheckedChange = { onSave(config.copy(inviteEnabled = it)) })
            }
            androidx.compose.material3.OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("نص الدعوة ({name} = اسم المحل)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.material3.OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("رابط تحميل التطبيق (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.material3.Button(
                onClick = { onSave(config.copy(inviteMessage = message.trim(), appLink = link.trim())) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(10.dp)
            ) { Text("حفظ النص والرابط") }
        }
    }
}

@Composable
private fun VerificationCard(
    r: com.example.data.model.VerificationRequest,
    onDecide: (Boolean, String) -> Unit,
    onOpenImage: (String) -> Unit
) {
    var reason by androidx.compose.runtime.remember(r.uid) { androidx.compose.runtime.mutableStateOf("") }
    var rejecting by androidx.compose.runtime.remember(r.uid) { androidx.compose.runtime.mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(r.storeName.ifBlank { r.uid.take(8) }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CalmSagePrimary)
            if (r.docNumber.isNotBlank()) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("${com.example.data.model.VerificationRequest.docLabel(r.docType)}: ${r.docNumber}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(
                            if (r.docType == com.example.data.model.VerificationRequest.DOC_FREELANCE) "https://freelance.sa" else "https://mc.gov.sa"
                        )))
                    }
                }) { Text("تحقق من الموقع الرسمي ›", fontSize = 12.sp) }
            }
            Text(
                when {
                    !r.locationChecked -> "📍 الموقع: ما تم التحقق"
                    r.locationOk -> "📍 كان عند المحل ✅ (${r.distanceMeters} م)"
                    else -> "📍 كان بعيد عن المحل ⚠️ (${r.distanceMeters} م)"
                },
                fontSize = 13.sp
            )
            if (r.photoUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = com.example.data.CloudinaryUrls.image(r.photoUrl, 500),
                    contentDescription = "صورة الواجهة",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onOpenImage(r.photoUrl) }
                )
            }
            if (rejecting) {
                androidx.compose.material3.OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب الرفض (يظهر للتاجر)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (!rejecting) {
                    androidx.compose.material3.Button(
                        onClick = { onDecide(true, "") },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("✓ وثّق") }
                    OutlinedButton(onClick = { rejecting = true }, shape = RoundedCornerShape(10.dp)) {
                        Text("رفض", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    androidx.compose.material3.Button(
                        onClick = { onDecide(false, reason.trim()) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("تأكيد الرفض") }
                    OutlinedButton(onClick = { rejecting = false }, shape = RoundedCornerShape(10.dp)) { Text("إلغاء") }
                }
            }
        }
    }
}

/** تشغيل/إيقاف التوثيق وكل شرط فيه. */
@Composable
private fun VerifySettingsCard(
    config: com.example.data.model.VerifyConfig,
    onSave: (com.example.data.model.VerifyConfig) -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("🛡️ توثيق المتاجر", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("التوثيق اختياري للتاجر: الموثّق ياخذ ✓ وينشر مباشرة، وغيره عروضه تمر بالمراجعة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Composable
            fun toggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
                }
            }
            toggle("تفعيل التوثيق (زر \"وثّق متجرك\" للتجار)", config.enabled) { onSave(config.copy(enabled = it)) }
            toggle("رقم السجل التجاري / العمل الحر", config.requireDoc, config.enabled) { onSave(config.copy(requireDoc = it)) }
            toggle("صورة حيّة لواجهة المحل", config.requirePhoto, config.enabled) { onSave(config.copy(requirePhoto = it)) }
            toggle("التأكد إنه عند المحل (GPS)", config.requireLocation, config.enabled) { onSave(config.copy(requireLocation = it)) }
        }
    }
}
