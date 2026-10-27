import { currentPercent } from "./logic.js";
// المنتجات والخدمات والمنيو + الطلب كملخص على واتساب + الضريبة.
// كل الإعدادات من لوحة الأدمن (config/app.catalog و config/app.vat و config/app.categoryTypes).
// إذا الخدمة مطفية (catalog.enabled = false) ما يظهر شي — والبيانات تبقى محفوظة.

export const KINDS = {
  menu: { label: "منيو", icon: "🍽️", title: "المنيو", add: "صنف في المنيو", color: "#C2410C", bg: "#FFF1E3" },
  product: { label: "منتج", icon: "🛍️", title: "منتجاتنا", add: "منتج", color: "#1D4ED8", bg: "#E8F0FE" },
  service: { label: "خدمة", icon: "✂️", title: "خدماتنا", add: "خدمة", color: "#6D28D9", bg: "#F1EAFE" },
};
export const KIND_IDS = Object.keys(KINDS);

export const CATALOG_DEFAULT = {
  enabled: false, orderEnabled: false, review: true,
  limits: { menu: 30, product: 10, service: 10 }, // 0 = مفتوح بدون حد
  pricing: {
    enabled: false, enabledAt: null, graceUntil: null, graceDays: 14,
    // priceH = السعر باليوم بالهللة (50 = نص ريال/نقطة)
    menu: { priceH: 50, active: true }, product: { priceH: 50, active: true }, service: { priceH: 50, active: true },
    all: { priceH: 300, active: true },
  },
};
export function catalogOf(config) {
  const c = config?.catalog || {}, p = c.pricing || {}, D = CATALOG_DEFAULT;
  return {
    enabled: !!c.enabled, orderEnabled: !!c.orderEnabled, review: c.review ?? true,
    limits: { ...D.limits, ...(c.limits || {}) },
    pricing: { ...D.pricing, ...p, menu: { ...D.pricing.menu, ...(p.menu || {}) }, product: { ...D.pricing.product, ...(p.product || {}) },
      service: { ...D.pricing.service, ...(p.service || {}) }, all: { ...D.pricing.all, ...(p.all || {}) } },
  };
}
/** مزايا الطلب (مفاتيح من الأدمن، كلها شغّالة افتراضياً) */
export const FEATURES = {
  ratings: "⭐ التقييمات بعد الطلب", curbside: "🚗 الاستلام من السيارة", payChoice: "💳 اختيار طريقة الدفع عند الاستلام",
  freeHint: "💡 تلميح التوصيل المجاني", eta: "⏱️ الوقت التقريبي للتجهيز", superGlow: "🚀 لمعة صفحة عرض سوبر", crop: "✂️ قص الصور بيد التاجر",
  dealDiscount: "🔥 عرض: خصم على المنيو", dealBundle: "🎁 عرض: باقة", dealBogo: "➕ عرض: اشترِ واحصل", dealPromo: "📣 عرض: إعلان قابل للطلب", quickDeal: "⚡ الخصم السريع من المنيو",
};
export const feat = (config, k) => (config?.catalog?.features || {})[k] !== false;
export const vatOf = (config) => ({ enabled: config?.vat?.enabled ?? true, rate: Number(config?.vat?.rate ?? 15) });

/** النوع الأساسي للمحل حسب تصنيفه (الأدمن يحدده لكل تصنيف، وإلا نخمّن من الاسم). */
export function guessKind(cat) {
  const c = String(cat || "");
  if (/مطاعم|مطعم|مقاهي|مقهى|حلويات|مخبز|مخابز|كافيه/.test(c)) return "menu";
  if (/خياط|مشاغل|حلاق|صالون|خدمات|صيانة|نادي|رياض|مغسل|تنظيف|تعليم|تدريب|عيادة|سباك|كهرب/.test(c)) return "service";
  return "product";
}
export const baseKindOf = (config, cat) => (config?.categoryTypes || {})[cat] || guessKind(cat);
/** ترتيب الأقسام في صفحة المتجر: الأساسي أول */
export const kindOrder = (base) => [base, ...KIND_IDS.filter((k) => k !== base)];

export const limitOf = (cat, kind) => +(cat.limits?.[kind] ?? 0) || 0; // 0 = مفتوح
export const slotId = (uid, kind, n) => `${uid}_${kind}_${n}`;
const ms = (t) => (t && t.toMillis ? t.toMillis() : typeof t === "number" ? t : 0);

/** تكلفة الظهور بالنقاط (تقريب لفوق): priceH بالهللة × العدد × الأيام */
export const passCost = (priceH, count, days) => Math.ceil((priceH * count * days) / 100);
export const riyal = (h) => (h / 100).toLocaleString("en-US", { maximumFractionDigits: 2 });

