package com.example.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** بريد التواصل الظاهر في سياسة الخصوصية — غيّره لبريد الدعم الرسمي عند توفره. */
const val SUPPORT_EMAIL = "hayyna.sa@gmail.com"

val PRIVACY_POLICY_TEXT = """
سياسة الخصوصية لتطبيق حيّنا

1. الموقع الجغرافي
نستخدم موقعك على جهازك فقط لحساب المسافة إلى العروض وترتيبها حسب الأقرب. لا نحفظ موقعك ولا نرسله لأي جهة.

2. حساب التاجر
عند تسجيل الدخول بحساب Google نحفظ معرّف الحساب والبريد والاسم لربط العروض بصاحبها. بيانات المطعم (الاسم، الرقم، الموقع، الدوام) تُحفظ لتعبئة العروض الجديدة تلقائياً.

3. العروض
العروض وصورها ومقاطعها ورقم التواصل تظهر لجميع مستخدمي التطبيق. تُخزَّن الصور والفيديو لدى Cloudinary، وبيانات العروض لدى Google Firebase.

4. الإحصائيات
نحسب عدد مشاهدات العرض وطلبات الاتجاه والتواصل بشكل مجهّز (أرقام فقط) بدون ربطها بهويتك.

5. المفضلة
العروض المحفوظة تبقى على جهازك فقط.

6. البلاغات
عند الإبلاغ عن عرض نحفظ سبب البلاغ والعرض المعني لمراجعته.

7. حذف البيانات
يقدر التاجر يحذف حسابه وكل عروضه من صفحة التاجر ← "حذف حسابي". يتم الحذف فوراً ونهائياً.

8. التواصل
لأي استفسار: $SUPPORT_EMAIL
""".trimIndent()

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سياسة الخصوصية") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(PRIVACY_POLICY_TEXT, fontSize = 14.sp, lineHeight = 22.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}
