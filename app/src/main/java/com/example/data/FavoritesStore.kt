package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** المفضلة محفوظة على الجوال نفسه (بدون حساب وبدون سيرفر). */
class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hayyna_favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun toggle(offerId: String) {
        val next = _ids.value.toMutableSet().apply { if (!add(offerId)) remove(offerId) }
        _ids.value = next
        prefs.edit().putStringSet(KEY, next).apply()
    }

    private companion object {
        const val KEY = "offer_ids"
    }
}
