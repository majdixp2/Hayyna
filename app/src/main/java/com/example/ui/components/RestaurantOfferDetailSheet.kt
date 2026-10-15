package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary
import com.example.ui.theme.SoftTerracotta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantOfferDetailSheet(
    offer: RestaurantOffer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("restaurant_offer_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Top Drag Down Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clickable { onDismiss() }
                ) {}
            }

            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = offer.cuisineType,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تصغير للخريطة 🔽",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Advertisement Media Player: Plays video ad or displays offer photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                ProductMediaViewer(
                    mediaUri = offer.mediaUri,
                    mediaType = offer.mediaType,
                    isPromotionalVideo = offer.isPromotionalVideo,
                    promotionalBadge = offer.discountBadge,
                    autoPlayVideo = offer.mediaType == MediaType.VIDEO,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Restaurant Name & Verified Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = offer.restaurantName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "مطعم معتمد",
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Offer Title
            Text(
                text = offer.offerTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                OpenNowChip(offer = offer)
                OfferExpiryChip(offer = offer)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price & Discount
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${offer.price} ${offer.currency}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (offer.originalPrice != null && offer.originalPrice > offer.price) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${offer.originalPrice} ${offer.currency}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                }

                if (offer.discountBadge.isNotBlank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = offer.discountBadge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = offer.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Prominent Distance Banner (كم يبعد المحل عن العميل)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0FDF4),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "كم يبعد المحل عنك الآن:",
                                fontSize = 11.sp,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "${if (offer.distanceKm < 1.0) "${(offer.distanceKm * 1000).toInt()} متر" else "${offer.distanceKm} كم"} فقط",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "🚗 ${offer.driveTimeMinutes} دقائق قيادة",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Big CTA: التوجه إلى المطعم (Google Maps Directions)
            Button(
                onClick = {
                    com.example.data.OfferTracker.directions(offer.id)
                        openDirectionsInGoogleMaps(context, offer.latitude, offer.longitude)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("navigate_to_restaurant_cta_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CalmSagePrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "التوجه إلى المطعم عبر Google ماب 🚗",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Location Map Card showing district and region
            LocationMapCard(
                district = offer.district,
                region = offer.region,
                latitude = offer.latitude,
                longitude = offer.longitude,
                storeOrItemName = offer.restaurantName,
                isInteractive = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Contact Buttons: Call & WhatsApp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val message = "مرحباً، أود طلب العرض: ${offer.offerTitle} بسعر ${offer.price} ر.س المعلن عنه في تطبيق عروض المطاعم."
                        com.example.data.OfferTracker.whatsapp(offer.id)
                            openWhatsApp(context, offer.phone, message)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("whatsapp_restaurant_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طلب عبر واتساب", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        callPhone(context, offer.phone)
                    },
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("call_restaurant_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اتصال هاتفي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
