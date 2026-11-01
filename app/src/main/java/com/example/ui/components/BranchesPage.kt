package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.example.ui.i18n.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Branch
import com.example.data.model.MerchantProfile
import com.example.data.model.OfferTime
import com.example.ui.theme.CalmSagePrimary
import java.util.UUID

/**
 * صفحة "مطعمي وفروعي": اسم المطعم، الشعار، وقائمة الفروع (إضافة/تعديل/حذف).
 * [onSave] يستلم الملف الجديد + رابط الشعار المحلي إن اختار شعار جديد.
 */
@Composable
fun BranchesPage(
    profile: MerchantProfile,
    isSaving: Boolean,
    onSave: (MerchantProfile, StoreMediaEdits) -> Unit,
    onClose: () -> Unit,
    // جاي من "أنا صاحب المحل" والتوثيق مفعّل
    claimMode: Boolean = false,
    categories: List<String> = com.example.data.repository.RestaurantOfferRepository.DEFAULT_CATEGORIES,
    // "أنا صاحب المحل" وعنده متجر من قبل: نفتح فرع جديد في موقع المحل مباشرة
    claimBranch: Branch? = null,
    // «أنا صاحب المحل» ولمحله فروع فيها عروض إعلانات: نعرضها كلها جاهزة
    claimPreset: List<BulkRow>? = null,
    claimLead: String? = null
) {
    var restaurantName by remember { mutableStateOf(profile.restaurantName) }
    var logoLocal by remember { mutableStateOf<String?>(null) }
    var coverLocal by remember { mutableStateOf<String?>(null) }
    var about by remember { mutableStateOf(profile.about) }
    var category by remember {
        mutableStateOf(profile.cuisineType.takeIf { it in categories } ?: "")
    }
    var priceFromText by remember {
        mutableStateOf(if (profile.priceFrom > 0) profile.priceFrom.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } else "")
    }
    val menuImages = remember { mutableStateListOf<String>().apply { addAll(profile.menuImages) } }
    var delivery by remember { mutableStateOf(profile.delivery) }
    var deliveryFeeText by remember { mutableStateOf(moneyText(profile.deliveryFee)) }
    var deliveryMinText by remember { mutableStateOf(moneyText(profile.deliveryMinOrder)) }
    var deliveryFreeText by remember { mutableStateOf(moneyText(profile.deliveryFreeOver)) }
    var curbside by remember { mutableStateOf(profile.curbside) }
    var payCash by remember { mutableStateOf(profile.payCash) }
    var payCard by remember { mutableStateOf(profile.payCard) }
    // ✂️ التاجر يقص الغلاف والشعار بيده
    var cropCover by remember { mutableStateOf<String?>(null) }
    var cropLogo by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) cropCover = uri.toString()
    }
    cropCover?.let { src -> ImageCropDialog(src, aspect = 2f, title = "قص صورة الواجهة") { out -> if (out != null) coverLocal = out; cropCover = null } }
    cropLogo?.let { src -> ImageCropDialog(src, aspect = 1f, round = true, title = "قص الشعار", outWidth = 600) { out -> if (out != null) logoLocal = out; cropLogo = null } }
    val menuPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(com.example.data.model.StoreProfile.MAX_MENU_IMAGES)
    ) { uris: List<Uri> ->
        uris.forEach { if (menuImages.size < com.example.data.model.StoreProfile.MAX_MENU_IMAGES) menuImages.add(it.toString()) }
    }
    val branches = remember {
        mutableStateListOf<Branch>().apply {
            if (profile.branches.isNotEmpty()) addAll(profile.branches)
            else if (profile.hasLocation) add(
                // ملف قديم بموقع واحد: نعتبره الفرع الرئيسي
                Branch(
                    id = "main",
                    name = "الفرع الرئيسي",
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    region = profile.region,
                    district = profile.district,
                    phone = profile.phone,
                    openMinute = profile.openMinute,
                    closeMinute = profile.closeMinute
                )
            )
        }
    }
    var editing by remember { mutableStateOf<Branch?>(if (claimPreset.isNullOrEmpty()) claimBranch else null) }
    var bulkOpen by remember { mutableStateOf(false) }
    var presetRows by remember { mutableStateOf(claimPreset?.takeIf { it.isNotEmpty() }) }
    var pendingDelete by remember { mutableStateOf<Branch?>(null) }
    var error by remember { mutableStateOf("") }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) cropLogo = uri.toString()
    }

    val preset = presetRows
    if (bulkOpen || preset != null) {
        BulkBranchesPage(
            storeName = restaurantName,
            existing = branches.toList(),
            onAdd = { list ->
                // فرع «الرئيسي» الفاضي (بدون حي ورقم) من «أنا صاحب المحل» يتبدّل بالفروع الحقيقية
                if (preset != null) branches.removeAll { it.id == "main" && it.district.isBlank() && it.phone.isBlank() }
                branches.addAll(list); bulkOpen = false; presetRows = null
            },
            onCancel = { bulkOpen = false; if (preset != null) { presetRows = null; editing = claimBranch } },
            preset = preset,
            title = if (preset != null) "🏪 فروع محلك على لكوفر" else null,
            lead = if (preset != null) claimLead else null
        )
        return
    }

    val currentEditing = editing
    if (currentEditing != null) {
        BranchEditor(
            branch = currentEditing,
            isNew = branches.none { it.id == currentEditing.id },
            others = branches.filter { it.id != currentEditing.id },
            onDone = { updated ->
                val i = branches.indexOfFirst { it.id == updated.id }
                if (i >= 0) branches[i] = updated else branches.add(updated)
                editing = null
            },
            onCancel = { editing = null }
        )
        return
    }

    BackHandler { if (!isSaving) onClose() }

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("صفحة متجري", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (!isSaving) onClose() }) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
                }
                HorizontalDivider()

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (claimMode) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE0F2FE), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "🏪 عبّينا اسم المحل وموقعه من الخريطة. كمّل البيانات واحفظ، وبعدها بنطلب منك رقم السجل التجاري أو وثيقة العمل الحر لإثبات إنك صاحب المحل.",
                                fontSize = 13.sp,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // الشعار + الاسم
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(CalmSagePrimary.copy(alpha = 0.12f))
                                .clickable { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            val shown = logoLocal ?: profile.logoUrl.ifBlank { null }
                            if (shown != null) {
                                AsyncImage(model = shown, contentDescription = "الشعار", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(34.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("شعار المتجر", fontWeight = FontWeight.Bold)
                            Text(
                                "اضغط على الدائرة لاختيار الشعار. يظهر صغير على عروضك في الخريطة.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = restaurantName,
                        onValueChange = { restaurantName = it },
                        label = { Text("اسم المتجر *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---------- صفحة المتجر الدائمة ----------
                    Text("تصنيف المتجر *", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories.size) { i ->
                            val c = categories[i]
                            androidx.compose.material3.FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("صورة الغلاف", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        val cover = coverLocal ?: profile.coverUrl.ifBlank { null }
                        if (cover != null) {
                            AsyncImage(model = cover, contentDescription = "الغلاف", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text("📷 اضغط لاختيار صورة واجهة المحل أو الداخل", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedTextField(
                        value = about,
                        onValueChange = { if (it.length <= 500) about = it },
                        label = { Text("نبذة عن المتجر") },
                        placeholder = { Text("مثال: مطعم شاورما عربي من 2010، نستخدم خبز صاج طازج يومياً") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = priceFromText,
                        onValueChange = { priceFromText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("الأسعار تبدأ من (ر.س)") },
                        placeholder = { Text("مثال: 15") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    DeliveryEditor(
                        delivery = delivery,
                        onDeliveryChange = { delivery = it },
                        feeText = deliveryFeeText,
                        onFeeChange = { deliveryFeeText = it },
                        minText = deliveryMinText,
                        onMinChange = { deliveryMinText = it },
                        freeText = deliveryFreeText,
                        onFreeChange = { deliveryFreeText = it }
                    )

                    // 🚗 استلام من السيارة + 💳 طرق الدفع عند الاستلام
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🚗 أوفّر الاستلام من السيارة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("العميل يكتب وصف سيارته ويضغط «وصلت»", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HSwitch(checked = curbside, onCheckedChange = { curbside = it })
                    }
                    Text("💳 طرق الدفع اللي أقبلها عند الاستلام", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = payCash, onCheckedChange = { payCash = it; if (!it && !payCard) payCard = true })
                        Text("💵 كاش", fontSize = 14.sp)
                        Spacer(Modifier.width(16.dp))
                        androidx.compose.material3.Checkbox(checked = payCard, onCheckedChange = { payCard = it; if (!it && !payCash) payCash = true })
                        Text("💳 بطاقة / شبكة", fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "المنيو / المنتجات والأسعار (${menuImages.size}/${com.example.data.model.StoreProfile.MAX_MENU_IMAGES})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "صوّر المنيو أو قائمة الأسعار — تظهر دائماً في صفحة متجرك",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { menuPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = menuImages.size < com.example.data.model.StoreProfile.MAX_MENU_IMAGES,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("صور")
                        }
                    }
                    if (menuImages.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(menuImages.size) { i ->
                                val img = menuImages[i]
                                Box(Modifier.size(width = 90.dp, height = 120.dp).clip(RoundedCornerShape(8.dp))) {
                                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(26.dp)
                                    ) {
                                        IconButton(onClick = { menuImages.remove(img) }) {
                                            Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الفروع (${branches.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                val last = branches.lastOrNull()
                                editing = Branch(
                                    id = UUID.randomUUID().toString().take(12),
                                    phone = "", // كل فرع له رقمه
                                    openMinute = last?.openMinute ?: -1,
                                    closeMinute = last?.closeMinute ?: -1
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("أضف فرع")
                        }
                    }
                    OutlinedButton(onClick = { bulkOpen = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("➕ أضف عدة فروع (من Google أو الخريطة)")
                    }
                    if (branches.any { it.phone.isBlank() }) Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFEEF4FF), modifier = Modifier.fillMaxWidth()) {
                        Text("📞 ${branches.count { it.phone.isBlank() }} فرع بدون رقم — طلباتها تروح لرقم المتجر الأساسي.", fontSize = 12.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(10.dp))
                    }

                    if (branches.isEmpty()) {
                        Text(
                            "ما أضفت فروع للحين. أضف فرعك الأول، وبعدها تقدر تنشر العرض لكل فروعك مرة وحدة.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    branches.forEach { branch ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    androidx.compose.material3.Text(branch.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(branch.district, branch.landmark, branch.region).filter { it.isNotBlank() }.joinToString(" — "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val hours = if (branch.openMinute in 0..1439) {
                                        if (branch.openMinute == branch.closeMinute) "24 ساعة"
                                        else "${OfferTime.formatMinute(branch.openMinute)} – ${OfferTime.formatMinute(branch.closeMinute)}"
                                    } else ""
                                    Text(
                                        listOf(toLocalPhone(branch.phone), hours).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editing = branch }) { Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = CalmSagePrimary) }
                                IconButton(onClick = { pendingDelete = branch }) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }

                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)) {
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                when {
                                    restaurantName.isBlank() -> error = "اكتب اسم المتجر."
                                    category.isBlank() -> error = "اختر تصنيف المتجر."
                                    branches.isEmpty() -> error = "أضف فرع واحد على الأقل."
                                    branches.none { it.phone.isNotBlank() } -> error = "اكتب رقم جوال فرع واحد على الأقل (هو الرقم الأساسي للمتجر)."
                                    branches.mapNotNull { normalizeSaudiPhone(it.phone) }.let { it.size != it.toSet().size } ->
                                        error = "فيه فرعين بنفس الرقم — كل فرع لازم يكون له رقمه."
                                    else -> onSave(
                                        profile.copy(
                                            restaurantName = restaurantName.trim(),
                                            branches = branches.toList(),
                                            cuisineType = category,
                                            about = about.trim(),
                                            priceFrom = priceFromText.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0,
                                            menuImages = menuImages.toList(),
                                            delivery = delivery,
                                            deliveryFee = if (delivery) deliveryFeeText.toDoubleOrNull() ?: 0.0 else 0.0,
                                            deliveryMinOrder = if (delivery) deliveryMinText.toDoubleOrNull() ?: 0.0 else 0.0,
                                            deliveryFreeOver = if (delivery) deliveryFreeText.toDoubleOrNull() ?: 0.0 else 0.0,
                                            curbside = curbside, payCash = payCash, payCard = payCard
                                        ),
                                        StoreMediaEdits(newLogo = logoLocal, newCover = coverLocal)
                                    )
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("جاري الحفظ...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { branch ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف ${branch.displayName}؟") },
            text = { Text("بيختفي هذا الفرع من كل عروضك بعد الحفظ.") },
            confirmButton = {
                TextButton(onClick = {
                    branches.removeAll { it.id == branch.id }
                    pendingDelete = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
        )
    }
}

/** صور جديدة اختارها التاجر (روابط محلية تنرفع عند الحفظ). */
data class StoreMediaEdits(val newLogo: String?, val newCover: String?)

/** شاشة إضافة/تعديل فرع واحد. */
@Composable
private fun BranchEditor(
    branch: Branch,
    isNew: Boolean,
    others: List<Branch> = emptyList(),
    onDone: (Branch) -> Unit,
    onCancel: () -> Unit
) {
    val hasLocation = branch.latitude != 0.0 || branch.longitude != 0.0
    var name by remember { mutableStateOf(branch.name) }
    var lat by remember { mutableStateOf(if (hasLocation) branch.latitude else 24.7136) }
    var lng by remember { mutableStateOf(if (hasLocation) branch.longitude else 46.6753) }
    var district by remember { mutableStateOf(branch.district) }
    var region by remember { mutableStateOf(branch.region) }
    var phone by remember { mutableStateOf(toLocalPhone(branch.phone)) }
    var landmark by remember { mutableStateOf(branch.landmark) }
    var open24 by remember { mutableStateOf(branch.openMinute >= 0 && branch.openMinute == branch.closeMinute) }
    var openMinute by remember { mutableStateOf(branch.openMinute.takeIf { it >= 0 } ?: (12 * 60)) }
    var closeMinute by remember { mutableStateOf(branch.closeMinute.takeIf { it >= 0 } ?: 0) }
    var error by remember { mutableStateOf("") }
    var ack by remember { mutableStateOf(!isNew && branch.phone.isBlank()) }

    BackHandler { onCancel() }

    Box(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isNew) "فرع جديد" else "تعديل الفرع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم الفرع (اختياري)") },
                        placeholder = { Text("مثال: فرع العليا") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LocationPickerCard(
                        latitude = lat,
                        longitude = lng,
                        onLocationChange = { a, b -> lat = a; lng = b },
                        district = district,
                        onDistrictChange = { district = it },
                        region = region,
                        onRegionChange = { region = it },
                        autoLocate = !hasLocation,
                        title = "موقع الفرع",
                        pinTitle = name.ifBlank { "الفرع" }
                    )
                    OutlinedTextField(
                        value = landmark,
                        onValueChange = { landmark = it.take(80) },
                        label = { Text("علامة مميزة (اختياري)") },
                        placeholder = { Text("مثال: بجوار البريد السعودي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم واتساب / جوال الفرع *") },
                        placeholder = { Text("05xxxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (phone.isBlank() && others.any { it.phone.isNotBlank() }) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ack, onCheckedChange = { ack = it })
                        Text("📞 بدون رقم — أأكد على مسؤوليتي إن طلبات هذا الفرع تروح لرقم المتجر الأساسي", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                    Text("أوقات الدوام 🕐", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = open24, onCheckedChange = { open24 = it })
                        Text("مفتوح 24 ساعة", fontSize = 13.sp)
                    }
                    if (!open24) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeDropdown("يفتح", openMinute, { openMinute = it }, Modifier.weight(1f))
                            TimeDropdown("يقفل", closeMinute, { closeMinute = it }, Modifier.weight(1f))
                        }
                    }
                }
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)) {
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                val normalized = if (phone.isBlank()) "" else normalizeSaudiPhone(phone)
                                when {
                                    district.isBlank() -> error = "حدد موقع الفرع واكتب الحي."
                                    normalized == null -> error = "اكتب رقم جوال صحيح مثل 05xxxxxxxx."
                                    normalized.isBlank() && others.none { it.phone.isNotBlank() } -> error = "اكتب رقم جوال الفرع (لازم رقم واحد على الأقل للمتجر)."
                                    normalized.isBlank() && !ack -> error = "اكتب رقم الفرع، أو أكّد إن طلباته تروح للرقم الأساسي."
                                    normalized.isNotBlank() && others.any { normalizeSaudiPhone(it.phone) == normalized } ->
                                        error = "الرقم هذا مسجّل لفرع ${others.first { normalizeSaudiPhone(it.phone) == normalized }.let { b -> b.name.ifBlank { b.district.ifBlank { "ثاني" } } }}، أدخل رقم ثاني."
                                    else -> onDone(
                                        branch.copy(
                                            name = name.trim(),
                                            latitude = lat,
                                            longitude = lng,
                                            district = district.trim(),
                                            region = region.trim(),
                                            phone = normalized,
                                            landmark = landmark.trim(),
                                            openMinute = if (open24) 0 else openMinute,
                                            closeMinute = if (open24) 0 else closeMinute
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text(if (isNew) "أضف الفرع" else "حفظ الفرع", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                }
            }
        }
    }
}
