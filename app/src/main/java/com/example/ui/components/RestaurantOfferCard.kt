package com.example.ui.components

import com.example.data.model.priceLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary
import com.example.ui.theme.SoftTerracotta

@Composable
fun RestaurantOfferCard(
    offer: RestaurantOffer,
    onOfferClick: () -> Unit,
    onShowOnMap: (() -> Unit)? = null,
    showAdminControls: Boolean = false,
    onToggleFeatured: () -> Unit = {},
    onDeleteOffer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOfferClick() }
            .testTag("restaurant_card_${offer.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Media thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                ProductMediaViewer(
                    mediaUri = offer.mediaUri,
                    mediaType = offer.mediaType,
                    isPromotionalVideo = offer.isPromotionalVideo,
                    promotionalBadge = offer.displayBadge,
                    autoPlayVideo = false,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )

                // Cuisine chip on media
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = offer.cuisineType,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Video play overlay
                if (offer.mediaType == MediaType.VIDEO) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE05A47).copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "تشغيل الفيديو الإعلاني",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(30.dp)
                        )
                    }
                }
            }

            // Info & Content
            Column(modifier = Modifier.padding(14.dp)) {
                // Restaurant Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (offer.logoUrl.startsWith("http")) {
                            StoreLogo(logoUrl = offer.logoUrl, size = 24.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = CalmSagePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = offer.restaurantName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CalmSagePrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (offer.isFeatured) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "مميز",
                                    color = Color(0xFFB45309),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Offer Title
                Text(
                    text = offer.offerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    OpenNowChip(offer = offer)
                    OfferExpiryChip(offer = offer)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Price and discount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = offer.priceLabel(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CalmSagePrimary
                    )

                    if (offer.originalPrice != null && offer.originalPrice > offer.price) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${offer.originalPrice} ${offer.currency}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PROMINENT DISTANCE BANNER (كم يبعد المحل عن العميل)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (offer.distanceKm <= 0.0) "موقع المحل: ${offer.district}" else "يبعد المحل: ${if (offer.distanceKm < 1.0) "${(offer.distanceKm * 1000).toInt()} م" else "${offer.distanceKm} كم"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF166534)
                            )
                        }

                        if (offer.driveTimeMinutes > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "⏱️ ${offer.driveTimeMinutes} دقائق بالسيارة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Neighborhood and Region Location Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openInGoogleMaps(context, offer.latitude, offer.longitude, "${offer.restaurantName} - ${offer.district}")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = CalmSagePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "الحي: ${offer.district} • ${offer.region}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "خرائط Google",
                            tint = CalmSagePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions: Directions to Restaurant + View on Map + View Offer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Big Directions Button (التوجه إلى المطعم)
                    Button(
                        onClick = {
                            com.example.data.OfferTracker.directions(offer.id)
                        openDirectionsInGoogleMaps(context, offer.latitude, offer.longitude)
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("directions_btn_${offer.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CalmSagePrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "التوجه للمحل 🚗",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // View on Map Button (عرض على الخريطة)
                    if (onShowOnMap != null) {
                        OutlinedButton(
                            onClick = onShowOnMap,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("map_view_btn_${offer.id}"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = CalmSagePrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "على الخريطة 🗺️",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CalmSagePrimary
                            )
                        }
                    }

                    // View Ad Details Button
                    OutlinedButton(
                        onClick = onOfferClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_offer_btn_${offer.id}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (offer.mediaType == MediaType.VIDEO) "مشاهدة الفيديو 🎬" else "تفاصيل العرض",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Admin Controls if in Admin Mode
                if (showAdminControls) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onToggleFeatured,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (offer.isFeatured) "إلغاء التمييز" else "تمييز على الخريطة ⭐",
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDeleteOffer,
                            modifier = Modifier.testTag("admin_delete_btn_${offer.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف العرض",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
