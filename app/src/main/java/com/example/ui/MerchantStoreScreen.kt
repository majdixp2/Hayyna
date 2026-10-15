package com.example.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LocationPresets
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.components.AddRestaurantOfferSheet
import com.example.ui.components.AdminDashboardView
import com.example.ui.components.BottomOffersCarousel
import com.example.ui.components.CustomerOffersView
import com.example.ui.components.FullScreenRestaurantMap
import com.example.ui.components.MapQuickGuidePill
import com.example.ui.components.MerchantPortalView
import com.example.ui.components.RestaurantListSheet
import com.example.ui.components.RestaurantOfferCard
import com.example.ui.components.RestaurantOfferDetailSheet
import com.example.ui.components.SelectedRestaurantOfferCard
import com.example.ui.components.openDirectionsInGoogleMaps
import com.example.ui.theme.CalmSagePrimary
import com.example.ui.theme.SoftCreamBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantStoreScreen(
    viewModel: MerchantViewModel
) {
    val context = LocalContext.current
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val allOffers by viewModel.allOffers.collectAsStateWithLifecycle()
    val filteredCustomerOffers by viewModel.filteredCustomerOffers.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val selectedCuisine by viewModel.selectedCuisine.collectAsStateWithLifecycle()
    val selectedMediaType by viewModel.selectedMediaType.collectAsStateWithLifecycle()
    val customerSortBy by viewModel.customerSortBy.collectAsStateWithLifecycle()

    val merchantEmail by viewModel.merchantEmail.collectAsStateWithLifecycle()
    val isMerchantLoggedIn by viewModel.isMerchantLoggedIn.collectAsStateWithLifecycle()
    val merchantRestaurantName by viewModel.merchantRestaurantName.collectAsStateWithLifecycle()
    val merchantOffers by viewModel.merchantOffers.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val openNowOnly by viewModel.openNowOnly.collectAsStateWithLifecycle()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()

    val selectedOfferOnMap by viewModel.selectedOfferOnMap.collectAsStateWithLifecycle()
    val activeDetailOffer by viewModel.activeDetailOffer.collectAsStateWithLifecycle()
    val isAddOfferSheetOpen by viewModel.isAddOfferSheetOpen.collectAsStateWithLifecycle()

    var isFilterExpanded by remember { mutableStateOf(false) }
    var isRestaurantListSheetOpen by remember { mutableStateOf(false) }

    val currentRegionDistricts = LocationPresets.regions.find { it.name == selectedRegion }?.districts ?: emptyList()

    // Full RTL Arabic Experience
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = SoftCreamBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentRole) {
                    AppRole.CUSTOMER_MAP -> {
                        // 1. FULL SCREEN MAP LIKE GOOGLE MAPS (الأساس الخريطة كاملة)
                        Box(modifier = Modifier.fillMaxSize()) {
                            // BASE LAYER: Full Screen Map
                            FullScreenRestaurantMap(
                                offers = filteredCustomerOffers,
                                selectedOffer = selectedOfferOnMap,
                                userLocation = viewModel.userLocation.collectAsStateWithLifecycle().value,
                                onLocationPermissionGranted = { viewModel.requestLocationUpdates() },
                                onSelectOffer = { offer ->
                                    viewModel.selectOfferOnMap(offer)
                                },
                                onBackgroundClick = {
                                    viewModel.deselectOfferOnMap()
                                },
                                currentDistrictName = if (selectedDistrict == "الكل") selectedRegion else selectedDistrict,
                                modifier = Modifier.fillMaxSize()
                            )

                            // TOP FLOATING CONTROLS OVER MAP
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                // Floating Glass Bar: Role switch & Header
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(alpha = 0.95f),
                                    shadowElevation = 5.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // Role Switcher Pills (العميل: خريطة العروض أو صفحة العروض - والتاجر - والأدمن)
                                        GlobalAppRoleBar(
                                            currentRole = currentRole,
                                            onRoleSelected = { viewModel.setRole(it) },
                                        showAdmin = isAdmin
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Search Bar with Filter Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                                placeholder = { Text("ابحث عن مطعم، أو وجبة...", fontSize = 12.sp) },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                trailingIcon = {
                                                    if (searchQuery.isNotBlank()) {
                                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(50.dp)
                                                    .testTag("search_map_input")
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isFilterExpanded) CalmSagePrimary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clickable { isFilterExpanded = !isFilterExpanded }
                                                    .testTag("toggle_filters_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = "الفلاتر",
                                                    tint = if (isFilterExpanded) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(13.dp)
                                                )
                                            }
                                        }

                                        // Expandable Filter Drawer over the map
                                        AnimatedVisibility(visible = isFilterExpanded) {
                                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                                // Regions Selector
                                                Text("المنطقة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                                    item {
                                                        FilterChip(
                                                            selected = selectedRegion == "الكل",
                                                            onClick = { viewModel.onRegionSelect("الكل") },
                                                            label = { Text("كل المناطق", fontSize = 11.sp) }
                                                        )
                                                    }
                                                    items(LocationPresets.regions) { reg ->
                                                        FilterChip(
                                                            selected = selectedRegion == reg.name,
                                                            onClick = { viewModel.onRegionSelect(reg.name) },
                                                            label = { Text(reg.name, fontSize = 11.sp) }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // Districts Selector (الأحياء)
                                                Text("الحي المطلوب:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                                    item {
                                                        FilterChip(
                                                            selected = selectedDistrict == "الكل",
                                                            onClick = { viewModel.onDistrictSelect("الكل") },
                                                            label = { Text("جميع الأحياء", fontSize = 11.sp) }
                                                        )
                                                    }
                                                    items(currentRegionDistricts) { dist ->
                                                        FilterChip(
                                                            selected = selectedDistrict == dist.name,
                                                            onClick = { viewModel.onDistrictSelect(dist.name) },
                                                            label = { Text(dist.name, fontSize = 11.sp) }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // Media & Cuisine Filters
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                                    items(listOf("الكل", "فيديوهات إعلانية", "صور فقط")) { filter ->
                                                        FilterChip(
                                                            selected = selectedMediaType == filter,
                                                            onClick = { viewModel.onMediaTypeSelect(filter) },
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

                                // Quick Neighborhood Chips Horizontal Bar Floating Directly on Map
                                if (!isFilterExpanded) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        // Quick Restaurant List trigger
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = Color.White.copy(alpha = 0.95f),
                                                shadowElevation = 2.dp,
                                                border = BorderStroke(1.dp, CalmSagePrimary.copy(alpha = 0.5f)),
                                                modifier = Modifier.clickable { isRestaurantListSheetOpen = true }
                                            ) {
                                                Text(
                                                    text = "📋 قائمة المطاعم (${filteredCustomerOffers.size})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CalmSagePrimary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        // فلتر "مفتوح الآن"
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (openNowOnly) Color(0xFF15803D) else Color.White.copy(alpha = 0.95f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier
                                                    .clickable { viewModel.toggleOpenNow() }
                                                    .testTag("open_now_chip")
                                            ) {
                                                Text(
                                                    text = "🟢 مفتوح الآن",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (openNowOnly) Color.White else Color(0xFF15803D),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (selectedDistrict == "الكل") CalmSagePrimary else Color.White.copy(alpha = 0.9f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.clickable { viewModel.onDistrictSelect("الكل") }
                                            ) {
                                                Text(
                                                    text = "📍 كل الأحياء",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selectedDistrict == "الكل") Color.White else Color(0xFF262A33),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        items(currentRegionDistricts) { dist ->
                                            val isSel = selectedDistrict == dist.name
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSel) CalmSagePrimary else Color.White.copy(alpha = 0.9f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.clickable { viewModel.onDistrictSelect(dist.name) }
                                            ) {
                                                Text(
                                                    text = dist.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) Color.White else Color(0xFF262A33),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. BOTTOM SECTION OVER THE MAP: OFFERS APPEAR ONLY WHEN CLICKED ON MAP
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                // Elevates / slides up the offer card when a restaurant is selected
                                AnimatedVisibility(
                                    visible = selectedOfferOnMap != null,
                                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                ) {
                                    selectedOfferOnMap?.let { offer ->
                                        SelectedRestaurantOfferCard(
                                            offer = offer,
                                            allOffersInDistrict = filteredCustomerOffers,
                                            onOfferClick = { clicked ->
                                                viewModel.openOfferDetail(clicked)
                                            },
                                            onSelectOtherOffer = { other ->
                                                viewModel.selectOfferOnMap(other)
                                            },
                                            onDismiss = {
                                                viewModel.deselectOfferOnMap()
                                            }
                                        )
                                    }
                                }

                                // Subtle floating pill when no restaurant is selected yet
                                AnimatedVisibility(
                                    visible = selectedOfferOnMap == null,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.Center)
                                ) {
                                    MapQuickGuidePill(
                                        offersCount = filteredCustomerOffers.size,
                                        onOpenList = { isRestaurantListSheetOpen = true }
                                    )
                                }
                            }
                        }
                    }

                    AppRole.CUSTOMER_OFFERS -> {
                        // 2. صفحة العروض - مخصصة بالكامل للمستخدم (العميل) لتصفح عروض المطاعم والمسافات
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    GlobalAppRoleBar(
                                        currentRole = currentRole,
                                        onRoleSelected = { viewModel.setRole(it) },
                                        showAdmin = isAdmin
                                    )
                                }
                            }

                            CustomerOffersView(
                                offers = filteredCustomerOffers,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                                selectedRegion = selectedRegion,
                                onRegionSelect = { viewModel.onRegionSelect(it) },
                                selectedDistrict = selectedDistrict,
                                onDistrictSelect = { viewModel.onDistrictSelect(it) },
                                selectedCuisine = selectedCuisine,
                                onCuisineSelect = { viewModel.onCuisineSelect(it) },
                                selectedMediaType = selectedMediaType,
                                onMediaTypeSelect = { viewModel.onMediaTypeSelect(it) },
                                sortBy = customerSortBy,
                                onSortBySelect = { viewModel.setCustomerSortBy(it) },
                                onOfferClick = { viewModel.openOfferDetail(it) },
                                onShowOnMap = { offer ->
                                    viewModel.focusOfferOnMap(offer)
                                },
                                openNowOnly = openNowOnly,
                                onToggleOpenNow = { viewModel.toggleOpenNow() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    AppRole.MERCHANT_PORTAL -> {
                        // 3. صفحة التاجر الجديدة: تسجيل الدخول بالبريد الإلكتروني + زر إضافة عرض
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    GlobalAppRoleBar(
                                        currentRole = currentRole,
                                        onRoleSelected = { viewModel.setRole(it) },
                                        showAdmin = isAdmin
                                    )
                                }
                            }

                            MerchantPortalView(
                                isLoggedIn = isMerchantLoggedIn,
                                merchantEmail = merchantEmail,
                                merchantRestaurantName = merchantRestaurantName,
                                merchantOffers = merchantOffers,
                                isSigningIn = isSigningIn,
                                nowMillis = nowMillis,
                                onGoogleSignIn = {
                                    (context as? android.app.Activity)?.let { activity ->
                                        viewModel.signInWithGoogle(activity)
                                    }
                                },
                                onLogout = { viewModel.logoutMerchant() },
                                onOpenAddOfferSheet = { viewModel.openAddOfferSheet() },
                                onOfferClick = { viewModel.openOfferDetail(it) },
                                onDeleteOffer = { viewModel.deleteOffer(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    AppRole.ADMIN_PORTAL -> {
                        // 4. لوحة تحكم الأدمن
                        Column(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    GlobalAppRoleBar(
                                        currentRole = currentRole,
                                        onRoleSelected = { viewModel.setRole(it) },
                                        showAdmin = isAdmin
                                    )
                                }
                            }

                            AdminDashboardView(
                                offers = allOffers,
                                onToggleFeatured = { id, featured -> viewModel.toggleFeatured(id, featured) },
                                onToggleApproved = { id, approved -> viewModel.toggleApproved(id, approved) },
                                onDeleteOffer = { id -> viewModel.deleteOffer(id) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. EXPANDING OFFER DETAIL FROM THE BOTTOM (عند الضغط على عرض يكبر من تحت وترتفع بكامل الإعلان والفيديو والتوجّه)
        activeDetailOffer?.let { offer ->
            RestaurantOfferDetailSheet(
                offer = offer,
                onDismiss = { viewModel.closeOfferDetail() }
            )
        }

        // Add Restaurant Offer Sheet
        if (isAddOfferSheetOpen) {
            AddRestaurantOfferSheet(
                onDismiss = { viewModel.closeAddOfferSheet() },
                onSaveOffer = { offer -> viewModel.saveOffer(offer) },
                isSaving = isUploadingMedia,
                defaultRestaurantName = merchantOffers.firstOrNull()?.restaurantName ?: merchantRestaurantName
            )
        }

        // Restaurant List Sheet (اختيار مطعم من القائمة لتحديد موقعه وعرض إعلانه)
        if (isRestaurantListSheetOpen) {
            RestaurantListSheet(
                offers = filteredCustomerOffers,
                selectedDistrict = if (selectedDistrict == "الكل") selectedRegion else selectedDistrict,
                onSelectOffer = { offer ->
                    viewModel.selectOfferOnMap(offer)
                },
                onDismiss = { isRestaurantListSheetOpen = false }
            )
        }
    }
}

@Composable
fun GlobalAppRoleBar(
    currentRole: AppRole,
    onRoleSelected: (AppRole) -> Unit,
    showAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        RoleTabButton(
            title = "الخريطة 🗺️",
            isSelected = currentRole == AppRole.CUSTOMER_MAP,
            onClick = { onRoleSelected(AppRole.CUSTOMER_MAP) },
            modifier = Modifier.weight(1f),
            testTag = "map_role_tab"
        )
        RoleTabButton(
            title = "العروض 🏷️",
            isSelected = currentRole == AppRole.CUSTOMER_OFFERS,
            onClick = { onRoleSelected(AppRole.CUSTOMER_OFFERS) },
            modifier = Modifier.weight(1.2f),
            testTag = "customer_offers_role_tab"
        )
        RoleTabButton(
            title = "التاجر 🏪",
            isSelected = currentRole == AppRole.MERCHANT_PORTAL,
            onClick = { onRoleSelected(AppRole.MERCHANT_PORTAL) },
            modifier = Modifier.weight(1.2f),
            testTag = "merchant_role_tab"
        )
        // تبويب الأدمن يظهر لحسابات الأدمن فقط
        if (showAdmin) {
            RoleTabButton(
                title = "الأدمن 🛡️",
                isSelected = currentRole == AppRole.ADMIN_PORTAL,
                onClick = { onRoleSelected(AppRole.ADMIN_PORTAL) },
                modifier = Modifier.weight(0.9f),
                testTag = "admin_role_tab"
            )
        }
    }
}

@Composable
fun RoleTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) CalmSagePrimary else Color.Transparent,
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

