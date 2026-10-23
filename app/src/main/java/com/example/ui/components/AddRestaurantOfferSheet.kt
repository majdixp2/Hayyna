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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
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
import com.example.data.model.OfferPricing
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddRestaurantOfferSheet(
    onDismiss: () -> Unit,
    onSaveOffer: (RestaurantOffer) -> Unit,
    isSaving: Boolean = false,
    editingOffer: RestaurantOffer? = null,
    profile: MerchantProfile? = null,
    onManageBranches: () -> Unit = {},
    aiEnabled: Boolean = false,
    isAutofilling: Boolean = false,
    onAutofill: (imageUri: String, onResult: (com.example.data.AiAutofill.Draft) -> Unit) -> Unit = { _, _ -> },
    categories: List<String> = com.example.data.repository.RestaurantOfferRepository.DEFAULT_CATEGORIES
) {
    val isEditing = editingOffer != null
    val profileBranches = profile?.branches.orEmpty()
    val hasBranches = profileBranches.isNotEmpty()

    // ---------- بيانات العرض ----------
    var restaurantName by remember { mutableStateOf(editingOffer?.restaurantName ?: profile?.restaurantName.orEmpty()) }
    var offerTitle by remember { mutableStateOf(editingOffer?.offerTitle.orEmpty()) }
    var description by remember { mutableStateOf(editingOffer?.description.orEmpty()) }
    var cuisineType by remember {
        mutableStateOf(editingOffer?.cuisineType ?: profile?.cuisineType?.takeIf { it.isNotBlank() } ?: categories.firstOrNull().orEmpty())
    }
    var priceText by remember { mutableStateOf(editingOffer?.price?.let { formatPrice(it) }.orEmpty()) }
    var originalPriceText by remember { mutableStateOf(editingOffer?.originalPrice?.let { formatPrice(it) }.orEmpty()) }

    // ---------- خصم بالنسبة / متدرّج / بداية / افتتاح ----------
    var percentMode by remember { mutableStateOf(editingOffer?.let { OfferPricing.isPercentOnly(it) } ?: false) }
    var percentText by remember { mutableStateOf(editingOffer?.discountPercent?.takeIf { it > 0 }?.toString().orEmpty()) }
    var tiered by remember { mutableStateOf(editingOffer?.dailyPercents?.isNotEmpty() == true) }
    val dayPercents = remember {
        mutableStateListOf<String>().apply {
            editingOffer?.dailyPercents?.forEach { add(it.toString()) }
            if (isEmpty()) { add(""); add("") }
        }
    }
    var itemsScope by remember { mutableStateOf(editingOffer?.itemsScope.orEmpty()) }
    var grandOpening by remember { mutableStateOf(editingOffer?.grandOpening ?: false) }
    // ✨ جديدنا: صنف/منتج جديد (السعر والخصم اختياري)
    var isNewProduct by remember { mutableStateOf(editingOffer?.postType == "new") }
    var startMillis by remember { mutableStateOf(editingOffer?.startsAt?.toDate()?.time) }
    var showDatePicker by remember { mutableStateOf(false) }

    // ---------- الوسوم / التوصيل / المَعلَم ----------
    var tagsText by remember { mutableStateOf(editingOffer?.tags?.joinToString("، ").orEmpty()) }
    var delivery by remember { mutableStateOf(editingOffer?.delivery ?: profile?.delivery ?: false) }
    var deliveryFeeText by remember { mutableStateOf(moneyText(editingOffer?.deliveryFee ?: profile?.deliveryFee ?: 0.0)) }
    var deliveryMinText by remember { mutableStateOf(moneyText(editingOffer?.deliveryMinOrder ?: profile?.deliveryMinOrder ?: 0.0)) }
    var deliveryFreeText by remember { mutableStateOf(moneyText(editingOffer?.deliveryFreeOver ?: profile?.deliveryFreeOver ?: 0.0)) }
    var landmark by remember {
        mutableStateOf(editingOffer?.branches?.firstOrNull()?.landmark ?: profile?.branches?.firstOrNull()?.landmark.orEmpty())
    }

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
    // مدة مخصصة بالأيام (expiryOptionIndex = -1)
    var customDaysText by remember { mutableStateOf("") }
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
        val price = if (percentMode) 0.0 else if (isNewProduct && priceText.isBlank()) 0.0 else priceText.toDoubleOrNull()
        val original = if (percentMode) null else originalPriceText.toDoubleOrNull()
        val pct = percentText.trim().toIntOrNull()
        val steps = dayPercents.mapNotNull { it.trim().toIntOrNull() }
        val now = System.currentTimeMillis()
        val dayMs = 24 * 3_600_000L
        val useTiers = percentMode && tiered
        val base = startMillis ?: (if (useTiers) editingOffer?.createdAt?.toDate()?.time else null) ?: now
        val newExpiry: Long? = when {
            useTiers -> OfferPricing.midnight(base) + steps.size * dayMs
            keepCurrentExpiry -> editingOffer?.expiresAt?.toDate()?.time
            expiryOptionIndex < 0 -> customDaysText.toIntOrNull()?.takeIf { it in 1..90 }?.let { base + it * dayMs }
            else -> EXPIRY_OPTIONS[expiryOptionIndex].days?.let { base + it * dayMs }
        }
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
                    landmark = landmark.trim(),
                    openMinute = if (isOpen24h) 0 else openMinute,
                    closeMinute = if (isOpen24h) 0 else closeMinute
                )
            )
        }
        errorMessage = when {
            restaurantName.isBlank() -> "يرجى كتابة اسم المتجر."
            offerTitle.isBlank() -> if (isNewProduct) "اكتب اسم المنتج." else "يرجى كتابة عنوان العرض."
            isNewProduct && price == null -> "اكتب سعر صحيح أو اتركه فاضي."
            isNewProduct && original != null && (price ?: 0.0) <= 0.0 -> "اكتب السعر بعد الخصم."
            !useTiers && !keepCurrentExpiry && expiryOptionIndex < 0 && customDaysText.toIntOrNull()?.takeIf { it in 1..90 } == null -> "اكتب عدد أيام العرض (من 1 إلى 90)."
            !percentMode && !isNewProduct && (price == null || price <= 0) -> "يرجى إدخال سعر صحيح للعرض."
            useTiers && (steps.size < 2 || steps.size != dayPercents.size || steps.any { it !in 1..99 }) -> "اكتب نسبة الخصم لكل يوم (من 1 إلى 99)."
            percentMode && !useTiers && (pct == null || pct !in 1..99) -> "اكتب نسبة الخصم (من 1 إلى 99)."
            startMillis != null && startMillis!! > now + 60 * dayMs -> "تاريخ البداية بعيد، اختر تاريخ خلال شهرين."
            newExpiry != null && newExpiry <= now -> "حسب التاريخ والمدة العرض منتهي، عدّل تاريخ البداية أو المدة."
            media.isEmpty() -> "يرجى إرفاق صورة أو فيديو للعرض."
            !hasBranches && district.isBlank() -> "حدد موقع المحل واكتب الحي."
            !hasBranches && normalizeSaudiPhone(phone) == null -> "يرجى كتابة رقم جوال صحيح مثل 05xxxxxxxx."
            chosenBranches.isEmpty() -> "اختر فرع واحد على الأقل."
            else -> ""
        }
        if (errorMessage.isNotBlank() || price == null) return

        val primary = chosenBranches.first()
        val discountPct = when {
            useTiers -> steps.first()
            percentMode -> pct ?: 0
            original != null && original > price -> ((original - price) / original * 100).toInt()
            else -> 0
        }
        val badge = when {
            isNewProduct && discountPct < 1 -> "جديد ✨"
            discountPct >= 1 -> "خصم $discountPct% 🔥"
            mediaType == MediaType.VIDEO -> "فيديو إعلاني 🎬"
            else -> ""
        }
        val offer = (editingOffer ?: RestaurantOffer()).copy(
            restaurantName = restaurantName.trim(),
            offerTitle = offerTitle.trim(),
            description = description.trim().ifBlank { if (isNewProduct) "منتج جديد متوفر الحين." else "عرض متاح لجميع العملاء." },
            postType = if (isNewProduct) "new" else "offer",
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
            expiresAt = newExpiry?.let { com.google.firebase.Timestamp(java.util.Date(it)) },
            startsAt = startMillis?.let { com.google.firebase.Timestamp(java.util.Date(it)) },
            discountPercent = if (percentMode) (if (useTiers) steps.first() else pct ?: 0) else 0,
            dailyPercents = if (useTiers) steps.map { it.toLong() } else emptyList(),
            itemsScope = if (percentMode) itemsScope.trim().take(40) else "",
            grandOpening = grandOpening && !isNewProduct,
            tags = parseTags(tagsText),
            delivery = delivery,
            deliveryFee = if (delivery) deliveryFeeText.toDoubleOrNull() ?: 0.0 else 0.0,
            deliveryMinOrder = if (delivery) deliveryMinText.toDoubleOrNull() ?: 0.0 else 0.0,
            deliveryFreeOver = if (delivery) deliveryFreeText.toDoubleOrNull() ?: 0.0 else 0.0
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

                    // ✨ التعبئة الذكية من الصورة
                    if (aiEnabled && mediaType == MediaType.IMAGE && media.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                onAutofill(media.first()) { d ->
                                    if (d.title.isNotBlank()) offerTitle = d.title
                                    if (d.description.isNotBlank()) description = d.description
                                    d.price?.let { priceText = formatPrice(it) }
                                    d.originalPrice?.let { originalPriceText = formatPrice(it) }
                                    if (d.storeName.isNotBlank() && restaurantName.isBlank()) restaurantName = d.storeName
                                    if (d.dailyPercents.size >= 2) {
                                        percentMode = true
                                        tiered = true
                                        dayPercents.clear()
                                        d.dailyPercents.forEach { dayPercents.add(it.toString()) }
                                    } else if (d.price == null && d.discountPercent != null) {
                                        percentMode = true
                                        tiered = false
                                        percentText = d.discountPercent.toString()
                                    }
                                    if (d.itemsScope.isNotBlank()) itemsScope = d.itemsScope
                                    d.startDateMillis?.let { ms ->
                                        val today = OfferPricing.midnight(System.currentTimeMillis())
                                        startMillis = if (ms == today) null else ms.takeIf { it > today - 14 * 24 * 3_600_000L }
                                    }
                                    if (d.grandOpening) grandOpening = true
                                    if (d.tags.isNotEmpty() && tagsText.isBlank()) tagsText = d.tags.joinToString("، ")
                                    if (d.delivery) {
                                        delivery = true
                                        d.deliveryFee?.let { deliveryFeeText = moneyText(it) }
                                        d.deliveryMinOrder?.let { deliveryMinText = moneyText(it) }
                                        d.deliveryFreeOver?.let { deliveryFreeText = moneyText(it) }
                                    }
                                    if (!hasBranches && landmark.isBlank() && d.landmark.isNotBlank()) landmark = d.landmark
                                    if (!hasBranches) {
                                        if (phone.isBlank() && d.phone.isNotBlank()) phone = toLocalPhone(d.phone)
                                        if (district.isBlank() && d.district.isNotBlank()) district = d.district
                                    }
                                    d.days?.let { days ->
                                        val idx = EXPIRY_OPTIONS.indexOfFirst { it.days == days }
                                        expiryOptionIndex = idx
                                        if (idx < 0) customDaysText = days.coerceIn(1, 90).toString()
                                        keepCurrentExpiry = false
                                    }
                                }
                            },
                            enabled = !isAutofilling,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            if (isAutofilling) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("نقرأ الصورة…")
                            } else {
                                Text("✨ عبّي البيانات من الصورة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // نوع المنشور: عرض أو جديدنا
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(false to ("🔥 عرض" to "خصم أو سعر خاص"), true to ("✨ جديدنا" to "صنف أو منتج جديد")).forEach { (newKind, t) ->
                            val selected = isNewProduct == newKind
                            val accent = if (newKind) Color(0xFF7C3AED) else CalmSagePrimary
                            OutlinedCard(
                                onClick = {
                                    if (isNewProduct != newKind) {
                                        isNewProduct = newKind
                                        if (newKind) { percentMode = false; grandOpening = false }
                                        if (!isEditing) {
                                            keepCurrentExpiry = false
                                            val two = EXPIRY_OPTIONS.indexOfFirst { it.days == 14 }
                                            if (newKind) { if (two >= 0) expiryOptionIndex = two else { expiryOptionIndex = -1; customDaysText = "14" } }
                                            else expiryOptionIndex = 0
                                        }
                                    }
                                },
                                border = BorderStroke(2.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
                                colors = CardDefaults.outlinedCardColors(containerColor = if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(t.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(t.second, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = offerTitle,
                        onValueChange = { offerTitle = it },
                        label = { Text(if (isNewProduct) "اسم المنتج الجديد *" else "عنوان العرض *") },
                        placeholder = { Text(if (isNewProduct) "مثال: برجر الترافل الجديد" else "مثال: خصم 30% على كل المنتجات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_offer_title")
                    )

                    // نوع الخصم: سعر محدد أو نسبة (على جميع الأصناف)
                    if (!isNewProduct) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !percentMode, onClick = { percentMode = false }, label = { Text("💰 سعر محدد", fontSize = 12.sp) })
                        FilterChip(selected = percentMode, onClick = { percentMode = true }, label = { Text("٪ خصم بالنسبة", fontSize = 12.sp) })
                    }
                    if (!percentMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it },
                                label = { Text(if (isNewProduct) "السعر (اختياري)" else "السعر (ر.س) *") },
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
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = tiered, onCheckedChange = { tiered = it })
                            Text("الخصم يتغيّر حسب اليوم (مثل: أول يوم 50% وبعدها 30%)", fontSize = 13.sp)
                        }
                        if (!tiered) {
                            OutlinedTextField(
                                value = percentText,
                                onValueChange = { v -> percentText = v.filter { it.isDigit() }.take(2) },
                                label = { Text("نسبة الخصم % *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            dayPercents.forEachIndexed { i, v ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("اليوم ${OfferPricing.ordinal(i)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                                    OutlinedTextField(
                                        value = v,
                                        onValueChange = { nv -> dayPercents[i] = nv.filter { it.isDigit() }.take(2) },
                                        placeholder = { Text(if (i > 0) dayPercents[i - 1].ifBlank { "%" } else "%") },
                                        suffix = { Text("%") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (dayPercents.size > 2) {
                                        IconButton(onClick = { dayPercents.removeAt(i) }) {
                                            Icon(Icons.Default.Close, contentDescription = "حذف اليوم", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            if (dayPercents.size < 14) {
                                TextButton(onClick = { dayPercents.add(dayPercents.lastOrNull().orEmpty()) }) { Text("+ أضف يوم") }
                            }
                            Text(
                                "مدة العرض = ${dayPercents.size} أيام، ويتغيّر الخصم تلقائياً كل يوم.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = itemsScope,
                            onValueChange = { itemsScope = it.take(40) },
                            label = { Text("على وش الخصم؟") },
                            placeholder = { Text("على جميع الأصناف") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
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

                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it.take(200) },
                        label = { Text("الأصناف (للبحث)") },
                        placeholder = { Text("شاورما، برجر، بروست") },
                        supportingText = { Text("افصل بينها بفاصلة — اللي يبحث عنها يلقى عرضك") },
                        singleLine = true,
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

                    Text("تصنيف المتجر", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((categories + listOf(cuisineType)).filter { it.isNotBlank() }.distinct()) { cui ->
                            FilterChip(selected = cuisineType == cui, onClick = { cuisineType = cui }, label = { Text(cui, fontSize = 12.sp) })
                        }
                    }

                    OutlinedTextField(
                        value = restaurantName,
                        onValueChange = { restaurantName = it },
                        label = { Text("اسم المتجر *") },
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
                            label = { Text("رقم واتساب / جوال المتجر *") },
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

                    // ---------- البداية ----------
                    Text("متى يبدأ العرض؟ 🔜", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = startMillis == null, onClick = { startMillis = null }, label = { Text("الحين", fontSize = 12.sp) })
                        FilterChip(
                            selected = startMillis != null,
                            onClick = { showDatePicker = true },
                            label = {
                                Text(
                                    startMillis?.let { "📅 " + OfferPricing.dateText(it) } ?: "📅 بتاريخ معيّن",
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                    if (startMillis != null && startMillis!! > System.currentTimeMillis()) {
                        Text("قبل البداية يطلع على الخريطة بعلامة \"قريباً\" عشان يتشوّق له الناس.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!isNewProduct) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = grandOpening, onCheckedChange = { grandOpening = it })
                        Text("🎉 عرض افتتاح (محل جديد)", fontSize = 13.sp)
                    }

                    // ---------- المدة ----------
                    if (!(percentMode && tiered)) {
                    Text(if (isNewProduct) "كم يظهر كـ «جديد»؟ ⏰" else "مدة العرض ⏰", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                        item {
                            FilterChip(
                                selected = !keepCurrentExpiry && expiryOptionIndex < 0,
                                onClick = {
                                    keepCurrentExpiry = false
                                    expiryOptionIndex = -1
                                },
                                label = { Text("✎ عدد أيام", fontSize = 12.sp) }
                            )
                        }
                    }
                    if (!keepCurrentExpiry && expiryOptionIndex < 0) {
                        OutlinedTextField(
                            value = customDaysText,
                            onValueChange = { v -> customDaysText = v.filter { it.isDigit() }.take(2) },
                            label = { Text("كم يوم يستمر العرض؟") },
                            placeholder = { Text("مثال: 2") },
                            suffix = { Text("يوم") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
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

        if (showDatePicker) {
            StartDatePickerDialog(
                initialMillis = startMillis,
                onPick = { picked ->
                    showDatePicker = false
                    startMillis = picked?.takeIf { it > System.currentTimeMillis() }
                },
                onDismiss = { showDatePicker = false }
            )
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
private data class ExpiryOption(val label: String, val days: Int?)

// الخيارات تُبنى من ملف OfferDurations.kt (عدّل الأيام هناك)
private val EXPIRY_OPTIONS: List<ExpiryOption> =
    (if (com.example.ui.ALLOW_NO_EXPIRY) listOf(ExpiryOption("بدون انتهاء", null)) else emptyList()) +
        com.example.ui.OFFER_DURATION_DAYS.filter { it > 0 }.map { days ->
            ExpiryOption(com.example.ui.durationLabel(days), days)
        }

/** اختيار تاريخ بداية العرض (من اليوم وطالع). يرجّع منتصف الليل بالتوقيت المحلي. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StartDatePickerDialog(initialMillis: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val utc = java.util.TimeZone.getTimeZone("UTC")
    val todayUtc = remember {
        val local = java.util.Calendar.getInstance()
        java.util.Calendar.getInstance(utc).apply {
            clear()
            set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val initialUtc = initialMillis?.let { ms ->
        val local = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        java.util.Calendar.getInstance(utc).apply {
            clear()
            set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialUtc ?: (todayUtc + 24 * 3_600_000L),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= todayUtc && utcTimeMillis <= todayUtc + 60L * 24 * 3_600_000L
        }
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val sel = state.selectedDateMillis
                if (sel == null) { onDismiss(); return@TextButton }
                val u = java.util.Calendar.getInstance(utc).apply { timeInMillis = sel }
                val local = java.util.Calendar.getInstance().apply {
                    clear()
                    set(u.get(java.util.Calendar.YEAR), u.get(java.util.Calendar.MONTH), u.get(java.util.Calendar.DAY_OF_MONTH))
                }
                onPick(local.timeInMillis)
            }) { Text("تم") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        androidx.compose.material3.DatePicker(state = state, title = null, headline = null, showModeToggle = false)
    }
}

private fun formatPrice(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
