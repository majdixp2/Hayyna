package com.example.data.model

import androidx.compose.ui.graphics.Color

// المنتجات والخدمات والمنيو + الطلب كملخص على واتساب + الضريبة (نفس منطق الويب: web/js/catalog.js)

enum class ItemKind(val id: String, val label: String, val icon: String, val title: String, val add: String, val color: Color, val bg: Color) {
    MENU("menu", "منيو", "🍽️", "المنيو", "صنف في المنيو", Color(0xFFC2410C), Color(0xFFFFF1E3)),
    PRODUCT("product", "منتج", "🛍️", "منتجاتنا", "منتج", Color(0xFF1D4ED8), Color(0xFFE8F0FE)),
    SERVICE("service", "خدمة", "✂️", "خدماتنا", "خدمة", Color(0xFF6D28D9), Color(0xFFF1EAFE));

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: PRODUCT
        /** النوع الأساسي من اسم التصنيف (لو الأدمن ما حدده) */
        fun guess(category: String): ItemKind = when {
            Regex("مطاعم|مطعم|مقاهي|مقهى|حلويات|مخبز|مخابز|كافيه").containsMatchIn(category) -> MENU
            Regex("خياط|مشاغل|حلاق|صالون|خدمات|صيانة|نادي|رياض|مغسل|تنظيف|تعليم|تدريب|عيادة|سباك|كهرب").containsMatchIn(category) -> SERVICE
            else -> PRODUCT
        }
        fun order(base: ItemKind) = listOf(base) + entries.filter { it != base }
    }
}

data class CatalogItem(
    val id: String = "", val merchantId: String = "", val kind: String = "product", val slot: Int = 0,
    val name: String = "", val desc: String = "", val price: Double = 0.0, val imageUrl: String = "", val section: String = "",
    val available: Boolean = true, val approved: Boolean = false, val createdAtMillis: Long = 0L,
    // «↩️ رجّعه للتاجر»: سبب من الأدمن (المنتج مخفي لين يعدّله)
    val adminNote: String = ""
)

data class ItemPass(val merchantId: String, val scope: String, val kind: String, val itemIds: List<String>, val startsAt: Long, val endsAt: Long)

data class KindPrice(val priceH: Long = 50, val active: Boolean = true)

data class CatalogConfig(
    val enabled: Boolean = false,
    val orderEnabled: Boolean = false,
    val review: Boolean = true,
    val limits: Map<String, Int> = mapOf("menu" to 30, "product" to 10, "service" to 10), // 0 = مفتوح
    val pricingEnabled: Boolean = false,
    val pricingEnabledAt: Long = 0L,
    val graceUntil: Long = 0L,
    val prices: Map<String, KindPrice> = mapOf("menu" to KindPrice(), "product" to KindPrice(), "service" to KindPrice(), "all" to KindPrice(300)),
    val vatEnabled: Boolean = true,
    val vatRate: Double = 15.0,
    val categoryTypes: Map<String, String> = emptyMap(),
    // مزايا الطلب (مفاتيح من الأدمن، كلها شغّالة افتراضياً)
    val features: Map<String, Boolean> = emptyMap()
) {
    fun feat(k: String) = features[k] != false
    fun limitOf(kind: String) = limits[kind] ?: 0
    fun baseKind(category: String) = categoryTypes[category]?.let { ItemKind.of(it) } ?: ItemKind.guess(category)
    fun price(key: String) = prices[key] ?: KindPrice()

    fun grace(it: CatalogItem): Long {
        if (!pricingEnabled || pricingEnabledAt == 0L || it.createdAtMillis >= pricingEnabledAt) return 0L
        return if (graceUntil > 0) graceUntil else Long.MAX_VALUE
    }
    fun paidUntil(it: CatalogItem, passes: List<ItemPass>, now: Long): Long =
        passes.filter { p -> p.merchantId == it.merchantId && p.startsAt <= now && now < p.endsAt && (p.scope == "all" || (p.kind == it.kind && it.id in p.itemIds)) }
            .maxOfOrNull { it.endsAt } ?: 0L
    fun paidUp(it: CatalogItem, passes: List<ItemPass>, now: Long): Boolean {
        if (!pricingEnabled) return true
        if (!price(it.kind).active && !price("all").active) return true
        if (grace(it) > now) return true
        return paidUntil(it, passes, now) > now
    }
    fun visible(it: CatalogItem, passes: List<ItemPass>, now: Long): Boolean {
        if (!enabled || !it.approved) return false
        val lim = limitOf(it.kind)
        if (lim > 0 && it.slot >= lim) return false
        return paidUp(it, passes, now)
    }

    companion object {
        /** تكلفة الظهور بالنقاط (تقريب لفوق): السعر بالهللة × العدد × الأيام */
        fun passCost(priceH: Long, count: Int, days: Int): Long = Math.ceil(priceH * count * days / 100.0).toLong()
        fun riyal(h: Long): String = if (h % 100 == 0L) "${h / 100}" else String.format(java.util.Locale.US, "%.2f", h / 100.0).trimEnd('0').trimEnd('.')
        fun validVat(v: String) = Regex("^3\\d{13}3$").matches(v)
    }
}