/** منتج قديم قبل تشغيل التسعير: مجاني لين نهاية فترة السماح */
export function itemGrace(it, cat) {
  const since = ms(cat.pricing.enabledAt);
  if (!cat.pricing.enabled || !since || ms(it.createdAt) >= since) return 0;
  return ms(cat.pricing.graceUntil) || Infinity;
}
/** نهاية ظهور منتج مدفوع (0 = غير مدفوع) */
export function itemPaidUntil(it, passes, now = Date.now()) {
  let end = 0;
  for (const p of passes) {
    if (p.merchantId !== it.merchantId) continue;
    const s = ms(p.startsAt), e = ms(p.endsAt);
    if (s > now || e <= now) continue;
    if (p.scope === "all" || (p.kind === it.kind && (p.itemIds || []).includes(it.id))) end = Math.max(end, e);
  }
  return end;
}
export function itemPaidUp(it, passes, cat, now = Date.now()) {
  if (!cat.pricing.enabled) return true;
  if (!cat.pricing[it.kind]?.active && !cat.pricing.all?.active) return true; // ما فيه طريقة دفع مفعّلة لهالنوع = مجاني
  if (itemGrace(it, cat) > now) return true;
  return itemPaidUntil(it, passes, now) > now;
}
/** يظهر للزبون؟ */
export function itemVisible(it, passes, cat, now = Date.now()) {
  if (!cat.enabled || !it.approved) return false;
  const lim = limitOf(cat, it.kind);
  if (lim && it.slot >= lim) return false;
  return itemPaidUp(it, passes, cat, now);
}

// ---------- الضريبة (الأسعار شاملة الضريبة) ----------
const r2 = (n) => Math.round(n * 100) / 100;
export const vatSplit = (total, rate) => { const vat = r2((total * rate) / (100 + rate)); return { net: r2(total - vat), vat }; };
export const validVatNumber = (v) => /^3\d{13}3$/.test(String(v || ""));
export const fmt = (n) => Number(n || 0).toFixed(2);

/** رقم طلب قصير وسهل: H-XXXX */
export const orderCode = () => "H-" + Date.now().toString(36).slice(-4).toUpperCase() + Math.floor(Math.random() * 36).toString(36).toUpperCase();

/** نص ملخص الطلب لواتساب */
export function orderText(o) {
  const L = [];
  L.push(`🧾 ملخص طلب من حيّنا — رقم #${o.code}`);
  L.push(`🏪 ${o.storeName}${o.branchName ? " — " + o.branchName : ""}`);
  L.push("━━━━━━━━━━━━");
  for (const it of o.items) {
    L.push(`${it.qty} × ${it.name} ........ ${fmt(it.price * it.qty)} ر.س${it.was > it.price ? ` (بدل ${fmt(it.was * it.qty)})` : ""}`);
    if (it.off > 0) L.push(`   🎁 ${it.bogo || "1+1"}: −${fmt(it.off)} ر.س`);
  }
  L.push("━━━━━━━━━━━━");
  if (o.discount > 0) L.push(`🔥 وفّرت: ${fmt(o.discount)} ر.س`);
  if (o.deliveryFee > 0) { L.push(`المجموع: ${fmt(o.subtotal)} ر.س`); L.push(`🚚 التوصيل: ${fmt(o.deliveryFee)} ر.س`); }
  if (o.vatNumber) {
    L.push(`المبلغ قبل الضريبة: ${fmt(o.total - o.vatAmount)} ر.س`);
    L.push(`ضريبة القيمة المضافة ${o.vatRate}%: ${fmt(o.vatAmount)} ر.س`);
    L.push(`💰 الإجمالي (شامل الضريبة): ${fmt(o.total)} ر.س`);
    L.push(`🔢 الرقم الضريبي: ${o.vatNumber}`);
  } else L.push(`💰 الإجمالي: ${fmt(o.total)} ر.س`);
  L.push("━━━━━━━━━━━━");
  if (o.note) L.push(`📝 ملاحظة: ${o.note}`);
  L.push(`👤 ${[o.customerName, o.customerPhone].filter(Boolean).join(" • ")}`);
  L.push(o.mode === "delivery" ? `📍 توصيل${o.location ? ` — https://maps.google.com/?q=${o.location.lat},${o.location.lng}` : ""}`
    : o.mode === "curbside" ? `🚗 استلام من السيارة${o.car ? ` — ${o.car}` : ""}` : "🏃 استلام من المحل");
  if (o.pay) L.push(`${PAY[o.pay]?.icon || "💵"} الدفع: ${PAY[o.pay]?.label || o.pay} عند الاستلام`);
  return L.join("\n");
}

