package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Feedback
import com.example.ui.theme.CalmSagePrimary

/** صفحة "تواصل معنا": نموذج رسالة + واتساب الدعم + رسائلي وردود الإدارة. */
@Composable
fun ContactPage(
    supportNumber: String,
    myMessages: List<Feedback>,
    isLoggedIn: Boolean,
    isSending: Boolean,
    appVersion: String,
    onSend: (type: String, message: String, screenshot: String?, contact: String, onSent: () -> Unit) -> Unit,
    onSeenReplies: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(Feedback.TYPES.first()) }
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var screenshot by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) screenshot = uri.toString()
    }

    // أول ما يفتح الصفحة، الردود الجديدة تصير مقروءة
    LaunchedEffect(myMessages.count { !it.replySeen }) {
        if (myMessages.any { !it.replySeen }) onSeenReplies()
    }

    BackHandler { if (!isSending) onClose() }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📨 تواصل معنا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (!isSending) onClose() }) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
            }
            HorizontalDivider()

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // واتساب الدعم للأمور العاجلة
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F8EE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("تبي رد سريع؟", fontWeight = FontWeight.Bold)
                            Text("كلمنا على واتساب الدعم", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                val phone = normalizeSaudiPhone(supportNumber) ?: supportNumber
                                openWhatsApp(
                                    context,
                                    phone,
                                    "مرحباً فريق لكوفر 👋\nنوع الرسالة: $type\n\n(إصدار التطبيق $appVersion)"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("واتساب", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text("أو اكتب لنا هنا:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Feedback.TYPES.size) { i ->
                        val t = Feedback.TYPES[i]
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 2000) message = it },
                    label = { Text("رسالتك *") },
                    placeholder = { Text("اكتب المشكلة أو الاقتراح بالتفصيل…") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isLoggedIn) {
                    OutlinedTextField(
                        value = contact,
                        onValueChange = { contact = it },
                        label = { Text("جوالك أو إيميلك (اختياري، عشان نرد عليك)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(if (screenshot == null) "📷 أرفق صورة شاشة" else "📷 غيّر الصورة") }
                    Spacer(Modifier.width(10.dp))
                    screenshot?.let { s ->
                        Box {
                            AsyncImage(
                                model = s,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                        TextButton("إزالة") { screenshot = null }
                    }
                }

                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

                Button(
                    onClick = {
                        if (message.isBlank()) {
                            error = "اكتب رسالتك أول."
                        } else {
                            error = ""
                            onSend(type, message, screenshot, contact) {
                                message = ""
                                screenshot = null
                                contact = ""
                            }
                        }
                    },
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("إرسال", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Text(
                    "يرفق التطبيق تلقائياً رقم الإصدار ونوع الجوال عشان نساعدك أسرع.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---------- رسائلي ----------
                if (isLoggedIn && myMessages.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("رسائلي (${myMessages.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    myMessages.forEach { f -> MyMessageCard(f) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun MyMessageCard(f: Feedback) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.type, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = CalmSagePrimary.copy(alpha = 0.12f)) {
                    Text(
                        Feedback.statusLabel(f.status),
                        fontSize = 11.sp,
                        color = CalmSagePrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(f.message, fontSize = 13.sp, maxLines = 4)
            if (f.reply.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE7F8EE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("رد إدارة لكوفر:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CalmSagePrimary)
                        Text(f.reply, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