/** نسخة سريعة من مفاتيح المزايا للواجهات (تتحدّث من الإعدادات) */
object CatalogFlags {
    @Volatile var superGlow = true
    @Volatile var crop = true
    @Volatile var eta = true
    @Volatile var ratings = true
}

data class CartLine(val id: String, val name: String, val kind: String, val price: Double, val qty: Int,
    // السعر قبل الخصم + خصم 1+1 على السطر (يتعبّى وقت الحساب)
    val was: Double = 0.0, val off: Double = 0.0, val bogo: String = "")

data class HayynaOrder(
    val id: String = "", val code: String = "", val storeId: String = "", val storeName: String = "", val branchName: String = "",
    val customerName: String = "", val customerPhone: String = "", val items: List<CartLine> = emptyList(),
    val subtotal: Double = 0.0, val deliveryFee: Double = 0.0, val total: Double = 0.0,
    val vatRate: Double = 0.0, val vatAmount: Double = 0.0, val vatNumber: String = "",
    val mode: String = "pickup", val lat: Double? = null, val lng: Double? = null, val note: String = "", val status: String = "sent",
    val createdAtMillis: Long = 0L, val etaAtMillis: Long = 0L,
    val pay: String = "", val car: String = "", val arrived: Boolean = false,
    val discount: Double = 0.0
) {
    val isDelivery get() = mode == "delivery"
    val isCurbside get() = mode == "curbside"
    val modeText get() = when (mode) { "delivery" -> "🚚 توصيل"; "curbside" -> "🚗 من السيارة"; else -> "🏃 استلام" }
    val payText get() = when (pay) { "cash" -> "💵 كاش"; "card" -> "💳 بطاقة / شبكة"; else -> "" }
    /** وصف الحالة للزبون حسب نوع الطلب */
    fun statusText(): String = when (status) {
        "sent" -> "📨 أُرسل — بانتظار المحل"; "received" -> "👍 المحل استلم طلبك ويجهّزه"
        "ready" -> if (isCurbside) (if (arrived) "🚗 جاهز — المحل جاي لسيارتك" else "🚗 جاهز — تعال واضغط «وصلت»") else "🛍️ جاهز للاستلام"
        "delivering" -> "🚚 جاري التوصيل"; "done" -> if (isDelivery) "✅ تم التوصيل" else if (isCurbside) "✅ تم التسليم" else "✅ تم الاستلام"; "cancelled" -> "✕ ملغي"; else -> status
    }
    val steps: List<Pair<String, String>> get() = if (isDelivery) listOf("sent" to "أُرسل", "received" to "يجهّزه", "delivering" to "بالطريق", "done" to "وصل")
        else listOf("sent" to "أُرسل", "received" to "يجهّزه", "ready" to "جاهز", "done" to if (isCurbside) "تسلّمته" else "استلمته")
    fun etaText(now: Long): String? {
        if (status != "received" || etaAtMillis <= 0) return null
        val m = ((etaAtMillis - now) / 60000).toInt()
        return if (m > 0) "⏱️ ${if (isDelivery) "يطلع للتوصيل" else "جاهز"} خلال ~$m دقيقة" else "⏱️ ${if (isDelivery) "بيطلع" else "بيكون جاهز"} قريب"
    }
    companion object {
        fun code(): String = "H-" + java.lang.Long.toString(System.currentTimeMillis(), 36).takeLast(4).uppercase() +
            java.lang.Long.toString((0..35).random().toLong(), 36).uppercase()
        val STATUS = mapOf("sent" to "📨 أُرسل", "received" to "👍 استلمه المحل", "ready" to "🛍️ جاهز للاستلام", "delivering" to "🚚 جاري التوصيل", "done" to "✅ تم", "cancelled" to "✕ ملغي")
    }
    private fun f(n: Double) = String.format(java.util.Locale.US, "%.2f", n)
    fun text(): String = buildString {
        appendLine("🧾 ملخص طلب من حيّنا — رقم #$code")
        appendLine("🏪 $storeName" + if (branchName.isNotBlank()) " — $branchName" else "")
        appendLine("━━━━━━━━━━━━")
        items.forEach {
            appendLine("${it.qty} × ${it.name} ........ ${f(it.price * it.qty)} ر.س" + if (it.was > it.price) " (بدل ${f(it.was * it.qty)})" else "")
            if (it.off > 0) appendLine("   🎁 ${it.bogo.ifBlank { "1+1" }}: −${f(it.off)} ر.س")
        }
        appendLine("━━━━━━━━━━━━")
        if (discount > 0) appendLine("🔥 وفّرت: ${f(discount)} ر.س")
        if (deliveryFee > 0) { appendLine("المجموع: ${f(subtotal)} ر.س"); appendLine("🚚 التوصيل: ${f(deliveryFee)} ر.س") }
        if (vatNumber.isNotBlank()) {
            appendLine("المبلغ قبل الضريبة: ${f(total - vatAmount)} ر.س")
            appendLine("ضريبة القيمة المضافة ${vatRate.toString().removeSuffix(".0")}%: ${f(vatAmount)} ر.س")
            appendLine("💰 الإجمالي (شامل الضريبة): ${f(total)} ر.س")
            appendLine("🔢 الرقم الضريبي: $vatNumber")
        } else appendLine("💰 الإجمالي: ${f(total)} ر.س")
        appendLine("━━━━━━━━━━━━")
        if (note.isNotBlank()) appendLine("📝 ملاحظة: $note")
        appendLine("👤 " + listOf(customerName, customerPhone).filter { it.isNotBlank() }.joinToString(" • "))
        append(when (mode) {
            "delivery" -> "📍 توصيل" + (if (lat != null && lng != null) " — https://maps.google.com/?q=$lat,$lng" else "")
            "curbside" -> "🚗 استلام من السيارة" + if (car.isNotBlank()) " — $car" else ""
            else -> "🏃 استلام من المحل"
        })
        if (payText.isNotBlank()) append("\n$payText — الدفع عند الاستلام")
    }
}


