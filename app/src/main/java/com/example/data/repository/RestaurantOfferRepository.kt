package com.example.data.repository

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

        fun getFallbackSeedOffers(): List<RestaurantOffer> {
            return listOf(
                RestaurantOffer(
                    id = "offer_arabica_morning",
                    merchantId = "merchant_default_riyadh",
                    restaurantName = "مقهى أرابيكا باثفايندر (Arabica Coffee)",
                    offerTitle = "عرض الصباح: فلات وايت بحليب الشوفان + كرواسون زبدة فرنسي فاخر",
                    description = "استمتع بمذاق حبوب البن المختصة المحمصة طازجاً مع لاتيه آرت متقن وكرواسون زبدة فرنسي مقرمش يذوب بالفم في أجواء هادئة وأنيقة.",
                    cuisineType = "مقاهي وحلويات ☕",
                    price = 28.0,
                    originalPrice = 42.0,
                    discountBadge = "عرض الصباح ☕",
                    mediaType = MediaType.IMAGE,
                    mediaUri = "cafe_arabica_offer_1791863237123",
                    isPromotionalVideo = false,
                    district = "حي الملقا",
                    region = "منطقة الرياض",
                    latitude = 24.8142,
                    longitude = 46.6025,
                    phone = "+966551122334",
                    workingHours = "يومياً من 6:30 ص حتى 12:00 م",
                    rating = 4.9,
                    isApproved = true,
                    isFeatured = true,
                    viewsCount = 1240
                ),
                RestaurantOffer(
                    id = "offer_burger_lounge",
                    merchantId = "merchant_default_riyadh",
                    restaurantName = "برجر لاونج الفاخر (Burger Lounge)",
                    offerTitle = "عرض وجبة الويكند: دبل واغيو برجر + بطاطس مقرمشة ومشروب مجاناً",
                    description = "استمتع بألذ برجر واغيو مشوي على اللهب مع جبن الشيدر الذائب والصلصة الخاصة، مع بطاطس مقلية ذهبية ومشروب منعش كبير.",
                    cuisineType = "برجر وساندوتش 🍔",
                    price = 45.0,
                    originalPrice = 68.0,
                    discountBadge = "خصم 34% 🔥",
                    mediaType = MediaType.IMAGE,
                    mediaUri = "restaurant_burger_offer_1791823697805",
                    isPromotionalVideo = false,
                    district = "حي الملقا",
                    region = "منطقة الرياض",
                    latitude = 24.8185,
                    longitude = 46.6060,
                    phone = "+966552233445",
                    workingHours = "يومياً من 1:00 م حتى 2:00 ص",
                    rating = 4.8,
                    isApproved = true,
                    isFeatured = true,
                    viewsCount = 980
                ),
                RestaurantOffer(
                    id = "offer_pizza_bella",
                    merchantId = "merchant_default_riyadh",
                    restaurantName = "بيتزا نابولي فيرونا (Bella Napoli)",
                    offerTitle = "عرض الحجم العائلي: 2 بيتزا نابوليتانا حطب + مقبلات وثومية",
                    description = "عجينة مخمرة لـ 48 ساعة مخبوزة على الحطب الإيطالي مع صلصة طماطم سان مارزانو وجبن الموزاريلا الطازج.",
                    cuisineType = "بيتزا ومعجنات 🍕",
                    price = 69.0,
                    originalPrice = 110.0,
                    discountBadge = "وفر 41 ر.س ✨",
                    mediaType = MediaType.IMAGE,
                    mediaUri = "restaurant_pizza_offer_1791823722379",
                    isPromotionalVideo = false,
                    district = "حي النخيل",
                    region = "منطقة الرياض",
                    latitude = 24.7431,
                    longitude = 46.6342,
                    phone = "+966553344556",
                    workingHours = "يومياً من 12:00 م حتى 1:30 ص",
                    rating = 4.7,
                    isApproved = true,
                    isFeatured = false,
                    viewsCount = 750
                ),
                RestaurantOffer(
                    id = "offer_grill_palace",
                    merchantId = "merchant_default_riyadh",
                    restaurantName = "مشاوي قصر الشام (Sham Palace Grill)",
                    offerTitle = "كيلو كباب وكفتة مشوية على الفحم + حمص وتبولة ومشروب",
                    description = "لحوم نعيمي طازجة يومياً متبلة بخلطة بهارات شامية خاصة ومشوية بعناية على الفحم الطبيعي مع خبز ساخن من الفرن.",
                    cuisineType = "مشاوي شرقية 🥩",
                    price = 119.0,
                    originalPrice = 165.0,
                    discountBadge = "خصم 28% 🍖",
                    mediaType = MediaType.IMAGE,
                    mediaUri = "restaurant_grill_offer_1791823746852",
                    isPromotionalVideo = false,
                    district = "حي العليا",
                    region = "منطقة الرياض",
                    latitude = 24.7085,
                    longitude = 46.6780,
                    phone = "+966554455667",
                    workingHours = "يومياً من 11:30 ص حتى 2:00 ص",
                    rating = 4.9,
                    isApproved = true,
                    isFeatured = true,
                    viewsCount = 1820
                )
            )
        }
    }

    // Secondary constructor resolving custom database ID from firebase_applet_config.xml
    constructor(context: Context) : this(resolveFirestore(context))

    private val offersCollection = db.collection("offers")

    val allOffers: Flow<List<RestaurantOffer>> = callbackFlow {
        // Emit fallback seed offers immediately so UI is never empty
        trySend(getFallbackSeedOffers())

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

                    if (offers.isNotEmpty()) {
                        trySend(offers)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach snapshot listener to offers: ${e.message}")
            null
        }

        awaitClose { listener?.remove() }
    }

    val approvedOffers: Flow<List<RestaurantOffer>> = callbackFlow {
        // Emit fallback seed offers immediately so UI is never empty
        trySend(getFallbackSeedOffers().filter { it.isApproved })

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

                    if (offers.isNotEmpty()) {
                        trySend(offers)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach snapshot listener to approved offers: ${e.message}")
            null
        }

        awaitClose { listener?.remove() }
    }

    suspend fun insertOffer(offer: RestaurantOffer): String {
        val docRef = if (offer.id.isNotBlank()) {
            offersCollection.document(offer.id)
        } else {
            offersCollection.document()
        }
        val offerData = hashMapOf<String, Any?>(
            "id" to docRef.id,
            "merchantId" to offer.merchantId,
            "restaurantName" to offer.restaurantName,
            "offerTitle" to offer.offerTitle,
            "description" to offer.description,
            "cuisineType" to offer.cuisineType,
            "price" to offer.price,
            "originalPrice" to offer.originalPrice,
            "currency" to offer.currency,
            "discountBadge" to offer.discountBadge,
            "mediaType" to offer.mediaType.name,
            "mediaUri" to offer.mediaUri,
            "isPromotionalVideo" to offer.isPromotionalVideo,
            "district" to offer.district,
            "region" to offer.region,
            "latitude" to offer.latitude,
            "longitude" to offer.longitude,
            "phone" to offer.phone,
            "workingHours" to offer.workingHours,
            "rating" to offer.rating,
            "isApproved" to offer.isApproved,
            "isFeatured" to offer.isFeatured,
            "viewsCount" to offer.viewsCount,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(offerData).await()
        return docRef.id
    }

    suspend fun updateOffer(offer: RestaurantOffer) {
        require(offer.id.isNotBlank()) { "Offer ID must not be blank" }
        val docRef = offersCollection.document(offer.id)
        val offerData = hashMapOf<String, Any?>(
            "id" to docRef.id,
            "merchantId" to offer.merchantId,
            "restaurantName" to offer.restaurantName,
            "offerTitle" to offer.offerTitle,
            "description" to offer.description,
            "cuisineType" to offer.cuisineType,
            "price" to offer.price,
            "originalPrice" to offer.originalPrice,
            "currency" to offer.currency,
            "discountBadge" to offer.discountBadge,
            "mediaType" to offer.mediaType.name,
            "mediaUri" to offer.mediaUri,
            "isPromotionalVideo" to offer.isPromotionalVideo,
            "district" to offer.district,
            "region" to offer.region,
            "latitude" to offer.latitude,
            "longitude" to offer.longitude,
            "phone" to offer.phone,
            "workingHours" to offer.workingHours,
            "rating" to offer.rating,
            "isApproved" to offer.isApproved,
            "isFeatured" to offer.isFeatured,
            "viewsCount" to offer.viewsCount,
            "createdAt" to (offer.createdAt ?: FieldValue.serverTimestamp())
        )
        docRef.set(offerData).await()
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

    suspend fun incrementViews(id: String) {
        offersCollection.document(id).update("viewsCount", FieldValue.increment(1)).await()
    }

    suspend fun seedInitialOffersIfEmpty(merchantId: String) {
        try {
            val snapshot = offersCollection.limit(1).get().await()
            if (snapshot.isEmpty) {
                val seedOffers = listOf(
                    RestaurantOffer(
                        id = "offer_arabica_morning",
                        merchantId = merchantId,
                        restaurantName = "مقهى أرابيكا باثفايندر (Arabica Coffee)",
                        offerTitle = "عرض الصباح: فلات وايت بحليب الشوفان + كرواسون زبدة فرنسي فاخر",
                        description = "استمتع بمذاق حبوب البن المختصة المحمصة طازجاً مع لاتيه آرت متقن وكرواسون زبدة فرنسي مقرمش يذوب بالفم في أجواء هادئة وأنيقة.",
                        cuisineType = "مقاهي وحلويات ☕",
                        price = 28.0,
                        originalPrice = 42.0,
                        discountBadge = "عرض الصباح ☕",
                        mediaType = MediaType.IMAGE,
                        mediaUri = "cafe_arabica_offer_1791863237123",
                        isPromotionalVideo = false,
                        district = "حي الملقا",
                        region = "منطقة الرياض",
                        latitude = 24.8142,
                        longitude = 46.6025,
                        phone = "+966551122334",
                        workingHours = "يومياً من 6:30 ص حتى 12:00 م",
                        rating = 4.9,
                        isApproved = true,
                        isFeatured = true,
                        viewsCount = 1240
                    ),
                    RestaurantOffer(
                        id = "offer_burger_lounge",
                        merchantId = merchantId,
                        restaurantName = "برجر لاونج الفاخر (Burger Lounge)",
                        offerTitle = "عرض وجبة الويكند: دبل واغيو برجر + بطاطس مقرمشة ومشروب مجاناً",
                        description = "استمتع بألذ برجر واغيو مشوي على اللهب مع جبن الشيدر الذائب والصلصة الخاصة، مع بطاطس مقلية ذهبية ومشروب منعش كبير.",
                        cuisineType = "برجر وساندوتش 🍔",
                        price = 45.0,
                        originalPrice = 68.0,
                        discountBadge = "خصم 34% 🔥",
                        mediaType = MediaType.IMAGE,
                        mediaUri = "restaurant_burger_offer_1791823697805",
                        isPromotionalVideo = false,
                        district = "حي الملقا",
                        region = "منطقة الرياض",
                        latitude = 24.8185,
                        longitude = 46.6060,
                        phone = "+966552233445",
                        workingHours = "يومياً من 1:00 م حتى 2:00 ص",
                        rating = 4.8,
                        isApproved = true,
                        isFeatured = true,
                        viewsCount = 980
                    ),
                    RestaurantOffer(
                        id = "offer_pizza_bella",
                        merchantId = merchantId,
                        restaurantName = "بيتزا نابولي فيرونا (Bella Napoli)",
                        offerTitle = "عرض الحجم العائلي: 2 بيتزا نابوليتانا حطب + مقبلات وثومية",
                        description = "عجينة مخمرة لـ 48 ساعة مخبوزة على الحطب الإيطالي مع صلصة طماطم سان مارزانو وجبن الموزاريلا الطازج.",
                        cuisineType = "بيتزا ومعجنات 🍕",
                        price = 69.0,
                        originalPrice = 110.0,
                        discountBadge = "وفر 41 ر.س ✨",
                        mediaType = MediaType.IMAGE,
                        mediaUri = "restaurant_pizza_offer_1791823722379",
                        isPromotionalVideo = false,
                        district = "حي النخيل",
                        region = "منطقة الرياض",
                        latitude = 24.7431,
                        longitude = 46.6342,
                        phone = "+966553344556",
                        workingHours = "يومياً من 12:00 م حتى 1:30 ص",
                        rating = 4.7,
                        isApproved = true,
                        isFeatured = false,
                        viewsCount = 750
                    ),
                    RestaurantOffer(
                        id = "offer_grill_palace",
                        merchantId = merchantId,
                        restaurantName = "مشاوي قصر الشام (Sham Palace Grill)",
                        offerTitle = "كيلو كباب وكفتة مشوية على الفحم + حمص وتبولة ومشروب",
                        description = "لحوم نعيمي طازجة يومياً متبلة بخلطة بهارات شامية خاصة ومشوية بعناية على الفحم الطبيعي مع خبز ساخن من الفرن.",
                        cuisineType = "مشاوي شرقية 🥩",
                        price = 119.0,
                        originalPrice = 165.0,
                        discountBadge = "خصم 28% 🍖",
                        mediaType = MediaType.IMAGE,
                        mediaUri = "restaurant_grill_offer_1791823746852",
                        isPromotionalVideo = false,
                        district = "حي العليا",
                        region = "منطقة الرياض",
                        latitude = 24.7085,
                        longitude = 46.6780,
                        phone = "+966554455667",
                        workingHours = "يومياً من 11:30 ص حتى 2:00 ص",
                        rating = 4.9,
                        isApproved = true,
                        isFeatured = true,
                        viewsCount = 1820
                    )
                )
                for (offer in seedOffers) {
                    insertOffer(offer)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Seed offers check ignored: ${e.message}")
        }
    }
}
