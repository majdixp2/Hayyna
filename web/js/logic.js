// منطق العروض المشترك (نفس التطبيق): الخصم، المتدرّج، البداية، الدوام، التوصيل، المسافة.
export const DAY = 86400000;
export const CATEGORIES = ["الكل", "مطاعم 🍽️", "مقاهي وحلويات ☕", "سوبرماركت وبقالة 🛒", "ملابس وأزياء 👕", "خياطة ومشاغل 🧵",
  "نظارات وبصريات 👓", "عطور وتجميل 💄", "جوالات وإلكترونيات 📱", "حلاقة وصالونات 💈", "خدمات أخرى 🧰"];
export const DURATIONS = [{ label: "بدون انتهاء", days: null }, { label: "يوم", days: 1 }, { label: "3 أيام", days: 3 },
  { label: "أسبوع", days: 7 }, { label: "أسبوعين", days: 14 }, { label: "شهر", days: 30 }];
export const REPORT_REASONS = ["عرض غير حقيقي أو مضلل", "السعر أو الخصم غير صحيح", "المحل مقفل أو غير موجود", "محتوى غير لائق", "أخرى"];
export const FEEDBACK_TYPES = ["🐞 مشكلة", "💡 اقتراح", "✨ فكرة إضافية", "🚩 شكوى على متجر", "أخرى"];

export const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
export const cld = (url, w = 400) => !url ? "" : url.includes("res.cloudinary.com") && url.includes("/upload/")
  ? url.replace("/upload/", `/upload/f_auto,q_auto,c_limit,w_${w}/`) : url;
export const videoThumb = (u, w = 400) => u && u.includes("/video/upload/")
  ? u.replace("/upload/", `/upload/so_1,f_jpg,q_auto,c_limit,w_${w}/`).replace(/\.[^./]+$/, ".jpg") : "";
export const gallery = (o) => (o.mediaUris || []).filter(Boolean).length ? o.mediaUris.filter(Boolean) : [o.mediaUri].filter(Boolean);
export const thumb = (o, w = 300) => { const u = gallery(o)[0] || ""; return o.mediaType === "VIDEO" ? videoThumb(u, w) : cld(u, w); };
export const ms = (t) => (t && t.toMillis ? t.toMillis() : (typeof t === "number" ? t : null));
export const money = (v) => (Number(v) % 1 === 0 ? String(Number(v)) : Number(v).toFixed(2).replace(/0+$/, ""));
export const norm = (s) => String(s || "").trim().toLowerCase().replace(/[ً-ْـ]/g, "")
  .replace(/[أإآ]/g, "ا").replace(/ة/g, "ه").replace(/ى/g, "ي").replace(/[اه](?=\s|$)/g, "ه");
