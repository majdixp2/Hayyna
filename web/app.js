// حيّنا على الويب — نسخة الزبائن (خريطة العروض + صفحات العروض والمتاجر).
// نفس قاعدة بيانات التطبيق، وقواعد Firestore تسمح بقراءة المعتمد فقط.
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getFirestore, collection, doc, onSnapshot, query, where, updateDoc, increment
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig, FIRESTORE_DB_ID, AUTO_HIDE_REPORTS } from "./config.js";

const db = getFirestore(initializeApp(firebaseConfig), FIRESTORE_DB_ID);
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const DAY = 86400000;
const CATEGORIES = ["الكل", "مطاعم 🍔", "مقاهي وحلويات ☕", "سوبرماركت وبقالة 🛒", "ملابس وأزياء 👕", "خياطة ومشاغل 🧵",
  "نظارات وبصريات 👓", "عطور وتجميل 💄", "جوالات وإلكترونيات 📱", "حلاقة وصالونات 💈", "خدمات أخرى 🧰"];

const state = { offers: [], stores: [], q: "", cat: "الكل", me: null, selected: null, branchId: null, view: null };

// ---------------- أدوات ----------------
const cld = (url, w = 400) => !url ? "" : url.includes("res.cloudinary.com") && url.includes("/upload/")
  ? url.replace("/upload/", `/upload/f_auto,q_auto,c_limit,w_${w}/`) : url;
const videoThumb = (u, w = 400) => u.includes("/video/upload/")
  ? u.replace("/upload/", `/upload/so_1,f_jpg,q_auto,c_limit,w_${w}/`).replace(/\.[^./]+$/, ".jpg") : "";
const gallery = (o) => (o.mediaUris || []).filter(Boolean).length ? o.mediaUris.filter(Boolean) : [o.mediaUri].filter(Boolean);
const thumb = (o, w = 300) => { const u = gallery(o)[0] || ""; return o.mediaType === "VIDEO" ? videoThumb(u, w) : cld(u, w); };
const ms = (t) => (t && t.toMillis ? t.toMillis() : null);
const money = (v) => (v % 1 === 0 ? String(v) : v.toFixed(2).replace(/0+$/, ""));
const norm = (s) => String(s || "").trim().toLowerCase().replace(/[ً-ْـ]/g, "")
  .replace(/[أإآ]/g, "ا").replace(/ة/g, "ه").replace(/ى/g, "ي")
  .replace(/[اه](?=\s|$)/g, "ه"); // شاورما = شاورمه
