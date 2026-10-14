package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MediaType {
    IMAGE,
    VIDEO
}

@IgnoreExtraProperties
data class RestaurantOffer(
    @DocumentId
    val id: String = "",
    val merchantId: String = "",
    val restaurantName: String = "",
    val offerTitle: String = "",
    val description: String = "",
    val cuisineType: String = "",
    val price: Double = 0.0,
    val originalPrice: Double? = null,
    val currency: String = "ر.س",
    val discountBadge: String = "",
    val mediaType: MediaType = MediaType.IMAGE,
    val mediaUri: String = "",
    val isPromotionalVideo: Boolean = false,
    val district: String = "",
    val region: String = "",
    val latitude: Double = 24.7136,
    val longitude: Double = 46.6753,
    val phone: String = "+966501234567",
    val workingHours: String = "يومياً من 12:00 م حتى 1:30 ص",
    val rating: Double = 4.8,
    val distanceKm: Double = 0.0,
    val driveTimeMinutes: Int = 0,
    val isApproved: Boolean = true,
    val isFeatured: Boolean = false,
    val viewsCount: Int = 1,
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
