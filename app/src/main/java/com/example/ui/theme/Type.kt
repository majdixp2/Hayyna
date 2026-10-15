package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R

/** خط Tajawal العربي (رخصة OFL) — ExtraBold و Black يستخدمان نفس الملف الأثقل. */
val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
    Font(R.font.tajawal_extrabold, FontWeight.Black)
)

private val Base = Typography()

private fun TextStyle.tajawal() = copy(fontFamily = Tajawal)

/** كل أنماط النصوص في التطبيق بخط Tajawal. */
val Typography = Typography(
    displayLarge = Base.displayLarge.tajawal(),
    displayMedium = Base.displayMedium.tajawal(),
    displaySmall = Base.displaySmall.tajawal(),
    headlineLarge = Base.headlineLarge.tajawal(),
    headlineMedium = Base.headlineMedium.tajawal(),
    headlineSmall = Base.headlineSmall.tajawal(),
    titleLarge = Base.titleLarge.tajawal(),
    titleMedium = Base.titleMedium.tajawal(),
    titleSmall = Base.titleSmall.tajawal(),
    bodyLarge = Base.bodyLarge.tajawal(),
    bodyMedium = Base.bodyMedium.tajawal(),
    bodySmall = Base.bodySmall.tajawal(),
    labelLarge = Base.labelLarge.tajawal(),
    labelMedium = Base.labelMedium.tajawal(),
    labelSmall = Base.labelSmall.tajawal()
)
