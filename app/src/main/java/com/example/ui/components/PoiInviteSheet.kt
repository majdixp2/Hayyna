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

/** بطاقة محل من نقاط Google مو مسجّل في لكوفر: ادعه، أو "أنا صاحب المحل". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiInviteSheet(
    poi: PointOfInterest,
    inviteText: String,
    onInvited: () -> Unit,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
    isLoggedIn: Boolean = true,
    canClaim: Boolean = true,
    onLoginToInvite: () -> Unit = {},
    loadLock: suspend (String) -> Pair<Boolean, Long>? = { null },
    canScout: Boolean = false,
    onUploadAd: () -> Unit = {}
) {
    val context = LocalContext.current
    // رقم المحل من خرائط Google (لو متاح): جوال = نفتح واتساب المحل مباشرة
    var info by androidx.compose.runtime.remember(poi.placeId) { androidx.compose.runtime.mutableStateOf<com.example.data.PlacesLookup.Info?>(null) }
    var loading by androidx.compose.runtime.remember(poi.placeId) { androidx.compose.runtime.mutableStateOf(true) }
    // قفل الدعوة: (أنا اللي دعيته؟, ينتهي متى)
    var lock by androidx.compose.runtime.remember(poi.placeId) { androidx.compose.runtime.mutableStateOf<Pair<Boolean, Long>?>(null) }
    androidx.compose.runtime.LaunchedEffect(poi.placeId) {
        lock = loadLock(poi.placeId)
        info = com.example.data.PlacesLookup.lookup(context, poi.placeId)
        loading = false
    }
    val lockedByOther = lock?.first == false
    val mine = lock?.first == true
    val daysLeft = lock?.let { maxOf(1L, (it.second - System.currentTimeMillis() + 86_399_999L) / 86_400_000L) } ?: 0L
    val daysWord = if (daysLeft == 1L) "يوم" else "أيام"
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
                "هذا المحل مو مسجّل في لكوفر للحين. ادعه ينضم وينشر عروضه لأهل الحي 👇",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            if (!loading && lockedByOther) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.ui.graphics.Color(0xFFF3F4F6),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("📨 تمت دعوة هذا المحل من أحد أهل الحي", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
                        Text("ننتظره يسجّل — تقدر تدعوه بعد $daysLeft $daysWord", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
                    }
                }
            } else if (!isLoggedIn) {
                Button(
                    onClick = onLoginToInvite,
                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) { Text("📨 سجّل دخولك وادعه", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Text("🎁 الدعوة تنحسب باسمك، ولو سجّل المحل تاخذ لين 100 نقطة", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFF8A5A00))
            } else {
                if (mine) Text("✅ دعيت هذا المحل — ننتظره يسجّل (باقي $daysLeft $daysWord محجوز لك)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CalmSagePrimary)
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
                        context.startActivity(Intent.createChooser(send, "ادعُ المحل إلى لكوفر"))
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
                        mobile != null -> if (mine) "💬 ذكّره على واتساب المحل" else "💬 ادعه على واتساب المحل"
                        else -> if (mine) "📣 ذكّره مرة ثانية" else "📣 ادعه إلى لكوفر"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
                if (!mine) Text("🎁 لو سجّل المحل عن طريق دعوتك تاخذ لين 100 نقطة", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFF8A5A00))
            }
            val phone = info?.phone.orEmpty()
            if (phone.isNotBlank() && mobile == null) {
                OutlinedButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("📞 اتصل بالمحل ($phone)") }
            }
            if (canClaim) OutlinedButton(
                onClick = onClaim,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text("🏪 أنا صاحب المحل — سجّله", fontWeight = FontWeight.Bold) }
            else Text(
                "صاحب المحل؟ سجّل خروج وادخل بحساب صاحب محل عشان تسجّله",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // فتح في Google Maps + (للأدمن والفريق) رفع عرض من إعلان
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val u = "https://www.google.com/maps/search/?api=1&query=${poi.latLng.latitude},${poi.latLng.longitude}&query_place_id=${android.net.Uri.encode(poi.placeId)}"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("فتح في Google Maps", fontSize = 13.sp) }
                if (canScout) Button(
                    onClick = onUploadAd,
                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("📷 ارفع عرض", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
            Row { Text("المعلومات من خرائط Google", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
