package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import com.example.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.RestaurantOffer
import com.example.data.model.priceLabel
import com.example.ui.theme.CalmSagePrimary

/**
 * 🚀 «عرض سوبر قريب منك»: مرة وحدة باليوم، لما يكون فيه عرض مُبرز في حدود 3 كم.
 */
@Composable
fun SpotlightWelcome(offers: List<RestaurantOffer>, hasLocation: Boolean, onOpen: (RestaurantOffer) -> Unit) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<RestaurantOffer?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(offers, hasLocation) {
        if (checked || !hasLocation) return@LaunchedEffect
        val near = offers.filter { it.spotlight && it.distanceKm in 0.0..3.0 }
        if (near.isEmpty()) return@LaunchedEffect
        checked = true
        val prefs = context.getSharedPreferences("hayyna", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (prefs.getString("spot_day", "") == today) return@LaunchedEffect
        prefs.edit().putString("spot_day", today).apply()
        kotlinx.coroutines.delay(1200)
        shown = near.random()
    }
    val o = shown ?: return
    Dialog(onDismissRequest = { shown = null }) {
        Card(
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, Color(0xFFFFD166)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                Box(Modifier.fillMaxWidth().background(Color(0xFF1F2937))) {
                    Text(
                        "🚀 عرض سوبر قريب منك",
                        color = Color(0xFFFFD166), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
                AsyncImage(
                    model = o.mediaUris.firstOrNull() ?: o.mediaUri,
                    contentDescription = o.offerTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(o.restaurantName, color = CalmSagePrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    androidx.compose.material3.Text(o.offerTitle, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 2)
                    Text(o.priceLabel(withScope = true), color = CalmSagePrimary, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(listOf(o.district, "%.1f كم".format(o.distanceKm)).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 12.sp, color = Color(0xFF6B7280))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        OutlinedButton(onClick = { shown = null }, modifier = Modifier.weight(1f)) { Text("لاحقاً") }
                        Button(
                            onClick = { shown = null; onOpen(o) },
                            colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                            modifier = Modifier.weight(1f)
                        ) { Text("شوف العرض", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