export const ORDER_STATUS = {
  sent: { t: "📨 أُرسل", cls: "" }, received: { t: "👍 استلمه المحل", cls: "open" }, ready: { t: "🛍️ جاهز للاستلام", cls: "open" },
  delivering: { t: "🚚 جاري التوصيل", cls: "open" }, done: { t: "✅ تم", cls: "closed" }, cancelled: { t: "✕ ملغي", cls: "urgent" },
};
export const PAY = { cash: { icon: "💵", label: "كاش" }, card: { icon: "💳", label: "بطاقة / شبكة" } };
export const MODES = { pickup: "🏃 استلام من المحل", delivery: "🚚 توصيل", curbside: "🚗 استلام من السيارة" };
/** وصف الحالة للزبون حسب نوع الطلب (استلام / توصيل / من السيارة) */
export function statusText(o) {
  const del = o.mode === "delivery", car = o.mode === "curbside";
  return ({ sent: "📨 أُرسل — بانتظار المحل", received: "👍 المحل استلم طلبك ويجهّزه",
    ready: car ? (o.arrivedAt ? "🚗 جاهز — المحل جاي لسيارتك" : "🚗 جاهز — تعال واضغط «وصلت»") : "🛍️ جاهز للاستلام", delivering: "🚚 جاري التوصيل",
    done: del ? "✅ تم التوصيل" : car ? "✅ تم التسليم" : "✅ تم الاستلام", cancelled: "✕ ملغي" })[o.status] || o.status;
}
/** التوصيل: الرسوم + كم باقي للمجاني */
export function deliveryInfo(s, sub) {
  if (!s?.delivery) return null;
  const fee = s.deliveryFee || 0, freeOver = s.deliveryFreeOver || 0;
  const free = fee <= 0 || (freeOver > 0 && sub >= freeOver);
  return { fee: free ? 0 : fee, free, freeOver, left: !free && freeOver > 0 ? Math.round((freeOver - sub) * 100) / 100 : 0,
    label: fee <= 0 ? "🚚 توصيل مجاني" : `🚚 توصيل — ${fmt(fee)} ر.س${freeOver > 0 ? ` (مجاني من ${fmt(freeOver)})` : ""}` };
}
export const payMethods = (s) => { const m = []; if (s?.payCash !== false) m.push("cash"); if (s?.payCard !== false) m.push("card"); return m.length ? m : ["cash"]; };
/** المراحل الأربع لشريط التقدّم */
export const orderSteps = (o) => o.mode === "curbside"
  ? [["sent", "أُرسل"], ["received", "يجهّزه"], ["ready", "جاهز"], ["done", "تسلّمته"]]
  : o.mode === "delivery"
  ? [["sent", "أُرسل"], ["received", "يجهّزه"], ["delivering", "بالطريق"], ["done", "وصل"]]
  : [["sent", "أُرسل"], ["received", "يجهّزه"], ["ready", "جاهز"], ["done", "استلمته"]];


