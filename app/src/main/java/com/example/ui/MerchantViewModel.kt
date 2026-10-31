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
import com.example.data.model.MediaType
import com.example.data.model.OfferTime
import com.example.data.model.RestaurantOffer
import com.example.data.model.isSoldOut
import com.example.data.model.atBranch
import com.example.data.model.effectiveBranches
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import com.example.ui.components.ExclusiveBadge
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

    // ---------------- 👥 حساب موظف (رقم المتجر + رقمه + رمز سري) ----------------
    val isStaffAccount: StateFlow<Boolean> = currentUser.map { com.example.data.model.StoreStaff.isStaffEmail(it?.email) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.model.StoreStaff.isStaffEmail(auth?.currentUser?.email))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val staffMe: StateFlow<com.example.data.model.StoreStaff?> = currentUser
        .flatMapLatest { u -> if (u != null && com.example.data.model.StoreStaff.isStaffEmail(u.email)) repository.staffSelf(u.uid) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** المتجر اللي نشتغل عليه: صاحب المحل = حسابه، الموظف = متجره (لو عنده الصلاحية) */
    private fun storeIdFlow(needs: (com.example.data.model.StoreStaff) -> Boolean) = combine(currentUser, staffMe) { u, st ->
        when {
            u == null -> null
            com.example.data.model.StoreStaff.isStaffEmail(u.email) -> st?.takeIf(needs)?.storeId
            else -> u.uid
        }
    }.distinctUntilChanged()
    val isStaffLoggingIn = MutableStateFlow(false)
    fun staffLogin(code: String, n: Int, pin: String, onError: (String) -> Unit) {
        if (isStaffLoggingIn.value) return
        viewModelScope.launch {
            isStaffLoggingIn.value = true
            try {
                val email = repository.staffLoginEmail(code, n) ?: return@launch onError("رقم المتجر أو رقم الموظف غلط")
                auth?.signInWithEmailAndPassword(email, pin)?.await() ?: return@launch onError("تعذّر الدخول")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val m = e.message.orEmpty()
                onError(when {
                    e is com.google.firebase.FirebaseTooManyRequestsException -> "محاولات كثيرة — انتظر شوي وجرّب"
                    e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException || e is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "الرمز السري غلط"
                    else -> "تعذّر الدخول: ${m.take(120)}"
                })
            } finally { isStaffLoggingIn.value = false }
        }
    }
    fun staffLogout() { auth?.signOut() }
    fun changeStaffPin(pin: String, onDone: (String) -> Unit) {
        val u = auth?.currentUser ?: return
        viewModelScope.launch {
            onDone(runCatching { u.updatePassword(pin).await(); "✅ تغيّر رمزك السري" }.getOrElse { e ->
                if (e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) "سجّل خروج وادخل من جديد، بعدها غيّره" else "تعذّر: ${e.message?.take(100)}" })
        }
    }

    // صاحب المحل: موظفينه ورقم متجره
    val storeCode = MutableStateFlow<String?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myStaff: StateFlow<List<com.example.data.model.StoreStaff>> = currentUser
        .flatMapLatest { u -> if (u == null || com.example.data.model.StoreStaff.isStaffEmail(u.email)) flowOf(emptyList()) else repository.storeStaffList(u.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isSavingStaff = MutableStateFlow(false)
    fun loadStoreCode() {
        val u = currentUser.value ?: return
        if (storeCode.value != null) return
        launchSafely("تعذّر إنشاء رقم المتجر") { storeCode.value = repository.ensureStoreCode(u.uid) }
    }
    fun saveStaff(old: com.example.data.model.StoreStaff?, name: String, role: String, perms: List<String>, pin: String, storeName: String, onDone: () -> Unit) {
        val u = currentUser.value ?: return
        val code = storeCode.value ?: return toast("انتظر رقم المتجر")
        if (perms.isEmpty()) return toast("اختر صلاحية وحدة على الأقل")
        viewModelScope.launch {
            isSavingStaff.value = true
            try {
                if (old != null && pin.isBlank()) repository.updateStaff(old.uid, mapOf("name" to name.take(40), "role" to role, "perms" to perms))
                else {
                    val n = old?.n ?: (1..50).firstOrNull { k -> myStaff.value.none { it.n == k } } ?: return@launch toast("وصلت للحد (50 موظف)")
                    repository.addStaff(getApplication(), u.uid, storeName, code, n, name.trim(), role, perms, pin, replace = old)
                }
                toast(if (old == null) "✅ انضاف الموظف" else if (pin.isNotBlank()) "✅ تغيّر الرمز السري" else "انحفظ ✅")
                onDone()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "staff: ${e.message}")
                toast(when {
                    e.message.orEmpty().contains("CONFIGURATION_NOT_FOUND") || e.message.orEmpty().contains("not allowed", true) -> "فعّل Email/Password في Firebase أول"
                    e is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "الرمز السري ضعيف، اختر 6 أرقام ثانية"
                    else -> "تعذّر الحفظ: ${e.message?.take(120)}"
                })
            } finally { isSavingStaff.value = false }
        }
    }
    fun setStaffActive(m: com.example.data.model.StoreStaff, active: Boolean) = launchSafely("تعذّر") {
        repository.updateStaff(m.uid, mapOf("active" to active)); toast(if (active) "▶️ رجع يشتغل" else "⏸️ انوقف — ما يقدر يدخل الحين")
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val staffOffers: StateFlow<List<RestaurantOffer>> = storeIdFlow { it.canStock }
        .flatMapLatest { id -> if (id == null || !com.example.data.model.StoreStaff.isStaffEmail(currentUser.value?.email)) flowOf(emptyList()) else repository.storeOffers(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun toggleOfferSoldOut(o: RestaurantOffer) = launchSafely("تعذّر التحديث") {
        val out = o.isSoldOut()
        val midnight = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        repository.setOfferSoldOut(o.id, if (out) null else midnight)
        toast(if (out) "✅ «${o.offerTitle}» رجع متوفر" else "❌ «${o.offerTitle}» نفد اليوم")
    }
    fun deleteStaff(m: com.example.data.model.StoreStaff) = launchSafely("تعذّر الحذف") { repository.deleteStaff(m); toast("انحذف") }

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
        if (loc == null) offers else offers.map { offer -> nearestBranchOf(offer, loc) }

    /** يختار أقرب فرع للمستخدم ويرجّع العرض بموقع ذاك الفرع ومسافته. */
    private fun nearestBranchOf(offer: RestaurantOffer, loc: Location): RestaurantOffer {
        val branches = offer.effectiveBranches
        val best = branches.minByOrNull {
            UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, it.latitude, it.longitude)
        } ?: return offer
        return withDistanceTo(if (offer.branches.isEmpty()) offer else offer.atBranch(best), loc)
    }

    private fun withDistanceTo(offer: RestaurantOffer, loc: Location?): RestaurantOffer {
        if (loc == null) return offer
        val dist = UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, offer.latitude, offer.longitude)
        return offer.copy(distanceKm = dist, driveTimeMinutes = UserLocationManager.calculateDriveTimeMinutes(dist))
    }

    /** العرض في فرع معيّن مع المسافة (للخريطة وقائمة الفروع). */
    fun offerAtBranch(offer: RestaurantOffer, branch: com.example.data.model.Branch): RestaurantOffer =
        withDistanceTo(if (offer.branches.isEmpty()) offer else offer.atBranch(branch), userLocation.value)

    /** كل العروض — للأدمن فقط (تشمل اللي بانتظار المراجعة). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allOffers: StateFlow<List<RestaurantOffer>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.allOffers else flowOf(emptyList()) }
        .combine(userLocation) { offers, loc -> withDistance(offers, loc) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** عروض التاجر الحالي فقط (المعتمدة واللي بانتظار المراجعة). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val merchantOffers: StateFlow<List<RestaurantOffer>> = currentUser
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else repository.offersByMerchant(user.uid) }
        .combine(userLocation) { offers, loc ->
            withDistance(offers, loc).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- صفحات المتاجر ----------------

    val approvedStores: StateFlow<List<com.example.data.model.StoreProfile>> = repository.approvedStores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingStores: StateFlow<List<com.example.data.model.StoreProfile>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.pendingStores else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** إظهار المتاجر اللي ما عندها عروض كنقاط صغيرة على الخريطة. */
    val showAllStores = MutableStateFlow(true)
    fun toggleShowAllStores() { showAllStores.value = !showAllStores.value }

    /** صفحة المتجر المفتوحة للعميل. */
    val openStore = MutableStateFlow<com.example.data.model.StoreProfile?>(null)

    fun openStorePage(store: com.example.data.model.StoreProfile) {
        activeDetailOffer.value = null
        openStore.value = store
    }

    fun closeStorePage() { openStore.value = null }

    // ---------------- تواصل معنا ----------------

    val supportWhatsapp: StateFlow<String> = repository.supportWhatsapp
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.model.Feedback.DEFAULT_SUPPORT_WHATSAPP)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myFeedback: StateFlow<List<com.example.data.model.Feedback>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repository.myFeedback(u.uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allFeedback: StateFlow<List<com.example.data.model.Feedback>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.allFeedback else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- التعبئة الذكية (يتحكم فيها الأدمن) ----------------

    val aiConfig: StateFlow<Pair<Boolean, String>> = repository.aiConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to RestaurantOfferRepository.DEFAULT_AI_MODEL)
    val isAutofilling = MutableStateFlow(false)
    /** آخر خطأ في التعبئة الذكية (يظهر للأدمن في لوحة الإدارة). */
    val lastAiError = MutableStateFlow<String?>(null)

    fun autofillFromImage(imageUri: String, onResult: (com.example.data.AiAutofill.Draft) -> Unit) {
        val (enabled, model) = aiConfig.value
        if (!enabled || isAutofilling.value) return
        viewModelScope.launch {
            isAutofilling.value = true
            try {
                // نتجاهل 2.5 (صارت مقصورة على المستخدمين القدامى) ونجرّب الاحتياط
                val models = (listOf(model).filter { !it.startsWith("gemini-2.5") } +
                    RestaurantOfferRepository.FALLBACK_AI_MODELS)
                val draft = com.example.data.AiAutofill.analyzeWithFallback(getApplication(), imageUri, models)
                lastAiError.value = null
                onResult(draft)
                toast("✨ عبّينا البيانات من الصورة — راجعها قبل النشر")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "autofill failed: ${e.message}", e)
                lastAiError.value = "${e.javaClass.simpleName}: ${e.message}".take(400)
                toast(
                    if (isAdmin.value) "خطأ التعبئة الذكية (يظهر لك كأدمن): ${e.message?.take(150)}"
                    else "التعبئة الذكية مو متاحة الحين، عبّي البيانات يدوياً 🙏"
                )
            } finally {
                isAutofilling.value = false
            }
        }
    }

    /** رمز تجربة App Check (للأدمن ينسخه ويسجّله في Firebase). */
    val appCheckDebugToken = MutableStateFlow<String?>(null)

    fun loadAppCheckToken() {
        viewModelScope.launch {
            appCheckDebugToken.value = runCatching { com.example.data.AppCheckHelper.debugToken(getApplication()) }.getOrNull()
                ?: "ما قدرنا نقرأ الرمز"
        }
    }

    fun saveAiConfig(enabled: Boolean, model: String) {
        launchSafely("تعذّر حفظ الإعداد.") {
            repository.setAiConfig(enabled, model)
            toast(if (enabled) "التعبئة الذكية مفعّلة ✨" else "التعبئة الذكية موقفة")
            audit("${if (enabled) "تشغيل" else "إيقاف"}: التعبئة الذكية", "settings", details = model)
        }
    }

    // ---------------- نقاط محلات Google + الدعوات ----------------

    val poiConfig: StateFlow<com.example.data.repository.PoiConfig> = repository.poiConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.repository.PoiConfig())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val topPoi: StateFlow<List<com.example.data.repository.PoiInterest>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.topPoi else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** المحل اللي ضغطه العميل من نقاط Google (مو مسجّل في لكوفر). */
    val selectedPoi = MutableStateFlow<com.google.android.gms.maps.model.PointOfInterest?>(null)
    /** محل يبي صاحبه يسجّله — نعبّي اسمه وموقعه تلقائياً. */
    val pendingClaim = MutableStateFlow<com.google.android.gms.maps.model.PointOfInterest?>(null)

    fun onPoiClick(poi: com.google.android.gms.maps.model.PointOfInterest) {
        if (!poiConfig.value.inviteEnabled && !isScout.value) return
        selectedOfferOnMap.value = null
        selectedPoi.value = poi
        launchSafely { repository.logPoi(poi.placeId, "taps") }
    }

    fun dismissPoi() { selectedPoi.value = null }

    // ---------------- 📷 عروض من إعلانات ----------------

    /** الأدمن أو موظف بصلاحية «عروض الإعلانات»: يرفع عرض من صورة إعلان لمحل مو مسجّل. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isScout: StateFlow<Boolean> = currentUser
        .mapLatest { u -> if (u == null) false else repository.isScout(u.email) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** المحل اللي نرفع له عرض الحين (يفتح شاشة الرفع). */
    val adUploadPoi = MutableStateFlow<com.google.android.gms.maps.model.PointOfInterest?>(null)
    val isPublishingAd = MutableStateFlow(false)

    fun openAdUpload(poi: com.google.android.gms.maps.model.PointOfInterest) {
        selectedPoi.value = null
        adUploadPoi.value = poi
    }
    fun closeAdUpload() { if (!isPublishingAd.value) adUploadPoi.value = null }
    suspend fun adScoutDays(): Int = repository.adScoutDays()

    data class AdDraft(
        val imageUri: String, val name: String, val title: String, val price: String, val originalPrice: String,
        val description: String, val phone: String, val category: String, val days: Int, val tags: List<String>
    )

    fun publishAdOffer(poi: com.google.android.gms.maps.model.PointOfInterest, place: com.example.data.PlacesLookup.Info?, d: AdDraft) {
        val uid = currentUser.value?.uid ?: return
        if (isPublishingAd.value) return
        viewModelScope.launch {
            isPublishingAd.value = true
            try {
                val url = com.example.data.MediaUploader.uploadOfferMedia(getApplication(), d.imageUri, false)
                fun num(v: String) = v.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("")
                    .filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0 }
                val price = num(d.price) ?: 0.0
                val orig = num(d.originalPrice)?.takeIf { it > price }
                val pct = if (orig != null && price > 0) ((orig - price) / orig * 100).toInt() else 0
                val days = d.days.coerceIn(1, 31)
                val data = mapOf<String, Any?>(
                    "merchantId" to "poi_" + poi.placeId, "placeId" to poi.placeId, "source" to "ad", "scoutedBy" to uid,
                    "restaurantName" to d.name.trim().ifBlank { place?.name ?: poi.name }.ifBlank { "محل" }.take(100),
                    "offerTitle" to d.title.trim().take(150),
                    "description" to d.description.trim().ifBlank { "عرض من إعلان المحل." }.take(2000),
                    "cuisineType" to d.category.take(60), "postType" to "offer",
                    "price" to price, "originalPrice" to orig, "currency" to "ر.س",
                    "discountBadge" to (if (pct >= 1) "خصم $pct% 🔥" else ""),
                    "mediaType" to "IMAGE", "mediaUri" to url, "mediaUris" to listOf(url), "isPromotionalVideo" to false,
                    "district" to (place?.district ?: "").take(100), "region" to (place?.city ?: "").take(100),
                    "latitude" to poi.latLng.latitude, "longitude" to poi.latLng.longitude,
                    "phone" to d.phone.filter { it.isDigit() || it == '+' }.ifBlank { place?.phone?.filter { it.isDigit() || it == '+' } ?: "" }.take(30),
                    "openMinute" to -1, "closeMinute" to -1, "workingHours" to "",
                    "expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + days * 86_400_000L)),
                    "startsAt" to null, "discountPercent" to 0, "dailyPercents" to emptyList<Int>(), "itemsScope" to "",
                    "grandOpening" to false, "exclusive" to false, "tags" to d.tags.take(12), "delivery" to false,
                    "logoUrl" to "", "branches" to emptyList<Any>(), "deal" to null
                )
                val id = repository.addAdOffer(data)
                audit("رفع عرض من إعلان", "scout", details = "${d.title} — ${poi.placeId}")
                adUploadPoi.value = null
                toast("انتشر العرض على الخريطة ✅")
                Log.d(TAG, "ad offer $id")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "ad offer failed", e)
                toast(
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                        "ما عندك صلاحية رفع عروض الإعلانات" else (e.message?.take(150) ?: "تعذّر النشر، جرّب مرة ثانية")
                )
            } finally {
                isPublishingAd.value = false
            }
        }
    }

    /** «أنا صاحب المحل» من عرض إعلان: نفس تسجيل محل Google. */
    fun claimAdOffer(offer: RestaurantOffer) {
        selectedOfferOnMap.value = null
        claimPoi(com.google.android.gms.maps.model.PointOfInterest(
            com.google.android.gms.maps.model.LatLng(offer.latitude, offer.longitude), offer.placeId, offer.restaurantName))
    }

    /** نص الدعوة (الأدمن يعدّله من الإعدادات). */
    fun inviteText(poi: com.google.android.gms.maps.model.PointOfInterest): String {
        val c = poiConfig.value
        val msg = c.inviteMessage.replace("{name}", poi.name.trim())
        val base = c.appLink.ifBlank { com.example.data.OfferSharer.WEB_BASE }
        // رابط فيه رمز صاحب الدعوة: لو سجّل المحل من الويب تنحسب النقاط له
        val uid = auth?.currentUser?.uid
        val link = if (uid != null) base + (if ('?' in base) "&" else "?") + "ref=" + uid else base
        return "$msg\n$link"
    }

    fun logInvite(poi: com.google.android.gms.maps.model.PointOfInterest) {
        launchSafely { repository.logPoi(poi.placeId, "invites") }
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch { runCatching { repository.lockPoiInvite(poi.placeId, uid, INVITE_LOCK_DAYS) } }
    }

    /** حالة قفل الدعوة لمحل: (أنا اللي دعيته؟, ينتهي متى) أو null لو مفتوح. */
    suspend fun poiInviteLock(placeId: String): Pair<Boolean, Long>? {
        val lock = runCatching { repository.getPoiInvite(placeId) }.getOrNull() ?: return null
        if (lock.second <= System.currentTimeMillis()) return null
        return (lock.first == currentUser.value?.uid) to lock.second
    }

    /** الدعوة تحتاج تسجيل دخول (عشان تنحسب باسمه وتنقفل للمحل). */
    fun loginToInvite() {
        selectedPoi.value = null
        currentRole.value = AppRole.MERCHANT_PORTAL
        toast("سجّل دخولك عشان تدعو المحل وتنحسب لك النقاط 🎁")
    }

    /** "أنا صاحب المحل": نوديه للتسجيل كتاجر وبيانات محله جاهزة. */
    fun claimPoi(poi: com.google.android.gms.maps.model.PointOfInterest) {
        selectedPoi.value = null
        pendingClaim.value = poi
        launchSafely { repository.logPoi(poi.placeId, "claims") }
        when {
            currentUser.value == null -> {
                currentRole.value = AppRole.MERCHANT_PORTAL
                toast("سجّل دخول كتاجر، وبنعبّي اسم محلك وموقعه لك 🏪")
            }
            !isMerchantAccount -> {
                pendingClaim.value = null
                toast("صاحب المحل؟ سجّل خروج وادخل بحساب صاحب محل عشان تسجّله")
            }
            else -> isBranchesPageOpen.value = true
        }
    }

    fun savePoiConfig(c: com.example.data.repository.PoiConfig) {
        launchSafely("تعذّر الحفظ.") {
            repository.setPoiConfig(c)
            toast("تم الحفظ ✅")
            audit("تعديل إعدادات محلات Google والدعوة", "settings")
        }
    }

    // ---------------- توثيق المتاجر ----------------

    val verifyConfig: StateFlow<com.example.data.model.VerifyConfig> = repository.verifyConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.model.VerifyConfig())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myVerification: StateFlow<com.example.data.model.VerificationRequest?> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(null) else repository.myVerification(u.uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingVerifications: StateFlow<List<com.example.data.model.VerificationRequest>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.pendingVerifications else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isVerifyOpen = MutableStateFlow(false)
    val isSubmittingVerification = MutableStateFlow(false)
    fun openVerify() { isVerifyOpen.value = true }
    fun closeVerify() { isVerifyOpen.value = false }

    /** المسافة من موقع المستخدم الحين لأقرب فرع (بالمتر)، أو null لو الموقع غير معروف. */
    fun distanceToNearestBranchMeters(): Int? {
        val loc = userLocation.value ?: return null
        val branches = merchantProfile.value?.branches.orEmpty()
        if (branches.isEmpty()) return null
        return branches.minOf {
            UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, it.latitude, it.longitude)
        }.let { (it * 1000).toInt() }
    }

    fun submitVerification(docType: String, docNumber: String, photoLocal: String?, distance: Int?) {
        val user = currentUser.value ?: return
        if (isSubmittingVerification.value) return
        val cfg = verifyConfig.value
        viewModelScope.launch {
            isSubmittingVerification.value = true
            try {
                val photo = photoLocal?.let { MediaUploader.uploadOfferMedia(getApplication(), it, false) }.orEmpty()
                repository.submitVerification(
                    com.example.data.model.VerificationRequest(
                        uid = user.uid,
                        storeName = merchantProfile.value?.restaurantName.orEmpty(),
                        docType = docType,
                        docNumber = docNumber.trim(),
                        photoUrl = photo,
                        locationChecked = cfg.requireLocation && distance != null,
                        locationOk = distance != null && distance <= com.example.data.model.VerificationRequest.MAX_DISTANCE_METERS,
                        distanceMeters = distance ?: -1
                    )
                )
                isVerifyOpen.value = false
                toast("وصل طلب التوثيق ✅ بنراجعه ونرد عليك قريب")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "verification failed: ${e.message}", e)
                toast("تعذّر إرسال الطلب: ${e.message ?: "خطأ"}")
            } finally {
                isSubmittingVerification.value = false
            }
        }
    }

    /** الأدمن: يوثّق المتجر (✓ + موثوق ينشر مباشرة) أو يرفض مع السبب. */
    fun decideVerification(req: com.example.data.model.VerificationRequest, approve: Boolean, reason: String = "") {
        launchSafely("تعذّر الحفظ.") {
            repository.decideVerification(req.uid, approve, reason)
            if (approve) {
                val cur = merchantStatuses.value.firstOrNull { it.uid == req.uid }
                repository.setMerchantStatus(
                    (cur ?: com.example.data.repository.MerchantStatus(req.uid)).copy(
                        trusted = true, suspended = false, verified = true, name = req.storeName
                    )
                )
            }
            toast(if (approve) "تم توثيق ${req.storeName} ✓" else "تم رفض الطلب")
            audit(if (approve) "توثيق متجر ✓" else "رفض توثيق", "verify", "store", req.uid, req.storeName,
                if (approve) "${req.docType}: ${req.docNumber}" else "السبب: ${reason.ifBlank { "—" }}")
        }
    }

    /** تصنيفات المتاجر (من إعدادات الأدمن). */
    val categories: StateFlow<List<String>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestaurantOfferRepository.DEFAULT_CATEGORIES)

    fun saveCategories(list: List<String>) {
        launchSafely("تعذّر حفظ التصنيفات.") {
            repository.setCategories(list)
            toast("تم حفظ التصنيفات ✅")
            audit("تعديل التصنيفات", "settings", details = list.joinToString("، "))
        }
    }

    fun renameCategory(old: String, new: String) {
        val name = new.trim()
        if (name.isBlank() || name == old) return
        launchSafely("تعذّر تغيير الاسم.") {
            repository.setCategories(categories.value.map { if (it == old) name else it })
            val n = repository.renameCategory(old, name)
            toast("تم تغيير الاسم ✅ وتحدّث $n عرض")
            audit("إعادة تسمية تصنيف", "settings", details = "$old ← $name ($n عرض)")
        }
    }

    fun saveVerifyConfig(c: com.example.data.model.VerifyConfig) {
        launchSafely("تعذّر الحفظ.") {
            repository.setVerifyConfig(c)
            toast("تم الحفظ ✅")
            audit("تعديل إعدادات التوثيق", "settings")
        }
    }

    val isContactOpen = MutableStateFlow(false)
    val isSendingFeedback = MutableStateFlow(false)

    fun openContact() { isContactOpen.value = true }
    fun closeContact() { isContactOpen.value = false }

    val deviceInfo: String
        get() = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} • Android ${android.os.Build.VERSION.RELEASE}"
    val appVersion: String get() = com.example.BuildConfig.VERSION_NAME

    fun sendFeedback(type: String, message: String, screenshotLocal: String?, contact: String, onSent: () -> Unit) {
        if (isSendingFeedback.value) return
        viewModelScope.launch {
            isSendingFeedback.value = true
            try {
                val shot = screenshotLocal?.let { MediaUploader.uploadOfferMedia(getApplication(), it, false) }.orEmpty()
                val user = currentUser.value
                repository.sendFeedback(
                    com.example.data.model.Feedback(
                        uid = user?.uid,
                        userName = user?.displayName.orEmpty(),
                        role = when {
                            user == null -> "زائر"
                            isMerchantAccount -> "تاجر"
                            else -> "عميل"
                        },
                        type = type,
                        message = message.trim(),
                        screenshotUrl = shot,
                        contact = contact.trim().ifBlank { user?.email.orEmpty() },
                        appVersion = appVersion,
                        device = deviceInfo
                    )
                )
                toast("وصلت رسالتك، شكراً لك 🙏 بنرد عليك بأقرب وقت")
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sendFeedback failed: ${e.message}", e)
                toast(if (e is MediaUploader.UploadException) e.message ?: "تعذّر رفع الصورة" else "تعذّر الإرسال، حاول مرة ثانية")
            } finally {
                isSendingFeedback.value = false
            }
        }
    }

    fun markRepliesSeen() {
        myFeedback.value.filter { !it.replySeen }.forEach { f ->
            launchSafely { repository.markReplySeen(f.id) }
        }
    }

    fun replyFeedback(id: String, reply: String, status: String) {
        launchSafely("تعذّر إرسال الرد.") {
            repository.replyFeedback(id, reply, status)
            toast("تم ✅")
            audit("رد على رسالة", "messages", "message", id, "", reply.take(600))
        }
    }

    fun setFeedbackStatus(id: String, status: String) {
        launchSafely("تعذّر التحديث.") {
            repository.setFeedbackStatus(id, status)
            audit(if (status == "done") "إنهاء رسالة" else "رسالة قيد المتابعة", "messages", "message", id)
        }
    }

    fun deleteFeedback(id: String) {
        launchSafely("تعذّر الحذف.") {
            repository.deleteFeedback(id)
            audit("حذف رسالة", "messages", "message", id)
        }
    }

    fun saveSupportWhatsapp(number: String) {
        val normalized = com.example.ui.components.normalizeSaudiPhone(number)
        if (normalized == null) {
            toast("اكتب رقم جوال صحيح مثل 05xxxxxxxx")
            return
        }
        launchSafely("تعذّر حفظ الرقم.") {
            repository.setSupportWhatsapp(com.example.ui.components.toLocalPhone(normalized))
            toast("تم تحديث رقم الدعم ✅")
        }
    }

    // ---------------- الحماية: حالة التاجر ----------------

    /** حالة التاجر الحالي (موثوق / موقوف). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myStatus: StateFlow<com.example.data.repository.MerchantStatus?> = currentUser
        .flatMapLatest { user -> if (user == null) flowOf(null) else repository.merchantStatus(user.uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** الأدمن والتاجر الموثوق ينشرون مباشرة. */
    private val canPublishDirectly: Boolean
        get() = isAdmin.value || (myStatus.value?.trusted == true && myStatus.value?.suspended != true)

    /** حالات كل التجار (للأدمن). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val merchantStatuses: StateFlow<List<com.example.data.repository.MerchantStatus>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.allMerchantStatuses else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val creditsConfig: StateFlow<com.example.data.repository.CreditsConfig> = repository.creditsConfig
        .onEach { ExclusiveBadge.label = if (it.exclusiveEnabled) "${it.exclusiveIcon} ${it.exclusiveLabel}" else "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.repository.CreditsConfig())

    // ---------------- رصيد العروض (المحفظة) ----------------
    // الشراء برّا التطبيق (بدون أي رابط دفع)، وهنا نعرض الرصيد ونصرفه على النشر والتمييز والإبراز.

    /** -1 = يحمّل، null = ما عنده محفظة، غيره = الرصيد */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val walletBalance: StateFlow<Long?> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(null) else repository.walletBalance(u.uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WALLET_LOADING)
    private fun balance(): Long = walletBalance.value?.takeIf { it >= 0 } ?: 0L

    val activeBoostsAll: StateFlow<List<com.example.data.repository.Boost>> = repository.activeBoosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var ensuringWallet = false
    /** أول ما يفتح التاجر حسابه والرصيد مفعّل: ننشئ محفظته (مع هدية الترحيب). */
    fun ensureWallet() {
        val uid = currentUser.value?.uid ?: return
        if (ensuringWallet || walletBalance.value != null || !creditsConfig.value.enabled) return
        ensuringWallet = true
        viewModelScope.launch {
            val w = creditsConfig.value.welcome
            runCatching { repository.createWelcomeWallet(uid, w) }
                .onSuccess { if (w > 0) toast("🎁 أهلاً بك! أضفنا لك $w نقطة هدية") }
                .onFailure { Log.w(TAG, "wallet: ${it.message}") }
            ensuringWallet = false
        }
    }

    data class Price(val item: com.example.data.repository.CreditItem, val days: Int, val full: Long, val cost: Long, val disc: Int)

    /** تكلفة خدمة على عرض (النشر عليه خصم لو العرض حصري ومكافأة الحصري شغّالة). null = الخدمة موقفة. */
    fun priceFor(itemId: String, days: Int, excl: Boolean): Price? {
        val cr = creditsConfig.value
        val it = cr.items[itemId]?.takeIf { it.active } ?: return null
        val d = it.daysFor(days.coerceIn(1, 90))
        val full = it.baseCost(d)
        val disc = if (it.effect == "publish" && excl && cr.rewardOn) cr.rewardPercent else 0
        val cost = if (disc > 0) Math.ceil(full * (100 - disc) / 100.0).toLong() else full
        return Price(it, d, full, cost, disc)
    }
    private fun spendNote(note: String, disc: Int) = if (disc > 0) "$note (💎 ${if (disc >= 100) "مجاني" else "خصم $disc%"})" else note

    private fun publishDaysOf(o: RestaurantOffer): Int {
        if (o.dailyPercents.isNotEmpty()) return o.dailyPercents.size
        val base = o.startsAt?.toDate()?.time ?: System.currentTimeMillis()
        val exp = o.expiresAt?.toDate()?.time ?: return 7
        return Math.round((exp - base) / 86_400_000.0).toInt().coerceIn(1, 90)
    }

    /** رسائل الرصيد: رصيد ما يكفي / انحفظت المسودة / نشر مسودة */
    data class CreditPrompt(
        val kind: String, val offer: RestaurantOffer? = null, val days: Int = 0, val fullCost: Long = 0, val exclCost: Long = 0,
        val myCost: Long = 0, val balance: Long = 0, val exclOk: Boolean = false, val canPay: Boolean = false
    ) {
        companion object { const val SHORT = "short"; const val SAVED = "saved"; const val DRAFT = "draft" }
    }
    val creditPrompt = MutableStateFlow<CreditPrompt?>(null)
    fun dismissCreditPrompt() { creditPrompt.value = null }

    /** من رسالة "رصيدك ما يكفي": excl = خلّه حصري وانشره، غيره = احفظه مسودة */
    fun resolveShortfall(makeExclusive: Boolean) {
        val p = creditPrompt.value ?: return
        val o = p.offer ?: return
        creditPrompt.value = null
        if (makeExclusive) saveOffer(o.copy(exclusive = true)) else saveOffer(o, asDraft = true)
    }

    /** زر "📢 انشر الحين" على المسودة */
    fun openPublishDraft(o: RestaurantOffer) {
        val days = publishDaysOf(o)
        val full = priceFor("publish", days, false) ?: run { toast("خدمة النشر موقفة حالياً."); return }
        val ex = priceFor("publish", days, true) ?: full
        val mine = if (o.exclusive) ex else full
        creditPrompt.value = CreditPrompt(
            kind = CreditPrompt.DRAFT, offer = o, days = days, fullCost = full.cost, exclCost = ex.cost, myCost = mine.cost, balance = balance(),
            canPay = isAdmin.value || balance() >= mine.cost,
            exclOk = !o.exclusive && creditsConfig.value.rewardOn && balance() >= ex.cost && ex.cost < full.cost
        )
    }

    fun publishDraft(makeExclusive: Boolean) {
        val p = creditPrompt.value ?: return
        val o = p.offer ?: return
        val uid = currentUser.value?.uid ?: return
        creditPrompt.value = null
        val excl = o.exclusive || makeExclusive
        val now = System.currentTimeMillis(); val dayMs = 86_400_000L
        val st = o.startsAt?.toDate()?.time ?: 0L
        val start = if (st > now) st else now
        val tiers = o.dailyPercents.size
        val pr = priceFor("publish", p.days, excl) ?: return
        val expiry = if (tiers > 0) com.example.data.model.OfferPricing.midnight(start) + tiers * dayMs else start + pr.days * dayMs
        if (!isAdmin.value && balance() < pr.cost) { toast("رصيدك ما يكفي."); return }
        launchSafely("تعذّر النشر. جرّب مرة ثانية.") {
            repository.spendOnOffer(
                uid = uid, balance = balance(), charge = !isAdmin.value, offerId = o.id, item = pr.item, days = pr.days,
                cost = if (isAdmin.value) pr.full else pr.cost, disc = if (isAdmin.value) 0 else pr.disc, startMillis = start,
                note = spendNote("📢 نشر: ${o.offerTitle}", if (isAdmin.value) 0 else pr.disc),
                offerUpdate = mapOf(
                    "draft" to false, "exclusive" to excl,
                    "startsAt" to com.google.firebase.Timestamp(java.util.Date(start)),
                    "expiresAt" to com.google.firebase.Timestamp(java.util.Date(expiry))
                )
            )
            toast(if (!o.isApproved) "انرسل للمراجعة ⏳ بيظهر بعد الموافقة" else if (pr.cost == 0L || isAdmin.value) "تم نشر العرض ✅" else "تم نشر العرض ✅ خصمنا ${pr.cost} نقطة")
        }
    }

    // ----- شراء خدمة (تمديد نشر / تمييز / إبراز) لعرض موجود -----
    val boostTarget = MutableStateFlow<RestaurantOffer?>(null)
    fun openBoost(o: RestaurantOffer) { boostTarget.value = o }
    fun closeBoost() { boostTarget.value = null }

    /** كم إبراز نشط في نفس الحي (للحد الأقصى) */
    fun spotlightsInDistrict(district: String): Int {
        val now = System.currentTimeMillis(); val key = normalizeDistrict(district)
        val ids = activeBoostsAll.value.filter { it.effect == "spotlight" && it.startsAt <= now && now < it.endsAt }.map { it.offerId }.toSet()
        return (approvedOffers.value + merchantOffers.value).distinctBy { it.id }.count { it.id in ids && normalizeDistrict(it.district) == key }
    }

    fun buyBoost(o: RestaurantOffer, itemId: String, days: Int) {
        val uid = currentUser.value?.uid ?: return
        val pr = priceFor(itemId, days, o.exclusive) ?: return
        if (!isAdmin.value && balance() < pr.cost) { toast("رصيدك ما يكفي (تحتاج ${pr.cost} نقطة)."); return }
        val now = System.currentTimeMillis()
        val pubUntil = activeBoostsAll.value.filter { it.offerId == o.id && it.effect == "publish" }.maxOfOrNull { it.endsAt } ?: 0L
        val cr = creditsConfig.value
        val created = o.createdAt?.toDate()?.time ?: now
        val grace = if (cr.enabledAtMillis > 0 && created < cr.enabledAtMillis && cr.graceUntilMillis != Long.MAX_VALUE) cr.graceUntilMillis else 0L
        // تمديد النشر يبدأ من نهاية النشر الحالي (أو نهاية فترة السماح)
        val start = if (pr.item.effect == "publish") maxOf(now, pubUntil, grace) else now
        boostTarget.value = null
        launchSafely("تعذّرت العملية. جرّب مرة ثانية.") {
            repository.spendOnOffer(
                uid = uid, balance = balance(), charge = !isAdmin.value, offerId = o.id, item = pr.item, days = pr.days,
                cost = if (isAdmin.value) pr.full else pr.cost, disc = if (isAdmin.value) 0 else pr.disc, startMillis = start,
                note = spendNote("${pr.item.name} — ${o.offerTitle}", if (isAdmin.value) 0 else pr.disc)
            )
            toast(if (pr.cost == 0L || isAdmin.value) "تم ✅" else "تم ✅ خصمنا ${pr.cost} نقطة")
        }
    }

    // ---------------- 🛍️ المنتجات والخدمات والمنيو + الطلبات ----------------
    val catalogConfig: StateFlow<com.example.data.model.CatalogConfig> = repository.catalogConfig
        .onEach { com.example.data.model.CatalogFlags.superGlow = it.feat("superGlow"); com.example.data.model.CatalogFlags.crop = it.feat("crop")
            com.example.data.model.CatalogFlags.eta = it.feat("eta"); com.example.data.model.CatalogFlags.ratings = it.feat("ratings") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.model.CatalogConfig())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myItems: StateFlow<List<com.example.data.model.CatalogItem>> = storeIdFlow { it.canStock }
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.myItems(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myPasses: StateFlow<List<com.example.data.model.ItemPass>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repository.myPasses(u.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val storeOrdersRaw: StateFlow<Pair<List<com.example.data.model.HayynaOrder>, Boolean>?> = storeIdFlow { it.canOrders }
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.storeOrders(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val storeOrders: StateFlow<List<com.example.data.model.HayynaOrder>> = storeOrdersRaw.map { it?.first.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** عدّاد «ما انستلم» للعملاء (يظهر للمحل جنب الطلب) */
    val custStats = MutableStateFlow<Map<String, com.example.data.model.CustStats?>>(emptyMap())
    private fun loadCustStats(uids: List<String>) {
        if (!catalogConfig.value.feat("noshow")) return
        uids.filter { it.isNotBlank() && it !in custStats.value }.distinct().forEach { u ->
            custStats.value = custStats.value + (u to null)
            viewModelScope.launch { val st = repository.custStats(u); if (st != null) custStats.value = custStats.value + (u to st) }
        }
    }

    /** منتجاتي تنزل مباشرة؟ (الموثّق ✔ / الموثوق / الأدمن / المراجعة مطفية) */
    val catalogDirect: Boolean
        get() = isAdmin.value || canPublishDirectly || (myStatus.value?.verified == true && myStatus.value?.suspended != true) || !catalogConfig.value.review

    val isSavingItem = MutableStateFlow(false)
    fun saveCatalogItem(old: com.example.data.model.CatalogItem?, kind: String, name: String, price: Double, desc: String, section: String, available: Boolean, image: String) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            isSavingItem.value = true
            try {
                val url = if (image.isNotBlank() && !image.startsWith("http")) MediaUploader.uploadOfferMedia(getApplication(), image, false) else image
                val cfg = catalogConfig.value
                // المرجّع من الأدمن يرجع للمراجعة بعد التعديل
                val item = if (old != null) old.copy(name = name.trim(), price = price, desc = desc.trim(), section = section.trim(), available = available, imageUrl = url, approved = old.adminNote.isBlank() && catalogDirect)
                else {
                    val used = myItems.value.filter { it.kind == kind }.map { it.slot }.toSet()
                    var n = 0; while (n in used) n++
                    val lim = cfg.limitOf(kind)
                    if (lim > 0 && n >= lim) { toast("وصلت للحد ($lim)."); return@launch }
                    com.example.data.model.CatalogItem("${uid}_${kind}_$n", uid, kind, n, name.trim(), desc.trim(), price, url, section.trim(), available, catalogDirect)
                }
                repository.saveItem(item, isNew = old == null)
                toast(if (catalogDirect) "انحفظ ✅" else "انحفظ ✅ بيظهر بعد المراجعة")
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "item: ${e.message}"); toast("تعذّر الحفظ: ${e.message ?: ""}")
            } finally { isSavingItem.value = false }
        }
    }
    fun toggleItemAvailable(it: com.example.data.model.CatalogItem) = launchSafely("تعذّر التحديث") {
        if (isStaffAccount.value) { repository.setItemAvailable(it.id, !it.available); toast(if (it.available) "❌ ${it.name} نفد" else "✅ ${it.name} رجع متوفر"); return@launchSafely }
        repository.saveItem(it.copy(available = !it.available, approved = it.approved && catalogDirect), isNew = false, clearNote = false)
    }
    fun deleteCatalogItem(id: String) = launchSafely("تعذّر الحذف") { repository.deleteItem(id); toast("انحذف") }

    fun buyItemPass(scope: String, kind: String, itemIds: List<String>, days: Int) {
        val uid = currentUser.value?.uid ?: return
        val cfg = catalogConfig.value
        val ph = cfg.price(if (scope == "all") "all" else kind).priceH
        val cost = com.example.data.model.CatalogConfig.passCost(ph, if (scope == "all") 1 else itemIds.size, days)
        if (!isAdmin.value && balance() < cost) { toast("رصيدك ما يكفي (تحتاج $cost نقطة)."); return }
        val curAll = myPasses.value.filter { it.scope == "all" }.maxOfOrNull { it.endsAt } ?: 0L
        val start = if (scope == "all") maxOf(System.currentTimeMillis(), curAll) else System.currentTimeMillis()
        val k = com.example.data.model.ItemKind.of(kind)
        launchSafely("تعذّرت العملية. جرّب مرة ثانية.") {
            repository.buyItemPass(uid, balance(), !isAdmin.value, scope, if (scope == "all") "" else kind, if (scope == "all") emptyList() else itemIds, days, cost, start,
                if (scope == "all") "📦 باقة كل المنتجات $days يوم" else "${k.icon} ظهور ${itemIds.size} ${k.label} $days يوم")
            toast("تم ✅ خصمنا $cost نقطة")
        }
    }
    fun setOrderStatus(id: String, status: String, etaMinutes: Int = 0, extra: Map<String, Any> = emptyMap()) = launchSafely("تعذّر التحديث — يمكن المحل استلمه") {
        val o = if (status == "cancelled") (storeOrders.value + myOrders.value).firstOrNull { it.id == id } else null
        repository.setOrderStatus(id, status, etaMinutes, extra, o?.takeIf { it.slotKey.isNotBlank() && it.status != "cancelled" }?.let { it.storeId to it.slotKey })
    }
    /** 🛍️ جاهز للاستلام + ملاحظة للعميل (وين يستلم) */
    fun markReady(o: com.example.data.model.HayynaOrder, note: String) {
        val n = note.trim().take(80)
        if (n.isNotBlank()) readyNotes.value = (listOf(n) + readyNotes.value.filter { it != n }).take(4).also { readyPrefs.edit().putString("notes", it.joinToString("\n")).apply() }
        setOrderStatus(o.id, "ready", extra = if (n.isNotBlank()) mapOf("readyNote" to n) else emptyMap())
    }
    private val readyPrefs = application.getSharedPreferences("lc_ready", android.content.Context.MODE_PRIVATE)
    val readyNotes = MutableStateFlow(readyPrefs.getString("notes", "").orEmpty().split("\n").filter { it.isNotBlank() })
    /** إلغاء من المحل بسبب */
    fun storeCancel(o: com.example.data.model.HayynaOrder, reason: String) = setOrderStatus(o.id, "cancelled", extra = mapOf("cancelBy" to "store", "cancelReason" to reason.take(120)))
    /** رد المحل على طلب الإلغاء */
    fun answerCancel(o: com.example.data.model.HayynaOrder, approve: Boolean) =
        if (approve) setOrderStatus(o.id, "cancelled", extra = mapOf("cancelReqAns" to "approved", "cancelBy" to "customer", "cancelReason" to o.cancelReq.take(120)))
        else setOrderStatus(o.id, o.status, extra = mapOf("cancelReqAns" to "rejected"))
    /** 🚫 ما انستلم */
    fun markNoshow(o: com.example.data.model.HayynaOrder) {
        val uid = currentUser.value?.uid ?: return
        val cfg = catalogConfig.value.noshow
        launchSafely("تعذّر التسجيل — جرّب مرة ثانية") {
            val n = repository.markNoshow(o, uid, cfg)
            custStats.value = custStats.value - o.customerUid
            toast(if (n >= cfg.max) "🚫 سجّلناه — العميل وصل $n طلبات ما استلمها وتوقف عن الطلب ${cfg.blockDays} أيام" else "🚫 سجّلنا إن الطلب ما انستلم")
        }
    }
    /** 🙋 العميل يطلب الإلغاء */
    fun requestCancel(o: com.example.data.model.HayynaOrder, reason: String) = launchSafely("تعذّر الإرسال — يمكن الطلب انتهى") { repository.requestCancel(o.id, reason); toast("انرسل طلب الإلغاء للمحل ⏳") }
    fun updateCurb(o: com.example.data.model.HayynaOrder, car: String, spot: String) = launchSafely("تعذّر الحفظ") { repository.updateCurb(o.id, car, spot); toast("انحفظ 👍") }
    /** حالة العميل نفسه (موقوف عن الطلب؟) */
    val myStats = MutableStateFlow<com.example.data.model.CustStats?>(null)
    fun loadMyStats() { val u = currentUser.value?.uid ?: return; if (!catalogConfig.value.feat("noshow")) return; viewModelScope.launch { myStats.value = repository.custStats(u) } }

    /** طلباتي (الزبون) لحظياً + تنبيه لما تتغيّر حالة طلب */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myOrders: StateFlow<List<com.example.data.model.HayynaOrder>> = combine(currentUser, catalogConfig) { u, c -> if (c.enabled && c.orderEnabled) u?.uid else null }
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else repository.myOrdersFlow(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val myRatingIds = MutableStateFlow<Set<String>>(emptySet())
    init {
        var prev: Map<String, String>? = null
        viewModelScope.launch {
            myOrders.collect { list ->
                val p = prev
                if (p != null) list.forEach { o -> val was = p[o.id]; if (was != null && was != o.status && o.status != "sent") toast("🧾 طلبك #${o.code}: ${o.statusText()}") }
                prev = list.associate { it.id to it.status }
            }
        }
        viewModelScope.launch { currentUser.collect { u -> myRatingIds.value = if (u == null) emptySet() else repository.myRatingIds(u.uid) } }
    }
    fun rateOrder(o: com.example.data.model.HayynaOrder, stars: Int, text: String) {
        val uid = currentUser.value?.uid ?: return
        launchSafely("تعذّر إرسال التقييم") {
            repository.saveRating(o.id, o.storeId, uid, userName.value.ifBlank { o.customerName }.substringBefore(" "), stars, text)
            myRatingIds.value = myRatingIds.value + o.id
            toast("شكراً على تقييمك 🌟")
        }
    }
    suspend fun storeRatings(uid: String) = repository.storeRatings(uid)
    /** 🚗 وصلت: ننبّه المحل + نفتح واتساب برسالة جاهزة */
    fun markArrived(o: com.example.data.model.HayynaOrder, onUrl: (String) -> Unit) {
        launchSafely("تعذّر التبليغ") {
            repository.markArrived(o.id)
            toast("بلّغنا المحل إنك وصلت 🚗")
            val st = approvedStores.value.firstOrNull { it.uid == o.storeId }
            val d = (st?.branches?.firstOrNull()?.phone ?: st?.phone ?: "").filter { it.isDigit() }.let { if (it.startsWith("05") && it.length == 10) "966" + it.drop(1) else it }
            if (d.isNotBlank()) onUrl("https://wa.me/$d?text=" + java.net.URLEncoder.encode("وصلت عند المحل 🚗 ${o.car}\nطلب #${o.code}", "UTF-8").replace("+", "%20"))
        }
    }
    init {
        // تنبيهات المحل: أول ما يفتح = تنبيه واحد بعدد الطلبات اللي تنتظره، وبعدها لكل طلب جديد / العميل وصل / طلب إلغاء (مع صوت)
        var prevStore: Map<String, com.example.data.model.HayynaOrder>? = null
        viewModelScope.launch {
            storeOrdersRaw.collect { raw ->
                if (raw == null) { prevStore = null; return@collect }
                val (list, fromCache) = raw
                loadCustStats(list.filter { it.isLive }.map { it.customerUid })
                val snd = catalogConfig.value.feat("sounds")
                val p = prevStore
                if (p == null) {
                    if (fromCache) return@collect // ننتظر أول دفعة من السيرفر
                    val waiting = list.count { it.status == "sent" }
                    if (waiting > 0) { toast(if (waiting == 1) "🛎️ عندك طلب جديد" else "🛎️ عندك $waiting طلبات جديدة"); if (snd) com.example.data.OrderSounds.play(getApplication(), "new") }
                } else list.forEach { o ->
                    val old = p[o.id]
                    when {
                        old == null -> if (o.status == "sent") { toast(if (o.isScheduled) "📅 طلب مسبق جديد #${o.code} — موعده ${com.example.data.model.PreOrder.whenText(o.scheduledAtMillis)}" else "🛎️ طلب جديد #${o.code} من ${o.customerName.ifBlank { "عميل" }}"); if (snd) com.example.data.OrderSounds.play(getApplication(), "new") }
                        o.arrived && !old.arrived -> { toast("🚗 العميل وصل — طلب #${o.code} ${o.car}${if (o.spot.isNotBlank()) " • " + o.spot else ""}"); if (snd) com.example.data.OrderSounds.play(getApplication(), "arrive") }
                        o.cancelReq.isNotBlank() && old.cancelReq.isBlank() -> { toast("🙋 العميل يطلب إلغاء #${o.code}: ${o.cancelReq}"); if (snd) com.example.data.OrderSounds.play(getApplication(), "cancel") }
                        o.status == "cancelled" && old.status != "cancelled" && o.cancelBy != "store" -> { toast("✕ العميل ألغى طلب #${o.code}"); if (snd) com.example.data.OrderSounds.play(getApplication(), "cancel") }
                    }
                }
                prevStore = list.associateBy { it.id }
            }
        }
    }

    // ----- السلة (لكل متجر) -----
    val carts = MutableStateFlow<Map<String, Map<String, com.example.data.model.CartLine>>>(emptyMap())
    /** عروض كل متجر (خصم / 1+1 / باقات) — تتحسب لما تنفتح صفحة المتجر */
    val storeDeals = MutableStateFlow<Map<String, com.example.data.model.StoreDeals>>(emptyMap())
    fun dealsOf(storeId: String) = storeDeals.value[storeId] ?: com.example.data.model.StoreDeals()
    fun cartAdd(storeId: String, it: com.example.data.model.CatalogItem, delta: Int) {
        val cart = carts.value[storeId].orEmpty().toMutableMap()
        val q = (cart[it.id]?.qty ?: 0) + delta
        val (price, was, _) = dealsOf(storeId).price(it)
        if (q <= 0) cart.remove(it.id) else cart[it.id] = com.example.data.model.CartLine(it.id, it.name, it.kind, price, q.coerceAtMost(99), was = was)
        carts.value = carts.value + (storeId to cart)
    }
    fun cartAddPack(storeId: String, p: com.example.data.model.DealPack, delta: Int) {
        if (!p.available && delta > 0) { toast("❌ هذا العرض نفد اليوم"); return }
        val cart = carts.value[storeId].orEmpty().toMutableMap()
        val q = (cart[p.id]?.qty ?: 0) + delta
        if (q <= 0) cart.remove(p.id) else cart[p.id] = com.example.data.model.CartLine(p.id, p.name, "deal", p.price, q.coerceAtMost(99), was = p.was)
        carts.value = carts.value + (storeId to cart)
    }
    /** نحدّث أسعار السلة حسب المنيو والعروض الحالية (ونشيل اللي ما عاد موجود) */
    private fun syncCart(storeId: String, items: List<com.example.data.model.CatalogItem>) {
        val d = dealsOf(storeId)
        val cart = carts.value[storeId] ?: return
        val out = cart.mapNotNull { (id, l) ->
            d.packs.firstOrNull { it.id == id }?.let { p -> return@mapNotNull if (!p.available) null else id to l.copy(name = p.name, price = p.price, was = p.was) }
            val it = items.firstOrNull { x -> x.id == id && x.available } ?: return@mapNotNull null
            val (price, was, _) = d.price(it); id to l.copy(price = price, was = was)
        }.toMap()
        carts.value = carts.value + (storeId to out)
    }
    /** نص زر «🛒 اطلب» في شاشة العرض (null = العرض ما ينطلب) */
    fun offerOrderLabel(o: RestaurantOffer): String? {
        if (o.isSoldOut()) return null // ❌ نفد اليوم
        val cfg = catalogConfig.value
        val d = com.example.data.model.OfferDeal.from(o.deal) ?: return null
        val t = com.example.data.model.DealType.of(d.type) ?: return null
        if (!cfg.enabled || !cfg.orderEnabled || !cfg.feat(t.feat)) return null
        return when (t) {
            com.example.data.model.DealType.DISCOUNT -> "🛍️ تسوّق بالخصم من المنيو"
            com.example.data.model.DealType.BOGO -> "🛒 اطلب العرض (1+1)"
            else -> if ((t == com.example.data.model.DealType.BUNDLE || d.orderable) && o.price > 0) "🛒 اطلب العرض" else null
        }
    }
    // ⚡ الخصم السريع (التاجر)
    suspend fun myQuickDeal(): com.example.data.model.QuickDeal? = currentUser.value?.uid?.let { repository.quickDeal(it) }
    fun saveQuickDeal(q: com.example.data.model.QuickDeal, done: () -> Unit) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch {
            runCatching { repository.saveQuickDeal(uid, q) }
                .onSuccess { toast("⚡ شغّلنا خصم ${q.pct}% على منيوك"); done() }
                .onFailure { toast("تعذّر الحفظ: ${it.message ?: ""}") }
        }
    }
    fun stopQuickDeal(done: () -> Unit) {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch { runCatching { repository.deleteQuickDeal(uid) }.onSuccess { toast("وقّفنا الخصم ⏹️"); done() }.onFailure { toast("تعذّر الإيقاف") } }
    }
    // ⏸️🌙 هل المحل يستقبل طلبات الحين؟ (إيقاف يدوي أو خارج الدوام)
    val pausedStores = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    // 📅 اطلب مسبقاً
    val preCfgs = MutableStateFlow<Map<String, com.example.data.model.PreorderCfg>>(emptyMap())
    fun preOn(sid: String): Boolean = catalogConfig.value.feat("preorder") && catalogConfig.value.orderEnabled && preCfgs.value[sid]?.active == true
    val slotCounts = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    fun loadSlotCounts(sid: String) { viewModelScope.launch { slotCounts.value = slotCounts.value + (sid to repository.slotCounts(sid)) } }
    /** إعدادات الطلب المسبق لمتجري (صاحب المحل/الموظف) */
    val myPre = MutableStateFlow(com.example.data.model.PreorderCfg())
    fun saveMyPre(c: com.example.data.model.PreorderCfg, onDone: () -> Unit) {
        val uid = currentUser.value?.uid ?: return
        launchSafely("تعذّر الحفظ") {
            repository.savePreorderCfg(uid, c); myPre.value = c; preCfgs.value = preCfgs.value + (uid to c)
            toast(if (c.on) "📅 الطلب المسبق مفعّل ✅" else "انقفل الطلب المسبق"); onDone()
        }
    }
    // ⏰ الطلب المسبق: تنبيه المحل لما يجي وقت تجهيزه
    private val duePrefs = application.getSharedPreferences("lc_due", android.content.Context.MODE_PRIVATE)
    init {
        viewModelScope.launch {
            storeIdFlow { it.canOrders }.collect { sid ->
                if (sid != null && catalogConfig.value.feat("preorder")) myPre.value = repository.preorderCfg(sid) ?: com.example.data.model.PreorderCfg()
            }
        }
        viewModelScope.launch {
            delay(5_000)
            while (true) {
                if (catalogConfig.value.feat("preorder")) {
                    val now = System.currentTimeMillis()
                    val prep = myPre.value.prepMin * 60_000L
                    val seen = duePrefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                    storeOrders.value.filter { it.isScheduled && it.status in listOf("sent", "received") && it.id !in seen }
                        .filter { it.scheduledAtMillis - prep <= now && it.scheduledAtMillis > now - 3_600_000L }
                        .forEach { o ->
                            seen += o.id
                            toast("⏰ حان وقت تجهيز الطلب المسبق #${o.code} — موعده ${com.example.data.model.PreOrder.timeOf(o.scheduledAtMillis)}")
                            if (catalogConfig.value.feat("sounds")) com.example.data.OrderSounds.play(getApplication(), "new")
                        }
                    duePrefs.edit().putStringSet("ids", seen.toList().takeLast(50).toSet()).apply()
                }
                delay(60_000)
            }
        }
    }
    suspend fun refreshStorePause(sid: String): Boolean {
        if (catalogConfig.value.feat("preorder")) repository.preorderCfg(sid).let { preCfgs.value = preCfgs.value + (sid to (it ?: com.example.data.model.PreorderCfg())) }
        val p = repository.storePausedNow(sid)
        pausedStores.value = pausedStores.value + (sid to p)
        return p
    }
    fun refreshStorePauseAsync(sid: String) { viewModelScope.launch { refreshStorePause(sid) } }
    /** branchId = null: المحل مغلق لو كل فروعه مغلقة. الفرع بدون دوام = مفتوح. */
    fun orderBlock(store: com.example.data.model.StoreProfile, branchId: String?, paused: Boolean = pausedStores.value[store.uid] == true): String? {
        if (paused) return "⏸️ المحل موقف استقبال الطلبات مؤقتاً — جرّب بعد شوي"
        val bs = store.branches
        if (bs.isEmpty()) return null
        val many = branchId != null && bs.size > 1
        val pick = if (branchId != null) listOf(bs.firstOrNull { it.id == branchId } ?: bs.first()) else bs
        if (pick.any { OfferTime.isOpenNow(it) != false }) return null
        return "🌙 ${if (many) "هذا الفرع" else "المحل"} مغلق الحين — يفتح ${OfferTime.formatMinute(pick.first().openMinute)}" + if (many) " (جرّب فرع ثاني)" else ""
    }
    /** حالة الإيقاف لمتجري (صاحب المحل أو موظف الطلبات) */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myStorePaused: StateFlow<Boolean> = storeIdFlow { it.canOrders }
        .flatMapLatest { sid -> if (sid == null) flowOf(false) else repository.storePaused(sid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun toggleStorePause() {
        val u = currentUser.value ?: return
        val st = staffMe.value
        val staff = com.example.data.model.StoreStaff.isStaffEmail(u.email)
        val sid = if (staff) st?.storeId ?: return else u.uid
        val to = !myStorePaused.value
        viewModelScope.launch {
            runCatching { repository.setStorePaused(sid, to, if (staff) st?.name?.ifBlank { "موظف ${st.n}" } ?: "موظف" else "صاحب المحل") }
                .onSuccess { toast(if (to) "⏸️ وقّفنا الطلبات — الزبائن يشوفون إن المحل موقف مؤقتاً" else "▶️ رجع استقبال الطلبات") }
                .onFailure { toast("تعذّر التغيير: ${it.message ?: ""}") }
        }
    }
    /** طلب مباشر لعرض (المتجر ما عنده منيو مفتوح) */
    val directCheckout = MutableStateFlow<com.example.data.model.StoreProfile?>(null)
    /** 🛒 اطلب من شاشة العرض: لو المتجر عنده منيو مفتوح نضيفه للسلة ونفتح المتجر، وإلا نطلبه مباشرة */
    fun orderFromOffer(o: RestaurantOffer) {
        if (o.isSoldOut()) { toast("❌ هذا العرض نفد اليوم"); return }
        viewModelScope.launch {
            val st = approvedStores.value.firstOrNull { it.uid == o.merchantId } ?: runCatching { repository.getStore(o.merchantId) }.getOrNull()
            if (st == null) { toast("صفحة المتجر مو جاهزة"); return@launch }
            val paused = refreshStorePause(st.uid)
            orderBlock(st, null, paused)?.let { if (paused || !preOn(st.uid)) { toast(it); return@launch } }
            val items = loadStoreCatalog(st.uid)
            val d = com.example.data.model.OfferDeal.from(o.deal) ?: return@launch
            when (d.type) {
                "discount" -> openStorePage(st)
                "bogo" -> { items.firstOrNull { it.id == d.itemId && it.available }?.let { cartAdd(st.uid, it, 2) }; openStorePage(st) }
                else -> {
                    val pk = dealsOf(st.uid).packs.firstOrNull { it.offerId == o.id }
                    when {
                        pk == null -> openStorePage(st)
                        items.any { it.available } -> { cartAddPack(st.uid, pk, 1); toast("انضاف العرض لسلتك 🛒 كمّل من صفحة المتجر"); openStorePage(st) }
                        else -> { carts.value = carts.value + (st.uid to emptyMap()); cartAddPack(st.uid, pk, 1); activeDetailOffer.value = null; directCheckout.value = st }
                    }
                }
            }
        }
    }
    fun cartLineAdd(storeId: String, id: String, delta: Int) {
        val cart = carts.value[storeId].orEmpty().toMutableMap()
        val l = cart[id] ?: return
        val q = l.qty + delta
        if (q <= 0) cart.remove(id) else cart[id] = l.copy(qty = q.coerceAtMost(99))
        carts.value = carts.value + (storeId to cart)
    }
    suspend fun loadStoreCatalog(uid: String): List<com.example.data.model.CatalogItem> {
        val cfg = catalogConfig.value
        if (!cfg.enabled) return emptyList()
        val (items, passes) = runCatching { repository.storeCatalog(uid) }.getOrDefault(emptyList<com.example.data.model.CatalogItem>() to emptyList())
        val now = System.currentTimeMillis()
        val visible = items.filter { cfg.visible(it, passes, now) }
        val quick = if (cfg.feat("quickDeal")) repository.quickDeal(uid) else null
        val offers = (approvedOffers.value + merchantOffers.value).distinctBy { it.id }.filter { it.merchantId == uid }
        storeDeals.value = storeDeals.value + (uid to com.example.data.model.StoreDeals.build(cfg, offers, quick, visible, now))
        syncCart(uid, visible)
        return visible
    }

    /** يحفظ الطلب (لو مسجّل) ويرجّع رابط واتساب المحل مع ملخص الطلب */
    fun sendOrder(store: com.example.data.model.StoreProfile, branch: com.example.data.model.Branch?, mode: String, name: String, phone: String, note: String, pay: String, car: String, scheduledAt: Long = 0L, onUrl: (String) -> Unit) {
        val later = scheduledAt > 0
        val slotKey = if (later && mode == "delivery") com.example.data.model.PreOrder.slotKey(scheduledAt) else ""
        if (slotKey.isNotBlank() && currentUser.value == null) { toast("سجّل دخولك عشان تحجز موعد توصيل"); return }
        val lines = carts.value[store.uid].orEmpty().values.toList()
        if (lines.isEmpty()) return
        val cfg = catalogConfig.value
        val tot = dealsOf(store.uid).totals(lines)
        val sub = tot.sub
        val fee = if (mode == "delivery") (if (store.deliveryFee <= 0 || (store.deliveryFreeOver > 0 && sub >= store.deliveryFreeOver)) 0.0 else store.deliveryFee) else 0.0
        val total = Math.round((sub + fee) * 100) / 100.0
        val vatNo = if (cfg.vatEnabled && com.example.data.model.CatalogConfig.validVat(store.vatNumber)) store.vatNumber else ""
        val vatAmt = if (vatNo.isNotBlank()) Math.round(total * cfg.vatRate / (100 + cfg.vatRate) * 100) / 100.0 else 0.0
        val loc = userLocation.value
        val o = com.example.data.model.HayynaOrder(
            code = com.example.data.model.HayynaOrder.code(), storeId = store.uid, storeName = store.name,
            branchName = if (store.branches.size > 1) (branch?.name?.ifBlank { branch.district } ?: "") else "",
            customerName = name.trim().take(60), customerPhone = phone.trim(), items = tot.lines, subtotal = sub, discount = tot.saved, deliveryFee = fee, total = total,
            vatRate = if (vatNo.isNotBlank()) cfg.vatRate else 0.0, vatAmount = vatAmt, vatNumber = vatNo, mode = mode, pay = pay, car = if (mode == "curbside") car.trim().take(60) else "",
            lat = if (mode == "delivery") loc?.latitude else null, lng = if (mode == "delivery") loc?.longitude else null, note = note.trim().take(300),
            scheduledAtMillis = scheduledAt, slotKey = slotKey
        )
        val digits = (branch?.phone ?: store.phone).filter { it.isDigit() }.let { d ->
            when { d.startsWith("05") && d.length == 10 -> "966" + d.drop(1); d.startsWith("5") && d.length == 9 -> "966$d"; else -> d }
        }
        if (digits.isBlank()) { toast("المحل ما عنده رقم واتساب."); return }
        val url = "https://wa.me/$digits?text=" + java.net.URLEncoder.encode(o.text(), "UTF-8").replace("+", "%20")
        viewModelScope.launch {
            // ⏸️ نتأكد من السيرفر إن المحل ما أوقف الطلبات، وإن الفرع مفتوح
            val paused = refreshStorePause(store.uid)
            orderBlock(store, branch?.id, paused)?.let { if (paused || !later || !preOn(store.uid)) { toast(it); return@launch } }
            val pc = preCfgs.value[store.uid] ?: com.example.data.model.PreorderCfg()
            if (later && !preOn(store.uid)) { toast("المحل قفل الطلب المسبق"); return@launch }
            if (later && scheduledAt < System.currentTimeMillis() + pc.prepMin * 60_000L) { toast("الوقت اللي اخترته فات — اختر وقت ثاني"); return@launch }
            // 📅 فترة التوصيل: نتأكد من السيرفر إنها ما امتلت
            var slotN = 0
            if (slotKey.isNotBlank()) {
                slotN = repository.slotCountNow(store.uid, slotKey)
                if (slotN >= pc.slotCap) { loadSlotCounts(store.uid); toast("😕 هالفترة امتلت الحين — اختر وقت ثاني"); return@launch }
            }
            // ❌ نتأكد من السيرفر إن العروض اللي في السلة ما نفدت (يمكن الموظف علّمها الحين)
            val sold = lines.filter { it.id.startsWith("deal_") }.filter { repository.offerSoldOutNow(it.id.removePrefix("deal_")) }
            if (sold.isNotEmpty()) {
                carts.value = carts.value + (store.uid to carts.value[store.uid].orEmpty().filterKeys { k -> sold.none { it.id == k } })
                toast("❌ «${sold.first().name}» نفد اليوم — شلناه من سلتك")
                return@launch
            }
            val saveErr = currentUser.value?.uid?.let { uid -> runCatching { repository.saveOrder(o, uid, branch?.id.orEmpty(), slotN) }.onFailure { Log.w(TAG, "order: ${it.message}") }.exceptionOrNull() }
            if (saveErr != null && slotKey.isNotBlank()) { loadSlotCounts(store.uid); toast("😕 ما قدرنا نحجز هالفترة (يمكن امتلت) — اختر وقت ثاني"); return@launch }
            if (saveErr != null && refreshStorePause(store.uid)) { toast(orderBlock(store, null, true) ?: ""); return@launch }
            carts.value = carts.value - store.uid
            toast(if (later) "📅 انحجز طلبك #${o.code} — ${com.example.data.model.PreOrder.whenText(scheduledAt)} ✅ كمّل في واتساب" else "انرسل طلبك #${o.code} ✅ كمّل في واتساب")
            onUrl(url)
        }
    }
    suspend fun loadMyOrders(): List<com.example.data.model.HayynaOrder> =
        currentUser.value?.uid?.let { uid ->
            runCatching { repository.myOrders(uid) }.onFailure { toast("تعذّر تحميل طلباتك: ${it.message ?: ""}") }.getOrDefault(emptyList())
        } ?: emptyList()

    /**
     * العروض الظاهرة: لما يكون رصيد العروض مفعّل، العرض الجديد لازم له "نشر" مدفوع،
     * والتمييز المدفوع يخلي العرض مميّز (حلقة صفراء وأول الترتيب).
     */
    private val approvedOffers: StateFlow<List<RestaurantOffer>> =
        combine(repository.approvedOffers, userLocation, creditsConfig, repository.activeBoosts, nowMillis) { offers, loc, cr, boosts, now ->
            val byOffer = boosts.groupBy { it.offerId }
            val visible = offers.mapNotNull { o ->
                if (o.draft) return@mapNotNull null // المسودات ما تظهر للناس
                val bs = byOffer[o.id].orEmpty()
                val created = o.createdAt?.toDate()?.time ?: 0L
                val inGrace = cr.enabledAtMillis > 0L && created < cr.enabledAtMillis && now < cr.graceUntilMillis
                val paidUp = !cr.enabled || cr.enabledAtMillis == 0L || inGrace || o.source == "ad" ||
                    bs.any { it.effect == "publish" && it.endsAt > now }
                if (!paidUp) return@mapNotNull null
                val active = bs.filter { it.startsAt <= now && now < it.endsAt }
                val spot = active.any { it.effect == "spotlight" }
                val featured = spot || active.any { it.effect == "feature" }
                if ((featured && !o.isFeatured) || spot) o.copy(isFeatured = o.isFeatured || featured, spotlight = spot) else o
            }
            withDistance(visible, loc)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- الفلاتر ----------------

    val searchQuery = MutableStateFlow("")
    val selectedRegion = MutableStateFlow("الكل") // الافتراضي: كل العروض، والترتيب حسب الأقرب لموقعك
    val selectedDistrict = MutableStateFlow("الكل")
    val selectedCuisine = MutableStateFlow("الكل")
    val selectedMediaType = MutableStateFlow("الكل")
    val customerSortBy = MutableStateFlow("NEAREST") // NEAREST, RATING, PRICE_LOW, DISCOUNT
    val openNowOnly = MutableStateFlow(false)

    // ---------------- تصفّح حي آخر ----------------
    data class BrowsePlace(val name: String, val latitude: Double, val longitude: Double, val subtitle: String = "")

    /** الحي/المكان اللي يتصفحه المستخدم (null = حول موقعه). */
    val browsePlace = MutableStateFlow<BrowsePlace?>(null)
    val placeSuggestions = MutableStateFlow<List<BrowsePlace>>(emptyList())
    private var placeSearchJob: kotlinx.coroutines.Job? = null

    data class DistrictCount(val name: String, val count: Int, val latitude: Double, val longitude: Double)
    val favoritesOnly = MutableStateFlow(false)

    // ---------------- المفضلة ----------------
    private val favoritesStore = FavoritesStore(application)
    val favoriteIds: StateFlow<Set<String>> = favoritesStore.ids

    fun toggleFavorite(offerId: String) = favoritesStore.toggle(offerId)

    init {
        favoritesStore.onChanged = { ids ->
            currentUser.value?.uid?.let { uid -> launchSafely { repository.saveFavorites(uid, ids) } }
        }
    }
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }

    // ---------------- ملف المطعم والتعديل ----------------
    val merchantProfile = MutableStateFlow<MerchantProfile?>(null)
    val editingOffer = MutableStateFlow<RestaurantOffer?>(null)

    // ---------------- البلاغات (للأدمن) ----------------
    val reports: StateFlow<List<OfferReport>> = repository.reports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isUploadingMedia = MutableStateFlow(false)
    val isBranchesPageOpen = MutableStateFlow(false)
    val isSavingBranches = MutableStateFlow(false)

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
    private val favoritesAndBrowse = combine(favoritesOnly, favoriteIds, browsePlace) { a, b, c -> Triple(a, b, c) }

    private val baseCustomerOffers: StateFlow<List<RestaurantOffer>> = combine(
        approvedOffers, filterParams, nowMillis, favoritesAndBrowse
    ) { offers, baseParams, now, (favOnly, favIds, browse) ->
        val params = baseParams.copy(favoritesOnly = favOnly)
        val filtered = offers.filter { offer ->
            if (OfferTime.isExpired(offer, now)) return@filter false
            if (offer.reportsCount >= AUTO_HIDE_REPORTS) return@filter false
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
            val q = searchNorm(params.query)
            val matchesQuery = q.isBlank() || (
                listOf(offer.restaurantName, offer.offerTitle, offer.description, offer.district, offer.cuisineType, offer.itemsScope) +
                    offer.tags + offer.effectiveBranches.map { it.landmark }
                ).any { searchNorm(it).contains(q) }
            val matchesOpenNow = !params.openNowOnly || OfferTime.isOpenNow(offer) == true

            matchesRegion && matchesDistrict && matchesCuisine && matchesMedia && matchesQuery && matchesOpenNow
        }

        // العروض المميزة (المدفوعة) دائماً في الأعلى، ثم حسب الترتيب المختار
        val sorted = when (params.sortBy) {
            "PRICE_LOW" -> filtered.sortedBy { if (it.price > 0) it.price else Double.MAX_VALUE }
            "DISCOUNT" -> filtered.sortedByDescending { com.example.data.model.OfferPricing.effectivePercent(it, now) }
            else -> if (browse != null) {
                // يتصفح حي ثاني: الترتيب حسب القرب من ذاك الحي (المسافة المعروضة تظل من موقعه هو)
                filtered.sortedBy { o ->
                    o.effectiveBranches.minOf {
                        UserLocationManager.calculateDistanceKm(browse.latitude, browse.longitude, it.latitude, it.longitude)
                    }
                }
            } else filtered.sortedBy { it.distanceKm }
        }
        sorted.sortedByDescending { it.isFeatured }.sortedByDescending { it.spotlight }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- نطاق العروض: حيّك / حولك 5 كم / كل الأحياء ----------------
    /** نطاق الخريطة: يتنقل بالضغط على شارة "أنت في". */
    val scope = MutableStateFlow(SCOPE_ALL)
    /** اسم حي المستخدم الحالي (من الخريطة). */
    val userDistrict = MutableStateFlow("")
    fun setUserDistrict(name: String) { userDistrict.value = name }

    private fun inScope(offer: RestaurantOffer, sc: String, loc: Location?, district: String): Boolean = when (sc) {
        SCOPE_DISTRICT -> district.isNotBlank() && offer.effectiveBranches.any { normalizeDistrict(it.district) == normalizeDistrict(district) }
        SCOPE_NEAR -> loc != null && offer.effectiveBranches.any {
            UserLocationManager.calculateDistanceKm(loc.latitude, loc.longitude, it.latitude, it.longitude) <= NEAR_KM
        }
        else -> true
    }

    val filteredCustomerOffers: StateFlow<List<RestaurantOffer>> = combine(
        baseCustomerOffers, scope, userLocation, userDistrict
    ) { offers, sc, loc, district ->
        if (sc == SCOPE_ALL) offers else offers.filter { inScope(it, sc, loc, district) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** الضغط على الشارة: حيّك ← حولك 5 كم ← كل الأحياء. لو المستوى فاضي نوسّع تلقائياً. */
    fun cycleScope() {
        val loc = userLocation.value
        if (loc == null) {
            toast("فعّل الموقع عشان نعرض عروض حيّك 📍")
            return
        }
        browsePlace.value = null
        selectedDistrict.value = "الكل"
        val base = baseCustomerOffers.value
        val district = userDistrict.value
        fun count(sc: String) = base.count { inScope(it, sc, loc, district) }
        scope.value = when (scope.value) {
            SCOPE_ALL -> when {
                district.isNotBlank() && count(SCOPE_DISTRICT) > 0 -> SCOPE_DISTRICT
                count(SCOPE_NEAR) > 0 -> {
                    toast(if (district.isBlank()) "عروض حولك (5 كم) 🎯" else "ما فيه عروض في حيّك للحين، شوف اللي حولك 🎯")
                    SCOPE_NEAR
                }
                else -> {
                    toast("ما فيه عروض قريبة منك للحين، نعرض لك كل الأحياء")
                    SCOPE_ALL
                }
            }
            SCOPE_DISTRICT -> if (count(SCOPE_NEAR) > 0) SCOPE_NEAR else SCOPE_ALL
            else -> SCOPE_ALL
        }
        selectedOfferOnMap.value = null
    }

    /** الأحياء اللي فيها عروض فعلاً، مع عددها (للفلاتر). */
    val districtCounts: StateFlow<List<DistrictCount>> = combine(approvedOffers, nowMillis) { offers, now ->
        offers.filter { !OfferTime.isExpired(it, now) }
            .flatMap { o -> o.effectiveBranches.map { b -> b } }
            .filter { it.district.isNotBlank() }
            .groupBy { normalizeDistrict(it.district) }
            .map { (_, list) ->
                val first = list.first()
                DistrictCount(first.district, list.size, first.latitude, first.longitude)
            }
            .sortedByDescending { it.count }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** نوع الحساب: "customer" أو "merchant" (null = غير مسجّل أو لسا ما انقرأ). */
    val userRole = MutableStateFlow<String?>(null)
    val isRoleLoading = MutableStateFlow(false)
    private var pendingRole: String? = null

    /** اسم المستخدم ورقم جواله (يكتبها أول مرة يدخل). */
    val userName = MutableStateFlow("")
    val userPhone = MutableStateFlow("")

    init {
        auth?.addAuthStateListener(authListener)
        viewModelScope.launch {
            currentUser.collect { user ->
                merchantProfile.value = null
                userRole.value = null
                userName.value = ""
                userPhone.value = ""
                if (user != null && com.example.data.model.StoreStaff.isStaffEmail(user.email)) {
                    userRole.value = "staff" // موظف: ما نكتب له ملف عميل
                } else if (user != null) {
                    resolveRole(user.uid)
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


    private suspend fun resolveRole(uid: String) {
        isRoleLoading.value = true
        try {
            val profile = runCatching { repository.getUserProfile(uid) }.getOrNull()
            val saved = profile?.role
            userName.value = profile?.name.orEmpty()
            userPhone.value = profile?.phone.orEmpty()
            val role = when {
                // اختار نوع الحساب من الزر وهو حساب جديد
                saved == null && pendingRole != null -> pendingRole!!
                // كان من أهل الحي واختار "صاحب محل" وهو يسجّل دخول: يتحوّل تاجر
                saved == ROLE_CUSTOMER && pendingRole == ROLE_MERCHANT -> ROLE_MERCHANT
                saved != null -> saved
                // حسابات قديمة: عنده ملف متجر = تاجر
                pendingRole == null && runCatching { repository.getMerchantProfile(uid) }.getOrNull() != null -> ROLE_MERCHANT
                else -> pendingRole ?: ROLE_CUSTOMER
            }
            userRole.value = role
            if (saved != role) runCatching { repository.setUserRole(uid, role) }
            // المفضلة: ندمج اللي على الجوال مع اللي في الحساب
            runCatching {
                val remote = repository.getFavorites(uid)
                val merged = remote + favoritesStore.ids.value
                favoritesStore.setAll(merged)
                if (merged != remote) repository.saveFavorites(uid, merged)
            }
        } finally {
            pendingRole = null
            isRoleLoading.value = false
            if (pendingClaim.value != null && (userRole.value == ROLE_MERCHANT || isAdmin.value)) {
                isBranchesPageOpen.value = true
            }
        }
    }

    /** يحفظ الاسم والجوال ونوع الحساب (أول دخول أو تعديل). */
    fun saveProfile(name: String, phone: String, role: String) {
        val user = currentUser.value ?: return
        launchSafely("تعذّر حفظ بياناتك. حاول مرة ثانية.") {
            repository.saveUserProfile(user.uid, role, name.trim(), phone)
            userName.value = name.trim()
            userPhone.value = phone
            val becameMerchant = role == ROLE_MERCHANT && userRole.value != ROLE_MERCHANT
            userRole.value = role
            toast(if (becameMerchant) "أهلاً بك 🏪 ابدأ بتجهيز صفحة متجرك" else "انحفظت بياناتك ✅")
        }
    }

    /** يحوّل حساب العميل لحساب تاجر. */
    fun becomeMerchant() {
        val user = currentUser.value ?: return
        launchSafely("تعذّر تحويل الحساب.") {
            repository.setUserRole(user.uid, ROLE_MERCHANT)
            userRole.value = ROLE_MERCHANT
            toast("أهلاً بك كتاجر في لكوفر 🏪")
        }
    }

    val isMerchantAccount: Boolean get() = userRole.value == ROLE_MERCHANT || isAdmin.value

    /** تسجيل دخول كعميل (asMerchant=false) أو كتاجر (asMerchant=true). */
    fun signInWithGoogle(activity: Activity, asMerchant: Boolean = true) {
        if (isSigningIn.value) return
        pendingRole = if (asMerchant) ROLE_MERCHANT else ROLE_CUSTOMER
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
        // المفضلة محفوظة في الحساب، نفضّي نسخة الجوال عشان ما تختلط مع حساب ثاني
        favoritesStore.setAll(emptySet())
        favoritesOnly.value = false
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
    // ---------------- شعار لكوفر: تحديث + عروض جديدة ----------------
    /** العروض اللي نزلت بعد هذا الوقت تُحسب «جديدة» (نقطة ذهبية على الشعار). */
    val seenAt = MutableStateFlow(System.currentTimeMillis())
    val isRefreshing = MutableStateFlow(false)
    /** قائمة الفريق: اختيار محل من Google لرفع عرض من إعلان. */
    val adPickerOpen = MutableStateFlow(false)

    fun refreshOffers(newCount: Int) {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            repository.reconnect()
            requestLocationUpdates()
            delay(900)
            seenAt.value = System.currentTimeMillis()
            isRefreshing.value = false
            toast(if (newCount > 0) "🆕 ${if (newCount == 1) "عرض جديد" else "$newCount عروض جديدة"} حولك" else "✅ العروض محدّثة")
        }
    }

    fun pickShopForAd(f: com.example.data.PlacesLookup.Found) {
        googleShops.value = null
        adPickerOpen.value = false
        openAdUpload(com.google.android.gms.maps.model.PointOfInterest(
            com.google.android.gms.maps.model.LatLng(f.lat, f.lng), f.placeId, f.name))
    }

    // ---------------- 🔎 البحث عن محل في Google (مسجّل أو لا) ----------------
    val googleShops = MutableStateFlow<List<com.example.data.PlacesLookup.Found>?>(null)
    val searchingShops = MutableStateFlow(false)

    fun searchGoogleShops(query: String) {
        val q = query.trim(); if (q.length < 2 || searchingShops.value) return
        viewModelScope.launch {
            searchingShops.value = true
            val near = browsePlace.value?.let { it.latitude to it.longitude }
                ?: userLocation.value?.let { it.latitude to it.longitude }
            googleShops.value = com.example.data.PlacesLookup.searchText(getApplication(), q, near?.first, near?.second)
            searchingShops.value = false
        }
    }

    /** يفتح بطاقة المحل (ادعه / أنا صاحب المحل / 📷 ارفع عرض) ويودّي الخريطة له. */
    fun openGoogleShop(f: com.example.data.PlacesLookup.Found) {
        googleShops.value = null
        browseTo(BrowsePlace(f.name, f.lat, f.lng, f.address))
        currentRole.value = AppRole.CUSTOMER_MAP
        selectedPoi.value = com.google.android.gms.maps.model.PointOfInterest(
            com.google.android.gms.maps.model.LatLng(f.lat, f.lng), f.placeId, f.name)
        launchSafely { repository.logPoi(f.placeId, "taps") }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        googleShops.value = null
        placeSearchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            placeSuggestions.value = emptyList()
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(450)
            val key = normalizeDistrict(q)
            // أولاً: أحياء فيها عروض فعلاً
            val local = districtCounts.value
                .filter { normalizeDistrict(it.name).contains(key) }
                .take(3)
                .map { BrowsePlace(it.name, it.latitude, it.longitude, "${it.count} عرض") }
            // ثانياً: أي حي أو مدينة من خرائط Google
            val remote = runCatching { com.example.data.ReverseGeocoder.search(getApplication(), q) }
                .getOrDefault(emptyList())
                .map { BrowsePlace(it.name, it.latitude, it.longitude, it.subtitle) }
            placeSuggestions.value = (local + remote)
                .distinctBy { normalizeDistrict(it.name) }
                .take(6)
        }
    }

    /** ينقل الخريطة لحي/مكان ويرتّب العروض حسب القرب منه. */
    fun browseTo(place: BrowsePlace, filterDistrict: Boolean = false) {
        scope.value = SCOPE_ALL
        browsePlace.value = place
        placeSearchJob?.cancel()
        placeSuggestions.value = emptyList()
        searchQuery.value = ""
        selectedRegion.value = "الكل"
        selectedDistrict.value = if (filterDistrict) place.name else "الكل"
        selectedOfferOnMap.value = null
    }

    /** يرجع للتصفّح حول موقع المستخدم. */
    fun clearBrowse() {
        browsePlace.value = null
        selectedDistrict.value = "الكل"
    }
    fun onRegionSelect(region: String) {
        selectedRegion.value = region
        selectedDistrict.value = "الكل"
    }
    fun onDistrictSelect(district: String) { selectedDistrict.value = district }
    fun onCuisineSelect(cuisine: String) { selectedCuisine.value = cuisine }
    fun onMediaTypeSelect(mediaFilter: String) { selectedMediaType.value = mediaFilter }
    fun toggleOpenNow() { openNowOnly.value = !openNowOnly.value }

    fun selectOfferOnMap(offer: RestaurantOffer?) {
        selectedOfferOnMap.value = offer?.let { withDistanceTo(it, userLocation.value) }
        if (offer != null) launchSafely { repository.incrementViews(offer.id) }
    }

    fun deselectOfferOnMap() { selectedOfferOnMap.value = null }

    fun openOfferDetail(offer: RestaurantOffer) {
        activeDetailOffer.value = withDistanceTo(offer, userLocation.value)
        launchSafely { repository.incrementViews(offer.id) }
    }

    fun closeOfferDetail() { activeDetailOffer.value = null }

    fun openAddOfferSheet() {
        if (currentUser.value == null) {
            toast("سجّل الدخول بحساب Google أولاً لإضافة عرض")
            return
        }
        if (!isMerchantAccount) {
            toast("سجّل متجرك أولاً من صفحة حسابي")
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

    fun saveOffer(offer: RestaurantOffer, asDraft: Boolean = false) {
        val user = currentUser.value
        if (user == null) {
            toast("سجّل الدخول بحساب Google أولاً لإضافة عرض")
            return
        }
        if (myStatus.value?.suspended == true) {
            toast("حسابك موقوف حالياً. تواصل مع إدارة لكوفر.")
            return
        }
        // نشر جديد بالرصيد: لو ما يكفي ما نرفض — نعرض الحصري (لو يغطيه) أو نحفظه مسودة
        val paid = offer.id.isBlank() && creditsConfig.value.enabled
        val pubDays = if (paid) publishDaysOf(offer) else 0
        if (paid && !isAdmin.value && !asDraft) {
            val full = priceFor("publish", pubDays, false)
            val ex = priceFor("publish", pubDays, true)
            val mine = if (offer.exclusive) ex else full
            if (full == null || mine == null) { toast("خدمة النشر موقفة حالياً. تواصل معنا."); return }
            if (balance() < mine.cost) {
                creditPrompt.value = CreditPrompt(
                    kind = CreditPrompt.SHORT, offer = offer, days = pubDays, fullCost = full.cost, exclCost = ex?.cost ?: full.cost,
                    myCost = mine.cost, balance = balance(),
                    exclOk = !offer.exclusive && creditsConfig.value.rewardOn && ex != null && balance() >= ex.cost
                )
                return
            }
        }
        val direct = canPublishDirectly
        if (!direct && offer.id.isBlank()) {
            val dayAgo = System.currentTimeMillis() - 24 * 3_600_000L
            val today = merchantOffers.value.count { (it.createdAt?.toDate()?.time ?: 0L) > dayAgo }
            if (today >= NEW_MERCHANT_DAILY_LIMIT) {
                toast("وصلت للحد اليومي ($NEW_MERCHANT_DAILY_LIMIT عروض) للحسابات الجديدة. جرّب بكرة 🙏")
                return
            }
        }
        viewModelScope.launch {
            isUploadingMedia.value = true
            try {
                val isVideo = offer.mediaType == MediaType.VIDEO
                val sources = (if (isVideo) listOf(offer.mediaUri) else offer.mediaUris.ifEmpty { listOf(offer.mediaUri) })
                    .filter { it.isNotBlank() }
                    .take(if (isVideo) 1 else 3)
                val uploaded = sources.map { MediaUploader.uploadOfferMedia(getApplication(), it, isVideo) }
                val cloudUrl = uploaded.firstOrNull() ?: throw MediaUploader.UploadException("يرجى إرفاق صورة أو فيديو للعرض.")
                val isUpdate = offer.id.isNotBlank()
                val fresh = offer.copy(
                    merchantId = user.uid, mediaUri = cloudUrl, mediaUris = uploaded, isFeatured = false, isApproved = direct,
                    reportsCount = 0, viewsCount = 0, directionsCount = 0, whatsappCount = 0
                )
                if (!isUpdate && asDraft) {
                    repository.insertOffer(fresh.copy(draft = true))
                } else if (!isUpdate && paid) {
                    // العرض + خصم النشر في عملية وحدة
                    val pr = priceFor("publish", pubDays, fresh.exclusive) ?: throw IllegalStateException("خدمة النشر موقفة")
                    val start = fresh.startsAt?.toDate()?.time ?: System.currentTimeMillis()
                    repository.spendOnOffer(
                        uid = user.uid, balance = balance(), charge = !isAdmin.value, offerId = "", item = pr.item, days = pr.days,
                        cost = if (isAdmin.value) pr.full else pr.cost, disc = if (isAdmin.value) 0 else pr.disc, startMillis = start,
                        note = spendNote("📢 نشر: ${fresh.offerTitle}", pr.disc), offer = fresh
                    )
                } else if (isUpdate) {
                    // تعديل التاجر غير الموثوق يرجّع العرض للمراجعة
                    repository.updateOffer(
                        offer.copy(mediaUri = cloudUrl, mediaUris = uploaded, isApproved = if (direct && offer.adminNote.isBlank()) offer.isApproved else false, adminNote = "") // التعديل يمسح ملاحظة الأدمن
                    )
                } else {
                    repository.insertOffer(fresh)
                }
                // نحفظ بيانات المطعم عشان تتعبّى تلقائياً في العرض الجاي
                val old = merchantProfile.value
                val profile = (old ?: MerchantProfile()).copy(
                    restaurantName = offer.restaurantName,
                    phone = offer.phone,
                    latitude = offer.latitude,
                    longitude = offer.longitude,
                    region = offer.region,
                    district = offer.district,
                    cuisineType = offer.cuisineType,
                    openMinute = offer.openMinute,
                    closeMinute = offer.closeMinute,
                    delivery = offer.delivery,
                    deliveryFee = offer.deliveryFee,
                    deliveryMinOrder = offer.deliveryMinOrder,
                    deliveryFreeOver = offer.deliveryFreeOver,
                    // أول عرض بدون فروع: موقعه يصير "الفرع الرئيسي"
                    branches = old?.branches?.ifEmpty { null } ?: offer.branches
                )
                merchantProfile.value = profile
                runCatching { repository.saveMerchantProfile(user.uid, profile) }
                closeAddOfferSheet()
                if (!isUpdate && asDraft) {
                    creditPrompt.value = CreditPrompt(kind = CreditPrompt.SAVED, offer = fresh, balance = balance())
                    return@launch
                }
                toast(
                    when {
                        !direct -> "وصل عرضك للمراجعة ⏳ بيظهر على الخريطة بعد الموافقة"
                        isUpdate -> "تم حفظ التعديلات ✅"
                        else -> "تم نشر العرض ✅"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saveOffer failed: ${e.message}", e)
                val msg = when {
                    e is MediaUploader.UploadException -> e.message
                    e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "ما انحفظ العرض: رفض الصلاحيات. تأكد إن حسابك مو موقوف وإن التطبيق آخر نسخة، أو تواصل معنا."
                    else -> "تعذّر نشر العرض: ${e.message ?: "خطأ غير معروف"}"
                }
                toast(msg ?: "تعذّر نشر العرض.")
            } finally {
                isUploadingMedia.value = false
            }
        }
    }

    // ---------------- الفروع والشعار ----------------

    fun openBranchesPage() {
        if (currentUser.value == null) {
            toast("سجّل الدخول بحساب Google أولاً")
            return
        }
        isBranchesPageOpen.value = true
    }

    fun closeBranchesPage() {
        isBranchesPageOpen.value = false
        pendingClaim.value = null
    }

    /** يحفظ الفروع والشعار، ويحدّثها في كل عروض التاجر. */
    fun saveBranches(profile: MerchantProfile, edits: com.example.ui.components.StoreMediaEdits) {
        val newLogoLocalUri = edits.newLogo
        val user = currentUser.value ?: return
        if (isSavingBranches.value) return
        viewModelScope.launch {
            isSavingBranches.value = true
            try {
                val logo = if (newLogoLocalUri != null) {
                    MediaUploader.uploadOfferMedia(getApplication(), newLogoLocalUri, false)
                } else profile.logoUrl
                val cover = if (edits.newCover != null) {
                    MediaUploader.uploadOfferMedia(getApplication(), edits.newCover, false)
                } else profile.coverUrl
                // صور المنيو: الجديدة (محلية) تنرفع، والمرفوعة سابقاً تبقى كما هي
                val menu = profile.menuImages.map { MediaUploader.uploadOfferMedia(getApplication(), it, false) }
                val primary = profile.branches.firstOrNull()
                val updated = profile.copy(
                    logoUrl = logo,
                    coverUrl = cover,
                    menuImages = menu,
                    phone = primary?.phone ?: profile.phone,
                    latitude = primary?.latitude ?: profile.latitude,
                    longitude = primary?.longitude ?: profile.longitude,
                    region = primary?.region ?: profile.region,
                    district = primary?.district ?: profile.district,
                    openMinute = primary?.openMinute ?: profile.openMinute,
                    closeMinute = primary?.closeMinute ?: profile.closeMinute
                )
                repository.saveMerchantProfile(user.uid, updated)
                merchantProfile.value = updated
                val direct = canPublishDirectly
                repository.saveStore(com.example.data.model.StoreProfile.from(user.uid, updated, approved = direct))
                runCatching { repository.syncMerchantOffers(user.uid, updated, keepApproval = direct) }
                isBranchesPageOpen.value = false
                val wasClaim = pendingClaim.value != null
                pendingClaim.value = null
                // جاي من "أنا صاحب المحل": نكمّل على طول بخطوة إثبات الملكية (السجل / العمل الحر)
                if (wasClaim && verifyConfig.value.enabled && myStatus.value?.verified != true) {
                    isVerifyOpen.value = true
                    toast("تم حفظ متجرك ✅ آخر خطوة: أثبت إنك صاحب المحل")
                    return@launch
                }
                toast(if (direct) "تم حفظ صفحة متجرك ✅" else "تم الحفظ ✅ صفحة متجرك بتظهر للعملاء بعد المراجعة")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saveBranches failed: ${e.message}", e)
                val msg = when {
                    e is MediaUploader.UploadException -> e.message
                    e is com.google.firebase.firestore.FirebaseFirestoreException &&
                        e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "رفض Firestore الحفظ (صلاحيات). تأكد من نشر قواعد Firestore الجديدة."
                    else -> null
                }
                toast(msg ?: "تعذّر الحفظ: ${e.message ?: "خطأ غير معروف"}")
            } finally {
                isSavingBranches.value = false
            }
        }
    }

    private val reportedPrefs = application.getSharedPreferences("hayyna_reported", android.content.Context.MODE_PRIVATE)

    fun reportOffer(offer: RestaurantOffer, reason: String) {
        launchSafely("تعذّر إرسال البلاغ. حاول مرة ثانية.") {
            repository.reportOffer(offer, reason, currentUser.value?.uid)
            // كل جهاز يحسب بلاغ واحد فقط لكل عرض
            if (!reportedPrefs.getBoolean(offer.id, false)) {
                reportedPrefs.edit().putBoolean(offer.id, true).apply()
                runCatching { repository.incrementCounter(offer.id, "reportsCount") }
            }
            toast("وصل بلاغك، شكراً لك 🙏")
        }
    }

    // ---------------- أدوات الأدمن: المراجعة والتوثيق والإيقاف ----------------

    /** يعتمد العرض، وبعد عرضين معتمدين يصير التاجر موثوق تلقائياً. */
    // ---------------- سجل عمليات الإدارة ----------------
    private fun offerThumb(o: RestaurantOffer?): String = o?.mediaUris?.firstOrNull() ?: o?.mediaUri.orEmpty()
    private fun audit(action: String, area: String, type: String = "", id: String = "", name: String = "", details: String = "", thumb: String = "") {
        if (!isAdmin.value) return
        val u = currentUser.value ?: return
        viewModelScope.launch {
            runCatching { repository.logAdmin(u.uid, u.email.orEmpty().lowercase(), action, area, type, id, name, details, thumb) }
                .onFailure { Log.w(TAG, "audit: ${it.message}") }
        }
    }
    private fun auditOffer(action: String, o: RestaurantOffer?, id: String, details: String = "") =
        audit(action, "review", "offer", id, o?.let { "${it.offerTitle} — ${it.restaurantName}" } ?: id, details, offerThumb(o))
    private fun offerById(id: String) = allOffers.value.firstOrNull { it.id == id }

    fun approveOffer(offer: RestaurantOffer) {
        launchSafely("تعذّر اعتماد العرض.") {
            repository.setOfferApproved(offer.id, true)
            val count = runCatching { repository.approvedCountFor(offer.merchantId) }.getOrDefault(0)
            val current = merchantStatuses.value.firstOrNull { it.uid == offer.merchantId }
            if (count >= 2 && current?.trusted != true && current?.suspended != true) {
                repository.setMerchantStatus(
                    com.example.data.repository.MerchantStatus(offer.merchantId, trusted = true, name = offer.restaurantName)
                )
                runCatching { repository.setStoreApproved(offer.merchantId, true) }
                toast("تم الاعتماد ✅ وصار ${offer.restaurantName} ⚡ ينشر مباشرة")
            } else {
                toast("تم اعتماد العرض ✅")
            }
            auditOffer("اعتماد عرض", offer, offer.id)
        }
    }

    /** يوثّق التاجر مباشرة ويعتمد العرض. */
    fun trustMerchant(offer: RestaurantOffer) {
        launchSafely("تعذّر توثيق التاجر.") {
            val cur = merchantStatuses.value.firstOrNull { it.uid == offer.merchantId }
            repository.setMerchantStatus(
                (cur ?: com.example.data.repository.MerchantStatus(offer.merchantId)).copy(trusted = true, suspended = false, name = offer.restaurantName)
            )
            repository.setOfferApproved(offer.id, true)
            runCatching { repository.setStoreApproved(offer.merchantId, true) }
            toast("صار ${offer.restaurantName} ⚡ ينشر مباشرة ✅")
            auditOffer("اعتماد عرض + توثيق التاجر", offer, offer.id)
        }
    }

    /** الأدمن: يعتمد صفحة متجر. */
    fun approveStore(store: com.example.data.model.StoreProfile) {
        launchSafely("تعذّر اعتماد الصفحة.") {
            repository.setStoreApproved(store.uid, true)
            toast("تم اعتماد صفحة ${store.name} ✅")
            audit("اعتماد صفحة متجر", "merchants", "store", store.uid, store.name)
        }
    }

    /** يوقف التاجر ويخفي كل عروضه. */
    fun suspendMerchant(merchantId: String, name: String) {
        launchSafely("تعذّر إيقاف الحساب.") {
            repository.setMerchantStatus(
                com.example.data.repository.MerchantStatus(merchantId, trusted = false, suspended = true, name = name)
            )
            repository.unapproveMerchantOffers(merchantId)
            runCatching { repository.setStoreApproved(merchantId, false) }
            toast("تم إيقاف $name وإخفاء عروضه")
            audit("إيقاف تاجر", "merchants", "merchant", merchantId, name)
        }
    }

    fun untrustMerchant(status: com.example.data.repository.MerchantStatus) {
        launchSafely("تعذّر التحديث.") {
            repository.setMerchantStatus(status.copy(trusted = false))
            toast("تم إلغاء توثيق ${status.name} — عروضه الجاية تمر بالمراجعة")
            audit("إلغاء الثقة بتاجر", "merchants", "merchant", status.uid, status.name)
        }
    }

    fun unsuspendMerchant(status: com.example.data.repository.MerchantStatus) {
        launchSafely("تعذّر إلغاء الإيقاف.") {
            repository.setMerchantStatus(status.copy(suspended = false, trusted = false))
            toast("تم إلغاء الإيقاف — عروضه الجاية تمر بالمراجعة")
            audit("إلغاء إيقاف تاجر", "merchants", "merchant", status.uid, status.name)
        }
    }

    /** العرض المبلّغ عنه سليم: يرجع يظهر ويتصفّر عدّاد البلاغات. */
    fun keepReportedOffer(offer: RestaurantOffer) {
        launchSafely("تعذّر تحديث العرض.") {
            repository.resetReportsCount(offer.id)
            reports.value.filter { it.offerId == offer.id }.forEach { runCatching { repository.deleteReport(it.id) } }
            toast("رجع العرض يظهر ✅")
            auditOffer("إرجاع عرض مخفي (البلاغات غير صحيحة)", offer, offer.id)
        }
    }

    fun dismissReport(reportId: String) {
        launchSafely("تعذّر حذف البلاغ.") {
            val r = reports.value.firstOrNull { it.id == reportId }
            repository.deleteReport(reportId)
            audit("تجاهل بلاغ", "review", "offer", r?.offerId.orEmpty(), r?.offerTitle.orEmpty(), r?.reason.orEmpty())
        }
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
            val before = offerById(id)
            repository.deleteOfferById(id)
            if (before != null && before.merchantId != currentUser.value?.uid) auditOffer("حذف عرض", before, id, "السعر: ${before.price} • الحي: ${before.district}")
            if (activeDetailOffer.value?.id == id) activeDetailOffer.value = null
            if (selectedOfferOnMap.value?.id == id) selectedOfferOnMap.value = null
        }
    }

    fun toggleFeatured(id: String, featured: Boolean) {
        launchSafely("تعذّر تحديث العرض.") {
            repository.setOfferFeatured(id, featured)
            auditOffer("تبديل تمييز عرض", offerById(id), id, if (featured) "صار مميّز ⭐" else "أُلغي التمييز")
        }
    }

    fun toggleApproved(id: String, approved: Boolean) {
        launchSafely("تعذّر تحديث العرض.") {
            repository.setOfferApproved(id, approved)
            auditOffer("تبديل حالة عرض", offerById(id), id, if (approved) "تفعّل" else "أُوقف")
        }
    }

    // ---------------- أدوات ----------------

    /** "منطقة مكة المكرمة - جدة" -> "مكة المكرمة" عشان تطابق اسم المنطقة الراجع من Google. */
    private fun regionKey(presetName: String): String =
        presetName.substringBefore(" - ").removePrefix("منطقة ").trim()

    /** "حي الملقا" و "الملقا" يعتبرون نفس الحي. */
    private fun normalizeDistrict(name: String): String =
        name.trim().removePrefix("حي ").trim()

    private fun toast(message: String) {
        Toast.makeText(getApplication(), com.example.ui.i18n.I18n.tr(message), Toast.LENGTH_LONG).show()
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

    // ---------------- 🔔 ذكّرني لما يتوفر ----------------
    private val restockPrefs = application.getSharedPreferences("hayyna_restock", android.content.Context.MODE_PRIVATE)
    val restockIds = MutableStateFlow(restockPrefs.getStringSet("ids", emptySet()).orEmpty().toSet())
    private fun saveRestock(ids: Set<String>) { restockIds.value = ids; restockPrefs.edit().putStringSet("ids", ids).apply() }
    fun toggleRestockId(id: String) { approvedOffers.value.firstOrNull { it.id == id }?.let { toggleRestock(it) } }
    fun toggleRestock(o: RestaurantOffer) {
        if (o.id in restockIds.value) { saveRestock(restockIds.value - o.id); toast("لغينا التذكير"); return }
        saveRestock(restockIds.value + o.id)
        toast("🔔 بنذكّرك أول ما يرجع متوفر")
        launchSafely { repository.incrementCounter(o.id, "notifyCount") } // المحل يشوف كم واحد ينتظر
    }
    init {
        viewModelScope.launch {
            approvedOffers.collect { list ->
                val ids = restockIds.value; if (ids.isEmpty() || list.isEmpty()) return@collect
                val now = System.currentTimeMillis()
                val back = list.filter { it.id in ids && !it.isSoldOut(now) && !OfferTime.isExpired(it, now) }
                val gone = ids.filter { id -> list.none { it.id == id } }
                if (back.isEmpty() && gone.isEmpty()) return@collect
                back.forEach { o ->
                    toast("🔔 «${o.offerTitle}» رجع متوفر — اطلبه الحين")
                    runCatching { com.example.data.OrderSounds.play(getApplication(), "ready") }
                }
                saveRestock(ids - back.map { it.id }.toSet() - gone.toSet())
            }
        }
    }

    companion object {
        const val WALLET_LOADING = -1L
        const val INVITE_LOCK_DAYS = 5
        private const val TAG = "MerchantViewModel"
        /** عدد البلاغات اللي يختفي بعدها العرض تلقائياً لين يراجعه الأدمن. */
        const val AUTO_HIDE_REPORTS = 3
        const val ROLE_CUSTOMER = "customer"
        const val SCOPE_ALL = "all"
        const val SCOPE_DISTRICT = "district"
        const val SCOPE_NEAR = "near"
        const val NEAR_KM = 5.0
        const val ROLE_MERCHANT = "merchant"
        /** حد العروض اليومية للتاجر الجديد (غير الموثوق). */
        const val NEW_MERCHANT_DAILY_LIMIT = 3
    }
}

/** توحيد الحروف للبحث: أ إ آ ← ا ، ة ← ه ، ى ← ي ، وبدون تشكيل. */
internal fun searchNorm(text: String): String =
    text.trim().lowercase()
        .replace(Regex("[\u064B-\u0652\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        // آخر الكلمة: ا / ه سواء (شاورما = شاورمه)
        .replace(Regex("[اه](?=\\s|$)"), "ه")
