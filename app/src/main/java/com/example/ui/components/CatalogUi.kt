package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.CloudinaryUrls
import com.example.data.model.CatalogConfig
import com.example.data.model.CatalogItem
import com.example.data.model.HayynaOrder
import com.example.data.model.ItemKind
import com.example.data.model.StoreProfile
import com.example.ui.MerchantViewModel
import com.example.ui.theme.CalmSagePrimary

private fun money(d: Double) = String.format(java.util.Locale.US, "%.2f", d)
private fun dm(t: Long): String { val c = java.util.Calendar.getInstance().apply { timeInMillis = t }; return "${c.get(java.util.Calendar.DAY_OF_MONTH)}/${c.get(java.util.Calendar.MONTH) + 1}" }

@Composable
private fun FullDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("✕", fontSize = 18.sp) }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

// =================== الزبون: صفحة المتجر ===================

/** المنيو/المنتجات/الخدمات داخل صفحة المتجر + السلة */
@Composable
fun StoreCatalogSection(store: StoreProfile, vm: MerchantViewModel) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val carts by vm.carts.collectAsStateWithLifecycle()
    var items by remember(store.uid) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var checkout by remember { mutableStateOf(false) }
    LaunchedEffect(store.uid, cfg.enabled) { items = vm.loadStoreCatalog(store.uid) }
    if (!cfg.enabled || items.isEmpty()) return
    val cart = carts[store.uid].orEmpty()
    val base = cfg.baseKind(store.category)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ItemKind.order(base).forEach { k ->
            val list = items.filter { it.kind == k.id }.sortedBy { it.slot }
            if (list.isEmpty()) return@forEach
            Surface(shape = RoundedCornerShape(12.dp), color = k.bg, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("${k.icon} ${k.title} (${list.size})", color = k.color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
            list.map { it.section }.distinct().forEach { sec ->
                if (sec.isNotBlank()) Text(sec, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                list.filter { it.section == sec }.forEach { it ->
                    val q = cart[it.id]?.qty ?: 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(k.bg), contentAlignment = Alignment.Center) {
                            if (it.imageUrl.isNotBlank()) AsyncImage(model = CloudinaryUrls.image(it.imageUrl, 240), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(k.icon, fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (it.desc.isNotBlank()) Text(it.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${money(it.price)} ر.س", color = k.color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        when {
                            !it.available -> Text("نفد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            !cfg.orderEnabled -> Unit
                            q == 0 -> OutlinedButton(onClick = { vm.cartAdd(store.uid, it, 1) }, border = BorderStroke(1.dp, k.color), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("＋ أضف", color = k.color, fontWeight = FontWeight.Bold) }
                            else -> QtyControl(q, k.color, onMinus = { vm.cartAdd(store.uid, it, -1) }, onPlus = { vm.cartAdd(store.uid, it, 1) })
                        }
                    }
                }
            }
        }
        if (cfg.orderEnabled && cart.isNotEmpty()) {
            Button(
                onClick = { checkout = true }, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(top = 6.dp)
            ) { Text("🧾 سلتك: ${cart.values.sumOf { it.qty }} • ${money(cart.values.sumOf { it.qty * it.price })} ر.س — اطلب", fontWeight = FontWeight.ExtraBold) }
        }
    }
    if (checkout) CheckoutDialog(store, vm, onDismiss = { checkout = false })
}

@Composable
private fun QtyControl(q: Int, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(32.dp).clickable(onClick = onMinus)) { Box(contentAlignment = Alignment.Center) { Text("−", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
        Text("$q", fontWeight = FontWeight.Bold)
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(32.dp).clickable(onClick = onPlus)) { Box(contentAlignment = Alignment.Center) { Text("＋", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutDialog(store: StoreProfile, vm: MerchantViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val carts by vm.carts.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()
    val userPhone by vm.userPhone.collectAsStateWithLifecycle()
    val loc by vm.userLocation.collectAsStateWithLifecycle()
    val lines = carts[store.uid].orEmpty().values.toList()
    var mode by remember { mutableStateOf("pickup") }
    var branchId by remember { mutableStateOf(store.branches.firstOrNull()?.id.orEmpty()) }
    var name by remember { mutableStateOf(userName) }
    var phone by remember { mutableStateOf(userPhone.let { p -> val d = p.filter { it.isDigit() }; if (d.startsWith("966")) "0" + d.drop(3) else d }) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(lines.isEmpty()) { if (lines.isEmpty()) onDismiss() }
    val sub = lines.sumOf { it.price * it.qty }
    val freeDel = store.deliveryFreeOver > 0 && sub >= store.deliveryFreeOver
    val fee = if (mode == "delivery" && !freeDel) store.deliveryFee else 0.0
    val total = sub + fee
    val vatNo = if (cfg.vatEnabled && CatalogConfig.validVat(store.vatNumber)) store.vatNumber else ""
    val vat = total * cfg.vatRate / (100 + cfg.vatRate)
    val minOk = mode != "delivery" || store.deliveryMinOrder <= 0 || sub >= store.deliveryMinOrder
    FullDialog("🧾 طلبك من ${store.name}", onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    QtyControl(l.qty, CalmSagePrimary, { vm.cartLineAdd(store.uid, l.id, -1) }, { vm.cartLineAdd(store.uid, l.id, 1) })
                    Spacer(Modifier.width(10.dp)); Text(money(l.qty * l.price), fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "pickup", onClick = { mode = "pickup" }, label = { Text("🏃 استلام من المحل") })
                if (store.delivery) FilterChip(selected = mode == "delivery", onClick = { mode = "delivery" }, label = { Text("🚚 توصيل") })
            }
            if (store.branches.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                store.branches.forEach { b -> FilterChip(selected = branchId == b.id, onClick = { branchId = b.id }, label = { Text(b.name.ifBlank { b.district.ifBlank { "فرع" } }, fontSize = 12.sp) }) }
            }
            if (mode == "delivery") Text(if (loc != null) "📍 بنرسل موقعك الحالي للمحل" else "📍 فعّل الموقع عشان نرسل مكانك للتوصيل", fontSize = 13.sp, color = if (loc != null) CalmSagePrimary else MaterialTheme.colorScheme.error)
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("اسمك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("جوالك") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it.take(300) }, label = { Text("ملاحظة (اختياري)") }, placeholder = { Text("مثال: بدون بصل") }, modifier = Modifier.fillMaxWidth())
            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    @Composable fun line(a: String, b: String, bold: Boolean = false) = Row { Text(a, Modifier.weight(1f), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal); Text(b, fontWeight = FontWeight.Bold) }
                    if (fee > 0) { line("المجموع", money(sub)); line("🚚 التوصيل", money(fee)) } else if (mode == "delivery" && freeDel) line("🚚 التوصيل", "مجاني 🎉")
                    if (vatNo.isNotBlank()) { line("المبلغ قبل الضريبة", money(total - vat)); line("ضريبة القيمة المضافة ${cfg.vatRate.toString().removeSuffix(".0")}%", money(vat)) }
                    line("💰 الإجمالي" + if (vatNo.isNotBlank()) " (شامل الضريبة)" else "", "${money(total)} ر.س", true)
                }
            }
            if (!minOk) Text("أقل طلب للتوصيل ${money(store.deliveryMinOrder)} ر.س", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Button(
                onClick = {
                    err = when {
                        name.isBlank() -> "اكتب اسمك."
                        !(phone.startsWith("05") && phone.length == 10) -> "اكتب رقم جوال صحيح مثل 05xxxxxxxx."
                        mode == "delivery" && loc == null -> "فعّل الموقع عشان التوصيل."
                        else -> ""
                    }
                    if (err.isBlank()) vm.sendOrder(store, store.branches.firstOrNull { it.id == branchId }, mode, name, "+966" + phone.drop(1), note) { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        onDismiss()
                    }
                },
                enabled = minOk, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text("📨 أرسل الطلب على واتساب", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        }
    }
}

// =================== الطلبات ===================

@Composable
fun OrdersDialog(title: String, orders: List<HayynaOrder>, asStore: Boolean, onStatus: (HayynaOrder, String) -> Unit, onAgain: ((HayynaOrder) -> Unit)? = null, onDismiss: () -> Unit) {
    FullDialog(title, onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (orders.isEmpty()) Text(if (asStore) "ما وصلتك طلبات للحين" else "ما طلبت شي للحين — ادخل صفحة أي متجر واطلب منه 🛍️", color = MaterialTheme.colorScheme.onSurfaceVariant)
            orders.take(100).forEach { o ->
                Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row { Text("#${o.code}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text(HayynaOrder.STATUS[o.status] ?: o.status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CalmSagePrimary) }
                        Text((if (asStore) listOf(o.customerName, o.customerPhone).filter { it.isNotBlank() }.joinToString(" • ") else "🏪 ${o.storeName}") + if (o.createdAtMillis > 0) " • ${dm(o.createdAtMillis)}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(o.items.joinToString("، ") { "${it.qty} × ${it.name}" }, fontSize = 13.sp)
                        Text("${if (o.mode == "delivery") "🚚 توصيل" else "🏃 استلام"} • ${money(o.total)} ر.س" + if (o.note.isNotBlank()) " • 📝 ${o.note}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val next = if (asStore) when (o.status) {
                            "sent" -> listOf("received" to "👍 استلمته", "cancelled" to "✕ إلغاء")
                            "received" -> listOf("ready" to "✅ جاهز", "cancelled" to "✕ إلغاء")
                            "ready" -> listOf("done" to "🎉 تم التسليم")
                            else -> emptyList()
                        } else if (o.status == "sent") listOf("cancelled" to "✕ إلغاء الطلب") else emptyList()
                        if (next.isNotEmpty() || onAgain != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            next.forEach { (s, l) -> OutlinedButton(onClick = { onStatus(o, s) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(l, fontSize = 12.sp) } }
                            if (onAgain != null) OutlinedButton(onClick = { onAgain(o) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("↻ اطلب نفسه", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** طلباتي (الزبون) */
@Composable
fun MyOrdersDialog(vm: MerchantViewModel, onDismiss: () -> Unit) {
    var orders by remember { mutableStateOf<List<HayynaOrder>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { orders = vm.loadMyOrders() }
    OrdersDialog("🧾 طلباتي", orders, asStore = false, onStatus = { o, s -> vm.setOrderStatus(o.id, s); orders = orders.map { if (it.id == o.id) it.copy(status = s) else it } }, onDismiss = onDismiss)
}

// =================== التاجر ===================

/** بطاقة «منتجاتي» في لوحة التاجر */
@Composable
fun MerchantCatalogCard(vm: MerchantViewModel, category: String, balance: Long) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val items by vm.myItems.collectAsStateWithLifecycle()
    val passes by vm.myPasses.collectAsStateWithLifecycle()
    val orders by vm.storeOrders.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf(false) }
    var showOrders by remember { mutableStateOf(false) }
    if (!cfg.enabled) return
    val base = cfg.baseKind(category)
    val now = System.currentTimeMillis()
    val newOrders = orders.count { it.status == "sent" }
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${base.icon} ${base.title}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(if (items.isEmpty()) "أضف منتجاتك وخدماتك عشان يشوفها الزبون في صفحة متجرك" else "${items.size} صنف • ${items.count { cfg.visible(it, passes, now) }} ظاهر للناس", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemKind.order(base).forEach { k ->
                    val lim = cfg.limitOf(k.id)
                    Surface(shape = RoundedCornerShape(10.dp), color = k.bg) { Text("${k.icon} ${items.count { it.kind == k.id }}${if (lim > 0) "/$lim" else ""}", color = k.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { manage = true }, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary), modifier = Modifier.weight(1f)) { Text("🛍️ إدارة منتجاتي", fontWeight = FontWeight.Bold) }
                if (cfg.orderEnabled) OutlinedButton(onClick = { showOrders = true }, modifier = Modifier.weight(1f)) { Text("🧾 الطلبات" + if (newOrders > 0) " ($newOrders)" else "", fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (manage) CatalogManagerDialog(vm, base, balance) { manage = false }
    if (showOrders) OrdersDialog("🧾 الطلبات (${orders.size})", orders, asStore = true, onStatus = { o, s -> vm.setOrderStatus(o.id, s) }) { showOrders = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogManagerDialog(vm: MerchantViewModel, base: ItemKind, balance: Long, onDismiss: () -> Unit) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val items by vm.myItems.collectAsStateWithLifecycle()
    val passes by vm.myPasses.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(base) }
    var editing by remember { mutableStateOf<CatalogItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf<CatalogItem?>(null) }
    val now = System.currentTimeMillis()
    val mine = items.filter { it.kind == kind.id }.sortedBy { it.slot }
    val lim = cfg.limitOf(kind.id)
    val full = lim > 0 && items.count { it.kind == kind.id && it.slot < lim } >= lim
    val allEnd = passes.filter { it.scope == "all" && it.endsAt > now }.maxOfOrNull { it.endsAt } ?: 0L
    FullDialog("🛍️ منتجاتي وخدماتي", onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cfg.pricingEnabled) Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF4E0), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("📢 ظهور منتجاتك للزبائن", fontWeight = FontWeight.Bold, color = Color(0xFF8A5A00))
                        Text((if (allEnd > now) "📦 الباقة شغّالة لين ${dm(allEnd)}" else "كل منتج يظهر بعد ما تدفع لظهوره") + " • رصيدك $balance نقطة", fontSize = 12.sp, color = Color(0xFF8A5A00))
                    }
                    Button(onClick = { pay = true }, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary)) { Text("ادفع للظهور") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemKind.order(base).forEach { k ->
                    val l = cfg.limitOf(k.id)
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text("${k.icon} ${k.label} (${items.count { it.kind == k.id }}${if (l > 0) "/$l" else ""})") })
                }
            }
            Button(onClick = { adding = true }, enabled = !full, colors = ButtonDefaults.buttonColors(containerColor = kind.color), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text("＋ إضافة ${kind.add}", fontWeight = FontWeight.ExtraBold)
            }
            if (full) Text("وصلت للحد ($lim). احذف صنف أو تواصل معنا لو تحتاج أكثر.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mine.isEmpty()) Text("ما أضفت ${kind.label} للحين", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            mine.forEach { it ->
                val paid = cfg.paidUntil(it, passes, now); val g = cfg.grace(it)
                val status = when {
                    !it.approved -> "⏳ بانتظار المراجعة"
                    !it.available -> "⛔ نفد"
                    lim > 0 && it.slot >= lim -> "مخفي (فوق الحد)"
                    !cfg.pricingEnabled -> "✅ ظاهر"
                    paid > now -> "✅ ظاهر لين ${dm(paid)}"
                    g > now -> "🕐 مجاني لين ${if (g == Long.MAX_VALUE) "إشعار آخر" else dm(g)}"
                    cfg.price(it.kind).active || cfg.price("all").active -> "💤 مو ظاهر — ادفع للظهور"
                    else -> "✅ ظاهر"
                }
                Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(kind.bg), contentAlignment = Alignment.Center) {
                            if (it.imageUrl.isNotBlank()) AsyncImage(model = CloudinaryUrls.image(it.imageUrl, 200), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text(kind.icon, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${money(it.price)} ر.س" + if (it.section.isNotBlank()) " • ${it.section}" else "", fontSize = 12.sp, color = kind.color, fontWeight = FontWeight.Bold)
                            Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TextButton(onClick = { vm.toggleItemAvailable(it) }, contentPadding = PaddingValues(4.dp)) { Text(if (it.available) "✓ متوفر" else "⛔ نفد", fontSize = 11.sp) }
                            Row {
                                TextButton(onClick = { editing = it }, contentPadding = PaddingValues(4.dp)) { Text("✎") }
                                TextButton(onClick = { confirmDel = it }, contentPadding = PaddingValues(4.dp)) { Text("🗑") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) ItemEditDialog(vm, editing, if (editing != null) ItemKind.of(editing!!.kind) else kind, items) { adding = false; editing = null }
    if (pay) ItemPayDialog(vm, kind, balance) { pay = false }
    confirmDel?.let { d ->
        AlertDialog(onDismissRequest = { confirmDel = null }, title = { Text("حذف «${d.name}»؟") },
            confirmButton = { TextButton(onClick = { vm.deleteCatalogItem(d.id); confirmDel = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDel = null }) { Text("إلغاء") } })
    }
}

@Composable
private fun ItemEditDialog(vm: MerchantViewModel, e: CatalogItem?, kind: ItemKind, all: List<CatalogItem>, onDismiss: () -> Unit) {
    val saving by vm.isSavingItem.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(e?.name.orEmpty()) }
    var price by remember { mutableStateOf(e?.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
    var desc by remember { mutableStateOf(e?.desc.orEmpty()) }
    var section by remember { mutableStateOf(e?.section.orEmpty()) }
    var available by remember { mutableStateOf(e?.available ?: true) }
    var image by remember { mutableStateOf(e?.imageUrl.orEmpty()) }
    var err by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) image = uri.toString() }
    LaunchedEffect(saving) { if (started && !saving) onDismiss() }
    val sections = all.filter { it.kind == kind.id }.map { it.section }.filter { it.isNotBlank() }.distinct()
    FullDialog("${kind.icon} ${if (e == null) "إضافة" else "تعديل"} ${kind.add}", onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(kind.bg).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                if (image.isNotBlank()) AsyncImage(model = if (image.startsWith("http")) CloudinaryUrls.image(image, 600) else image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("📷 صورة (اختياري)", color = kind.color, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("الاسم *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, label = { Text("السعر (ر.س) *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(section, { section = it.take(40) }, label = { Text("القسم (اختياري)") }, placeholder = { Text(if (kind == ItemKind.MENU) "مثال: مشروبات، وجبات" else "مثال: رجالي، نسائي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (sections.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sections.take(5).forEach { s -> FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s, fontSize = 12.sp) }) } }
            OutlinedTextField(desc, { desc = it.take(300) }, label = { Text("وصف قصير") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Text("متوفر الحين", modifier = Modifier.weight(1f)); Switch(checked = available, onCheckedChange = { available = it }) }
            if (!vm.catalogDirect) Text("⏳ بيظهر بعد مراجعة فريق حيّنا. وثّق متجرك عشان تنزل منتجاتك مباشرة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val p = price.toDoubleOrNull()
                err = when { name.isBlank() -> "اكتب الاسم."; p == null || p < 0 -> "اكتب سعر صحيح."; else -> "" }
                if (err.isBlank()) { started = true; vm.saveCatalogItem(e, kind.id, name, Math.round(p!! * 100) / 100.0, desc, section, available, image) }
            }, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = kind.color), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (saving) "جاري الحفظ…" else "حفظ", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemPayDialog(vm: MerchantViewModel, startKind: ItemKind, balance: Long, onDismiss: () -> Unit) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val items by vm.myItems.collectAsStateWithLifecycle()
    val passes by vm.myPasses.collectAsStateWithLifecycle()
    val kinds = ItemKind.entries.filter { cfg.price(it.id).active }
    var scope by remember { mutableStateOf(if (cfg.price("all").active) "all" else "kind") }
    var kind by remember { mutableStateOf(if (cfg.price(startKind.id).active) startKind else kinds.firstOrNull() ?: startKind) }
    var days by remember { mutableStateOf(30) }
    val now = System.currentTimeMillis()
    val sel = remember(kind) { mutableStateListOf<String>().apply { addAll(items.filter { it.kind == kind.id && it.approved && cfg.paidUntil(it, passes, now) <= now }.map { it.id }) } }
    var confirm by remember { mutableStateOf(false) }
    val n = if (scope == "all") 1 else sel.size
    val ph = cfg.price(if (scope == "all") "all" else kind.id).priceH
    val cost = if (n > 0) CatalogConfig.passCost(ph, n, days) else 0L
    FullDialog("📢 ظهور منتجاتك", onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("رصيدك: $balance نقطة", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (cfg.price("all").active) FilterChip(selected = scope == "all", onClick = { scope = "all" }, label = { Text("📦 باقة كل المنتجات (${CatalogConfig.riyal(cfg.price("all").priceH)}/يوم)") })
                if (kinds.isNotEmpty()) FilterChip(selected = scope == "kind", onClick = { scope = "kind" }, label = { Text("✔ منتجات محددة") })
            }
            if (scope == "kind") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text("${k.icon} ${k.label} (${CatalogConfig.riyal(cfg.price(k.id).priceH)}/يوم)") }) }
                }
                val list = items.filter { it.kind == kind.id && it.approved }
                if (list.isEmpty()) Text("ما فيه ${kind.label} معتمد للحين", color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEach { it ->
                    val pu = cfg.paidUntil(it, passes, now)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = it.id in sel, onCheckedChange = { c -> if (c) sel.add(it.id) else sel.remove(it.id) })
                        Text(it.name + if (pu > now) "  (ظاهر لين ${dm(pu)})" else "", fontSize = 14.sp)
                    }
                }
            }
            Text("المدة", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "يوم", 3 to "3 أيام", 7 to "7 أيام", 14 to "14 يوم", 30 to "شهر", 60 to "شهرين", 90 to "3 شهور", 180 to "6 شهور", 365 to "سنة").forEach { (d, l) ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF4E0), modifier = Modifier.fillMaxWidth()) {
                Text("💰 التكلفة: $cost نقطة" + if (scope == "kind") "  ($n × $days يوم × ${CatalogConfig.riyal(ph)})" else "  ($days يوم × ${CatalogConfig.riyal(ph)})", color = Color(0xFF8A5A00), fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            }
            Button(onClick = { confirm = true }, enabled = cost > 0, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("ادفع $cost نقطة", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("تأكيد الدفع") },
        text = { Text("$cost نقطة؟ رصيدك بعدها: ${balance - cost}") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.buyItemPass(scope, kind.id, sel.toList(), days); onDismiss() }, enabled = cost <= balance) { Text(if (cost <= balance) "تأكيد" else "رصيدك ما يكفي", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } }
    )
}
