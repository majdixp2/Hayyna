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
  for (const it of o.items) L.push(`${it.qty} × ${it.name} ........ ${fmt(it.price * it.qty)} ر.س`);
  L.push("━━━━━━━━━━━━");
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
