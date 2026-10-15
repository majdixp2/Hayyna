package com.example.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** يحوّل الإحداثيات إلى منطقة وحي بالعربي باستخدام خدمة Google المدمجة في أندرويد (مجانية). */
object ReverseGeocoder {

    data class Place(
        val region: String,   // مثل "منطقة مكة المكرمة - جدة"
        val district: String  // مثل "حي الوفاء"
    )

    suspend fun lookup(context: Context, lat: Double, lng: Double): Place? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale("ar")).getFromLocation(lat, lng, 1)?.firstOrNull()
                ?: return@withContext null

            val area = address.adminArea?.trim().orEmpty()
            val city = address.locality?.trim().orEmpty()
            val region = listOf(area, city)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" - ")

            val rawDistrict = (address.subLocality ?: address.subAdminArea ?: address.thoroughfare)?.trim().orEmpty()
            val district = when {
                rawDistrict.isBlank() -> ""
                rawDistrict.startsWith("حي") -> rawDistrict
                else -> "حي $rawDistrict"
            }
            Place(region = region, district = district)
        } catch (e: Exception) {
            null
        }
    }
}