// =================== العروض مع المنيو ===================
// العرض (offers) فيه حقل اختياري deal:
//   discount: { type, scope: all|kind|section|items, kind, section, itemIds }  — النسبة من discountPercent / dailyPercents
//   bundle:   { type, itemIds }  — السعر = price، والأصناف المربوطة اختيارية (للمقارنة «بدل»)
//   bogo:     { type, itemId, getPct: 100|50 } — 1+1 أو الثاني بنص السعر
//   promo:    { type, orderable } — إعلان، ولو orderable وله سعر يصير قابل للطلب
// والخصم السريع: quickDeals/{uid} = { pct, scope, kind, section, itemIds, endsAt }
export const DEAL_TYPES = {
  discount: { icon: "🔥", t: "خصم على المنيو", d: "نسبة خصم على أصنافك، تنطبق لحالها", feat: "dealDiscount" },
  bundle: { icon: "🎁", t: "باقة", d: "وجبة + وجبة، خدمة + خدمة، بسعر واحد", feat: "dealBundle" },
  bogo: { icon: "➕", t: "اشترِ واحصل", d: "1+1 مجاناً، أو الثاني بنص السعر", feat: "dealBogo" },
  promo: { icon: "📣", t: "إعلان", d: "افتتاح، منتج جديد، أي خبر", feat: "dealPromo" },
};
export const DEAL_SCOPES = { all: "كل الأصناف", kind: "نوع معيّن", section: "قسم معيّن", items: "أصناف أختارها" };
const r2d = (n) => Math.round(n * 100) / 100;
/** النسبة الحالية للعرض (يدعم الخصم المتدرّج بالأيام) */
export const offerPct = (o, now = Date.now()) => +currentPercent(o, now) || 0;
const live = (o, now) => o && !o.draft && o.isApproved !== false && !(ms(o.expiresAt) && ms(o.expiresAt) <= now) && !(ms(o.startsAt) > now);
const scopeText = (r, items) => r.scope === "kind" ? (KINDS[r.kind]?.title || "") : r.scope === "section" ? r.section : r.scope === "items" ? (items || []).filter((x) => (r.itemIds || []).includes(x.id)).map((x) => x.name).slice(0, 3).join("، ") : "على كل شي";
/** كل العروض الشغّالة لمتجر: قواعد الخصم + 1+1 + الباقات القابلة للطلب */
export function storeDeals(config, offers, quick, items = [], now = Date.now()) {
  const on = (t) => feat(config, DEAL_TYPES[t].feat);
  const D = { rules: [], bogo: {}, packs: [], banners: [] };
  for (const o of offers || []) {
    const d = o.deal; if (!d || !live(o, now) || !DEAL_TYPES[d.type] || !on(d.type)) continue;
    if (d.type === "discount") {
      const pct = offerPct(o, now); if (pct <= 0 || pct >= 100) continue;
      const r = { pct, scope: d.scope || "all", kind: d.kind || "", section: d.section || "", itemIds: d.itemIds || [], src: o.id };
      D.rules.push(r); D.banners.push({ icon: "🔥", t: `${pct}% ${r.scope === "all" ? "على كل شي" : "على " + scopeText(r, items)}`, offerId: o.id });
    } else if (d.type === "bogo") {
      const it = items.find((x) => x.id === d.itemId); if (!it) continue;
      const g = d.getPct === 50 ? 50 : 100;
      D.bogo[it.id] = { getPct: g, label: g === 100 ? "1+1 مجاناً" : "الثاني بنص السعر", offerId: o.id };
      D.banners.push({ icon: "➕", t: `${it.name}: ${D.bogo[it.id].label}`, offerId: o.id });
    } else if ((d.type === "bundle" || (d.type === "promo" && d.orderable)) && o.price > 0) {
      const linked = (d.itemIds || []).map((id) => items.find((x) => x.id === id)).filter(Boolean);
      const sum = r2d(linked.reduce((a, x) => a + (+x.price || 0), 0));
      const was = sum > o.price ? sum : (o.originalPrice > o.price ? o.originalPrice : 0);
      D.packs.push({ id: "deal_" + o.id, offerId: o.id, kind: "deal", name: o.offerTitle, desc: linked.length ? linked.map((x) => x.name).join(" + ") : (o.description || "").slice(0, 120),
        price: +o.price, was, imageUrl: o.mediaType === "VIDEO" ? "" : o.mediaUri, type: d.type, available: true });
    }
  }
  if (quick && on("discount") && feat(config, "quickDeal") && +quick.pct > 0 && +quick.pct < 100 && ms(quick.endsAt) > now) {
    const r = { pct: +quick.pct, scope: quick.scope || "all", kind: quick.kind || "", section: quick.section || "", itemIds: quick.itemIds || [], src: "quick" };
    D.rules.push(r); D.banners.unshift({ icon: "⚡", t: `خصم ${r.pct}% ${r.scope === "all" ? "على كل شي" : "على " + scopeText(r, items)}`, quick: true });
  }
  return D;
}
/** سعر الصنف بعد أفضل خصم واحد (ما تتراكب الخصومات) */
export function dealPrice(it, D) {
  let pct = 0;
  for (const r of D?.rules || []) {
    const hit = r.scope === "all" || (r.scope === "kind" && r.kind === it.kind) || (r.scope === "section" && r.section && r.section === (it.section || ""))
      || (r.scope === "items" && (r.itemIds || []).includes(it.id));
    if (hit && r.pct > pct) pct = r.pct;
  }
  const base = +it.price || 0;
  return pct ? { price: r2d(base * (100 - pct) / 100), was: base, pct } : { price: base, was: 0, pct: 0 };
}
/** حساب السلة: سعر كل سطر بعد الخصم + 1+1 + كم وفّر */
export function cartTotals(lines, D) {
  let sub = 0, saved = 0, bogoOff = 0;
  const out = lines.map((l) => {
    const unit = +l.price || 0, was = +l.was || 0, b = D?.bogo?.[l.id];
    const off = b ? r2d(Math.floor(l.qty / 2) * unit * b.getPct / 100) : 0;
    sub += unit * l.qty; bogoOff += off; if (was > unit) saved += (was - unit) * l.qty;
    return { ...l, off, bogo: b?.label || "" };
  });
  sub = r2d(sub - bogoOff); saved = r2d(saved + bogoOff);
  return { lines: out, sub, saved, bogoOff: r2d(bogoOff) };
}
