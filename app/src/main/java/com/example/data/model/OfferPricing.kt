package com.example.data.model

import java.util.Calendar

/**
 * حساب الخصم الفعلي للعرض:
 * - عرض بسعر: النسبة من السعرين.
 * - عرض بالنسبة (على جميع الأصناف): discountPercent.
 * - عرض متدرّج: dailyPercents حسب اليوم (اليوم الأول 50% ثم 30%...).
 * - عرض قادم: startsAt في المستقبل.
 */
object OfferPricing {

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun startMillis(offer: RestaurantOffer): Long? =
        (offer.startsAt ?: offer.createdAt)?.toDate()?.time

    fun isUpcoming(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val s = offer.startsAt ?: return false
        return s.toDate().time > nowMillis
    }

    /** رقم اليوم من بداية العرض (0 = اليوم الأول) بحسب أيام التقويم. */
    fun dayIndex(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): Int {
        val start = startMillis(offer) ?: return 0
        if (nowMillis <= start) return 0
        return ((midnight(nowMillis) - midnight(start) + DAY_MS / 2) / DAY_MS).toInt().coerceAtLeast(0)
    }

    /** نسبة الخصم الحالية (0 = ما فيه خصم بالنسبة). */
    fun currentPercent(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): Int {
        val steps = offer.dailyPercents
        if (steps.isNotEmpty()) {
            val i = dayIndex(offer, nowMillis)
            return (steps.getOrNull(i) ?: steps.last()).toInt()
        }
        return offer.discountPercent
    }

    /** الخصم الفعلي سواء من السعرين أو من النسبة. */
    fun effectivePercent(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): Int {
        val original = offer.originalPrice
        if (original != null && original > 0 && offer.price > 0 && offer.price < original) {
            return ((original - offer.price) / original * 100).toInt()
        }
        return currentPercent(offer, nowMillis)
    }

    /** هل هو عرض بالنسبة بدون سعر محدد؟ */
    fun isPercentOnly(offer: RestaurantOffer): Boolean =
        offer.price <= 0 && (offer.discountPercent > 0 || offer.dailyPercents.isNotEmpty())

    /** وصف التدرّج: "اليوم الأول 50% • الثاني والثالث 30%". */
    fun tiersText(offer: RestaurantOffer): String? {
        val steps = offer.dailyPercents.map { it.toInt() }
        if (steps.size < 2) return null
        val groups = mutableListOf<Triple<Int, Int, Int>>() // from, to, pct
        steps.forEachIndexed { i, p ->
            val last = groups.lastOrNull()
            if (last != null && last.third == p && last.second == i - 1) groups[groups.size - 1] = last.copy(second = i)
            else groups.add(Triple(i, i, p))
        }
        return groups.joinToString(" • ") { (from, to, pct) ->
            val label = when {
                from == to -> "اليوم ${ordinal(from)}"
                to == from + 1 -> "اليوم ${ordinal(from)} و${ordinal(to)}"
                else -> "من اليوم ${ordinal(from)} إلى ${ordinal(to)}"
            }
            "$label $pct%"
        }
    }

    /** "يبدأ بكرة" / "يبدأ السبت 17 أكتوبر" / "يبدأ بعد 3 ساعات". */
    fun startsText(offer: RestaurantOffer, nowMillis: Long = System.currentTimeMillis()): String? {
        val s = offer.startsAt?.toDate()?.time ?: return null
        if (s <= nowMillis) return null
        val diffMin = (s - nowMillis) / 60_000L
        val days = ((midnight(s) - midnight(nowMillis) + DAY_MS / 2) / DAY_MS).toInt()
        return when {
            diffMin < 60 -> "يبدأ بعد ${diffMin.coerceAtLeast(1)} دقيقة"
            days == 0 -> "يبدأ اليوم ${OfferTime.formatMinute(minuteOfDay(s))}"
            days == 1 -> "يبدأ بكرة"
            else -> "يبدأ ${dateText(s)}"
        }
    }

    fun dateText(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${DAY_NAMES[c.get(Calendar.DAY_OF_WEEK) - 1]} ${c.get(Calendar.DAY_OF_MONTH)} ${MONTH_NAMES[c.get(Calendar.MONTH)]}"
    }

    fun midnight(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun minuteOfDay(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun ordinal(i: Int): String = ORDINALS.getOrElse(i) { "${i + 1}" }

    private val ORDINALS = listOf("الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر")
    private val DAY_NAMES = listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val MONTH_NAMES = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
}

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** نص السعر للعرض: "25 ر.س" أو "خصم 30%" لعروض النسبة. */
fun RestaurantOffer.priceLabel(nowMillis: Long = System.currentTimeMillis(), withScope: Boolean = false): String =
    if (OfferPricing.isPercentOnly(this)) {
        val p = OfferPricing.currentPercent(this, nowMillis)
        if (withScope && itemsScope.isNotBlank()) "خصم $p% $itemsScope" else "خصم $p%"
    } else "${fmt(price)} $currency"
