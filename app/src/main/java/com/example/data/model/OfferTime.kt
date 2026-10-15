package com.example.data.model

import java.util.Calendar

/** أدوات الوقت الخاصة بالعروض: الانتهاء، العد التنازلي، و"مفتوح الآن". */
object OfferTime {

    fun isExpired(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = offer.expiresAt ?: return false
        return exp.toDate().time <= nowMillis
    }

    /** نص العد التنازلي مثل "باقي 3 ساعات" أو null إذا العرض بلا انتهاء. */
    fun remainingText(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): String? {
        val exp = offer.expiresAt ?: return null
        val diffMin = (exp.toDate().time - nowMillis) / 60_000L
        return when {
            diffMin <= 0 -> "انتهى العرض"
            diffMin < 60 -> "باقي ${diffMin} دقيقة"
            diffMin < 60 * 24 -> {
                val h = diffMin / 60
                when (h) {
                    1L -> "باقي ساعة"
                    2L -> "باقي ساعتين"
                    in 3L..10L -> "باقي $h ساعات"
                    else -> "باقي $h ساعة"
                }
            }
            else -> {
                val d = diffMin / (60 * 24)
                when (d) {
                    1L -> "باقي يوم"
                    2L -> "باقي يومين"
                    in 3L..10L -> "باقي $d أيام"
                    else -> "باقي $d يوم"
                }
            }
        }
    }

    /** أقل من 3 ساعات = عرض ينتهي قريباً (يُبرز بلون مختلف). */
    fun isEndingSoon(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = offer.expiresAt ?: return false
        val diff = exp.toDate().time - nowMillis
        return diff in 1..(3 * 60 * 60 * 1000L)
    }

    fun hasHours(offer: RestaurantOffer): Boolean =
        offer.openMinute in 0..1439 && offer.closeMinute in 0..1439

    /** هل المطعم مفتوح الآن؟ null إذا أوقات الدوام غير محددة. */
    fun isOpenNow(offer: RestaurantOffer, calendar: Calendar = Calendar.getInstance()): Boolean? {
        if (!hasHours(offer)) return null
        val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val open = offer.openMinute
        val close = offer.closeMinute
        return when {
            open == close -> true // 24 ساعة
            open < close -> now in open until close
            else -> now >= open || now < close // دوام بعد منتصف الليل
        }
    }

    fun formatMinute(minute: Int): String {
        val h24 = minute / 60
        val m = minute % 60
        val period = if (h24 < 12) "ص" else "م"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return if (m == 0) "$h12 $period" else "$h12:${m.toString().padStart(2, '0')} $period"
    }

    fun hoursText(offer: RestaurantOffer): String =
        if (hasHours(offer)) {
            if (offer.openMinute == offer.closeMinute) "مفتوح 24 ساعة"
            else "يومياً من ${formatMinute(offer.openMinute)} حتى ${formatMinute(offer.closeMinute)}"
        } else offer.workingHours
}
