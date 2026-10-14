package com.example.ui

import android.app.Activity
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseStorageHelper
import com.example.data.GoogleSignInHelper
import com.example.data.UserLocationManager
import com.example.data.model.LocationPresets
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.example.data.repository.RestaurantOfferRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppRole {
    CUSTOMER_MAP,      // خريطة العروض للمستكشف
    CUSTOMER_OFFERS,   // صفحة العروض لتصفح العروض والمسافات
    MERCHANT_PORTAL,   // صفحة التاجر الجديدة (تسجيل الدخول بالبريد وإضافة العروض)
    ADMIN_PORTAL       // صفحة الأدمن لإدارة التطبيق
}

class MerchantViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = RestaurantOfferRepository(application)
    private val locationManager = UserLocationManager(application)

    private fun getFirebaseAuthSafely(): FirebaseAuth? {
        return try {
            if (com.google.firebase.FirebaseApp.getApps(getApplication()).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(getApplication())
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            android.util.Log.w("MerchantViewModel", "FirebaseAuth initialization safely caught: ${e.message}")
            null
        }
    }

    private val auth by lazy { getFirebaseAuthSafely() }

    val currentRole = MutableStateFlow(AppRole.CUSTOMER_MAP)

    // User location state
    val userLocation: StateFlow<Location?> = locationManager.userLocation

    private val rawAllOffers: StateFlow<List<RestaurantOffer>> = repository.allOffers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val rawApprovedOffers: StateFlow<List<RestaurantOffer>> = repository.approvedOffers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Real distance recalculation dynamically based on user's real GPS location
    val allOffers: StateFlow<List<RestaurantOffer>> = combine(rawAllOffers, userLocation) { offers, loc ->
        if (loc == null) {
            offers
        } else {
            offers.map { offer ->
                val dist = UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, offer.latitude, offer.longitude)
                val driveTime = UserLocationManager.calculateDriveTimeMinutes(dist)
                offer.copy(distanceKm = dist, driveTimeMinutes = driveTime)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val approvedOffers: StateFlow<List<RestaurantOffer>> = combine(rawApprovedOffers, userLocation) { offers, loc ->
        if (loc == null) {
            offers
        } else {
            offers.map { offer ->
                val dist = UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, offer.latitude, offer.longitude)
                val driveTime = UserLocationManager.calculateDriveTimeMinutes(dist)
                offer.copy(distanceKm = dist, driveTimeMinutes = driveTime)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter states
    val searchQuery = MutableStateFlow("")
    val selectedRegion = MutableStateFlow(LocationPresets.regions[0].name) // Default Riyadh
    val selectedDistrict = MutableStateFlow("الكل")
    val selectedCuisine = MutableStateFlow("الكل")
    val selectedMediaType = MutableStateFlow("الكل")
    val customerSortBy = MutableStateFlow("NEAREST") // NEAREST, RATING, PRICE_LOW, DISCOUNT

    // Merchant authentication state
    val merchantEmail = MutableStateFlow<String?>(auth?.currentUser?.email ?: "saad@restaurant.com")
    val isMerchantLoggedIn = MutableStateFlow(auth?.currentUser != null)
    val merchantRestaurantName = MutableStateFlow("مطعم ومقهى ناي")
    val isUploadingMedia = MutableStateFlow(false)

    // Map & details interaction states
    val selectedOfferOnMap = MutableStateFlow<RestaurantOffer?>(null)
    val activeDetailOffer = MutableStateFlow<RestaurantOffer?>(null)
    val isAddOfferSheetOpen = MutableStateFlow(false)

    data class FilterParams(
        val query: String,
        val region: String,
        val district: String,
        val cuisine: String,
        val mediaFilter: String,
        val sortBy: String
    )

    private val filterParams = combine(
        combine(searchQuery, selectedRegion, selectedDistrict) { q, r, d -> Triple(q, r, d) },
        combine(selectedCuisine, selectedMediaType, customerSortBy) { c, m, s -> Triple(c, m, s) }
    ) { (query, region, district), (cuisine, mediaFilter, sortBy) ->
        FilterParams(query, region, district, cuisine, mediaFilter, sortBy)
    }

    // Filtered offers for Customer Views (Map & Offers List)
    val filteredCustomerOffers: StateFlow<List<RestaurantOffer>> = combine(
        approvedOffers,
        filterParams
    ) { offers, params ->
        val filtered = offers.filter { offer ->
            val matchesRegion = params.region == "الكل" || offer.region.contains(params.region)
            val matchesDistrict = params.district == "الكل" || offer.district == params.district
            val cleanCuisine = params.cuisine.replace(Regex("[^\\p{L}\\s]"), "").trim()
            val matchesCuisine = params.cuisine == "الكل" || offer.cuisineType.contains(cleanCuisine)
            val matchesMedia = when (params.mediaFilter) {
                "فيديوهات إعلانية" -> offer.mediaType == MediaType.VIDEO
                "صور فقط" -> offer.mediaType == MediaType.IMAGE
                else -> true
            }
            val matchesQuery = params.query.isBlank() ||
                offer.restaurantName.contains(params.query, ignoreCase = true) ||
                offer.offerTitle.contains(params.query, ignoreCase = true) ||
                offer.description.contains(params.query, ignoreCase = true) ||
                offer.district.contains(params.query, ignoreCase = true)

            matchesRegion && matchesDistrict && matchesCuisine && matchesMedia && matchesQuery
        }

        when (params.sortBy) {
            "NEAREST" -> filtered.sortedBy { it.distanceKm }
            "RATING" -> filtered.sortedByDescending { it.rating }
            "PRICE_LOW" -> filtered.sortedBy { it.price }
            "DISCOUNT" -> filtered.sortedByDescending { (it.originalPrice ?: it.price) - it.price }
            else -> filtered.sortedBy { it.distanceKm }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        locationManager.requestLocationUpdates()
        viewModelScope.launch {
            val uid = auth?.currentUser?.uid ?: "default_merchant"
            repository.seedInitialOffersIfEmpty(uid)
        }
    }

    fun requestLocationUpdates() {
        locationManager.requestLocationUpdates()
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            val result = GoogleSignInHelper.signInWithGoogle(activity)
            result.onSuccess { user ->
                if (user != null) {
                    merchantEmail.value = user.email
                    isMerchantLoggedIn.value = true
                    merchantRestaurantName.value = user.displayName ?: "متجر ${user.email?.substringBefore('@')}"
                }
            }
        }
    }

    fun loginMerchant(email: String, restaurantName: String) {
        merchantEmail.value = email.trim()
        merchantRestaurantName.value = restaurantName.ifBlank { "مطعم $email" }
        isMerchantLoggedIn.value = true
    }

    fun logoutMerchant() {
        auth?.signOut()
        isMerchantLoggedIn.value = false
        merchantEmail.value = null
        merchantRestaurantName.value = ""
    }

    fun setCustomerSortBy(sort: String) {
        customerSortBy.value = sort
    }

    fun focusOfferOnMap(offer: RestaurantOffer) {
        selectedOfferOnMap.value = offer
        currentRole.value = AppRole.CUSTOMER_MAP
    }

    fun setRole(role: AppRole) {
        currentRole.value = role
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onRegionSelect(region: String) {
        selectedRegion.value = region
        selectedDistrict.value = "الكل"
    }

    fun onDistrictSelect(district: String) {
        selectedDistrict.value = district
    }

    fun onCuisineSelect(cuisine: String) {
        selectedCuisine.value = cuisine
    }

    fun onMediaTypeSelect(mediaFilter: String) {
        selectedMediaType.value = mediaFilter
    }

    fun selectOfferOnMap(offer: RestaurantOffer?) {
        selectedOfferOnMap.value = offer
        if (offer != null) {
            launchSafely { repository.incrementViews(offer.id) }
        }
    }

    fun deselectOfferOnMap() {
        selectedOfferOnMap.value = null
    }

    fun openOfferDetail(offer: RestaurantOffer) {
        activeDetailOffer.value = offer
        launchSafely { repository.incrementViews(offer.id) }
    }

    fun closeOfferDetail() {
        activeDetailOffer.value = null
    }

    fun openAddOfferSheet() {
        isAddOfferSheetOpen.value = true
    }

    fun closeAddOfferSheet() {
        isAddOfferSheetOpen.value = false
    }

    fun saveOffer(offer: RestaurantOffer) {
        viewModelScope.launch {
            isUploadingMedia.value = true
            try {
                // Upload image or video to Firebase Storage and get public download URL
                val cloudUrl = FirebaseStorageHelper.uploadOfferMedia(
                    getApplication(),
                    offer.mediaUri,
                    offer.mediaType == MediaType.VIDEO
                )

                val currentUserId = auth?.currentUser?.uid ?: "merchant_${merchantEmail.value?.hashCode() ?: "guest"}"
                val finalOffer = offer.copy(
                    merchantId = currentUserId,
                    mediaUri = cloudUrl
                )
                repository.insertOffer(finalOffer)
                closeAddOfferSheet()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MerchantViewModel", "saveOffer failed: ${e.message}", e)
                android.widget.Toast.makeText(
                    getApplication(),
                    "تعذّر نشر العرض. تأكد من تسجيل الدخول بحساب Google ومن اتصال الإنترنت.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } finally {
                isUploadingMedia.value = false
            }
        }
    }

    fun deleteOffer(id: String) {
        launchSafely("تعذّر حذف العرض. يمكنك حذف عروضك أنت فقط.") {
            repository.deleteOfferById(id)
            if (activeDetailOffer.value?.id == id) {
                activeDetailOffer.value = null
            }
            if (selectedOfferOnMap.value?.id == id) {
                selectedOfferOnMap.value = null
            }
        }
    }

    fun toggleFeatured(id: String, featured: Boolean) {
        launchSafely("تعذّر تحديث العرض.") {
            repository.setOfferFeatured(id, featured)
        }
    }

    fun toggleApproved(id: String, approved: Boolean) {
        launchSafely("تعذّر تحديث العرض.") {
            repository.setOfferApproved(id, approved)
        }
    }

    /**
     * ينفّذ عملية على Firestore ويلتقط أي خطأ (صلاحيات، شبكة، وثيقة غير موجودة)
     * بدلاً من أن ينهار التطبيق. يعرض رسالة للمستخدم إذا وُجدت.
     */
    private fun launchSafely(userMessageOnError: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MerchantViewModel", "Operation failed: ${e.message}", e)
                if (userMessageOnError != null) {
                    android.widget.Toast.makeText(getApplication(), userMessageOnError, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationManager.stopLocationUpdates()
    }
}
