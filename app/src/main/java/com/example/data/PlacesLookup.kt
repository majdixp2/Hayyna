package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * رقم محل من خرائط Google (Places API New) — نطلبه لحظة الضغط على "ادعه" بس، وما نخزّنه.
 * يستخدم نفس مفتاح الخرائط (لازم Places API (New) مضافة لقيود المفتاح).
 */
object PlacesLookup {
    private const val TAG = "PlacesLookup"
    private val client by lazy { OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build() }

    data class Info(
        val name: String, val phone: String,
        val address: String = "", val district: String = "", val city: String = "",
        val lat: Double? = null, val lng: Double? = null
    )

    suspend fun lookup(context: Context, placeId: String): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val key = mapsKey(context) ?: return@runCatching null
            val req = Request.Builder()
                .url("https://places.googleapis.com/v1/places/$placeId?languageCode=ar")
                .header("X-Goog-Api-Key", key)
                .header("X-Goog-FieldMask", "displayName,nationalPhoneNumber,internationalPhoneNumber,formattedAddress,addressComponents,location")
                .header("X-Android-Package", context.packageName)
                .apply { certSha1(context)?.let { header("X-Android-Cert", it) } }
                .build()
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) { Log.w(TAG, "places ${r.code}: ${body.take(300)}"); return@use null }
                val j = JSONObject(body)
                val comps = j.optJSONArray("addressComponents")
                fun comp(type: String): String {
                    if (comps == null) return ""
                    for (i in 0 until comps.length()) {
                        val c = comps.optJSONObject(i) ?: continue
                        val types = c.optJSONArray("types") ?: continue
                        if ((0 until types.length()).any { types.optString(it) == type }) return c.optString("longText")
                    }
                    return ""
                }
                val loc = j.optJSONObject("location")
                Info(
                    name = j.optJSONObject("displayName")?.optString("text").orEmpty(),
                    phone = j.optString("internationalPhoneNumber").ifBlank { j.optString("nationalPhoneNumber") },
                    address = j.optString("formattedAddress"),
                    district = comp("sublocality_level_1").ifBlank { comp("sublocality") }.ifBlank { comp("neighborhood") },
                    city = comp("locality").ifBlank { comp("administrative_area_level_2") }.ifBlank { comp("administrative_area_level_1") },
                    lat = loc?.optDouble("latitude")?.takeIf { !it.isNaN() },
                    lng = loc?.optDouble("longitude")?.takeIf { !it.isNaN() }
                )
            }
        }.getOrNull()
    }

    data class Found(val placeId: String, val name: String, val address: String, val lat: Double, val lng: Double, val district: String = "", val city: String = "")

    /** بحث عن محلات بالاسم في خرائط Google (Text Search) — قريب من موقع معيّن لو متوفر. */
    suspend fun searchText(context: Context, query: String, lat: Double?, lng: Double?, max: Int = 8): List<Found> = withContext(Dispatchers.IO) {
        runCatching {
            val key = mapsKey(context) ?: return@runCatching emptyList()
            val body = JSONObject().apply {
                put("textQuery", query); put("languageCode", "ar"); put("regionCode", "SA"); put("maxResultCount", max.coerceIn(1, 20))
                if (lat != null && lng != null) put("locationBias", JSONObject().put("circle", JSONObject()
                    .put("center", JSONObject().put("latitude", lat).put("longitude", lng)).put("radius", 30000.0)))
            }
            val req = Request.Builder()
                .url("https://places.googleapis.com/v1/places:searchText")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("X-Goog-Api-Key", key)
                .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location,places.addressComponents")
                .header("X-Android-Package", context.packageName)
                .apply { certSha1(context)?.let { header("X-Android-Cert", it) } }
                .build()
            client.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) { Log.w(TAG, "search ${r.code}: ${txt.take(300)}"); return@use emptyList() }
                val arr = JSONObject(txt).optJSONArray("places") ?: return@use emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val p = arr.optJSONObject(i) ?: return@mapNotNull null
                    val loc = p.optJSONObject("location") ?: return@mapNotNull null
                    val comps = p.optJSONArray("addressComponents")
                    fun comp(type: String): String {
                        if (comps == null) return ""
                        for (k in 0 until comps.length()) {
                            val c = comps.optJSONObject(k) ?: continue
                            val types = c.optJSONArray("types") ?: continue
                            if ((0 until types.length()).any { types.optString(it) == type }) return c.optString("longText")
                        }
                        return ""
                    }
                    Found(p.optString("id"), p.optJSONObject("displayName")?.optString("text").orEmpty(),
                        p.optString("formattedAddress"), loc.optDouble("latitude"), loc.optDouble("longitude"),
                        district = comp("sublocality_level_1").ifBlank { comp("sublocality") }.ifBlank { comp("neighborhood") },
                        city = comp("locality").ifBlank { comp("administrative_area_level_2") })
                }.filter { it.placeId.isNotBlank() && !it.lat.isNaN() }
            }
        }.getOrDefault(emptyList())
    }

    /** «حي الحمدانية» → «الحمدانية» (للاسم التلقائي للفرع) */
    fun cleanDistrict(d: String): String = d.trim().removePrefix("حي ").trim()

    /** رقم جوال سعودي (عليه واتساب غالباً) بصيغة 9665xxxxxxxx، وإلا null. */
    fun saudiMobile(phone: String): String? {
        val d = phone.filter { it.isDigit() }
        return when {
            d.startsWith("9665") && d.length == 12 -> d
            d.startsWith("009665") && d.length == 14 -> d.drop(2)
            d.startsWith("05") && d.length == 10 -> "966" + d.drop(1)
            d.startsWith("5") && d.length == 9 -> "966$d"
            else -> null
        }
    }

    private fun mapsKey(context: Context): String? = runCatching {
        val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        ai.metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun certSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
