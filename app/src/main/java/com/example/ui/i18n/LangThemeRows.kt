package com.example.ui.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Context.findActivity(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

/** يبدّل اللغة ويعيد فتح الشاشة (عشان الخريطة واتجاه الصفحة يتبعون اللغة) */
fun switchLang(ctx: Context) { I18n.setLang(ctx.applicationContext, if (I18n.isEn) "ar" else "en"); ctx.findActivity()?.recreate() }
/** فاتح ← داكن ← حسب الجوال */
fun nextTheme(ctx: Context) = I18n.setTheme(ctx.applicationContext, when (I18n.theme) { "light" -> "dark"; "dark" -> "auto"; else -> "light" })
fun themeLabel() = when (I18n.theme) { "dark" -> "🌙 المظهر: داكن"; "auto" -> "🌗 المظهر: حسب الجوال"; else -> "☀️ المظهر: فاتح" }
const val LANG_LABEL_AR = "🌐 اللغة: العربية"

/** سطرين في الإعدادات: اللغة والمظهر */
@Composable
fun LangThemeRows() {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { switchLang(ctx) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(if (I18n.isEn) "🌐 Language: English" else LANG_LABEL_AR, fontSize = 14.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Text(if (I18n.isEn) "العربية" else "English", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().clickable { nextTheme(ctx) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(themeLabel(), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("تغيير ›", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}