const midnight = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
const ORD = ["الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر"];
const ordinal = (i) => ORD[i] || String(i + 1);
const DAYS = ["الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"];
const MONTHS = ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"];
const dateText = (t) => { const d = new Date(t); return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}`; };
const fmtMinute = (m) => { const h = Math.floor(m / 60), mm = m % 60, p = h < 12 ? "ص" : "م"; const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h; return mm ? `${h12}:${String(mm).padStart(2, "0")} ${p}` : `${h12} ${p}`; };
const toast = (m) => { const t = $("toast"); t.textContent = m; t.classList.remove("hidden"); clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add("hidden"), 2600); };

function km(a, b) {
  const R = 6371, r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r;
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}
const distText = (d) => d == null ? "" : d < 1 ? `${Math.round(d * 1000)} متر` : `${d.toFixed(1)} كم`;

// ---------------- منطق العرض (نفس التطبيق) ----------------
const branchesOf = (o) => (o.branches && o.branches.length) ? o.branches : [{
  id: "main", latitude: o.latitude, longitude: o.longitude, district: o.district, region: o.region,
  phone: o.phone, openMinute: o.openMinute, closeMinute: o.closeMinute, landmark: ""
}];
const isUpcoming = (o, now = Date.now()) => (ms(o.startsAt) || 0) > now;
const isExpired = (o, now = Date.now()) => { const e = ms(o.expiresAt); return e != null && e <= now; };
const isPercentOnly = (o) => (o.price || 0) <= 0 && ((o.discountPercent || 0) > 0 || (o.dailyPercents || []).length > 0);
function dayIndex(o, now = Date.now()) {
  const s = ms(o.startsAt) || ms(o.createdAt); if (!s || now <= s) return 0;
  return Math.max(0, Math.round((midnight(now) - midnight(s)) / DAY));
}
function currentPercent(o, now = Date.now()) {
  const st = o.dailyPercents || [];
  if (st.length) return Number(st[dayIndex(o, now)] ?? st[st.length - 1]);
  return o.discountPercent || 0;
}
function effectivePercent(o) {
  if (o.originalPrice > 0 && o.price > 0 && o.price < o.originalPrice) return Math.floor((o.originalPrice - o.price) / o.originalPrice * 100);
  return currentPercent(o);
}
const priceLabel = (o, scope = false) => isPercentOnly(o)
  ? `خصم ${currentPercent(o)}%${scope && o.itemsScope ? " " + o.itemsScope : ""}`
  : `${money(o.price)} ${o.currency || "ر.س"}`;
function tiersText(o) {
  const st = (o.dailyPercents || []).map(Number); if (st.length < 2) return null;
  const g = [];
  st.forEach((p, i) => { const l = g[g.length - 1]; if (l && l[2] === p && l[1] === i - 1) l[1] = i; else g.push([i, i, p]); });
  return g.map(([a, b, p]) => `${a === b ? `اليوم ${ordinal(a)}` : b === a + 1 ? `اليوم ${ordinal(a)} و${ordinal(b)}` : `من اليوم ${ordinal(a)} إلى ${ordinal(b)}`} ${p}%`).join(" • ");
}
function startsText(o, now = Date.now()) {
  const s = ms(o.startsAt); if (!s || s <= now) return null;
  const min = Math.floor((s - now) / 60000), days = Math.round((midnight(s) - midnight(now)) / DAY);
  if (min < 60) return `يبدأ بعد ${Math.max(1, min)} دقيقة`;
  if (days === 0) { const d = new Date(s); return `يبدأ اليوم ${fmtMinute(d.getHours() * 60 + d.getMinutes())}`; }
  if (days === 1) return "يبدأ بكرة";
  return `يبدأ ${dateText(s)}`;
}
function remainingText(o, now = Date.now()) {
  const st = startsText(o, now); if (st) return { t: st, cls: "soon" };
  const e = ms(o.expiresAt); if (!e) return null;
  const m = Math.floor((e - now) / 60000);
  if (m <= 0) return { t: "انتهى العرض", cls: "closed" };
  const urgent = m < 180;
  let t;
  if (m < 60) t = `باقي ${m} دقيقة`;
  else if (m < 1440) { const h = Math.floor(m / 60); t = h === 1 ? "باقي ساعة" : h === 2 ? "باقي ساعتين" : h <= 10 ? `باقي ${h} ساعات` : `باقي ${h} ساعة`; }
  else { const d = Math.floor(m / 1440); t = d === 1 ? "باقي يوم" : d === 2 ? "باقي يومين" : d <= 10 ? `باقي ${d} أيام` : `باقي ${d} يوم`; }
  return { t: "⏰ " + t, cls: urgent ? "urgent" : "" };
}
function openNow(b) {
  if (!(b.openMinute >= 0 && b.openMinute < 1440 && b.closeMinute >= 0 && b.closeMinute < 1440)) return null;
  const d = new Date(), n = d.getHours() * 60 + d.getMinutes(), a = b.openMinute, c = b.closeMinute;
  return a === c ? true : a < c ? (n >= a && n < c) : (n >= a || n < c);
}
const hoursText = (b) => (b.openMinute >= 0 && b.closeMinute >= 0)
  ? (b.openMinute === b.closeMinute ? "مفتوح 24 ساعة" : `${fmtMinute(b.openMinute)} – ${fmtMinute(b.closeMinute)}`) : "";
function deliveryText(x) {
  if (!x.delivery) return null;
  const fee = (x.deliveryFee || 0) <= 0 ? "توصيل مجاني" : `توصيل ${money(x.deliveryFee)} ر.س`;
  return `🛵 ${fee}${(x.deliveryMinOrder || 0) > 0 ? ` • للطلبات فوق ${money(x.deliveryMinOrder)} ر.س` : ""}`;
}
function badge(o) {
  if (isUpcoming(o)) return { t: "قريباً", soon: true };
  const p = effectivePercent(o); return p >= 1 ? { t: `-${p}%` } : null;
}
function nearestBranch(o) {
  const bs = branchesOf(o);
  if (!state.me) return { b: bs[0], d: null };
  let best = bs[0], bd = Infinity;
  bs.forEach((b) => { const d = km(state.me, { lat: b.latitude, lng: b.longitude }); if (d < bd) { bd = d; best = b; } });
  return { b: best, d: bd };
}
const waDigits = (p) => {
  let d = String(p || "").replace(/\D/g, "");
  if (d.startsWith("05") && d.length === 10) d = "966" + d.slice(1);
  else if (d.startsWith("5") && d.length === 9) d = "966" + d;
  return d;
};
const offerUrl = (o) => `${location.origin}/o/${encodeURIComponent(o.id)}`;
const storeUrl = (uid) => `${location.origin}/s/${encodeURIComponent(uid)}`;
const bump = (id, field) => updateDoc(doc(db, "offers", id), { [field]: increment(1) }).catch(() => {});

// ---------------- الخريطة ----------------
const map = L.map("map", { zoomControl: false, attributionControl: true }).setView([21.5433, 39.1728], 12);
L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 19,
  attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(map);
map.attributionControl.setPrefix(false);
const offerLayer = L.layerGroup().addTo(map);
const storeLayer = L.layerGroup().addTo(map);
let meMarker = null, fitted = false;

function markerHtml(o, extra, on) {
  const bd = badge(o);
  const logo = o.logoUrl ? `<img class="lg" src="${esc(cld(o.logoUrl, 80))}" alt="">` : "";
  return `<div class="mk${on ? " on" : ""}"><div class="ring"><img src="${esc(thumb(o, 140))}" alt="" loading="lazy"></div>${logo}`
    + (extra > 0 ? `<span class="more">+${extra}</span>` : "")
    + (bd ? `<span class="pct${bd.soon ? " soon" : ""}">${esc(bd.t)}</span>` : "") + `</div>`;
}

// ---------------- الفلترة ----------------
function visibleOffers() {
  const now = Date.now(), q = norm(state.q), catKey = state.cat.replace(/[^\p{L}\s]/gu, "").trim();
  return state.offers.filter((o) => {
    if (isExpired(o, now) || (o.reportsCount || 0) >= AUTO_HIDE_REPORTS) return false;
    if (state.cat !== "الكل" && !String(o.cuisineType || "").includes(catKey)) return false;
    if (!q) return true;
    const hay = [o.restaurantName, o.offerTitle, o.description, o.district, o.cuisineType, o.itemsScope, ...(o.tags || []),
      ...branchesOf(o).flatMap((b) => [b.district, b.landmark, b.name])];
    return hay.some((h) => norm(h).includes(q));
  }).map((o) => ({ o, ...nearestBranch(o) }))
    .sort((a, b) => (b.o.isFeatured ? 1 : 0) - (a.o.isFeatured ? 1 : 0) || (a.d ?? 0) - (b.d ?? 0));
}

// ---------------- الرسم ----------------
function render() {
  const list = visibleOffers();
  // أيقونات الخريطة: لكل فرع، ونجمع عروض نفس المتجر في نفس المكان (+N)
  offerLayer.clearLayers();
  const groups = new Map();
  list.forEach(({ o }) => branchesOf(o).forEach((b) => {
    if (!b.latitude && !b.longitude) return;
    const key = `${o.merchantId || o.restaurantName}|${Math.round(b.latitude * 20000)}|${Math.round(b.longitude * 20000)}`;
    if (!groups.has(key)) groups.set(key, { b, items: [] });
    groups.get(key).items.push(o);
  }));
  groups.forEach(({ b, items }) => {
    items.sort((x, y) => (y.isFeatured ? 1 : 0) - (x.isFeatured ? 1 : 0) || effectivePercent(y) - effectivePercent(x));
    const top = items[0], on = state.selected === top.id;
    const m = L.marker([b.latitude, b.longitude], {
      icon: L.divIcon({ html: markerHtml(top, items.length - 1, on), className: "", iconSize: [60, 72], iconAnchor: [30, 36] }),
      zIndexOffset: on ? 1000 : (top.isFeatured ? 500 : 0)
    }).addTo(offerLayer);
    m.on("click", () => { selectOffer(top.id, false); scrollCardIntoView(top.id); });
  });

  // نقاط المتاجر الهادئة (اللي ما عندها عروض ظاهرة الحين)
  storeLayer.clearLayers();
  const withOffers = new Set(list.map(({ o }) => o.merchantId));
  if (!state.q && state.cat === "الكل") {
    state.stores.filter((s) => !withOffers.has(s.uid)).forEach((s) => (s.branches || []).forEach((b) => {
      if (!b.latitude && !b.longitude) return;
      L.marker([b.latitude, b.longitude], { icon: L.divIcon({ html: '<div class="dot"></div>', className: "", iconSize: [14, 14] }) })
        .addTo(storeLayer).on("click", () => openStore(s.uid, true));
    }));
  }

  // البطاقات
  const car = $("carousel");
  car.innerHTML = list.map(({ o, b, d }) => {
    const bd = badge(o), rem = remainingText(o), op = openNow(b);
    const meta = [b.district || o.district, distText(d), o.delivery ? "🛵" : ""].filter(Boolean).join(" • ");
    return `<button class="card${state.selected === o.id ? " on" : ""}" data-id="${esc(o.id)}">
      <div class="thumb"><img src="${esc(thumb(o))}" alt="" loading="lazy">${bd ? `<span class="badge${bd.soon ? " soon" : ""}">${esc(bd.t)}</span>` : ""}</div>
      <div class="info">
        <div class="store-row">${o.logoUrl ? `<img class="logo" src="${esc(cld(o.logoUrl, 60))}" alt="">` : ""}<span>${esc(o.restaurantName)}</span></div>
        <div class="title">${esc(o.offerTitle)}</div>
        <div class="price">${esc(priceLabel(o))}${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)}</s>` : ""}</div>
        <div class="meta">${esc(meta)}</div>
        <div class="pills">${op === true ? '<span class="pill open">● مفتوح</span>' : op === false ? '<span class="pill closed">● مغلق</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
      </div></button>`;
  }).join("");
  car.classList.toggle("hidden", list.length === 0);
  const empty = $("empty");
  if (!list.length && state.offers.length) {
    empty.innerHTML = `<b>ما لقينا عروض مطابقة</b>جرّب كلمة ثانية أو تصنيف ثاني`;
    empty.classList.remove("hidden");
  } else if (!state.offers.length && loaded) {
    empty.innerHTML = `<b>لا توجد عروض حالياً</b>ارجع قريب، المحلات تنزّل عروضها أول بأول 🔥`;
    empty.classList.remove("hidden");
  } else empty.classList.add("hidden");

  if (!fitted && list.length && !state.me && !state.view) {
    fitted = true;
    const pts = list.flatMap(({ o }) => branchesOf(o).map((b) => [b.latitude, b.longitude])).filter(([a, c]) => a || c);
    if (pts.length) map.fitBounds(pts, { paddingTopLeft: [40, 140], paddingBottomRight: [40, 260], maxZoom: 15 });
  }
}

