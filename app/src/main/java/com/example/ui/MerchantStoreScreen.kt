package com.example.ui

import androidx.compose.foundation.layout.heightIn
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
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.ui.components.OrderTracker
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
    // رصيد العروض
    val creditsCfg by viewModel.creditsConfig.collectAsStateWithLifecycle()
    val walletBal by viewModel.walletBalance.collectAsStateWithLifecycle()
    val boostsAll by viewModel.activeBoostsAll.collectAsStateWithLifecycle()
    val creditPrompt by viewModel.creditPrompt.collectAsStateWithLifecycle()
    val boostTarget by viewModel.boostTarget.collectAsStateWithLifecycle()
    val catalogCfg by viewModel.catalogConfig.collectAsStateWithLifecycle()
    var showMyOrders by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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
    val myCatalogItems by viewModel.myItems.collectAsStateWithLifecycle()
    val merchantRestaurantName by viewModel.merchantRestaurantName.collectAsStateWithLifecycle()
    val merchantOffers by viewModel.merchantOffers.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val openNowOnly by viewModel.openNowOnly.collectAsStateWithLifecycle()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val merchantProfile by viewModel.merchantProfile.collectAsStateWithLifecycle()
    val editingOffer by viewModel.editingOffer.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val selectedOfferOnMap by viewModel.selectedOfferOnMap.collectAsStateWithLifecycle()
    val activeDetailOffer by viewModel.activeDetailOffer.collectAsStateWithLifecycle()
    val isAddOfferSheetOpen by viewModel.isAddOfferSheetOpen.collectAsStateWithLifecycle()
    val isBranchesPageOpen by viewModel.isBranchesPageOpen.collectAsStateWithLifecycle()
    val isSavingBranches by viewModel.isSavingBranches.collectAsStateWithLifecycle()

    var isFilterExpanded by remember { mutableStateOf(false) }
    var isRestaurantListSheetOpen by remember { mutableStateOf(false) }
    // عروض مطعم واحد في نفس المكان (لما يضغط على أيقونة فيها +N)
    var markerGroupOffers by remember { mutableStateOf<List<RestaurantOffer>?>(null) }

    val browsePlace by viewModel.browsePlace.collectAsStateWithLifecycle()
    val myStatus by viewModel.myStatus.collectAsStateWithLifecycle()
    val myFeedback by viewModel.myFeedback.collectAsStateWithLifecycle()
    val verifyConfig by viewModel.verifyConfig.collectAsStateWithLifecycle()
    val myVerification by viewModel.myVerification.collectAsStateWithLifecycle()
    val pendingVerifications by viewModel.pendingVerifications.collectAsStateWithLifecycle()
    val isVerifyOpen by viewModel.isVerifyOpen.collectAsStateWithLifecycle()
    val isSubmittingVerification by viewModel.isSubmittingVerification.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val poiConfig by viewModel.poiConfig.collectAsStateWithLifecycle()
    val selectedPoi by viewModel.selectedPoi.collectAsStateWithLifecycle()
    val isScout by viewModel.isScout.collectAsStateWithLifecycle()
    val adUploadPoi by viewModel.adUploadPoi.collectAsStateWithLifecycle()
    val pendingClaim by viewModel.pendingClaim.collectAsStateWithLifecycle()
    val topPoi by viewModel.topPoi.collectAsStateWithLifecycle()
    val appCheckToken by viewModel.appCheckDebugToken.collectAsStateWithLifecycle()
    val lastAiError by viewModel.lastAiError.collectAsStateWithLifecycle()
    val isAutofilling by viewModel.isAutofilling.collectAsStateWithLifecycle()
    val allFeedback by viewModel.allFeedback.collectAsStateWithLifecycle()
    val supportWhatsapp by viewModel.supportWhatsapp.collectAsStateWithLifecycle()
    val isContactOpen by viewModel.isContactOpen.collectAsStateWithLifecycle()
    val isSendingFeedback by viewModel.isSendingFeedback.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val userPhone by viewModel.userPhone.collectAsStateWithLifecycle()
    val isRoleLoading by viewModel.isRoleLoading.collectAsStateWithLifecycle()
    val approvedStores by viewModel.approvedStores.collectAsStateWithLifecycle()
    val pendingStores by viewModel.pendingStores.collectAsStateWithLifecycle()
    val openStore by viewModel.openStore.collectAsStateWithLifecycle()
    val showAllStores by viewModel.showAllStores.collectAsStateWithLifecycle()
    val merchantStatuses by viewModel.merchantStatuses.collectAsStateWithLifecycle()
    val placeSuggestions by viewModel.placeSuggestions.collectAsStateWithLifecycle()
    val districtCounts by viewModel.districtCounts.collectAsStateWithLifecycle()

    // زر الرجوع في الشاشة الرئيسية: من أي تبويب يرجع للخريطة، ومن الخريطة يسأل قبل الخروج
    // (يُسجّل أول شي عشان صفحات التطبيق المفتوحة فوقه تاخذ الأولوية)
    var showExitDialog by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler {
        if (currentRole != AppRole.CUSTOMER_MAP) viewModel.setRole(AppRole.CUSTOMER_MAP)
        else showExitDialog = true
    }
    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("تبي تطلع من لكوفر؟") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showExitDialog = false
                    (context as? android.app.Activity)?.finish()
                }) { Text("خروج", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showExitDialog = false }) { Text("لا، خلّني") }
            }
        )
    }

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
                            // 🚀 عرض مُبرز قريب (مرة باليوم)
                            com.example.ui.components.SpotlightWelcome(
                                offers = filteredCustomerOffers,
                                hasLocation = viewModel.userLocation.collectAsStateWithLifecycle().value != null,
                                onOpen = { viewModel.openOfferDetail(it) }
                            )
                            // BASE LAYER: Full Screen Map
                            FullScreenRestaurantMap(
                                offers = filteredCustomerOffers,
                                selectedOffer = selectedOfferOnMap,
                                userLocation = viewModel.userLocation.collectAsStateWithLifecycle().value,
                                onLocationPermissionGranted = { viewModel.requestLocationUpdates() },
                                onSelectOffer = { offer ->
                                    viewModel.selectOfferOnMap(offer)
                                },
                                onSelectGroup = { group -> markerGroupOffers = group },
                                onBackgroundClick = {
                                    viewModel.deselectOfferOnMap()
                                },
                                currentDistrictName = if (selectedDistrict == "الكل") selectedRegion else selectedDistrict,
                                modifier = Modifier.fillMaxSize(),
                                focusPoint = browsePlace?.let { it.latitude to it.longitude },
                                // المتاجر اللي ما عندها عروض ظاهرة: نقاط صغيرة هادية
                                quietStores = if (showAllStores) {
                                    val withOffers = filteredCustomerOffers.map { it.merchantId }.toSet()
                                    approvedStores.filter { it.uid !in withOffers }
                                } else emptyList(),
                                onStoreClick = { viewModel.openStorePage(it) },
                                showGooglePoi = poiConfig.showGooglePoi,
                                onPoiClick = { viewModel.onPoiClick(it) },
                                onMyLocationClick = { viewModel.clearBrowse() },
                                scope = scope,
                                scopeCount = filteredCustomerOffers.size,
                                onScopeClick = { viewModel.cycleScope() },
                                onUserDistrict = { viewModel.setUserDistrict(it) }
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
                                                placeholder = { Text("ابحث عن متجر، عرض، أو حي...", fontSize = 12.sp) },
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
                                                    .heightIn(min = 50.dp)
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

                                        // اقتراحات الأحياء/الأماكن أثناء الكتابة
                                        if (placeSuggestions.isNotEmpty()) {
                                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                                Text(
                                                    "📍 روح لحي:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                placeSuggestions.forEach { place ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .clickable { viewModel.browseTo(place) }
                                                            .padding(horizontal = 6.dp, vertical = 8.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.LocationOn,
                                                            contentDescription = null,
                                                            tint = CalmSagePrimary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(place.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                            if (place.subtitle.isNotBlank()) {
                                                                Text(
                                                                    place.subtitle,
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Expandable Filter Drawer over the map
                                        AnimatedVisibility(visible = isFilterExpanded) {
                                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                                // الأحياء اللي فيها عروض فعلاً (مع العدد)
                                                Text("أحياء فيها عروض:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                                    item {
                                                        FilterChip(
                                                            selected = selectedDistrict == "الكل",
                                                            onClick = { viewModel.clearBrowse() },
                                                            label = { Text("الكل (حولي)", fontSize = 11.sp) }
                                                        )
                                                    }
                                                    items(districtCounts) { d ->
                                                        FilterChip(
                                                            selected = selectedDistrict == d.name,
                                                            onClick = {
                                                                viewModel.browseTo(
                                                                    MerchantViewModel.BrowsePlace(d.name, d.latitude, d.longitude),
                                                                    filterDistrict = true
                                                                )
                                                            },
                                                            label = { Text("${d.name} (${d.count})", fontSize = 11.sp) }
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
                                        // شريط "تتصفح: حي X" مع زر رجوع لموقعي
                                        browsePlace?.let { place ->
                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = CalmSagePrimary,
                                                    shadowElevation = 2.dp,
                                                    modifier = Modifier.clickable { viewModel.clearBrowse() }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text(
                                                            text = "📍 تتصفح: ${place.name}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            maxLines = 1
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "رجوع لموقعي",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

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
                                                    text = "📋 قائمة المتاجر (${filteredCustomerOffers.size})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CalmSagePrimary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        // فلتر "المفضلة"
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (favoritesOnly) Color(0xFFE05A47) else Color.White.copy(alpha = 0.95f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier
                                                    .clickable { viewModel.toggleFavoritesOnly() }
                                                    .testTag("favorites_chip")
                                            ) {
                                                Text(
                                                    text = "❤️ المفضلة (${favoriteIds.size})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (favoritesOnly) Color.White else Color(0xFFE05A47),
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

                                        // إظهار/إخفاء المتاجر اللي ما عندها عروض
                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (showAllStores) CalmSagePrimary else Color.White.copy(alpha = 0.95f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.clickable { viewModel.toggleShowAllStores() }
                                            ) {
                                                Text(
                                                    text = "🏪 كل المتاجر",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (showAllStores) Color.White else CalmSagePrimary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        item {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (selectedDistrict == "الكل") CalmSagePrimary else Color.White.copy(alpha = 0.9f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.clickable { viewModel.clearBrowse() }
                                            ) {
                                                Text(
                                                    text = "📍 حولي",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selectedDistrict == "الكل") Color.White else Color(0xFF262A33),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                        items(districtCounts.take(8)) { dist ->
                                            val isSel = selectedDistrict == dist.name
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSel) CalmSagePrimary else Color.White.copy(alpha = 0.9f),
                                                shadowElevation = 2.dp,
                                                modifier = Modifier.clickable {
                                                    viewModel.browseTo(
                                                        MerchantViewModel.BrowsePlace(dist.name, dist.latitude, dist.longitude),
                                                        filterDistrict = true
                                                    )
                                                }
                                            ) {
                                                Text(
                                                    text = "${dist.name} (${dist.count})",
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
                                districts = districtCounts.map { it.name to it.count },
                                browseName = browsePlace?.name,
                                onClearBrowse = { viewModel.clearBrowse() },
                                storeHits = remember(searchQuery, approvedStores) {
                                    val q = searchQuery.trim().lowercase()
                                    if (q.length < 2) emptyList() else approvedStores.filter { st ->
                                        (listOf(st.name, st.category) + st.branches.flatMap { listOf(it.name, it.district, it.landmark) })
                                            .any { it.lowercase().contains(q) }
                                    }.sortedByDescending { it.name.lowercase().contains(q) }.take(10)
                                },
                                onStoreClick = { viewModel.openStorePage(it) },
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
                                // العنوان في لوحة التاجر: اسم المتجر، وإلا اسم صاحبه
                                merchantRestaurantName = merchantProfile?.restaurantName?.takeIf { it.isNotBlank() } ?: userName.ifBlank { merchantRestaurantName },
                                merchantOffers = merchantOffers,
                                isSigningIn = isSigningIn,
                                nowMillis = nowMillis,
                                onGoogleSignIn = {
                                    (context as? android.app.Activity)?.let { activity ->
                                        viewModel.signInWithGoogle(activity, asMerchant = true)
                                    }
                                },
                                onCustomerSignIn = {
                                    (context as? android.app.Activity)?.let { activity ->
                                        viewModel.signInWithGoogle(activity, asMerchant = false)
                                    }
                                },
                                isMerchantAccount = isAdmin || userRole == MerchantViewModel.ROLE_MERCHANT,
                                userName = userName,
                                userPhone = userPhone,
                                currentRole = userRole ?: MerchantViewModel.ROLE_CUSTOMER,
                                roleResolved = userRole != null,
                                onSaveProfile = { n, p, r -> viewModel.saveProfile(n, p, r) },
                                isRoleLoading = isRoleLoading,
                                onBecomeMerchant = { viewModel.becomeMerchant() },
                                favoritesCount = favoriteIds.size,
                                onOpenContact = { viewModel.openContact() },
                                verifyEnabled = verifyConfig.enabled,
                                isVerified = myStatus?.verified == true,
                                verifyStatus = when (myVerification?.status) {
                                    com.example.data.model.VerificationRequest.STATUS_PENDING -> "⏳ طلبك قيد المراجعة"
                                    com.example.data.model.VerificationRequest.STATUS_REJECTED -> "تم رفض الطلب — اضغط للتفاصيل"
                                    else -> null
                                },
                                onOpenVerify = { viewModel.openVerify() },
                                unreadReplies = myFeedback.count { !it.replySeen },
                                onShowFavorites = {
                                    if (!favoritesOnly) viewModel.toggleFavoritesOnly()
                                    viewModel.setRole(AppRole.CUSTOMER_MAP)
                                },
                                onLogout = { viewModel.logoutMerchant() },
                                onOpenAddOfferSheet = { viewModel.openAddOfferSheet() },
                                onOfferClick = { viewModel.openOfferDetail(it) },
                                onDeleteOffer = { viewModel.deleteOffer(it) },
                                onEditOffer = { viewModel.startEditOffer(it) },
                                onDeleteAccount = { viewModel.deleteAccount() },
                                branchesCount = merchantProfile?.branches?.size ?: 0,
                                onManageBranches = { viewModel.openBranchesPage() },
                                isSuspended = myStatus?.suspended == true,
                                isTrusted = isAdmin || (myStatus?.trusted == true && myStatus?.suspended != true),
                                credits = if (creditsCfg.enabled && isMerchantLoggedIn) com.example.ui.components.MerchantCredits(
                                    balance = (walletBal ?: 0L).coerceAtLeast(0L),
                                    items = creditsCfg.items.values.filter { it.active }.sortedBy { it.order },
                                    chips = { o -> com.example.ui.components.paidChipsFor(o, boostsAll, nowMillis) },
                                    onPublishDraft = { viewModel.openPublishDraft(it) },
                                    onBoost = { viewModel.openBoost(it) }
                                ) else null,
                                catalogCard = if (catalogCfg.enabled && isMerchantLoggedIn) {
                                    { com.example.ui.components.MerchantCatalogCard(viewModel, merchantProfile?.cuisineType.orEmpty(), if (isAdmin) 999_999L else (walletBal ?: 0L).coerceAtLeast(0L)) }
                                } else null,
                                onMyOrders = if (catalogCfg.enabled && catalogCfg.orderEnabled && isMerchantLoggedIn) ({ showMyOrders = true }) else null,
                                modifier = Modifier.fillMaxSize()
                            )
                            androidx.compose.runtime.LaunchedEffect(creditsCfg.enabled, walletBal, isMerchantLoggedIn) {
                                if (isMerchantLoggedIn) viewModel.ensureWallet()
                            }
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
                                reports = reports,
                                onDismissReport = { viewModel.dismissReport(it) },
                                merchantStatuses = merchantStatuses,
                                onApprove = { viewModel.approveOffer(it) },
                                onTrustMerchant = { viewModel.trustMerchant(it) },
                                onSuspendMerchant = { id, name -> viewModel.suspendMerchant(id, name) },
                                onUnsuspend = { viewModel.unsuspendMerchant(it) },
                                onKeepReported = { viewModel.keepReportedOffer(it) },
                                pendingStores = pendingStores,
                                feedback = allFeedback,
                                onReplyFeedback = { id, r, st -> viewModel.replyFeedback(id, r, st) },
                                onFeedbackStatus = { id, st -> viewModel.setFeedbackStatus(id, st) },
                                onDeleteFeedback = { viewModel.deleteFeedback(it) },
                                supportNumber = supportWhatsapp,
                                onSaveSupportNumber = { viewModel.saveSupportWhatsapp(it) },
                                aiEnabled = aiConfig.first,
                                aiModel = aiConfig.second,
                                onSaveAi = { en, m -> viewModel.saveAiConfig(en, m) },
                                appCheckToken = appCheckToken,
                                lastAiError = lastAiError,
                                onLoadAppCheckToken = { viewModel.loadAppCheckToken() },
                                onUntrust = { viewModel.untrustMerchant(it) },
                                poiConfig = poiConfig,
                                onSavePoiConfig = { viewModel.savePoiConfig(it) },
                                topPoi = topPoi,
                                pendingVerifications = pendingVerifications,
                                onDecideVerification = { r, ok, reason -> viewModel.decideVerification(r, ok, reason) },
                                verifyConfig = verifyConfig,
                                onSaveVerifyConfig = { viewModel.saveVerifyConfig(it) },
                                categories = categories,
                                onSaveCategories = { viewModel.saveCategories(it) },
                                onRenameCategory = { o, n -> viewModel.renameCategory(o, n) },
                                onOfferClick = { viewModel.openOfferDetail(it) },
                                onApproveStore = { viewModel.approveStore(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp)
                            )
                        }
                    }
                }

                // 🧾 متابعة الطلب الحي (شريط أو دائرة حسب إعداد الأدمن) — بس في شاشات العميل
                if (currentRole == AppRole.CUSTOMER_OFFERS || (currentRole == AppRole.CUSTOMER_MAP && selectedOfferOnMap == null)) {
                    OrderTracker(
                        vm = viewModel,
                        bottomPadding = if (currentRole == AppRole.CUSTOMER_MAP) 64.dp else 12.dp,
                        onOpen = { showMyOrders = true }
                    )
                }
            }
        }

        // 3. EXPANDING OFFER DETAIL FROM THE BOTTOM (عند الضغط على عرض يكبر من تحت وترتفع بكامل الإعلان والفيديو والتوجّه)
        activeDetailOffer?.let { offer ->
            RestaurantOfferDetailSheet(
                offer = offer,
                onDismiss = { viewModel.closeOfferDetail() },
                isFavorite = offer.id in favoriteIds,
                isOwner = currentUser?.uid != null && currentUser?.uid == offer.merchantId,
                onToggleFavorite = { viewModel.toggleFavorite(offer.id) },
                onReport = { reason -> viewModel.reportOffer(offer, reason) },
                onEdit = { viewModel.startEditOffer(offer) },
                branchOffers = if (offer.branches.size > 1) {
                    offer.branches.map { viewModel.offerAtBranch(offer, it) }.sortedBy { it.distanceKm }
                } else emptyList(),
                onOpenStore = approvedStores.firstOrNull { it.uid == offer.merchantId }?.let { st -> { viewModel.openStorePage(st) } },
                isVerified = approvedStores.firstOrNull { it.uid == offer.merchantId }?.verified == true,
                orderLabel = viewModel.offerOrderLabel(offer),
                onOrder = { viewModel.orderFromOffer(offer) },
                onClaimAd = { viewModel.closeOfferDetail(); viewModel.claimAdOffer(offer) }
            )
        }

        // محل من نقاط Google مو مسجّل: ادعه / أنا صاحب المحل
        selectedPoi?.let { poi ->
            com.example.ui.components.PoiInviteSheet(
                poi = poi,
                inviteText = viewModel.inviteText(poi),
                onInvited = { viewModel.logInvite(poi) },
                onClaim = { viewModel.claimPoi(poi) },
                onDismiss = { viewModel.dismissPoi() },
                isLoggedIn = currentUser != null,
                canClaim = currentUser == null || viewModel.isMerchantAccount,
                onLoginToInvite = { viewModel.loginToInvite() },
                loadLock = { id -> viewModel.poiInviteLock(id) },
                canScout = isScout,
                onUploadAd = { viewModel.openAdUpload(poi) }
            )
        }
        // 📷 رفع عرض من إعلان (الأدمن والفريق)
        adUploadPoi?.let { poi -> com.example.ui.components.AdScoutSheet(poi = poi, vm = viewModel) }

        // توثيق المتجر
        if (isVerifyOpen) {
            com.example.ui.components.VerificationPage(
                config = verifyConfig,
                current = myVerification,
                isSubmitting = isSubmittingVerification,
                hasBranches = merchantProfile?.branches?.isNotEmpty() == true,
                measureDistance = { viewModel.distanceToNearestBranchMeters() },
                onRequestLocation = { viewModel.requestLocationUpdates() },
                onSubmit = { t, n, ph, d -> viewModel.submitVerification(t, n, ph, d) },
                onClose = { viewModel.closeVerify() }
            )
        }

        // تواصل معنا
        if (isContactOpen) {
            com.example.ui.components.ContactPage(
                supportNumber = supportWhatsapp,
                myMessages = myFeedback,
                isLoggedIn = currentUser != null,
                isSending = isSendingFeedback,
                appVersion = viewModel.appVersion,
                onSend = { t, m, shot, c, done -> viewModel.sendFeedback(t, m, shot, c, done) },
                onSeenReplies = { viewModel.markRepliesSeen() },
                onClose = { viewModel.closeContact() }
            )
        }

        // صفحة المتجر الدائمة
        openStore?.let { st ->
            com.example.ui.components.StorePage(
                store = st,
                offers = filteredCustomerOffers.filter { it.merchantId == st.uid },
                onOfferClick = { o ->
                    viewModel.closeStorePage()
                    viewModel.openOfferDetail(o)
                },
                onClose = { viewModel.closeStorePage() },
                catalog = {
                    com.example.ui.components.StoreRatingsSummary(st.uid, viewModel)
                    com.example.ui.components.StoreCatalogSection(st, viewModel)
                }
            )
        }
        if (showMyOrders) com.example.ui.components.MyOrdersDialog(viewModel) { showMyOrders = false }
        com.example.ui.components.DirectCheckoutHost(viewModel)

        // Add Restaurant Offer Sheet
        if (isAddOfferSheetOpen) {
          androidx.compose.runtime.key(editingOffer?.id) {
            AddRestaurantOfferSheet(
                onDismiss = { viewModel.closeAddOfferSheet() },
                onSaveOffer = { offer -> viewModel.saveOffer(offer) },
                isSaving = isUploadingMedia,
                editingOffer = editingOffer,
                profile = merchantProfile ?: merchantRestaurantName.takeIf { it.isNotBlank() }?.let {
                    com.example.data.model.MerchantProfile(restaurantName = it)
                },
                onManageBranches = { viewModel.openBranchesPage() },
                aiEnabled = aiConfig.first,
                isAutofilling = isAutofilling,
                onAutofill = { uri, onResult -> viewModel.autofillFromImage(uri, onResult) },
                categories = categories,
                publish = if (creditsCfg.enabled) creditsCfg.items["publish"]?.takeIf { it.active }?.let { pub ->
                    com.example.ui.components.PublishInfo(
                        balance = (walletBal ?: 0L).coerceAtLeast(0L), price = pub.price, perDay = pub.unit == "day",
                        rewardOn = creditsCfg.rewardOn, rewardPercent = creditsCfg.rewardPercent, isAdmin = isAdmin,
                        cost = { d, ex -> viewModel.priceFor("publish", d, ex).let { p -> (p?.full ?: 0L) to (p?.cost ?: 0L) } }
                    )
                } else null,
                catalogItems = myCatalogItems.filter { it.approved && it.adminNote.isBlank() },
                dealsCfg = catalogCfg.takeIf { it.enabled }
            )
          }
        }

        // رسائل الرصيد (ما يكفي / انحفظت المسودة / نشر مسودة)
        creditPrompt?.let { pr ->
            com.example.ui.components.CreditPromptDialog(
                prompt = pr,
                rewardOn = creditsCfg.rewardOn,
                rewardPercent = creditsCfg.rewardPercent,
                onMakeExclusive = { if (pr.kind == MerchantViewModel.CreditPrompt.DRAFT) viewModel.publishDraft(true) else viewModel.resolveShortfall(true) },
                onSaveDraft = { viewModel.resolveShortfall(false) },
                onPublish = { viewModel.publishDraft(false) },
                onDismiss = { viewModel.dismissCreditPrompt() }
            )
        }
        // شراء خدمة لعرض (تمديد / تمييز / إبراز)
        boostTarget?.let { o ->
            val spotHere = boostsAll.any { it.offerId == o.id && it.effect == "spotlight" && it.startsAt <= nowMillis && nowMillis < it.endsAt }
            com.example.ui.components.BoostDialog(
                offer = o,
                balance = if (isAdmin) 999_999L else (walletBal ?: 0L).coerceAtLeast(0L),
                items = creditsCfg.items.values.filter { it.active }.sortedBy { it.order },
                priceOf = { id, d -> viewModel.priceFor(id, d, o.exclusive).let { p -> (p?.full ?: 0L) to (p?.cost ?: 0L) } },
                spotUsed = viewModel.spotlightsInDistrict(o.district),
                spotMax = creditsCfg.spotlightMax,
                spotActiveHere = spotHere,
                onBuy = { id, d -> viewModel.buyBoost(o, id, d) },
                onDismiss = { viewModel.closeBoost() }
            )
        }

        // صفحة مطعمي وفروعي (الشعار + الفروع)
        if (isBranchesPageOpen) {
            com.example.ui.components.BranchesPage(
                profile = (merchantProfile ?: com.example.data.model.MerchantProfile(restaurantName = merchantRestaurantName)).let { p ->
                    // جاي من "أنا صاحب المحل": نعبّي الاسم والموقع من نقطة الخريطة
                    val claim = pendingClaim
                    if (claim != null && p.branches.isEmpty() && !p.hasLocation) p.copy(
                        restaurantName = claim.name.replace("\n", " ").trim(),
                        branches = listOf(
                            com.example.data.model.Branch(
                                id = "main",
                                name = "الفرع الرئيسي",
                                latitude = claim.latLng.latitude,
                                longitude = claim.latLng.longitude
                            )
                        )
                    ) else p
                },
                isSaving = isSavingBranches,
                onSave = { p, logo -> viewModel.saveBranches(p, logo) },
                onClose = { viewModel.closeBranchesPage() },
                claimMode = pendingClaim != null && verifyConfig.enabled && myStatus?.verified != true,
                categories = categories,
                claimBranch = pendingClaim?.let { claim ->
                    val p = merchantProfile
                    if (p != null && (p.branches.isNotEmpty() || p.hasLocation)) com.example.data.model.Branch(
                        id = java.util.UUID.randomUUID().toString().take(8),
                        name = claim.name.replace("\n", " ").trim(),
                        latitude = claim.latLng.latitude,
                        longitude = claim.latLng.longitude
                    ) else null
                }
            )
        }

        markerGroupOffers?.let { group ->
            RestaurantListSheet(
                offers = group,
                selectedDistrict = group.first().district,
                onSelectOffer = { offer ->
                    markerGroupOffers = null
                    viewModel.selectOfferOnMap(offer)
                },
                onDismiss = { markerGroupOffers = null },
                title = "عروض ${group.first().restaurantName} (${group.size})",
                subtitle = group.first().district.ifBlank { "اختر العرض اللي يعجبك" }
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
            title = "حسابي 👤",
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

