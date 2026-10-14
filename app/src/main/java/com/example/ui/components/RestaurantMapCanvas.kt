package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

/**
 * Real Google Maps view using maps-compose with custom circular badges, discount tags,
 * real GPS location tracking, and camera animations.
 */
@Composable
fun FullScreenRestaurantMap(
    offers: List<RestaurantOffer>,
    selectedOffer: RestaurantOffer?,
    userLocation: Location? = null,
    onSelectOffer: (RestaurantOffer) -> Unit,
    onBackgroundClick: () -> Unit = {},
    onLocationPermissionGranted: () -> Unit = {},
    currentDistrictName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // بعد موافقة المستخدم نبدأ تتبع الموقع فوراً (بدونها لا يعمل حساب المسافة إلا بعد إعادة تشغيل التطبيق)
        if (hasLocationPermission) onLocationPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            onLocationPermissionGranted()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Default initial location: Riyadh center or user GPS location
    val initialLatLng = remember(userLocation) {
        if (userLocation != null) {
            LatLng(userLocation.latitude, userLocation.longitude)
        } else if (offers.isNotEmpty()) {
            LatLng(offers.first().latitude, offers.first().longitude)
        } else {
            LatLng(24.7136, 46.6753)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 13.5f)
    }

    var hasAnimatedToInitialLocation by remember { mutableStateOf(false) }

    // Animate camera to user location on launch as requested
    LaunchedEffect(userLocation) {
        if (userLocation != null && !hasAnimatedToInitialLocation) {
            hasAnimatedToInitialLocation = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(userLocation.latitude, userLocation.longitude),
                    14f
                )
            )
        }
    }

    // Animate camera when user focuses a specific offer
    LaunchedEffect(selectedOffer?.id) {
        if (selectedOffer != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(selectedOffer.latitude, selectedOffer.longitude),
                    15.5f
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("full_google_map_view"),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = true
            ),
            onMapClick = {
                onBackgroundClick()
            }
        ) {
            offers.forEach { offer ->
                val isSelected = selectedOffer?.id == offer.id
                val markerState = rememberMarkerState(
                    key = "marker_${offer.id}",
                    position = LatLng(offer.latitude, offer.longitude)
                )

                MarkerComposable(
                    state = markerState,
                    keys = arrayOf(offer.id, isSelected, offer.discountBadge),
                    onClick = {
                        onSelectOffer(offer)
                        true
                    }
                ) {
                    OfferCircleMarker(
                        offer = offer,
                        isSelected = isSelected
                    )
                }
            }
        }

        // Floating Map Controls (+ zoom, - zoom, My Location)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                        }
                    }
                    .testTag("map_zoom_in_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "تكبير الخريطة",
                    tint = Color(0xFF333333),
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                        }
                    }
                    .testTag("map_zoom_out_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "تصغير الخريطة",
                    tint = Color(0xFF333333),
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // My Location Button: Centers directly on real GPS position
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 5.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        if (hasLocationPermission && userLocation != null) {
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(userLocation.latitude, userLocation.longitude),
                                        15f
                                    )
                                )
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                    .testTag("map_recenter_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "موقعي الحالي",
                    tint = CalmSagePrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // District Indicator Chip on Map
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 210.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = CalmSagePrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "أنت في: $currentDistrictName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF262A33)
                )
            }
        }
    }
}

/**
 * Custom Circular Marker for each offer:
 * Features the offer image in a small circle with the discount badge below it.
 */
@Composable
fun OfferCircleMarker(
    offer: RestaurantOffer,
    isSelected: Boolean
) {
    val context = LocalContext.current
    val pulseTransition = rememberInfiniteTransition(label = "markerPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        // Label with restaurant name when selected
        if (isSelected) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CalmSagePrimary,
                shadowElevation = 6.dp,
                border = BorderStroke(1.5.dp, Color(0xFFFFD166))
            ) {
                Text(
                    text = offer.restaurantName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (isSelected) 56.dp else 48.dp)
        ) {
            // Pulsing ring if selected
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFD166).copy(alpha = 0.35f),
                    modifier = Modifier.size((56 * pulseScale).dp)
                ) {}
            }

            // Circular Picture Badge
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = if (isSelected) 8.dp else 4.dp,
                border = BorderStroke(
                    width = if (isSelected) 3.dp else 2.dp,
                    color = if (isSelected) Color(0xFFFFD166) else CalmSagePrimary
                ),
                modifier = Modifier.size(if (isSelected) 50.dp else 42.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val drawableResId = remember(offer.mediaUri) {
                        when (offer.mediaUri) {
                            "cafe_arabica_offer_1791863237123" -> R.drawable.cafe_arabica_offer_1791863237123
                            "restaurant_burger_offer_1791823697805" -> R.drawable.restaurant_burger_offer_1791823697805
                            "restaurant_pizza_offer_1791823722379" -> R.drawable.restaurant_pizza_offer_1791823722379
                            "restaurant_grill_offer_1791823746852" -> R.drawable.restaurant_grill_offer_1791823746852
                            else -> {
                                val id = context.resources.getIdentifier(offer.mediaUri, "drawable", context.packageName)
                                if (id != 0) id else null
                            }
                        }
                    }

                    if (drawableResId != null) {
                        Image(
                            painter = painterResource(id = drawableResId),
                            contentDescription = offer.restaurantName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(offer.mediaUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = offer.restaurantName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (offer.mediaType == MediaType.VIDEO) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDC2626).copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }
                }
            }

            // Discount Badge at bottom: e.g. "خصم 34%"
            if (offer.discountBadge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE05A47),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color.White),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 4.dp)
                ) {
                    Text(
                        text = offer.discountBadge.take(10),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
