package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserLocationManager
import com.example.data.model.LocationPresets
import com.example.data.model.MediaType
import com.example.data.model.RestaurantOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun `read app name string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("سوق التاجر", appName)
    }

    @Test
    fun `calculate real distance between coordinates using UserLocationManager`() {
        // Riyadh Malqa to Olaya coordinates
        val lat1 = 24.8142
        val lon1 = 46.6025
        val lat2 = 24.7085
        val lon2 = 46.6780

        val distanceKm = UserLocationManager.calculateDistanceKm(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be positive and realistic", distanceKm > 10.0 && distanceKm < 20.0)

        val driveMinutes = UserLocationManager.calculateDriveTimeMinutes(distanceKm)
        assertTrue("Drive time should be estimated realistically", driveMinutes > 15)
    }

    @Test
    fun `create and validate restaurant offer model with real coordinates and discount badge`() {
        val offer = RestaurantOffer(
            id = "offer_test_123",
            merchantId = "merchant_user_abc",
            restaurantName = "برجر لاونج",
            offerTitle = "عرض دبل واغيو برجر + بطاطس ومشروب مجاناً",
            description = "وجبة برجر لذيذة مع مشروب وبطاطس",
            cuisineType = "برجر وساندوتش 🍔",
            price = 45.0,
            originalPrice = 65.0,
            currency = "ر.س",
            discountBadge = "خصم 30%",
            mediaType = MediaType.VIDEO,
            mediaUri = "https://firebasestorage.googleapis.com/v0/b/app/promo.mp4",
            isPromotionalVideo = true,
            district = "حي الملقا",
            region = "منطقة الرياض",
            latitude = 24.8142,
            longitude = 46.6025,
            phone = "+966551122334"
        )

        assertEquals("offer_test_123", offer.id)
        assertEquals("merchant_user_abc", offer.merchantId)
        assertEquals("برجر لاونج", offer.restaurantName)
        assertEquals("حي الملقا", offer.district)
        assertEquals(24.8142, offer.latitude, 0.0001)
        assertEquals(46.6025, offer.longitude, 0.0001)
        assertEquals(true, offer.isPromotionalVideo)
        assertEquals(MediaType.VIDEO, offer.mediaType)
    }

    @Test
    fun `verify location presets have valid regions and coordinates`() {
        assertTrue(LocationPresets.regions.isNotEmpty())
        val riyadh = LocationPresets.regions.first()
        assertEquals("منطقة الرياض", riyadh.name)
        assertTrue(riyadh.districts.isNotEmpty())
        val firstDistrict = riyadh.districts.first()
        assertTrue(firstDistrict.latitude in 24.0..25.5)
        assertTrue(firstDistrict.longitude in 46.0..47.5)
    }

    @Test
    fun `test activity launch without crashing`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }
}
