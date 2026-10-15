package com.example.data

/**
 * حسابات الأدمن: هؤلاء فقط يشاهدون تبويب "الأدمن" ويقدرون يعتمدون أو يميّزون أو يحذفون أي عرض.
 * لإضافة أدمن جديد: أضف بريده هنا وفي firestore.rules (دالة isAdmin) ثم انشر القواعد.
 */
object AdminConfig {
    val ADMIN_EMAILS: Set<String> = setOf(
        "majdixp2@gmail.com"
    )

    fun isAdmin(email: String?): Boolean =
        email != null && ADMIN_EMAILS.any { it.equals(email.trim(), ignoreCase = true) }
}
