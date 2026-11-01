package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.example.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ReverseGeocoder
import com.example.data.model.OfferTime
import com.example.ui.theme.CalmSagePrimary
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay

/**
 * بطاقة اختيار الموقع: زر "موقعي الحالي" + خريطة بدبوس يتحرك + الحي والمنطقة من Google تلقائياً.
 * [autoLocate] = يحدد موقع الجهاز أول ما تفتح (للفرع/العرض الجديد بدون موقع محفوظ).
 */
@Composable
fun LocationPickerCard(
    latitude: Double,
    longitude: Double,
    onLocationChange: (lat: Double, lng: Double) -> Unit,
    district: String,
    onDistrictChange: (String) -> Unit,
    region: String,
    onRegionChange: (String) -> Unit,
    autoLocate: Boolean,
    title: String = "موقع المحل",
    pinTitle: String = "موقع المحل",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnLocationChange by rememberUpdatedState(onLocationChange)
    var isLocating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (autoLocate) "" else "✓ الموقع المحفوظ") }
    // أول مرة ما نغيّر الحي المحفوظ؛ نجيبه من Google فقط بعد ما يتحرك الدبوس
    var skipNextGeocode by remember { mutableStateOf(!autoLocate && district.isNotBlank()) }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun fetchCurrentLocation() {
        if (!hasPermission()) return
        isLocating = true
        status = "جاري تحديد موقعك..."
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    isLocating = false
                    if (loc != null) currentOnLocationChange(loc.latitude, loc.longitude)
                    else status = "ما قدرنا نحدد موقعك. تأكد إن الـ GPS شغّال، أو حرّك الدبوس يدوياً."
                }
                .addOnFailureListener {
                    isLocating = false
                    status = "ما قدرنا نحدد موقعك. حرّك الدبوس يدوياً."
                }
        } catch (e: SecurityException) {
            isLocating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) fetchCurrentLocation()
        else status = "بدون صلاحية الموقع: حرّك الدبوس على مكان محلك."
    }

    fun locate() {
        if (hasPermission()) fetchCurrentLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) { if (autoLocate) locate() }

    // كل ما تغيّر مكان الدبوس: الحي والمنطقة من Google
    LaunchedEffect(latitude, longitude) {
        if (skipNextGeocode) {
            skipNextGeocode = false
            return@LaunchedEffect
        }
        delay(500)
        val place = ReverseGeocoder.lookup(context, latitude, longitude)
        if (place != null) {
            if (place.region.isNotBlank()) onRegionChange(place.region)
            if (place.district.isNotBlank()) onDistrictChange(place.district)
            status = if (place.district.isNotBlank()) "✓ تم تحديد الحي تلقائياً" else "اكتب اسم الحي"
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CalmSagePrimary)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { locate() },
                enabled = !isLocating,
                colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("use_my_location_btn")
            ) {
                if (isLocating) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("استخدم موقعي الحالي", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CalmSagePrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                val cameraState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 16f)
                }
                LaunchedEffect(latitude, longitude) {
                    cameraState.position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 16f)
                }
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    properties = MapProperties(mapStyleOptions = CleanMapStyle.options),
                    onMapClick = { currentOnLocationChange(it.latitude, it.longitude) },
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false)
                ) {
                    val pinState = rememberMarkerState(position = LatLng(latitude, longitude))
                    LaunchedEffect(latitude, longitude) { pinState.position = LatLng(latitude, longitude) }
                    LaunchedEffect(pinState) {
                        snapshotFlow { pinState.isDragging to pinState.position }.collect { (dragging, pos) ->
                            if (!dragging && (pos.latitude != latitude || pos.longitude != longitude)) {
                                currentOnLocationChange(pos.latitude, pos.longitude)
                            }
                        }
                    }
                    Marker(state = pinState, draggable = true, title = pinTitle)
                }
            }

            Text(
                text = status.ifBlank { "اضغط على الخريطة أو اسحب الدبوس لتعديل المكان" },
                fontSize = 12.sp,
                color = if (status.startsWith("✓")) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = district,
                onValueChange = onDistrictChange,
                label = { Text("الحي *") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("input_restaurant_district")
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = region,
                onValueChange = onRegionChange,
                label = { Text("المنطقة / المدينة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** قائمة منسدلة لاختيار الوقت كل نصف ساعة. */
@Composable
fun TimeDropdown(
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
        HMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
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

/** يحوّل 05xxxxxxxx أو 9665xxxxxxxx أو +9665xxxxxxxx إلى +9665xxxxxxxx. يرجع null إذا الرقم غير صحيح. */
fun normalizeSaudiPhone(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
        .map { c ->
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

/** +9665xxxxxxxx -> 05xxxxxxxx لعرضه للتاجر بالشكل المألوف. */
fun toLocalPhone(phone: String): String =
    if (phone.startsWith("+9665") && phone.length == 13) "0" + phone.substring(4) else phone
