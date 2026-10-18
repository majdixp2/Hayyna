package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** بيانات التوصيل كنص: "🛵 توصيل 10 ر.س • للطلبات فوق 40 ر.س" (null = ما يوصّل). */
fun deliveryText(delivery: Boolean, fee: Double, minOrder: Double): String? {
    if (!delivery) return null
    fun f(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    val feePart = if (fee <= 0) "توصيل مجاني" else "توصيل ${f(fee)} ر.س"
    val minPart = if (minOrder > 0) " • للطلبات فوق ${f(minOrder)} ر.س" else ""
    return "🛵 $feePart$minPart"
}

fun moneyText(v: Double): String = when {
    v <= 0 -> ""
    v % 1.0 == 0.0 -> v.toLong().toString()
    else -> v.toString()
}

/** محرر التوصيل: يوصّل؟ + الرسوم + الحد الأدنى. */
@Composable
fun DeliveryEditor(
    delivery: Boolean,
    onDeliveryChange: (Boolean) -> Unit,
    feeText: String,
    onFeeChange: (String) -> Unit,
    minText: String,
    onMinChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = delivery, onCheckedChange = onDeliveryChange)
            Text("🛵 عندنا توصيل", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (delivery) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { v -> onFeeChange(v.filter { it.isDigit() || it == '.' }.take(6)) },
                    label = { Text("رسوم التوصيل") },
                    placeholder = { Text("0 = مجاني") },
                    suffix = { Text("ر.س") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minText,
                    onValueChange = { v -> onMinChange(v.filter { it.isDigit() || it == '.' }.take(6)) },
                    label = { Text("للطلبات فوق") },
                    placeholder = { Text("اختياري") },
                    suffix = { Text("ر.س") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** سطر معلومات صغير ملوّن (توصيل / مَعلَم). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, bg: Color = Color(0xFFE7F6EC), fg: Color = Color(0xFF15803D)) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg, modifier = modifier) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** وسوم الأصناف (#شاورما #برجر). */
@Composable
fun TagsRow(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        items(tags) { t ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("#$t", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/** يحوّل "شاورما، برجر , بروست" إلى قائمة وسوم نظيفة. */
fun parseTags(text: String): List<String> =
    text.split(',', '،', '\n', '#')
        .map { it.trim() }
        .filter { it.isNotBlank() && it.length <= 25 }
        .distinct()
        .take(12)
