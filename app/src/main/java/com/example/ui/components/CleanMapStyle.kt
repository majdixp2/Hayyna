package com.example.ui.components

import com.google.android.gms.maps.model.MapStyleOptions

/**
 * خريطة "نظيفة": تخفي كل المحلات والمعالم والمواصلات من Google،
 * وتبقي الشوارع وأسماء الأحياء بألوان هادئة، عشان تبرز عروض التطبيق فقط.
 */
object CleanMapStyle {
    private const val JSON = """
[
  { "featureType": "poi", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "poi.park", "elementType": "geometry", "stylers": [ { "visibility": "on" }, { "color": "#E3EDE4" } ] },
  { "featureType": "transit", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "road", "elementType": "labels.icon", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "landscape", "elementType": "geometry", "stylers": [ { "color": "#F5F3EF" } ] },
  { "featureType": "road", "elementType": "geometry", "stylers": [ { "color": "#FFFFFF" } ] },
  { "featureType": "road.highway", "elementType": "geometry", "stylers": [ { "color": "#F3E3C9" } ] },
  { "featureType": "road", "elementType": "labels.text.fill", "stylers": [ { "color": "#9A9A9A" } ] },
  { "featureType": "administrative.neighborhood", "elementType": "labels.text.fill", "stylers": [ { "color": "#7C8A80" } ] },
  { "featureType": "administrative.land_parcel", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "water", "elementType": "geometry", "stylers": [ { "color": "#D3E3EA" } ] },
  { "featureType": "water", "elementType": "labels", "stylers": [ { "visibility": "off" } ] }
]
"""

    val options: MapStyleOptions by lazy { MapStyleOptions(JSON) }
}
