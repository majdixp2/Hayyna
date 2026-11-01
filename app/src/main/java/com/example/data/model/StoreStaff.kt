package com.example.data.model

/** 👥 موظف متجر: يدخل برقم المتجر + رقمه + رمز سري، ويشوف الطلبات و«نفد اليوم» بس. */
data class StoreStaff(
    val uid: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val code: String = "",
    val n: Int = 0,
    val name: String = "",
    val role: String = "cashier",
    val perms: List<String> = emptyList(),
    val active: Boolean = true,
    val email: String = "",
    /** 📍 فروعه (فاضي = كل الفروع) */
    val branchIds: List<String> = emptyList()
) {
    val displayName: String get() = name.ifBlank { "موظف $n" }
    val canOrders: Boolean get() = active && "orders" in perms
    val canStock: Boolean get() = active && "stock" in perms
    val roleLabel: String get() = ROLES[role]?.first ?: role

    companion object {
        const val DOMAIN = "staff.lacover.app"
        fun isStaffEmail(email: String?) = email?.lowercase()?.endsWith("@$DOMAIN") == true
        /** الدور ← (الاسم، الصلاحيات الافتراضية، الشرح) */
        val ROLES = linkedMapOf(
            "cashier" to Triple("💳 كاشير", listOf("orders", "stock"), "يستقبل الطلبات ويغيّر حالتها، ويعلّم الأصناف «نفد»"),
            "kitchen" to Triple("👤 مقدم خدمة", listOf("orders"), "يشوف الطلبات ويحدّث حالتها (يتجهّز / جاهز)")
        )
        val PERMS = linkedMapOf("orders" to "🧾 الطلبات (استقبال وتغيير الحالة)", "stock" to "🍽️ «نفد اليوم» للأصناف")
    }
}
