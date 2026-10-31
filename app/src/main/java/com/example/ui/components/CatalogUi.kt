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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
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
import com.example.ui.i18n.Surface
import androidx.compose.material3.Switch
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
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
            // نترك مسافة شريط الحالة وشريط التنقل والكيبورد (كان زر الإرسال ينقص تحت)
            Column(Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)) {
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

@Composable
private fun OrdersShell(embedded: Boolean, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (embedded) Box(Modifier.fillMaxSize()) { content() } else FullDialog(title, onDismiss, content)
}

// =================== الزبون: صفحة المتجر ===================

/** المنيو/المنتجات/الخدمات داخل صفحة المتجر + السلة */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreCatalogSection(store: StoreProfile, vm: MerchantViewModel) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val carts by vm.carts.collectAsStateWithLifecycle()
    val allDeals by vm.storeDeals.collectAsStateWithLifecycle()
    var items by remember(store.uid) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var checkout by remember { mutableStateOf(false) }
    LaunchedEffect(store.uid, cfg.enabled) { items = vm.loadStoreCatalog(store.uid) }
    val deals = allDeals[store.uid] ?: com.example.data.model.StoreDeals()
    val packs = if (cfg.orderEnabled) deals.packs else emptyList()
    if (!cfg.enabled || (items.isEmpty() && packs.isEmpty())) return
    val cart = carts[store.uid].orEmpty()
    val base = cfg.baseKind(store.category)
    LaunchedEffect(store.uid, cfg.orderEnabled) { if (cfg.orderEnabled) vm.refreshStorePause(store.uid) }
    val pausedMap by vm.pausedStores.collectAsStateWithLifecycle()
    val preMap by vm.preCfgs.collectAsStateWithLifecycle()
    val closedMsg = if (cfg.orderEnabled) vm.orderBlock(store, null, pausedMap[store.uid] == true) else null
    val pre = closedMsg != null && pausedMap[store.uid] != true && preMap[store.uid]?.active == true && vm.preOn(store.uid)
    val canOrder = cfg.orderEnabled && (closedMsg == null || pre)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (closedMsg != null) ClosedBanner(closedMsg + if (pre) "\n📅 تقدر تطلب الحين بموعد لاحق" else "")
        // 🔥 شريط العروض الشغّالة
        if (deals.banners.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            deals.banners.forEach { b -> Surface(shape = RoundedCornerShape(50), color = Color(0xFFFFF1E3), border = BorderStroke(1.dp, Color(0xFFF8C9A0))) { Text(b, color = Color(0xFFC2410C), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)) } }
        }
        // 🎁 عروض تقدر تطلبها (باقات / إعلان قابل للطلب)
        if (packs.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF7E6), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("🎁 عروض تقدر تطلبها (${packs.size})", color = Color(0xFFB45309), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
            packs.forEach { p ->
                val q = cart[p.id]?.qty ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF7E6)), contentAlignment = Alignment.Center) {
                        if (p.imageUrl.isNotBlank()) AsyncImage(model = CloudinaryUrls.image(p.imageUrl, 240), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text("🎁", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (p.desc.isNotBlank()) androidx.compose.material3.Text(p.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        PriceLine(p.price, p.was, 0, "", Color(0xFFB45309))
                    }
                    if (!p.available) Column(horizontalAlignment = Alignment.End) {
                        Text("❌ نفد اليوم", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val restock by vm.restockIds.collectAsStateWithLifecycle()
                        val on = p.offerId in restock
                        TextButton(onClick = { vm.toggleRestockId(p.offerId) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(if (on) "🔔 بنذكّرك ✓" else "🔔 ذكّرني", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else if (!canOrder) Unit
                    else if (q == 0) OutlinedButton(onClick = { vm.cartAddPack(store.uid, p, 1) }, border = BorderStroke(1.dp, Color(0xFFB45309)), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("＋ أضف", color = Color(0xFFB45309), fontWeight = FontWeight.Bold) }
                    else QtyControl(q, Color(0xFFB45309), onMinus = { vm.cartAddPack(store.uid, p, -1) }, onPlus = { vm.cartAddPack(store.uid, p, 1) })
                }
            }
        }
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
                            androidx.compose.material3.Text(it.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (it.desc.isNotBlank()) androidx.compose.material3.Text(it.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val (dp, was, pct) = deals.price(it)
                            PriceLine(dp, was, pct, deals.bogo[it.id]?.second.orEmpty(), k.color)
                        }
                        when {
                            !it.available -> Text("نفد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            !canOrder -> Unit
                            q == 0 -> OutlinedButton(onClick = { vm.cartAdd(store.uid, it, 1) }, border = BorderStroke(1.dp, k.color), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("＋ أضف", color = k.color, fontWeight = FontWeight.Bold) }
                            else -> QtyControl(q, k.color, onMinus = { vm.cartAdd(store.uid, it, -1) }, onPlus = { vm.cartAdd(store.uid, it, 1) })
                        }
                    }
                }
            }
        }
        if (canOrder && cart.isNotEmpty()) {
            Button(
                onClick = { checkout = true }, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(top = 6.dp)
            ) { Text("🧾 سلتك: ${cart.values.sumOf { it.qty }} • ${money(deals.totals(cart.values.toList()).sub)} ر.س — ${if (pre) "📅 اطلب مسبقاً" else "اطلب"}", fontWeight = FontWeight.ExtraBold) }
        }
    }
    if (checkout) CheckoutDialog(store, vm, onDismiss = { checkout = false })
}

/** السعر بعد الخصم + القديم مشطوب + شارة النسبة / 1+1 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceLine(price: Double, was: Double, pct: Int, bogo: String, color: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        Text("${money(price)} ر.س", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        if (was > price) Text(money(was), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
        if (pct > 0) Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFDECEC)) { Text("−$pct%", color = Color(0xFFB42318), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp)) }
        if (bogo.isNotBlank()) Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEDE9FE)) { Text(bogo, color = Color(0xFF6D28D9), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp)) }
    }
}

/** طلب مباشر لعرض من شاشة العرض (المتجر ما عنده منيو مفتوح) */
@Composable
fun DirectCheckoutHost(vm: MerchantViewModel) {
    val st by vm.directCheckout.collectAsStateWithLifecycle()
    st?.let { CheckoutDialog(it, vm, onDismiss = { vm.directCheckout.value = null }) }
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
    val allDeals by vm.storeDeals.collectAsStateWithLifecycle()
    val tot = (allDeals[store.uid] ?: com.example.data.model.StoreDeals()).totals(carts[store.uid].orEmpty().values.toList())
    val lines = tot.lines
    var mode by remember { mutableStateOf("pickup") }
    val pays = listOfNotNull(if (store.payCash) "cash" else null, if (store.payCard) "card" else null).ifEmpty { listOf("cash") }
    var pay by remember { mutableStateOf(pays.first()) }
    var car by remember { mutableStateOf("") }
    var branchId by remember { mutableStateOf(vm.defaultBranch(store)?.id.orEmpty()) }
    var pickBranch by remember { mutableStateOf(false) }
    LaunchedEffect(store.uid) { vm.refreshStorePause(store.uid); vm.loadSlotCounts(store.uid) }
    val paused by vm.pausedStores.collectAsStateWithLifecycle()
    val preMap by vm.preCfgs.collectAsStateWithLifecycle()
    val slotMap by vm.slotCounts.collectAsStateWithLifecycle()
    val isPaused = paused[store.uid] == true
    val closedMsg = vm.orderBlock(store, branchId.ifBlank { null }, isPaused)
    // 📅 اطلب مسبقاً
    val pc = preMap[store.uid] ?: com.example.data.model.PreorderCfg()
    val pre = !isPaused && pc.active && vm.preOn(store.uid)
    val closedNow = closedMsg != null && !isPaused
    var whenLater by remember { mutableStateOf(false) }
    val later = pre && (whenLater || closedNow)
    var day by remember { mutableStateOf(0L) }
    var slot by remember { mutableStateOf(0L) }
    var name by remember { mutableStateOf(userName) }
    var phone by remember { mutableStateOf(userPhone.let { p -> val d = p.filter { it.isDigit() }; if (d.startsWith("966")) "0" + d.drop(3) else d }) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(lines.isEmpty()) { if (lines.isEmpty()) onDismiss() }
    val sub = tot.sub
    val freeDel = store.deliveryFee <= 0 || (store.deliveryFreeOver > 0 && sub >= store.deliveryFreeOver)
    val fee = if (mode == "delivery" && !freeDel) store.deliveryFee else 0.0
    val total = sub + fee
    val vatNo = if (cfg.vatEnabled && CatalogConfig.validVat(store.vatNumber)) store.vatNumber else ""
    val vat = total * cfg.vatRate / (100 + cfg.vatRate)
    val minOk = mode != "delivery" || store.deliveryMinOrder <= 0 || sub >= store.deliveryMinOrder
    val br0 = store.branches.firstOrNull { it.id == branchId } ?: store.branches.firstOrNull()
    val modeOkLater = if (mode == "delivery") pc.delivery else pc.pickup
    val preDays = if (later) com.example.data.model.PreOrder.days(pc) else emptyList()
    if (later && day !in preDays) day = preDays.firstOrNull { com.example.data.model.PreOrder.times(pc, br0, mode, it).isNotEmpty() } ?: preDays.firstOrNull() ?: 0L
    val preTimes = if (later && modeOkLater) com.example.data.model.PreOrder.times(pc, br0, mode, day) else emptyList()
    val isFull = { x: com.example.data.model.PreSlot -> mode == "delivery" && (slotMap[store.uid]?.get(x.key) ?: 0) >= pc.slotCap }
    if (slot != 0L && preTimes.none { it.t == slot && !isFull(it) }) slot = 0L
    val blocked = isPaused || (closedNow && !later)
    LaunchedEffect(Unit) { vm.loadMyStats() }
    val myStats by vm.myStats.collectAsStateWithLifecycle()
    val blockedTill = if (cfg.feat("noshow")) cfg.noshow.blockedUntil(myStats, System.currentTimeMillis()) else 0L
    FullDialog("🧾 طلبك من ${store.name}", onDismiss) {
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 📍 الفرع: واضح أول الصفحة عشان الزبون يتأكد منه
            val brName = { b: com.example.data.model.Branch -> b.name.ifBlank { b.district.ifBlank { "الفرع" } } }
            val brMeta = { b: com.example.data.model.Branch -> listOfNotNull(vm.branchDistanceKm(b)?.let { d -> (if (d < 1) "${(d * 1000).toInt()} متر" else String.format(java.util.Locale.US, "%.1f كم", d)) + " منك" },
                when (com.example.data.model.OfferTime.isOpenNow(b)) { true -> "مفتوح"; false -> "مغلق"; else -> null }).joinToString(" • ") }
            br0?.let { b ->
                Surface(shape = RoundedCornerShape(16.dp), color = CalmSagePrimary.copy(alpha = 0.08f), border = BorderStroke(2.dp, CalmSagePrimary), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📍 طلبك من", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row { Text("فرع ", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = CalmSagePrimary); androidx.compose.material3.Text(brName(b), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = CalmSagePrimary) }
                            brMeta(b).takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (store.branches.size > 1) OutlinedButton(onClick = { pickBranch = !pickBranch }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(if (pickBranch) "إغلاق" else "تغيير", fontSize = 13.sp) }
                    }
                }
            }
            if (pickBranch) store.branches.forEach { b ->
                Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, if (b.id == branchId) CalmSagePrimary else MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { branchId = b.id; vm.branchPick.value = store.uid to b.id; pickBranch = false; slot = 0L }) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row { Text("فرع ", fontWeight = FontWeight.Bold); androidx.compose.material3.Text(brName(b), fontWeight = FontWeight.Bold) }
                        brMeta(b).takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            lines.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Text(l.name, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                            if (l.was > l.price) Text("  " + money(l.was), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                        }
                        if (l.bogo.isNotBlank()) Text("${l.bogo}" + if (l.off > 0) " −${money(l.off)}" else " (أضف واحد ثاني)", fontSize = 11.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Bold)
                    }
                    QtyControl(l.qty, CalmSagePrimary, { vm.cartLineAdd(store.uid, l.id, -1) }, { vm.cartLineAdd(store.uid, l.id, 1) })
                    Spacer(Modifier.width(10.dp)); Text(money(l.qty * l.price - l.off), fontWeight = FontWeight.Bold)
                }
            }
            if (tot.saved > 0) Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFDECEC), modifier = Modifier.fillMaxWidth()) {
                Text("🔥 وفّرت ${money(tot.saved)} ر.س بالعروض", color = Color(0xFFB42318), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            HorizontalDivider()
            Text("طريقة الاستلام", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (!store.delivery && !(store.curbside && cfg.feat("curbside"))) Text("🏃 الطلب استلام من المحل — المحل ما يوفر توصيل", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                @Composable fun modeCard(id: String, title: String, sub: String = "") = Surface(
                    shape = RoundedCornerShape(14.dp), color = if (mode == id) Color(0xFFE7F6EC) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (mode == id) 2.dp else 1.dp, if (mode == id) CalmSagePrimary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { mode = id }
                ) { Column(Modifier.padding(12.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp); if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                modeCard("pickup", "🏃 استلام من المحل")
                if (store.curbside && cfg.feat("curbside")) modeCard("curbside", "🚗 استلام من السيارة", "المحل يطلّع طلبك لسيارتك")
                if (store.delivery) modeCard("delivery",
                    if (store.deliveryFee <= 0) "🚚 توصيل مجاني" else "🚚 توصيل — ${money(store.deliveryFee)} ر.س" + if (store.deliveryFreeOver > 0) " (مجاني من ${money(store.deliveryFreeOver)})" else "",
                    if (store.deliveryMinOrder > 0) "أقل طلب ${money(store.deliveryMinOrder)} ر.س" + (if (sub < store.deliveryMinOrder) " — باقي ${money(store.deliveryMinOrder - sub)} ر.س عشان يصير متاح" else "") else "")
            }
            // 💡 كم باقي للتوصيل المجاني
            if (mode == "delivery" && !freeDel && store.deliveryFreeOver > 0 && cfg.feat("freeHint")) {
                val left = store.deliveryFreeOver - sub
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF8E6), border = BorderStroke(1.dp, Color(0xFFF2C27A)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💡 أضف ${money(left)} ر.س وصار التوصيل مجاني 🎉", color = Color(0xFF7A4F00), fontWeight = FontWeight.Bold)
                        androidx.compose.material3.LinearProgressIndicator(progress = { (sub / store.deliveryFreeOver).toFloat().coerceIn(0f, 1f) }, color = CalmSagePrimary, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                        Text("${money(sub)} من ${money(store.deliveryFreeOver)}", fontSize = 12.sp, color = Color(0xFF7A4F00))
                        TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) { Text("‹ تصفّح المنيو وكمّل", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (pre) {
                Text("⏰ متى تبي طلبك؟", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !later, enabled = !closedNow, onClick = { whenLater = false }, label = { Text("⚡ الحين" + if (closedNow) " (مغلق)" else "") })
                    FilterChip(selected = later, onClick = { whenLater = true }, label = { Text("📅 وقت لاحق") })
                }
                if (later) {
                    if (!modeOkLater) Text(if (mode == "delivery") "المحل ما يوفر توصيل بموعد — اختر الاستلام" else "المحل ما يوفر استلام بموعد — اختر التوصيل", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        if (preDays.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            preDays.forEach { d -> FilterChip(selected = d == day, onClick = { day = d; slot = 0L }, label = { Text(com.example.data.model.PreOrder.dayLabel(d)) }) }
                        }
                        if (preTimes.isEmpty()) Text("ما فيه أوقات متاحة ${com.example.data.model.PreOrder.dayLabel(day)}" + if (preDays.size > 1) " — جرّب يوم ثاني" else "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            preTimes.forEach { x ->
                                val full = isFull(x)
                                FilterChip(selected = x.t == slot, enabled = !full, onClick = { slot = x.t },
                                    label = { Text(com.example.data.model.PreOrder.timeOf(x.t) + (if (x.end > 0) " – ${com.example.data.model.PreOrder.timeOf(x.end)}" else "") + if (full) " (ممتلئة)" else "", fontSize = 12.sp) })
                            }
                        }
                    }
                }
            }
            if (mode == "curbside") OutlinedTextField(car, { car = it.take(60) }, label = { Text("🚗 وصف سيارتك (عشان المحل يعرفك)") }, placeholder = { Text("مثال: كامري بيضاء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (cfg.feat("payChoice")) Text("💳 الدفع عند الاستلام", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (!cfg.feat("payChoice")) Unit else if (pays.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = pay == "cash", onClick = { pay = "cash" }, label = { Text("💵 كاش") })
                FilterChip(selected = pay == "card", onClick = { pay = "card" }, label = { Text("💳 بطاقة / شبكة") })
            } else Text(if (pays.first() == "card") "💳 بطاقة / شبكة عند الاستلام (المحل يقبلها بس)" else "💵 كاش عند الاستلام (المحل يقبله بس)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode == "delivery") Text(if (loc != null) "📍 بنرسل موقعك الحالي للمحل" else "📍 فعّل الموقع عشان نرسل مكانك للتوصيل", fontSize = 13.sp, color = if (loc != null) CalmSagePrimary else MaterialTheme.colorScheme.error)
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("اسمك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("جوالك") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it.take(300) }, label = { Text("ملاحظة (اختياري)") }, placeholder = { Text("مثال: بدون بصل") }, modifier = Modifier.fillMaxWidth())
            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    @Composable fun line(a: String, b: String, bold: Boolean = false) = Row { Text(a, Modifier.weight(1f), fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal); Text(b, fontWeight = FontWeight.Bold) }
                    if (fee > 0) { line("المجموع", money(sub)); line("🚚 التوصيل", money(fee)) } else if (mode == "delivery") line("🚚 التوصيل", "مجاني 🎉")
                    if (vatNo.isNotBlank()) { line("المبلغ قبل الضريبة", money(total - vat)); line("ضريبة القيمة المضافة ${cfg.vatRate.toString().removeSuffix(".0")}%", money(vat)) }
                    line("💰 الإجمالي" + if (vatNo.isNotBlank()) " (شامل الضريبة)" else "", "${money(total)} ر.س", true)
                }
            }
            if (!minOk) Text("أقل طلب للتوصيل ${money(store.deliveryMinOrder)} ر.س — أضف ${money(store.deliveryMinOrder - sub)} ر.س أو اختر الاستلام", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (blocked && closedMsg != null) ClosedBanner(closedMsg)
            else if (closedNow) Text("🌙 المحل مغلق الحين — طلبك يوصله ويتجهّز على موعدك", fontSize = 13.sp, color = Color(0xFF10318F), fontWeight = FontWeight.Bold)
            if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (blockedTill > 0) Text("🚫 الطلب موقف عن حسابك مؤقتاً لين ${dm(blockedTill)} بسبب طلبات سابقة ما انستلمت. لو فيه خطأ تواصل معنا.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        // زر الإرسال ثابت تحت دايم
        Surface(shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Button(
                onClick = {
                    err = when {
                        name.isBlank() -> "اكتب اسمك."
                        !(phone.startsWith("05") && phone.length == 10) -> "اكتب رقم جوال صحيح مثل 05xxxxxxxx."
                        mode == "delivery" && loc == null -> "فعّل الموقع عشان التوصيل."
                        mode == "curbside" && car.isBlank() -> "اكتب وصف سيارتك (مثال: كامري بيضاء)."
                        later && !modeOkLater -> if (mode == "delivery") "المحل ما يوفر توصيل بموعد — اختر الاستلام." else "المحل ما يوفر استلام بموعد."
                        later && slot == 0L -> "اختر وقت طلبك."
                        else -> ""
                    }
                    if (err.isBlank()) vm.sendOrder(store, store.branches.firstOrNull { it.id == branchId }, mode, name, "+966" + phone.drop(1), note, if (cfg.feat("payChoice")) pay else "", car, if (later) slot else 0L) { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        onDismiss()
                    }
                },
                enabled = minOk && !blocked && blockedTill == 0L, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) { Text((if (later) "📅 أرسل الطلب المسبق" else "📨 أرسل الطلب") + if (store.branches.size > 1 && br0 != null) " لفرع ${br0.name.ifBlank { br0.district.ifBlank { "الفرع" } }}" else " على واتساب", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
          }
        }
      }
    }
}

// =================== الطلبات ===================

@Composable
private fun OrderStepper(o: HayynaOrder) {
    if (o.status == "cancelled" || o.status == "noshow") return
    val steps = o.steps
    val at = o.stepIndex
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        steps.forEachIndexed { i, (_, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(if (i == at) 16.dp else 12.dp).clip(CircleShape).background(if (i <= at) CalmSagePrimary else MaterialTheme.colorScheme.outlineVariant))
                Text(label, fontSize = 11.sp, fontWeight = if (i <= at) FontWeight.Bold else FontWeight.Normal, color = if (i <= at) CalmSagePrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun waOf(phone: String) = phone.filter { it.isDigit() }.let { d -> when { d.startsWith("05") && d.length == 10 -> "966" + d.drop(1); d.startsWith("5") && d.length == 9 -> "966$d"; else -> d } }

@Composable
private fun Note(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = Modifier.fillMaxWidth()) { Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) }
}

/** 🛍️ جاهز للاستلام: ملاحظة للعميل (وين يستلم) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyNoteDialog(o: HayynaOrder, vm: MerchantViewModel, onDismiss: () -> Unit) {
    val recent by vm.readyNotes.collectAsStateWithLifecycle()
    var note by remember(o.id) { mutableStateOf("") }
    val picks = (recent + listOf("عند خدمة العملاء", "عند الكاشير", "كاشير رقم 1", "كاشير رقم 2", "كاشير رقم 3", if (o.isCurbside) "بنطلّعه لسيارتك" else "عند الباب")).distinct().take(9)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("🛍️ طلب #${o.code} جاهز") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تبي تكتب للعميل وين يستلم؟ (اختياري)", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { picks.forEach { p -> FilterChip(selected = note == p, onClick = { note = p }, label = { Text(p, fontSize = 12.sp) }) } }
                OutlinedTextField(note, { note = it.take(80) }, placeholder = { Text("مثال: لدى كاشير رقم 3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { vm.markReady(o, note); onDismiss() }) { Text("✅ جاهز — بلّغ العميل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } })
}

/** 📅 إعدادات الطلب المسبق (صاحب المحل) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreorderSettingsDialog(vm: MerchantViewModel, onDismiss: () -> Unit) {
    val cur by vm.myPre.collectAsStateWithLifecycle()
    val profile by vm.merchantProfile.collectAsStateWithLifecycle()
    val hasDelivery = profile?.delivery == true
    var c by remember { mutableStateOf(cur) }
    var daysTxt by remember { mutableStateOf(if (cur.maxDays > 0) cur.maxDays.toString() else "2") }
    var capTxt by remember { mutableStateOf(cur.slotCap.toString()) }
    var err by remember { mutableStateOf("") }
    val digits = { t: String -> t.map { ch -> if (ch in '٠'..'٩') '0' + (ch - '٠') else ch }.filter { it.isDigit() }.joinToString("") }
    FullDialog("📅 الطلب المسبق", onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الزبون يطلب الحين ويختار موعد لاحق — حتى لو المحل مغلق.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("تفعيل «اطلب مسبقاً»", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Switch(c.on, { c = c.copy(on = it) }) }
            if (c.on) {
                Text("أبعد موعد يقدر الزبون يختاره", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = c.maxDays == 0, onClick = { c = c.copy(maxDays = 0) }, label = { Text("نفس اليوم بس") })
                    FilterChip(selected = c.maxDays > 0, onClick = { c = c.copy(maxDays = (digits(daysTxt).toIntOrNull() ?: 2).coerceIn(1, 14)) }, label = { Text("عدد أيام") })
                }
                if (c.maxDays > 0) OutlinedTextField(daysTxt, { daysTxt = it.take(2); c = c.copy(maxDays = (digits(it).toIntOrNull() ?: 1).coerceIn(1, 14)) },
                    label = { Text("كم يوم قدام (لين 14)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("⏱️ وقت التجهيز (أقرب موعد بعد كم؟)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(15, 30, 45, 60, 90, 120) + c.prepMin).distinct().forEach { m -> FilterChip(selected = c.prepMin == m, onClick = { c = c.copy(prepMin = m) }, label = { Text("$m دقيقة") }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("🏃 استلام بموعد", fontWeight = FontWeight.Bold); Text("الزبون يختار وقت داخل الدوام كل ربع ساعة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(c.pickup, { c = c.copy(pickup = it) }) }
                if (hasDelivery) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("🚚 توصيل بموعد", fontWeight = FontWeight.Bold); Text("فترات تحددها أنت", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(c.delivery, { c = c.copy(delivery = it) }) }
                    if (c.delivery) Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("وقت التوصيل", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = c.delFrom < 0, onClick = { c = c.copy(delFrom = -1, delTo = -1) }, label = { Text("حسب الدوام") })
                                FilterChip(selected = c.delFrom >= 0, onClick = { if (c.delFrom < 0) c = c.copy(delFrom = 16 * 60, delTo = 23 * 60) }, label = { Text("أحدده") })
                            }
                            if (c.delFrom >= 0) {
                                MinuteStepper("من", c.delFrom) { c = c.copy(delFrom = it) }
                                MinuteStepper("لين", c.delTo) { c = c.copy(delTo = it) }
                            }
                            Text("طول الفترة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30 to "نص ساعة", 60 to "ساعة", 120 to "ساعتين").forEach { (m, l) -> FilterChip(selected = c.slotMin == m, onClick = { c = c.copy(slotMin = m) }, label = { Text(l) }) } }
                            OutlinedTextField(capTxt, { capTxt = it.take(2); c = c.copy(slotCap = (digits(it).toIntOrNull() ?: 1).coerceIn(1, 50)) },
                                label = { Text("الحد الأقصى للطلبات في كل فترة") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Button(onClick = {
                val out = c.copy(delivery = hasDelivery && c.delivery)
                if (out.on && !out.pickup && !out.delivery) { err = "فعّل الاستلام أو التوصيل بموعد."; return@Button }
                vm.saveMyPre(out, onDismiss)
            }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("حفظ ✓", fontWeight = FontWeight.ExtraBold) }
        }
    }
}

/** اختيار وقت بخطوات نص ساعة */
@Composable
private fun MinuteStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        OutlinedButton(onClick = { onChange((value - 30 + 1440) % 1440) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
        Text(com.example.data.model.OfferTime.formatMinute(value), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = { onChange((value + 30) % 1440) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}

/** ⏸️🌙 تنبيه «المحل مغلق / موقف الطلبات» */
@Composable
fun ClosedBanner(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF4DA), border = BorderStroke(1.5.dp, Color(0xFFF4B400)), modifier = modifier.fillMaxWidth()) {
        Text(text, color = Color(0xFF8A5A00), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(12.dp))
    }
}

/** ⏸️ زر إيقاف استقبال الطلبات (صاحب المحل + موظف الطلبات) */
@Composable
fun PauseOrdersRow(vm: MerchantViewModel) {
    val paused by vm.myStorePaused.collectAsStateWithLifecycle()
    Surface(shape = RoundedCornerShape(14.dp), color = if (paused) Color(0xFFFDE8E6) else Color(0xFFE7F6EC), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (paused) "⏸️ الطلبات موقفة — الزبائن ما يقدرون يطلبون" else "🟢 تستقبل طلبات", Modifier.weight(1f), color = if (paused) Color(0xFFB42318) else Color(0xFF15803D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (paused) Button(onClick = { vm.toggleStorePause() }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("▶️ رجّع الاستقبال", fontSize = 13.sp) }
            else OutlinedButton(onClick = { vm.toggleStorePause() }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("⏸️ أوقف الطلبات", fontSize = 13.sp) }
        }
    }
}

private const val REMIND_MIN = 30

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersDialog(
    title: String, orders: List<HayynaOrder>, asStore: Boolean, vm: MerchantViewModel,
    onDismiss: () -> Unit, rated: Set<String> = emptySet(), onRate: ((HayynaOrder) -> Unit)? = null, storePhone: (String) -> String = { "" },
    onArrive: ((HayynaOrder) -> Unit)? = null,
    // داخل شاشة (موظف) بدل نافذة
    embedded: Boolean = false
) {
    val context = LocalContext.current
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val staffMe by vm.staffMe.collectAsStateWithLifecycle()
    val stats by vm.custStats.collectAsStateWithLifecycle()
    var etaFor by remember { mutableStateOf<HayynaOrder?>(null) }
    var cancelFor by remember { mutableStateOf<HayynaOrder?>(null) }      // المحل يلغي بسبب / الزبون يلغي طلب مرسل
    var askFor by remember { mutableStateOf<HayynaOrder?>(null) }         // الزبون: طلب إلغاء
    var noshowFor by remember { mutableStateOf<HayynaOrder?>(null) }
    var curbFor by remember { mutableStateOf<HayynaOrder?>(null) }
    var readyFor by remember { mutableStateOf<HayynaOrder?>(null) }
    var preSettings by remember { mutableStateOf(false) }
    val myPre by vm.myPre.collectAsStateWithLifecycle()
    // نحدّث «جاهز من X دقيقة» كل دقيقة
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val ns = cfg.feat("noshow") && staffMe == null // «ما انستلم» لصاحب المحل بس
    val open = { u: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }
    OrdersShell(embedded, title, onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (asStore) PauseOrdersRow(vm)
            if (asStore && cfg.feat("preorder") && staffMe == null) Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFC9D3F2)), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable { preSettings = true }) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📅 الطلب المسبق: ", fontSize = 13.sp, color = Color(0xFF10318F)); Text(if (myPre.on) "مفعّل" else "مقفل", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10318F))
                    Spacer(Modifier.weight(1f)); Text("إعدادات ‹", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (orders.isEmpty()) Text(if (asStore) "ما وصلتك طلبات للحين" else "ما طلبت شي للحين — ادخل صفحة أي متجر واطلب منه 🛍️", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val card: @Composable (HayynaOrder) -> Unit = { o ->
                val (bg, line) = orderTint(o.status)
                Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(if (asStore && o.status == "sent") 2.dp else 1.dp, if (asStore && o.status == "sent") CalmSagePrimary else line ?: MaterialTheme.colorScheme.outlineVariant), color = bg ?: MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row { Text("#${o.code}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text(if (asStore) HayynaOrder.STATUS[o.status] ?: o.status else o.statusText(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CalmSagePrimary) }
                        Text((if (asStore) listOf(o.customerName, o.customerPhone.let { p -> val d = p.filter { it.isDigit() }; if (d.startsWith("966")) "0" + d.drop(3) else p }).filter { it.isNotBlank() }.joinToString(" • ") else "🏪 ${o.storeName}") + if (o.createdAtMillis > 0) " • ${dm(o.createdAtMillis)}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val nsc = if (asStore && ns && o.isLive) cfg.noshow.recentCount(stats[o.customerUid], now) else 0
                        if (nsc > 0) Note("⚠️ هذا العميل عنده ${if (nsc == 1) "طلب" else "$nsc طلبات"} ما استلمها", Color(0xFFFEF3C7), Color(0xFF92400E))
                        if (o.isScheduled && o.isLive) Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFEEF2FF), modifier = Modifier.fillMaxWidth()) {
                            Text("📅 ${if (asStore) "الموعد" else "موعد طلبك"}: ${com.example.data.model.PreOrder.whenText(o.scheduledAtMillis, now)}", color = Color(0xFF10318F), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        if (asStore && o.readyNote.isNotBlank() && o.status == "ready") Text("📍 ${o.readyNote}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        OrderStepper(o)
                        o.etaText(now)?.takeIf { com.example.data.model.CatalogFlags.eta }?.let { Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFE7F6EC)) { Text(it, color = CalmSagePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) } }
                        Text(o.items.joinToString("، ") { "${it.qty} × ${it.name}" }, fontSize = 13.sp)
                        Text("${o.modeText} • ${money(o.total)} ر.س" + (if (o.payText.isNotBlank()) " • ${o.payText}" else "") + if (o.note.isNotBlank()) " • 📝 ${o.note}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (asStore && o.isCurbside && (o.car.isNotBlank() || o.spot.isNotBlank())) Text("🚗 ${o.car}" + if (o.spot.isNotBlank()) " • 📍 ${o.spot}" else "", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (asStore && o.isCurbside && o.arrived && o.isLive) Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF111827), modifier = Modifier.fillMaxWidth()) {
                            Text("🚗 العميل وصل عند المحل!" + if (o.spot.isNotBlank()) " — ${o.spot}" else "", color = Color(0xFFFFD166), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                        // 🚗 خانة الاستلام من السيارة (الزبون)
                        if (!asStore && o.isCurbside && o.isLive && o.status != "delivering") {
                            val hot = o.status == "ready" && !o.arrived
                            Surface(shape = RoundedCornerShape(12.dp), color = if (o.arrived) Color(0xFFE7F6EC) else if (hot) Color(0xFFFFF7E6) else Color(0xFFEEF6FF),
                                border = BorderStroke(if (hot) 2.dp else 1.dp, if (o.arrived) Color.Transparent else if (hot) Color(0xFFF5D58A) else Color(0xFFBFD7F7)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(when { o.arrived -> "🚗 بلّغنا المحل إنك وصلت — جايك 👍"; o.status == "ready" -> "🚗 طلبك جاهز! أول ما توقف عند المحل اضغط «وصلت»"; o.status == "received" -> "🚗 المحل يجهّز طلبك — لو وصلت بدري اضغط «وصلت»"; else -> "🚗 استلام من السيارة" },
                                        fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1F2937))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("سيارتك: ${o.car.ifBlank { "—" }}" + if (o.spot.isNotBlank()) " • 📍 ${o.spot}" else "", fontSize = 12.sp, color = Color(0xFF4B5563), modifier = Modifier.weight(1f))
                                        if (!o.arrived) TextButton(onClick = { curbFor = o }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("✎ عدّل", fontSize = 12.sp) }
                                    }
                                    if (!o.arrived && o.status in listOf("received", "ready") && onArrive != null)
                                        Button(onClick = { onArrive(o) }, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary), modifier = Modifier.fillMaxWidth()) { Text("🚗 وصلت", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        // 🙋 طلب الإلغاء
                        if (o.pendingCancelReq) {
                            if (asStore) Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF1E3), border = BorderStroke(1.dp, Color(0xFFF8C9A0)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("🙋 العميل يطلب الإلغاء: ${o.cancelReq}", color = Color(0xFF9A3412), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { vm.answerCancel(o, true) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("✅ وافق وألغِ", fontSize = 12.sp) }
                                        OutlinedButton(onClick = { vm.answerCancel(o, false) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("✕ ارفض (جاهز)", fontSize = 12.sp) }
                                    }
                                }
                            } else Note("⏳ أرسلت طلب إلغاء (${o.cancelReq}) — بانتظار رد المحل", Color(0xFFFFF1E3), Color(0xFF9A3412))
                        }
                        if (!asStore && o.cancelReqAns == "rejected" && o.isLive) Note("✕ المحل ما قدر يلغيه — الطلب جاهز أو تجهّز", Color(0xFFF3F4F6), Color(0xFF374151))
                        if (o.status == "cancelled" && o.cancelReason.isNotBlank()) Text((if (o.cancelBy == "store") "سبب إلغاء المحل: " else "سبب الإلغاء: ") + o.cancelReason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!asStore && o.status == "noshow") Text("المحل سجّل إن الطلب ما انستلم. لو فيه خطأ تواصل مع المحل.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // ⏰ تذكير المحل
                        val readyMin = if (o.status == "ready" && o.readyAtMillis > 0) ((now - o.readyAtMillis) / 60000).toInt() else 0
                        if (asStore && readyMin >= REMIND_MIN) Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFDECEC), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⏰ جاهز من $readyMin دقيقة وما انستلم", color = Color(0xFFB42318), fontWeight = FontWeight.Bold, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                val cw = waOf(o.customerPhone)
                                if (cw.isNotBlank()) TextButton(onClick = { open("https://wa.me/$cw?text=" + java.net.URLEncoder.encode("هلا ${o.customerName} 👋 طلبك #${o.code} من ${o.storeName} جاهز وينتظرك 🛍️", "UTF-8").replace("+", "%20")) }) { Text("💬 ذكّره", fontSize = 12.sp) }
                            }
                        }
                        if (!asStore && o.status in listOf("received", "ready", "delivering") && o.cancelReq.isBlank()) {
                            val wa = storePhone(o.storeId)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("المحل استلم طلبك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                if (wa.isNotBlank()) TextButton(onClick = { open("https://wa.me/$wa") }, contentPadding = PaddingValues(0.dp)) { Text("💬 تواصل مع المحل", fontSize = 12.sp) }
                            }
                        }
                        if (asStore && o.isDelivery && o.lat != null && o.lng != null) TextButton(onClick = { open("https://maps.google.com/?q=${o.lat},${o.lng}") }, contentPadding = PaddingValues(0.dp)) { Text("📍 موقع العميل", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        val next = if (asStore) when (o.status) {
                            "sent" -> listOf("received" to "👍 استلمته", "cancelled" to "✕ إلغاء")
                            "received" -> listOf((if (o.isDelivery) "delivering" to "🚚 طلع للتوصيل" else "ready" to "🛍️ جاهز للاستلام"), "cancelled" to "✕ إلغاء") + if (ns) listOf("noshow" to "🚫 ما انستلم") else emptyList()
                            "ready" -> listOf("done" to if (o.isCurbside) "✅ سلّمته" else "✅ تم الاستلام", "cancelled" to "✕ إلغاء") + if (ns) listOf("noshow" to "🚫 ما انستلم") else emptyList()
                            "delivering" -> listOf("done" to "✅ تم التوصيل", "cancelled" to "✕ إلغاء") + if (ns) listOf("noshow" to "🚫 ما انستلم") else emptyList()
                            else -> emptyList()
                        } else when {
                            o.status == "sent" -> listOf("cancelled" to "✕ إلغاء الطلب")
                            o.status in listOf("received", "ready", "delivering") && o.cancelReq.isBlank() -> listOf("ask" to "🙋 طلب إلغاء")
                            else -> emptyList()
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            next.forEach { (s, l) ->
                                OutlinedButton(onClick = {
                                    when (s) {
                                        "received" -> if (com.example.data.model.CatalogFlags.eta) etaFor = o else vm.setOrderStatus(o.id, "received")
                                        "cancelled" -> cancelFor = o
                                        "noshow" -> noshowFor = o
                                        "ask" -> askFor = o
                                        "ready" -> readyFor = o
                                        else -> vm.setOrderStatus(o.id, s)
                                    }
                                }, border = if (s == "noshow") BorderStroke(1.dp, Color(0xFFF5C2C2)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline), contentPadding = PaddingValues(horizontal = 10.dp)) {
                                    Text(l, fontSize = 12.sp, color = if (s == "noshow") Color(0xFFB42318) else Color.Unspecified)
                                }
                            }
                            if (!asStore && o.status == "done" && onRate != null) {
                                if (o.id in rated) Text("⭐ شكراً على تقييمك", fontSize = 12.sp, color = CalmSagePrimary, modifier = Modifier.padding(8.dp))
                                else OutlinedButton(onClick = { onRate(o) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("⭐ قيّم طلبك", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            if (!asStore) orders.take(100).forEach { card(it) }
            else ORDER_GROUPS.forEach { g ->
                val prepMs = myPre.prepMin * 60_000L
                val ahead = { o: HayynaOrder -> o.isScheduled && o.scheduledAtMillis - prepMs > now }
                val os = orders.filter { it.status in g.statuses && when (g.key) { "sched" -> ahead(it); "new", "prep" -> !ahead(it); else -> true } }
                    .let { l -> if (g.key == "sched") l.sortedBy { it.scheduledAtMillis } else l }.take(100)
                if (os.isNotEmpty()) {
                    var openG by rememberSaveable(g.key) { mutableStateOf(g.open) }
                    Surface(shape = RoundedCornerShape(12.dp), color = g.head, modifier = Modifier.fillMaxWidth().clickable { openG = !openG }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF1F2937))
                            Surface(shape = RoundedCornerShape(50), color = Color.White, modifier = Modifier.padding(start = 8.dp)) { Text("${os.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937), modifier = Modifier.padding(horizontal = 8.dp)) }
                            Spacer(Modifier.weight(1f))
                            Text(if (openG) "▾" else "◂", fontSize = 16.sp, color = Color(0xFF6B7280))
                        }
                    }
                    if (openG) os.forEach { card(it) }
                }
            }
        }
    }
    readyFor?.let { o -> ReadyNoteDialog(o, vm) { readyFor = null } }
    if (preSettings) PreorderSettingsDialog(vm) { preSettings = false }
    etaFor?.let { o ->
        AlertDialog(onDismissRequest = { etaFor = null }, title = { Text("👍 استلام الطلب #${o.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (o.isDelivery) "متى يطلع للتوصيل تقريباً؟" else "متى يكون جاهز للاستلام تقريباً؟")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 15, 20, 30, 45, 60).forEach { m -> FilterChip(selected = false, onClick = { vm.setOrderStatus(o.id, "received", m); etaFor = null }, label = { Text("$m دقيقة") }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setOrderStatus(o.id, "received"); etaFor = null }) { Text("بدون وقت") } },
            dismissButton = { TextButton(onClick = { etaFor = null }) { Text("رجوع") } })
    }
    cancelFor?.let { o ->
        if (!asStore) AlertDialog(onDismissRequest = { cancelFor = null }, title = { Text("إلغاء الطلب #${o.code}؟") },
            confirmButton = { TextButton(onClick = { vm.setOrderStatus(o.id, "cancelled", extra = mapOf("cancelBy" to "customer")); cancelFor = null }) { Text("نعم، إلغاء", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { cancelFor = null }) { Text("رجوع") } })
        else ReasonDialog("✕ إلغاء الطلب #${o.code}", "اختر السبب — يوصل للعميل في «طلباتي».", HayynaOrder.STORE_CANCEL_REASONS, 120, "✕ ألغِ الطلب",
            onDone = { r -> vm.storeCancel(o, r); cancelFor = null }, onDismiss = { cancelFor = null })
    }
    askFor?.let { o ->
        ReasonDialog("🙋 طلب إلغاء #${o.code}", "المحل استلم طلبك ويمكن بدأ يجهّزه، فالإلغاء يحتاج موافقته. وش السبب؟", HayynaOrder.CUST_CANCEL_REASONS, 200, "أرسل طلب الإلغاء",
            onDone = { r -> vm.requestCancel(o, r); askFor = null }, onDismiss = { askFor = null })
    }
    noshowFor?.let { o ->
        AlertDialog(onDismissRequest = { noshowFor = null }, title = { Text("🚫 العميل ما استلم #${o.code}؟") },
            text = { Text("بينحسب عليه، ولو تكرر ${cfg.noshow.max} مرات خلال ${cfg.noshow.days} يوم يتوقف عن الطلب ${cfg.noshow.blockDays} أيام.") },
            confirmButton = { TextButton(onClick = { vm.markNoshow(o); noshowFor = null }) { Text("نعم، ما انستلم", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { noshowFor = null }) { Text("رجوع") } })
    }
    curbFor?.let { o ->
        var car by remember(o.id) { mutableStateOf(o.car) }
        var spot by remember(o.id) { mutableStateOf(o.spot) }
        AlertDialog(onDismissRequest = { curbFor = null }, title = { Text("🚗 سيارتك") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(car, { car = it.take(60) }, label = { Text("وصف السيارة") }, placeholder = { Text("مثال: كامري بيضاء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(spot, { spot = it.take(60) }, label = { Text("وين واقف؟ (اختياري)") }, placeholder = { Text("مثال: عند الباب الجانبي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.updateCurb(o, car.trim(), spot.trim()); curbFor = null }) { Text("حفظ", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { curbFor = null }) { Text("رجوع") } })
    }
}

/** اختيار سبب (إلغاء المحل / طلب إلغاء الزبون) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonDialog(title: String, hint: String, reasons: List<String>, max: Int, action: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { reasons.forEach { r -> FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r, fontSize = 12.sp) }) } }
                OutlinedTextField(if (reason in reasons) "" else reason, { reason = it.take(max) }, placeholder = { Text("أو اكتب السبب") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (reason.isNotBlank()) onDone(reason.trim()) }, enabled = reason.isNotBlank()) { Text(action, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } })
}

/** طلباتي (الزبون) — تتحدّث لحظياً */
@Composable
fun MyOrdersDialog(vm: MerchantViewModel, onDismiss: () -> Unit) {
    val orders by vm.myOrders.collectAsStateWithLifecycle()
    val rated by vm.myRatingIds.collectAsStateWithLifecycle()
    val stores by vm.approvedStores.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var rate by remember { mutableStateOf<HayynaOrder?>(null) }
    OrdersDialog("🧾 طلباتي", orders, asStore = false, vm = vm, onDismiss = onDismiss,
        rated = rated, onRate = if (com.example.data.model.CatalogFlags.ratings) ({ rate = it }) else null,
        onArrive = { o -> vm.markArrived(o) { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } } },
        storePhone = { sid -> stores.firstOrNull { it.uid == sid }?.let { st -> (st.branches.firstOrNull()?.phone ?: st.phone).filter { it.isDigit() }.let { d -> if (d.startsWith("05") && d.length == 10) "966" + d.drop(1) else d } }.orEmpty() })
    rate?.let { o ->
        var stars by remember { mutableStateOf(5) }
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { rate = null }, title = { Text("⭐ قيّم طلبك من ${o.storeName}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row { (1..5).forEach { n -> Text(if (n <= stars) "★" else "☆", fontSize = 36.sp, color = Color(0xFFF5B301), modifier = Modifier.clickable { stars = n }.padding(3.dp)) } }
                    OutlinedTextField(text, { text = it.take(300) }, placeholder = { Text("كلمة عن تجربتك (اختياري)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.rateOrder(o, stars, text.trim()); rate = null }) { Text("أرسل", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { rate = null }) { Text("إلغاء") } })
    }
}

/** ملخص التقييمات في صفحة المتجر */
@Composable
fun StoreRatingsSummary(uid: String, vm: MerchantViewModel) {
    var list by remember(uid) { mutableStateOf<List<Triple<Int, String, String>>>(emptyList()) }
    LaunchedEffect(uid) { list = vm.storeRatings(uid) }
    if (list.isEmpty() || !com.example.data.model.CatalogFlags.ratings) return
    val avg = list.sumOf { it.first }.toDouble() / list.size
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("⭐ ${String.format(java.util.Locale.US, "%.1f", avg)}  (${list.size} ${if (list.size == 1) "تقييم" else "تقييمات"})", fontWeight = FontWeight.ExtraBold)
        list.filter { it.third.isNotBlank() }.take(3).forEach { (st, name, t) ->
            Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("${"★".repeat(st)}${"☆".repeat(5 - st)}  ${name.ifBlank { "زبون" }}", fontSize = 12.sp, color = Color(0xFFB7791F), fontWeight = FontWeight.Bold)
                    Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
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
    var quick by remember { mutableStateOf(false) }
    if (!cfg.enabled) return
    val base = cfg.baseKind(category)
    val now = System.currentTimeMillis()
    val newOrders = orders.count { it.status == "sent" }
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.any { it.adminNote.isNotBlank() }) Text("↩️ عندك ${items.count { it.adminNote.isNotBlank() }} صنف يحتاج تعديل — افتح «إدارة منتجاتي»", color = Color(0xFFB42318), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            if (items.isNotEmpty() && cfg.feat("quickDeal") && cfg.feat("dealDiscount")) TextButton(onClick = { quick = true }, modifier = Modifier.fillMaxWidth()) { Text("⚡ خصم سريع على المنيو", fontWeight = FontWeight.Bold) }
            if (cfg.orderEnabled && cfg.feat("sounds")) {
                val ctx = LocalContext.current
                var snd by remember { mutableStateOf(com.example.data.OrderSounds.isOn(ctx)) }
                TextButton(onClick = { snd = !snd; com.example.data.OrderSounds.setOn(ctx, snd); if (snd) com.example.data.OrderSounds.play(ctx, "new") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (snd) "🔔 صوت الطلبات: شغّال" else "🔕 صوت الطلبات: مكتوم", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (quick) QuickDealDialog(vm, items.filter { it.approved }, base) { quick = false }
    if (manage) CatalogManagerDialog(vm, base, balance) { manage = false }
    if (showOrders) OrdersDialog("🧾 الطلبات (${orders.size})", orders, asStore = true, vm = vm, onDismiss = { showOrders = false })
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
                    it.adminNote.isNotBlank() -> "↩️ يحتاج تعديل: ${it.adminNote} — اضغط ✎"
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
                            androidx.compose.material3.Text(it.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${money(it.price)} ر.س" + if (it.section.isNotBlank()) " • ${it.section}" else "", fontSize = 12.sp, color = kind.color, fontWeight = FontWeight.Bold)
                            Text(status, fontSize = 11.sp, color = if (it.adminNote.isNotBlank()) Color(0xFFB42318) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (it.adminNote.isNotBlank()) FontWeight.Bold else FontWeight.Normal)
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
    var cropSrc by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cropSrc = uri.toString() }
    cropSrc?.let { src -> ImageCropDialog(src, aspect = 1f, title = "قص الصورة", outWidth = 1000) { out -> if (out != null) image = out; cropSrc = null } }
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
            Row(verticalAlignment = Alignment.CenterVertically) { Text("متوفر الحين", modifier = Modifier.weight(1f)); HSwitch(checked = available, onCheckedChange = { available = it }) }
            if (!vm.catalogDirect) Text("⏳ بيظهر بعد مراجعة فريق لكوفر. وثّق متجرك عشان تنزل منتجاتك مباشرة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


/** مجموعات طلبات المحل: اللي فيها شغل مفتوحة، والمنتهية والملغية مطوية */
private class OrderGroup(val key: String, val title: String, val statuses: List<String>, val open: Boolean, val head: Color)
private val ORDER_GROUPS = listOf(
    OrderGroup("sched", "📅 مجدولة (موعدها لاحق)", listOf("sent", "received"), true, Color(0xFFEEF2FF)),
    OrderGroup("new", "🆕 الجديدة", listOf("sent"), true, Color(0xFFF4F5F2)),
    OrderGroup("prep", "👨‍🍳 جاري التجهيز", listOf("received"), true, Color(0xFFFFF1B8)),
    OrderGroup("ready", "🛍️ جاهز / 🚚 بالطريق", listOf("ready", "delivering"), true, Color(0xFFD6E8FF)),
    OrderGroup("done", "✅ المنتهية", listOf("done"), false, Color(0xFFD7F0DF)),
    OrderGroup("cancelled", "✕ الملغية / ما انستلمت", listOf("cancelled", "noshow"), false, Color(0xFFFADADA))
)
/** لون فاتح لبطاقة الطلب حسب حالته (null = الافتراضي) */
private fun orderTint(status: String): Pair<Color?, Color?> = when (status) {
    "received" -> Color(0xFFFFF8DB) to Color(0xFFF3DE8A)
    "ready", "delivering" -> Color(0xFFE8F2FF) to Color(0xFFBFD7F7)
    "done" -> Color(0xFFEAF7EE) to Color(0xFFBFE3CB)
    "cancelled", "noshow" -> Color(0xFFFDECEC) to Color(0xFFF5C2C2)
    else -> null to null
}


/** ⚡ الخصم السريع: نسبة على المنيو بدون ما يسوي عرض بصورة */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDealDialog(vm: MerchantViewModel, items: List<CatalogItem>, base: ItemKind, onDismiss: () -> Unit) {
    var cur by remember { mutableStateOf<com.example.data.model.QuickDeal?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("all") }
    var kind by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var days by remember { mutableStateOf(1) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        cur = vm.myQuickDeal()?.takeIf { it.endsAt > System.currentTimeMillis() }
        cur?.let { pct = it.pct.toString(); scope = it.scope; kind = it.kind; section = it.section; picked.addAll(it.itemIds) }
        loaded = true
    }
    FullDialog("⚡ خصم سريع على ${base.title}", onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!loaded) { androidx.compose.material3.CircularProgressIndicator(); return@Column }
            cur?.let { c -> Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE7F6EC)) { Text("شغّال الحين: ${c.pct}% لين ${dm(c.endsAt)}", color = CalmSagePrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp)) } }
            Text("الخصم ينطبق لحاله على أسعار المنيو في صفحة متجرك، والزبون يشوف السعر القديم مشطوب.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("نسبة الخصم %", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(10, 15, 20, 25, 30, 40, 50).forEach { n -> FilterChip(selected = pct == "$n", onClick = { pct = "$n" }, label = { Text("$n%") }) } }
            OutlinedTextField(pct, { v -> pct = v.filter { it.isDigit() }.take(2) }, label = { Text("أو اكتب نسبة (1 - 90)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text("على وش؟", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            DealScopePicker(items, scope, kind, section, picked, { scope = it }, { kind = it }, { section = it })
            Text("المدة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "اليوم بس", 3 to "3 أيام", 7 to "أسبوع", 14 to "أسبوعين", 30 to "شهر").forEach { (d, t) -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text(t) }) }
            }
            if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Button(onClick = {
                val p = pct.toIntOrNull()
                err = when {
                    p == null || p !in 1..90 -> "اكتب نسبة من 1 إلى 90."
                    scope == "kind" && kind.isBlank() -> "اختر النوع."
                    scope == "section" && section.isBlank() -> "اختر القسم."
                    scope == "items" && picked.isEmpty() -> "اختر صنف واحد على الأقل."
                    else -> ""
                }
                if (err.isNotBlank() || p == null) return@Button
                val end = if (days == 1) java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59) }.timeInMillis
                    else System.currentTimeMillis() + days * 86_400_000L
                vm.saveQuickDeal(com.example.data.model.QuickDeal(p, scope, if (scope == "kind") kind else "", if (scope == "section") section else "", if (scope == "items") picked.toList() else emptyList(), end), onDismiss)
            }, colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (cur != null) "⚡ حدّث الخصم" else "⚡ شغّل الخصم", fontWeight = FontWeight.ExtraBold)
            }
            if (cur != null) OutlinedButton(onClick = { vm.stopQuickDeal(onDismiss) }, modifier = Modifier.fillMaxWidth()) { Text("⏹️ أوقف الخصم", color = MaterialTheme.colorScheme.error) }
        }
    }
}
