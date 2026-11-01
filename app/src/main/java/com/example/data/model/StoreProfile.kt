package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/** صفحة المتجر العامة (دائمة): تظهر للعملاء حتى لو ما عنده عروض. */
@IgnoreExtraProperties
data class StoreProfile(
    val uid: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val coverUrl: String = "",
    val about: String = "",
    val category: String = "",
    val priceFrom: Double = 0.0,
    val menuImages: List<String> = emptyList(),
    val branches: List<Branch> = emptyList(),
    val phone: String = "",
    val approved: Boolean = false,
    // ✓ موثّق (يحطها الأدمن بعد التحقق من السجل/العمل الحر)
    val verified: Boolean = false,
    val delivery: Boolean = false,
    val deliveryFee: Double = 0.0,
    val deliveryMinOrder: Double = 0.0,
    val deliveryFreeOver: Double = 0.0,
    val vatNumber: String = "",
    val curbside: Boolean = false,
    val payCash: Boolean = true,
    val payCard: Boolean = true
) {
    /** اسم قسم المنيو حسب نوع المتجر. */
    val menuLabel: String
        get() = when {
            category.contains("مطاعم") || category.contains("مقاهي") -> "المنيو"
            category.contains("خياط") || category.contains("حلاقة") || category.contains("خدمات") -> "الخدمات والأسعار"
            else -> "المنتجات والأسعار"
        }

    companion object {
        const val MAX_MENU_IMAGES = 10

        fun from(uid: String, p: MerchantProfile, approved: Boolean) = StoreProfile(
            uid = uid,
            name = p.restaurantName,
            logoUrl = p.logoUrl,
            coverUrl = p.coverUrl,
            about = p.about,
            category = p.cuisineType,
            priceFrom = p.priceFrom,
            menuImages = p.menuImages.take(MAX_MENU_IMAGES),
            branches = p.branches,
            phone = p.branches.firstOrNull { it.phone.isNotBlank() }?.phone ?: p.phone,
            approved = approved,
            delivery = p.delivery,
            deliveryFee = p.deliveryFee,
            deliveryMinOrder = p.deliveryMinOrder,
            deliveryFreeOver = p.deliveryFreeOver,
            curbside = p.curbside, payCash = p.payCash, payCard = p.payCard
        )
    }
}
