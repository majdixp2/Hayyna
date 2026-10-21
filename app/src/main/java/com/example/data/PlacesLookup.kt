package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    data class Info(val name: String, val phone: String)

    suspend fun lookup(context: Context, placeId: String): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val key = mapsKey(context) ?: return@runCatching null
            val req = Request.Builder()
                .url("https://places.googleapis.com/v1/places/$placeId?languageCode=ar")
                .header("X-Goog-Api-Key", key)
                .header("X-Goog-FieldMask", "displayName,nationalPhoneNumber,internationalPhoneNumber")
                .header("X-Android-Package", context.packageName)
                .apply { certSha1(context)?.let { header("X-Android-Cert", it) } }
                .build()
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) { Log.w(TAG, "places ${r.code}: ${body.take(300)}"); return@use null }
                val j = JSONObject(body)
                Info(
                    name = j.optJSONObject("displayName")?.optString("text").orEmpty(),
                    phone = j.optString("internationalPhoneNumber").ifBlank { j.optString("nationalPhoneNumber") }
                )
            }
        }.getOrNull()
    }

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
