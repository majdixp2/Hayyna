package com.example.ui.components

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
    onSaveOffer: (RestaurantOffer) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            try {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        // Will update state in location handler
                    }
                }
            } catch (e: SecurityException) {}
        }
    }

    var restaurantName by remember { mutableStateOf("") }
    var offerTitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cuisineType by remember { mutableStateOf(LocationPresets.cuisines[1]) }
    var priceText by remember { mutableStateOf("") }
    var originalPriceText by remember { mutableStateOf("") }
    var discountBadge by remember { mutableStateOf(LocationPresets.offerBadges[0]) }

    var mediaType by remember { mutableStateOf(MediaType.IMAGE) }
    var mediaUri by remember { mutableStateOf("restaurant_burger_offer_1791823697805") }
    var isPromotionalVideo by remember { mutableStateOf(false) }

    // Location
    var selectedRegion by remember { mutableStateOf(LocationPresets.regions[0].name) }
    var districtText by remember { mutableStateOf(LocationPresets.regions[0].districts[0].name) }
    var latitude by remember { mutableDoubleStateOf(LocationPresets.regions[0].districts[0].latitude) }
    var longitude by remember { mutableDoubleStateOf(LocationPresets.regions[0].districts[0].longitude) }

    var phone by remember { mutableStateOf("+966551122334") }
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

                    Text("أو اختر نموذجاً جاهزاً لعروض المطاعم:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetMediaChip("برجر واغيو", "restaurant_burger_offer_1791823697805", MediaType.IMAGE) { uri, type ->
                            mediaUri = uri; mediaType = type; isPromotionalVideo = false
                        }
                        PresetMediaChip("بيتزا حطب", "restaurant_pizza_offer_1791823722379", MediaType.IMAGE) { uri, type ->
                            mediaUri = uri; mediaType = type; isPromotionalVideo = false
                        }
                        PresetMediaChip("مشاوي ملكية", "restaurant_grill_offer_1791823746852", MediaType.IMAGE) { uri, type ->
                            mediaUri = uri; mediaType = type; isPromotionalVideo = false
                        }
                        PresetMediaChip("فيديو إعلاني 🎥", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", MediaType.VIDEO) { uri, type ->
                            mediaUri = uri; mediaType = type; isPromotionalVideo = true; discountBadge = "فيديو إعلاني حصري 🎬"
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
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

            Spacer(modifier = Modifier.height(16.dp))

            // Location Section (الموقع الدقيق للمطعم عبر الخريطة التفاعلية والدبوس المتحرك)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("موقع المحل الدقيق على الخريطة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CalmSagePrimary)
                        }

                        // Button: استخدم موقعي الحالي (Use my current location)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CalmSagePrimary,
                            modifier = Modifier
                                .clickable {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                                            if (loc != null) {
                                                latitude = loc.latitude
                                                longitude = loc.longitude
                                            }
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                                .testTag("use_my_location_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استخدم موقعي الحالي 📍", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mini Interactive Google Map for Pin Placement
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CalmSagePrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        val miniMapCameraState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 14.5f)
                        }

                        LaunchedEffect(latitude, longitude) {
                            miniMapCameraState.position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 15f)
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = miniMapCameraState,
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
                            // عند انتهاء سحب الدبوس نحفظ موقعه الجديد (بدون هذا يضيع السحب ويُحفظ الموقع القديم)
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
                                title = restaurantName.ifBlank { "موقع المحل" },
                                snippet = "انقر على الخريطة أو اسحب الدبوس لتعديل موقع محلك"
                            )
                        }

                        // Top Helper Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "حرك أو انقر على الخريطة لتحديد موقع محلك بدقة 📍",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Region selection
                    Text("1. المنطقة:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        items(LocationPresets.regions) { reg ->
                            FilterChip(
                                selected = selectedRegion == reg.name,
                                onClick = {
                                    selectedRegion = reg.name
                                    val firstDist = reg.districts.firstOrNull()
                                    if (firstDist != null) {
                                        districtText = firstDist.name
                                        latitude = firstDist.latitude
                                        longitude = firstDist.longitude
                                    }
                                },
                                label = { Text(reg.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // District input
                    OutlinedTextField(
                        value = districtText,
                        onValueChange = { districtText = it },
                        label = { Text("2. اسم الحي (الحي السكني أو التجاري) *") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_restaurant_district")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Quick district chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        items(currentRegionDistricts) { dist ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (districtText == dist.name) CalmSagePrimary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable {
                                    districtText = dist.name
                                    latitude = dist.latitude
                                    longitude = dist.longitude
                                }
                            ) {
                                Text(
                                    text = dist.name,
                                    fontSize = 11.sp,
                                    color = if (districtText == dist.name) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Coordinates display
                    Text(
                        text = "إحداثيات GPS المحددة: ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phone
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم هاتف / واتساب المطعم للتواصل") },
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
                    } else {
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
                            phone = phone.trim()
                        )
                        onSaveOffer(offer)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_restaurant_offer_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("نشر العرض على الخريطة وقائمة المطاعم ✨", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

