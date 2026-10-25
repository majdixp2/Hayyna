package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RestaurantOffer
import com.example.data.repository.CreditItem
import com.example.ui.MerchantViewModel

// رصيد العروض في التطبيق: نعرض الرصيد ونصرفه بس. الشراء برّا التطبيق، وما فيه أي رابط أو ذكر لمكان الدفع (سياسة Google Play).

private val Purple = Color(0xFF7C3AED)
private val Amber = Color(0xFF8A5A00)
private val AmberBg = Color(0xFFFFF4E0)

/** معلومات النشر بالرصيد لنموذج العرض. cost(days, excl) = (السعر الكامل، السعر بعد خصم الحصري) */
data class PublishInfo(
    val balance: Long,
    val price: Long,
    val perDay: Boolean,
    val rewardOn: Boolean,
    val rewardPercent: Int,
    val isAdmin: Boolean,
    val cost: (days: Int, excl: Boolean) -> Pair<Long, Long>
)

@Composable
fun PublishBalanceBar(p: PublishInfo) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💰 رصيدك: ${p.balance} نقطة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("📢 النشر: ${p.price} ${if (p.perDay) "نقاط باليوم" else "نقاط"}", fontSize = 13.sp)
            }
            if (p.rewardOn) Text(
                "💎 الحصري: " + if (p.rewardPercent >= 100) "نشره مجاني" else "خصم ${p.rewardPercent}%",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Purple
            )
        }
    }
}