function scrollCardIntoView(id) {
  const el = document.querySelector(`.card[data-id="${CSS.escape(id)}"]`);
  if (el) el.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
}

function selectOffer(id, openSheet = true) {
  state.selected = id;
  const o = state.offers.find((x) => x.id === id);
  if (o) {
    const { b } = nearestBranch(o);
    map.panTo([b.latitude, b.longitude], { animate: true });
  }
  render();
  if (openSheet && o) openOffer(id, true);
}

// ---------------- اللوحة ----------------
function showSheet(html) {
  $("sheetBody").innerHTML = html;
  $("sheetBody").scrollTop = 0;
  $("sheet").classList.remove("hidden");
  $("sheetBackdrop").classList.remove("hidden");
}
function hideSheet() {
  $("sheet").classList.add("hidden");
  $("sheetBackdrop").classList.add("hidden");
  state.view = null;
}

function galleryHtml(o) {
  const items = gallery(o);
  const inner = items.map((u) => o.mediaType === "VIDEO"
    ? `<video src="${esc(u)}" poster="${esc(videoThumb(u, 800))}" controls playsinline preload="none"></video>`
    : `<img src="${esc(cld(u, 900))}" data-full="${esc(cld(u, 1600))}" alt="">`).join("");
  return `<div class="gallery">${inner}</div>${items.length > 1 ? `<div class="dots">${items.map(() => "<i></i>").join("")}</div>` : ""}`;
}

