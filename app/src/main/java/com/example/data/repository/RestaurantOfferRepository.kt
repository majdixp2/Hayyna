package com.example.data.repository

import com.example.data.model.MerchantProfile
import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RestaurantOfferRepository(
    private val db: FirebaseFirestore
) {
    companion object {
        private const val TAG = "OfferRepo"
        const val DEFAULT_AI_MODEL = "gemini-3.8-flash"
        /** نماذج احتياط لو النموذج المحدد ما اشتغل (Google قصرت 2.5 على المستخدمين القدامى). */
        val FALLBACK_AI_MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")

        private fun resolveFirestore(context: Context): FirebaseFirestore {
            val appContext = context.applicationContext
            try {
                if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) {
                    com.google.firebase.FirebaseApp.initializeApp(appContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp.initializeApp failed or skipped: ${e.message}")
            }

            val dbId = try {
                appContext.getString(R.string.firestore_database_id)
            } catch (e: Exception) {
                null
            }

            return try {
                val app = com.google.firebase.FirebaseApp.getInstance()
                if (!dbId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance(app, dbId)
                } else {
                    FirebaseFirestore.getInstance(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Custom database lookup failed, falling back to default getInstance: ${e.message}")
                try {
                    FirebaseFirestore.getInstance()
                } catch (e2: Exception) {
                    Log.e(TAG, "Fatal getting Firestore instance: ${e2.message}")
                    throw e2
                }
            }
        }
    }

    // Secondary constructor resolving custom database ID from firebase_applet_config.xml
    constructor(context: Context) : this(resolveFirestore(context))

    private val offersCollection = db.collection("offers")

    val allOffers: Flow<List<RestaurantOffer>> = callbackFlow {

        val listener = try {
            offersCollection
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore listening warning for all offers: ${error.message}")
                        return@addSnapshotListener
                    }
                    val offers = snapshot?.documents?.mapNotNull { doc ->
                        // أي وثيقة تالفة تُتجاهل بدل أن توقف التطبيق
                        try {
                            doc.toObject(RestaurantOffer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed offer ${doc.id}: ${e.message}")
                            null
                        }
                    } ?: emptyList()

                    trySend(offers)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach snapshot listener to offers: ${e.message}")
            null
        }

        awaitClose { listener?.remove() }
    }

    val approvedOffers: Flow<List<RestaurantOffer>> = callbackFlow {

        val listener = try {
            offersCollection
                .whereEqualTo("isApproved", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore listening warning for approved offers: ${error.message}")
                        return@addSnapshotListener
                    }
                    val offers = snapshot?.documents?.mapNotNull { doc ->
                        // أي وثيقة تالفة تُتجاهل بدل أن توقف التطبيق
                        try {
                            doc.toObject(RestaurantOffer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                ?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed offer ${doc.id}: ${e.message}")
                            null
                        }
                    } ?: emptyList()

                    trySend(offers)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach snapshot listener to approved offers: ${e.message}")
            null
        }

        awaitClose { listener?.remove() }
    }

    private fun parseOffers(snapshot: com.google.firebase.firestore.QuerySnapshot?): List<RestaurantOffer> =
        snapshot?.documents?.mapNotNull { doc ->
            try {
                doc.toObject(RestaurantOffer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = doc.id)
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

    /** عروض تاجر معيّن (تشمل اللي بانتظار المراجعة — يسمح بها صاحبها والأدمن). */
    fun offersByMerchant(uid: String): Flow<List<RestaurantOffer>> = callbackFlow {
        val listener = offersCollection.whereEqualTo("merchantId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "merchant offers: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(parseOffers(snapshot))
            }
        awaitClose { listener.remove() }
    }

    // ---------------- حالة التاجر (موثوق / موقوف) ----------------

    private val statusCollection = db.collection("merchantStatus")

    fun merchantStatus(uid: String): Flow<MerchantStatus> = callbackFlow {
        val listener = statusCollection.document(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(MerchantStatus(uid))
                return@addSnapshotListener
            }
            trySend(
                MerchantStatus(
                    uid = uid,
                    trusted = snap?.getBoolean("trusted") ?: false,
                    suspended = snap?.getBoolean("suspended") ?: false,
                    name = snap?.getString("name").orEmpty(),
                    verified = snap?.getBoolean("verified") ?: false
                )
            )
        }
        awaitClose { listener.remove() }
    }

    /** كل حالات التجار (للأدمن فقط). */
    val allMerchantStatuses: Flow<List<MerchantStatus>> = callbackFlow {
        val listener = statusCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map { d ->
                MerchantStatus(
                    uid = d.id,
                    trusted = d.getBoolean("trusted") ?: false,
                    suspended = d.getBoolean("suspended") ?: false,
                    name = d.getString("name").orEmpty(),
                    verified = d.getBoolean("verified") ?: false
                )
            } ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    suspend fun setMerchantStatus(status: MerchantStatus) {
        statusCollection.document(status.uid).set(
            hashMapOf(
                "trusted" to status.trusted,
                "suspended" to status.suspended,
                "name" to status.name,
                "verified" to status.verified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** يخفي كل عروض تاجر (عند إيقافه). */
    suspend fun unapproveMerchantOffers(uid: String) {
        val docs = offersCollection.whereEqualTo("merchantId", uid).get().await().documents
        for (d in docs) runCatching { d.reference.update("isApproved", false).await() }
    }

    /** عدد العروض المعتمدة لتاجر (لترقيته لموثوق تلقائياً). */
    suspend fun approvedCountFor(uid: String): Int =
        offersCollection.whereEqualTo("merchantId", uid).whereEqualTo("isApproved", true).get().await().size()

    suspend fun resetReportsCount(offerId: String) {
        offersCollection.document(offerId).update("reportsCount", 0).await()
    }

    // ---------------- نوع الحساب: عميل أو تاجر ----------------

    private val usersCollection = db.collection("users")

    suspend fun getUserRole(uid: String): String? =
        usersCollection.document(uid).get().await().getString("role")

    suspend fun setUserRole(uid: String, role: String) {
        usersCollection.document(uid).set(
            hashMapOf("role" to role, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    /** بيانات الحساب: النوع والاسم والجوال (null = ما له وثيقة). */
    suspend fun getUserProfile(uid: String): UserProfile? {
        val d = usersCollection.document(uid).get().await()
        if (!d.exists()) return null
        return UserProfile(d.getString("role"), d.getString("name").orEmpty(), d.getString("phone").orEmpty())
    }

    suspend fun saveUserProfile(uid: String, role: String, name: String, phone: String) {
        usersCollection.document(uid).set(
            hashMapOf("role" to role, "name" to name.take(60), "phone" to phone.take(20), "updatedAt" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun getFavorites(uid: String): Set<String> =
        (usersCollection.document(uid).get().await().get("favorites") as? List<*>)
            ?.filterIsInstance<String>()?.toSet() ?: emptySet()

    suspend fun saveFavorites(uid: String, ids: Set<String>) {
        usersCollection.document(uid).set(
            hashMapOf("favorites" to ids.take(500)),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    // ---------------- تواصل معنا ----------------

    private val feedbackCollection = db.collection("feedback")
    private val configDoc = db.collection("config").document("app")

    private fun parseFeedback(d: DocumentSnapshot) = com.example.data.model.Feedback(
        id = d.id,
        uid = d.getString("uid"),
        userName = d.getString("userName").orEmpty(),
        role = d.getString("role").orEmpty(),
        type = d.getString("type").orEmpty(),
        message = d.getString("message").orEmpty(),
        screenshotUrl = d.getString("screenshotUrl").orEmpty(),
        contact = d.getString("contact").orEmpty(),
        appVersion = d.getString("appVersion").orEmpty(),
        device = d.getString("device").orEmpty(),
        status = d.getString("status") ?: com.example.data.model.Feedback.STATUS_NEW,
        reply = d.getString("reply").orEmpty(),
        replySeen = d.getBoolean("replySeen") ?: true,
        createdAtMillis = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
    )

    suspend fun sendFeedback(f: com.example.data.model.Feedback) {
        feedbackCollection.add(
            hashMapOf(
                "uid" to f.uid,
                "userName" to f.userName.take(100),
                "role" to f.role.take(20),
                "type" to f.type.take(40),
                "message" to f.message.take(2000),
                "screenshotUrl" to f.screenshotUrl.take(2000),
                "contact" to f.contact.take(100),
                "appVersion" to f.appVersion.take(30),
                "device" to f.device.take(100),
                "status" to com.example.data.model.Feedback.STATUS_NEW,
                "reply" to "",
                "replySeen" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** رسائل المستخدم نفسه (مع ردود الإدارة). */
    fun myFeedback(uid: String): Flow<List<com.example.data.model.Feedback>> = callbackFlow {
        val listener = feedbackCollection.whereEqualTo("uid", uid).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.map { parseFeedback(it) }?.sortedByDescending { it.createdAtMillis } ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    /** كل الرسائل (للأدمن). */
    val allFeedback: Flow<List<com.example.data.model.Feedback>> = callbackFlow {
        val listener = feedbackCollection
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(300)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { parseFeedback(it) } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun replyFeedback(id: String, reply: String, status: String) {
        feedbackCollection.document(id).update(
            mapOf("reply" to reply.take(2000), "status" to status, "replySeen" to reply.isBlank())
        ).await()
    }

    suspend fun setFeedbackStatus(id: String, status: String) {
        feedbackCollection.document(id).update("status", status).await()
    }

    suspend fun markReplySeen(id: String) {
        feedbackCollection.document(id).update("replySeen", true).await()
    }

    suspend fun deleteFeedback(id: String) {
        feedbackCollection.document(id).delete().await()
    }

    /** إعدادات عامة يغيّرها الأدمن (مثل رقم الدعم). */
    val supportWhatsapp: Flow<String> = callbackFlow {
        val listener = configDoc.addSnapshotListener { snap, _ ->
            trySend(snap?.getString("supportWhatsapp")?.takeIf { it.isNotBlank() }
                ?: com.example.data.model.Feedback.DEFAULT_SUPPORT_WHATSAPP)
        }
        awaitClose { listener.remove() }
    }

    /** إعدادات الذكاء الاصطناعي: (مفعّل؟، اسم النموذج). */
    val aiConfig: Flow<Pair<Boolean, String>> = callbackFlow {
        val listener = configDoc.addSnapshotListener { snap, _ ->
            trySend(
                (snap?.getBoolean("aiAutofillEnabled") ?: false) to
                    (snap?.getString("aiModel")?.takeIf { it.isNotBlank() } ?: DEFAULT_AI_MODEL)
            )
        }
        awaitClose { listener.remove() }
    }

    suspend fun setAiConfig(enabled: Boolean, model: String) {
        configDoc.set(
            hashMapOf("aiAutofillEnabled" to enabled, "aiModel" to model.trim().ifBlank { DEFAULT_AI_MODEL }.take(60)),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /** إعدادات نقاط محلات Google والدعوات (يتحكم فيها الأدمن). */
    val poiConfig: Flow<PoiConfig> = callbackFlow {
        val listener = configDoc.addSnapshotListener { snap, _ ->
            trySend(
                PoiConfig(
                    showGooglePoi = snap?.getBoolean("showGooglePoi") ?: true,
                    inviteEnabled = snap?.getBoolean("poiInviteEnabled") ?: true,
                    inviteMessage = snap?.getString("inviteMessage")?.takeIf { it.isNotBlank() } ?: PoiConfig.DEFAULT_MESSAGE,
                    appLink = snap?.getString("appLink").orEmpty()
                )
            )
        }
        awaitClose { listener.remove() }
    }

    suspend fun setPoiConfig(c: PoiConfig) {
        configDoc.set(
            hashMapOf(
                "showGooglePoi" to c.showGooglePoi,
                "poiInviteEnabled" to c.inviteEnabled,
                "inviteMessage" to c.inviteMessage.take(500),
                "appLink" to c.appLink.take(300)
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    private val poiInterest = db.collection("poiInterest")

    /** نسجّل الاهتمام بمحل من نقاط Google (نخزّن معرّف المكان فقط حسب شروط Google). */
    suspend fun logPoi(placeId: String, field: String) {
        require(field in setOf("taps", "invites", "claims"))
        poiInterest.document(placeId.take(200)).set(
            hashMapOf(field to FieldValue.increment(1), "lastAt" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /** أكثر المحلات اللي ضغطها العملاء (للأدمن). */
    val topPoi: Flow<List<PoiInterest>> = callbackFlow {
        val listener = poiInterest.orderBy("taps", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { d ->
                    PoiInterest(
                        placeId = d.id,
                        taps = d.getLong("taps") ?: 0,
                        invites = d.getLong("invites") ?: 0,
                        claims = d.getLong("claims") ?: 0
                    )
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun setSupportWhatsapp(number: String) {
        configDoc.set(hashMapOf("supportWhatsapp" to number.take(20)), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    // ---------------- صفحات المتاجر (عامة) ----------------

    private val storesCollection = db.collection("stores")

    private fun parseStores(snapshot: com.google.firebase.firestore.QuerySnapshot?): List<com.example.data.model.StoreProfile> =
        snapshot?.documents?.mapNotNull { d ->
            runCatching { d.toObject(com.example.data.model.StoreProfile::class.java)?.copy(uid = d.id) }.getOrNull()
        } ?: emptyList()

    /** المتاجر المعتمدة (للعملاء). */
    val approvedStores: Flow<List<com.example.data.model.StoreProfile>> = callbackFlow {
        val listener = storesCollection.whereEqualTo("approved", true).addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "stores: ${error.message}")
                return@addSnapshotListener
            }
            trySend(parseStores(snap))
        }
        awaitClose { listener.remove() }
    }

    /** صفحات المتاجر بانتظار المراجعة (للأدمن). */
    val pendingStores: Flow<List<com.example.data.model.StoreProfile>> = callbackFlow {
        val listener = storesCollection.whereEqualTo("approved", false).addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(parseStores(snap))
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveStore(store: com.example.data.model.StoreProfile) {
        storesCollection.document(store.uid).set(
            hashMapOf(
                "uid" to store.uid,
                "name" to store.name.take(100),
                "logoUrl" to store.logoUrl,
                "coverUrl" to store.coverUrl,
                "about" to store.about.take(500),
                "category" to store.category.take(60),
                "priceFrom" to store.priceFrom,
                "menuImages" to store.menuImages.take(com.example.data.model.StoreProfile.MAX_MENU_IMAGES),
                "branches" to store.branches.take(50).map { it.toMap() },
                "phone" to store.phone.take(30),
                "approved" to store.approved,
                "delivery" to store.delivery,
                "deliveryFee" to store.deliveryFee,
                "deliveryMinOrder" to store.deliveryMinOrder,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            // دمج: عشان ما تنمسح علامة ✓ اللي يحطها الأدمن
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    // ---------------- التوثيق ----------------

    private val verifyCollection = db.collection("verificationRequests")

    private fun parseVerification(d: DocumentSnapshot) = com.example.data.model.VerificationRequest(
        uid = d.id,
        storeName = d.getString("storeName").orEmpty(),
        docType = d.getString("docType").orEmpty(),
        docNumber = d.getString("docNumber").orEmpty(),
        photoUrl = d.getString("photoUrl").orEmpty(),
        locationChecked = d.getBoolean("locationChecked") ?: false,
        locationOk = d.getBoolean("locationOk") ?: false,
        distanceMeters = (d.getLong("distanceMeters") ?: -1L).toInt(),
        status = d.getString("status") ?: com.example.data.model.VerificationRequest.STATUS_PENDING,
        rejectReason = d.getString("rejectReason").orEmpty(),
        createdAtMillis = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
    )

    val verifyConfig: Flow<com.example.data.model.VerifyConfig> = callbackFlow {
        val listener = configDoc.addSnapshotListener { snap, _ ->
            trySend(
                com.example.data.model.VerifyConfig(
                    enabled = snap?.getBoolean("verifyEnabled") ?: true,
                    requireDoc = snap?.getBoolean("verifyRequireDoc") ?: true,
                    requirePhoto = snap?.getBoolean("verifyRequirePhoto") ?: true,
                    requireLocation = snap?.getBoolean("verifyRequireLocation") ?: true
                )
            )
        }
        awaitClose { listener.remove() }
    }

    suspend fun setVerifyConfig(c: com.example.data.model.VerifyConfig) {
        configDoc.set(
            hashMapOf(
                "verifyEnabled" to c.enabled,
                "verifyRequireDoc" to c.requireDoc,
                "verifyRequirePhoto" to c.requirePhoto,
                "verifyRequireLocation" to c.requireLocation
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun myVerification(uid: String): Flow<com.example.data.model.VerificationRequest?> = callbackFlow {
        val listener = verifyCollection.document(uid).addSnapshotListener { snap, error ->
            if (error != null || snap == null || !snap.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(parseVerification(snap))
        }
        awaitClose { listener.remove() }
    }

    val pendingVerifications: Flow<List<com.example.data.model.VerificationRequest>> = callbackFlow {
        val listener = verifyCollection.whereEqualTo("status", com.example.data.model.VerificationRequest.STATUS_PENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { parseVerification(it) }?.sortedBy { it.createdAtMillis } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun submitVerification(r: com.example.data.model.VerificationRequest) {
        verifyCollection.document(r.uid).set(
            hashMapOf(
                "uid" to r.uid,
                "storeName" to r.storeName.take(100),
                "docType" to r.docType,
                "docNumber" to r.docNumber.take(40),
                "photoUrl" to r.photoUrl,
                "locationChecked" to r.locationChecked,
                "locationOk" to r.locationOk,
                "distanceMeters" to r.distanceMeters,
                "status" to com.example.data.model.VerificationRequest.STATUS_PENDING,
                "rejectReason" to "",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun decideVerification(uid: String, approved: Boolean, reason: String) {
        verifyCollection.document(uid).update(
            mapOf(
                "status" to if (approved) com.example.data.model.VerificationRequest.STATUS_APPROVED else com.example.data.model.VerificationRequest.STATUS_REJECTED,
                "rejectReason" to reason.take(300)
            )
        ).await()
        val storeRef = storesCollection.document(uid)
        if (storeRef.get().await().exists()) {
            storeRef.update(mapOf("verified" to approved) + if (approved) mapOf("approved" to true) else emptyMap()).await()
        }
    }

    /** الأدمن: يعتمد/يخفي صفحة متجر (لو موجودة). */
    suspend fun setStoreApproved(uid: String, approved: Boolean) {
        val ref = storesCollection.document(uid)
        if (ref.get().await().exists()) ref.update("approved", approved).await()
    }

    private fun RestaurantOffer.toFirestoreMap(docId: String, createdAtValue: Any?): HashMap<String, Any?> =
        hashMapOf(
            "id" to docId,
            "merchantId" to merchantId,
            "restaurantName" to restaurantName,
            "offerTitle" to offerTitle,
            "description" to description,
            "cuisineType" to cuisineType,
            "price" to price,
            "originalPrice" to originalPrice,
            "currency" to currency,
            "discountBadge" to discountBadge,
            "mediaType" to mediaType.name,
            "mediaUri" to mediaUri,
            "isPromotionalVideo" to isPromotionalVideo,
            "district" to district,
            "region" to region,
            "latitude" to latitude,
            "longitude" to longitude,
            "phone" to phone,
            "workingHours" to workingHours,
            "openMinute" to openMinute,
            "closeMinute" to closeMinute,
            "expiresAt" to expiresAt,
            "startsAt" to startsAt,
            "discountPercent" to discountPercent,
            "dailyPercents" to dailyPercents.take(14),
            "itemsScope" to itemsScope.take(60),
            "grandOpening" to grandOpening,
            "tags" to tags.take(12),
            "delivery" to delivery,
            "deliveryFee" to deliveryFee,
            "deliveryMinOrder" to deliveryMinOrder,
            "rating" to rating,
            "isApproved" to isApproved,
            "isFeatured" to isFeatured,
            "viewsCount" to viewsCount,
            "directionsCount" to directionsCount,
            "whatsappCount" to whatsappCount,
            "reportsCount" to reportsCount,
            "mediaUris" to mediaUris.take(3),
            "logoUrl" to logoUrl,
            "branches" to branches.take(50).map { it.toMap() },
            "createdAt" to createdAtValue
        )

    suspend fun insertOffer(offer: RestaurantOffer): String {
        val docRef = if (offer.id.isNotBlank()) offersCollection.document(offer.id) else offersCollection.document()
        docRef.set(offer.toFirestoreMap(docRef.id, FieldValue.serverTimestamp())).await()
        return docRef.id
    }

    suspend fun updateOffer(offer: RestaurantOffer) {
        require(offer.id.isNotBlank()) { "Offer ID must not be blank" }
        val docRef = offersCollection.document(offer.id)
        docRef.set(offer.toFirestoreMap(docRef.id, offer.createdAt ?: FieldValue.serverTimestamp())).await()
    }

    suspend fun deleteOfferById(id: String) {
        offersCollection.document(id).delete().await()
    }

    suspend fun setOfferApproved(id: String, approved: Boolean) {
        offersCollection.document(id).update("isApproved", approved).await()
    }

    suspend fun setOfferFeatured(id: String, featured: Boolean) {
        offersCollection.document(id).update("isFeatured", featured).await()
    }

    /** يزيد عدّاد إحصائية واحدة (viewsCount / directionsCount / whatsappCount) بمقدار 1. */
    suspend fun incrementCounter(id: String, field: String) {
        require(field in setOf("viewsCount", "directionsCount", "whatsappCount", "reportsCount"))
        offersCollection.document(id).update(field, FieldValue.increment(1)).await()
    }

    suspend fun incrementViews(id: String) = incrementCounter(id, "viewsCount")

    // ---------------- ملف المطعم ----------------

    private val merchantsCollection = db.collection("merchants")

    suspend fun getMerchantProfile(uid: String): MerchantProfile? =
        merchantsCollection.document(uid).get().await().toObject(MerchantProfile::class.java)

    suspend fun saveMerchantProfile(uid: String, profile: MerchantProfile) {
        merchantsCollection.document(uid).set(
            hashMapOf(
                "restaurantName" to profile.restaurantName,
                "phone" to profile.phone,
                "latitude" to profile.latitude,
                "longitude" to profile.longitude,
                "region" to profile.region,
                "district" to profile.district,
                "cuisineType" to profile.cuisineType,
                "openMinute" to profile.openMinute,
                "closeMinute" to profile.closeMinute,
                "logoUrl" to profile.logoUrl,
                "branches" to profile.branches.take(50).map { it.toMap() },
                "coverUrl" to profile.coverUrl,
                "about" to profile.about.take(500),
                "priceFrom" to profile.priceFrom,
                "menuImages" to profile.menuImages.take(com.example.data.model.StoreProfile.MAX_MENU_IMAGES),
                "delivery" to profile.delivery,
                "deliveryFee" to profile.deliveryFee,
                "deliveryMinOrder" to profile.deliveryMinOrder,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * بعد تعديل الفروع أو الشعار: نحدّث نسخها داخل كل عروض التاجر،
     * عشان يتغيّر الموقع/الرقم/الدوام/الشعار في كل العروض مرة وحدة.
     */
    suspend fun syncMerchantOffers(uid: String, profile: MerchantProfile, keepApproval: Boolean = true) {
        val byId = profile.branches.associateBy { it.id }
        val docs = offersCollection.whereEqualTo("merchantId", uid).get().await().documents
        for (doc in docs) {
            val offer = runCatching {
                doc.toObject(RestaurantOffer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = doc.id)
            }.getOrNull() ?: continue
            val updatedBranches = offer.branches.mapNotNull { byId[it.id] }
            val branches = if (offer.branches.isNotEmpty() && updatedBranches.isEmpty()) offer.branches else updatedBranches
            val primary = branches.firstOrNull()
            val updated = offer.copy(
                logoUrl = profile.logoUrl,
                branches = branches,
                delivery = profile.delivery,
                deliveryFee = profile.deliveryFee,
                deliveryMinOrder = profile.deliveryMinOrder,
                latitude = primary?.latitude ?: offer.latitude,
                longitude = primary?.longitude ?: offer.longitude,
                region = primary?.region?.ifBlank { null } ?: offer.region,
                district = primary?.district?.ifBlank { null } ?: offer.district,
                phone = primary?.phone?.ifBlank { null } ?: offer.phone,
                openMinute = primary?.openMinute ?: offer.openMinute,
                closeMinute = primary?.closeMinute ?: offer.closeMinute,
                // التاجر غير الموثوق: أي تعديل يرجّع العرض للمراجعة
                isApproved = if (keepApproval) offer.isApproved else false
            )
            if (updated != offer) runCatching { updateOffer(updated) }
        }
    }

    // ---------------- البلاغات ----------------

    private val reportsCollection = db.collection("reports")

    suspend fun reportOffer(offer: RestaurantOffer, reason: String, reporterUid: String?) {
        reportsCollection.add(
            hashMapOf(
                "offerId" to offer.id,
                "offerTitle" to offer.offerTitle.take(150),
                "restaurantName" to offer.restaurantName.take(100),
                "merchantId" to offer.merchantId,
                "reason" to reason.take(300),
                "reporterUid" to reporterUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** البلاغات للأدمن (الأحدث أولاً). */
    val reports: Flow<List<OfferReport>> = callbackFlow {
        val listener = reportsCollection
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // غير الأدمن ما يقدر يقرأ البلاغات — نرجع قائمة فاضية بهدوء
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { doc ->
                    OfferReport(
                        id = doc.id,
                        offerId = doc.getString("offerId").orEmpty(),
                        offerTitle = doc.getString("offerTitle").orEmpty(),
                        restaurantName = doc.getString("restaurantName").orEmpty(),
                        reason = doc.getString("reason").orEmpty(),
                        createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun deleteReport(id: String) {
        reportsCollection.document(id).delete().await()
    }

    // ---------------- حذف الحساب ----------------

    /** يحذف كل عروض التاجر وملفه (قبل حذف حساب Firebase Auth). */
    suspend fun deleteMerchantData(uid: String) {
        val offers = offersCollection.whereEqualTo("merchantId", uid).get().await()
        for (doc in offers.documents) doc.reference.delete().await()
        merchantsCollection.document(uid).delete().await()
        runCatching { storesCollection.document(uid).delete().await() }
        runCatching { usersCollection.document(uid).delete().await() }
    }
}

data class UserProfile(val role: String?, val name: String, val phone: String)

data class PoiConfig(
    val showGooglePoi: Boolean = true,
    val inviteEnabled: Boolean = true,
    val inviteMessage: String = DEFAULT_MESSAGE,
    val appLink: String = ""
) {
    companion object {
        const val DEFAULT_MESSAGE =
            "السلام عليكم 👋 شفت محلكم \"{name}\" على خريطة حيّنا. سجّلوا محلكم مجاناً وانشروا عروضكم لأهل الحي، وخلّوا الزباين يوصلون لكم بسهولة 📍"
    }
}

data class PoiInterest(val placeId: String, val taps: Long, val invites: Long, val claims: Long)

data class MerchantStatus(
    val uid: String,
    val trusted: Boolean = false,
    val suspended: Boolean = false,
    val name: String = "",
    val verified: Boolean = false
)

data class OfferReport(
    val id: String,
    val offerId: String,
    val offerTitle: String,
    val restaurantName: String,
    val reason: String,
    val createdAtMillis: Long
)
