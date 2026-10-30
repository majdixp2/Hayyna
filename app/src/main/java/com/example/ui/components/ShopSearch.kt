package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.StoreProfile
import com.example.ui.MerchantViewModel

private val Navy = Color(0xFF10318F)

/** المحلات المسجّلة اللي تطابق البحث. */
fun storeHits(query: String, stores: List<StoreProfile>): List<StoreProfile> {
    val q = query.trim().lowercase()
    if (q.length < 2) return emptyList()
    return stores.filter { st ->
        (listOf(st.name, st.category) + st.branches.flatMap { listOf(it.name, it.district, it.landmark) })
            .any { it.lowercase().contains(q) }
    }.sortedByDescending { it.name.lowercase().contains(q) }.take(10)
}

/**
 * 🏪 البحث عن محل: المسجّلة في لكوفر (صف) + زر «دوّر في محلات Google» للي مو مسجّلة،
 * والضغط على محل Google يفتح بطاقته (ادعه / أنا صاحب المحل / 📷 ارفع عرض للفريق).
 */
@Composable
fun ShopSearchSection(vm: MerchantViewModel, query: String, stores: List<StoreProfile>, horizontalPadding: Int = 16) {
    val q = query.trim()
    if (q.length < 2) return
    val google by vm.googleShops.collectAsStateWithLifecycle()
    val searching by vm.searchingShops.collectAsStateWithLifecycle()
    val hits = storeHits(q, stores)
    val pad = horizontalPadding.dp
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("🏪 محلات", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = pad))
        LazyRow(contentPadding = PaddingValues(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.uid }) { st ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { vm.openStorePage(st) }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (st.logoUrl.isNotBlank()) AsyncImage(model = st.logoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(24.dp).clip(CircleShape)) else Text("🏪")
                        Spacer(Modifier.width(6.dp))
                        Text(st.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            item(key = "google") {
                Surface(shape = RoundedCornerShape(20.dp), color = Navy, modifier = Modifier.clickable(enabled = !searching) { vm.searchGoogleShops(q) }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (searching) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp) else Text("🔎", fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("«${q.take(20)}» في محلات Google", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
        google?.let { list ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (list.isEmpty()) Text("ما لقينا محلات بهالاسم في Google. جرّب اسم ثاني أو أضف الحي.", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    list.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.openGoogleShop(f) }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍", fontSize = 16.sp)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(f.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                if (f.address.isNotBlank()) Text(f.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text("›", fontSize = 18.sp, color = Navy)
                        }
                    }
                    Text("المعلومات من خرائط Google", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}