function openOffer(id, push) {
  const o = state.offers.find((x) => x.id === id);
  if (!o) { if (loaded) { toast("العرض انتهى أو غير متاح"); go("/", true); } return; }
  state.view = { type: "o", id };
  if (push) history.pushState({ v: "o", id }, "", `/o/${encodeURIComponent(id)}`);
  document.title = `${o.offerTitle} — ${o.restaurantName} | حيّنا`;
  if (!sessionStorage.getItem("v_" + id)) { sessionStorage.setItem("v_" + id, "1"); bump(id, "viewsCount"); }

  const bs = branchesOf(o);
  let br = bs.find((b) => b.id === state.branchId) || nearestBranch(o).b;
  const draw = () => {
    const d = state.me ? km(state.me, { lat: br.latitude, lng: br.longitude }) : null;
    const rem = remainingText(o), op = openNow(br), tiers = tiersText(o), starts = startsText(o), del = deliveryText(o);
    const store = state.stores.find((s) => s.uid === o.merchantId);
    showSheet(`${galleryHtml(o)}
      <div class="pad">
        <div class="store-link">
          <div class="name">${o.logoUrl ? `<img src="${esc(cld(o.logoUrl, 100))}" alt="">` : ""}<span>${esc(o.restaurantName)}${store && store.verified ? ' <span class="verified" title="متجر موثّق">✔</span>' : ""}</span></div>
          ${store ? `<button class="btn-soft" data-store="${esc(store.uid)}">🏪 صفحة المتجر ›</button>` : ""}
        </div>
        <h1 class="title">${esc(o.offerTitle)}</h1>
        <div class="pills">${o.grandOpening ? '<span class="pill">🎉 افتتاح</span>' : ""}${op === true ? '<span class="pill open">● مفتوح الآن</span>' : op === false ? '<span class="pill closed">● مغلق الآن</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
        <div class="big-price"><span class="p">${esc(priceLabel(o, true))}</span>${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)} ر.س</s>` : ""}${!isPercentOnly(o) && effectivePercent(o) > 0 ? `<span class="pill urgent">خصم ${effectivePercent(o)}% 🔥</span>` : ""}</div>
        ${tiers || starts ? `<div class="box">${starts ? `<div class="soon">🔜 ${esc(starts)}</div>` : ""}${tiers ? `<div>📅 ${esc(tiers)}</div>` : ""}</div>` : ""}
        ${del ? `<div class="info-pill">${esc(del)}</div>` : ""}
        ${(o.tags || []).length ? `<div class="tags">${o.tags.map((t) => `<span>#${esc(t)}</span>`).join("")}</div>` : ""}
        ${o.description ? `<p class="desc">${esc(o.description)}</p>` : ""}
        ${bs.length > 1 ? `<div class="section-title">📍 الفروع (${bs.length})</div><div class="branches">${bs.map((b) => {
          const bd = state.me ? km(state.me, { lat: b.latitude, lng: b.longitude }) : null;
          return `<button class="branch${b === br ? " on" : ""}" data-branch="${esc(b.id)}"><div class="b-main"><div class="b-name">${esc(b.name || b.district || "الفرع")}</div><div class="b-sub">${esc([b.district, b.landmark, hoursText(b)].filter(Boolean).join(" • "))}</div></div><div class="b-sub">${esc(distText(bd))}</div></button>`;
        }).join("")}</div>` : `<div class="info-pill gray">📍 ${esc([br.district ? (br.district.startsWith("حي") ? br.district : "حي " + br.district) : "", br.landmark, br.region].filter(Boolean).join(" • "))}${d != null ? ` — ${esc(distText(d))} عنك` : ""}</div>`}
        <div class="actions">
          <a class="btn dir" data-act="dir" href="https://www.google.com/maps/dir/?api=1&destination=${br.latitude},${br.longitude}" target="_blank" rel="noopener">🧭 الاتجاهات إلى المحل</a>
          <a class="btn wa" data-act="wa" href="https://wa.me/${waDigits(br.phone || o.phone)}?text=${encodeURIComponent(`مرحباً، أود طلب العرض: ${o.offerTitle} (${priceLabel(o)}) المعلن عنه في حيّنا\n${offerUrl(o)}`)}" target="_blank" rel="noopener">💬 طلب واتساب</a>
          <a class="btn call" href="tel:${esc(br.phone || o.phone)}">📞 اتصال</a>
          <button class="btn share" data-act="share">↗︎ شارك العرض</button>
        </div>
      </div>`);
    const body = $("sheetBody");
    body.querySelectorAll("[data-branch]").forEach((el) => el.onclick = () => {
      br = bs.find((b) => b.id === el.dataset.branch) || br; state.branchId = br.id;
      map.panTo([br.latitude, br.longitude]); draw();
    });
    body.querySelector("[data-store]")?.addEventListener("click", (e) => openStore(e.currentTarget.dataset.store, true));
    body.querySelector('[data-act="dir"]').addEventListener("click", () => bump(o.id, "directionsCount"));
    body.querySelector('[data-act="wa"]').addEventListener("click", () => bump(o.id, "whatsappCount"));
    body.querySelector('[data-act="share"]').addEventListener("click", () => share(
      `🔥 ${o.offerTitle}\n📍 ${o.restaurantName}${o.district ? " — " + o.district : ""}\n💰 ${priceLabel(o, true)}`, offerUrl(o), o.offerTitle));
    body.querySelectorAll(".gallery img").forEach((im) => im.onclick = () => openViewer(im.dataset.full));
  };
  draw();
}

function openStore(uid, push) {
  const s = state.stores.find((x) => x.uid === uid);
  if (!s) { if (storesLoaded) { toast("صفحة المتجر غير متاحة"); go("/", true); } return; }
  state.view = { type: "s", id: uid };
  if (push) history.pushState({ v: "s", id: uid }, "", `/s/${encodeURIComponent(uid)}`);
  document.title = `${s.name} | حيّنا`;
  const offers = visibleOffersAll().filter((o) => o.merchantId === uid);
  const del = deliveryText(s);
  const label = /مطاعم|مقاهي/.test(s.category || "") ? "المنيو" : /خياط|حلاقة|خدمات/.test(s.category || "") ? "الخدمات والأسعار" : "المنتجات والأسعار";
  const phone = s.phone || (s.branches || [])[0]?.phone || "";
  showSheet(`${s.coverUrl ? `<img class="cover" src="${esc(cld(s.coverUrl, 1000))}" alt="">` : `<div class="cover"></div>`}
    <div class="pad">
      <div class="store-head">${s.logoUrl ? `<img src="${esc(cld(s.logoUrl, 200))}" alt="">` : ""}<div class="nm">${esc(s.name)}${s.verified ? ' <span class="verified" title="متجر موثّق">✔ موثّق</span>' : ""}</div></div>
      ${s.category ? `<div class="pills"><span class="pill">${esc(s.category)}</span></div>` : ""}
      ${s.priceFrom > 0 ? `<div class="box">💰 الأسعار تبدأ من ${money(s.priceFrom)} ر.س</div>` : ""}
      ${del ? `<div class="info-pill">${esc(del)}</div>` : ""}
      ${s.about ? `<p class="desc">${esc(s.about)}</p>` : ""}
      ${phone ? `<div class="actions"><a class="btn wa" href="https://wa.me/${waDigits(phone)}" target="_blank" rel="noopener">💬 واتساب</a><a class="btn call" href="tel:${esc(phone)}">📞 اتصال</a></div>` : ""}
      ${offers.length ? `<div class="section-title">🔥 العروض الحالية (${offers.length})</div><div class="hscroll">${offers.map((o) =>
        `<button class="mini" data-offer="${esc(o.id)}"><img src="${esc(thumb(o))}" alt=""><div>${esc(o.offerTitle)}<small>${esc(priceLabel(o))}</small></div></button>`).join("")}</div>` : ""}
      ${(s.menuImages || []).length ? `<div class="section-title">📋 ${label}</div><div class="hscroll">${s.menuImages.map((u) =>
        `<img class="menu-img" src="${esc(cld(u, 400))}" data-full="${esc(cld(u, 1600))}" alt="">`).join("")}</div>` : ""}
      ${(s.branches || []).length ? `<div class="section-title">📍 الفروع (${s.branches.length})</div><div class="branches">${s.branches.map((b) => {
        const d = state.me ? km(state.me, { lat: b.latitude, lng: b.longitude }) : null, op = openNow(b);
        return `<a class="branch" href="https://www.google.com/maps/dir/?api=1&destination=${b.latitude},${b.longitude}" target="_blank" rel="noopener"><div class="b-main"><div class="b-name">${esc(b.name || b.district || "الفرع")} ${op === true ? '<span class="pill open">مفتوح</span>' : op === false ? '<span class="pill closed">مغلق</span>' : ""}</div><div class="b-sub">${esc([b.district, b.landmark, hoursText(b)].filter(Boolean).join(" • "))}</div></div><div class="b-sub">${esc(distText(d))} 🧭</div></a>`;
      }).join("")}</div>` : ""}
      <button class="btn share" data-act="share">↗︎ شارك صفحة المتجر</button>
    </div>`);
  const body = $("sheetBody");
  body.querySelectorAll("[data-offer]").forEach((el) => el.onclick = () => { state.selected = el.dataset.offer; render(); openOffer(el.dataset.offer, true); });
  body.querySelectorAll(".menu-img").forEach((im) => im.onclick = () => openViewer(im.dataset.full));
  body.querySelector('[data-act="share"]').onclick = () => share(`🏪 ${s.name} على حيّنا`, storeUrl(uid), s.name);
}
const visibleOffersAll = () => state.offers.filter((o) => !isExpired(o) && (o.reportsCount || 0) < AUTO_HIDE_REPORTS);

async function share(text, url, title) {
  if (navigator.share) { try { await navigator.share({ title, text, url }); return; } catch (e) { if (e.name === "AbortError") return; } }
  try { await navigator.clipboard.writeText(`${text}\n${url}`); toast("نسخنا الرابط ✅"); } catch { prompt("انسخ الرابط:", url); }
}
function openViewer(src) { $("viewerImg").src = src; $("viewer").classList.remove("hidden"); }

// ---------------- التنقل (روابط /o/ و /s/) ----------------
function route() {
  const m = location.pathname.match(/^\/(o|s)\/([^/]+)/);
  if (!m) { hideSheet(); document.title = "حيّنا — عروض المحلات حولك على الخريطة"; return; }
  const id = decodeURIComponent(m[2]);
  if (m[1] === "o") { state.selected = id; openOffer(id, false); render(); const o = state.offers.find((x) => x.id === id); if (o) { const { b } = nearestBranch(o); map.setView([b.latitude, b.longitude], 15); } }
  else openStore(id, false);
}
function go(path, replace) { (replace ? history.replaceState : history.pushState).call(history, {}, "", path); route(); }
window.addEventListener("popstate", route);
const closeSheet = () => { if (location.pathname !== "/") history.length > 1 && history.state ? history.back() : go("/", true); else hideSheet(); };
$("sheetClose").onclick = closeSheet;
$("sheetBackdrop").onclick = closeSheet;
$("viewerClose").onclick = () => $("viewer").classList.add("hidden");
$("viewer").onclick = (e) => { if (e.target.id === "viewer") $("viewer").classList.add("hidden"); };

// ---------------- البحث والتصنيفات ----------------
$("chips").innerHTML = CATEGORIES.map((c) => `<button class="chip${c === "الكل" ? " on" : ""}" data-cat="${esc(c)}">${esc(c)}</button>`).join("");
$("chips").onclick = (e) => {
  const b = e.target.closest("[data-cat]"); if (!b) return;
  state.cat = b.dataset.cat; fitted = false;
  document.querySelectorAll(".chip").forEach((x) => x.classList.toggle("on", x === b));
  render();
};
let qt;
$("q").oninput = (e) => { state.q = e.target.value; $("clearQ").classList.toggle("hidden", !state.q); clearTimeout(qt); qt = setTimeout(() => { fitted = false; render(); }, 200); };
$("clearQ").onclick = () => { $("q").value = ""; state.q = ""; $("clearQ").classList.add("hidden"); render(); };
$("carousel").onclick = (e) => { const c = e.target.closest(".card"); if (c) selectOffer(c.dataset.id, true); };

// ---------------- موقعي ----------------
function locate(center) {
  if (!navigator.geolocation) return toast("المتصفح ما يدعم تحديد الموقع");
  navigator.geolocation.getCurrentPosition((p) => {
    state.me = { lat: p.coords.latitude, lng: p.coords.longitude };
    if (meMarker) meMarker.setLatLng([state.me.lat, state.me.lng]);
    else meMarker = L.marker([state.me.lat, state.me.lng], { icon: L.divIcon({ html: '<div class="me"></div>', className: "", iconSize: [18, 18] }), interactive: false }).addTo(map);
    if (center) map.setView([state.me.lat, state.me.lng], 14);
    render();
  }, () => { if (center) toast("فعّل الموقع عشان نرتّب العروض حسب الأقرب لك"); }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
}
$("locate").onclick = () => locate(true);
// لو سبق وسمح بالموقع، نجيبه بهدوء
navigator.permissions?.query({ name: "geolocation" }).then((r) => { if (r.state === "granted") locate(!location.pathname.startsWith("/o/")); }).catch(() => {});

// ---------------- تلميح التثبيت للآيفون ----------------
(() => {
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const standalone = window.navigator.standalone || matchMedia("(display-mode: standalone)").matches;
  let seen = false; try { seen = localStorage.getItem("hayyna_install_tip") === "1"; } catch {}
  if (ios && !standalone && !seen) setTimeout(() => $("installTip").classList.remove("hidden"), 4000);
  $("installClose").onclick = () => { $("installTip").classList.add("hidden"); try { localStorage.setItem("hayyna_install_tip", "1"); } catch {} };
})();

// ---------------- البيانات ----------------
let loaded = false, storesLoaded = false;
onSnapshot(query(collection(db, "offers"), where("isApproved", "==", true)), (snap) => {
  state.offers = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
  const first = !loaded; loaded = true;
  render();
  if (first) route();
}, (e) => { console.error(e); toast("تعذّر تحميل العروض، حدّث الصفحة"); });
onSnapshot(query(collection(db, "stores"), where("approved", "==", true)), (snap) => {
  state.stores = snap.docs.map((d) => ({ ...d.data(), uid: d.id }));
  const first = !storesLoaded; storesLoaded = true;
  render();
  if (first && location.pathname.startsWith("/s/")) route();
  // زر "صفحة المتجر" يحتاج بيانات المتاجر: نعيد رسم العرض المفتوح لو وصلت بعده
  else if (first && state.view?.type === "o" && !$("sheet").classList.contains("hidden")) openOffer(state.view.id, false);
}, (e) => console.error(e));

// تحديث العد التنازلي والخصم المتدرّج كل دقيقة
setInterval(render, 60000);
