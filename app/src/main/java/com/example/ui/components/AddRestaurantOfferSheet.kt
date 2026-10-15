package com.example.ui.components

import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.location.Priority
import com.example.data.ReverseGeocoder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.background
import com.example.data.model.OfferTime
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Videocam
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPresets
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRestaurantOfferSheet(
    onDismiss: () -> Unit,
    onSaveOffer: (RestaurantOffer) -> Unit,
    isSaving: Boolean = false,
    defaultRestaurantName: String = ""
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current


    var restaurantName by remember { mutableStateOf(defaultRestaurantName) }
    var offerTitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cuisineType by remember { mutableStateOf(LocationPresets.cuisines[1]) }
    var priceText by remember { mutableStateOf("") }
    var originalPriceText by remember { mutableStateOf("") }
    var discountBadge by remember { mutableStateOf(LocationPresets.offerBadges[0]) }

    var mediaType by remember { mutableStateOf(MediaType.IMAGE) }
    var mediaUri by remember { mutableStateOf("") }
    var isPromotionalVideo by remember { mutableStateOf(false) }

    // Location
    var selectedRegion by remember { mutableStateOf("") }
    var districtText by remember { mutableStateOf("") }
    var isLocating by remember { mutableStateOf(false) }
    var locationStatus by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(LocationPresets.regions[0].districts[0].latitude) }
    var longitude by remember { mutableDoubleStateOf(LocationPresets.regions[0].districts[0].longitude) }

    var phone by remember { mutableStateOf("") }

    // مدة العرض (index في EXPIRY_OPTIONS) وأوقات الدوام بالدقائق من منتصف الليل
    var expiryOptionIndex by remember { mutableStateOf(0) }
    var openMinute by remember { mutableStateOf(12 * 60) }
    var closeMinute by remember { mutableStateOf(0) }
    var isOpen24h by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // يجيب موقع الجهاز الحالي ويحرّك الدبوس عليه
    fun fetchCurrentLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        isLocating = true
        locationStatus = "جاري تحديد موقعك..."
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    isLocating = false
                    if (loc != null) {
                        latitude = loc.latitude
                        longitude = loc.longitude
                    } else {
                        locationStatus = "ما قدرنا نحدد موقعك. تأكد إن الـ GPS شغّال، أو حرّك الدبوس يدوياً."
                    }
                }
                .addOnFailureListener {
                    isLocating = false
                    locationStatus = "ما قدرنا نحدد موقعك. حرّك الدبوس يدوياً."
                }
        } catch (e: SecurityException) {
            isLocating = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) fetchCurrentLocation()
        else locationStatus = "بدون صلاحية الموقع: حرّك الدبوس على مكان محلك."
    }

    // عند فتح الشاشة: نحدد موقع التاجر تلقائياً
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) fetchCurrentLocation()
        else locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // كل ما تغيّر مكان الدبوس: نجيب المنطقة والحي من Google تلقائياً
    LaunchedEffect(latitude, longitude) {
        kotlinx.coroutines.delay(500)
        val place = ReverseGeocoder.lookup(context, latitude, longitude)
        if (place != null) {
            if (place.region.isNotBlank()) selectedRegion = place.region
            if (place.district.isNotBlank()) districtText = place.district
            locationStatus = if (place.district.isNotBlank()) "✓ تم تحديد الحي تلقائياً" else "اكتب اسم الحي"
        }
    }

    var errorMessage by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaUri = uri.toString()
            mediaType = MediaType.IMAGE
            isPromotionalVideo = false
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaUri = uri.toString()
            mediaType = MediaType.VIDEO
            isPromotionalVideo = true
            discountBadge = "فيديو إعلاني حصري 🎬"
        }
    }

    val currentRegionDistricts = remember(selectedRegion) {
        LocationPresets.regions.find { it.name == selectedRegion }?.districts ?: emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("add_restaurant_offer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "إضافة عرض جديد للمطعم 🍽️",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            Text(
                text = "سجل عرض مطعمك، أرفق صورة أو فيديو إعلاني، وحدد الحي وموقع الخريطة.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Restaurant Name
            OutlinedTextField(
                value = restaurantName,
                onValueChange = { restaurantName = it },
                label = { Text("اسم المطعم *") },
                placeholder = { Text("مثال: برجر هاوس، شاورما السلطان...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("input_restaurant_name")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Offer Title
            OutlinedTextField(
                value = offerTitle,
                onValueChange = { offerTitle = it },
                label = { Text("عنوان العرض الترويجي *") },
                placeholder = { Text("مثال: وجبة برجر + بطاطس ومشروب مجاناً") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_offer_title")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Cuisine Type chips
            Text("نوع المأكولات:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                items(LocationPresets.cuisines.filter { it != "الكل" }) { cui ->
                    FilterChip(
                        selected = cuisineType == cui,
                        onClick = { cuisineType = cui },
                        label = { Text(cui, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price & Original Price
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("سعر العرض (ر.س) *") },
                    placeholder = { Text("45") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("input_offer_price")
                )

                OutlinedTextField(
                    value = originalPriceText,
                    onValueChange = { originalPriceText = it },
                    label = { Text("قبل الخصم (اختياري)") },
                    placeholder = { Text("60") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("تفاصيل العرض ومحتويات الوجبة *") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Media attachment box (يرفق صورة أو فيديو إعلاني)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("إرفاق الوسائط (صورة أو فيديو إعلاني):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mediaType == MediaType.IMAGE) CalmSagePrimary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إرفاق صورة 📷", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mediaType == MediaType.VIDEO) Color(0xFFE05A47) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إرفاق فيديو 🎬", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (mediaUri.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "أرفق صورة أو فيديو للعرض 📷",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            ProductMediaViewer(
                                mediaUri = mediaUri,
                                mediaType = mediaType,
                                isPromotionalVideo = isPromotionalVideo,
                                promotionalBadge = discountBadge,
                                autoPlayVideo = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // موقع المحل: يتحدد تلقائياً من الـ GPS، والتاجر يقدر يحرّك الدبوس
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("موقع المحل", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CalmSagePrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (granted) fetchCurrentLocation()
                            else locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        },
                        enabled = !isLocating,
                        colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("use_my_location_btn")
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استخدم موقعي الحالي", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CalmSagePrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        val miniMapCameraState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 16f)
                        }

                        LaunchedEffect(latitude, longitude) {
                            miniMapCameraState.position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 16f)
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = miniMapCameraState,
                            properties = com.google.maps.android.compose.MapProperties(
                                mapStyleOptions = CleanMapStyle.options
                            ),
                            onMapClick = { clickedLatLng ->
                                latitude = clickedLatLng.latitude
                                longitude = clickedLatLng.longitude
                            },
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                myLocationButtonEnabled = false,
                                mapToolbarEnabled = false
                            )
                        ) {
                            val pinState = rememberMarkerState(position = LatLng(latitude, longitude))
                            LaunchedEffect(latitude, longitude) {
                                pinState.position = LatLng(latitude, longitude)
                            }
                            // عند انتهاء سحب الدبوس نحفظ موقعه الجديد
                            LaunchedEffect(pinState) {
                                androidx.compose.runtime.snapshotFlow { pinState.isDragging to pinState.position }
                                    .collect { (dragging, pos) ->
                                        if (!dragging && (pos.latitude != latitude || pos.longitude != longitude)) {
                                            latitude = pos.latitude
                                            longitude = pos.longitude
                                        }
                                    }
                            }
                            Marker(
                                state = pinState,
                                draggable = true,
                                title = restaurantName.ifBlank { "موقع المحل" }
                            )
                        }
                    }

                    Text(
                        text = locationStatus.ifBlank { "اضغط على الخريطة أو اسحب الدبوس لتعديل مكان محلك" },
                        fontSize = 12.sp,
                        color = if (locationStatus.startsWith("✓")) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = districtText,
                        onValueChange = { districtText = it },
                        label = { Text("الحي *") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_restaurant_district")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = selectedRegion,
                        onValueChange = { selectedRegion = it },
                        label = { Text("المنطقة / المدينة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // مدة العرض
            Text("مدة العرض ⏰", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                items(EXPIRY_OPTIONS.size) { index ->
                    FilterChip(
                        selected = expiryOptionIndex == index,
                        onClick = { expiryOptionIndex = index },
                        label = { Text(EXPIRY_OPTIONS[index].label, fontSize = 12.sp) }
                    )
                }
            }
            Text(
                "بعد انتهاء المدة يختفي العرض تلقائياً من الخريطة.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // أوقات الدوام (لفلتر "مفتوح الآن")
            Text("أوقات الدوام 🕐", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Checkbox(checked = isOpen24h, onCheckedChange = { isOpen24h = it })
                Text("مفتوح 24 ساعة", fontSize = 13.sp)
            }
            if (!isOpen24h) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeDropdown(
                        label = "يفتح",
                        minute = openMinute,
                        onMinuteChange = { openMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                    TimeDropdown(
                        label = "يقفل",
                        minute = closeMinute,
                        onMinuteChange = { closeMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phone
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم واتساب / جوال المطعم *") },
                placeholder = { Text("05xxxxxxxx") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (restaurantName.isBlank()) {
                        errorMessage = "يرجى كتابة اسم المطعم."
                    } else if (offerTitle.isBlank()) {
                        errorMessage = "يرجى كتابة عنوان العرض."
                    } else if (price == null || price <= 0) {
                        errorMessage = "يرجى إدخال سعر صحيح للعرض."
                    } else if (districtText.isBlank()) {
                        errorMessage = "يرجى كتابة اسم الحي."
                    } else if (mediaUri.isBlank()) {
                        errorMessage = "يرجى إرفاق صورة أو فيديو للعرض."
                    } else if (normalizeSaudiPhone(phone) == null) {
                        errorMessage = "يرجى كتابة رقم جوال صحيح مثل 05xxxxxxxx."
                    } else {
                        val finalOpen = if (isOpen24h) 0 else openMinute
                        val finalClose = if (isOpen24h) 0 else closeMinute
                        val hoursOffer = RestaurantOffer(openMinute = finalOpen, closeMinute = finalClose)
                        val offer = RestaurantOffer(
                            restaurantName = restaurantName.trim(),
                            offerTitle = offerTitle.trim(),
                            description = description.trim().ifBlank { "عرض استثنائي ولذيذ متاح لجميع زبائن المطعم مع إمكانية التوجه مباشرة للفرع." },
                            cuisineType = cuisineType,
                            price = price,
                            originalPrice = originalPriceText.toDoubleOrNull(),
                            discountBadge = discountBadge,
                            mediaType = mediaType,
                            mediaUri = mediaUri,
                            isPromotionalVideo = isPromotionalVideo,
                            district = districtText.trim(),
                            region = selectedRegion,
                            latitude = latitude,
                            longitude = longitude,
                            phone = normalizeSaudiPhone(phone) ?: phone.trim(),
                            openMinute = finalOpen,
                            closeMinute = finalClose,
                            workingHours = OfferTime.hoursText(hoursOffer),
                            expiresAt = EXPIRY_OPTIONS[expiryOptionIndex].expiresAt()
                        )
                        onSaveOffer(offer)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_restaurant_offer_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("جاري رفع العرض...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Text("انشر العرض ✨", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun PresetMediaChip(
    title: String,
    uri: String,
    type: MediaType,
    onSelect: (String, MediaType) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable { onSelect(uri, type) }
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}



/** خيارات مدة العرض. */
private data class ExpiryOption(val label: String, val expiresAt: () -> com.google.firebase.Timestamp?)

private val EXPIRY_OPTIONS: List<ExpiryOption> = listOf(
    ExpiryOption("بدون انتهاء") { null },
    ExpiryOption("3 ساعات") { timestampAfterHours(3) },
    ExpiryOption("6 ساعات") { timestampAfterHours(6) },
    ExpiryOption("لين آخر اليوم") {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 0)
        }
        com.google.firebase.Timestamp(cal.time)
    },
    ExpiryOption("3 أيام") { timestampAfterHours(72) },
    ExpiryOption("أسبوع") { timestampAfterHours(24 * 7) }
)

private fun timestampAfterHours(hours: Int): com.google.firebase.Timestamp =
    com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + hours * 3_600_000L))

/** يحوّل 05xxxxxxxx أو 9665xxxxxxxx أو +9665xxxxxxxx إلى +9665xxxxxxxx. يرجع null إذا الرقم غير صحيح. */
internal fun normalizeSaudiPhone(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
        .map { c -> // تحويل الأرقام العربية الهندية إن وجدت
            val v = Character.getNumericValue(c)
            if (v in 0..9) ('0' + v) else c
        }.joinToString("")
    val local = when {
        digits.startsWith("9665") && digits.length == 12 -> digits.substring(3)
        digits.startsWith("05") && digits.length == 10 -> digits.substring(1)
        digits.startsWith("5") && digits.length == 9 -> digits
        else -> return if (digits.length in 8..15) "+$digits" else null
    }
    return "+966$local"
}

/** قائمة منسدلة لاختيار الوقت كل نصف ساعة. */
@Composable
private fun TimeDropdown(
    label: String,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: ${OfferTime.formatMinute(minute)}", fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (0 until 48).forEach { slot ->
                val m = slot * 30
                DropdownMenuItem(
                    text = { Text(OfferTime.formatMinute(m)) },
                    onClick = {
                        onMinuteChange(m)
                        expanded = false
                    }
                )
            }
        }
    }
}
