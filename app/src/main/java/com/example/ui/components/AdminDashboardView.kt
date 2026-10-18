package com.example.ui.components

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

    LazyColumn(
        modifier = modifier.testTag("admin_dashboard_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 📨 رسائل المستخدمين
        item {
            Text(
                text = "📨 رسائل المستخدمين" + if (newCount > 0) " ($newCount جديدة)" else "",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (newCount > 0) CalmSagePrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
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

        // ⚙️ رقم واتساب الدعم
        item { SupportNumberEditor(current = supportNumber, onSave = onSaveSupportNumber) }
        item { AiSettingsCard(enabled = aiEnabled, model = aiModel, onSave = onSaveAi) }
        if (lastAiError != null) {
            item {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("آخر خطأ في التعبئة الذكية:\n$lastAiError", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { AppCheckTokenCard(token = appCheckToken, onLoad = onLoadAppCheckToken) }

        // ⏳ بانتظار المراجعة
        item {
            Text(
                text = if (pending.isEmpty()) "بانتظار المراجعة: لا يوجد ✅" else "⏳ بانتظار المراجعة (${pending.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (pending.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF8A5A00)
            )
        }
        items(pending, key = { "p_" + it.id }) { o ->
            ReviewCard(
                offer = o,
                note = null,
                primaryLabel = "اعتماد ✓",
                onPrimary = { onApprove(o) },
                secondaryLabel = "اعتماد + توثيق التاجر",
                onSecondary = { onTrustMerchant(o) },
                onDelete = { onDeleteOffer(o.id) },
                onSuspend = { confirmSuspend = o }
            )
        }

        // 🏪 صفحات متاجر بانتظار المراجعة
        val visiblePendingStores = pendingStores.filter { st -> merchantStatuses.none { it.uid == st.uid && it.suspended } }
        if (visiblePendingStores.isNotEmpty()) {
            item {
                Text(
                    "🏪 صفحات متاجر بانتظار المراجعة (${visiblePendingStores.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF8A5A00)
                )
            }
            items(visiblePendingStores, key = { "st_" + it.uid }) { st ->
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
                                onClick = { onApproveStore(st) },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("اعتماد الصفحة ✓", fontSize = 12.sp) }
                            OutlinedButton(onClick = { onSuspendMerchant(st.uid, st.name) }, shape = RoundedCornerShape(10.dp)) {
                                Text("إيقاف التاجر", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // 🚩 مخفية بسبب البلاغات
        if (hiddenByReports.isNotEmpty()) {
            item {
                Text(
                    text = "🚩 مخفية بسبب البلاغات (${hiddenByReports.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(hiddenByReports, key = { "r_" + it.id }) { o ->
                ReviewCard(
                    offer = o,
                    note = "${o.reportsCount} بلاغات: " + reports.filter { it.offerId == o.id }.joinToString("، ") { it.reason }.take(200),
                    primaryLabel = "سليم، رجّعه",
                    onPrimary = { onKeepReported(o) },
                    secondaryLabel = null,
                    onSecondary = {},
                    onDelete = { onDeleteOffer(o.id) },
                    onSuspend = { confirmSuspend = o }
                )
            }
        }

        // ⛔ الحسابات الموقوفة
        if (suspended.isNotEmpty()) {
            item {
                Text("⛔ حسابات موقوفة (${suspended.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(suspended, key = { "s_" + it.uid }) { st ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(st.name.ifBlank { st.uid.take(8) }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { onUnsuspend(st) }) { Text("إلغاء الإيقاف") }
                    }
                }
            }
        }

        // البلاغات أولاً — تحتاج مراجعة
        item {
            Text(
                text = if (reports.isEmpty()) "البلاغات: لا يوجد ✅" else "🚩 البلاغات (${reports.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (reports.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        items(reports.size) { index ->
            val report = reports[index]
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(report.offerTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                    Text(report.restaurantName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("السبب: ${report.reason}", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        androidx.compose.material3.TextButton(onClick = { onDismissReport(report.id) }) {
                            Text("تجاهل البلاغ")
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            onDeleteOffer(report.offerId)
                            onDismissReport(report.id)
                        }) {
                            Text("احذف العرض", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Admin Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "لوحة تحكم إدارة التطبيق (Admin Portal)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "إدارة عروض المتاجر، الفيديوهات الإعلانية، واعتماد المواقع على الخريطة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Summary Statistics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminStatCard(
                    title = "إجمالي المتاجر",
                    value = "${offers.map { it.restaurantName }.distinct().size}",
                    icon = Icons.Default.Restaurant,
                    color = CalmSagePrimary,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "فيديوهات إعلانية",
                    value = "${offers.count { it.mediaType == MediaType.VIDEO }}",
                    icon = Icons.Default.Videocam,
                    color = Color(0xFFE05A47),
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "الأحياء المغطاة",
                    value = "${offers.map { it.district }.distinct().size}",
                    icon = Icons.Default.LocationOn,
                    color = SoftTerracotta,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "قائمة جميع العروض وإدارتها (${offers.size} عرض):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Offers Management Items
        items(offers, key = { it.id }) { offer ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_offer_item_${offer.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = offer.restaurantName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CalmSagePrimary
                            )
                            Text(
                                text = offer.offerTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (offer.mediaType == MediaType.VIDEO) Color(0xFFFDE8E8) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (offer.mediaType == MediaType.VIDEO) "فيديو 🎬" else "صورة 📷",
                                color = if (offer.mediaType == MediaType.VIDEO) Color(0xFFE05A47) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "الحي: ${offer.district} | المنطقة: ${offer.region} | السعر: ${offer.price} ر.س",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Featured on Map
                        OutlinedButton(
                            onClick = { onToggleFeatured(offer.id, !offer.isFeatured) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (offer.isFeatured) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = if (offer.isFeatured) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (offer.isFeatured) "مميز بالخريطة ⭐" else "تمييز العرض",
                                fontSize = 11.sp
                            )
                        }

                        // Toggle Active/Approved
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (offer.isApproved) "نشط" else "معلق",
                                fontSize = 11.sp,
                                color = if (offer.isApproved) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = offer.isApproved,
                                onCheckedChange = { onToggleApproved(offer.id, it) }
                            )
                        }

                        // Delete
                        IconButton(
                            onClick = { onDeleteOffer(offer.id) },
                            modifier = Modifier.testTag("admin_delete_offer_${offer.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
    note: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    onDelete: () -> Unit,
    onSuspend: () -> Unit
) {
    Card(
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
                        "${offer.price} ر.س • ${offer.district}",
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
