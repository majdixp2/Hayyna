package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

/**
 * صفحة التاجر:
 * - الدخول بحساب Google فقط (حساب حقيقي مربوط بـ Firebase)
 * - التاجر يشوف عروضه هو فقط، مع إحصائيات حقيقية لكل عرض
 */
@Composable
fun MerchantPortalView(
    isLoggedIn: Boolean,
    merchantEmail: String?,
    merchantRestaurantName: String,
    merchantOffers: List<RestaurantOffer>,
    isSigningIn: Boolean,
    nowMillis: Long,
    onGoogleSignIn: () -> Unit,
    onLogout: () -> Unit,
    onOpenAddOfferSheet: () -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onDeleteOffer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isLoggedIn) {
        MerchantLoginView(isSigningIn = isSigningIn, onGoogleSignIn = onGoogleSignIn, modifier = modifier)
    } else {
        MerchantDashboard(
            merchantEmail = merchantEmail.orEmpty(),
            merchantName = merchantRestaurantName,
            offers = merchantOffers,
            nowMillis = nowMillis,
            onOpenAddOfferSheet = onOpenAddOfferSheet,
            onLogout = onLogout,
            onOfferClick = onOfferClick,
            onDeleteOffer = onDeleteOffer,
            modifier = modifier
        )
    }
}

@Composable
private fun MerchantLoginView(
    isSigningIn: Boolean,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("merchant_login_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = CalmSagePrimary.copy(alpha = 0.12f), modifier = Modifier.size(84.dp)) {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = CalmSagePrimary,
                modifier = Modifier.padding(20.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "أنت صاحب مطعم أو مقهى؟",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "انشر عروضك بالصورة أو الفيديو، وخلّ أهل الحي يشوفونها على الخريطة ويوصلون لك مباشرة.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onGoogleSignIn,
            enabled = !isSigningIn,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("merchant_google_sign_in_btn")
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("الدخول بحساب Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "مجاناً • بدون كلمة مرور",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MerchantDashboard(
    merchantEmail: String,
    merchantName: String,
    offers: List<RestaurantOffer>,
    nowMillis: Long,
    onOpenAddOfferSheet: () -> Unit,
    onLogout: () -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onDeleteOffer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var offerPendingDelete by remember { mutableStateOf<RestaurantOffer?>(null) }
    val activeCount = offers.count { !OfferTime.isExpired(it, nowMillis) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("merchant_dashboard_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = merchantName.ifBlank { "لوحة التاجر" },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = merchantEmail,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onLogout,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("merchant_logout_btn")
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("خروج", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onOpenAddOfferSheet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("merchant_add_new_offer_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("أضف عرض جديد", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        // الإحصائيات الحقيقية
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MerchantStatTile("العروض النشطة", "$activeCount", Icons.Default.LocalOffer, Modifier.weight(1f))
                    MerchantStatTile("المشاهدات", "${offers.sumOf { it.viewsCount }}", Icons.Default.Visibility, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MerchantStatTile("طلبوا الاتجاه", "${offers.sumOf { it.directionsCount }}", Icons.Default.Directions, Modifier.weight(1f))
                    MerchantStatTile("تواصلوا واتساب", "${offers.sumOf { it.whatsappCount }}", Icons.Default.Chat, Modifier.weight(1f))
                }
            }
        }

        item {
            Text(
                text = "عروضك (${offers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (offers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ما عندك عروض للحين", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "اضغط \"أضف عرض جديد\" وانشر أول عرض لك. بيظهر مباشرة على الخريطة.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(offers, key = { it.id }) { offer ->
                MerchantOfferRow(
                    offer = offer,
                    nowMillis = nowMillis,
                    onClick = { onOfferClick(offer) },
                    onDelete = { offerPendingDelete = offer }
                )
            }
        }
    }

    offerPendingDelete?.let { offer ->
        AlertDialog(
            onDismissRequest = { offerPendingDelete = null },
            title = { Text("حذف العرض؟") },
            text = { Text("بيختفي \"${offer.offerTitle}\" من الخريطة ومن صفحة العروض. ما تقدر تتراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteOffer(offer.id)
                    offerPendingDelete = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { offerPendingDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun MerchantOfferRow(
    offer: RestaurantOffer,
    nowMillis: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val expired = OfferTime.isExpired(offer, nowMillis)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductMediaViewer(
                mediaUri = offer.mediaUri,
                mediaType = offer.mediaType,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = offer.offerTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (offer.expiresAt == null) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE7F6EC)) {
                            Text(
                                "نشط",
                                color = Color(0xFF15803D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        OfferExpiryChip(offer = offer, nowMillis = nowMillis)
                    }
                    if (offer.isFeatured) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4E0)) {
                            Text(
                                "⭐ مميز",
                                color = Color(0xFF8A5A00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "👁 ${offer.viewsCount}   🧭 ${offer.directionsCount}   💬 ${offer.whatsappCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف العرض", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MerchantStatTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = CalmSagePrimary.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                Icon(icon, contentDescription = null, tint = CalmSagePrimary, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
