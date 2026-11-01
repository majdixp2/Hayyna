package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlacesLookup
import com.example.data.ReverseGeocoder
import com.example.data.model.Branch
import com.example.ui.i18n.Text
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
import java.util.UUID

private val BNavy = Color(0xFF10318F)

/** صف فرع في المراجعة */
data class BulkRow(
    val id: String = UUID.randomUUID().toString().take(12),
    val name: String = "", val district: String = "", val region: String = "",
    val lat: Double = 0.0, val lng: Double = 0.0, val phone: String = "", val on: Boolean = true
)

private fun distM(a: Double, b: Double, c: Double, d: Double): Double {
    val r = Math.PI / 180; val dLat = (c - a) * r; val dLng = (d - b) * r
    val x = Math.sin(dLat / 2).let { it * it } + Math.cos(a * r) * Math.cos(c * r) * Math.sin(dLng / 2).let { it * it }
    return 2 * 6_371_000 * Math.asin(Math.sqrt(x))
}

/** «منطقة مكة المكرمة - جدة» → «جدة» */
private fun cityOf(region: String) = region.substringAfterLast(" - ").trim()

/**
 * ➕ أضف عدة فروع مرة وحدة: من Google (سلسلة) أو بالضغط على الخريطة.
 * الاسم التلقائي «المحل + الحي» (يتعدّل)، الدوام واحد للكل، والرقم لكل فرع أو تأكيد إن طلباته تروح للرقم الأساسي.
 * [preset] = فروع جاهزة (من «أنا صاحب المحل» وعروض الإعلانات) — يفتح على المراجعة مباشرة.
 */
