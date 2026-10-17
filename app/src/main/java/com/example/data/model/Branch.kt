package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/** فرع المطعم: موقعه ورقمه ودوامه. يُحفظ في ملف المطعم، وتُنسخ الفروع المختارة داخل كل عرض. */
@IgnoreExtraProperties
data class Branch(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val region: String = "",
    val district: String = "",
    val phone: String = "",
    val openMinute: Int = -1,
    val closeMinute: Int = -1
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "latitude" to latitude,
        "longitude" to longitude,
        "region" to region,
        "district" to district,
        "phone" to phone,
        "openMinute" to openMinute,
        "closeMinute" to closeMinute
    )

    /** اسم قصير للعرض: "فرع العليا" أو الحي إذا ما له اسم. */
    val displayName: String get() = name.ifBlank { district.ifBlank { "الفرع" } }
}

/** نسخة من العرض بموقع فرع معيّن (للمسافة والاتجاه والاتصال والدوام). */
fun RestaurantOffer.atBranch(branch: Branch): RestaurantOffer = copy(
    latitude = branch.latitude,
    longitude = branch.longitude,
    region = branch.region.ifBlank { region },
    district = branch.district.ifBlank { district },
    phone = branch.phone.ifBlank { phone },
    openMinute = if (branch.openMinute >= 0) branch.openMinute else openMinute,
    closeMinute = if (branch.closeMinute >= 0) branch.closeMinute else closeMinute,
    selectedBranchId = branch.id
)

/** فروع العرض؛ العروض القديمة (بدون فروع) تُعامل كفرع واحد من موقعها الأساسي. */
val RestaurantOffer.effectiveBranches: List<Branch>
    get() = branches.ifEmpty {
        listOf(
            Branch(
                id = "main",
                latitude = latitude,
                longitude = longitude,
                region = region,
                district = district,
                phone = phone,
                openMinute = openMinute,
                closeMinute = closeMinute
            )
        )
    }

/** صور العرض (العروض القديمة فيها صورة وحدة في mediaUri). */
val RestaurantOffer.gallery: List<String>
    get() = mediaUris.filter { it.isNotBlank() }.ifEmpty { listOf(mediaUri).filter { it.isNotBlank() } }