export const midnight = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
const ORD = ["الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر"];
export const ordinal = (i) => ORD[i] || String(i + 1);
const DAYS = ["الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"];
const MONTHS = ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"];
export const dateText = (t) => { const d = new Date(t); return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}`; };
export const fmtMinute = (m) => { const h = Math.floor(m / 60), mm = m % 60, p = h < 12 ? "ص" : "م"; const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h; return mm ? `${h12}:${String(mm).padStart(2, "0")} ${p}` : `${h12} ${p}`; };
export const districtLabel = (d) => !d ? "" : d.trim().startsWith("حي") ? d.trim() : "حي " + d.trim();

export function km(a, b) {
  const R = 6371, r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r;
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}
export const distText = (d) => d == null ? "" : d < 1 ? `${Math.round(d * 1000)} متر` : `${d.toFixed(1)} كم`;

export const branchesOf = (o) => (o.branches && o.branches.length) ? o.branches : [{
  id: "main", latitude: o.latitude, longitude: o.longitude, district: o.district, region: o.region,
  phone: o.phone, openMinute: o.openMinute, closeMinute: o.closeMinute, landmark: ""
}];
/** ❌ نفد اليوم (يعلّمه المحل أو موظفه، ويرجع لحاله بعد منتصف الليل) */
export const isSoldOut = (o, now = Date.now()) => (ms(o.soldOutUntil) || 0) > now;
export const isUpcoming = (o, now = Date.now()) => (ms(o.startsAt) || 0) > now;
export const isExpired = (o, now = Date.now()) => { const e = ms(o.expiresAt); return e != null && e <= now; };
export const isPercentOnly = (o) => (o.price || 0) <= 0 && ((o.discountPercent || 0) > 0 || (o.dailyPercents || []).length > 0);
export function dayIndex(o, now = Date.now()) {
  const s = ms(o.startsAt) || ms(o.createdAt); if (!s || now <= s) return 0;
  return Math.max(0, Math.round((midnight(now) - midnight(s)) / DAY));
}
export function currentPercent(o, now = Date.now()) {
  const st = o.dailyPercents || [];
  if (st.length) return Number(st[dayIndex(o, now)] ?? st[st.length - 1]);
  return o.discountPercent || 0;
}
export function effectivePercent(o) {
  if (o.originalPrice > 0 && o.price > 0 && o.price < o.originalPrice) return Math.floor((o.originalPrice - o.price) / o.originalPrice * 100);
  return currentPercent(o);
}
export const priceLabel = (o, scope = false) => o.source === "ad" && !(o.price > 0) && !isPercentOnly(o) ? "📷 التفاصيل في الإعلان"
  : o.postType === "new" && !(o.price > 0) && !isPercentOnly(o) ? "✨ منتج جديد"
  : isPercentOnly(o)
  ? `خصم ${currentPercent(o)}%${scope && o.itemsScope ? " " + o.itemsScope : ""}`
  : `${money(o.price)} ${o.currency || "ر.س"}`;
export function tiersText(o) {
  const st = (o.dailyPercents || []).map(Number); if (st.length < 2) return null;
  const g = [];
  st.forEach((p, i) => { const l = g[g.length - 1]; if (l && l[2] === p && l[1] === i - 1) l[1] = i; else g.push([i, i, p]); });
  return g.map(([a, b, p]) => `${a === b ? `اليوم ${ordinal(a)}` : b === a + 1 ? `اليوم ${ordinal(a)} و${ordinal(b)}` : `من اليوم ${ordinal(a)} إلى ${ordinal(b)}`} ${p}%`).join(" • ");
}
export function startsText(o, now = Date.now()) {
  const s = ms(o.startsAt); if (!s || s <= now) return null;
  const min = Math.floor((s - now) / 60000), days = Math.round((midnight(s) - midnight(now)) / DAY);
  if (min < 60) return `يبدأ بعد ${Math.max(1, min)} دقيقة`;
  if (days === 0) { const d = new Date(s); return `يبدأ اليوم ${fmtMinute(d.getHours() * 60 + d.getMinutes())}`; }
  if (days === 1) return "يبدأ بكرة";
  return `يبدأ ${dateText(s)}`;
}
export function remainingText(o, now = Date.now()) {
  const st = startsText(o, now); if (st) return { t: "🔜 " + st, cls: "soon" };
  const e = ms(o.expiresAt); if (!e) return null;
  const m = Math.floor((e - now) / 60000);
  if (m <= 0) return { t: "انتهى العرض", cls: "closed" };
  let t;
  if (m < 60) t = `باقي ${m} دقيقة`;
  else if (m < 1440) { const h = Math.floor(m / 60); t = h === 1 ? "باقي ساعة" : h === 2 ? "باقي ساعتين" : h <= 10 ? `باقي ${h} ساعات` : `باقي ${h} ساعة`; }
  else { const d = Math.floor(m / 1440); t = d === 1 ? "باقي يوم" : d === 2 ? "باقي يومين" : d <= 10 ? `باقي ${d} أيام` : `باقي ${d} يوم`; }
  return { t: "⏰ " + t, cls: m < 180 ? "urgent" : "" };
}
export function openNow(b) {
  if (!b || !(b.openMinute >= 0 && b.openMinute < 1440 && b.closeMinute >= 0 && b.closeMinute < 1440)) return null;
  const d = new Date(), n = d.getHours() * 60 + d.getMinutes(), a = b.openMinute, c = b.closeMinute;
  return a === c ? true : a < c ? (n >= a && n < c) : (n >= a || n < c);
}
export const hoursText = (b) => (b && b.openMinute >= 0 && b.closeMinute >= 0)
  ? (b.openMinute === b.closeMinute ? "مفتوح 24 ساعة" : `${fmtMinute(b.openMinute)} – ${fmtMinute(b.closeMinute)}`) : "";
export function deliveryText(x) {
  if (!x || !x.delivery) return null;
  const fee = (x.deliveryFee || 0) <= 0 ? "توصيل مجاني" : `توصيل ${money(x.deliveryFee)} ر.س`;
  return `🛵 ${fee}${(x.deliveryMinOrder || 0) > 0 ? ` • للطلبات فوق ${money(x.deliveryMinOrder)} ر.س` : ""}${(x.deliveryFee || 0) > 0 && (x.deliveryFreeOver || 0) > 0 ? ` • مجاني فوق ${money(x.deliveryFreeOver)} ر.س` : ""}`;
}
export function badge(o) {
  if (isUpcoming(o)) return { t: "قريباً", soon: true };
  const p = effectivePercent(o);
  if (o.postType === "new") return { t: p >= 1 ? `جديد -${p}%` : "✨ جديد", isNew: true };
  return p >= 1 ? { t: `-${p}%` } : null;
}
export const waDigits = (p) => {
  let d = String(p || "").replace(/\D/g, "");
  if (d.startsWith("05") && d.length === 10) d = "966" + d.slice(1);
  else if (d.startsWith("5") && d.length === 9) d = "966" + d;
  return d;
};
export function normalizeSaudiPhone(raw) {
  const d = String(raw || "").replace(/[٠-٩]/g, (c) => "٠١٢٣٤٥٦٧٨٩".indexOf(c)).replace(/\D/g, "");
  let local;
  if (d.startsWith("9665") && d.length === 12) local = d.slice(3);
  else if (d.startsWith("05") && d.length === 10) local = d.slice(1);
  else if (d.startsWith("5") && d.length === 9) local = d;
  else return d.length >= 8 && d.length <= 15 ? "+" + d : null;
  return "+966" + local;
}
export const toLocalPhone = (p) => { const d = String(p || "").replace(/\D/g, ""); return d.startsWith("966") ? "0" + d.slice(3) : d; };
export const menuLabel = (cat) => /مطاعم|مقاهي/.test(cat || "") ? "المنيو" : /خياط|حلاقة|خدمات/.test(cat || "") ? "الخدمات والأسعار" : "المنتجات والأسعار";
export const minuteOptions = () => Array.from({ length: 48 }, (_, i) => i * 30);

// لغة التواريخ: مثل لغة الموقع (أرقام لاتينية)
export const LOC = (() => { try { return localStorage.getItem("hayyna_lang") === "en" ? "en-GB" : "ar-SA-u-ca-gregory-nu-latn"; } catch { return "ar-SA-u-ca-gregory-nu-latn"; } })();
// ===== 📅 اطلب مسبقاً =====
export const PRE_DEF = { on: false, maxDays: 0, prepMin: 30, pickup: true, delivery: true, delFrom: -1, delTo: -1, slotMin: 60, slotCap: 1 };
export const preCfg = (c) => ({ ...PRE_DEF, ...(c || {}) });
const p2 = (n) => String(n).padStart(2, "0");
const dayStartOf = (t) => { const d = new Date(t); return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime(); };
/** مفتاح الفترة: YYYYMMDD-HHMM (13 حرف) */
export const slotKeyOf = (t) => { const d = new Date(t); return `${d.getFullYear()}${p2(d.getMonth() + 1)}${p2(d.getDate())}-${p2(d.getHours())}${p2(d.getMinutes())}`; };
const winOf = (a, b) => (a >= 0 && a < 1440 && b >= 0 && b < 1440) ? (a === b ? [0, 1440] : [a, b > a ? b : b + 1440]) : null;
/** الأيام المتاحة: اليوم + لين maxDays */
export function preDays(cfg, now = Date.now()) {
  const c = preCfg(cfg), s = dayStartOf(now), out = [];
  for (let i = 0; i <= Math.min(14, Math.max(0, c.maxDays | 0)); i++) { const d = new Date(s); d.setDate(d.getDate() + i); out.push(d.getTime()); }
  return out;
}
export const dayLabel = (t, now = Date.now()) => {
  const diff = Math.round((dayStartOf(t) - dayStartOf(now)) / 864e5);
  if (diff === 0) return "اليوم";
  if (diff === 1) return "بكرة";
  const d = new Date(t);
  return `${d.toLocaleDateString(LOC, { weekday: "long" })} ${d.getDate()}/${d.getMonth() + 1}`;
};
/** الأوقات المتاحة ليوم: التوصيل فترات (طولها slotMin) داخل نافذة المحل، والاستلام كل ربع ساعة داخل الدوام */
export function preTimes(cfg, branch, mode, day, now = Date.now()) {
  const c = preCfg(cfg), del = mode === "delivery";
  const win = (del && c.delFrom >= 0 ? winOf(c.delFrom, c.delTo) : null) || winOf(branch?.openMinute, branch?.closeMinute) || [0, 1440];
  const step = del ? c.slotMin : 15, min = now + c.prepMin * 60000, out = [];
  for (let m = win[0]; del ? m + step <= win[1] : m < win[1]; m += step) {
    const t = day + m * 60000;
    if (t < min) continue;
    out.push({ t, end: del ? t + step * 60000 : 0, key: slotKeyOf(t) });
  }
  return out;
}
export const timeOf = (t) => { const d = new Date(t); return fmtMinute(d.getHours() * 60 + d.getMinutes()); };
/** «اليوم 7:30 م» */
export const preWhen = (t, now = Date.now()) => `${dayLabel(t, now)} ${timeOf(t)}`;
