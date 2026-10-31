package com.example.data.model

import java.util.Calendar
import java.util.Locale

/** 📅 إعدادات «اطلب مسبقاً» للمحل (preorderCfg/{uid}) — نفس منطق الويب (logic.js) */
data class PreorderCfg(
    val on: Boolean = false, val maxDays: Int = 0, val prepMin: Int = 30,
    val pickup: Boolean = true, val delivery: Boolean = true,
    val delFrom: Int = -1, val delTo: Int = -1, val slotMin: Int = 60, val slotCap: Int = 1
) {
    val active get() = on && (pickup || delivery)
    fun toMap(): Map<String, Any> = mapOf(
        "on" to on, "maxDays" to maxDays.coerceIn(0, 14).toLong(), "prepMin" to prepMin.coerceIn(0, 720).toLong(),
        "pickup" to pickup, "delivery" to delivery, "delFrom" to delFrom.toLong(), "delTo" to delTo.toLong(),
        "slotMin" to (if (slotMin in listOf(30, 60, 120)) slotMin else 60).toLong(), "slotCap" to slotCap.coerceIn(1, 50).toLong()
    )
}

data class PreSlot(val t: Long, val end: Long, val key: String)

object PreOrder {
    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
    fun dayStart(t: Long): Long = cal(t).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun p2(n: Int) = n.toString().padStart(2, '0')
    /** مفتاح الفترة: YYYYMMDD-HHMM */
    fun slotKey(t: Long): String { val c = cal(t)
        return "${c.get(Calendar.YEAR)}${p2(c.get(Calendar.MONTH) + 1)}${p2(c.get(Calendar.DAY_OF_MONTH))}-${p2(c.get(Calendar.HOUR_OF_DAY))}${p2(c.get(Calendar.MINUTE))}" }
    private fun win(a: Int, b: Int): Pair<Int, Int>? =
        if (a in 0..1439 && b in 0..1439) (if (a == b) 0 to 1440 else a to (if (b > a) b else b + 1440)) else null
    fun days(c: PreorderCfg, now: Long = System.currentTimeMillis()): List<Long> {
        val s = dayStart(now)
        return (0..c.maxDays.coerceIn(0, 14)).map { i -> cal(s).apply { add(Calendar.DAY_OF_MONTH, i) }.timeInMillis }
    }
    fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
        val diff = Math.round((dayStart(t) - dayStart(now)) / 86_400_000.0).toInt()
        if (diff == 0) return "اليوم"
        if (diff == 1) return "بكرة"
        val c = cal(t)
        return "${java.text.SimpleDateFormat("EEEE", Locale("ar")).format(c.time)} ${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}"
    }
    /** التوصيل: فترات داخل نافذة المحل. الاستلام: كل ربع ساعة داخل الدوام */
    fun times(c: PreorderCfg, branch: Branch?, mode: String, day: Long, now: Long = System.currentTimeMillis()): List<PreSlot> {
        val del = mode == "delivery"
        val w = (if (del && c.delFrom >= 0) win(c.delFrom, c.delTo) else null) ?: branch?.let { win(it.openMinute, it.closeMinute) } ?: (0 to 1440)
        val step = if (del) c.slotMin else 15
        val min = now + c.prepMin * 60_000L
        val out = mutableListOf<PreSlot>()
        var m = w.first
        while (if (del) m + step <= w.second else m < w.second) {
            val t = cal(day).apply { add(Calendar.MINUTE, m) }.timeInMillis
            if (t >= min) out += PreSlot(t, if (del) t + step * 60_000L else 0L, slotKey(t))
            m += step
        }
        return out
    }
    fun timeOf(t: Long): String { val c = cal(t); return OfferTime.formatMinute(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) }
    fun whenText(t: Long, now: Long = System.currentTimeMillis()) = "${dayLabel(t, now)} ${timeOf(t)}"
}
