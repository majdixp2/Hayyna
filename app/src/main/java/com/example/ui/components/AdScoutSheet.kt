package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.example.ui.i18n.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.PlacesLookup
import com.example.ui.MerchantViewModel
import com.google.android.gms.maps.model.PointOfInterest

private val Navy = Color(0xFF10318F)
private val Gold = Color(0xFFF4B400)

/**
 * 📷 رفع عرض من صورة إعلان على موقع محل Google مو مسجّل (للأدمن وفريق الإعلانات).
 * الذكاء الاصطناعي يقرأ الإعلان ويعبّي البيانات، والعرض يختفي لحاله بعد المدة.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdScoutSheet(poi: PointOfInterest, vm: MerchantViewModel) {
    val context = LocalContext.current
    val ai by vm.aiConfig.collectAsStateWithLifecycle()
    val busyAi by vm.isAutofilling.collectAsStateWithLifecycle()
    val publishing by vm.isPublishingAd.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var place by remember(poi.placeId) { mutableStateOf<PlacesLookup.Info?>(null) }
    var image by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf(poi.name.replace("\n", " ").trim()) }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var orig by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiDone by remember { mutableStateOf(false) }
    // 👥 فروع ثانية لنفس المحل (نفس الإعلان)
    val extras = remember { mutableStateListOf<PlacesLookup.Found>() }
    var addMore by remember { mutableStateOf(false) }
    var moreQ by remember { mutableStateOf("") }
    var moreRes by remember { mutableStateOf<List<PlacesLookup.Found>?>(null) }
    var moreBusy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(poi.placeId) {
        days = vm.adScoutDays().toString()
        place = PlacesLookup.lookup(context, poi.placeId)
        place?.let { p -> if (p.name.isNotBlank()) name = p.name; if (phone.isBlank()) phone = p.phone }
    }
    LaunchedEffect(categories) { if (category.isBlank()) category = categories.firstOrNull().orEmpty() }

    fun runAi(uri: Uri) {
        vm.autofillFromImage(uri.toString()) { d ->
            if (d.title.isNotBlank()) title = d.title
            if (d.description.isNotBlank()) desc = d.description
            d.price?.let { price = fmt(it) }
            d.originalPrice?.let { orig = fmt(it) }
            d.days?.let { days = it.coerceIn(1, 31).toString() }
            if (d.phone.isNotBlank() && phone.isBlank()) phone = d.phone
            if (d.storeName.isNotBlank() && name.isBlank()) name = d.storeName
            tags = d.tags
            aiDone = true
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { image = uri; aiDone = false; if (ai.first) runAi(uri) }
    }

    ModalBottomSheet(onDismissRequest = { vm.closeAdUpload() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("📷 ارفع عرض من إعلان", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
                "العرض بيتحط على موقع هذا المحل، ويظهر للزبائن بعلامة «📷 من إعلان المحل» ويختفي لحاله بعد المدة.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // الصورة
            Box(
                Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(16.dp))
                    .background(Navy.copy(alpha = 0.05f))
                    .border(2.dp, Navy.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(8.dp).clip(RoundedCornerShape(12.dp)))
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text("تغيير الصورة", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text("🖼️", fontSize = 34.sp)
                    Text("اختر صورة الإعلان", fontWeight = FontWeight.Bold, color = Navy)
                    Text("بوستر، منشور انستقرام، أو صورة من واتساب", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (image != null) {
                if (ai.first) Button(
                    onClick = { image?.let { runAi(it) } }, enabled = !busyAi,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    if (busyAi) { CircularProgressIndicator(Modifier.size(18.dp), color = Navy, strokeWidth = 2.dp); Text("  نقرأ الإعلان…", fontWeight = FontWeight.Bold) }
                    else Text(if (aiDone) "✨ اقرأه مرة ثانية" else "✨ اقرأ الإعلان بالذكاء الاصطناعي", fontWeight = FontWeight.Bold)
                } else Note("✨ القراءة الذكية موقفة من الإعدادات — عبّي البيانات يدوي")
                if (aiDone) Note("✅ قرأنا الإعلان. راجع البيانات قبل النشر", Color(0x2215803D))
            }
            // المحل
            Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Navy.copy(alpha = 0.2f)), color = Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 22.sp)
                    Column(Modifier.padding(start = 10.dp)) {
                        androidx.compose.material3.Text(place?.name?.ifBlank { null } ?: poi.name.replace("\n", " "), fontWeight = FontWeight.Bold)
                        val addr = place?.address?.ifBlank { null } ?: listOfNotNull(place?.district?.ifBlank { null }, place?.city?.ifBlank { null }).joinToString("، ")
                        if (addr.isNotBlank()) Text(addr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            extras.forEach { x ->
                Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Navy.copy(alpha = 0.2f)), color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 20.sp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            androidx.compose.material3.Text(x.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            androidx.compose.material3.Text(listOf(x.district, x.address).filter { it.isNotBlank() }.joinToString(" — "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(onClick = { extras.remove(x) }) { Text("✕") }
                    }
                }
            }
            if (!addMore) TextButton(onClick = { addMore = true; if (moreQ.isBlank()) moreQ = name }) { Text("＋ فروع ثانية لنفس المحل", color = Navy, fontWeight = FontWeight.Bold) }
            else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(moreQ, { moreQ = it.take(80) }, singleLine = true, label = { Text("دوّر على فروعه") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (moreQ.isBlank() || moreBusy) return@Button
                        moreBusy = true
                        scope.launch { moreRes = PlacesLookup.searchText(context, moreQ.trim(), poi.latLng.latitude, poi.latLng.longitude, 20).filter { it.placeId != poi.placeId }; moreBusy = false }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Navy), shape = RoundedCornerShape(12.dp)) {
                        if (moreBusy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Text("بحث")
                    }
                }
                moreRes?.let { res ->
                    if (res.isEmpty()) Note("ما لقينا فروع ثانية. جرّب اسم ثاني أو أضف المدينة.")
                    else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = res.all { r -> extras.any { it.placeId == r.placeId } }, onCheckedChange = { on ->
                                if (on) res.forEach { r -> if (extras.none { it.placeId == r.placeId }) extras.add(r) } else extras.removeAll { e -> res.any { it.placeId == e.placeId } } })
                            Text("اختر الكل (${res.size})", fontWeight = FontWeight.Bold)
                        }
                        res.forEach { r ->
                            val on = extras.any { it.placeId == r.placeId }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (on) extras.removeAll { it.placeId == r.placeId } else extras.add(r) }) {
                                androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { c -> if (c) extras.add(r) else extras.removeAll { it.placeId == r.placeId } })
                                Column(Modifier.weight(1f)) {
                                    androidx.compose.material3.Text(r.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                    androidx.compose.material3.Text(listOf(r.district, r.address).filter { it.isNotBlank() }.joinToString(" — "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { addMore = false; moreRes = null }) { Text("تم ✓", color = Navy, fontWeight = FontWeight.Bold) }
                }
            }
            OutlinedTextField(name, { name = it.take(100) }, label = { Text("اسم المحل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it.take(150) }, label = { Text("عنوان العرض *") }, placeholder = { Text("مثال: عرض الوطن الجامبو") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(price, { price = it.take(10) }, label = { Text("السعر (ر.س)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(orig, { orig = it.take(10) }, label = { Text("قبل الخصم") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(desc, { desc = it.take(2000) }, label = { Text("الوصف") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("جوال المحل") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
                OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, label = { Text("يختفي بعد (أيام)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            if (categories.isNotEmpty()) {
                Text("التصنيف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c ->
                        val on = c == category
                        Surface(
                            shape = RoundedCornerShape(20.dp), color = if (on) Navy else Color.Transparent,
                            border = BorderStroke(1.dp, Navy.copy(alpha = if (on) 1f else 0.25f)),
                            modifier = Modifier.clickable { category = c }
                        ) { Text(c, fontSize = 13.sp, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                }
            }
            val ready = image != null && title.isNotBlank()
            Button(
                onClick = {
                    val img = image ?: return@Button
                    vm.publishAdOffer(poi, place, MerchantViewModel.AdDraft(
                        img.toString(), name, title, price, orig, desc, phone, category, days.toIntOrNull() ?: 7, tags), extras.toList())
                },
                enabled = ready && !publishing,
                colors = ButtonDefaults.buttonColors(containerColor = Navy),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) {
                if (publishing) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Text("  ننشر…", fontWeight = FontWeight.Bold) }
                else Text(if (extras.isNotEmpty()) "🚀 انشر على ${extras.size + 1} فروع" else "🚀 انشر على الخريطة", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            if (!ready) Text(
                if (image == null) "اختر صورة الإعلان عشان تقدر تنشر" else "اكتب عنوان العرض عشان تقدر تنشر",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun Note(text: String, bg: Color = Color(0x24F4B400)) {
    Surface(shape = RoundedCornerShape(12.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 12.5.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** شريط «📷 من إعلان المحل» في صفحة العرض. */
@Composable
fun AdSourceBanner(onClaim: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = Gold.copy(alpha = 0.14f), border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📷", fontSize = 20.sp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("من إعلان المحل", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("المحل مو مسجّل في لكوفر — تأكد من العرض مع المحل قبل ما تروح", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClaim != null) Surface(
                shape = RoundedCornerShape(10.dp), color = Navy.copy(alpha = 0.12f), modifier = Modifier.clickable { onClaim() }
            ) { Text("أنا صاحب المحل", color = Navy, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
        }
    }
}
