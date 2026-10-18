package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** المفضلة: محفوظة على الجوال، وتتزامن مع حساب العميل لما يسجّل دخول. */
class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hayyna_favorites", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** يُستدعى بعد كل تغيير (للمزامنة مع الحساب). */
    var onChanged: ((Set<String>) -> Unit)? = null

    fun toggle(offerId: String) {
        val next = _ids.value.toMutableSet().apply { if (!add(offerId)) remove(offerId) }
        setAll(next)
        onChanged?.invoke(next)
    }

    fun setAll(ids: Set<String>) {
        _ids.value = ids
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    private companion object {
        const val KEY = "offer_ids"
    }
}