// =================== العروض مع المنيو (نفس منطق الويب: storeDeals / dealPrice / cartTotals) ===================

enum class DealType(val id: String, val icon: String, val title: String, val desc: String, val feat: String) {
    DISCOUNT("discount", "🔥", "خصم على المنيو", "نسبة خصم على أصنافك، تنطبق لحالها", "dealDiscount"),
    BUNDLE("bundle", "🎁", "باقة", "وجبة + وجبة، خدمة + خدمة، بسعر واحد", "dealBundle"),
    BOGO("bogo", "➕", "اشترِ واحصل", "1+1 مجاناً، أو الثاني بنص السعر", "dealBogo"),
    PROMO("promo", "📣", "إعلان", "افتتاح، منتج جديد، أي خبر", "dealPromo");
    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/** نوع العرض كما ينحفظ في offers.deal */
data class OfferDeal(
    val type: String, val scope: String = "all", val kind: String = "", val section: String = "",
    val itemIds: List<String> = emptyList(), val itemId: String = "", val getPct: Int = 100, val orderable: Boolean = false
) {
    fun toMap(): Map<String, Any> = when (type) {
        "discount" -> mapOf("type" to type, "scope" to scope, "kind" to kind, "section" to section, "itemIds" to itemIds.take(50))
        "bundle" -> mapOf("type" to type, "itemIds" to itemIds.take(20))
        "bogo" -> mapOf("type" to type, "itemId" to itemId, "getPct" to (if (getPct == 50) 50L else 100L))
        else -> mapOf("type" to "promo", "orderable" to orderable)
    }
    companion object {
        fun from(m: Map<String, Any>?): OfferDeal? {
            val t = m?.get("type") as? String ?: return null
            if (DealType.of(t) == null) return null
            return OfferDeal(t, m["scope"] as? String ?: "all", m["kind"] as? String ?: "", m["section"] as? String ?: "",
                (m["itemIds"] as? List<*>).orEmpty().mapNotNull { it as? String }, m["itemId"] as? String ?: "",
                (m["getPct"] as? Number)?.toInt() ?: 100, m["orderable"] as? Boolean ?: false)
        }
    }
}

/** ⚡ الخصم السريع (quickDeals/{uid}) */
data class QuickDeal(val pct: Int = 0, val scope: String = "all", val kind: String = "", val section: String = "", val itemIds: List<String> = emptyList(), val endsAt: Long = 0L)

data class DealRule(val pct: Int, val scope: String, val kind: String, val section: String, val itemIds: List<String>)
data class DealPack(val id: String, val offerId: String, val name: String, val desc: String, val price: Double, val was: Double, val imageUrl: String)
data class CartTotals(val lines: List<CartLine>, val sub: Double, val saved: Double)

data class StoreDeals(
    val rules: List<DealRule> = emptyList(),
    val bogo: Map<String, Pair<Int, String>> = emptyMap(), // itemId -> (getPct, label)
    val packs: List<DealPack> = emptyList(),
    val banners: List<String> = emptyList()
) {
    /** سعر الصنف بعد أفضل خصم واحد: (السعر، قبل الخصم أو 0، النسبة) */
    fun price(it: CatalogItem): Triple<Double, Double, Int> {
        var pct = 0
        rules.forEach { r ->
            val hit = r.scope == "all" || (r.scope == "kind" && r.kind == it.kind) || (r.scope == "section" && r.section.isNotBlank() && r.section == it.section) ||
                (r.scope == "items" && it.id in r.itemIds)
            if (hit && r.pct > pct) pct = r.pct
        }
        return if (pct > 0) Triple(r2(it.price * (100 - pct) / 100), it.price, pct) else Triple(it.price, 0.0, 0)
    }
    fun totals(lines: List<CartLine>): CartTotals {
        var sub = 0.0; var saved = 0.0; var bo = 0.0
        val out = lines.map { l ->
            val b = bogo[l.id]
            val off = if (b != null) r2((l.qty / 2) * l.price * b.first / 100) else 0.0
            sub += l.price * l.qty; bo += off; if (l.was > l.price) saved += (l.was - l.price) * l.qty
            l.copy(off = off, bogo = b?.second.orEmpty())
        }
        return CartTotals(out, r2(sub - bo), r2(saved + bo))
    }
    companion object {
        fun r2(d: Double) = Math.round(d * 100) / 100.0
        fun build(cfg: CatalogConfig, offers: List<RestaurantOffer>, quick: QuickDeal?, items: List<CatalogItem>, now: Long): StoreDeals {
            val rules = mutableListOf<DealRule>(); val bogo = mutableMapOf<String, Pair<Int, String>>(); val packs = mutableListOf<DealPack>(); val banners = mutableListOf<String>()
            fun scopeText(r: DealRule) = when (r.scope) {
                "kind" -> ItemKind.of(r.kind).title; "section" -> r.section
                "items" -> items.filter { it.id in r.itemIds }.take(3).joinToString("، ") { it.name }; else -> "كل شي"
            }
            offers.forEach { o ->
                val d = OfferDeal.from(o.deal) ?: return@forEach
                val t = DealType.of(d.type) ?: return@forEach
                if (!cfg.feat(t.feat) || o.draft || !o.isApproved || OfferTime.isExpired(o, now) || OfferPricing.isUpcoming(o, now)) return@forEach
                when (t) {
                    DealType.DISCOUNT -> {
                        val pct = OfferPricing.currentPercent(o, now)
                        if (pct in 1..99) { val r = DealRule(pct, d.scope, d.kind, d.section, d.itemIds); rules += r; banners += "🔥 $pct% على ${scopeText(r)}" }
                    }
                    DealType.BOGO -> items.firstOrNull { it.id == d.itemId }?.let { it ->
                        val g = if (d.getPct == 50) 50 else 100; val label = if (g == 100) "1+1 مجاناً" else "الثاني بنص السعر"
                        bogo[it.id] = g to label; banners += "➕ ${it.name}: $label"
                    }
                    else -> if ((t == DealType.BUNDLE || d.orderable) && o.price > 0) {
                        val linked = d.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
                        val sum = r2(linked.sumOf { it.price })
                        val was = if (sum > o.price) sum else if ((o.originalPrice ?: 0.0) > o.price) o.originalPrice!! else 0.0
                        packs += DealPack("deal_${o.id}", o.id, o.offerTitle, if (linked.isNotEmpty()) linked.joinToString(" + ") { it.name } else o.description.take(120),
                            o.price, was, if (o.mediaType == MediaType.VIDEO) "" else o.mediaUri)
                    }
                }
            }
            if (quick != null && cfg.feat("dealDiscount") && cfg.feat("quickDeal") && quick.pct in 1..99 && quick.endsAt > now) {
                val r = DealRule(quick.pct, quick.scope, quick.kind, quick.section, quick.itemIds); rules += r
                banners.add(0, "⚡ خصم ${quick.pct}% على ${scopeText(r)}")
            }
            return StoreDeals(rules, bogo, packs, banners)
        }
    }
}
