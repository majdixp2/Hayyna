package com.example.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * التعبئة الذكية: يقرأ صورة العرض (والكلام المكتوب عليها) ويقترح العنوان والسعر والوصف والمدة.
 * يستخدم Gemini عبر Firebase AI Logic (الخطة المجانية). الأدمن يقدر يوقفها من لوحة الإدارة.
 */
object AiAutofill {

    data class Draft(
        val title: String = "",
        val description: String = "",
        val price: Double? = null,
        val originalPrice: Double? = null,
        val days: Int? = null,
        val storeName: String = "",
        val discountPercent: Int? = null,
        val dailyPercents: List<Int> = emptyList(),
        val itemsScope: String = "",
        /** تاريخ البداية بالميلي ثانية (منتصف الليل) أو null */
        val startDateMillis: Long? = null,
        val grandOpening: Boolean = false,
        val phone: String = "",
        val district: String = ""
    )

    private fun prompt(today: String) = """
أنت مساعد لتطبيق عروض محلات في السعودية. هذي صورة إعلان/عرض لمتجر.
تاريخ اليوم: $today
اقرأ الصورة وكل نص مكتوب عليها بدقة، وطلّع بيانات العرض بالعربي.
رجّع JSON فقط بهذا الشكل بدون أي كلام ثاني:
{"title": "عنوان قصير وجذاب للعرض (أقل من 60 حرف)",
 "description": "وصف مختصر سطرين: الأصناف المشمولة، والتوصيل ورسومه والحد الأدنى إذا مكتوب، والعنوان/المعلم إذا مكتوب",
 "storeName": "اسم المحل كما هو مكتوب أو \"\"",
 "price": رقم السعر بعد الخصم بالريال أو null,
 "originalPrice": رقم السعر قبل الخصم أو null,
 "discountPercent": نسبة الخصم كرقم إذا العرض نسبة بدون سعر محدد (مثل 30) أو null,
 "dailyPercents": إذا الخصم يتغير حسب الأيام قائمة نسبة كل يوم بالترتيب (مثلاً "اليوم الأول 50% واليوم الثاني والثالث 30%" = [50,30,30]) وإلا [],
 "itemsScope": "نطاق الخصم مثل \"على جميع الأصناف\" أو \"\"",
 "startDate": "تاريخ بداية العرض بصيغة yyyy-MM-dd إذا مكتوب (احسب اليوم من التاريخ واسم اليوم) أو null",
 "days": عدد أيام العرض إذا مكتوب أو null,
 "grandOpening": true إذا الإعلان عن افتتاح محل جديد وإلا false,
 "phone": "رقم الجوال المكتوب أرقام فقط أو \"\"",
 "district": "اسم الحي إذا مكتوب أو \"\""}
لا تخترع أي معلومة غير موجودة في الصورة.
"""

    /** يجرّب النموذج المحدد، ولو فشل يجرّب النماذج الاحتياطية بالترتيب. */
    suspend fun analyzeWithFallback(context: Context, imageUri: String, models: List<String>): Draft {
        var last: Exception? = null
        for (m in models.distinct()) {
            try {
                return analyze(context, imageUri, m)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                // أخطاء App Check / الصلاحيات ما تتصلح بتغيير النموذج
                val msg = e.message.orEmpty()
                if (msg.contains("App Check", true) || msg.contains("PERMISSION", true) || msg.contains("403")) throw e
            }
        }
        throw last ?: IllegalStateException("no model")
    }

    suspend fun analyze(context: Context, imageUri: String, modelName: String): Draft {
        val bitmap = loadBitmap(context, imageUri) ?: throw IllegalStateException("تعذّر قراءة الصورة")
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            generationConfig = generationConfig { responseMimeType = "application/json" }
        )
        val response = model.generateContent(
            content {
                image(bitmap)
                text(prompt(java.text.SimpleDateFormat("yyyy-MM-dd EEEE", java.util.Locale.forLanguageTag("ar")).format(java.util.Date())))
            }
        )
        val raw = response.text?.trim().orEmpty()
        val json = JSONObject(raw.substring(raw.indexOf('{').coerceAtLeast(0), raw.lastIndexOf('}') + 1))
        fun num(key: String): Double? =
            if (json.isNull(key)) null else json.optDouble(key).takeIf { !it.isNaN() && it > 0 }
        fun str(key: String): String = if (json.isNull(key)) "" else json.optString(key).trim().takeIf { it != "null" }.orEmpty()
        val steps = json.optJSONArray("dailyPercents")?.let { arr ->
            (0 until arr.length()).map { arr.optInt(it) }.filter { it in 1..99 }
        }.orEmpty().take(14)
        val startMillis = str("startDate").takeIf { it.isNotBlank() }?.let {
            runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }.getOrNull()
        }
        val pct = if (json.isNull("discountPercent")) null else json.optInt("discountPercent").takeIf { it in 1..99 }
        return Draft(
            title = str("title").take(80),
            description = str("description").take(300),
            price = num("price"),
            originalPrice = num("originalPrice"),
            days = if (json.isNull("days")) null else json.optInt("days").takeIf { it > 0 },
            storeName = str("storeName").take(80),
            discountPercent = pct ?: steps.firstOrNull(),
            dailyPercents = if (steps.size >= 2 && steps.distinct().size > 1) steps else emptyList(),
            itemsScope = str("itemsScope").take(40),
            startDateMillis = startMillis,
            grandOpening = json.optBoolean("grandOpening", false),
            phone = str("phone").filter { it.isDigit() }.take(15),
            district = str("district").take(60)
        )
    }

    private suspend fun loadBitmap(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(uri).size(1024).allowHardware(false).build()
            ) as? SuccessResult
            result?.drawable?.toBitmap()
        }.getOrNull()
    }
}
