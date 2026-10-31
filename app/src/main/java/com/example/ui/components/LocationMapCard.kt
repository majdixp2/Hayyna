package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.ui.i18n.Surface
import com.example.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MapPinRed

@Composable
fun LocationMapCard(
    district: String,
    region: String,
    latitude: Double,
    longitude: Double,
    storeOrItemName: String,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true
) {
    val context = LocalContext.current

    // Pulsing animation for the GPS map marker
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("location_map_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: District & Region Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "الموقع",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(6.dp)
                                .fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (district.trim().startsWith("حي")) district.trim() else "حي $district",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = region,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { shareLocation(context, storeOrItemName, district, region, latitude, longitude) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "مشاركة الموقع",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Map Simulator Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE2E8F0))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable {
                        openInGoogleMaps(context, latitude, longitude, "$storeOrItemName - $district")
                    }
                    .testTag("map_canvas_preview"),
                contentAlignment = Alignment.Center
            ) {
                // Drawing realistic street map patterns and crossroads
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Background land
                    drawRect(Color(0xFFE2E8F0))

                    // Secondary roads
                    val roadColor = Color.White
                    val gridStep = 45f
                    for (x in 0..(w.toInt()) step gridStep.toInt()) {
                        drawLine(
                            color = roadColor.copy(alpha = 0.6f),
                            start = Offset(x.toFloat(), 0f),
                            end = Offset(x.toFloat(), h),
                            strokeWidth = 3f
                        )
                    }
                    for (y in 0..(h.toInt()) step gridStep.toInt()) {
                        drawLine(
                            color = roadColor.copy(alpha = 0.6f),
                            start = Offset(0f, y.toFloat()),
                            end = Offset(w, y.toFloat()),
                            strokeWidth = 3f
                        )
                    }

                    // Main highway / artery diagonal
                    drawLine(
                        color = Color(0xFFFED7AA),
                        start = Offset(0f, h * 0.7f),
                        end = Offset(w, h * 0.2f),
                        strokeWidth = 14f
                    )
                    drawLine(
                        color = Color(0xFFFDBA74),
                        start = Offset(0f, h * 0.7f),
                        end = Offset(w, h * 0.2f),
                        strokeWidth = 4f
                    )

                    // Secondary artery
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.35f, 0f),
                        end = Offset(w * 0.55f, h),
                        strokeWidth = 12f
                    )

                    // Target Pin location at center
                    val centerPin = Offset(w * 0.5f, h * 0.48f)

                    // Pulsing GPS circle
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = pulseAlpha),
                        radius = 35f * pulseScale,
                        center = centerPin
                    )

                    // Inner circle
                    drawCircle(
                        color = Color(0xFF3B82F6).copy(alpha = 0.25f),
                        radius = 24f,
                        center = centerPin
                    )
                }

                // Interactive Pin Overlay
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        shadowElevation = 4.dp
                    ) {
                        androidx.compose.material3.Text(
                            text = district,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "دبوس الموقع",
                        tint = MapPinRed,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Click hint badge at top corner
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "انقر لفتح الخريطة",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isInteractive) {
                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons to open in Google Maps and Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openInGoogleMaps(context, latitude, longitude, "$storeOrItemName - $district")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_in_google_maps_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "فتح الخريطة",
                            fontSize = 13.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            openDirectionsInGoogleMaps(context, latitude, longitude)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_directions_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الاتجاهات",
                            fontSize = 13.sp,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

fun openInGoogleMaps(context: Context, lat: Double, lng: Double, label: String) {
    try {
        val encodedLabel = Uri.encode(label)
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to web browser Google Maps
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (e: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

fun openDirectionsInGoogleMaps(context: Context, lat: Double, lng: Double) {
    try {
        val navUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, navUri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (e: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

fun shareLocation(context: Context, name: String, district: String, region: String, lat: Double, lng: Double) {
    val mapLink = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
    val shareText = """
        📍 موقع $name
        الحي: $district
        المنطقة: $region
        رابط خرائط Google ماب:
        $mapLink
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "موقع $name - $district")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة موقع السلعة / المحل"))
}

fun openWhatsApp(context: Context, phone: String, message: String) {
    try {
        val cleanPhone = phone.replace("+", "").replace(" ", "").replace("-", "")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        val cleanPhone = phone.replace("+", "").replace(" ", "").replace("-", "")
        val uri = Uri.parse("https://wa.me/$cleanPhone?text=${Uri.encode(message)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

fun callPhone(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (_: Exception) {}
}

