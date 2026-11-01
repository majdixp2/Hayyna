package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.example.ui.i18n.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StoreStaff
import com.example.data.model.isSoldOut
import com.example.data.model.priceLabel
import com.example.ui.MerchantViewModel
import kotlinx.coroutines.delay

private val Navy = Color(0xFF10318F)
private fun digits(s: String, max: Int) = s.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.filter { it.isDigit() }.joinToString("").take(max)

// =================== شاشة الموظف (الطلبات + نفد اليوم بس) ===================
@Composable
fun StaffHomeScreen(vm: MerchantViewModel) {
    val me by vm.staffMe.collectAsStateWithLifecycle()
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    val orders by vm.storeOrders.collectAsStateWithLifecycle()
    val items by vm.myItems.collectAsStateWithLifecycle()
    val offers by vm.staffOffers.collectAsStateWithLifecycle()
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(5000); waited = true }
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }
    val m = me

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (m == null || !m.active || !cfg.feat("staff")) {
                Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (m == null && !waited) CircularProgressIndicator(color = Navy)
                    else {
                        Text("⏸️", fontSize = 48.sp)
                        Text(when { m == null -> "حسابك انحذف أو ما قدرنا نحمّله"; !m.active -> "حسابك موقوف"; else -> "دخول الموظفين موقف حالياً" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("تواصل مع صاحب المحل.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { vm.staffLogout() }) { Text("تسجيل الخروج") }
                }
                return@Column
            }
            // الشريط العلوي
            Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.storeName.ifBlank { "المتجر" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("👤 ${m.displayName} • ${m.roleLabel}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", color = Color.White, fontSize = 22.sp) }
                    HMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        HMenuItem("🔔", "تجربة صوت الطلبات") { menu = false; com.example.data.OrderSounds.play(vm.getApplication<android.app.Application>(), "new") }
                        HMenuItem("🔑", "غيّر رمزي السري") { menu = false; pinDialog = true }
                        HMenuDivider()
                        HMenuItem("🚪", "تسجيل الخروج", danger = true) { menu = false; vm.staffLogout() }
                    }
                }
            }
            if (m.canStock) TabRow(selectedTabIndex = tab, contentColor = Navy) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("🧾 الطلبات (${orders.count { it.isLive }})", fontWeight = FontWeight.Bold) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🍽️ نفد اليوم", fontWeight = FontWeight.Bold) })
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0 || !m.canStock) OrdersDialog("", orders, asStore = true, vm = vm, onDismiss = {}, embedded = true)
                else {
                    val sorted = items.sortedWith(compareBy({ it.section }, { it.slot }))
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("اضغط على اللي خلص — يتعلّم «نفد» وما ينطلب لين ترجّعه (العروض ترجع لحالها بكرة).", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp)) }
                        val now = System.currentTimeMillis()
                        val live = offers.filter { it.isApproved && !it.draft && (it.expiresAt?.toDate()?.time ?: Long.MAX_VALUE) > now }
                            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                        if (live.isNotEmpty()) item { Text("🔥 العروض", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp)) }
                        items(live, key = { "o_" + it.id }) { o ->
                            val out = o.isSoldOut(now)
                            Surface(
                                shape = RoundedCornerShape(14.dp), color = if (out) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.5.dp, if (out) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().clickable { vm.toggleOfferSoldOut(o) }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        androidx.compose.material3.Text(o.offerTitle, fontWeight = FontWeight.Bold, textDecoration = if (out) TextDecoration.LineThrough else null)
                                        Text(o.priceLabel(now) + if (out && o.notifyCount > 0) " • 🔔 ${o.notifyCount} ينتظرون" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(if (out) "❌ نفد اليوم" else "✅ متوفر", fontWeight = FontWeight.Bold, color = if (out) Color(0xFFB91C1C) else Color(0xFF15803D))
                                }
                            }
                        }
                        if (sorted.isNotEmpty()) item { Text("📋 المنيو", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp)) }
                        if (sorted.isEmpty() && live.isEmpty()) item { Text("ما فيه عروض ولا أصناف", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(sorted, key = { it.id }) { it ->
                            val out = !it.available
                            Surface(
                                shape = RoundedCornerShape(14.dp), color = if (out) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.5.dp, if (out) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().clickable { vm.toggleItemAvailable(it) }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        androidx.compose.material3.Text(it.name, fontWeight = FontWeight.Bold, textDecoration = if (out) TextDecoration.LineThrough else null)
                                        if (it.section.isNotBlank()) androidx.compose.material3.Text(it.section, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(if (out) "❌ نفد" else "✅ متوفر", fontWeight = FontWeight.Bold, color = if (out) Color(0xFFB91C1C) else Color(0xFF15803D))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
    if (pinDialog) PinChangeDialog(onDismiss = { pinDialog = false }) { pin -> vm.changeStaffPin(pin) { msg -> android.widget.Toast.makeText(vm.getApplication<android.app.Application>(), com.example.ui.i18n.I18n.tr(msg), android.widget.Toast.LENGTH_LONG).show() }; pinDialog = false }
}

@Composable
private fun PinFields(pin: String, onPin: (String) -> Unit, pin2: String, onPin2: (String) -> Unit) {
    OutlinedTextField(pin, { onPin(digits(it, 6)) }, label = { Text("الرمز السري (6 أرقام)") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(pin2, { onPin2(digits(it, 6)) }, label = { Text("أعد كتابة الرمز") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PinChangeDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("🔑 رمز سري جديد") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PinFields(pin, { pin = it }, pin2, { pin2 = it }) } },
        confirmButton = { TextButton(onClick = { onSave(pin) }, enabled = pin.length == 6 && pin == pin2) { Text("حفظ", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } })
}

// =================== دخول الموظف ===================
@Composable
fun StaffLoginDialog(vm: MerchantViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("hayyna_staff", Context.MODE_PRIVATE) }
    val busy by vm.isStaffLoggingIn.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf(prefs.getString("code", "").orEmpty()) }
    var n by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("👨‍🍳 دخول موظف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("البيانات يعطيك إياها صاحب المحل.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code, { code = digits(it, 5) }, label = { Text("رقم المتجر") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.4f))
                    OutlinedTextField(n, { n = digits(it, 2) }, label = { Text("رقم الموظف") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(pin, { pin = digits(it, 6) }, label = { Text("الرمز السري") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    err = null
                    val k = n.toIntOrNull() ?: 0
                    when {
                        !Regex("^[1-9][0-9]{4}$").matches(code) -> err = "رقم المتجر 5 أرقام"
                        k !in 1..50 -> err = "اكتب رقم الموظف"
                        pin.length != 6 -> err = "الرمز السري 6 أرقام"
                        else -> { prefs.edit().putString("code", code).apply(); vm.staffLogin(code, k, pin) { e -> err = e } }
                    }
                },
                enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("دخول", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } })
}

// =================== صاحب المحل: إدارة الموظفين ===================
/** بطاقة «موظفين المتجر» في لوحة التاجر */
@Composable
fun StaffCard(vm: MerchantViewModel, storeName: String) {
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    if (!cfg.feat("staff")) return
    var open by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { open = true }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("👥", fontSize = 24.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("موظفين المتجر", fontWeight = FontWeight.Bold)
                Text("العاملين يدخلون برقم ورمز سري ويشوفون الطلبات بس", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 22.sp, color = Navy)
        }
    }
    if (open) StaffManagerDialog(vm, storeName) { open = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffManagerDialog(vm: MerchantViewModel, storeName: String, onDismiss: () -> Unit) {
    val code by vm.storeCode.collectAsStateWithLifecycle()
    val list by vm.myStaff.collectAsStateWithLifecycle()
    val cfg by vm.catalogConfig.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadStoreCode() }
    var form by remember { mutableStateOf<Pair<Boolean, StoreStaff?>?>(null) } // (رمز جديد؟, الموظف)
    var actions by remember { mutableStateOf<StoreStaff?>(null) }
    var creds by remember { mutableStateOf<StoreStaff?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("👥 موظفين المتجر", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("✕", fontSize = 18.sp) }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Navy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("رقم المتجر", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            if (code == null) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text(code!!, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = 6.sp)
                            Text("يكتبه الموظف مع رقمه ورمزه السري عند «دخول موظف»", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                    if (list.isEmpty()) Text("ما أضفت موظفين للحين. أضف الكاشير أو مقدم الخدمة، وكل واحد يدخل برقمه ورمزه السري.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    list.forEach { m ->
                        Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().clickable { actions = m }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).background(Navy.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Text("${m.n}", color = Navy, fontWeight = FontWeight.ExtraBold) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    androidx.compose.material3.Text(m.displayName, fontWeight = FontWeight.Bold, color = if (m.active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(m.roleLabel + " • " + m.perms.joinToString(" • ") { if (it == "orders") "🧾 الطلبات" else "🍽️ نفد اليوم" } + if (m.active) "" else " • ⏸️ موقوف",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("إدارة ›", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Button(onClick = { form = true to null }, enabled = code != null, colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("➕ إضافة موظف", fontWeight = FontWeight.Bold) }
                    Text("🔒 الموظف ما يشوف العروض ولا الأسعار ولا الرصيد ولا صفحة المتجر. وتقدر توقفه بضغطة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    form?.let { (newPin, m) -> StaffFormDialog(vm, m, newPin, storeName) { form = null } }
    actions?.let { m ->
        AlertDialog(onDismissRequest = { actions = null }, title = { Text("👤 ${m.displayName} (رقم ${m.n})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { creds = m; actions = null }, modifier = Modifier.fillMaxWidth()) { Text("🔑 بيانات الدخول" + if (cfg.feat("staffQr")) " و QR" else "") }
                    Button(onClick = { form = false to m; actions = null }, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF10318F), contentColor = androidx.compose.ui.graphics.Color.White), modifier = Modifier.fillMaxWidth()) { Text("✎ تعديل الاسم والصلاحيات", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { form = true to m; actions = null }, modifier = Modifier.fillMaxWidth()) { Text("🔁 رمز سري جديد") }
                    OutlinedButton(onClick = { vm.setStaffActive(m, !m.active); actions = null }, modifier = Modifier.fillMaxWidth()) { Text(if (m.active) "⏸️ إيقاف مؤقت" else "▶️ تفعيل") }
                    OutlinedButton(onClick = { vm.deleteStaff(m); actions = null }, modifier = Modifier.fillMaxWidth()) { Text("🗑️ حذف الموظف", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { actions = null }) { Text("رجوع") } })
    }
    creds?.let { m -> StaffCredsDialog(code.orEmpty(), m, cfg.feat("staffQr")) { creds = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffFormDialog(vm: MerchantViewModel, old: StoreStaff?, withPin: Boolean, storeName: String, onDismiss: () -> Unit) {
    val saving by vm.isSavingStaff.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(old?.name.orEmpty()) }
    var role by remember { mutableStateOf(old?.role ?: "cashier") }
    var perms by remember { mutableStateOf((old?.perms ?: StoreStaff.ROLES["cashier"]!!.second).toSet()) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val pinOnly = old != null && withPin
    // 📍 فرعه (فاضي = كل الفروع)
    val storeBranches = remember { vm.merchantProfile.value?.branches.orEmpty().filter { it.id.isNotBlank() } }
    var brs by remember { mutableStateOf(old?.branchIds.orEmpty().toSet()) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(when { old == null -> "➕ موظف جديد"; pinOnly -> "🔁 رمز سري جديد — ${old.displayName}"; else -> "✎ تعديل ${old.displayName}" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pinOnly) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("الاسم (اختياري)") }, placeholder = { Text("مثال: أحمد — الكاشير") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("الدور", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoreStaff.ROLES.forEach { (k, r) -> FilterChip(selected = role == k, onClick = { role = k; perms = r.second.toSet() }, label = { Text(r.first) }) }
                    }
                    Text(StoreStaff.ROLES[role]?.third.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StoreStaff.PERMS.forEach { (k, l) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            HSwitch(checked = k in perms, onCheckedChange = { perms = if (it) perms + k else perms - k })
                        }
                    }
                    if (storeBranches.size > 1) {
                        Text("📍 فرعه", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("كل الفروع", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            HSwitch(checked = brs.isEmpty(), onCheckedChange = { brs = if (it) emptySet() else setOf(storeBranches.first().id) })
                        }
                        if (brs.isNotEmpty()) storeBranches.forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Text(b.name.ifBlank { b.district.ifBlank { "الفرع" } }, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                HSwitch(checked = b.id in brs, onCheckedChange = { on -> brs = if (on) brs + b.id else (brs - b.id) })
                            }
                        }
                        Text("الموظف يشوف طلبات فرعه بس، ويوقف استقبال طلبات فرعه بس.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else Text("الرمز القديم يتقفل فوراً، والموظف يدخل بالرمز الجديد (نفس رقمه).", fontSize = 13.sp)
                if (withPin) PinFields(pin, { pin = it }, pin2, { pin2 = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.saveStaff(old, name, role, perms.toList(), if (withPin) pin else "", storeName, onDismiss, brs.toList()) },
                enabled = !saving && (!withPin || (pin.length == 6 && pin == pin2)) && perms.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (old == null) "إضافة" else "حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("رجوع") } })
}

private fun qrBitmap(text: String, size: Int = 600): ImageBitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF10318F.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
private fun StaffCredsDialog(code: String, m: StoreStaff, qr: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val link = "${com.example.data.OfferSharer.WEB_BASE}/?staff=$code-${m.n}"
    val bmp = remember(link, qr) { if (qr) runCatching { qrBitmap(link) }.getOrNull() else null }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("🔑 دخول ${m.displayName}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("رقم المتجر" to code, "رقم الموظف" to "${m.n}", "الرمز السري" to "اللي اخترته").forEach { (l, v) ->
                        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(l, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, fontWeight = FontWeight.ExtraBold, fontSize = if (v.length > 6) 12.sp else 18.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                bmp?.let {
                    Image(it, contentDescription = "QR", modifier = Modifier.size(200.dp))
                    Text("الموظف يصوّر الـ QR بكاميرا جواله، ويكتب الرمز السري بس.", fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                Text("من التطبيق أو الموقع: «حسابي» ← «👨‍🍳 دخول موظف».", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val txt = "دخول موظف في لكوفر 👨‍🍳\nرقم المتجر: $code\nرقم الموظف: ${m.n}\n" + if (qr) link else com.example.data.OfferSharer.WEB_BASE
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("staff", txt))
                android.widget.Toast.makeText(ctx, com.example.ui.i18n.I18n.tr("انسخت ✅"), android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("📋 انسخ البيانات") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("تم") } })
}
