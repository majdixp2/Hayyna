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
        val DEFAULT_CATEGORIES: List<String> = com.example.data.model.LocationPresets.cuisines.filter { it != "الكل" }
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

    /** تصنيفات المتاجر (يتحكم فيها الأدمن). فاضية = القائمة الافتراضية. */
    val categories: Flow<List<String>> = callbackFlow {
        val listener = configDoc.addSnapshotListener { snap, _ ->
            val list = (snap?.get("categories") as? List<*>)?.filterIsInstance<String>()
                ?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct()
            trySend(list?.takeIf { it.isNotEmpty() } ?: DEFAULT_CATEGORIES)
        }
        awaitClose { listener.remove() }
    }

    suspend fun setCategories(list: List<String>) {
        configDoc.set(
            hashMapOf("categories" to list.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(40)),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /** تغيير اسم تصنيف: يحدّث العروض وصفحات المتاجر اللي عليه (الأدمن). */
    suspend fun renameCategory(old: String, new: String): Int {
        var n = 0
        for (d in offersCollection.whereEqualTo("cuisineType", old).get().await().documents) {
            runCatching { d.reference.update("cuisineType", new).await(); n++ }
        }
        for (d in storesCollection.whereEqualTo("category", old).get().await().documents) {
            runCatching { d.reference.update("category", new).await() }
        }
        return n
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

    /** رصيد العروض + شارة الحصري (من لوحة الأدمن). */
    val creditsConfig: Flow<CreditsConfig> = callbackFlow {
        val listener = configDoc.addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            val cr = snap?.get("credits") as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val ex = snap?.get("exclusive") as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val rw = ex["reward"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val items = (cr["items"] as? Map<String, Any?>)?.mapNotNull { (id, v) ->
                val m = v as? Map<String, Any?> ?: return@mapNotNull null
                CreditItem(
                    id = id, name = (m["name"] as? String ?: id).let { n -> if (Regex("إبراز|مُبرز").containsMatchIn(n)) n.replace(Regex("🚀\\s*"), "").replace(Regex("الإبراز|إبراز|مُبرز"), "🚀 عرض سوبر") else n }, effect = m["effect"] as? String ?: "",
                    unit = m["unit"] as? String ?: "fixed", price = (m["price"] as? Number)?.toLong() ?: 0L,
                    days = (m["days"] as? Number)?.toInt() ?: 1, active = m["active"] as? Boolean ?: false,
                    order = (m["order"] as? Number)?.toInt() ?: 0
                )
            }?.associateBy { it.id } ?: CreditItem.DEFAULTS
            trySend(
                CreditsConfig(
                    items = items,
                    welcome = (cr["welcome"] as? Number)?.toLong() ?: 0L,
                    spotlightMax = (cr["spotlightMax"] as? Number)?.toInt() ?: 3,
                    rewardOn = (rw["enabled"] as? Boolean ?: false) && (ex["enabled"] as? Boolean ?: true) && ((rw["percent"] as? Number)?.toInt() ?: 0) > 0,
                    rewardPercent = ((rw["percent"] as? Number)?.toInt() ?: 0).coerceIn(0, 100),
                    enabled = cr["enabled"] as? Boolean ?: false,
                    enabledAtMillis = (cr["enabledAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                    graceUntilMillis = (cr["graceUntil"] as? Timestamp)?.toDate()?.time ?: Long.MAX_VALUE,
                    exclusiveEnabled = ex["enabled"] as? Boolean ?: true,
                    exclusiveLabel = (ex["label"] as? String)?.takeIf { it.isNotBlank() } ?: "حصري في لكوفر",
                    exclusiveIcon = (ex["icon"] as? String)?.takeIf { it.isNotBlank() } ?: "💎"
                )
            )
        }
        awaitClose { listener.remove() }
    }

    /** الخدمات المدفوعة النشطة (نشر / تمييز / إعلان بارز). */
    val activeBoosts: Flow<List<Boost>> = callbackFlow {
        val listener = db.collection("boosts").whereGreaterThan("endsAt", Timestamp.now())
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents.orEmpty().map { d ->
                    Boost(
                        merchantId = d.getString("merchantId").orEmpty(),
                        offerId = d.getString("offerId").orEmpty(),
                        effect = d.getString("effect").orEmpty(),
                        startsAt = d.getTimestamp("startsAt")?.toDate()?.time ?: 0L,
                        endsAt = d.getTimestamp("endsAt")?.toDate()?.time ?: 0L
                    )
                })
            }
        awaitClose { listener.remove() }
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
    /** قفل الدعوة: أول من يدعو المحل يحجزه كم يوم. يرجّع (by, untilMillis) أو null. */
    suspend fun getPoiInvite(placeId: String): Pair<String, Long>? {
        val d = db.collection("poiInvites").document(placeId.take(200)).get().await()
        if (!d.exists()) return null
        val until = d.getTimestamp("until")?.toDate()?.time ?: return null
        return (d.getString("by").orEmpty()) to until
    }

    suspend fun lockPoiInvite(placeId: String, uid: String, days: Int) {
        db.collection("poiInvites").document(placeId.take(200)).set(
            hashMapOf(
                "by" to uid,
                "at" to FieldValue.serverTimestamp(),
                "until" to Timestamp(java.util.Date(System.currentTimeMillis() + days * 86_400_000L))
            )
        ).await()
    }

    /** يعيد الاتصال بقاعدة البيانات (تحديث يدوي من الشعار). */
    suspend fun reconnect() {
        runCatching { db.disableNetwork().await(); db.enableNetwork().await() }
    }

    // ---------------- 📷 عروض من إعلانات (الأدمن وفريق الإعلانات) ----------------

    /** المالك، أو موظف فعّال عنده صلاحية scout. */
    suspend fun isScout(email: String?): Boolean {
        val e = email?.lowercase()?.trim().orEmpty()
        if (e.isBlank()) return false
        if (com.example.data.AdminConfig.isAdmin(e)) return true
        return runCatching {
            val d = db.collection("staff").document(e).get().await()
            d.getBoolean("active") == true && ((d.get("perms") as? List<*>)?.contains("scout") == true)
        }.getOrDefault(false)
    }

    /** مدة عروض الإعلانات الافتراضية بالأيام (الأدمن يغيّرها من اللوحة). */
    suspend fun adScoutDays(): Int = runCatching {
        (db.collection("config").document("app").get().await().getLong("adScoutDays") ?: 7L).toInt().coerceIn(1, 31)
    }.getOrDefault(7)

    /** ينشر عرض من إعلان لمحل غير مسجّل. يرجّع رقم العرض. */
    suspend fun addAdOffer(data: Map<String, Any?>): String {
        val ref = db.collection("offers").document()
        val doc = HashMap<String, Any?>(data)
        doc["id"] = ref.id
        doc["isApproved"] = true; doc["isFeatured"] = false
        doc["viewsCount"] = 0; doc["directionsCount"] = 0; doc["whatsappCount"] = 0; doc["reportsCount"] = 0
        doc["createdAt"] = FieldValue.serverTimestamp()
        ref.set(doc).await()
        return ref.id
    }

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

    suspend fun getStore(uid: String): com.example.data.model.StoreProfile? =
        storesCollection.document(uid).get().await().let { d -> if (!d.exists()) null else runCatching { d.toObject(com.example.data.model.StoreProfile::class.java)?.copy(uid = d.id) }.getOrNull() }

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
                "deliveryFreeOver" to store.deliveryFreeOver,
                "curbside" to store.curbside, "payCash" to store.payCash, "payCard" to store.payCard,
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
                "rejectReason" to reason.take(300),
                "decidedAt" to FieldValue.serverTimestamp(),
                "decidedBy" to (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: "")
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
            "postType" to (if (postType == "new") "new" else "offer"),
            "exclusive" to exclusive,
            "draft" to draft,
            "adminNote" to adminNote,
            "deal" to deal,
            "tags" to tags.take(12),
            "delivery" to delivery,
            "deliveryFee" to deliveryFee,
            "deliveryMinOrder" to deliveryMinOrder,
            "deliveryFreeOver" to deliveryFreeOver,
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

    // ================= رصيد العروض (المحفظة) =================
    // الشراء برّا التطبيق؛ هنا نعرض الرصيد ونصرفه بس (نفس قواعد Firestore للويب).

    /** رصيد التاجر (null = ما عنده محفظة للحين). */
    fun walletBalance(uid: String): Flow<Long?> = callbackFlow {
        val l = db.collection("wallets").document(uid).addSnapshotListener { s, _ ->
            trySend(if (s != null && s.exists()) (s.getLong("balance") ?: 0L) else null)
        }
        awaitClose { l.remove() }
    }

    /** أول ما يتفعّل الرصيد: محفظة جديدة (مع هدية الترحيب لو فيه). */
    suspend fun createWelcomeWallet(uid: String, welcome: Long) {
        val b = db.batch()
        b.set(db.collection("wallets").document(uid), hashMapOf("balance" to welcome, "lastOp" to hashMapOf("kind" to "welcome", "ref" to ""), "updatedAt" to FieldValue.serverTimestamp()))
        if (welcome > 0) b.set(db.collection("wallets").document(uid).collection("tx").document(),
            hashMapOf("kind" to "welcome", "points" to welcome, "note" to "🎁 هدية الترحيب من لكوفر", "ref" to "", "at" to FieldValue.serverTimestamp()))
        b.commit().await()
    }

    /**
     * عملية صرف وحدة: (إنشاء/تحديث العرض) + سجل الخدمة + خصم الرصيد — يا تصير كلها يا ولا شي.
     * offer: عرض جديد يُنشأ بنفس العملية. offerUpdate: تحديث حقول عرض موجود (مثل نشر مسودة).
     */
    suspend fun spendOnOffer(
        uid: String, balance: Long, charge: Boolean, offerId: String, item: CreditItem, days: Int, cost: Long, disc: Int,
        startMillis: Long, note: String, offer: RestaurantOffer? = null, offerUpdate: Map<String, Any?>? = null
    ): String {
        val b = db.batch()
        val offerRef = if (offerId.isNotBlank()) offersCollection.document(offerId) else offersCollection.document()
        if (offer != null) b.set(offerRef, offer.toFirestoreMap(offerRef.id, FieldValue.serverTimestamp()))
        if (offerUpdate != null) b.update(offerRef, offerUpdate)
        val boostRef = db.collection("boosts").document()
        val start = maxOf(System.currentTimeMillis(), startMillis)
        val boost = hashMapOf<String, Any?>(
            "id" to boostRef.id, "merchantId" to uid, "offerId" to offerRef.id, "item" to item.id, "effect" to item.effect,
            "days" to days.toLong(), "cost" to cost, "createdAt" to FieldValue.serverTimestamp(),
            "startsAt" to Timestamp(java.util.Date(start)), "endsAt" to Timestamp(java.util.Date(start + days * 86_400_000L))
        )
        if (disc > 0) boost["disc"] = disc.toLong()
        b.set(boostRef, boost)
        if (charge && cost > 0) {
            val w = db.collection("wallets").document(uid)
            b.set(w, hashMapOf("balance" to balance - cost, "lastOp" to hashMapOf("kind" to "spend", "ref" to boostRef.id), "updatedAt" to FieldValue.serverTimestamp()))
            b.set(w.collection("tx").document(), hashMapOf("kind" to "spend", "points" to -cost, "note" to note.take(120), "ref" to boostRef.id, "at" to FieldValue.serverTimestamp()))
        }
        b.commit().await()
        return offerRef.id
    }

    // ================= المنتجات والخدمات والمنيو + الطلبات =================

    val catalogConfig: Flow<com.example.data.model.CatalogConfig> = callbackFlow {
        val l = configDoc.addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST") val c = snap?.get("catalog") as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST") val p = c["pricing"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST") val lim = c["limits"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST") val vat = snap?.get("vat") as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST") val types = snap?.get("categoryTypes") as? Map<String, Any?> ?: emptyMap()
            val def = com.example.data.model.CatalogConfig()
            fun price(k: String): com.example.data.model.KindPrice {
                @Suppress("UNCHECKED_CAST") val m = p[k] as? Map<String, Any?> ?: return def.price(k)
                return com.example.data.model.KindPrice((m["priceH"] as? Number)?.toLong() ?: def.price(k).priceH, m["active"] as? Boolean ?: true)
            }
            trySend(com.example.data.model.CatalogConfig(
                enabled = c["enabled"] as? Boolean ?: false,
                orderEnabled = c["orderEnabled"] as? Boolean ?: false,
                review = c["review"] as? Boolean ?: true,
                limits = def.limits + lim.mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toInt() } },
                pricingEnabled = p["enabled"] as? Boolean ?: false,
                pricingEnabledAt = (p["enabledAt"] as? Timestamp)?.toDate()?.time ?: 0L,
                graceUntil = (p["graceUntil"] as? Timestamp)?.toDate()?.time ?: 0L,
                prices = listOf("menu", "product", "service", "all").associateWith { price(it) },
                vatEnabled = vat["enabled"] as? Boolean ?: true,
                vatRate = (vat["rate"] as? Number)?.toDouble() ?: 15.0,
                categoryTypes = types.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
                features = (c["features"] as? Map<*, *>)?.mapNotNull { (k, v) -> (k as? String)?.let { kk -> (v as? Boolean)?.let { kk to it } } }?.toMap() ?: emptyMap(),
                noshow = (c["noshow"] as? Map<*, *>).let { n -> com.example.data.model.NoshowCfg(
                    (n?.get("max") as? Number)?.toInt() ?: 3, (n?.get("days") as? Number)?.toInt() ?: 60, (n?.get("blockDays") as? Number)?.toInt() ?: 7) },
                tracker = (c["tracker"] as? String)?.takeIf { it in listOf("bar", "bubble", "off") } ?: "bar"
            ))
        }
        awaitClose { l.remove() }
    }

    private fun parseItem(d: DocumentSnapshot) = com.example.data.model.CatalogItem(
        id = d.id, merchantId = d.getString("merchantId").orEmpty(), kind = d.getString("kind") ?: "product",
        slot = (d.getLong("slot") ?: 0L).toInt(), name = d.getString("name").orEmpty(), desc = d.getString("desc").orEmpty(),
        price = d.getDouble("price") ?: 0.0, imageUrl = d.getString("imageUrl").orEmpty(), section = d.getString("section").orEmpty(),
        available = d.getBoolean("available") ?: true, approved = d.getBoolean("approved") ?: false, adminNote = d.getString("adminNote").orEmpty(),
        createdAtMillis = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L
    )
    @Suppress("UNCHECKED_CAST")
    private fun parsePass(d: DocumentSnapshot) = com.example.data.model.ItemPass(
        merchantId = d.getString("merchantId").orEmpty(), scope = d.getString("scope").orEmpty(), kind = d.getString("kind").orEmpty(),
        itemIds = (d.get("itemIds") as? List<String>).orEmpty(),
        startsAt = d.getTimestamp("startsAt")?.toDate()?.time ?: 0L, endsAt = d.getTimestamp("endsAt")?.toDate()?.time ?: 0L
    )

    /** منتجات متجر معتمدة + ظهورها المدفوع (لصفحة المتجر) */
    suspend fun storeCatalog(uid: String): Pair<List<com.example.data.model.CatalogItem>, List<com.example.data.model.ItemPass>> {
        val items = db.collection("items").whereEqualTo("merchantId", uid).whereEqualTo("approved", true).get().await().documents.map { parseItem(it) }
        val passes = runCatching { db.collection("itemPasses").whereEqualTo("merchantId", uid).get().await().documents.map { parsePass(it) } }.getOrDefault(emptyList())
        return items to passes
    }
    fun myItems(uid: String): Flow<List<com.example.data.model.CatalogItem>> = callbackFlow {
        val l = db.collection("items").whereEqualTo("merchantId", uid).addSnapshotListener { s, _ -> trySend(s?.documents.orEmpty().map { parseItem(it) }) }
        awaitClose { l.remove() }
    }
    fun myPasses(uid: String): Flow<List<com.example.data.model.ItemPass>> = callbackFlow {
        val l = db.collection("itemPasses").whereEqualTo("merchantId", uid).addSnapshotListener { s, _ -> trySend(s?.documents.orEmpty().map { parsePass(it) }) }
        awaitClose { l.remove() }
    }
    suspend fun saveItem(it: com.example.data.model.CatalogItem, isNew: Boolean, clearNote: Boolean = true) {
        val data = hashMapOf<String, Any?>(
            "name" to it.name.take(80), "price" to it.price, "desc" to it.desc.take(300), "section" to it.section.take(40),
            "imageUrl" to it.imageUrl, "available" to it.available, "approved" to it.approved, "updatedAt" to FieldValue.serverTimestamp()
        )
        if (clearNote) data["adminNote"] = "" // التعديل يمسح ملاحظة الأدمن
        val ref = db.collection("items").document(it.id)
        if (isNew) ref.set(data + mapOf("id" to it.id, "merchantId" to it.merchantId, "kind" to it.kind, "slot" to it.slot.toLong(), "createdAt" to FieldValue.serverTimestamp())).await()
        else ref.update(data).await()
    }
    suspend fun deleteItem(id: String) { db.collection("items").document(id).delete().await() }
    /** 👥 موظف «نفد اليوم»: يغيّر التوفر بس */
    suspend fun setItemAvailable(id: String, available: Boolean) {
        db.collection("items").document(id).update(mapOf("available" to available, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    // ---------------- 👥 موظفين المتجر ----------------
    private fun parseStaff(d: DocumentSnapshot) = com.example.data.model.StoreStaff(
        uid = d.id, storeId = d.getString("storeId").orEmpty(), storeName = d.getString("storeName").orEmpty(),
        code = d.getString("code").orEmpty(), n = (d.getLong("n") ?: 0L).toInt(), name = d.getString("name").orEmpty(),
        role = d.getString("role") ?: "cashier", perms = (d.get("perms") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        active = d.getBoolean("active") ?: false, email = d.getString("email").orEmpty()
    )
    /** ملف الموظف نفسه (null = انحذف) */
    fun staffSelf(uid: String): Flow<com.example.data.model.StoreStaff?> = callbackFlow {
        val l = db.collection("storeStaff").document(uid).addSnapshotListener { s, e ->
            if (e != null) { trySend(null); return@addSnapshotListener }
            trySend(if (s != null && s.exists()) parseStaff(s) else null)
        }
        awaitClose { l.remove() }
    }
    fun storeStaffList(ownerUid: String): Flow<List<com.example.data.model.StoreStaff>> = callbackFlow {
        val l = db.collection("storeStaff").whereEqualTo("storeId", ownerUid).addSnapshotListener { s, _ ->
            trySend(s?.documents.orEmpty().map { parseStaff(it) }.sortedBy { it.n })
        }
        awaitClose { l.remove() }
    }
    /** رقم المتجر (5 أرقام) — ينشأ أول مرة */
    suspend fun ensureStoreCode(uid: String): String {
        val q = db.collection("storeCodes").whereEqualTo("uid", uid).limit(1).get().await()
        q.documents.firstOrNull()?.let { return it.id }
        repeat(8) {
            val code = (10000..99999).random().toString()
            val ok = runCatching { db.collection("storeCodes").document(code).set(mapOf("uid" to uid, "at" to FieldValue.serverTimestamp())).await() }.isSuccess
            if (ok) return code
        }
        throw IllegalStateException("تعذّر إنشاء رقم المتجر، جرّب مرة ثانية")
    }
    /** دخول الموظف: رقم المتجر + رقمه ← الحساب الداخلي */
    suspend fun staffLoginEmail(code: String, n: Int): String? =
        db.collection("staffLogin").document("$code-$n").get().await().takeIf { it.exists() }?.getString("email")

    /** حساب داخلي جديد بدون ما يطلع صاحب المحل من حسابه (تطبيق Firebase ثاني مؤقت) */
    private suspend fun createStaffAccount(context: Context, email: String, pin: String): String {
        val main = com.google.firebase.FirebaseApp.getInstance()
        val name = "staff-" + System.currentTimeMillis()
        val app2 = com.google.firebase.FirebaseApp.initializeApp(context, main.options, name)
        try {
            val a2 = com.google.firebase.auth.FirebaseAuth.getInstance(app2)
            val uid = a2.createUserWithEmailAndPassword(email, pin).await().user?.uid ?: throw IllegalStateException("no uid")
            a2.signOut()
            return uid
        } finally { runCatching { app2.delete() } }
    }
    suspend fun addStaff(context: Context, ownerUid: String, storeName: String, code: String, n: Int, name: String, role: String, perms: List<String>, pin: String,
                         replace: com.example.data.model.StoreStaff? = null) {
        val rnd = (1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val email = "$code-$n-$rnd@${com.example.data.model.StoreStaff.DOMAIN}"
        val uid = createStaffAccount(context, email, pin)
        val wb = db.batch()
        if (replace != null) wb.delete(db.collection("storeStaff").document(replace.uid))
        wb.set(db.collection("storeStaff").document(uid), hashMapOf(
            "storeId" to ownerUid, "storeName" to storeName.take(100), "code" to code, "n" to n.toLong(), "name" to name.take(40),
            "role" to role, "perms" to perms, "active" to (replace?.active ?: true), "createdAt" to FieldValue.serverTimestamp(), "email" to email))
        wb.set(db.collection("staffLogin").document("$code-$n"), hashMapOf("email" to email, "storeId" to ownerUid, "n" to n.toLong()))
        wb.commit().await()
    }
    suspend fun updateStaff(uid: String, data: Map<String, Any>) { db.collection("storeStaff").document(uid).update(data).await() }
    suspend fun deleteStaff(m: com.example.data.model.StoreStaff) {
        val wb = db.batch()
        wb.delete(db.collection("storeStaff").document(m.uid))
        wb.delete(db.collection("staffLogin").document("${m.code}-${m.n}"))
        wb.commit().await()
    }

    /** شراء ظهور المنتجات: باقة كل المنتجات أو منتجات محددة من نوع — مع خصم الرصيد بنفس العملية */
    suspend fun buyItemPass(uid: String, balance: Long, charge: Boolean, scope: String, kind: String, itemIds: List<String>, days: Int, cost: Long, startMillis: Long, note: String) {
        val b = db.batch()
        val ref = db.collection("itemPasses").document()
        val start = maxOf(System.currentTimeMillis(), startMillis)
        b.set(ref, hashMapOf(
            "id" to ref.id, "merchantId" to uid, "scope" to scope, "kind" to kind, "itemIds" to itemIds.take(200), "days" to days.toLong(), "cost" to cost,
            "createdAt" to FieldValue.serverTimestamp(), "startsAt" to Timestamp(java.util.Date(start)), "endsAt" to Timestamp(java.util.Date(start + days * 86_400_000L))
        ))
        if (charge) {
            val w = db.collection("wallets").document(uid)
            b.set(w, hashMapOf("balance" to balance - cost, "lastOp" to hashMapOf("kind" to "catalog", "ref" to ref.id), "updatedAt" to FieldValue.serverTimestamp()))
            b.set(w.collection("tx").document(), hashMapOf("kind" to "spend", "points" to -cost, "note" to note.take(120), "ref" to ref.id, "at" to FieldValue.serverTimestamp()))
        }
        b.commit().await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseOrder(d: DocumentSnapshot) = com.example.data.model.HayynaOrder(
        id = d.id, code = d.getString("code").orEmpty(), storeId = d.getString("storeId").orEmpty(), storeName = d.getString("storeName").orEmpty(),
        branchName = d.getString("branchName").orEmpty(), customerName = d.getString("customerName").orEmpty(), customerPhone = d.getString("customerPhone").orEmpty(),
        items = (d.get("items") as? List<Map<String, Any?>>).orEmpty().map {
            com.example.data.model.CartLine(it["id"] as? String ?: "", it["name"] as? String ?: "", it["kind"] as? String ?: "", (it["price"] as? Number)?.toDouble() ?: 0.0, (it["qty"] as? Number)?.toInt() ?: 1,
                was = (it["was"] as? Number)?.toDouble() ?: 0.0, off = (it["off"] as? Number)?.toDouble() ?: 0.0, bogo = it["bogo"] as? String ?: "")
        },
        discount = d.getDouble("discount") ?: 0.0,
        subtotal = d.getDouble("subtotal") ?: 0.0, deliveryFee = d.getDouble("deliveryFee") ?: 0.0, total = d.getDouble("total") ?: 0.0,
        vatRate = d.getDouble("vatRate") ?: 0.0, vatAmount = d.getDouble("vatAmount") ?: 0.0, vatNumber = d.getString("vatNumber").orEmpty(),
        mode = d.getString("mode") ?: "pickup", note = d.getString("note").orEmpty(), status = d.getString("status") ?: "sent",
        createdAtMillis = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
        etaAtMillis = d.getTimestamp("etaAt")?.toDate()?.time ?: 0L,
        pay = d.getString("pay").orEmpty(), car = d.getString("car").orEmpty(), arrived = d.get("arrivedAt") != null,
        customerUid = d.getString("customerUid").orEmpty(), spot = d.getString("spot").orEmpty(),
        readyAtMillis = (d.getTimestamp("readyAt") ?: d.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))?.toDate()?.time ?: 0L,
        cancelReq = d.getString("cancelReq").orEmpty(), cancelReqAns = d.getString("cancelReqAns").orEmpty(),
        cancelBy = d.getString("cancelBy").orEmpty(), cancelReason = d.getString("cancelReason").orEmpty(),
        lat = (d.get("location") as? Map<String, Any?>)?.get("lat") as? Double, lng = (d.get("location") as? Map<String, Any?>)?.get("lng") as? Double
    )
    fun myOrdersFlow(uid: String): Flow<List<com.example.data.model.HayynaOrder>> = callbackFlow {
        val l = db.collection("orders").whereEqualTo("customerUid", uid).addSnapshotListener { s, e ->
            if (e != null) { Log.w(TAG, "myOrders: ${e.message}"); return@addSnapshotListener }
            trySend(s?.documents.orEmpty().map { parseOrder(it) }.sortedByDescending { it.createdAtMillis })
        }
        awaitClose { l.remove() }
    }
    suspend fun myRatingIds(uid: String): Set<String> = runCatching { db.collection("ratings").whereEqualTo("uid", uid).get().await().documents.map { it.id }.toSet() }.getOrDefault(emptySet())
    suspend fun saveRating(orderId: String, storeId: String, uid: String, name: String, stars: Int, text: String) {
        db.collection("ratings").document(orderId).set(hashMapOf("orderId" to orderId, "storeId" to storeId, "uid" to uid, "name" to name.take(30),
            "stars" to stars.toLong(), "text" to text.take(300), "at" to FieldValue.serverTimestamp())).await()
    }
    /** متوسط التقييم + آخر التعليقات */
    suspend fun storeRatings(uid: String): List<Triple<Int, String, String>> = runCatching {
        db.collection("ratings").whereEqualTo("storeId", uid).get().await().documents
            .sortedByDescending { it.getTimestamp("at")?.toDate()?.time ?: 0L }
            .map { Triple((it.getLong("stars") ?: 0L).toInt(), it.getString("name").orEmpty(), it.getString("text").orEmpty()) }
    }.getOrDefault(emptyList())
    /** طلبات المحل + هل الدفعة من الكاش (أول دفعة من السيرفر هي «الأساس» عشان ما نحسب القديمة «جديدة») */
    fun storeOrders(uid: String): Flow<Pair<List<com.example.data.model.HayynaOrder>, Boolean>> = callbackFlow {
        val l = db.collection("orders").whereEqualTo("storeId", uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, _ ->
            if (s != null) trySend(s.documents.map { parseOrder(it) }.sortedByDescending { it.createdAtMillis } to s.metadata.isFromCache)
        }
        awaitClose { l.remove() }
    }
    suspend fun myOrders(uid: String) = db.collection("orders").whereEqualTo("customerUid", uid).get().await().documents.map { parseOrder(it) }.sortedByDescending { it.createdAtMillis }
    suspend fun markArrived(id: String) {
        db.collection("orders").document(id).update(mapOf("arrivedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun setOrderStatus(id: String, status: String, etaMinutes: Int = 0, extra: Map<String, Any> = emptyMap()) {
        val m = mutableMapOf<String, Any>("status" to status, "updatedAt" to FieldValue.serverTimestamp())
        if (etaMinutes > 0) m["etaAt"] = Timestamp(java.util.Date(System.currentTimeMillis() + etaMinutes * 60_000L))
        if (status == "ready") m["readyAt"] = FieldValue.serverTimestamp()
        m.putAll(extra)
        db.collection("orders").document(id).update(m).await()
    }
    /** 🙋 العميل يطلب الإلغاء (بعد ما يستلمه المحل) */
    suspend fun requestCancel(id: String, reason: String) {
        db.collection("orders").document(id).update(mapOf("cancelReq" to reason.take(200), "cancelReqAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    /** 🚗 وصف السيارة + وين واقف */
    suspend fun updateCurb(id: String, car: String, spot: String) {
        db.collection("orders").document(id).update(mapOf("car" to car.take(60), "spot" to spot.take(60), "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun custStats(uid: String): com.example.data.model.CustStats? = runCatching {
        val d = db.collection("custStats").document(uid).get().await()
        if (!d.exists()) null else com.example.data.model.CustStats((d.getLong("count") ?: 0L).toInt(),
            d.getTimestamp("windowStart")?.toDate()?.time ?: 0L, d.getTimestamp("blockedAt")?.toDate()?.time ?: 0L)
    }.getOrNull()
    /** 🚫 ما انستلم: الطلب + سجل + عدّاد العميل في عملية وحدة. يرجّع العدد الجديد */
    suspend fun markNoshow(o: com.example.data.model.HayynaOrder, storeUid: String, cfg: com.example.data.model.NoshowCfg): Int {
        val ref = db.collection("custStats").document(o.customerUid)
        val cur = ref.get().await()
        val now = System.currentTimeMillis()
        val ws = cur.getTimestamp("windowStart")
        val fresh = !cur.exists() || ws == null || ws.toDate().time <= now - cfg.days * 86_400_000L + 60_000L
        val count = if (fresh) 1 else ((cur.getLong("count") ?: 0L) + 1).toInt()
        val stats = hashMapOf<String, Any>("count" to count.toLong(), "windowStart" to (if (fresh) FieldValue.serverTimestamp() else ws!!), "lastOrder" to o.id, "updatedAt" to FieldValue.serverTimestamp())
        if (count >= cfg.max) stats["blockedAt"] = FieldValue.serverTimestamp() else cur.getTimestamp("blockedAt")?.let { stats["blockedAt"] = it }
        val b = db.batch()
        b.update(db.collection("orders").document(o.id), mapOf("status" to "noshow", "updatedAt" to FieldValue.serverTimestamp()))
        b.set(db.collection("noshows").document(o.id), hashMapOf("orderId" to o.id, "customerUid" to o.customerUid, "storeId" to storeUid, "at" to FieldValue.serverTimestamp()))
        b.set(ref, stats)
        b.commit().await()
        return count
    }
    suspend fun saveOrder(o: com.example.data.model.HayynaOrder, customerUid: String, branchId: String) {
        val ref = db.collection("orders").document()
        ref.set(hashMapOf(
            "id" to ref.id, "code" to o.code, "storeId" to o.storeId, "storeName" to o.storeName, "branchId" to branchId, "branchName" to o.branchName,
            "customerUid" to customerUid, "customerName" to o.customerName, "customerPhone" to o.customerPhone,
            "items" to o.items.map { l ->
                mutableMapOf<String, Any>("id" to l.id, "name" to l.name, "kind" to l.kind, "qty" to l.qty.toLong(), "price" to l.price).apply {
                    if (l.was > l.price) put("was", l.was)
                    if (l.off > 0) { put("off", l.off); put("bogo", l.bogo) }
                }
            },
            "subtotal" to o.subtotal, "discount" to o.discount, "deliveryFee" to o.deliveryFee, "total" to o.total, "vatRate" to o.vatRate, "vatAmount" to o.vatAmount, "vatNumber" to o.vatNumber,
            "mode" to o.mode, "location" to (if (o.lat != null && o.lng != null) mapOf("lat" to o.lat, "lng" to o.lng) else null),
            "pay" to o.pay, "car" to o.car.take(60),
            "note" to o.note.take(300), "status" to "sent", "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    // ⚡ الخصم السريع على المنيو
    suspend fun quickDeal(uid: String): com.example.data.model.QuickDeal? = runCatching {
        val d = db.collection("quickDeals").document(uid).get().await()
        if (!d.exists()) null else com.example.data.model.QuickDeal(
            (d.getLong("pct") ?: 0L).toInt(), d.getString("scope") ?: "all", d.getString("kind").orEmpty(), d.getString("section").orEmpty(),
            (d.get("itemIds") as? List<*>).orEmpty().mapNotNull { it as? String }, d.getTimestamp("endsAt")?.toDate()?.time ?: 0L)
    }.getOrNull()
    suspend fun saveQuickDeal(uid: String, q: com.example.data.model.QuickDeal) {
        db.collection("quickDeals").document(uid).set(hashMapOf(
            "merchantId" to uid, "pct" to q.pct.toLong(), "scope" to q.scope, "kind" to q.kind, "section" to q.section, "itemIds" to q.itemIds.take(50),
            "endsAt" to Timestamp(java.util.Date(q.endsAt)), "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }
    suspend fun deleteQuickDeal(uid: String) { db.collection("quickDeals").document(uid).delete().await() }

    suspend fun insertOffer(offer: RestaurantOffer): String {
        val docRef = if (offer.id.isNotBlank()) offersCollection.document(offer.id) else offersCollection.document()
        docRef.set(offer.toFirestoreMap(docRef.id, FieldValue.serverTimestamp())).await()
        return docRef.id
    }

    suspend fun updateOffer(offer: RestaurantOffer) {
        require(offer.id.isNotBlank()) { "Offer ID must not be blank" }
        val docRef = offersCollection.document(offer.id)
        // التعديل ما يلمس العدّادات والتمييز والإنشاء: نسختها المحلية ممكن تكون قديمة (أو «مميز» محسوب من الخدمات المدفوعة)
        // والقواعد ترفض أي تغيير فيها → «رفض Firestore حفظ العرض (صلاحيات)»
        val m = offer.toFirestoreMap(docRef.id, null).apply {
            listOf("merchantId", "isFeatured", "viewsCount", "directionsCount", "whatsappCount", "reportsCount", "createdAt", "rating").forEach { remove(it) }
        }
        @Suppress("UNCHECKED_CAST")
        docRef.update(m as Map<String, Any>).await() // update يستبدل الحقول كاملة (deal ما يندمج مع القديم)
    }

    suspend fun deleteOfferById(id: String) {
        offersCollection.document(id).delete().await()
    }

    suspend fun setOfferApproved(id: String, approved: Boolean) {
        offersCollection.document(id).update("isApproved", approved).await()
    }

    /** سجل عمليات الإدارة (نفس سجل لوحة الويب). */
    suspend fun logAdmin(uid: String, email: String, action: String, area: String, targetType: String, targetId: String, targetName: String, details: String, thumb: String) {
        db.collection("adminLog").add(
            hashMapOf(
                "at" to FieldValue.serverTimestamp(), "actor" to email, "actorUid" to uid,
                "action" to action.take(60), "area" to area, "targetType" to targetType, "targetId" to targetId.take(120),
                "targetName" to targetName.take(200), "details" to details.take(1500), "thumb" to thumb.take(500), "source" to "app"
            )
        ).await()
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

    suspend fun getMerchantProfile(uid: String): MerchantProfile? {
        val d = merchantsCollection.document(uid).get().await()
        if (!d.exists()) return null
        // لو فيه حقل نوعه غريب (من الويب مثلاً) ما نخلي الملف كله يطيح: نقرأه يدوي
        return runCatching { d.toObject(MerchantProfile::class.java) }.getOrNull() ?: runCatching {
            fun n(k: String) = (d.get(k) as? Number)?.toDouble() ?: 0.0
            fun i(k: String, def: Int = -1) = (d.get(k) as? Number)?.toInt() ?: def
            fun t(k: String) = d.getString(k).orEmpty()
            fun b(k: String, def: Boolean) = d.getBoolean(k) ?: def
            val br = (d.get("branches") as? List<*>).orEmpty().mapNotNull { m ->
                (m as? Map<*, *>)?.let {
                    fun bn(k: String) = (it[k] as? Number)?.toDouble() ?: 0.0
                    fun bi(k: String) = (it[k] as? Number)?.toInt() ?: -1
                    fun bt(k: String) = it[k]?.toString().orEmpty()
                    com.example.data.model.Branch(id = bt("id"), name = bt("name"), latitude = bn("latitude"), longitude = bn("longitude"),
                        region = bt("region"), district = bt("district"), phone = bt("phone"), openMinute = bi("openMinute"), closeMinute = bi("closeMinute"), landmark = bt("landmark"))
                }
            }
            MerchantProfile(restaurantName = t("restaurantName"), phone = t("phone"), latitude = n("latitude"), longitude = n("longitude"),
                region = t("region"), district = t("district"), cuisineType = t("cuisineType"), openMinute = i("openMinute"), closeMinute = i("closeMinute"),
                logoUrl = t("logoUrl"), branches = br, coverUrl = t("coverUrl"), about = t("about"), priceFrom = n("priceFrom"),
                menuImages = (d.get("menuImages") as? List<*>).orEmpty().mapNotNull { it as? String },
                delivery = b("delivery", false), deliveryFee = n("deliveryFee"), deliveryMinOrder = n("deliveryMinOrder"), deliveryFreeOver = n("deliveryFreeOver"),
                vatNumber = t("vatNumber"), curbside = b("curbside", false), payCash = b("payCash", true), payCard = b("payCard", true))
        }.onFailure { android.util.Log.w("Hayyna", "merchant profile parse failed", it) }.getOrNull()
    }

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
                "deliveryFreeOver" to profile.deliveryFreeOver,
                "vatNumber" to profile.vatNumber,
                "curbside" to profile.curbside, "payCash" to profile.payCash, "payCard" to profile.payCard,
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
                deliveryFreeOver = profile.deliveryFreeOver,
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

data class CreditsConfig(
    val items: Map<String, CreditItem> = CreditItem.DEFAULTS,
    val welcome: Long = 0L,
    val spotlightMax: Int = 3,
    // 🎁 مكافأة الحصري: خصم على «النشر» بس للعروض الحصرية (100 = مجاني)
    val rewardOn: Boolean = false,
    val rewardPercent: Int = 0,
    val enabled: Boolean = false,
    val enabledAtMillis: Long = 0L,
    // العروض المنشورة قبل التشغيل مجانية لين هالوقت
    val graceUntilMillis: Long = Long.MAX_VALUE,
    val exclusiveEnabled: Boolean = true,
    val exclusiveLabel: String = "حصري في لكوفر",
    val exclusiveIcon: String = "💎"
)

data class Boost(val offerId: String, val effect: String, val startsAt: Long, val endsAt: Long, val merchantId: String = "")

/** خدمة مدفوعة (من إعدادات الأدمن): effect = publish / feature / listTop / spotlight */
data class CreditItem(
    val id: String, val name: String, val effect: String, val unit: String, val price: Long, val days: Int,
    val active: Boolean, val order: Int = 0
) {
    fun baseCost(d: Int): Long = if (unit == "day") price * d else price
    fun daysFor(d: Int): Int = if (unit == "day") d else days
    companion object {
        val DEFAULTS = listOf(
            CreditItem("publish", "📢 نشر عرض", "publish", "day", 3, 1, true, 0),
            CreditItem("feature", "✨ تمييز 3 أيام", "feature", "fixed", 10, 3, true, 1),
            CreditItem("boost", "🚀 عرض سوبر أسبوع", "spotlight", "fixed", 25, 7, true, 2),
            CreditItem("listtop", "📌 إعلان بارز في صفحة العروض", "listTop", "fixed", 40, 7, false, 3)
        ).associateBy { it.id }
    }
}

data class PoiConfig(
    val showGooglePoi: Boolean = true,
    val inviteEnabled: Boolean = true,
    val inviteMessage: String = DEFAULT_MESSAGE,
    val appLink: String = ""
) {
    companion object {
        const val DEFAULT_MESSAGE =
            "السلام عليكم 👋 شفت محلكم \"{name}\" على خريطة لكوفر. سجّلوا محلكم مجاناً وانشروا عروضكم لأهل الحي، وخلّوا الزباين يوصلون لكم بسهولة 📍"
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
