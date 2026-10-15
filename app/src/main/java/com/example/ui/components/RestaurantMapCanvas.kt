package com.example.ui.components

import com.example.data.CloudinaryUrls
import com.example.data.ReverseGeocoder
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.SuccessResult
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
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
@OptIn(MapsComposeExperimentalApi::class)
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

    // صور العروض تُحمّل مسبقاً كـ Bitmap، لأن أيقونات الخريطة تُرسم مرة واحدة ولا تنتظر تحميل الصور من الإنترنت
    val markerBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    // صورة الأيقونة: الصورة نفسها (مصغّرة)، أو غلاف الفيديو من Cloudinary
    val markerSources: Map<String, String> = offers
        .filter { it.mediaUri.startsWith("http") }
        .mapNotNull { offer ->
            val src = if (offer.mediaType == MediaType.VIDEO) CloudinaryUrls.videoThumbnail(offer.mediaUri, 200)
            else CloudinaryUrls.image(offer.mediaUri, 200)
            src?.let { offer.mediaUri to it }
        }
        .toMap()
    LaunchedEffect(markerSources.keys) {
        markerSources.filterKeys { it !in markerBitmaps }.forEach { (key, url) ->
            loadMarkerBitmap(context, url)?.let { markerBitmaps[key] = it }
        }
    }

    val clusterItems = offers.map { offer ->
        OfferClusterItem(
            offer = offer,
            isSelected = selectedOffer?.id == offer.id,
            bitmap = markerBitmaps[offer.mediaUri]
        )
    }

    // اسم الحي اللي فيه المستخدم الحين (يتحدّث إذا تحرّك أكثر من ~300م)
    var userPlaceName by remember { mutableStateOf("") }
    val roundedLat = userLocation?.let { Math.round(it.latitude * 300) }
    val roundedLng = userLocation?.let { Math.round(it.longitude * 300) }
    LaunchedEffect(roundedLat, roundedLng) {
        val loc = userLocation ?: return@LaunchedEffect
        val place = ReverseGeocoder.lookup(context, loc.latitude, loc.longitude)
        userPlaceName = place?.district?.ifBlank { place.region }.orEmpty()
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("full_google_map_view"),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = CleanMapStyle.options,
                isBuildingEnabled = false
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
            // العروض القريبة من بعض تتجمع في دائرة فيها العدد، وتتفرّق عند التكبير
            Clustering(
                items = clusterItems,
                onClusterClick = { cluster ->
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                cluster.position,
                                cameraPositionState.position.zoom + 2f
                            )
                        )
                    }
                    true
                },
                onClusterItemClick = { item ->
                    onSelectOffer(item.offer)
                    true
                },
                clusterContent = { cluster ->
                    OfferClusterBubble(count = cluster.size)
                },
                clusterItemContent = { item ->
                    OfferCircleMarker(
                        offer = item.offer,
                        isSelected = item.isSelected,
                        bitmap = item.bitmap
                    )
                }
            )
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

        // اسم حي المستخدم الحقيقي (من Google) — يظهر فقط إذا عرفناه
        if (userPlaceName.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 80.dp)
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
                        text = "أنت في: $userPlaceName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF262A33),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** عنصر في التجميع: العرض + هل هو المختار + صورته الجاهزة. */
data class OfferClusterItem(
    val offer: RestaurantOffer,
    val isSelected: Boolean,
    val bitmap: ImageBitmap?
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(offer.latitude, offer.longitude)
    override fun getTitle(): String? = offer.restaurantName
    override fun getSnippet(): String? = offer.offerTitle
    override fun getZIndex(): Float? = if (isSelected) 2f else if (offer.isFeatured) 1f else 0f
}

private suspend fun loadMarkerBitmap(context: Context, url: String): ImageBitmap? = try {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(160)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request)
    (result as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
} catch (e: Exception) {
    null
}

/** نسبة الخصم من السعرين، مثل "-34%". */
private fun discountPercentLabel(offer: RestaurantOffer): String? {
    val original = offer.originalPrice ?: return null
    if (original <= 0 || offer.price >= original) return null
    val pct = ((original - offer.price) / original * 100).toInt()
    return if (pct >= 1) "-$pct%" else null
}

/** دائرة التجميع: عدد العروض في المنطقة. */
@Composable
fun OfferClusterBubble(count: Int) {
    Surface(
        shape = CircleShape,
        color = CalmSagePrimary,
        border = BorderStroke(3.dp, Color.White),
        shadowElevation = 6.dp,
        modifier = Modifier.size(if (count >= 10) 52.dp else 44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (count > 99) "99+" else "$count",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * أيقونة العرض على الخريطة: صورة العرض داخل دائرة، وتحتها نسبة الخصم.
 * ملاحظة: تُرسم كصورة ثابتة، لذلك الصورة تُمرّر جاهزة (bitmap) بدل تحميلها هنا.
 */
@Composable
fun OfferCircleMarker(
    offer: RestaurantOffer,
    isSelected: Boolean,
    bitmap: ImageBitmap? = null
) {
    val context = LocalContext.current
    val ringColor = when {
        isSelected -> Color(0xFFFFD166)
        offer.isFeatured -> Color(0xFFF59E0B)
        else -> CalmSagePrimary
    }
    val circleSize = if (isSelected) 54.dp else 46.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
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
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(circleSize + 6.dp)) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = if (isSelected) 8.dp else 4.dp,
                border = BorderStroke(if (isSelected) 3.dp else 2.5.dp, ringColor),
                modifier = Modifier.size(circleSize)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val drawableResId = remember(offer.mediaUri) {
                        if (offer.mediaUri.isBlank() || offer.mediaUri.contains("/")) null
                        else context.resources.getIdentifier(offer.mediaUri, "drawable", context.packageName)
                            .takeIf { it != 0 }
                    }
                    when {
                        bitmap != null -> Image(
                            bitmap = bitmap,
                            contentDescription = offer.restaurantName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        drawableResId != null -> Image(
                            painter = painterResource(id = drawableResId),
                            contentDescription = offer.restaurantName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> Text(text = "🍽️", fontSize = 20.sp)
                    }

                    if (offer.mediaType == MediaType.VIDEO) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFDC2626).copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
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

        }

        // نسبة الخصم تحت الدائرة (ما تغطي صورة العرض)
        val badge = discountPercentLabel(offer)
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE05A47),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, Color.White),
                modifier = Modifier.offset(y = (-4).dp)
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}