@Composable
fun BulkBranchesPage(
    storeName: String,
    existing: List<Branch>,
    onAdd: (List<Branch>) -> Unit,
    onCancel: () -> Unit,
    preset: List<BulkRow>? = null,
    title: String? = null,
    lead: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun autoName(d: String) = listOf(storeName.trim(), PlacesLookup.cleanDistrict(d)).filter { it.isNotBlank() }.joinToString(" ").take(60)
    fun isDup(lat: Double, lng: Double) = existing.any { distM(it.latitude, it.longitude, lat, lng) < 60 }

    var step by remember { mutableStateOf(if (preset != null) "review" else "pick") }
    var mode by remember { mutableStateOf("google") }
    var q by remember { mutableStateOf(storeName) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PlacesLookup.Found>?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    val pins = remember { mutableStateListOf<BulkRow>() }
    val rows = remember { mutableStateListOf<BulkRow>().apply { preset?.let { addAll(it) } } }
    val last = existing.lastOrNull()
    var open24 by remember { mutableStateOf(last != null && last.openMinute >= 0 && last.openMinute == last.closeMinute) }
    var openMin by remember { mutableStateOf(last?.openMinute?.takeIf { it >= 0 } ?: 720) }
    var closeMin by remember { mutableStateOf(last?.closeMinute?.takeIf { it >= 0 } ?: 0) }
    var ack by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    BackHandler { if (step == "review" && preset == null) step = "pick" else onCancel() }

    fun search() {
        if (q.isBlank() || searching) return
        searching = true
        scope.launch {
            val near = existing.firstOrNull()
            results = PlacesLookup.searchText(context, q.trim(), near?.latitude, near?.longitude, 20)
            searching = false
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title ?: if (step == "pick") "➕ أضف عدة فروع" else "✔ راجع الفروع (${rows.size})",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
            }
            HorizontalDivider()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), enabled = !(step == "pick" && mode == "map")).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step == "pick") {
                    // تبويبين: Google / الخريطة
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
                        listOf("google" to "🔎 من Google", "map" to "📍 من الخريطة").forEach { (k, l) ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == k) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { mode = k; error = "" }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(l, fontWeight = FontWeight.Bold, color = if (mode == k) BNavy else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (mode == "google") {
                        Text("اكتب اسم محلك والمدينة، وعلّم على فروعك من القائمة.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(q, { q = it.take(80) }, singleLine = true, placeholder = { Text("مثال: كنتاكي جدة") }, modifier = Modifier.weight(1f))
                            Button(onClick = { search() }, enabled = !searching, colors = ButtonDefaults.buttonColors(containerColor = BNavy), shape = RoundedCornerShape(12.dp)) {
                                if (searching) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("بحث", fontWeight = FontWeight.Bold)
                            }
                        }
                        results?.let { res ->
                            if (res.isEmpty()) Text("ما لقينا نتائج. جرّب اسم ثاني، أو حدد فروعك من الخريطة.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else {
                                val free = res.filter { !isDup(it.lat, it.lng) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = free.isNotEmpty() && free.all { it.placeId in sel }, onCheckedChange = { on -> sel.clear(); if (on) sel.addAll(free.map { it.placeId }) })
                                    Text("اختر الكل (${free.size})", fontWeight = FontWeight.Bold)
                                }
                                res.forEach { r ->
                                    val dup = isDup(r.lat, r.lng)
                                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !dup) { if (r.placeId in sel) sel.remove(r.placeId) else sel.add(r.placeId) }) {
                                        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = dup || r.placeId in sel, enabled = !dup, onCheckedChange = { on -> if (on) sel.add(r.placeId) else sel.remove(r.placeId) })
                                            Column(Modifier.weight(1f)) {
                                                androidx.compose.material3.Text(r.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                                val sub = if (dup) "✅ مضاف من قبل" else listOf(PlacesLookup.cleanDistrict(r.district).takeIf { it.isNotBlank() }?.let { "حي $it" }, r.address).filterNotNull().filter { it.isNotBlank() }.joinToString(" — ")
                                                androidx.compose.material3.Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text("اضغط على مكان كل فرع في الخريطة. الحي يتعبّى لحاله.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val start = existing.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(21.5433, 39.1728)
                        val cam = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(start, 12f) }
                        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(), cameraPositionState = cam,
                                properties = MapProperties(mapStyleOptions = CleanMapStyle.options),
                                uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false, mapToolbarEnabled = false),
                                onMapClick = { ll ->
                                    when {
                                        pins.size >= 30 -> error = "حد أقصى 30 فرع مرة وحدة"
                                        isDup(ll.latitude, ll.longitude) || pins.any { distM(it.lat, it.lng, ll.latitude, ll.longitude) < 60 } -> error = "فيه فرع في هالمكان من قبل"
                                        else -> {
                                            error = ""
                                            val pin = BulkRow(lat = ll.latitude, lng = ll.longitude, district = "…")
                                            pins.add(pin)
                                            scope.launch {
                                                val p = ReverseGeocoder.lookup(context, ll.latitude, ll.longitude)
                                                val i = pins.indexOfFirst { it.id == pin.id }
                                                if (i >= 0) pins[i] = pins[i].copy(district = PlacesLookup.cleanDistrict(p?.district.orEmpty()), region = cityOf(p?.region.orEmpty()))
                                            }
                                        }
                                    }
                                }
                            ) {
                                pins.forEachIndexed { i, p ->
                                    MarkerComposable(keys = arrayOf(p.id, i), state = rememberMarkerState(key = p.id, position = LatLng(p.lat, p.lng))) {
                                        Box(Modifier.size(28.dp).clip(CircleShape).background(BNavy).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                            androidx.compose.material3.Text("${i + 1}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        pins.forEachIndexed { i, p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(BNavy), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(when { p.district == "…" -> "…"; p.district.isBlank() -> "حي غير معروف — بتكتبه بعدين"; else -> "حي ${p.district}" }, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pins.removeAt(i) }) { Icon(Icons.Default.Close, contentDescription = "شيل", modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                } else {
                    Text(lead ?: "الاسم يتكوّن من اسم المحل والحي — تقدر تعدّله.", fontSize = 13.sp, color = if (lead != null) Color(0xFF1D4ED8) else MaterialTheme.colorScheme.onSurfaceVariant)
                    rows.forEachIndexed { i, r ->
                        Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(BNavy), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (preset != null) {
                                        Checkbox(checked = r.on, onCheckedChange = { rows[i] = r.copy(on = it) }); Text("أضفه", fontSize = 13.sp)
                                    } else IconButton(onClick = { rows.removeAt(i); if (rows.isEmpty()) step = "pick" }) { Icon(Icons.Default.Close, contentDescription = "شيل", modifier = Modifier.size(18.dp)) }
                                }
                                OutlinedTextField(r.name, { rows[i] = r.copy(name = it.take(60)) }, label = { Text("اسم الفرع") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(r.district, { rows[i] = r.copy(district = it.take(60)) }, label = { Text("الحي *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(r.phone, { rows[i] = r.copy(phone = it.take(20)) }, label = { Text("رقم واتساب / جوال الفرع") }, placeholder = { Text("05xxxxxxxx") },
                                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Text("🕐 الدوام (لكل الفروع)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = open24, onCheckedChange = { open24 = it }); Text("مفتوح 24 ساعة", fontSize = 13.sp) }
                    if (!open24) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeDropdown("يفتح", openMin, { openMin = it }, Modifier.weight(1f))
                        TimeDropdown("يقفل", closeMin, { closeMin = it }, Modifier.weight(1f))
                    }
                    Text("تقدر تغيّر دوام أي فرع لحاله بعدين بزر ✎", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (rows.any { it.on && it.phone.isBlank() }) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ack, onCheckedChange = { ack = it })
                        Text("📞 أأكد على مسؤوليتي: الفروع اللي بدون رقم تروح طلباتها لرقم المتجر الأساسي", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)) {
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (step == "pick") {
                        val n = if (mode == "google") sel.size else pins.count { it.district != "…" }
                        Button(
                            onClick = {
                                val picked = if (mode == "google") (results ?: emptyList()).filter { it.placeId in sel }
                                    .map { BulkRow(name = autoName(it.district), district = PlacesLookup.cleanDistrict(it.district), region = it.city, lat = it.lat, lng = it.lng) }
                                else pins.filter { it.district != "…" }.map { it.copy(name = autoName(it.district)) }
                                if (picked.isNotEmpty()) { rows.clear(); rows.addAll(picked); error = ""; step = "review" }
                            },
                            enabled = n > 0, colors = ButtonDefaults.buttonColors(containerColor = BNavy), shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("التالي ($n ${if (n == 1) "فرع" else "فروع"})", fontWeight = FontWeight.Bold) }
                    } else {
                        val on = rows.filter { it.on }
                        Button(
                            onClick = {
                                val seen = HashMap<String, String>()
                                existing.forEach { e -> normalizeSaudiPhone(e.phone)?.let { seen[it] = e.displayName } }
                                val out = mutableListOf<Branch>()
                                for (r in on) {
                                    val d = PlacesLookup.cleanDistrict(r.district)
                                    val raw = r.phone.trim(); val ph = if (raw.isBlank()) "" else normalizeSaudiPhone(raw)
                                    val label = r.name.ifBlank { d }
                                    if (d.isBlank()) { error = "اكتب الحي للفرع «${label.ifBlank { "بدون اسم" }}»."; return@Button }
                                    if (ph == null) { error = "رقم «$label» غير صحيح — اكتبه مثل 05xxxxxxxx."; return@Button }
                                    if (ph.isNotBlank() && seen.containsKey(ph)) { error = "رقم «$label» مسجّل لفرع ${seen[ph]}، كل فرع له رقمه."; return@Button }
                                    if (ph.isNotBlank()) seen[ph] = label
                                    out += Branch(id = r.id, name = r.name.trim().take(60), latitude = r.lat, longitude = r.lng, district = d, region = r.region,
                                        phone = ph, openMinute = if (open24) 0 else openMin, closeMinute = if (open24) 0 else closeMin)
                                }
                                when {
                                    out.isEmpty() -> error = "اختر فرع واحد على الأقل."
                                    seen.isEmpty() -> error = "اكتب رقم فرع واحد على الأقل — هو الرقم الأساسي للمتجر."
                                    out.any { it.phone.isBlank() } && !ack -> error = "اكتب أرقام الفروع، أو علّم على التأكيد إن طلباتها تروح للرقم الأساسي."
                                    existing.size + out.size > 50 -> error = "الحد الأقصى 50 فرع للمتجر."
                                    else -> onAdd(out)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BNavy), shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("➕ أضف ${on.size} ${if (on.size == 1) "فرع" else "فروع"}", fontWeight = FontWeight.Bold) }
                        if (preset != null) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("مو فروعي، تخطّ") }
                    }
                }
            }
        }
    }
}
