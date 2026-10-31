package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.example.ui.i18n.Surface
import com.example.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MerchantViewModel
import com.example.ui.theme.CalmSagePrimary

/** اختيار نوع الحساب: زبون / صاحب محل. */
@Composable
private fun RolePicker(role: String, onRole: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(MerchantViewModel.ROLE_CUSTOMER to "🛍️ زبون", MerchantViewModel.ROLE_MERCHANT to "🏪 صاحب محل").forEach { (key, label) ->
            val on = role == key
            Surface(
                onClick = { onRole(key) },
                shape = RoundedCornerShape(14.dp),
                color = if (on) Color(0xFFF2FAF5) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, if (on) CalmSagePrimary else Color(0xFFE7E2D9)),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (on) CalmSagePrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
        }
    }
}

/** يرجّع رسالة الخطأ أو null لو البيانات سليمة. */
private fun validate(name: String, phone: String): String? = when {
    name.trim().length < 2 -> "اكتب اسمك."
    normalizeSaudiPhone(phone) == null -> "اكتب رقم جوال صحيح مثل 05xxxxxxxx."
    else -> null
}

/** أول دخول: الاسم + الجوال + نوع الحساب. */
@Composable
fun ProfileSetupView(
    initialName: String,
    initialPhone: String,
    initialRole: String,
    isSaving: Boolean,
    onSave: (name: String, phone: String, role: String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(toLocalPhone(initialPhone)) }
    var role by remember { mutableStateOf(initialRole) }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("كمّل بياناتك 👋", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("خطوة وحدة وتبدأ", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            label = { Text("اسمك *") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it.take(15) },
            label = { Text("رقم جوالك *") }, placeholder = { Text("05xxxxxxxx") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
        )
        Text("نوع الحساب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        RolePicker(role) { role = it }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Button(
            onClick = {
                val e = validate(name, phone)
                if (e != null) error = e else onSave(name.trim(), normalizeSaudiPhone(phone)!!, role)
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("متابعة", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("تسجيل الخروج") }
    }
}

/** تعديل الاسم والجوال ونوع الحساب. */
@Composable
fun ProfileEditDialog(
    initialName: String,
    initialPhone: String,
    initialRole: String,
    email: String,
    onSave: (name: String, phone: String, role: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(toLocalPhone(initialPhone)) }
    var role by remember { mutableStateOf(initialRole) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بياناتي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it.take(15) }, label = { Text("رقم الجوال") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                if (email.isNotBlank()) Text(email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validate(name, phone)
                if (e != null) error = e else { onSave(name.trim(), normalizeSaudiPhone(phone)!!, role); onDismiss() }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
