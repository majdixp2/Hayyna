package com.example.ui.components

import com.example.data.model.priceLabel
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

/**
 * Modal bottom sheet allowing the user to browse restaurants from a list and pick one,
 * which will select it on the map and elevate its offer card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantListSheet(
    offers: List<RestaurantOffer>,
    selectedDistrict: String,
    onSelectOffer: (RestaurantOffer) -> Unit,
    onDismiss: () -> Unit,
    title: String = "قائمة متاجر وعروض الحي 🛍️",
    subtitle: String = "اضغط على أي متجر لعرض موقعه وإعلانه على الخريطة"
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("restaurant_list_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF262A33)
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3F1EC),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onDismiss() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color(0xFF555555),
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (offers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد متاجر مطابقة للبحث في هذا الحي حالياً",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(offers, key = { it.id }) { offer ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectOffer(offer)
                                    onDismiss()
                                }
                                .testTag("restaurant_list_item_${offer.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, Color(0xFFE5E0D8))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    ProductMediaViewer(
                                        mediaUri = offer.mediaUri,
                                        mediaType = offer.mediaType,
                                        isPromotionalVideo = offer.isPromotionalVideo,
                                        autoPlayVideo = false,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (offer.mediaType == MediaType.VIDEO) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFE05A47).copy(alpha = 0.9f),
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircleFilled,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.padding(3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StoreLogo(logoUrl = offer.logoUrl, size = 20.dp)
                                        if (offer.logoUrl.startsWith("http")) Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = offer.restaurantName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = CalmSagePrimary
                                        )

                                        if (offer.displayBadge.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    text = offer.displayBadge,
                                                    fontSize = 9.sp,
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
                                        color = Color(0xFF333333),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = offer.priceLabel(),
                            maxLines = 1,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CalmSagePrimary
                                        )

                                        Text(
                                            text = "• ${offer.district} (${offer.cuisineType})",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF0FDF4),
                                        border = BorderStroke(0.5.dp, Color(0xFF86EFAC))
                                    ) {
                                        Text(
                                            text = "📍 يبعد: ${if (offer.distanceKm < 1.0) "${(offer.distanceKm * 1000).toInt()} م" else "${offer.distanceKm} كم"} • ${offer.driveTimeMinutes} دقائق",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
