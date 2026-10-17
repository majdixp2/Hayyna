package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Branch
import com.example.data.model.LocationPresets
import com.example.data.model.MediaType
import com.example.data.model.MerchantProfile
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.data.model.gallery
import com.example.ui.theme.CalmSagePrimary

private const val MAX_IMAGES = 3

/**
 * صفحة إضافة/تعديل عرض.
 * - إذا التاجر عنده فروع: يختار "كل الفروع" أو فروع معيّنة.
 * - إذا ما عنده فروع: يحدد موقع المحل هنا، ويصير أول فرع له تلقائياً.
 */
@Composable
fun AddRestaurantOfferSheet(
    onDismiss: () -> Unit,
    onSaveOffer: (RestaurantOffer) -> Unit,
    isSaving: Boolean = false,
    editingOffer: RestaurantOffer? = null,
    profile: MerchantProfile? = null,
    onManageBranches: () -> Unit = {}
) {
    val isEditing = editingOffer != null
    val profileBranches = profile?.branches.orEmpty()
    val hasBranches = profileBranches.isNotEmpty()

    // ---------- بيانات العرض ----------
    var restaurantName by remember { mutableStateOf(editingOffer?.restaurantName ?: profile?.restaurantName.orEmpty()) }
    var offerTitle by remember { mutableStateOf(editingOffer?.offerTitle.orEmpty()) }
    var description by remember { mutableStateOf(editingOffer?.description.orEmpty()) }
    var cuisineType by remember {
        mutableStateOf(editingOffer?.cuisineType ?: profile?.cuisineType?.takeIf { it.isNotBlank() } ?: LocationPresets.cuisines[1])
    }
    var priceText by remember { mutableStateOf(editingOffer?.price?.let { formatPrice(it) }.orEmpty()) }
    var originalPriceText by remember { mutableStateOf(editingOffer?.originalPrice?.let { formatPrice(it) }.orEmpty()) }

    // ---------- الصور / الفيديو ----------
    var mediaType by remember { mutableStateOf(editingOffer?.mediaType ?: MediaType.IMAGE) }
    val media = remember { mutableStateListOf<String>().apply { editingOffer?.let { addAll(it.gallery) } } }

    // ---------- الفروع ----------
    val initiallySelected = editingOffer?.branches?.map { it.id }?.toSet().orEmpty()
    var allBranches by remember {
        mutableStateOf(!isEditing || initiallySelected.isEmpty() || profileBranches.all { it.id in initiallySelected })
    }
    val selectedIds = remember { mutableStateListOf<String>().apply { addAll(initiallySelected) } }

    // ---------- موقع (لما ما فيه فروع) ----------
    val startLat = editingOffer?.latitude ?: profile?.takeIf { it.hasLocation }?.latitude
    val startLng = editingOffer?.longitude ?: profile?.takeIf { it.hasLocation }?.longitude
    var latitude by remember { mutableStateOf(startLat ?: 24.7136) }
    var longitude by remember { mutableStateOf(startLng ?: 46.6753) }
    var region by remember { mutableStateOf(editingOffer?.region ?: profile?.region.orEmpty()) }
    var district by remember { mutableStateOf(editingOffer?.district ?: profile?.district.orEmpty()) }
    var phone by remember { mutableStateOf(toLocalPhone(editingOffer?.phone ?: profile?.phone.orEmpty())) }
    val startOpen = editingOffer?.openMinute?.takeIf { it >= 0 } ?: profile?.openMinute?.takeIf { it >= 0 }
    val startClose = editingOffer?.closeMinute?.takeIf { it >= 0 } ?: profile?.closeMinute?.takeIf { it >= 0 }
    var openMinute by remember { mutableStateOf(startOpen ?: (12 * 60)) }
    var closeMinute by remember { mutableStateOf(startClose ?: 0) }
    var isOpen24h by remember { mutableStateOf(startOpen != null && startOpen == startClose) }

    // ---------- المدة ----------
    var expiryOptionIndex by remember { mutableStateOf(0) }
    var keepCurrentExpiry by remember { mutableStateOf(isEditing) }

    var errorMessage by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val imagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (mediaType != MediaType.IMAGE) media.clear()
            mediaType = MediaType.IMAGE
            uris.forEach { if (media.size < MAX_IMAGES) media.add(it.toString()) }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            media.clear()
            media.add(uri.toString())
            mediaType = MediaType.VIDEO
        }
    }

    val hasUnsavedInput = offerTitle.isNotBlank() || media.isNotEmpty() || priceText.isNotBlank() || description.isNotBlank()
    fun requestClose() {
        when {
            isSaving -> Unit
            hasUnsavedInput -> showDiscardDialog = true
            else -> onDismiss()
        }
    }

    fun submit() {
        val price = priceText.toDoubleOrNull()
        val original = originalPriceText.toDoubleOrNull()
        val chosenBranches: List<Branch> = if (hasBranches) {
            if (allBranches) profileBranches else profileBranches.filter { it.id in selectedIds }
        } else {
            val normalized = normalizeSaudiPhone(phone)
            if (normalized == null || district.isBlank()) emptyList()
            else listOf(
                Branch(
                    id = "main",
                    name = "الفرع الرئيسي",
                    latitude = latitude,
                    longitude = longitude,
                    region = region.trim(),
                    district = district.trim(),
                    phone = normalized,
                    openMinute = if (isOpen24h) 0 else openMinute,
                    closeMinute = if (isOpen24h) 0 else closeMinute
                )
            )
        }
        errorMessage = when {
            restaurantName.isBlank() -> "يرجى كتابة اسم المطعم."
            offerTitle.isBlank() -> "يرجى كتابة عنوان العرض."
            price == null || price <= 0 -> "يرجى إدخال سعر صحيح للعرض."
            media.isEmpty() -> "يرجى إرفاق صورة أو فيديو للعرض."
            !hasBranches && district.isBlank() -> "حدد موقع المحل واكتب الحي."
            !hasBranches && normalizeSaudiPhone(phone) == null -> "يرجى كتابة رقم جوال صحيح مثل 05xxxxxxxx."
            chosenBranches.isEmpty() -> "اختر فرع واحد على الأقل."
            else -> ""
        }
        if (errorMessage.isNotBlank() || price == null) return

        val primary = chosenBranches.first()
        val discountPct = if (original != null && original > price) ((original - price) / original * 100).toInt() else 0
        val badge = when {
            discountPct >= 1 -> "خصم $discountPct% 🔥"
            mediaType == MediaType.VIDEO -> "فيديو إعلاني 🎬"
            else -> ""
        }
        val offer = (editingOffer ?: RestaurantOffer()).copy(
            restaurantName = restaurantName.trim(),
            offerTitle = offerTitle.trim(),
            description = description.trim().ifBlank { "عرض متاح لجميع زبائن المطعم." },
            cuisineType = cuisineType,
            price = price,
            originalPrice = original,
            discountBadge = badge,
            mediaType = mediaType,
            mediaUri = media.first(),
            mediaUris = media.toList(),
            isPromotionalVideo = mediaType == MediaType.VIDEO,
            branches = chosenBranches,
            latitude = primary.latitude,
            longitude = primary.longitude,
            region = primary.region,
            district = primary.district,
            phone = primary.phone,
            openMinute = primary.openMinute,
            closeMinute = primary.closeMinute,
            workingHours = OfferTime.hoursText(RestaurantOffer(openMinute = primary.openMinute, closeMinute = primary.closeMinute)),
            logoUrl = profile?.logoUrl.orEmpty(),
            expiresAt = if (keepCurrentExpiry) editingOffer?.expiresAt else EXPIRY_OPTIONS[expiryOptionIndex].expiresAt()
        )
        onSaveOffer(offer)
    }

    BackHandler { requestClose() }

    // صفحة كاملة فوق التطبيق، تحترم شريط الحالة وشريط التنقل والكيبورد
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .testTag("add_restaurant_offer_sheet")
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "تعديل العرض" else "عرض جديد",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { requestClose() }) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
                }
                HorizontalDivider()

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ---------- الصور / الفيديو ----------
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("صور العرض (حتى $MAX_IMAGES) أو فيديو", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { imagesPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    enabled = !(mediaType == MediaType.IMAGE && media.size >= MAX_IMAGES),
                                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("صور 📷", fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE05A47)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("فيديو 🎬", fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (media.isEmpty()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("أرفق صور العرض أو فيديو 📷", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(media.toList()) { uri ->
                                        Box(Modifier.size(width = 120.dp, height = 110.dp)) {
                                            ProductMediaViewer(
                                                mediaUri = uri,
                                                mediaType = mediaType,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                                    .size(26.dp)
                                            ) {
                                                IconButton(onClick = { media.remove(uri) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (mediaType == MediaType.IMAGE) {
                                    Text(
                                        "أول صورة هي الغلاف اللي يطلع على الخريطة.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = offerTitle,
                        onValueChange = { offerTitle = it },
                        label = { Text("عنوان العرض *") },
                        placeholder = { Text("مثال: وجبة برجر + بطاطس ومشروب مجاناً") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_offer_title")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("السعر (ر.س) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_offer_price")
                        )
                        OutlinedTextField(
                            value = originalPriceText,
                            onValueChange = { originalPriceText = it },
                            label = { Text("قبل الخصم") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("تفاصيل العرض") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("نوع المأكولات", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(LocationPresets.cuisines.filter { it != "الكل" }) { cui ->
                            FilterChip(selected = cuisineType == cui, onClick = { cuisineType = cui }, label = { Text(cui, fontSize = 12.sp) })
                        }
                    }

                    OutlinedTextField(
                        value = restaurantName,
                        onValueChange = { restaurantName = it },
                        label = { Text("اسم المطعم *") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_restaurant_name")
                    )

                    // ---------- الفروع أو الموقع ----------
                    if (hasBranches) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("وين يظهر العرض؟", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CalmSagePrimary, modifier = Modifier.weight(1f))
                                    TextButton(onClick = onManageBranches) { Text("إدارة الفروع") }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = allBranches, onCheckedChange = { allBranches = it })
                                    Text("كل الفروع (${profileBranches.size})", fontWeight = FontWeight.Bold)
                                }
                                if (!allBranches) {
                                    profileBranches.forEach { b ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                                            Checkbox(
                                                checked = b.id in selectedIds,
                                                onCheckedChange = { checked -> if (checked) selectedIds.add(b.id) else selectedIds.remove(b.id) }
                                            )
                                            Column {
                                                Text(b.displayName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(b.region, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "الرقم والدوام يكونون حسب كل فرع.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LocationPickerCard(
                            latitude = latitude,
                            longitude = longitude,
                            onLocationChange = { a, b -> latitude = a; longitude = b },
                            district = district,
                            onDistrictChange = { district = it },
                            region = region,
                            onRegionChange = { region = it },
                            autoLocate = startLat == null
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("رقم واتساب / جوال المطعم *") },
                            placeholder = { Text("05xxxxxxxx") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("أوقات الدوام 🕐", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isOpen24h, onCheckedChange = { isOpen24h = it })
                            Text("مفتوح 24 ساعة", fontSize = 13.sp)
                        }
                        if (!isOpen24h) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimeDropdown("يفتح", openMinute, { openMinute = it }, Modifier.weight(1f))
                                TimeDropdown("يقفل", closeMinute, { closeMinute = it }, Modifier.weight(1f))
                            }
                        }
                        OutlinedButton(onClick = onManageBranches, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("عندك أكثر من فرع؟ أضف فروعك")
                        }
                    }

                    // ---------- المدة ----------
                    Text("مدة العرض ⏰", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isEditing) {
                            item {
                                val current = editingOffer?.let { OfferTime.remainingText(it) } ?: "بدون انتهاء"
                                FilterChip(
                                    selected = keepCurrentExpiry,
                                    onClick = { keepCurrentExpiry = true },
                                    label = { Text("بدون تغيير ($current)", fontSize = 12.sp) }
                                )
                            }
                        }
                        items(EXPIRY_OPTIONS.size) { index ->
                            FilterChip(
                                selected = !keepCurrentExpiry && expiryOptionIndex == index,
                                onClick = {
                                    keepCurrentExpiry = false
                                    expiryOptionIndex = index
                                },
                                label = { Text(EXPIRY_OPTIONS[index].label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Text("بعد انتهاء المدة يختفي العرض تلقائياً من الخريطة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // زر النشر ثابت أسفل الصفحة
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)) {
                        if (errorMessage.isNotBlank()) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(
                            onClick = { submit() },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_restaurant_offer_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(if (isEditing) "جاري الحفظ..." else "جاري رفع العرض...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            } else {
                                Text(if (isEditing) "حفظ التعديلات ✓" else "انشر العرض ✨", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("تجاهل العرض؟") },
                text = { Text("المعلومات اللي كتبتها بتضيع.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    }) { Text("تجاهل", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("كمّل التعبئة") } }
            )
        }
    }
}

/** خيارات مدة العرض. */
private data class ExpiryOption(val label: String, val expiresAt: () -> com.google.firebase.Timestamp?)

// الخيارات تُبنى من ملف OfferDurations.kt (عدّل الأيام هناك)
private val EXPIRY_OPTIONS: List<ExpiryOption> =
    (if (com.example.ui.ALLOW_NO_EXPIRY) listOf(ExpiryOption("بدون انتهاء") { null }) else emptyList()) +
        com.example.ui.OFFER_DURATION_DAYS.filter { it > 0 }.map { days ->
            ExpiryOption(com.example.ui.durationLabel(days)) { timestampAfterHours(days * 24) }
        }

private fun timestampAfterHours(hours: Int): com.google.firebase.Timestamp =
    com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + hours * 3_600_000L))

private fun formatPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
