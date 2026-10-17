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
                    name = snap?.getString("name").orEmpty()
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
                    name = d.getString("name").orEmpty()
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
    }
}

data class MerchantStatus(
    val uid: String,
    val trusted: Boolean = false,
    val suspended: Boolean = false,
    val name: String = ""
)

data class OfferReport(
    val id: String,
    val offerId: String,
    val offerTitle: String,
    val restaurantName: String,
    val reason: String,
    val createdAtMillis: Long
)
