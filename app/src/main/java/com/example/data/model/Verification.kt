package com.example.data.model

/** طلب توثيق متجر (رقم السجل التجاري أو وثيقة العمل الحر + صورة الواجهة + التواجد عند المحل). */
data class VerificationRequest(
    val uid: String = "",
    val storeName: String = "",
    val docType: String = "",
    val docNumber: String = "",
    val photoUrl: String = "",
    val locationChecked: Boolean = false,
    val locationOk: Boolean = false,
    val distanceMeters: Int = -1,
    val status: String = STATUS_PENDING,
    val rejectReason: String = "",
    val createdAtMillis: Long = 0L
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val DOC_CR = "cr"
        const val DOC_FREELANCE = "freelance"
        const val MAX_DISTANCE_METERS = 200

        fun docLabel(type: String) = if (type == DOC_FREELANCE) "وثيقة العمل الحر" else "السجل التجاري"
    }
}

/** إعدادات التوثيق (الأدمن يشغّل/يطفي كل شرط). */
data class VerifyConfig(
    val enabled: Boolean = true,
    val requireDoc: Boolean = true,
    val requirePhoto: Boolean = true,
    val requireLocation: Boolean = true
)
