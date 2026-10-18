package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class MediaType {
    IMAGE,
    VIDEO
}

@IgnoreExtraProperties
data class RestaurantOffer(
    // لا نستخدم @DocumentId لأن الوثيقة تحتوي حقل "id" أيضاً، والجمع بينهما يسبب انهيار التطبيق عند القراءة.
    // المعرّف يُضبط يدوياً في المستودع عبر copy(id = doc.id).
    val id: String = "",
    val merchantId: String = "",
    val restaurantName: String = "",
    val offerTitle: String = "",
    val description: String = "",
    val cuisineType: String = "",
    val price: Double = 0.0,
    val originalPrice: Double? = null,
    val currency: String = "ر.س",
    val discountBadge: String = "",
    val mediaType: MediaType = MediaType.IMAGE,
    val mediaUri: String = "",
    @get:PropertyName("isPromotionalVideo") @field:PropertyName("isPromotionalVideo")
    val isPromotionalVideo: Boolean = false,
    val district: String = "",
    val region: String = "",
    val latitude: Double = 24.7136,
    val longitude: Double = 46.6753,
    val phone: String = "+966501234567",
    val workingHours: String = "يومياً من 12:00 م حتى 1:30 ص",
    val rating: Double = 4.8,
    val distanceKm: Double = 0.0,
    val driveTimeMinutes: Int = 0,
    @get:PropertyName("isApproved") @field:PropertyName("isApproved")
    val isApproved: Boolean = true,
    @get:PropertyName("isFeatured") @field:PropertyName("isFeatured")
    val isFeatured: Boolean = false,
    val viewsCount: Int = 0,
    // عدد مرات الضغط على "الاتجاه" و"واتساب" (إحصائيات حقيقية للتاجر)
    val directionsCount: Int = 0,
    val whatsappCount: Int = 0,
    // عدد البلاغات (3 أو أكثر = يختفي تلقائياً لين يراجعه الأدمن)
    val reportsCount: Int = 0,
    // وقت الدوام بالدقائق من منتصف الليل (-1 = غير محدد). يدعم الدوام بعد منتصف الليل (مثلاً 13:00 حتى 02:00)
    val openMinute: Int = -1,
    val closeMinute: Int = -1,
    // وقت انتهاء العرض (null = بدون انتهاء)
    val expiresAt: Timestamp? = null,
    // وقت بداية العرض (null = يبدأ فوراً). قبله يطلع "قريباً" على الخريطة
    val startsAt: Timestamp? = null,
    // خصم بالنسبة بدون سعر محدد (مثل: 30% على جميع الأصناف). 0 = عرض بسعر عادي
    val discountPercent: Int = 0,
    // خصم متدرّج حسب الأيام: [50, 30, 30] = اليوم الأول 50% ثم اليوم الثاني والثالث 30%
    val dailyPercents: List<Long> = emptyList(),
    // نطاق الخصم، مثل "على جميع الأصناف"
    val itemsScope: String = "",
    // عرض افتتاح محل جديد 🎉
    val grandOpening: Boolean = false,
    // وسوم الأصناف للبحث: شاورما، برجر، بروست
    val tags: List<String> = emptyList(),
    // التوصيل (نسخة من ملف المتجر)
    val delivery: Boolean = false,
    val deliveryFee: Double = 0.0,
    val deliveryMinOrder: Double = 0.0,
    // صور العرض (حتى 3). mediaUri يبقى الصورة الأولى/الغلاف للتوافق مع العروض القديمة.
    val mediaUris: List<String> = emptyList(),
    // شعار المتجر (يظهر صغير على أيقونة الخريطة)
    val logoUrl: String = "",
    // الفروع اللي يشملها العرض (نسخة من ملف المطعم وقت النشر)
    val branches: List<Branch> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    // محلي فقط (ما ينحفظ): الفرع اللي اختاره المستخدم أو الأقرب له
    @get:com.google.firebase.firestore.Exclude
    val selectedBranchId: String = ""
)
