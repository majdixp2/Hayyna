package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** مشاركة العرض (صورة + وصف + رابط الموقع) على واتساب أو أي تطبيق. */
object OfferSharer {

    fun shareText(offer: RestaurantOffer): String = buildString {
        append("🔥 ").append(offer.offerTitle).append('\n')
        append("🍽️ ").append(offer.restaurantName)
        if (offer.district.isNotBlank()) append(" — ").append(offer.district)
        append('\n')
        val price = if (offer.price % 1.0 == 0.0) offer.price.toLong().toString() else offer.price.toString()
        append("💰 ").append(price).append(' ').append(offer.currency)
        offer.originalPrice?.takeIf { it > offer.price }?.let { orig ->
            val o = if (orig % 1.0 == 0.0) orig.toLong().toString() else orig.toString()
            append(" بدل ").append(o)
        }
        append('\n')
        append("📍 https://maps.google.com/?q=").append(offer.latitude).append(',').append(offer.longitude).append('\n')
        append('\n').append("شفته في تطبيق حيّنا 📍")
    }

    suspend fun share(context: Context, offer: RestaurantOffer) {
        val text = shareText(offer)
        val imageUrl = when {
            !offer.mediaUri.startsWith("http") -> null
            offer.mediaType == MediaType.VIDEO -> CloudinaryUrls.videoThumbnail(offer.mediaUri, 1080)
            else -> CloudinaryUrls.image(offer.mediaUri, 1080)
        }
        val imageUri = imageUrl?.let { url ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(url).allowHardware(false).build()
                    ) as? SuccessResult ?: return@runCatching null
                    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                    val file = File(dir, "hayyna_offer.jpg")
                    file.outputStream().use { result.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            if (imageUri != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة العرض").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
