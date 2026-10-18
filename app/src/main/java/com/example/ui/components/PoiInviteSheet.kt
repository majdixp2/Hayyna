package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CalmSagePrimary
import com.google.android.gms.maps.model.PointOfInterest

/** بطاقة محل من نقاط Google مو مسجّل في حيّنا: ادعه، أو "أنا صاحب المحل". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiInviteSheet(
    poi: PointOfInterest,
    inviteText: String,
    onInvited: () -> Unit,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(poi.name.replace("\n", " "), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(
                "هذا المحل مو مسجّل في حيّنا للحين. ادعه ينضم وينشر عروضه لأهل الحي 👇",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onInvited()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, inviteText)
                    }
                    context.startActivity(Intent.createChooser(send, "ادعُ المحل لحيّنا"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text("📣 ادعه لحيّنا", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            OutlinedButton(
                onClick = onClaim,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text("🏪 أنا صاحب المحل — سجّله", fontWeight = FontWeight.Bold) }
            Row { Text("المعلومات من خرائط Google", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
