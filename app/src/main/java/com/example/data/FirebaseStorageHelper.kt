package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

object FirebaseStorageHelper {
    private const val TAG = "FirebaseStorageHelper"
    private const val MAX_IMAGE_SIDE = 1600
    private const val MAX_VIDEO_BYTES = 30L * 1024 * 1024

    /** خطأ رفع برسالة عربية واضحة للتاجر. */
    class UploadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * يرفع صورة أو فيديو العرض إلى Firebase Storage ويرجع رابط التحميل العام.
     * الصور تُصغّر وتُضغط قبل الرفع (أسرع وأوفر للباقة).
     */
    suspend fun uploadOfferMedia(context: Context, mediaUriString: String, isVideo: Boolean): String {
        if (mediaUriString.startsWith("http://") || mediaUriString.startsWith("https://") || !mediaUriString.contains("://")) {
            return mediaUriString
        }
        val uri = Uri.parse(mediaUriString)
        val ref = FirebaseStorage.getInstance().reference
            .child("offers/${UUID.randomUUID()}.${if (isVideo) "mp4" else "jpg"}")

        try {
            if (isVideo) {
                val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                if (size > MAX_VIDEO_BYTES) {
                    throw UploadException("الفيديو كبير (أكثر من 30 ميجا). اختر فيديو أقصر.")
                }
                val type = context.contentResolver.getType(uri) ?: "video/mp4"
                ref.putFile(uri, StorageMetadata.Builder().setContentType(type).build()).await()
            } else {
                val bytes = withContext(Dispatchers.IO) { compressImage(context, uri) }
                    ?: throw UploadException("ما قدرنا نقرأ الصورة. جرّب صورة ثانية.")
                ref.putBytes(bytes, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            }
            return ref.downloadUrl.await().toString()
        } catch (e: UploadException) {
            throw e
        } catch (e: StorageException) {
            Log.e(TAG, "Storage upload failed code=${e.errorCode} http=${e.httpResultCode}: ${e.message}", e)
            throw UploadException(describe(e), e)
        } catch (e: SecurityException) {
            throw UploadException("انتهت صلاحية الوصول للصورة. اختر الصورة مرة ثانية.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            throw UploadException("تعذّر رفع الملف: ${e.message ?: "خطأ غير معروف"}", e)
        }
    }

    private fun describe(e: StorageException): String = when (e.errorCode) {
        StorageException.ERROR_NOT_AUTHORIZED ->
            "رفض Firebase Storage الرفع (صلاحيات). لازم تنشر قواعد Storage. [-13021]"
        StorageException.ERROR_NOT_AUTHENTICATED ->
            "لازم تسجّل الدخول قبل رفع الصورة. [-13020]"
        StorageException.ERROR_BUCKET_NOT_FOUND, StorageException.ERROR_PROJECT_NOT_FOUND ->
            "خدمة Storage غير مفعّلة في Firebase. [${e.errorCode}]"
        StorageException.ERROR_QUOTA_EXCEEDED ->
            "تجاوزت حد التخزين المجاني في Firebase. [-13013]"
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
            "الاتصال ضعيف، ما اكتمل الرفع. حاول مرة ثانية. [-13030]"
        else ->
            if (e.httpResultCode == 402 || e.httpResultCode == 403 && (e.message ?: "").contains("billing", true))
                "خدمة Storage تحتاج تفعيل خطة Blaze في Firebase. [${e.httpResultCode}]"
            else "تعذّر رفع الملف [${e.errorCode} / HTTP ${e.httpResultCode}]"
    }

    /** يصغّر الصورة لأقصى 1600px ويضغطها JPEG بجودة 82%. */
    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        val bitmap: Bitmap = try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(w, h)
                    if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image decode failed: ${e.message}")
            return null
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
    }
}
