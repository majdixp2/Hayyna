package com.example.ui

import com.example.data.FavoritesStore
import com.example.data.model.MerchantProfile
import com.example.data.repository.OfferReport
import kotlinx.coroutines.tasks.await
import android.app.Activity
import android.app.Application
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminConfig
import com.example.data.MediaUploader
import com.example.data.GoogleSignInHelper
import com.example.data.OfferTracker
import com.example.data.UserLocationManager
import com.example.data.model.LocationPresets
import com.example.data.model.MediaType
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.data.repository.RestaurantOfferRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppRole {
    CUSTOMER_MAP,      // خريطة العروض
    CUSTOMER_OFFERS,   // صفحة العروض
    MERCHANT_PORTAL,   // صفحة التاجر
    ADMIN_PORTAL       // لوحة الأدمن (تظهر للأدمن فقط)
}

class MerchantViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = RestaurantOfferRepository(application)
    private val locationManager = UserLocationManager(application)

    private val auth: FirebaseAuth? = try {
        if (FirebaseApp.getApps(application).isEmpty()) FirebaseApp.initializeApp(application)
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAuth unavailable: ${e.message}")
        null
    }

    // ---------------- الحساب ----------------

    private val _currentUser = MutableStateFlow(auth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { _currentUser.value = it.currentUser }

    val isMerchantLoggedIn: StateFlow<Boolean> = currentUser.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth?.currentUser != null)

    val merchantEmail: StateFlow<String?> = currentUser.map { it?.email }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth?.currentUser?.email)

    val merchantRestaurantName: StateFlow<String> = currentUser.map { it?.displayName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth?.currentUser?.displayName.orEmpty())

    val isAdmin: StateFlow<Boolean> = currentUser.map { AdminConfig.isAdmin(it?.email) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdminConfig.isAdmin(auth?.currentUser?.email))

    val isSigningIn = MutableStateFlow(false)

    // ---------------- الموقع والوقت ----------------

    val userLocation: StateFlow<Location?> = locationManager.userLocation

    /** نبضة كل دقيقة لتحديث العدّاد التنازلي وإخفاء العروض المنتهية و"مفتوح الآن". */
    val nowMillis: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    // ---------------- العروض ----------------

    private fun withDistance(offers: List<RestaurantOffer>, loc: Location?): List<RestaurantOffer> =
        if (loc == null) offers else offers.map { offer ->
            val dist = UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, offer.latitude, offer.longitude)
            offer.copy(distanceKm = dist, driveTimeMinutes = UserLocationManager.calculateDriveTimeMinutes(dist))
        }

    /** كل العروض (للأدمن وللتاجر) مع المسافة الحقيقية. */
    val allOffers: StateFlow<List<RestaurantOffer>> = combine(repository.allOffers, userLocation) { offers, loc ->
        withDistance(offers, loc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** عروض التاجر الحالي فقط. */
    val merchantOffers: StateFlow<List<RestaurantOffer>> = combine(allOffers, currentUser) { offers, user ->
        if (user == null) emptyList() else offers.filter { it.merchantId == user.uid }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val approvedOffers: StateFlow<List<RestaurantOffer>> =
        combine(repository.approvedOffers, userLocation) { offers, loc -> withDistance(offers, loc) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- الفلاتر ----------------

    val searchQuery = MutableStateFlow("")
    val selectedRegion = MutableStateFlow("الكل") // الافتراضي: كل العروض، والترتيب حسب الأقرب لموقعك
    val selectedDistrict = MutableStateFlow("الكل")
    val selectedCuisine = MutableStateFlow("الكل")
    val selectedMediaType = MutableStateFlow("الكل")
    val customerSortBy = MutableStateFlow("NEAREST") // NEAREST, RATING, PRICE_LOW, DISCOUNT
    val openNowOnly = MutableStateFlow(false)
    val favoritesOnly = MutableStateFlow(false)

    // ---------------- المفضلة ----------------
    private val favoritesStore = FavoritesStore(application)
    val favoriteIds: StateFlow<Set<String>> = favoritesStore.ids

    fun toggleFavorite(offerId: String) = favoritesStore.toggle(offerId)
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }

    // ---------------- ملف المطعم والتعديل ----------------
    val merchantProfile = MutableStateFlow<MerchantProfile?>(null)
    val editingOffer = MutableStateFlow<RestaurantOffer?>(null)

    // ---------------- البلاغات (للأدمن) ----------------
    val reports: StateFlow<List<OfferReport>> = repository.reports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isUploadingMedia = MutableStateFlow(false)

    val selectedOfferOnMap = MutableStateFlow<RestaurantOffer?>(null)
    val activeDetailOffer = MutableStateFlow<RestaurantOffer?>(null)
    val isAddOfferSheetOpen = MutableStateFlow(false)

    val currentRole = MutableStateFlow(AppRole.CUSTOMER_MAP)

    data class FilterParams(
        val query: String,
        val region: String,
        val district: String,
        val cuisine: String,
        val mediaFilter: String,
        val sortBy: String,
        val openNowOnly: Boolean,
        val favoritesOnly: Boolean = false
    )

    private val filterParams = combine(
        combine(searchQuery, selectedRegion, selectedDistrict) { q, r, d -> Triple(q, r, d) },
        combine(selectedCuisine, selectedMediaType, customerSortBy) { c, m, s -> Triple(c, m, s) },
        openNowOnly
    ) { (query, region, district), (cuisine, mediaFilter, sortBy), openNow ->
        FilterParams(query, region, district, cuisine, mediaFilter, sortBy, openNow)
    }

    /** العروض الظاهرة للعملاء: معتمدة، غير منتهية، ومطابقة للفلاتر. */
    val filteredCustomerOffers: StateFlow<List<RestaurantOffer>> = combine(
        approvedOffers, filterParams, nowMillis, favoritesOnly, favoriteIds
    ) { offers, baseParams, now, favOnly, favIds ->
        val params = baseParams.copy(favoritesOnly = favOnly)
        val filtered = offers.filter { offer ->
            if (OfferTime.isExpired(offer, now)) return@filter false
            if (params.favoritesOnly && offer.id !in favIds) return@filter false
            val matchesRegion = params.region == "الكل" || regionKey(params.region).let { key ->
                offer.region.contains(key) || offer.district.contains(key)
            }
            val matchesDistrict = params.district == "الكل" ||
                normalizeDistrict(offer.district) == normalizeDistrict(params.district)
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
            val matchesOpenNow = !params.openNowOnly || OfferTime.isOpenNow(offer) == true

            matchesRegion && matchesDistrict && matchesCuisine && matchesMedia && matchesQuery && matchesOpenNow
        }

        // العروض المميزة (المدفوعة) دائماً في الأعلى، ثم حسب الترتيب المختار
        val sorted = when (params.sortBy) {
            "RATING" -> filtered.sortedByDescending { it.rating }
            "PRICE_LOW" -> filtered.sortedBy { it.price }
            "DISCOUNT" -> filtered.sortedByDescending { (it.originalPrice ?: it.price) - it.price }
            else -> filtered.sortedBy { it.distanceKm }
        }
        sorted.sortedByDescending { it.isFeatured }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        auth?.addAuthStateListener(authListener)
        viewModelScope.launch {
            currentUser.collect { user ->
                merchantProfile.value = null
                if (user != null) {
                    merchantProfile.value = runCatching { repository.getMerchantProfile(user.uid) }.getOrNull()
                }
            }
        }
        locationManager.requestLocationUpdates()
        OfferTracker.handler = { offerId, field ->
            launchSafely { repository.incrementCounter(offerId, field) }
        }
    }

    // ---------------- تسجيل الدخول ----------------

    fun signInWithGoogle(activity: Activity) {
        if (isSigningIn.value) return
        isSigningIn.value = true
        viewModelScope.launch {
            try {
                GoogleSignInHelper.signInWithGoogle(activity).onFailure { e ->
                    toast("تعذّر تسجيل الدخول: ${e.message ?: "خطأ غير معروف"}")
                }
            } finally {
                isSigningIn.value = false
            }
        }
    }

    fun logoutMerchant() {
        auth?.signOut()
        if (currentRole.value == AppRole.ADMIN_PORTAL) currentRole.value = AppRole.MERCHANT_PORTAL
    }

    // ---------------- التنقل والفلاتر ----------------

    fun setCustomerSortBy(sort: String) { customerSortBy.value = sort }

    fun focusOfferOnMap(offer: RestaurantOffer) {
        selectedOfferOnMap.value = offer
        currentRole.value = AppRole.CUSTOMER_MAP
    }

    fun setRole(role: AppRole) {
        if (role == AppRole.ADMIN_PORTAL && !isAdmin.value) return
        currentRole.value = role
    }

    fun requestLocationUpdates() = locationManager.requestLocationUpdates()
    fun onSearchQueryChange(query: String) { searchQuery.value = query }
    fun onRegionSelect(region: String) {
        selectedRegion.value = region
        selectedDistrict.value = "الكل"
    }
    fun onDistrictSelect(district: String) { selectedDistrict.value = district }
    fun onCuisineSelect(cuisine: String) { selectedCuisine.value = cuisine }
    fun onMediaTypeSelect(mediaFilter: String) { selectedMediaType.value = mediaFilter }
    fun toggleOpenNow() { openNowOnly.value = !openNowOnly.value }

    fun selectOfferOnMap(offer: RestaurantOffer?) {
        selectedOfferOnMap.value = offer
        if (offer != null) launchSafely { repository.incrementViews(offer.id) }
    }

    fun deselectOfferOnMap() { selectedOfferOnMap.value = null }

    fun openOfferDetail(offer: RestaurantOffer) {
        activeDetailOffer.value = offer
        launchSafely { repository.incrementViews(offer.id) }
    }

    fun closeOfferDetail() { activeDetailOffer.value = null }

    fun openAddOfferSheet() {
        if (currentUser.value == null) {
            toast("سجّل الدخول بحساب Google أولاً لإضافة عرض")
            return
        }
        editingOffer.value = null
        isAddOfferSheetOpen.value = true
    }

    fun closeAddOfferSheet() {
        isAddOfferSheetOpen.value = false
        editingOffer.value = null
    }

    /** يفتح صفحة العرض بوضع التعديل (للتاجر صاحب العرض). */
    fun startEditOffer(offer: RestaurantOffer) {
        val user = currentUser.value ?: return
        if (offer.merchantId != user.uid) return
        activeDetailOffer.value = null
        editingOffer.value = offer
        isAddOfferSheetOpen.value = true
    }

    // ---------------- إدارة العروض ----------------

    fun saveOffer(offer: RestaurantOffer) {
        val user = currentUser.value
        if (user == null) {
            toast("سجّل الدخول بحساب Google أولاً لإضافة عرض")
            return
        }
        viewModelScope.launch {
            isUploadingMedia.value = true
            try {
                val cloudUrl = MediaUploader.uploadOfferMedia(
                    getApplication(), offer.mediaUri, offer.mediaType == MediaType.VIDEO
                )
                val isUpdate = offer.id.isNotBlank()
                if (isUpdate) {
                    repository.updateOffer(offer.copy(mediaUri = cloudUrl))
                } else {
                    repository.insertOffer(
                        offer.copy(
                            merchantId = user.uid,
                            mediaUri = cloudUrl,
                            isFeatured = false,
                            isApproved = true,
                            viewsCount = 0,
                            directionsCount = 0,
                            whatsappCount = 0
                        )
                    )
                }
                // نحفظ بيانات المطعم عشان تتعبّى تلقائياً في العرض الجاي
                val profile = MerchantProfile(
                    restaurantName = offer.restaurantName,
                    phone = offer.phone,
                    latitude = offer.latitude,
                    longitude = offer.longitude,
                    region = offer.region,
                    district = offer.district,
                    cuisineType = offer.cuisineType,
                    openMinute = offer.openMinute,
                    closeMinute = offer.closeMinute
                )
                merchantProfile.value = profile
                runCatching { repository.saveMerchantProfile(user.uid, profile) }
                closeAddOfferSheet()
                toast(if (isUpdate) "تم حفظ التعديلات ✅" else "تم نشر العرض ✅")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saveOffer failed: ${e.message}", e)
                val msg = when {
                    e is MediaUploader.UploadException -> e.message
                    e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "رفض Firestore حفظ العرض (صلاحيات). تأكد من نشر قواعد Firestore الجديدة."
                    else -> "تعذّر نشر العرض: ${e.message ?: "خطأ غير معروف"}"
                }
                toast(msg ?: "تعذّر نشر العرض.")
            } finally {
                isUploadingMedia.value = false
            }
        }
    }

    fun reportOffer(offer: RestaurantOffer, reason: String) {
        launchSafely("تعذّر إرسال البلاغ. حاول مرة ثانية.") {
            repository.reportOffer(offer, reason, currentUser.value?.uid)
            toast("وصل بلاغك، شكراً لك 🙏")
        }
    }

    fun dismissReport(reportId: String) {
        launchSafely("تعذّر حذف البلاغ.") { repository.deleteReport(reportId) }
    }

    /** يحذف كل عروض التاجر وملفه ثم حسابه نهائياً. */
    fun deleteAccount() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteMerchantData(user.uid)
                user.delete().await()
                toast("تم حذف حسابك وكل عروضك")
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                toast("لأمانك: سجّل خروج ثم دخول مرة ثانية، وبعدها احذف الحساب.")
            } catch (e: Exception) {
                Log.w(TAG, "deleteAccount failed: ${e.message}", e)
                toast("تعذّر حذف الحساب: ${e.message ?: "خطأ غير معروف"}")
            }
        }
    }

    fun deleteOffer(id: String) {
        launchSafely("تعذّر حذف العرض. يمكنك حذف عروضك أنت فقط.") {
            repository.deleteOfferById(id)
            if (activeDetailOffer.value?.id == id) activeDetailOffer.value = null
            if (selectedOfferOnMap.value?.id == id) selectedOfferOnMap.value = null
        }
    }

    fun toggleFeatured(id: String, featured: Boolean) {
        launchSafely("تعذّر تحديث العرض.") { repository.setOfferFeatured(id, featured) }
    }

    fun toggleApproved(id: String, approved: Boolean) {
        launchSafely("تعذّر تحديث العرض.") { repository.setOfferApproved(id, approved) }
    }

    // ---------------- أدوات ----------------

    /** "منطقة مكة المكرمة - جدة" -> "مكة المكرمة" عشان تطابق اسم المنطقة الراجع من Google. */
    private fun regionKey(presetName: String): String =
        presetName.substringBefore(" - ").removePrefix("منطقة ").trim()

    /** "حي الملقا" و "الملقا" يعتبرون نفس الحي. */
    private fun normalizeDistrict(name: String): String =
        name.trim().removePrefix("حي ").trim()

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }

    /** ينفّذ عملية على Firestore ويلتقط أي خطأ بدل انهيار التطبيق. */
    private fun launchSafely(userMessageOnError: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Operation failed: ${e.message}", e)
                if (userMessageOnError != null) toast(userMessageOnError)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth?.removeAuthStateListener(authListener)
        OfferTracker.handler = null
        locationManager.stopLocationUpdates()
    }

    companion object {
        private const val TAG = "MerchantViewModel"
    }
}
