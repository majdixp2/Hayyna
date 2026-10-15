package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** إعدادات Cloudinary (ليست أسرار: الرفع عبر preset غير موقّع مخصص للتطبيق). */
object CloudinaryConfig {
    const val CLOUD_NAME = "ffhqn5dw"
    const val UPLOAD_PRESET = "hayyna_offers"
}

/**
 * رفع صور وفيديوهات العروض إلى Cloudinary.
 * الصور تُصغّر وتُضغط على الجهاز قبل الرفع، والفيديو بحد أقصى 30MB.
 */
object MediaUploader {
    private const val TAG = "MediaUploader"
    private const val MAX_IMAGE_SIDE = 1600
    private const val MAX_VIDEO_BYTES = 30L * 1024 * 1024

    class UploadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    suspend fun uploadOfferMedia(context: Context, mediaUriString: String, isVideo: Boolean): String {
        if (mediaUriString.startsWith("http://") || mediaUriString.startsWith("https://") || !mediaUriString.contains("://")) {
            return mediaUriString
        }
        val uri = Uri.parse(mediaUriString)
        return withContext(Dispatchers.IO) {
            try {
                val filePart: RequestBody = if (isVideo) {
                    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    if (size > MAX_VIDEO_BYTES) throw UploadException("الفيديو كبير (أكثر من 30 ميجا). اختر فيديو أقصر.")
                    val type = context.contentResolver.getType(uri) ?: "video/mp4"
                    object : RequestBody() {
                        override fun contentType() = type.toMediaType()
                        override fun contentLength() = size
                        override fun writeTo(sink: BufferedSink) {
                            context.contentResolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
                                ?: throw IOException("cannot open video")
                        }
                    }
                } else {
                    val bytes = compressImage(context, uri)
                        ?: throw UploadException("ما قدرنا نقرأ الصورة. جرّب صورة ثانية.")
                    bytes.toRequestBody("image/jpeg".toMediaType())
                }

                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", CloudinaryConfig.UPLOAD_PRESET)
                    .addFormDataPart("file", if (isVideo) "offer.mp4" else "offer.jpg", filePart)
                    .build()

                val resource = if (isVideo) "video" else "image"
                val request = okhttp3.Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/${CloudinaryConfig.CLOUD_NAME}/$resource/upload")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiMessage = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                        Log.e(TAG, "Cloudinary upload failed ${response.code}: $text")
                        throw UploadException("تعذّر رفع الملف [HTTP ${response.code}] ${apiMessage.orEmpty()}".trim())
                    }
                    JSONObject(text).getString("secure_url")
                }
            } catch (e: UploadException) {
                throw e
            } catch (e: IOException) {
                throw UploadException("الاتصال انقطع أثناء الرفع. تأكد من الإنترنت وحاول مرة ثانية.", e)
            } catch (e: SecurityException) {
                throw UploadException("انتهت صلاحية الوصول للملف. اختره مرة ثانية.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed: ${e.message}", e)
                throw UploadException("تعذّر رفع الملف: ${e.message ?: "خطأ غير معروف"}", e)
            }
        }
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

/** روابط Cloudinary المحسّنة: صورة مصغّرة بالحجم المطلوب، وصورة غلاف للفيديو. */
object CloudinaryUrls {
    private fun isCloudinary(url: String) = url.contains("res.cloudinary.com") && url.contains("/upload/")

    /** صورة بعرض محدد وتنسيق وجودة تلقائية (أخف على الباقة). */
    fun image(url: String, width: Int): String =
        if (!isCloudinary(url)) url
        else url.replaceFirst("/upload/", "/upload/f_auto,q_auto,c_limit,w_$width/")

    /** صورة غلاف من الثانية الأولى للفيديو. */
    fun videoThumbnail(url: String, width: Int): String? {
        if (!isCloudinary(url) || !url.contains("/video/upload/")) return null
        val withTransform = url.replaceFirst("/upload/", "/upload/so_1,f_jpg,q_auto,c_limit,w_$width/")
        return withTransform.substringBeforeLast('.') + ".jpg"
    }
}
