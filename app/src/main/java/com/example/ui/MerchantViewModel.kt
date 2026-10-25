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

    fun storeFor(merchantId: String): com.example.data.model.StoreProfile? =
        approvedStores.value.firstOrNull { it.uid == merchantId }

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
        }
    }

    // ---------------- نقاط محلات Google + الدعوات ----------------

    val poiConfig: StateFlow<com.example.data.repository.PoiConfig> = repository.poiConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.repository.PoiConfig())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val topPoi: StateFlow<List<com.example.data.repository.PoiInterest>> = isAdmin
        .flatMapLatest { admin -> if (admin) repository.topPoi else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** المحل اللي ضغطه العميل من نقاط Google (مو مسجّل في حيّنا). */
    val selectedPoi = MutableStateFlow<com.google.android.gms.maps.model.PointOfInterest?>(null)
    /** محل يبي صاحبه يسجّله — نعبّي اسمه وموقعه تلقائياً. */
    val pendingClaim = MutableStateFlow<com.google.android.gms.maps.model.PointOfInterest?>(null)

    fun onPoiClick(poi: com.google.android.gms.maps.model.PointOfInterest) {
        if (!poiConfig.value.inviteEnabled) return
        selectedOfferOnMap.value = null
        selectedPoi.value = poi
        launchSafely { repository.logPoi(poi.placeId, "taps") }
    }

    fun dismissPoi() { selectedPoi.value = null }

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
        }
    }

    /** تصنيفات المتاجر (من إعدادات الأدمن). */
    val categories: StateFlow<List<String>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestaurantOfferRepository.DEFAULT_CATEGORIES)

    fun saveCategories(list: List<String>) {
        launchSafely("تعذّر حفظ التصنيفات.") {
            repository.setCategories(list)
            toast("تم حفظ التصنيفات ✅")
        }
    }

    fun renameCategory(old: String, new: String) {
        val name = new.trim()
        if (name.isBlank() || name == old) return
        launchSafely("تعذّر تغيير الاسم.") {
            repository.setCategories(categories.value.map { if (it == old) name else it })
            val n = repository.renameCategory(old, name)
            toast("تم تغيير الاسم ✅ وتحدّث $n عرض")
        }
    }

    fun saveVerifyConfig(c: com.example.data.model.VerifyConfig) {
        launchSafely("تعذّر الحفظ.") {
            repository.setVerifyConfig(c)
            toast("تم الحفظ ✅")
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
        }
    }

    fun setFeedbackStatus(id: String, status: String) {
        launchSafely("تعذّر التحديث.") { repository.setFeedbackStatus(id, status) }
    }

    fun deleteFeedback(id: String) {
        launchSafely("تعذّر الحذف.") { repository.deleteFeedback(id) }
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

    /**
     * العروض الظاهرة: لما يكون رصيد العروض مفعّل، العرض الجديد لازم له "نشر" مدفوع،
     * والتمييز المدفوع يخلي العرض مميّز (حلقة صفراء وأول الترتيب).
     */
    private val approvedOffers: StateFlow<List<RestaurantOffer>> =
        combine(repository.approvedOffers, userLocation, creditsConfig, repository.activeBoosts, nowMillis) { offers, loc, cr, boosts, now ->
            val byOffer = boosts.groupBy { it.offerId }
            val visible = offers.mapNotNull { o ->
                val bs = byOffer[o.id].orEmpty()
                val created = o.createdAt?.toDate()?.time ?: 0L
                val inGrace = cr.enabledAtMillis > 0L && created < cr.enabledAtMillis && now < cr.graceUntilMillis
                val paidUp = !cr.enabled || cr.enabledAtMillis == 0L || inGrace ||
                    bs.any { it.effect == "publish" && it.endsAt > now }
                if (!paidUp) return@mapNotNull null
                val featured = bs.any { it.effect == "feature" && it.startsAt <= now && now < it.endsAt }
                if (featured && !o.isFeatured) o.copy(isFeatured = true) else o
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
        sorted.sortedByDescending { it.isFeatured }
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
                if (user != null) {
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
            toast("أهلاً بك كتاجر في حيّنا 🏪")
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
    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
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

    fun saveOffer(offer: RestaurantOffer) {
        val user = currentUser.value
        if (user == null) {
            toast("سجّل الدخول بحساب Google أولاً لإضافة عرض")
            return
        }
        if (myStatus.value?.suspended == true) {
            toast("حسابك موقوف حالياً. تواصل مع إدارة حيّنا.")
            return
        }
        if (offer.id.isBlank() && creditsConfig.value.enabled && !isAdmin.value) {
            toast("النشر صار برصيد العروض: انشر عرضك من لوحة التاجر في موقع حيّنا 🌐")
            return
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
                if (isUpdate) {
                    // تعديل التاجر غير الموثوق يرجّع العرض للمراجعة
                    repository.updateOffer(
                        offer.copy(mediaUri = cloudUrl, mediaUris = uploaded, isApproved = if (direct) offer.isApproved else false)
                    )
                } else {
                    repository.insertOffer(
                        offer.copy(
                            merchantId = user.uid,
                            mediaUri = cloudUrl,
                            mediaUris = uploaded,
                            isFeatured = false,
                            isApproved = direct,
                            reportsCount = 0,
                            viewsCount = 0,
                            directionsCount = 0,
                            whatsappCount = 0
                        )
                    )
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
                        "رفض Firestore حفظ العرض (صلاحيات). تأكد من نشر قواعد Firestore الجديدة."
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
                toast("تم الاعتماد ✅ وصار ${offer.restaurantName} موثوق (ينشر مباشرة)")
            } else {
                toast("تم اعتماد العرض ✅")
            }
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
            toast("صار ${offer.restaurantName} موثوق ✅")
        }
    }

    /** الأدمن: يعتمد صفحة متجر. */
    fun approveStore(store: com.example.data.model.StoreProfile) {
        launchSafely("تعذّر اعتماد الصفحة.") {
            repository.setStoreApproved(store.uid, true)
            toast("تم اعتماد صفحة ${store.name} ✅")
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
        }
    }

    fun untrustMerchant(status: com.example.data.repository.MerchantStatus) {
        launchSafely("تعذّر التحديث.") {
            repository.setMerchantStatus(status.copy(trusted = false))
            toast("تم إلغاء توثيق ${status.name} — عروضه الجاية تمر بالمراجعة")
        }
    }

    fun unsuspendMerchant(status: com.example.data.repository.MerchantStatus) {
        launchSafely("تعذّر إلغاء الإيقاف.") {
            repository.setMerchantStatus(status.copy(suspended = false, trusted = false))
            toast("تم إلغاء الإيقاف — عروضه الجاية تمر بالمراجعة")
        }
    }

    /** العرض المبلّغ عنه سليم: يرجع يظهر ويتصفّر عدّاد البلاغات. */
    fun keepReportedOffer(offer: RestaurantOffer) {
        launchSafely("تعذّر تحديث العرض.") {
            repository.resetReportsCount(offer.id)
            reports.value.filter { it.offerId == offer.id }.forEach { runCatching { repository.deleteReport(it.id) } }
            toast("رجع العرض يظهر ✅")
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
