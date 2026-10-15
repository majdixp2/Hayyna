package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

/**
 * Bottom interactive section for the full-screen map.
 * In accordance with user requirements, offers do NOT appear automatically on startup.
 * They only appear when a user clicks on a restaurant marker on the map (or chooses from the list).
 */
@Composable
fun BottomOffersCarousel(
    offers: List<RestaurantOffer>,
    selectedOffer: RestaurantOffer?,
    onOfferClick: (RestaurantOffer) -> Unit,
    onSelectOtherOffer: (RestaurantOffer) -> Unit = {},
    onDismiss: () -> Unit = {},
    onOpenRestaurantList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (selectedOffer != null) {
            SelectedRestaurantOfferCard(
                offer = selectedOffer,
                allOffersInDistrict = offers,
                onOfferClick = onOfferClick,
                onSelectOtherOffer = onSelectOtherOffer,
                onDismiss = onDismiss
            )
        } else {
            MapQuickGuidePill(
                offersCount = offers.size,
                onOpenList = onOpenRestaurantList
            )
        }
    }
}

/**
 * Modern floating bottom card that elevates when a specific restaurant is tapped on the map.
 * Features media preview, offer details, navigation to restaurant, and expand button.
 */
@Composable
fun SelectedRestaurantOfferCard(
    offer: RestaurantOffer,
    allOffersInDistrict: List<RestaurantOffer>,
    onOfferClick: (RestaurantOffer) -> Unit,
    onSelectOtherOffer: (RestaurantOffer) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("selected_restaurant_bottom_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E0D8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 1. Header bar with Cuisine pill, rating, and close button (✕)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CalmSagePrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = offer.cuisineType,
                            color = CalmSagePrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (offer.mediaType == MediaType.VIDEO) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "إعلان فيديو 🎬",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }

                    Text(
                        text = "⭐ ${offer.rating}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309)
                    )
                }

                // Close button to dismiss card and return to full clean map
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3F1EC),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onDismiss() }
                        .testTag("dismiss_offer_card_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color(0xFF555555),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Clickable Body: Media Thumbnail + Offer Information
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOfferClick(offer) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media Thumbnail with Video play overlay
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    ProductMediaViewer(
                        mediaUri = offer.mediaUri,
                        mediaType = offer.mediaType,
                        isPromotionalVideo = offer.isPromotionalVideo,
                        promotionalBadge = "",
                        autoPlayVideo = false,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (offer.mediaType == MediaType.VIDEO) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE05A47).copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "تشغيل الفيديو",
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Text Information Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = offer.restaurantName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = CalmSagePrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (offer.discountBadge.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = offer.discountBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = offer.offerTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF262A33),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OpenNowChip(offer = offer)
                        OfferExpiryChip(offer = offer)
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${offer.price} ${offer.currency}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CalmSagePrimary
                        )

                        if (offer.originalPrice != null && offer.originalPrice > offer.price) {
                            Text(
                                text = "${offer.originalPrice} ${offer.currency}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        }

                        Text(
                            text = "• ${offer.district}",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    // Prominent Distance Chip (كم يبعد المحل)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (offer.distanceKm <= 0.0) "الموقع: ${offer.district}" else "يبعد عنك: ${if (offer.distanceKm < 1.0) "${(offer.distanceKm * 1000).toInt()} م" else "${offer.distanceKm} كم"} (${offer.driveTimeMinutes} دقائق 🚗)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )

                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Primary Actions: Navigate in Google Maps + Expand Full Ad & Video
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Navigate to Restaurant
                Button(
                    onClick = {
                        com.example.data.OfferTracker.directions(offer.id)
                        openDirectionsInGoogleMaps(context, offer.latitude, offer.longitude)
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(38.dp)
                        .testTag("directions_bottom_card_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "التوجّه للمطعم 🚗",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Expand Ad & Video (تكبير العرض)
                OutlinedButton(
                    onClick = { onOfferClick(offer) },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(38.dp)
                        .testTag("expand_offer_bottom_card_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CalmSagePrimary
                    ),
                    border = BorderStroke(1.5.dp, CalmSagePrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تكبير الإعلان 🎬",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 3: Quick WhatsApp Chat
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF25D366),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable {
                            val message = "مرحباً ${offer.restaurantName}، أود الاستفسار عن عرض: ${offer.offerTitle}"
                            com.example.data.OfferTracker.whatsapp(offer.id)
                            openWhatsApp(context, offer.phone, message)
                        }
                        .testTag("quick_whatsapp_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "واتساب",
                        tint = Color.White,
                        modifier = Modifier.padding(9.dp)
                    )
                }

                // Button 4: Quick Phone Call
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF3F1EC),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable {
                            callPhone(context, offer.phone)
                        }
                        .testTag("quick_call_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "اتصال",
                        tint = Color(0xFF262A33),
                        modifier = Modifier.padding(9.dp)
                    )
                }
            }

            // 4. Other restaurants in the same district (Quick Horizontal Switcher)
            val otherOffers = allOffersInDistrict.filter { it.id != offer.id }
            if (otherOffers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = "عروض أخرى بالحي:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(otherOffers) { other ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF5F3EE),
                                border = BorderStroke(1.dp, Color(0xFFE2DDD5)),
                                modifier = Modifier.clickable { onSelectOtherOffer(other) }
                            ) {
                                Text(
                                    text = other.restaurantName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF262A33),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean floating guide pill shown at bottom when no offer is selected.
 * Keeps the map totally uncluttered and provides a one-tap restaurant list.
 */
@Composable
fun MapQuickGuidePill(
    offersCount: Int,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, Color(0xFFE2DDD5)),
        modifier = modifier
            .padding(bottom = 14.dp, start = 16.dp, end = 16.dp)
            .clickable { onOpenList() }
            .testTag("map_quick_guide_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = CalmSagePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (offersCount == 0) "ما فيه عروض هنا للحين" else "اضغط على أي عرض في الخريطة ($offersCount)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF262A33),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
