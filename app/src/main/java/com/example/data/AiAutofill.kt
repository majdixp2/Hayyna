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
        val days: Int? = null
    )

    private const val PROMPT = """
أنت مساعد لتطبيق عروض محلات في السعودية. هذي صورة إعلان/عرض لمتجر.
اقرأ الصورة وأي نص مكتوب عليها، وطلّع بيانات العرض بالعربي.
رجّع JSON فقط بهذا الشكل بدون أي كلام ثاني:
{"title": "عنوان قصير وجذاب للعرض (أقل من 60 حرف)",
 "description": "وصف مختصر سطر أو سطرين",
 "price": رقم السعر بعد الخصم بالريال أو null,
 "originalPrice": رقم السعر قبل الخصم أو null,
 "days": عدد أيام العرض إذا مكتوب أو null}
لا تخترع أسعار غير موجودة في الصورة.
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
                text(PROMPT)
            }
        )
        val raw = response.text?.trim().orEmpty()
        val json = JSONObject(raw.substring(raw.indexOf('{').coerceAtLeast(0), raw.lastIndexOf('}') + 1))
        fun num(key: String): Double? =
            if (json.isNull(key)) null else json.optDouble(key).takeIf { !it.isNaN() && it > 0 }
        return Draft(
            title = json.optString("title").take(80),
            description = json.optString("description").take(300),
            price = num("price"),
            originalPrice = num("originalPrice"),
            days = if (json.isNull("days")) null else json.optInt("days").takeIf { it > 0 }
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
