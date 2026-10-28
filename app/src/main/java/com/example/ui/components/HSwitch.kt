package com.example.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CalmSagePrimary

/** مفتاح تشغيل واضح وهو مقفل (الافتراضي باهت جداً على خلفية التطبيق) */
@Composable
fun HSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = CalmSagePrimary, checkedBorderColor = CalmSagePrimary,
            uncheckedThumbColor = Color(0xFF6B7280), uncheckedTrackColor = Color(0xFFE5E7EB), uncheckedBorderColor = Color(0xFF6B7280)
        )
    )
}
