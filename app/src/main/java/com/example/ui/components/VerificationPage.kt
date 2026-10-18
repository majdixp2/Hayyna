package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.VerificationRequest
import com.example.data.model.VerifyConfig
import com.example.ui.theme.CalmSagePrimary
import java.io.File

/**
 * صفحة "وثّق متجرك": رقم السجل التجاري / وثيقة العمل الحر، صورة حيّة للواجهة، والتواجد عند المحل.
 * كل شرط يقدر الأدمن يشغّله أو يطفيه من الإعدادات.
 */
@Composable
fun VerificationPage(
    config: VerifyConfig,
    current: VerificationRequest?,
    isSubmitting: Boolean,
    hasBranches: Boolean,
    measureDistance: () -> Int?,
    onRequestLocation: () -> Unit,
    onSubmit: (docType: String, docNumber: String, photoLocal: String?, distance: Int?) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var docType by remember { mutableStateOf(current?.docType?.ifBlank { null } ?: VerificationRequest.DOC_CR) }
    var docNumber by remember { mutableStateOf(current?.docNumber.orEmpty()) }
    var photo by remember { mutableStateOf<String?>(null) }
    var distance by remember { mutableStateOf<Int?>(null) }
    var locationTried by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // صورة حيّة من الكاميرا (مو من المعرض)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photo = pendingUri?.toString()
    }
    fun openCamera() {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "storefront_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri
        runCatching { camera.launch(uri) }.onFailure { error = "ما قدرنا نفتح الكاميرا" }
    }

    BackHandler { if (!isSubmitting) onClose() }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓ وثّق متجرك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (!isSubmitting) onClose() }) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
            }
            HorizontalDivider()
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "المتجر الموثّق يطلع جنب اسمه علامة ✓، والعملاء يثقون فيه أكثر، وعروضه تنزل مباشرة بدون انتظار.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                current?.let { r ->
                    val (bg, fg, label) = when (r.status) {
                        VerificationRequest.STATUS_APPROVED -> Triple(Color(0xFFE7F8EE), CalmSagePrimary, "✓ متجرك موثّق")
                        VerificationRequest.STATUS_REJECTED -> Triple(Color(0xFFFDE8E6), Color(0xFFB42318), "تم رفض الطلب: ${r.rejectReason.ifBlank { "بدون سبب" }} — تقدر تعدّل وترسل مرة ثانية")
                        else -> Triple(Color(0xFFFFF4E0), Color(0xFF8A5A00), "⏳ طلبك قيد المراجعة")
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
                        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                    }
                }

                if (config.requireDoc) {
                    Text("١. المستند الرسمي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = docType == VerificationRequest.DOC_CR,
                            onClick = { docType = VerificationRequest.DOC_CR },
                            label = { Text("سجل تجاري") }
                        )
                        FilterChip(
                            selected = docType == VerificationRequest.DOC_FREELANCE,
                            onClick = { docType = VerificationRequest.DOC_FREELANCE },
                            label = { Text("وثيقة العمل الحر") }
                        )
                    }
                    OutlinedTextField(
                        value = docNumber,
                        onValueChange = { if (it.length <= 40) docNumber = it },
                        label = { Text(if (docType == VerificationRequest.DOC_CR) "رقم السجل التجاري *" else "رقم وثيقة العمل الحر *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (docType == VerificationRequest.DOC_CR) KeyboardType.Number else KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("نكتفي بالرقم ونتحقق منه من المصدر الرسمي — ما نطلب صورة المستند.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (config.requirePhoto) {
                    Text("${if (config.requireDoc) "٢" else "١"}. صورة حيّة لواجهة المحل", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("صوّر واجهة المحل واللوحة واضحة (من الكاميرا مباشرة).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { openCamera() }, shape = RoundedCornerShape(10.dp)) {
                            Text(if (photo == null) "📷 صوّر الواجهة" else "📷 صوّر مرة ثانية")
                        }
                        Spacer(Modifier.width(10.dp))
                        photo?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                if (config.requireLocation) {
                    Text("آخر خطوة: تأكيد إنك عند المحل", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (!hasBranches) {
                        Text("أضف موقع فرعك أول من \"صفحة متجري\".", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("اضغط الزر وأنت واقف عند المحل.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = {
                                onRequestLocation()
                                locationTried = true
                                distance = measureDistance()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("📍 تحقق من موقعي") }
                        if (locationTried) {
                            val d = distance
                            Text(
                                when {
                                    d == null -> "ما قدرنا نحدد موقعك — فعّل الموقع وجرب مرة ثانية"
                                    d <= VerificationRequest.MAX_DISTANCE_METERS -> "✅ أنت عند المحل (${d} م)"
                                    else -> "⚠️ أنت بعيد عن المحل (${if (d >= 1000) "${d / 1000} كم" else "$d م"}) — روح للمحل وجرب"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (d != null && d <= VerificationRequest.MAX_DISTANCE_METERS) CalmSagePrimary else Color(0xFFB42318)
                            )
                        }
                    }
                }

                if (!config.requireDoc && !config.requirePhoto && !config.requireLocation) {
                    Text("أرسل الطلب وبنراجع متجرك ونوثّقه.", fontSize = 14.sp)
                }
                Spacer(Modifier.height(10.dp))
            }

            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)) {
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            error = when {
                                config.requireDoc && docNumber.trim().length < 5 -> "اكتب رقم المستند صحيح."
                                config.requirePhoto && photo == null -> "صوّر واجهة المحل."
                                config.requireLocation && !hasBranches -> "أضف موقع فرعك أول."
                                config.requireLocation && (distance ?: Int.MAX_VALUE) > VerificationRequest.MAX_DISTANCE_METERS ->
                                    "لازم تكون عند المحل وتضغط \"تحقق من موقعي\"."
                                else -> ""
                            }
                            if (error.isBlank()) onSubmit(docType, docNumber, photo, distance)
                        },
                        enabled = !isSubmitting && current?.status != VerificationRequest.STATUS_APPROVED,
                        colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        if (isSubmitting) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        else Text("أرسل طلب التوثيق", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** شارة "✓ موثّق" الصغيرة. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE0F2FE), modifier = modifier) {
        Text("✓ موثّق", color = Color(0xFF0369A1), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
