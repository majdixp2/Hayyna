package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OfferSharer
import com.example.data.model.RestaurantOffer
import kotlinx.coroutines.launch

/** صف أزرار العرض: مفضلة، مشاركة، إبلاغ — وتعديل لصاحب العرض. */
@Composable
fun OfferActionsRow(
    offer: RestaurantOffer,
    isFavorite: Boolean,
    isOwner: Boolean,
    onToggleFavorite: () -> Unit,
    onReport: (reason: String) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReport by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (isFavorite) "محفوظ" else "حفظ",
            tint = if (isFavorite) Color(0xFFE05A47) else MaterialTheme.colorScheme.onSurface,
            onClick = onToggleFavorite,
            modifier = Modifier.weight(1f)
        )
        ActionButton(
            icon = Icons.Default.Share,
            label = "مشاركة",
            tint = Color(0xFF15803D),
            onClick = { scope.launch { OfferSharer.share(context, offer) } },
            modifier = Modifier.weight(1f)
        )
        if (isOwner) {
            ActionButton(
                icon = Icons.Default.Edit,
                label = "تعديل",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
        } else {
            ActionButton(
                icon = Icons.Default.Flag,
                label = "إبلاغ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { showReport = true },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showReport) {
        ReportOfferDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                showReport = false
                onReport(reason)
            }
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val REPORT_REASONS = listOf(
    "العرض غير صحيح أو منتهي",
    "السعر مختلف عن المعلن",
    "محتوى غير لائق",
    "موقع المطعم خطأ",
    "احتيال أو إعلان مزيف"
)

@Composable
fun ReportOfferDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var selected by remember { mutableStateOf(REPORT_REASONS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إبلاغ عن العرض") },
        text = {
            Column {
                Text("وش المشكلة في العرض؟", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                REPORT_REASONS.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == reason, onClick = { selected = reason })
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = selected == reason, onClick = { selected = reason })
                        Text(reason, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected) }) { Text("إرسال البلاغ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
