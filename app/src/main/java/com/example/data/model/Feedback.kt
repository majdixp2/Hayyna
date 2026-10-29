package com.example.data.model

/** رسالة من مستخدم لإدارة لكوفر (مشكلة / اقتراح / فكرة / شكوى). */
data class Feedback(
    val id: String = "",
    val uid: String? = null,
    val userName: String = "",
    val role: String = "",
    val type: String = "",
    val message: String = "",
    val screenshotUrl: String = "",
    val contact: String = "",
    val appVersion: String = "",
    val device: String = "",
    val status: String = STATUS_NEW,
    val reply: String = "",
    val replySeen: Boolean = true,
    val createdAtMillis: Long = 0L
) {
    companion object {
        const val STATUS_NEW = "new"
        const val STATUS_PROGRESS = "in_progress"
        const val STATUS_DONE = "done"

        val TYPES = listOf("🐞 مشكلة", "💡 اقتراح", "✨ فكرة إضافية", "🚩 شكوى على متجر", "أخرى")
        const val DEFAULT_SUPPORT_WHATSAPP = "0552793379"

        fun statusLabel(status: String) = when (status) {
            STATUS_PROGRESS -> "قيد المتابعة"
            STATUS_DONE -> "تمت ✅"
            else -> "جديدة"
        }
    }
}
