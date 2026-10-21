package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.ui.theme.CalmSagePrimary
import com.google.android.gms.maps.model.PointOfInterest

/** بطاقة محل من نقاط Google مو مسجّل في حيّنا: ادعه، أو "أنا صاحب المحل". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiInviteSheet(
    poi: PointOfInterest,
    inviteText: String,
    onInvited: () -> Unit,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // رقم المحل من خرائط Google (لو متاح): جوال = نفتح واتساب المحل مباشرة
    var info by androidx.compose.runtime.remember(poi.placeId) { androidx.compose.runtime.mutableStateOf<com.example.data.PlacesLookup.Info?>(null) }
    var loading by androidx.compose.runtime.remember(poi.placeId) { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(poi.placeId) {
        info = com.example.data.PlacesLookup.lookup(context, poi.placeId)
        loading = false
    }
    val mobile = info?.phone?.let { com.example.data.PlacesLookup.saudiMobile(it) }
    val name = info?.name?.takeIf { it.isNotBlank() }
    val text = if (name != null && !inviteText.contains(name)) inviteText.replace(poi.name.trim(), name) else inviteText
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text((name ?: poi.name).replace("\n", " "), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
                "هذا المحل مو مسجّل في حيّنا للحين. ادعه ينضم وينشر عروضه لأهل الحي 👇",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onInvited()
                    if (mobile != null) {
                        // واتساب المحل مباشرة والرسالة جاهزة
                        val wa = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$mobile?text=" + android.net.Uri.encode(text)))
                        runCatching { context.startActivity(wa) }
                    } else {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "ادعُ المحل لحيّنا"))
                    }
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = if (mobile != null) androidx.compose.ui.graphics.Color(0xFF25D366) else CalmSagePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) {
                Text(
                    when {
                        loading -> "…"
                        mobile != null -> "💬 ادعه على واتساب المحل"
                        else -> "📣 ادعه لحيّنا"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            val phone = info?.phone.orEmpty()
            if (phone.isNotBlank() && mobile == null) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("📞 اتصل بالمحل ($phone)") }
            }
            OutlinedButton(
                onClick = onClaim,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text("🏪 أنا صاحب المحل — سجّله", fontWeight = FontWeight.Bold) }
            Row { Text("المعلومات من خرائط Google", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