@Composable
fun PublishCostLine(p: PublishInfo, days: Int, exclusive: Boolean) {
    val (full, cost) = p.cost(days, exclusive)
    Surface(shape = RoundedCornerShape(12.dp), color = AmberBg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💰 تكلفة النشر: ", fontSize = 14.sp, color = Amber)
                if (cost < full) {
                    Text("$full", fontSize = 14.sp, color = Amber, textDecoration = TextDecoration.LineThrough)
                    Text(if (cost == 0L) " مجاني 💎" else " $cost نقطة 💎", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Amber)
                } else Text("$days ${if (days == 1) "يوم" else "أيام"} = $full نقطة", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Amber)
            }
            if (!p.isAdmin && p.balance < cost) Text(
                "رصيدك ${p.balance}، ما يكفي — بس لا تشيل هم: ما يضيع شي من عرضك 👍",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** بطاقة الرصيد في حساب التاجر */
@Composable
fun WalletCard(balance: Long, items: List<CreditItem>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF2D6A4F), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("💰 رصيد العروض", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Text("$balance", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("نقطة", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items.take(4).forEach {
                    Text("${it.name}: ${it.price}${if (it.unit == "day") "/يوم" else ""}", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

/** رسائل الرصيد: ما يكفي (نقترح الحصري أو المسودة) / انحفظت المسودة / نشر مسودة */
@Composable
fun CreditPromptDialog(
    prompt: MerchantViewModel.CreditPrompt,
    rewardOn: Boolean,
    rewardPercent: Int,
    onMakeExclusive: () -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onDismiss: () -> Unit
) {
    val exl = ExclusiveBadge.label.ifBlank { "💎 حصري لحيّنا" }
    val exclPrice = if (prompt.exclCost == 0L) "مجاناً" else "بـ ${prompt.exclCost} نقطة"
    val title: String
    val body: @Composable () -> Unit
    when (prompt.kind) {
        MerchantViewModel.CreditPrompt.SAVED -> {
            title = "انحفظ عرضك ✅"
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("يتنشر بضغطة وحدة أول ما يصير عندك رصيد. تلقاه في «حسابي» تحت «عروضك» وعليه زر «📢 انشر الحين».", fontSize = 14.sp)
                    if (rewardOn && prompt.offer?.exclusive != true) ExclusiveHint(
                        "💡 ترى $exl " + if (rewardPercent >= 100) "نشره مجاني" else "نشره بخصم $rewardPercent%",
                        "لو عرضك لعملاء حيّنا بس، اضغط «انشر الحين» واختر «خلّه حصري»."
                    )
                }
            }
        }
        MerchantViewModel.CreditPrompt.DRAFT -> {
            title = "📢 ${prompt.offer?.offerTitle.orEmpty()}"
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المدة: ${prompt.days} ${if (prompt.days == 1) "يوم" else "أيام"} • رصيدك ${prompt.balance} نقطة", fontSize = 14.sp)
                    if (prompt.canPay) Button(
                        onClick = onPublish, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D6A4F)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("📢 انشره " + if (prompt.myCost == 0L) "مجاناً" else "بـ ${prompt.myCost} نقطة", fontWeight = FontWeight.Bold) }
                    else Text("رصيدك ما يكفي: النشر يحتاج ${prompt.myCost} نقطة.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    if (prompt.exclOk) ExclusiveOffer(
                        head = if (prompt.canPay) "أو وفّر 👇" else "بس عندنا لك حل 👇",
                        text = "خلّه $exl وانشره ${if (prompt.exclCost == 0L) "مجاناً" else "بـ ${prompt.exclCost} نقطة بدل ${prompt.fullCost}"}.",
                        button = "خلّه حصري وانشره $exclPrice", onClick = onMakeExclusive
                    )
                    if (!prompt.canPay && !prompt.exclOk) Text("يتنشر بضغطة وحدة أول ما يصير عندك رصيد.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        else -> {
            title = "رصيدك ما يكفي 🙁"
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("نشر العرض يحتاج ${prompt.myCost} نقطة، ورصيدك ${prompt.balance}.", fontSize = 14.sp)
                    if (prompt.exclOk) ExclusiveOffer(
                        head = "بس عندنا لك حل 👇",
                        text = "خلّ عرضك $exl وانشره ${if (prompt.exclCost == 0L) "مجاناً" else "بـ ${prompt.exclCost} نقطة بدل ${prompt.fullCost}"} الحين.",
                        button = "خلّه حصري وانشره $exclPrice", onClick = onMakeExclusive
                    )
                    OutlinedButton(onClick = onSaveDraft, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("📝 احفظه مسودة لين أشحن", fontWeight = FontWeight.Bold)
                    }
                    Text("المسودة ما تظهر للناس، وتنشرها بضغطة وحدة من «حسابي» أول ما يصير عندك رصيد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { body() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (prompt.kind == MerchantViewModel.CreditPrompt.SAVED) "تمام" else "رجوع") } }
    )
}

@Composable
private fun ExclusiveHint(head: String, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Purple.copy(alpha = 0.08f), border = BorderStroke(1.dp, Purple.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(head, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ExclusiveOffer(head: String, text: String, button: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Purple.copy(alpha = 0.08f), border = BorderStroke(1.dp, Purple.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(head, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(text, fontSize = 14.sp)
            Text("الحصري يعني العرض لعملاء حيّنا بس، يوري التطبيق عند الكاشير.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Purple), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("💎 $button", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** شراء خدمة لعرض: تمديد النشر / تمييز / إبراز */
@Composable
fun BoostDialog(
    offer: RestaurantOffer,
    balance: Long,
    items: List<CreditItem>,
    priceOf: (itemId: String, days: Int) -> Pair<Long, Long>,
    spotUsed: Int,
    spotMax: Int,
    spotActiveHere: Boolean,
    onBuy: (itemId: String, days: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val daysSel = remember { mutableStateMapOf<String, Int>() }
    var confirm by remember { mutableStateOf<Pair<CreditItem, Int>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚀 ${offer.offerTitle}", fontWeight = FontWeight.Bold, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("رصيدك: $balance نقطة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                items.forEach { item ->
                    val isSpot = item.effect == "spotlight"
                    val full = isSpot && !spotActiveHere && spotUsed >= spotMax
                    val d = daysSel[item.id] ?: if (item.unit == "day") 7 else item.days
                    val (base, cost) = priceOf(item.id, d)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSpot) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text(effectHint(item.effect), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isSpot) Text(
                                if (full) "⛔ الأماكن في ${offer.district.ifBlank { "حيّك" }} ممتلئة ($spotUsed/$spotMax)، جرّب بعد كم يوم"
                                else "باقي ${spotMax - spotUsed} من $spotMax أماكن في ${offer.district.ifBlank { "حيّك" }}",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber
                            )
                            if (item.unit == "day") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 3, 7, 14, 30).forEach { n ->
                                    FilterChip(selected = d == n, onClick = { daysSel[item.id] = n }, label = { Text("$n", fontSize = 12.sp) })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (cost < base) "💎 " + (if (cost == 0L) "مجاني" else "$cost نقطة بدل $base") else "$base نقطة") +
                                        " • ${if (item.unit == "day") d else item.days} ${if ((if (item.unit == "day") d else item.days) == 1) "يوم" else "أيام"}",
                                    fontSize = 13.sp, modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { confirm = item to d }, enabled = !full,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D6A4F))
                                ) { Text(if (item.effect == "publish") "تمديد" else "اشترِ", fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
    confirm?.let { (item, d) ->
        val cost = priceOf(item.id, d).second
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("تأكيد") },
            text = { Text(if (cost == 0L) "${item.name} (مجاني لأن عرضك حصري 💎)؟" else "${item.name} بـ $cost نقطة؟\nرصيدك بعدها: ${balance - cost}") },
            confirmButton = { TextButton(onClick = { confirm = null; onBuy(item.id, d) }, enabled = cost <= balance) { Text(if (cost <= balance) "تأكيد" else "رصيدك ما يكفي", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } }
        )
    }
}

private fun effectHint(effect: String) = when (effect) {
    "publish" -> "العرض يظهر على الخريطة وفي القائمة طوال المدة"
    "feature" -> "حلقة صفراء مع وميض، ويطلع أول في الترتيب"
    "listTop" -> "يتثبّت فوق قائمة العروض في «إعلانات بارزة»"
    "spotlight" -> "كل مزايا التمييز + اسم المحل ظاهر دايم على الخريطة، وبطاقة «عرض مُبرز قريب منك» لأهل الحي"
    else -> ""
}

/** شارات الخدمات النشطة لعرض التاجر */
fun paidChipsFor(offer: RestaurantOffer, boosts: List<com.example.data.repository.Boost>, now: Long): List<String> {
    val mine = boosts.filter { it.offerId == offer.id }
    val out = mutableListOf<String>()
    if (mine.any { it.effect == "spotlight" && it.startsAt <= now && now < it.endsAt }) out += "🚀 مُبرز"
    else if (mine.any { it.effect == "feature" && it.startsAt <= now && now < it.endsAt }) out += "✨ مميّز"
    if (mine.any { it.effect == "listTop" && it.startsAt <= now && now < it.endsAt }) out += "📌 بارز"
    mine.filter { it.effect == "publish" }.maxOfOrNull { it.endsAt }?.let {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = it }
        out += "📢 منشور لين ${c.get(java.util.Calendar.DAY_OF_MONTH)}/${c.get(java.util.Calendar.MONTH) + 1}"
    }
    return out
}

/** رصيد العروض في لوحة التاجر (null = الرصيد مطفي) */
data class MerchantCredits(
    val balance: Long,
    val items: List<CreditItem>,
    val chips: (RestaurantOffer) -> List<String>,
    val onPublishDraft: (RestaurantOffer) -> Unit,
    val onBoost: (RestaurantOffer) -> Unit
)
