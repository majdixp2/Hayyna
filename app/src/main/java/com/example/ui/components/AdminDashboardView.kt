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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary
import com.example.ui.theme.SoftTerracotta

@Composable
fun AdminDashboardView(
    offers: List<RestaurantOffer>,
    onToggleFeatured: (String, Boolean) -> Unit,
    onToggleApproved: (String, Boolean) -> Unit,
    onDeleteOffer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("admin_dashboard_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Admin Header Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "لوحة تحكم إدارة التطبيق (Admin Portal)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "إدارة عروض المطاعم، الفيديوهات الإعلانية، واعتماد المواقع على الخريطة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Summary Statistics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminStatCard(
                    title = "إجمالي المطاعم",
                    value = "${offers.map { it.restaurantName }.distinct().size}",
                    icon = Icons.Default.Restaurant,
                    color = CalmSagePrimary,
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "فيديوهات إعلانية",
                    value = "${offers.count { it.mediaType == MediaType.VIDEO }}",
                    icon = Icons.Default.Videocam,
                    color = Color(0xFFE05A47),
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    title = "الأحياء المغطاة",
                    value = "${offers.map { it.district }.distinct().size}",
                    icon = Icons.Default.LocationOn,
                    color = SoftTerracotta,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "قائمة جميع العروض وإدارتها (${offers.size} عرض):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Offers Management Items
        items(offers, key = { it.id }) { offer ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_offer_item_${offer.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = offer.restaurantName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CalmSagePrimary
                            )
                            Text(
                                text = offer.offerTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (offer.mediaType == MediaType.VIDEO) Color(0xFFFDE8E8) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (offer.mediaType == MediaType.VIDEO) "فيديو 🎬" else "صورة 📷",
                                color = if (offer.mediaType == MediaType.VIDEO) Color(0xFFE05A47) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "الحي: ${offer.district} | المنطقة: ${offer.region} | السعر: ${offer.price} ر.س",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Featured on Map
                        OutlinedButton(
                            onClick = { onToggleFeatured(offer.id, !offer.isFeatured) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (offer.isFeatured) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = if (offer.isFeatured) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (offer.isFeatured) "مميز بالخريطة ⭐" else "تمييز العرض",
                                fontSize = 11.sp
                            )
                        }

                        // Toggle Active/Approved
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (offer.isApproved) "نشط" else "معلق",
                                fontSize = 11.sp,
                                color = if (offer.isApproved) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = offer.isApproved,
                                onCheckedChange = { onToggleApproved(offer.id, it) }
                            )
                        }

                        // Delete
                        IconButton(
                            onClick = { onDeleteOffer(offer.id) },
                            modifier = Modifier.testTag("admin_delete_offer_${offer.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
