package com.example.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RestaurantOffer
import com.example.ui.theme.CalmSagePrimary

/**
 * صفحة العروض - مخصصة بالكامل للمستخدم (العميل):
 * - تصفح جميع عروض المطاعم والإعلانات
 * - إظهار مسافة كم يبعد المحل عن العميل بشكل واضح
 * - فلاتر الأحياء والوجبات وفرز بالأقرب مسافة
 * - زر الانتقال المباشر للخريطة أو التوجّه للمطعم
 */
@Composable
fun CustomerOffersView(
    offers: List<RestaurantOffer>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedRegion: String,
    onRegionSelect: (String) -> Unit,
    selectedDistrict: String,
    onDistrictSelect: (String) -> Unit,
    selectedCuisine: String,
    onCuisineSelect: (String) -> Unit,
    selectedMediaType: String,
    onMediaTypeSelect: (String) -> Unit,
    sortBy: String,
    onSortBySelect: (String) -> Unit,
    onOfferClick: (RestaurantOffer) -> Unit,
    onShowOnMap: (RestaurantOffer) -> Unit,
    openNowOnly: Boolean = false,
    onToggleOpenNow: () -> Unit = {},
    districts: List<Pair<String, Int>> = emptyList(),
    browseName: String? = null,
    onClearBrowse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFilterExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("customer_offers_feed_view"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search & Filters Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "صفحة العروض الترويجية 🏷️",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF262A33)
                            )
                            Text(
                                text = "تصفح أحدث عروض المتاجر وشاهد كم يبعد كل محل عنك",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CalmSagePrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${offers.size} عرض",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CalmSagePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search input & filter button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("ابحث عن متجر، عرض، منتج...", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("search_offers_input")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isFilterExpanded) CalmSagePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(50.dp)
                                .clickable { isFilterExpanded = !isFilterExpanded }
                                .testTag("toggle_offers_filter_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "الفلاتر",
                                tint = if (isFilterExpanded) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(13.dp)
                            )
                        }
                    }

                    // Sort By Row (الأقرب مسافة، التقييم، السعر)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ترتيب حسب:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = openNowOnly,
                                onClick = onToggleOpenNow,
                                label = { Text("🟢 مفتوح الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sortBy == "NEAREST",
                                onClick = { onSortBySelect("NEAREST") },
                                label = { Text("📍 الأقرب إليك مسافة", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sortBy == "PRICE_LOW",
                                onClick = { onSortBySelect("PRICE_LOW") },
                                label = { Text("💰 الأقل سعراً", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = sortBy == "DISCOUNT",
                                onClick = { onSortBySelect("DISCOUNT") },
                                label = { Text("🔥 أكبر خصم", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Expandable filters drawer
                    AnimatedVisibility(visible = isFilterExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // الأحياء اللي فيها عروض فعلاً
                            Text("الحي:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedDistrict == "الكل",
                                        onClick = { onDistrictSelect("الكل") },
                                        label = { Text("كل الأحياء", fontSize = 11.sp) }
                                    )
                                }
                                items(districts) { (name, count) ->
                                    FilterChip(
                                        selected = selectedDistrict == name,
                                        onClick = { onDistrictSelect(name) },
                                        label = { Text("$name ($count)", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Media filter (videos vs images)
                            Text("نوع الإعلان:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                items(listOf("الكل", "فيديوهات إعلانية", "صور فقط")) { filter ->
                                    FilterChip(
                                        selected = selectedMediaType == filter,
                                        onClick = { onMediaTypeSelect(filter) },
                                        label = {
                                            Text(
                                                text = when (filter) {
                                                    "فيديوهات إعلانية" -> "🎬 فيديوهات إعلانية"
                                                    "صور فقط" -> "📷 صور فقط"
                                                    else -> "كل الوسائط"
                                                },
                                                fontSize = 11.sp
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Offers list items
        if (offers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔍 لا توجد نتائج مطابقة لبحثك",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "جرّب تغيير الحي أو إزالة كلمات البحث لتظهر لك باقي العروض",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (browseName != null) {
                item {
                    androidx.compose.material3.AssistChip(
                        onClick = onClearBrowse,
                        label = { Text("📍 مرتبة حسب القرب من: $browseName  ✕", fontSize = 12.sp) },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
            items(offers, key = { it.id }) { offer ->
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    RestaurantOfferCard(
                        offer = offer,
                        onOfferClick = { onOfferClick(offer) },
                        onShowOnMap = { onShowOnMap(offer) }
                    )
                }
            }
        }
    }
}
