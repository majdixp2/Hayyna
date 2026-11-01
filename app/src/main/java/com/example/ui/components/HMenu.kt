package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.Text

/** قائمة منسدلة بشكل حديث: زوايا دائرية، ظل ناعم، خلفية بيضاء (بدل الافتراضي البنفسجي) */
@Composable
fun HMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, offset: DpOffset = DpOffset(0.dp, 6.dp), content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest, offset = offset,
        modifier = modifier.widthIn(min = 230.dp).padding(vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp, shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

/** عنصر القائمة: أيقونة في مربع ناعم + عنوان (+ سطر صغير اختياري) */
@Composable
fun HMenuItem(icon: String, label: String, sub: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) Color(0xFFDC2626) else Color(0xFF10318F)
    DropdownMenuItem(
        leadingIcon = {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(icon, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (danger) tint else MaterialTheme.colorScheme.onSurface)
                if (sub != null) Text(sub, fontSize = 11.sp, color = Color(0xFFB07D00))
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        onClick = onClick
    )
}

@Composable
fun HMenuDivider() = HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
