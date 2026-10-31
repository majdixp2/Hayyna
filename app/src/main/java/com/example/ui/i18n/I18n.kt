package com.example.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

/**
 * 🌐 اللغة والمظهر.
 * العربي هو الأساس. لما يختار المستخدم English نترجم نصوص الواجهة وهي تنرسم من القاموس المشترك مع الويب
 * (web/i18n/en.json — ينضاف للتطبيق كـ asset وقت البناء). محتوى التجار (أسماء المحلات والعروض) ما يترجم.
 */
object I18n {
    private const val PREFS = "lc_prefs"
    var lang by mutableStateOf("ar"); private set
    /** light | dark | auto — الافتراضي فاتح */
    var theme by mutableStateOf("light"); private set
    val isEn get() = lang == "en"

    private var dict: Map<String, String> = emptyMap()
    private var rules: List<Pair<Regex, String>> = emptyList()
    private var keysRe: Regex? = null
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 1500
    }
    private val arabic = Regex("[\\u0600-\\u06FF]")

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lang = p.getString("lang", "ar") ?: "ar"
        theme = p.getString("theme", "light") ?: "light"
        if (isEn && dict.isEmpty()) load(ctx)
    }

    private fun load(ctx: Context) = runCatching {
        val j = JSONObject(ctx.assets.open("en.json").bufferedReader().use { it.readText() })
        val d = j.getJSONObject("dict")
        dict = buildMap { d.keys().forEach { k -> put(k, d.getString(k)) } }
        val r = j.getJSONArray("rules")
        rules = (0 until r.length()).mapNotNull { i ->
            val a = r.getJSONArray(i)
            runCatching { Regex(a.getString(0)) to a.getString(1) }.getOrNull()
        }
        val l = "\\u0621-\\u064A\\u064B-\\u0652\\u0671-\\u06D3"
        val keys = dict.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        keysRe = if (keys.isEmpty()) null else Regex("(?<![$l])($keys)(?![$l])")
    }.onFailure { android.util.Log.w("I18n", "load: ${it.message}") }

    /** يترجم نص الواجهة (لو اللغة إنجليزي) */
    fun tr(text: String): String {
        if (!isEn || text.isEmpty() || !arabic.containsMatchIn(text)) return text
        synchronized(cache) { cache[text]?.let { return it } }
        val core = text.trim()
        val out = dict[core]?.let { text.replace(core, it) } ?: run {
            var s = text
            for ((re, rep) in rules) s = re.replace(s, rep)
            keysRe?.replace(s) { m -> dict[m.value] ?: m.value } ?: s
        }
        synchronized(cache) { cache[text] = out }
        return out
    }

    fun setLang(ctx: Context, l: String) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", l).apply(); lang = l; cache.clear(); if (l == "en") load(ctx) }
    fun setTheme(ctx: Context, t: String) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("theme", t).apply(); theme = t }

    /** الخريطة وأسماء الأماكن تتبع لغة التطبيق */
    fun wrap(base: Context): Context {
        val l = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "ar") ?: "ar"
        if (l != "en") return base
        val loc = Locale.ENGLISH
        Locale.setDefault(loc)
        val conf = Configuration(base.resources.configuration).apply { setLocale(loc); setLayoutDirection(loc) }
        return base.createConfigurationContext(conf)
    }
}

/** اختصار: نص واجهة مترجم */
fun t(s: String) = I18n.tr(s)
