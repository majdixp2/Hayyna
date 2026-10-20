package com.example.data.model

/**
 * ملف المطعم: يُحفظ تلقائياً بعد كل عرض ينشره التاجر،
 * ويُستخدم لتعبئة العرض الجديد (الاسم، الرقم، الموقع، الدوام) بدون ما يكتبها من جديد.
 */
data class MerchantProfile(
    val restaurantName: String = "",
    val phone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val region: String = "",
    val district: String = "",
    val cuisineType: String = "",
    val openMinute: Int = -1,
    val closeMinute: Int = -1,
    val logoUrl: String = "",
    val branches: List<Branch> = emptyList(),
    // صفحة المتجر الدائمة
    val coverUrl: String = "",
    val about: String = "",
    val priceFrom: Double = 0.0,
    val menuImages: List<String> = emptyList(),
    // التوصيل
    val delivery: Boolean = false,
    val deliveryFee: Double = 0.0,
    val deliveryMinOrder: Double = 0.0,
    // توصيل مجاني للطلبات فوق هذا المبلغ (0 = ما فيه)
    val deliveryFreeOver: Double = 0.0
) {
    val hasLocation: Boolean get() = latitude != 0.0 || longitude != 0.0
}
