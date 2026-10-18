package com.example.data

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.tasks.await
import java.io.File

/** يطلّع "رمز التجربة" حق App Check عشان الأدمن يسجّله في Firebase. */
object AppCheckHelper {
    private val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    suspend fun debugToken(context: Context): String? {
        // نطلب توكن عشان المكتبة تنشئ الرمز وتحفظه
        runCatching { FirebaseAppCheck.getInstance().getAppCheckToken(false).await() }
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val files = dir.listFiles { f -> f.name.contains("appcheck", ignoreCase = true) && f.name.contains("debug", ignoreCase = true) }
            ?: return null
        for (f in files) {
            val prefs = context.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
            prefs.all.values.forEach { v ->
                val s = v as? String ?: return@forEach
                uuidRegex.find(s)?.let { return it.value }
            }
        }
        return null
    }
}
