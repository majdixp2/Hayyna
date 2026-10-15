package com.example.data

/**
 * نقطة واحدة لتسجيل تفاعل العملاء مع العروض (الاتجاه / واتساب) في إحصائيات التاجر.
 * الـ ViewModel يضبط [handler] عند الإنشاء، والأزرار تستدعي الدوال مباشرة.
 */
object OfferTracker {
    @Volatile
    var handler: ((offerId: String, field: String) -> Unit)? = null

    fun directions(offerId: String) = handler?.invoke(offerId, "directionsCount")
    fun whatsapp(offerId: String) = handler?.invoke(offerId, "whatsappCount")
}
