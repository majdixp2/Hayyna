// حيّنا على الويب — نفس تجربة التطبيق: خريطة، عروض، مفضلة، حسابي (زبون / تاجر).
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, OAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, onAuthStateChanged, signOut,
  sendSignInLinkToEmail, isSignInWithEmailLink, signInWithEmailLink, deleteUser
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  getFirestore, collection, doc, onSnapshot, query, where, updateDoc, setDoc, getDoc, addDoc, deleteDoc, increment, serverTimestamp, Timestamp
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig, FIRESTORE_DB_ID, AUTO_HIDE_REPORTS, ADMIN_EMAILS, MAPS_WEB_KEY } from "../config.js";
import * as X from "./logic.js";
import { createMap } from "./map.js";

const { esc, cld, thumb, priceLabel, isPercentOnly, effectivePercent, branchesOf, km, distText, openNow, hoursText,
  remainingText, tiersText, startsText, deliveryText, badge, waDigits, norm, districtLabel, money, gallery, videoThumb } = X;

export const fbApp = initializeApp(firebaseConfig);
export const auth = getAuth(fbApp);
auth.languageCode = "ar"; // رسائل الدخول بالإيميل تطلع بالعربي
export const db = getFirestore(fbApp, FIRESTORE_DB_ID);
export const $ = (id) => document.getElementById(id);

export const state = {
  offers: [], stores: [], q: "", cat: "الكل", sort: "near", openOnly: false, browse: null,
  me: null, selected: null, branchId: null, view: null, tab: "map",
  favs: new Set(), user: null, role: null, profile: null, scope: "all", roleLoading: false, pendingRole: null,
  config: {}, loaded: false, storesLoaded: false, referrals: [],
};

// ---------------- الإحالات: مين دعا المحل ----------------
// اللي يفتح رابط فيه ?ref=… نحفظ صاحب الدعوة 60 يوم، ولو سجّل محل تنحسب الدعوة له
(() => {
  try {
    const u = new URL(location.href), r = u.searchParams.get("ref");
    if (r && /^[A-Za-z0-9_-]{6,128}$/.test(r)) {
      localStorage.setItem("hayyna_ref", JSON.stringify({ uid: r, at: Date.now() }));
      u.searchParams.delete("ref"); history.replaceState(history.state, "", u.pathname + (u.search || "") + u.hash);
    }
  } catch {}
})();
export function pendingRef() {
  try { const r = JSON.parse(localStorage.getItem("hayyna_ref") || "null"); return r && Date.now() - r.at < 60 * 864e5 ? r.uid : null; } catch { return null; }
}
export const clearRef = () => { try { localStorage.removeItem("hayyna_ref"); } catch {} };
export const withRef = (link) => state.user ? link + (link.includes("?") ? "&" : "?") + "ref=" + state.user.uid : link;
export const POINTS = { verified: 50, offer: 50 };
export const TIERS = [
  { min: 0, name: "سفير الحي", ico: "🏅" }, { min: 1000, name: "سفير فضي", ico: "🥈" },
  { min: 5000, name: "سفير ذهبي", ico: "🥇" }, { min: 10000, name: "سفير ماسي", ico: "💎" },
];
export const pointsOf = (list) => list.reduce((a, r) => a + (r.verifiedPts || 0) + (r.offerPts || 0), 0);
export function tierOf(pts) {
  let i = 0; TIERS.forEach((t, k) => { if (pts >= t.min) i = k; });
  const cur = TIERS[i], next = TIERS[i + 1];
  return { ...cur, next, pct: next ? Math.min(100, Math.round((pts - cur.min) / (next.min - cur.min) * 100)) : 100, left: next ? next.min - pts : 0 };
}

// ---------------- أدوات الواجهة (مشتركة مع لوحة التاجر) ----------------
export const toast = (m, long) => { const t = $("toast"); t.textContent = m; t.classList.remove("hidden"); clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add("hidden"), long ? 4500 : 2600); };
export function showSheet(html) {
  $("sheetBody").innerHTML = html; $("sheetBody").scrollTop = 0;
  $("sheet").classList.remove("hidden"); $("sheetBackdrop").classList.remove("hidden");
}
export function hideSheet() { $("sheet").classList.add("hidden"); $("sheetBackdrop").classList.add("hidden"); state.view = null; }
let pageOnClose = null;
export function openPage(title, body, foot = "", onClose = null) {
  $("pageTitle").textContent = title; $("pageBody").innerHTML = body; $("pageFoot").innerHTML = foot;
  $("pageFoot").classList.toggle("hidden", !foot); $("pageBody").scrollTop = 0;
  $("page").classList.remove("hidden"); pageOnClose = onClose;
  history.pushState({ page: 1 }, "", location.pathname);
}
export function closePage(fromPop) {
  if ($("page").classList.contains("hidden")) return;
  if (pageOnClose && pageOnClose() === false) { if (fromPop) history.pushState({ page: 1 }, "", location.pathname); return; }
  $("page").classList.add("hidden"); $("pageBody").innerHTML = "";
  if (!fromPop && history.state?.page) history.back();
}
$("pageClose").onclick = () => closePage(false);

export async function uploadImage(file, maxSide = 1600) {
  // نصغّر الصورة قبل الرفع (أسرع وأوفر)
  const blob = await new Promise((resolve) => {
    if (!file.type.startsWith("image/") || file.type === "image/gif") return resolve(file);
    const img = new Image(); const url = URL.createObjectURL(file);
    img.onload = () => {
      const s = Math.min(1, maxSide / Math.max(img.width, img.height));
      const c = document.createElement("canvas"); c.width = Math.round(img.width * s); c.height = Math.round(img.height * s);
      c.getContext("2d").drawImage(img, 0, 0, c.width, c.height); URL.revokeObjectURL(url);
      c.toBlob((b) => resolve(b || file), "image/jpeg", 0.85);
    };
    img.onerror = () => resolve(file); img.src = url;
  });
  const isVideo = file.type.startsWith("video/");
  const fd = new FormData(); fd.append("upload_preset", "hayyna_offers"); fd.append("file", isVideo ? file : blob, isVideo ? "offer.mp4" : "offer.jpg");
  const r = await fetch(`https://api.cloudinary.com/v1_1/ffhqn5dw/${isVideo ? "video" : "image"}/upload`, { method: "POST", body: fd });
  const j = await r.json().catch(() => ({}));
  if (!r.ok || !j.secure_url) throw new Error("تعذّر رفع الملف" + (j.error?.message ? ": " + j.error.message : ""));
  return j.secure_url;
}

const bump = (id, field) => updateDoc(doc(db, "offers", id), { [field]: increment(1) }).catch(() => {});
const offerUrl = (o) => `${location.origin}/o/${encodeURIComponent(o.id)}`;
const storeUrl = (uid) => `${location.origin}/s/${encodeURIComponent(uid)}`;
const heartSvg = (on) => `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="${on ? "#E05A47" : "none"}" stroke="${on ? "#E05A47" : "currentColor"}" stroke-width="2" d="M12 20.5l-1.2-1.1C6.2 15.2 3 12.3 3 8.8 3 6.1 5.1 4 7.8 4c1.6 0 3.1.8 4.2 2 1.1-1.2 2.6-2 4.2-2C18.9 4 21 6.1 21 8.8c0 3.5-3.2 6.4-7.8 10.6L12 20.5z"/></svg>`;

function nearestBranch(o) {
  const bs = branchesOf(o);
  const ref = state.browse || state.me;
  if (!ref) return { b: bs[0], d: null };
  let best = bs[0], bd = Infinity;
  bs.forEach((b) => { const d = km(ref, { lat: b.latitude, lng: b.longitude }); if (d < bd) { bd = d; best = b; } });
  const dMe = state.me ? km(state.me, { lat: best.latitude, lng: best.longitude }) : null;
  return { b: best, d: dMe, dSort: bd };
}

// ---------------- المفضلة ----------------
try { JSON.parse(localStorage.getItem("hayyna_favs") || "[]").forEach((id) => state.favs.add(id)); } catch {}
function saveFavsLocal() { try { localStorage.setItem("hayyna_favs", JSON.stringify([...state.favs])); } catch {} }
export function toggleFav(id) {
  if (state.favs.has(id)) state.favs.delete(id); else { state.favs.add(id); toast("انحفظ في المفضلة ❤️"); }
  saveFavsLocal();
  if (state.user && state.role) setDoc(doc(db, "users", state.user.uid), { role: state.role, favorites: [...state.favs].slice(0, 500) }, { merge: true }).catch(() => {});
  renderAll();
}

// ---------------- الفلترة ----------------
function filtered({ ignoreQuery = false } = {}) {
  const now = Date.now(), q = ignoreQuery ? "" : norm(state.q), catKey = state.cat.replace(/[^\p{L}\s]/gu, "").trim();
  const list = state.offers.filter((o) => {
    if (X.isExpired(o, now) || (o.reportsCount || 0) >= AUTO_HIDE_REPORTS) return false;
    if (state.cat !== "الكل" && !String(o.cuisineType || "").includes(catKey)) return false;
    if (state.openOnly && !branchesOf(o).some((b) => openNow(b) === true)) return false;
    if (!q) return true;
    const hay = [o.restaurantName, o.offerTitle, o.description, o.district, o.cuisineType, o.itemsScope, ...(o.tags || []),
      ...branchesOf(o).flatMap((b) => [b.district, b.landmark, b.name])];
    return hay.some((h) => norm(h).includes(q));
  }).map((o) => ({ o, ...nearestBranch(o) }));
  const feat = (a, b) => (b.o.isFeatured ? 1 : 0) - (a.o.isFeatured ? 1 : 0);
  if (state.sort === "cheap") list.sort((a, b) => feat(a, b) || ((a.o.price > 0 ? a.o.price : 1e9) - (b.o.price > 0 ? b.o.price : 1e9)));
  else if (state.sort === "discount") list.sort((a, b) => feat(a, b) || effectivePercent(b.o) - effectivePercent(a.o));
  else if (state.sort === "new") list.sort((a, b) => feat(a, b) || (X.ms(b.o.createdAt) || 0) - (X.ms(a.o.createdAt) || 0));
  else list.sort((a, b) => feat(a, b) || (a.dSort ?? 0) - (b.dSort ?? 0));
  if (state.scope === "all") return list;
  return list.filter(({ o }) => inScope(o, state.scope));
}

// ---------------- النطاق: حيّك / حولك 5 كم / كل الأحياء ----------------
const NEAR_KM = 5;
function userDistrict() {
  // أقرب حي معروف لموقع المستخدم (من فروع العروض والمتاجر) في حدود 1.5 كم
  if (!state.me) return "";
  let best = "", bd = 1.5;
  const check = (b) => { if (!b?.district || !(b.latitude || b.longitude)) return; const d = km(state.me, { lat: b.latitude, lng: b.longitude }); if (d < bd) { bd = d; best = b.district; } };
  state.offers.forEach((o) => branchesOf(o).forEach(check));
  state.stores.forEach((s) => (s.branches || []).forEach(check));
  return best;
}
function inScope(o, sc) {
  if (sc === "district") { const d = norm(userDistrict().replace(/^حي\s+/, "")); return !!d && branchesOf(o).some((b) => norm((b.district || "").replace(/^حي\s+/, "")) === d); }
  if (sc === "near") return !!state.me && branchesOf(o).some((b) => km(state.me, { lat: b.latitude, lng: b.longitude }) <= NEAR_KM);
  return true;
}
function cycleScope() {
  if (!state.me) { locate(true); return toast("فعّل الموقع عشان نعرض عروض حيّك 📍"); }
  state.browse = null;
  const prev = state.scope; state.scope = "all";
  const base = filtered(), count = (sc) => base.filter(({ o }) => inScope(o, sc)).length, dist = userDistrict();
  if (prev === "all") {
    if (dist && count("district") > 0) state.scope = "district";
    else if (count("near") > 0) { state.scope = "near"; toast(dist ? "ما فيه عروض في حيّك للحين، شوف اللي حولك 🎯" : "عروض حولك (5 كم) 🎯"); }
    else toast("ما فيه عروض قريبة منك للحين، نعرض لك كل الأحياء");
  } else if (prev === "district") state.scope = count("near") > 0 ? "near" : "all";
  else state.scope = "all";
  if (map) {
    if (state.scope === "district") map.setView(state.me.lat, state.me.lng, 15);
    else if (state.scope === "near") map.setView(state.me.lat, state.me.lng, 12);
    else { fitted = false; }
    map.setCircle(state.scope === "near" ? { lat: state.me.lat, lng: state.me.lng, radius: NEAR_KM * 1000 } : null);
  }
  const btn = $("scopeBtn"); btn.classList.remove("flash"); void btn.offsetWidth; btn.classList.add("flash");
  renderAll();
}
function renderScope(count) {
  const btn = $("scopeBtn"); if (!btn) return;
  const dist = userDistrict();
  const n = `${count} ${count === 1 ? "عرض" : "عروض"}`;
  btn.classList.remove("hidden");
  btn.classList.toggle("active", state.scope !== "all");
  btn.innerHTML = state.scope === "district" ? `📍 ${esc(districtLabel(dist) || "حيّك")} • ${n} <b>⇄</b>`
    : state.scope === "near" ? `🎯 حولك 5 كم • ${n} <b>⇄</b>`
    : dist ? `📍 أنت في: ${esc(districtLabel(dist))} <b>⇄</b>` : `📍 عروض حولك <b>⇄</b>`;
}

// ---------------- الخريطة ----------------
let map = null, fitted = false;
function markerHtml(o, extra, on) {
  const bd = badge(o);
  const logo = o.logoUrl ? `<img class="lg" src="${esc(cld(o.logoUrl, 80))}" alt="">` : "";
  return `<div class="mk${on ? " on" : ""}"><div class="ring"><img src="${esc(thumb(o, 140))}" alt="" loading="lazy"></div>${logo}`
    + (extra > 0 ? `<span class="more">+${extra}</span>` : "")
    + (bd ? `<span class="pct${bd.soon ? " soon" : ""}">${esc(bd.t)}</span>` : "") + `</div>`;
}
function renderMap(list) {
  if (!map) return;
  const groups = new Map();
  list.forEach(({ o }) => branchesOf(o).forEach((b) => {
    if (!b.latitude && !b.longitude) return;
    const key = `${o.merchantId || o.restaurantName}|${Math.round(b.latitude * 20000)}|${Math.round(b.longitude * 20000)}`;
    if (!groups.has(key)) groups.set(key, { b, items: [] });
    groups.get(key).items.push(o);
  }));
  const items = [];
  groups.forEach(({ b, items: arr }) => {
    arr.sort((x, y) => (y.isFeatured ? 1 : 0) - (x.isFeatured ? 1 : 0) || effectivePercent(y) - effectivePercent(x));
    const top = arr[0], on = state.selected === top.id;
    items.push({ lat: b.latitude, lng: b.longitude, html: markerHtml(top, arr.length - 1, on), z: on ? 1000 : top.isFeatured ? 500 : 10,
      onClick: () => { state.selected = top.id; renderAll(); scrollCardIntoView(top.id); if (arr.length > 1) openGroup(arr); } });
  });
  map.setMarkers(items);
  const withOffers = new Set(list.map(({ o }) => o.merchantId));
  const dots = [];
  if (!state.q && state.cat === "الكل" && !state.openOnly) {
    state.stores.filter((s) => !withOffers.has(s.uid)).forEach((s) => (s.branches || []).forEach((b) => {
      if (b.latitude || b.longitude) dots.push({ lat: b.latitude, lng: b.longitude, html: '<div class="dot"></div>', z: 1, onClick: () => openStore(s.uid, true) });
    }));
  }
  map.setDots(dots);
  if (!fitted && list.length && !state.me && !state.view && !state.browse) {
    fitted = true;
    map.fitBounds(list.flatMap(({ o }) => branchesOf(o).map((b) => [b.latitude, b.longitude])).filter(([a, c]) => a || c));
  }
}
function openGroup(arr) {
  showSheet(`<div class="pad"><div class="section-title">${esc(arr[0].restaurantName)} — ${arr.length} عروض</div>
    <div class="list">${arr.map((o) => listCard(o)).join("")}</div></div>`);
}

// ---------------- البطاقات ----------------
function carouselCard({ o, b, d }) {
  const bd = badge(o), rem = remainingText(o), op = openNow(b), fav = state.favs.has(o.id);
  const meta = [districtLabel(b.district || o.district), distText(d), o.delivery ? "🛵" : ""].filter(Boolean).join(" • ");
  return `<div class="card${state.selected === o.id ? " on" : ""}" data-id="${esc(o.id)}" role="button">
    <div class="thumb"><img src="${esc(thumb(o))}" alt="" loading="lazy">${bd ? `<span class="badge${bd.soon ? " soon" : ""}">${esc(bd.t)}</span>` : ""}</div>
    <div class="info">
      <div class="store-row">${o.logoUrl ? `<img class="logo" src="${esc(cld(o.logoUrl, 60))}" alt="">` : ""}<span>${esc(o.restaurantName)}</span>
        <button class="heart" data-fav="${esc(o.id)}" aria-label="مفضلة">${heartSvg(fav)}</button></div>
      <div class="title">${esc(o.offerTitle)}</div>
      <div class="price">${esc(priceLabel(o))}${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)}</s>` : ""}</div>
      <div class="meta">${esc(meta)}</div>
      <div class="pills">${op === true ? '<span class="pill open">● مفتوح</span>' : op === false ? '<span class="pill closed">● مغلق</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
    </div></div>`;
}
function listCard(o) {
  const { b, d } = nearestBranch(o), bd = badge(o), rem = remainingText(o), op = openNow(b), fav = state.favs.has(o.id);
  return `<div class="lcard" data-id="${esc(o.id)}" role="button">
    <div class="lthumb"><img src="${esc(thumb(o, 500))}" alt="" loading="lazy">${bd ? `<span class="badge${bd.soon ? " soon" : ""}">${esc(bd.t)}</span>` : ""}
      <button class="heart float" data-fav="${esc(o.id)}" aria-label="مفضلة">${heartSvg(fav)}</button></div>
    <div class="linfo">
      <div class="store-row">${o.logoUrl ? `<img class="logo" src="${esc(cld(o.logoUrl, 60))}" alt="">` : ""}<span>${esc(o.restaurantName)}</span></div>
      <div class="title">${esc(o.offerTitle)}</div>
      <div class="price">${esc(priceLabel(o, true))}${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)}</s>` : ""}</div>
      <div class="meta">${esc([districtLabel(b.district || o.district), distText(d), deliveryText(o) || ""].filter(Boolean).join(" • "))}</div>
      <div class="pills">${op === true ? '<span class="pill open">● مفتوح</span>' : op === false ? '<span class="pill closed">● مغلق</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
    </div></div>`;
}
function scrollCardIntoView(id) {
  const el = document.querySelector(`#carousel .card[data-id="${CSS.escape(id)}"]`);
  if (el) el.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
}

// ---------------- الرسم ----------------
export function renderAll() {
  const list = filtered();
  renderMap(list);
  const car = $("carousel"), sl = car.scrollLeft;
  car.innerHTML = list.map(carouselCard).join("");
  car.scrollLeft = sl;
  car.classList.toggle("hidden", !list.length);
  const empty = $("empty");
  if (state.loaded && !list.length) {
    empty.innerHTML = state.offers.length ? `<b>ما لقينا عروض مطابقة</b>جرّب كلمة ثانية أو شيل الفلاتر` : `<b>لا توجد عروض حالياً</b>المحلات تنزّل عروضها أول بأول 🔥`;
    empty.classList.remove("hidden");
  } else empty.classList.add("hidden");
  $("filterDot").classList.toggle("hidden", !(state.openOnly || state.sort !== "near"));
  $("placeLabel").textContent = state.browse ? state.browse.name : "حولي";
  renderScope(list.length);
  // قائمة العروض
  $("sortbar").innerHTML = [["near", "📍 الأقرب"], ["discount", "🔥 أكبر خصم"], ["cheap", "💰 الأرخص"], ["new", "🆕 الأحدث"]]
    .map(([k, l]) => `<button class="chip${state.sort === k ? " on" : ""}" data-sort="${k}">${l}</button>`).join("")
    + `<button class="chip${state.openOnly ? " on" : ""}" data-open="1">● مفتوح الآن</button>`;
  $("list").innerHTML = list.length ? list.map(({ o }) => listCard(o)).join("") : `<div class="empty-inline">ما فيه عروض مطابقة</div>`;
  // المفضلة
  const favList = state.offers.filter((o) => state.favs.has(o.id) && !X.isExpired(o));
  $("favs").innerHTML = favList.length ? favList.map(listCard).join("")
    : `<div class="empty-inline"><div style="font-size:40px">🤍</div><b>ما عندك مفضلة للحين</b><br>اضغط القلب على أي عرض عشان تحفظه هنا</div>`;
  if (state.tab === "account") renderAccount();
}

// ---------------- تفاصيل العرض ----------------
function galleryHtml(o) {
  const items = gallery(o);
  const inner = items.map((u) => o.mediaType === "VIDEO"
    ? `<video src="${esc(u)}" poster="${esc(videoThumb(u, 800))}" controls playsinline preload="none"></video>`
    : `<img src="${esc(cld(u, 900))}" data-full="${esc(cld(u, 1600))}" alt="">`).join("");
  return `<div class="gallery">${inner}</div>${items.length > 1 ? `<div class="dots">${items.map(() => "<i></i>").join("")}</div>` : ""}`;
}
export function openOffer(id, push) {
  const o = state.offers.find((x) => x.id === id);
  if (!o) { if (state.loaded) { toast("العرض انتهى أو غير متاح"); go("/", true); } return; }
  state.view = { type: "o", id }; state.selected = id;
  if (push) history.pushState({ v: "o", id }, "", `/o/${encodeURIComponent(id)}`);
  document.title = `${o.offerTitle} — ${o.restaurantName} | حيّنا`;
  if (!sessionStorage.getItem("v_" + id)) { sessionStorage.setItem("v_" + id, "1"); bump(id, "viewsCount"); }
  const bs = branchesOf(o);
  let br = bs.find((b) => b.id === state.branchId) || nearestBranch(o).b;
  if (map && br) map.panTo(br.latitude, br.longitude);
  const draw = () => {
    const d = state.me ? km(state.me, { lat: br.latitude, lng: br.longitude }) : null;
    const rem = remainingText(o), op = openNow(br), tiers = tiersText(o), starts = startsText(o), del = deliveryText(o);
    const store = state.stores.find((s) => s.uid === o.merchantId), fav = state.favs.has(o.id);
    const isOwner = state.user && state.user.uid === o.merchantId;
    showSheet(`${galleryHtml(o)}
      <div class="pad">
        <div class="store-link">
          <div class="name">${o.logoUrl ? `<img src="${esc(cld(o.logoUrl, 100))}" alt="">` : ""}<span>${esc(o.restaurantName)}${store && store.verified ? ' <span class="verified" title="متجر موثّق">✔</span>' : ""}</span></div>
          ${store ? `<button class="btn-soft" data-store="${esc(store.uid)}">🏪 صفحة المتجر ›</button>` : ""}
        </div>
        <h1 class="title">${esc(o.offerTitle)}</h1>
        <div class="pills">${o.grandOpening ? '<span class="pill">🎉 افتتاح</span>' : ""}${op === true ? '<span class="pill open">● مفتوح الآن</span>' : op === false ? '<span class="pill closed">● مغلق الآن</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
        <div class="row3">
          <button class="btn-out" data-act="fav">${heartSvg(fav)} ${fav ? "محفوظ" : "حفظ"}</button>
          <button class="btn-out" data-act="share">↗︎ مشاركة</button>
          ${isOwner ? `<button class="btn-out" data-act="edit">✎ تعديل</button>` : `<button class="btn-out" data-act="report">🚩 بلاغ</button>`}
        </div>
        <div class="big-price"><span class="p">${esc(priceLabel(o, true))}</span>${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)} ر.س</s>` : ""}${!isPercentOnly(o) && effectivePercent(o) > 0 ? `<span class="pill urgent">خصم ${effectivePercent(o)}% 🔥</span>` : ""}</div>
        ${tiers || starts ? `<div class="box">${starts ? `<div class="soon">🔜 ${esc(starts)}</div>` : ""}${tiers ? `<div>📅 ${esc(tiers)}</div>` : ""}</div>` : ""}
        ${del ? `<div class="info-pill">${esc(del)}</div>` : ""}
        ${(o.tags || []).length ? `<div class="tags">${o.tags.map((t) => `<span>#${esc(t)}</span>`).join("")}</div>` : ""}
        ${o.description ? `<p class="desc">${esc(o.description)}</p>` : ""}
        ${bs.length > 1 ? `<div class="section-title">📍 الفروع (${bs.length})</div><div class="branches">${bs.map((b) => {
          const bd = state.me ? km(state.me, { lat: b.latitude, lng: b.longitude }) : null;
          return `<button class="branch${b === br ? " on" : ""}" data-branch="${esc(b.id)}"><div class="b-main"><div class="b-name">${esc(b.name || districtLabel(b.district) || "الفرع")}</div><div class="b-sub">${esc([districtLabel(b.district), b.landmark, hoursText(b)].filter(Boolean).join(" • "))}</div></div><div class="b-sub">${esc(distText(bd))}</div></button>`;
        }).join("")}</div>` : `<div class="info-pill gray">📍 ${esc([districtLabel(br.district), br.landmark, br.region].filter(Boolean).join(" • "))}${d != null ? ` — ${esc(distText(d))} عنك` : ""}</div>`}
        <div class="actions">
          <a class="btn dir" data-act="dir" href="https://www.google.com/maps/dir/?api=1&destination=${br.latitude},${br.longitude}" target="_blank" rel="noopener">🧭 الاتجاهات إلى المحل</a>
          <a class="btn wa" data-act="wa" href="https://wa.me/${waDigits(br.phone || o.phone)}?text=${encodeURIComponent(`مرحباً، أود طلب العرض: ${o.offerTitle} (${priceLabel(o)}) المعلن عنه في حيّنا\n${offerUrl(o)}`)}" target="_blank" rel="noopener">💬 طلب واتساب</a>
          <a class="btn call" href="tel:${esc(br.phone || o.phone)}">📞 اتصال</a>
        </div>
      </div>`);
    const body = $("sheetBody");
    body.querySelectorAll("[data-branch]").forEach((el) => el.onclick = () => {
      br = bs.find((b) => b.id === el.dataset.branch) || br; state.branchId = br.id;
      if (map) map.panTo(br.latitude, br.longitude); draw();
    });
    body.querySelector("[data-store]")?.addEventListener("click", (e) => openStore(e.currentTarget.dataset.store, true));
    body.querySelector('[data-act="dir"]').addEventListener("click", () => bump(o.id, "directionsCount"));
    body.querySelector('[data-act="wa"]').addEventListener("click", () => bump(o.id, "whatsappCount"));
    body.querySelector('[data-act="fav"]').onclick = () => { toggleFav(o.id); draw(); };
    body.querySelector('[data-act="share"]').onclick = () => share(`🔥 ${o.offerTitle}\n📍 ${o.restaurantName}${o.district ? " — " + o.district : ""}\n💰 ${priceLabel(o, true)}`, offerUrl(o), o.offerTitle);
    body.querySelector('[data-act="report"]')?.addEventListener("click", () => reportOffer(o));
    body.querySelector('[data-act="edit"]')?.addEventListener("click", async () => { const m = await import("./merchant.js"); m.openOfferForm(o); });
    body.querySelectorAll(".gallery img").forEach((im) => im.onclick = () => openViewer(im.dataset.full));
  };
  draw();
  renderAll();
}

function reportOffer(o) {
  showSheet(`<div class="pad"><div class="section-title">🚩 بلاغ عن العرض</div><p class="desc">${esc(o.offerTitle)}</p>
    <div class="radio-list">${X.REPORT_REASONS.map((r, i) => `<label class="radio"><input type="radio" name="rr" value="${esc(r)}" ${i === 0 ? "checked" : ""}> ${esc(r)}</label>`).join("")}</div>
    <button class="btn danger" id="sendReport">إرسال البلاغ</button></div>`);
  $("sendReport").onclick = async () => {
    const reason = document.querySelector('input[name="rr"]:checked')?.value || "أخرى";
    try {
      await addDoc(collection(db, "reports"), { offerId: o.id, offerTitle: (o.offerTitle || "").slice(0, 150), restaurantName: (o.restaurantName || "").slice(0, 100),
        merchantId: o.merchantId || "", reason, reporterUid: state.user?.uid || null, createdAt: serverTimestamp() });
      let reported = false; try { reported = localStorage.getItem("rep_" + o.id) === "1"; localStorage.setItem("rep_" + o.id, "1"); } catch {}
      if (!reported) bump(o.id, "reportsCount");
      toast("وصل بلاغك، شكراً لك 🙏"); openOffer(o.id, false);
    } catch (e) { toast("تعذّر إرسال البلاغ، حاول مرة ثانية"); }
  };
}

// ---------------- صفحة المتجر ----------------
export function openStore(uid, push) {
  const s = state.stores.find((x) => x.uid === uid);
  if (!s) { if (state.storesLoaded) { toast("صفحة المتجر غير متاحة"); go("/", true); } return; }
  state.view = { type: "s", id: uid };
  if (push) history.pushState({ v: "s", id: uid }, "", `/s/${encodeURIComponent(uid)}`);
  document.title = `${s.name} | حيّنا`;
  const offers = state.offers.filter((o) => o.merchantId === uid && !X.isExpired(o) && (o.reportsCount || 0) < AUTO_HIDE_REPORTS);
  const del = deliveryText(s), phone = s.phone || (s.branches || [])[0]?.phone || "";
  showSheet(`${s.coverUrl ? `<img class="cover" src="${esc(cld(s.coverUrl, 1000))}" alt="">` : `<div class="cover"></div>`}
    <div class="pad">
      <div class="store-head">${s.logoUrl ? `<img src="${esc(cld(s.logoUrl, 200))}" alt="">` : `<div class="logo-ph">🏪</div>`}<div class="nm">${esc(s.name)}${s.verified ? ' <span class="verified">✔ موثّق</span>' : ""}</div></div>
      ${s.category ? `<div class="pills"><span class="pill">${esc(s.category)}</span></div>` : ""}
      ${s.priceFrom > 0 ? `<div class="box">💰 الأسعار تبدأ من ${money(s.priceFrom)} ر.س</div>` : ""}
      ${del ? `<div class="info-pill">${esc(del)}</div>` : ""}
      ${s.about ? `<p class="desc">${esc(s.about)}</p>` : ""}
      ${phone ? `<div class="actions"><a class="btn wa" href="https://wa.me/${waDigits(phone)}" target="_blank" rel="noopener">💬 واتساب</a><a class="btn call" href="tel:${esc(phone)}">📞 اتصال</a></div>` : ""}
      ${offers.length ? `<div class="section-title">🔥 العروض الحالية (${offers.length})</div><div class="hscroll">${offers.map((o) =>
        `<button class="mini" data-offer="${esc(o.id)}"><img src="${esc(thumb(o))}" alt=""><div>${esc(o.offerTitle)}<small>${esc(priceLabel(o))}</small></div></button>`).join("")}</div>` : ""}
      ${(s.menuImages || []).length ? `<div class="section-title">📋 ${X.menuLabel(s.category)}</div><div class="hscroll">${s.menuImages.map((u) =>
        `<img class="menu-img" src="${esc(cld(u, 400))}" data-full="${esc(cld(u, 1600))}" alt="">`).join("")}</div>` : ""}
      ${(s.branches || []).length ? `<div class="section-title">📍 الفروع (${s.branches.length})</div><div class="branches">${s.branches.map((b) => {
        const d = state.me ? km(state.me, { lat: b.latitude, lng: b.longitude }) : null, op = openNow(b);
        return `<a class="branch" href="https://www.google.com/maps/dir/?api=1&destination=${b.latitude},${b.longitude}" target="_blank" rel="noopener"><div class="b-main"><div class="b-name">${esc(b.name || districtLabel(b.district) || "الفرع")} ${op === true ? '<span class="pill open">مفتوح</span>' : op === false ? '<span class="pill closed">مغلق</span>' : ""}</div><div class="b-sub">${esc([districtLabel(b.district), b.landmark, hoursText(b)].filter(Boolean).join(" • "))}</div></div><div class="b-sub">${esc(distText(d))} 🧭</div></a>`;
      }).join("")}</div>` : ""}
      <button class="btn share" data-act="share">↗︎ شارك صفحة المتجر</button>
    </div>`);
  const body = $("sheetBody");
  body.querySelectorAll("[data-offer]").forEach((el) => el.onclick = () => openOffer(el.dataset.offer, true));
  body.querySelectorAll(".menu-img").forEach((im) => im.onclick = () => openViewer(im.dataset.full));
  body.querySelector('[data-act="share"]').onclick = () => share(`🏪 ${s.name} على حيّنا`, storeUrl(uid), s.name);
}

async function share(text, url, title) {
  if (navigator.share) { try { await navigator.share(url ? { title, text, url } : { title, text }); return; } catch (e) { if (e.name === "AbortError") return; } }
  if (!url) { window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, "_blank"); return; }
  try { await navigator.clipboard.writeText(`${text}\n${url}`); toast("نسخنا الرابط ✅"); } catch { prompt("انسخ الرابط:", url); }
}
function openViewer(src) { $("viewerImg").src = src; $("viewer").classList.remove("hidden"); }

// ---------------- محلات Google غير المسجّلة: ادعه / أنا صاحب المحل ----------------
const poiLog = (placeId, field) => setDoc(doc(db, "poiInterest", placeId), { [field]: increment(1), lastAt: serverTimestamp() }, { merge: true }).catch(() => {});
const INVITE_LOCK_DAYS = 5; // بعد ما أحد يدعو المحل، تتقفل الدعوة كذا يوم لين يسجّل
const daysLeft = (ms) => Math.max(1, Math.ceil((ms - Date.now()) / 864e5));
async function openPoi({ placeId, lat, lng }) {
  poiLog(placeId, "taps");
  showSheet(`<div class="pad"><div class="spinner"></div></div>`);
  const [info, lockSnap] = await Promise.all([
    map?.placeInfo(placeId).catch(() => null),
    getDoc(doc(db, "poiInvites", placeId)).catch(() => null),
  ]);
  const name = info?.name || "", phone = info?.phone || "";
  const lock = lockSnap?.exists?.() ? lockSnap.data() : null;
  const until = X.ms(lock?.until) || 0;
  const locked = until > Date.now(), mine = locked && lock.by === state.user?.uid;
  const mobile = (() => { const d = String(phone).replace(/\D/g, ""); return d.startsWith("9665") && d.length === 12 ? d : d.startsWith("05") && d.length === 10 ? "966" + d.slice(1) : null; })();
  const cfg = state.config;
  const shown = name || "هذا المحل";
  const appLink = withRef(cfg.appLink || location.origin);
  const invite = (cfg.inviteMessage || 'السلام عليكم 👋 شفت محلكم "{name}" على خريطة حيّنا. سجّلوا محلكم مجاناً وانشروا عروضكم لأهل الحي، وخلّوا الزباين يوصلون لكم بسهولة 📍')
    .replace(/\{name\}/g, name || "محلكم") + "\n" + appLink;
  const isMerchant = state.role === "merchant" || (state.user && ADMIN_EMAILS.includes(state.user.email));
  const inviteBtn = mobile
    ? `<a class="btn wa" id="poiInvite" href="https://wa.me/${mobile}?text=${encodeURIComponent(invite)}" target="_blank" rel="noopener">💬 ${mine ? "ذكّره على واتساب المحل" : "ادعه على واتساب المحل"}</a>`
    : `<button class="btn wa" id="poiInvite">📨 ${mine ? "ذكّره مرة ثانية" : "ادعه لحيّنا"}</button>`;
  let inviteHtml = "";
  if (cfg.poiInviteEnabled !== false) {
    if (locked && !mine) inviteHtml = `<div class="poi-locked">📨 تمت دعوة هذا المحل من أحد أهل الحي<br><small>ننتظره يسجّل — تقدر تدعوه بعد ${daysLeft(until)} ${daysLeft(until) === 1 ? "يوم" : "أيام"}</small></div>`;
    else if (!state.user) inviteHtml = `<button class="btn wa" id="poiLoginInvite">📨 سجّل دخولك وادعه</button>
      <p class="poi-pts">🎁 الدعوة تنحسب باسمك، ولو سجّل المحل تاخذ لين <b>${POINTS.verified + POINTS.offer} نقطة</b></p>`;
    else inviteHtml = (mine ? `<div class="poi-locked mine">✅ دعيت هذا المحل — ننتظره يسجّل (باقي ${daysLeft(until)} ${daysLeft(until) === 1 ? "يوم" : "أيام"} محجوز لك)</div>` : "")
      + inviteBtn + (mine ? "" : `<p class="poi-pts">🎁 لو سجّل المحل عن طريق دعوتك تاخذ لين <b>${POINTS.verified + POINTS.offer} نقطة</b></p>`);
  }
  showSheet(`<div class="pad poi-card">
    <div class="poi-ico">🏪</div>
    <div class="section-title" style="text-align:center">${esc(shown)}</div>
    <p class="desc" style="text-align:center">هذا المحل مو مسجّل في حيّنا للحين</p>
    ${inviteHtml}
    ${phone && !mobile ? `<a class="btn call" href="tel:${esc(phone.replace(/[^\d+]/g, ""))}">📞 اتصل بالمحل (${esc(phone)})</a>` : ""}
    ${!state.user || isMerchant ? `<button class="btn dir" id="poiClaim">🏪 أنا صاحب المحل — سجّله</button>`
      : `<p class="poi-owner-hint">صاحب المحل؟ سجّل خروج وادخل بحساب <b>صاحب محل</b> عشان تسجّله</p>`}
    <a class="btn call" href="https://www.google.com/maps/search/?api=1&query=${lat},${lng}&query_place_id=${encodeURIComponent(placeId)}" target="_blank" rel="noopener">فتح في Google Maps</a>
  </div>`);
  $("poiLoginInvite")?.addEventListener("click", () => { hideSheet(); setTab("account"); openLogin("customer"); });
  $("poiInvite")?.addEventListener("click", () => {
    poiLog(placeId, "invites");
    // نحجز الدعوة باسمه كم يوم عشان ما يتكرر الإزعاج للمحل
    setDoc(doc(db, "poiInvites", placeId), { by: state.user.uid, at: serverTimestamp(), until: Timestamp.fromMillis(Date.now() + INVITE_LOCK_DAYS * 864e5) }).catch(() => {});
    if (!mobile) share(invite, "", "حيّنا");
  });
  $("poiClaim")?.addEventListener("click", () => {
    poiLog(placeId, "claims");
    try { sessionStorage.setItem("hayyna_claim", JSON.stringify({ name, lat, lng })); } catch {}
    hideSheet();
    if (!state.user) { setTab("account"); openLogin("merchant"); return; }
    setTab("account");
  });
}
export function takeClaim() {
  try { const c = JSON.parse(sessionStorage.getItem("hayyna_claim") || "null"); sessionStorage.removeItem("hayyna_claim"); return c; } catch { return null; }
}

// ---------------- الفلاتر والأحياء ----------------
function openFilters() {
  showSheet(`<div class="pad"><div class="section-title">الفلاتر</div>
    <label class="switch-row"><span>● مفتوح الآن فقط</span><input type="checkbox" id="fOpen" ${state.openOnly ? "checked" : ""}></label>
    <div class="label">الترتيب</div>
    <div class="chips wrap">${[["near", "📍 الأقرب"], ["discount", "🔥 أكبر خصم"], ["cheap", "💰 الأرخص"], ["new", "🆕 الأحدث"]]
      .map(([k, l]) => `<button class="chip${state.sort === k ? " on" : ""}" data-sort="${k}">${l}</button>`).join("")}</div>
    <div class="row2"><button class="btn call" id="fReset">مسح الفلاتر</button><button class="btn dir" id="fDone">تم</button></div></div>`);
  $("fOpen").onchange = (e) => { state.openOnly = e.target.checked; renderAll(); };
  $("sheetBody").querySelectorAll("[data-sort]").forEach((b) => b.onclick = () => { state.sort = b.dataset.sort; openFilters(); renderAll(); });
  $("fReset").onclick = () => { state.openOnly = false; state.sort = "near"; renderAll(); hideSheet(); };
  $("fDone").onclick = hideSheet;
}
function openPlaces() {
  const now = Date.now(), counts = new Map();
  state.offers.filter((o) => !X.isExpired(o, now)).forEach((o) => branchesOf(o).forEach((b) => {
    if (!b.district) return; const k = norm(b.district.replace(/^حي\s+/, ""));
    const c = counts.get(k) || { name: districtLabel(b.district), n: 0, lat: b.latitude, lng: b.longitude }; c.n++; counts.set(k, c);
  }));
  state.stores.forEach((s) => (s.branches || []).forEach((b) => {
    if (!b.district) return; const k = norm(b.district.replace(/^حي\s+/, ""));
    if (!counts.has(k)) counts.set(k, { name: districtLabel(b.district), n: 0, lat: b.latitude, lng: b.longitude });
  }));
  // الأقرب لك أولاً (أو لوسط الخريطة لو موقعك مو معروف)
  const ref = state.me || map?.center?.();
  const arr = [...counts.values()];
  arr.forEach((c) => { c.d = ref && c.lat != null ? X.km(ref, { lat: c.lat, lng: c.lng }) : null; });
  arr.sort((a, b) => (a.d ?? 1e9) - (b.d ?? 1e9) || b.n - a.n);
  showSheet(`<div class="pad"><div class="section-title">📍 تصفّح حي</div>
    <input id="placeQ" class="input" placeholder="ابحث عن حي…">
    <button class="place-row ${!state.browse ? "on" : ""}" data-here="1"><span>📍 حولي (موقعي)</span></button>
    <div id="placeList">${arr.map((c, i) => `<button class="place-row${state.browse?.name === c.name ? " on" : ""}" data-i="${i}"><span>${esc(c.name)}</span><small>${c.n ? c.n + " عرض" : "متاجر"}${state.me && c.d != null ? " • " + X.distText(c.d) : ""}</small></button>`).join("")}</div></div>`);
  const body = $("sheetBody");
  body.querySelector("[data-here]").onclick = () => { state.browse = null; hideSheet(); if (state.me) map?.setView(state.me.lat, state.me.lng, 14); else locate(true); renderAll(); };
  body.querySelectorAll("[data-i]").forEach((el) => el.onclick = () => {
    const c = arr[+el.dataset.i]; state.browse = { name: c.name, lat: c.lat, lng: c.lng }; state.scope = "all"; map?.setCircle(null); hideSheet();
    map?.setView(c.lat, c.lng, 14); renderAll();
  });
  $("placeQ").oninput = (e) => { const q = norm(e.target.value); body.querySelectorAll("[data-i]").forEach((el) => el.classList.toggle("hidden", q && !norm(el.textContent).includes(q))); };
}

// ---------------- حسابي (أهل الحي) ----------------
function renderCustomer(el, { name, pr, u, support, foot }) {
  const now = Date.now();
  const live = state.offers.filter((o) => !X.isExpired(o, now));
  const favList = live.filter((o) => state.favs.has(o.id));
  const dist = userDistrict();
  const near = state.me ? live.filter((o) => branchesOf(o).some((b) => km(state.me, { lat: b.latitude, lng: b.longitude }) <= NEAR_KM)).length : null;
  const fresh = live.filter((o) => { const t = X.ms(o.startsAt) || X.ms(o.createdAt); return t && t <= now && now - t < 864e5; }).length;
  const endingFav = favList.filter((o) => { const e = X.ms(o.expiresAt); return e && e - now < 864e5; }).length;
  const mini = (o) => `<div class="mcard" data-id="${esc(o.id)}" role="button"><div class="mthumb"><img src="${esc(thumb(o, 300))}" alt="" loading="lazy">
      ${badge(o) ? `<span class="badge">${esc(badge(o).t)}</span>` : ""}</div>
      <div class="mname">${esc(o.restaurantName)}</div><div class="mprice">${esc(priceLabel(o))}</div></div>`;
  const row = (id, ico, label, sub = "", href = "") => href
    ? `<a class="menu-row" href="${href}" target="_blank" rel="noopener"><span class="mi">${ico}</span><span class="ml"><b>${label}</b>${sub ? `<small>${sub}</small>` : ""}</span><span class="chev">‹</span></a>`
    : `<button class="menu-row" id="${id}"><span class="mi">${ico}</span><span class="ml"><b>${label}</b>${sub ? `<small>${sub}</small>` : ""}</span><span class="chev">‹</span></button>`;
  el.innerHTML = `<div class="cust-hero">
      <div class="ch-top">${u.photoURL ? `<img src="${esc(u.photoURL)}" alt="">` : `<div class="avatar">${esc((name || "؟")[0])}</div>`}
        <div class="ch-main"><b>${esc(name)}</b><small dir="ltr">${esc(X.toLocalPhone(pr.phone))}</small></div>
        <button class="ch-edit" id="editProfile" aria-label="تعديل البيانات">✎</button></div>
      <div class="ch-badge">🏡 ${dist ? `من أهل ${esc(districtLabel(dist))}` : "من أهل الحي"}</div>
    </div>
    <div class="pad cust">
      <div class="qstats">
        <button class="qs" data-go="favs"><b>${favList.length}</b><small>❤️ مفضلتي</small></button>
        <button class="qs" data-go="${near == null ? "locate" : "map"}"><b>${near == null ? "📍" : near}</b><small>${near == null ? "حدّد موقعك" : "📍 عروض حولك"}</small></button>
        <button class="qs" data-go="list"><b>${fresh}</b><small>🆕 جديد اليوم</small></button>
      </div>

      <div class="sec-head"><b>مفضلتي ❤️</b>${favList.length ? `<button class="link" data-go="favs">عرض الكل ‹</button>` : ""}</div>
      ${endingFav ? `<div class="hint warn">⏰ ${endingFav === 1 ? "عرض من مفضلتك ينتهي" : endingFav + " عروض من مفضلتك تنتهي"} خلال 24 ساعة — لا يفوتك</div>` : ""}
      ${favList.length ? `<div class="favrow">${favList.slice(0, 10).map(mini).join("")}</div>`
        : `<div class="fav-empty"><span>🤍</span><div><b>مفضلتك فاضية</b><br><small>اضغط القلب على أي عرض يعجبك، ويظهر هنا عشان ترجع له بسرعة</small></div></div>`}

      ${ambassadorCard()}


      <div class="menu">
        ${canOfferInstall() ? row("installAcc", "📲", "ثبّت حيّنا على جوالك", "يفتح بشاشة كاملة مثل التطبيق") : ""}
        ${row("contactBtn", "💬", "تواصل معنا", "اقتراح، مشكلة، أو محل تبيه ينضم")}
        ${row("", "🟢", "واتساب الدعم", esc(support), `https://wa.me/${waDigits(support)}`)}
        ${row("", "🔒", "سياسة الخصوصية", "", "/privacy.html")}
      </div>
    </div>` + foot;
  el.querySelector(".acc-foot .install-row")?.remove(); // موجود في القائمة فوق
  el.querySelector(".acc-foot .legal")?.remove();
  el.querySelectorAll("[data-go]").forEach((b) => b.onclick = () => { const g = b.dataset.go; if (g === "locate") { setTab("map"); locate(true); } else setTab(g); });
  $("contactBtn").onclick = openContact;
  $("inviteFriend").onclick = inviteFriend;
  $("howPts")?.addEventListener("click", howPoints);
}
function ambassadorCard() {
  const refs = state.referrals, pts = pointsOf(refs), t = tierOf(pts);
  const st = (r) => r.offerPts ? ["🔥", "نزّل عروض", "done"] : r.verifiedPts ? ["✅", "انضم وتوثّق", "done"] : ["⏳", "تحت المراجعة", "wait"];
  return `<div class="amb tier-${TIERS.indexOf(TIERS.find((x) => x.name === t.name))}">
    <div class="amb-top"><div class="amb-ico">${t.ico}</div>
      <div class="amb-main"><b>${t.name}</b><small>${refs.length ? `دعيت ${refs.length} ${refs.length === 1 ? "محل" : "محلات"}` : "ادع المحلات اللي تحبها لحيّنا"}</small></div>
      <div class="amb-pts"><b>${pts.toLocaleString("en")}</b><small>نقطة</small></div></div>
    <div class="amb-bar"><span style="width:${t.pct}%"></span></div>
    <div class="amb-next">${t.next ? `باقي <b>${t.left.toLocaleString("en")}</b> نقطة وتصير <b>${t.next.ico} ${t.next.name}</b>` : "💎 وصلت أعلى مستوى — أنت فخر الحي"}</div>
    ${refs.length ? `<div class="amb-list">${refs.slice(0, 20).map((r) => { const [i, l, c] = st(r); return `<div class="amb-row"><span>${i}</span><b>${esc(r.storeName || "محل")}</b><small class="${c}">${l}${(r.verifiedPts || 0) + (r.offerPts || 0) ? ` • +${(r.verifiedPts || 0) + (r.offerPts || 0)}` : ""}</small></div>`; }).join("")}</div>` : ""}
    <div class="amb-actions"><button class="invite-btn" id="inviteFriend">🎁 شارك رابطك</button><button class="link" id="howPts">كيف أجمع نقاط؟</button></div>
  </div>`;
}
function howPoints() {
  showSheet(`<div class="pad install-sheet"><div style="font-size:44px">🏅</div><h2>كيف تجمع نقاط؟</h2>
    <ol>
      <li>اضغط على أي محل <b>مو مسجّل</b> في الخريطة، واختر <b>«ادعه»</b> — الرسالة فيها رابطك الخاص</li>
      <li>أو اضغط <b>«شارك رابطك»</b> وأرسله لأصحاب المحلات اللي تعرفهم</li>
      <li>إذا سجّل المحل من رابطك وتوثّق: <b>+${POINTS.verified}</b> نقطة</li>
      <li>إذا نزّل أول عرض: <b>+${POINTS.offer}</b> نقطة</li>
    </ol>
    <div class="tiers">${TIERS.map((x) => `<div><span>${x.ico}</span><b>${x.name}</b><small>${x.min ? x.min.toLocaleString("en") + "+" : "البداية"}</small></div>`).join("")}</div>
    <p class="muted">النقاط تنحسب بعد مراجعة فريق حيّنا، وقريباً تقدر تستبدلها بخصومات وكوبونات من المحلات 🎁</p>
    <button class="btn primary" id="howOk">تمام</button></div>`);
  $("howOk").onclick = hideSheet;
}
async function inviteFriend() {
  const link = withRef(state.config.appLink || location.origin);
  const text = `شف حيّنا 📍 تطبيق يعرض لك عروض وخصومات المحلات اللي حولك على الخريطة — مطاعم، مقاهي، ملابس وأكثر. جرّبه:`;
  if (navigator.share) { try { await navigator.share({ title: "حيّنا", text, url: link }); return; } catch (e) { if (e?.name === "AbortError") return; } }
  window.open(`https://wa.me/?text=${encodeURIComponent(text + "\n" + link)}`, "_blank");
}

// ---------------- حسابي ----------------
const APPLE_ENABLED = false; // يتفعّل بعد إعداد Sign in with Apple في Firebase (يحتاج حساب Apple Developer)
const roleName = (r) => r === "merchant" ? "صاحب محل 🏪" : "من أهل الحي 🏡";
export function renderAccount() {
  const el = $("account");
  const u = state.user;
  const support = X.toLocalPhone(state.config.supportWhatsapp || "0552793379");
  if (!u) {
    el.innerHTML = `<div class="acc-hero"><img src="/img/icon-192.png" alt=""><h1>أهلاً بك في حيّنا</h1><p>سجّل دخولك عشان تحفظ مفضلتك، أو تنشر عروض محلك</p></div>
      <div class="pad">
        <button class="role-card" data-login="customer"><div class="rc-ico">🏡</div><div><b>أنا من أهل الحي</b><br><small>أدوّر عروض المحلات حولي، وأحفظ اللي يعجبني</small></div></button>
        <button class="role-card" data-login="merchant"><div class="rc-ico">🏪</div><div><b>أنا صاحب محل</b><br><small>انشر عروضك مجاناً وخلّ أهل الحي يوصلون لك</small></div></button>
        <button class="btn call" id="contactBtn">💬 تواصل معنا</button>
        ${canOfferInstall() ? `<button class="btn install-row" id="installAcc">📲 ثبّت حيّنا على جوالك</button>` : ""}
        <div class="legal"><a href="/privacy.html">سياسة الخصوصية</a></div>
      </div>`;
    el.querySelectorAll("[data-login]").forEach((b) => b.onclick = () => openLogin(b.dataset.login));
    $("contactBtn").onclick = openContact;
    if ($("installAcc")) $("installAcc").onclick = doInstall;
    return;
  }
  const isAdmin = ADMIN_EMAILS.includes(u.email);
  const pr = state.profile || {};
  if (state.roleLoading) { el.innerHTML = `<div class="pad"><div class="spinner"></div></div>`; return; }
  // أول دخول: نكمّل البيانات (الاسم + الجوال + نوع الحساب)
  if (!pr.name || !pr.phone) { renderOnboarding(el); return; }
  const name = pr.name || u.displayName || "";
  const head = `<div class="acc-user">${u.photoURL ? `<img src="${esc(u.photoURL)}" alt="">` : `<div class="avatar">${esc((name || "؟")[0])}</div>`}
    <div class="au-main"><b>${esc(name)}</b><br><small dir="ltr">${esc(X.toLocalPhone(pr.phone))}</small> <span class="pill role">${roleName(state.role)}</span></div>
    <button class="icon-btn" id="editProfile" aria-label="تعديل البيانات">✎</button></div>`;
  const foot = `<div class="pad acc-foot">${canOfferInstall() ? `<button class="btn install-row" id="installAcc">📲 ثبّت حيّنا على جوالك</button>` : ""}<button class="btn ghost" id="logoutBtn">تسجيل الخروج</button>
    <button class="btn ghost danger-text" id="deleteAcc">حذف الحساب</button><div class="legal"><a href="/privacy.html">سياسة الخصوصية</a></div></div>`;
  if (state.role === "merchant" || isAdmin) {
    el.innerHTML = head + `<div id="merchantRoot" class="pad"><div class="spinner"></div></div>` + foot;
    import("./merchant.js").then((m) => m.renderMerchant($("merchantRoot"), { isAdmin, support, logout: doLogout, contact: openContact }));
  } else {
    renderCustomer(el, { name, pr, u, support, foot });
  }
  $("editProfile").onclick = () => openProfileEdit();
  $("logoutBtn").onclick = doLogout;
  if ($("installAcc")) $("installAcc").onclick = doInstall;
  $("deleteAcc").onclick = confirmDeleteAccount;
}

function renderOnboarding(el) {
  const u = state.user, pr = state.profile || {};
  let role = state.role || "customer";
  el.innerHTML = `<div class="acc-hero small"><h1>كمّل بياناتك 👋</h1><p>خطوة وحدة وتبدأ</p></div>
    <div class="pad form">
      <label class="label">اسمك *</label><input id="obName" class="input" maxlength="60" value="${esc(pr.name || u.displayName || "")}" placeholder="الاسم">
      <label class="label">رقم جوالك *</label><input id="obPhone" class="input" inputmode="tel" dir="ltr" value="${esc(X.toLocalPhone(pr.phone || ""))}" placeholder="05xxxxxxxx">
      <label class="label">نوع الحساب</label>
      <div class="row2"><button class="role-mini${role === "customer" ? " on" : ""}" data-r="customer">🏡 من أهل الحي</button><button class="role-mini${role === "merchant" ? " on" : ""}" data-r="merchant">🏪 صاحب محل</button></div>
      <div id="obErr" class="err"></div>
      <button class="btn dir big" id="obSave">متابعة</button>
      <button class="btn ghost" id="obOut">تسجيل الخروج</button>
    </div>`;
  el.querySelectorAll("[data-r]").forEach((b) => b.onclick = () => { role = b.dataset.r; el.querySelectorAll("[data-r]").forEach((x) => x.classList.toggle("on", x === b)); });
  $("obOut").onclick = doLogout;
  $("obSave").onclick = async () => {
    const name = $("obName").value.trim(), phone = X.normalizeSaudiPhone($("obPhone").value);
    if (name.length < 2) return $("obErr").textContent = "اكتب اسمك.";
    if (!phone) return $("obErr").textContent = "اكتب رقم جوال صحيح مثل 05xxxxxxxx.";
    $("obSave").disabled = true;
    try {
      await setDoc(doc(db, "users", u.uid), { role, name, phone, updatedAt: serverTimestamp() }, { merge: true });
      state.profile = { name, phone }; state.role = role;
      toast(role === "merchant" ? "أهلاً بك 🏪 ابدأ بتجهيز صفحة متجرك" : `أهلاً ${name} 👋`);
      renderAll();
    } catch (e) { console.error(e); $("obSave").disabled = false; $("obErr").textContent = "تعذّر الحفظ، حاول مرة ثانية."; }
  };
}

function openProfileEdit() {
  const u = state.user, pr = state.profile || {};
  let role = state.role || "customer";
  showSheet(`<div class="pad form"><div class="section-title">بياناتي</div>
    <label class="label">الاسم</label><input id="peName" class="input" maxlength="60" value="${esc(pr.name || "")}">
    <label class="label">رقم الجوال</label><input id="pePhone" class="input" inputmode="tel" dir="ltr" value="${esc(X.toLocalPhone(pr.phone || ""))}">
    <small class="hint">${esc(u.email || "")} • ${roleName(role)}</small>
    <div id="peErr" class="err"></div><button class="btn dir" id="peSave">حفظ</button></div>`);
  $("peSave").onclick = async () => {
    const name = $("peName").value.trim(), phone = X.normalizeSaudiPhone($("pePhone").value);
    if (name.length < 2) return $("peErr").textContent = "اكتب اسمك.";
    if (!phone) return $("peErr").textContent = "اكتب رقم جوال صحيح.";
    try {
      await setDoc(doc(db, "users", u.uid), { role, name, phone, updatedAt: serverTimestamp() }, { merge: true });
      state.profile = { name, phone }; state.role = role; hideSheet(); toast("انحفظت بياناتك ✅"); renderAll();
    } catch (e) { $("peErr").textContent = "تعذّر الحفظ."; }
  };
}

async function setRole(role) {
  try {
    await setDoc(doc(db, "users", state.user.uid), { role, updatedAt: serverTimestamp() }, { merge: true });
    state.role = role; toast(role === "merchant" ? "صار حسابك حساب تاجر 🏪" : "رجعنا حسابك لحساب أهل الحي 🏡"); renderAll();
  } catch { toast("تعذّر التغيير"); }
}

function confirmDeleteAccount() {
  showSheet(`<div class="pad"><div class="section-title">حذف الحساب؟</div>
    <p class="desc">بننحذف بياناتك (الاسم، الجوال، المفضلة) وتسجيل دخولك. ${state.role === "merchant" ? "عروضك وصفحة متجرك تنحذف بعد من خلال فريق حيّنا خلال أيام." : ""}</p>
    <div class="row2"><button class="btn call" id="daNo">إلغاء</button><button class="btn danger" id="daYes">احذف حسابي</button></div></div>`);
  $("daNo").onclick = hideSheet;
  $("daYes").onclick = async () => {
    const u = auth.currentUser; if (!u) return;
    try {
      await deleteDoc(doc(db, "users", u.uid)).catch(() => {});
      if (state.role === "merchant") await addDoc(collection(db, "feedback"), { uid: u.uid, userName: (state.profile?.name || "").slice(0, 100), role: "merchant", type: "أخرى",
        message: "طلب حذف حساب التاجر وبياناته (عروض + صفحة المتجر)", screenshotUrl: "", contact: (u.email || "").slice(0, 100), appVersion: "web", device: "", status: "new", reply: "", replySeen: false, createdAt: serverTimestamp() }).catch(() => {});
      await deleteUser(u);
      state.favs = new Set(); saveFavsLocal(); hideSheet(); toast("انحذف حسابك. نتمنى نشوفك مرة ثانية 🌿", true);
    } catch (e) {
      if (e.code === "auth/requires-recent-login") { toast("لأمانك: سجّل دخول مرة ثانية وبعدها احذف الحساب", true); await signOut(auth); }
      else toast("تعذّر الحذف: " + (e.code || e.message));
    }
  };
}

// خيارات الدخول: قوقل / الإيميل / أبل
function openLogin(role) {
  state.pendingRole = role;
  try { sessionStorage.setItem("hayyna_pending_role", role); } catch {}
  showSheet(`<div class="pad"><div class="section-title">${role === "merchant" ? "🏪 دخول صاحب المحل" : "🏡 دخول أهل الحي"}</div>
    <button class="btn login google" id="lgGoogle"><img src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg" alt=""> المتابعة بحساب Google</button>
    ${APPLE_ENABLED ? `<button class="btn login apple" id="lgApple"> المتابعة بحساب Apple</button>` : ""}
    <div class="or"><span>أو بالإيميل</span></div>
    <input id="lgEmail" class="input" type="email" dir="ltr" placeholder="name@email.com" autocomplete="email">
    <button class="btn call" id="lgEmailBtn">✉️ أرسل لي رابط الدخول</button>
    <small class="hint">بنرسل لك رابط على إيميلك، تضغطه وتدخل مباشرة بدون كلمة سر.</small></div>`);
  $("lgGoogle").onclick = () => loginWith(new GoogleAuthProvider());
  if (APPLE_ENABLED) $("lgApple").onclick = () => {
    const p = new OAuthProvider("apple.com"); p.addScope("email"); p.addScope("name"); p.setCustomParameters({ locale: "ar" }); loginWith(p);
  };
  $("lgEmailBtn").onclick = async () => {
    const email = $("lgEmail").value.trim();
    if (!/^\S+@\S+\.\S+$/.test(email)) return toast("اكتب إيميل صحيح");
    try {
      await sendSignInLinkToEmail(auth, email, { url: location.origin + "/?login=email", handleCodeInApp: true });
      try { localStorage.setItem("hayyna_login_email", email); } catch {}
      showSheet(`<div class="pad" style="text-align:center"><div style="font-size:48px">📬</div><div class="section-title">افتح إيميلك</div>
        <p class="desc">أرسلنا رابط الدخول إلى <b dir="ltr">${esc(email)}</b>. اضغط الرابط من نفس الجوال وبتدخل على طول. (شيك على مجلد الرسائل غير المرغوبة لو ما لقيته)</p></div>`);
    } catch (e) {
      toast(e.code === "auth/operation-not-allowed" ? "الدخول بالإيميل مو مفعّل للحين" : "تعذّر الإرسال: " + (e.code || e.message), true);
    }
  };
}
async function loginWith(provider) {
  if (provider instanceof GoogleAuthProvider) provider.setCustomParameters({ prompt: "select_account" });
  try { await signInWithPopup(auth, provider); hideSheet(); }
  catch (e) {
    if (e.code === "auth/popup-blocked" || e.code === "auth/operation-not-supported-in-this-environment") { await signInWithRedirect(auth, provider); return; }
    if (e.code === "auth/account-exists-with-different-credential") return toast("هذا الإيميل مسجّل بطريقة دخول ثانية، جرّب قوقل أو الإيميل", true);
    if (e.code !== "auth/popup-closed-by-user" && e.code !== "auth/cancelled-popup-request") toast("تعذّر الدخول: " + (e.code || e.message));
  }
}
async function doLogout() { await signOut(auth); state.role = null; state.profile = null; toast("سجّلت خروج"); }

function openContact() {
  const support = X.toLocalPhone(state.config.supportWhatsapp || "0552793379");
  showSheet(`<div class="pad"><div class="section-title">💬 تواصل معنا</div>
    <div class="chips wrap" id="fbTypes">${X.FEEDBACK_TYPES.map((t, i) => `<button class="chip${i === 0 ? " on" : ""}" data-t="${esc(t)}">${esc(t)}</button>`).join("")}</div>
    <textarea id="fbMsg" class="input" rows="5" placeholder="اكتب رسالتك…" maxlength="2000"></textarea>
    ${state.user ? "" : `<input id="fbContact" class="input" placeholder="رقم جوالك أو إيميلك (عشان نرد عليك)">`}
    <button class="btn dir" id="fbSend">إرسال</button>
    <a class="btn wa" href="https://wa.me/${waDigits(support)}" target="_blank" rel="noopener">أو كلّمنا واتساب ${esc(support)}</a></div>`);
  let type = X.FEEDBACK_TYPES[0];
  $("fbTypes").querySelectorAll("[data-t]").forEach((b) => b.onclick = () => { type = b.dataset.t; $("fbTypes").querySelectorAll(".chip").forEach((x) => x.classList.toggle("on", x === b)); });
  $("fbSend").onclick = async () => {
    const message = $("fbMsg").value.trim(); if (!message) return toast("اكتب رسالتك أول");
    try {
      await addDoc(collection(db, "feedback"), {
        uid: state.user?.uid || null, userName: (state.user?.displayName || "").slice(0, 100), role: state.role || "visitor", type,
        message: message.slice(0, 2000), screenshotUrl: "", contact: (state.user?.email || $("fbContact")?.value || "").slice(0, 100),
        appVersion: "web", device: navigator.userAgent.slice(0, 100), status: "new", reply: "", replySeen: false, createdAt: serverTimestamp()
      });
      toast("وصلت رسالتك ✅ بنرد عليك قريب"); hideSheet();
    } catch (e) { toast("تعذّر الإرسال، حاول مرة ثانية"); }
  };
}

// ---------------- التبويبات والتنقل ----------------
export function setTab(t) {
  state.tab = t;
  document.querySelectorAll(".tab").forEach((s) => s.classList.toggle("on", s.id === "tab-" + t));
  document.querySelectorAll("#nav button").forEach((b) => b.classList.toggle("on", b.dataset.tab === t));
  if (t === "map") setTimeout(() => map?.resize(), 50);
  if (t === "account") renderAccount();
  try { sessionStorage.setItem("hayyna_tab", t); } catch {}
}
$("nav").onclick = (e) => { const b = e.target.closest("[data-tab]"); if (b) { hideSheet(); if (location.pathname !== "/") history.replaceState({}, "", "/"); setTab(b.dataset.tab); } };

function route() {
  const m = location.pathname.match(/^\/(o|s)\/([^/]+)/);
  if (!m) { hideSheet(); document.title = "حيّنا — عروض المحلات حولك على الخريطة"; return; }
  const id = decodeURIComponent(m[2]);
  if (m[1] === "o") {
    setTab("map"); openOffer(id, false);
    const o = state.offers.find((x) => x.id === id);
    if (o && map) { const { b } = nearestBranch(o); map.setView(b.latitude, b.longitude, 15); }
  } else openStore(id, false);
}
export function go(path, replace) { (replace ? history.replaceState : history.pushState).call(history, {}, "", path); route(); }
window.addEventListener("popstate", () => {
  if (!$("page").classList.contains("hidden")) { closePage(true); return; }
  route();
});
const closeSheet = () => { if (location.pathname !== "/") { history.state?.v ? history.back() : go("/", true); } else hideSheet(); };
$("sheetClose").onclick = closeSheet;
$("sheetBackdrop").onclick = closeSheet;
$("viewerClose").onclick = () => $("viewer").classList.add("hidden");
$("viewer").onclick = (e) => { if (e.target.id === "viewer") $("viewer").classList.add("hidden"); };

// أحداث البطاقات (خريطة + قائمة + مفضلة + لوحة)
document.addEventListener("click", (e) => {
  const h = e.target.closest("[data-fav]"); if (h) { e.stopPropagation(); toggleFav(h.dataset.fav); return; }
  const s = e.target.closest("#sortbar [data-sort]"); if (s) { state.sort = s.dataset.sort; renderAll(); return; }
  const op = e.target.closest("#sortbar [data-open]"); if (op) { state.openOnly = !state.openOnly; renderAll(); return; }
  const c = e.target.closest(".card[data-id], .lcard[data-id], .mcard[data-id]"); if (c) { openOffer(c.dataset.id, true); }
});

// التصنيفات من إعدادات الأدمن (وإلا الافتراضية)
export const categories = () => (state.config.categories && state.config.categories.length) ? state.config.categories : X.CATEGORIES.slice(1);
function renderChips() {
  const list = ["الكل", ...categories()];
  if (!list.includes(state.cat)) state.cat = "الكل";
  $("chips").innerHTML = list.map((c) => `<button class="chip${c === state.cat ? " on" : ""}" data-cat="${esc(c)}">${esc(c)}</button>`).join("");
}
renderChips();
$("chips").onclick = (e) => {
  const b = e.target.closest("[data-cat]"); if (!b) return;
  state.cat = b.dataset.cat; fitted = false;
  document.querySelectorAll("#chips .chip").forEach((x) => x.classList.toggle("on", x === b));
  renderAll();
};
let qt;
$("q").oninput = (e) => { state.q = e.target.value; $("clearQ").classList.toggle("hidden", !state.q); clearTimeout(qt); qt = setTimeout(() => { fitted = false; renderAll(); }, 200); };
$("clearQ").onclick = () => { $("q").value = ""; state.q = ""; $("clearQ").classList.add("hidden"); renderAll(); };
$("filterBtn").onclick = openFilters;
$("placeBtn").onclick = openPlaces;

// ---------------- موقعي ----------------
function locate(center) {
  if (!navigator.geolocation) return toast("المتصفح ما يدعم تحديد الموقع");
  const ok = (p) => {
    state.me = { lat: p.coords.latitude, lng: p.coords.longitude };
    map?.setMe(state.me.lat, state.me.lng);
    if (center) { state.browse = null; map?.setView(state.me.lat, state.me.lng, 14); }
    renderAll();
  };
  const fail = (err) => {
    if (!center) return;
    if (err?.code === 1) return locationHelp(); // الإذن مرفوض
    toast("ما قدرنا نحدد موقعك — تأكد إن الموقع (GPS) شغّال في الجوال", true);
  };
  // نحاول بدقة عالية، ولو تأخر GPS نرجع لموقع تقريبي (شبكة/واي فاي) بدل ما نفشل
  navigator.geolocation.getCurrentPosition(ok, (err) => {
    if (err?.code === 1) return fail(err);
    navigator.geolocation.getCurrentPosition(ok, fail, { enableHighAccuracy: false, timeout: 15000, maximumAge: 300000 });
  }, { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 });
}
function locationHelp() {
  const standalone = window.navigator.standalone === true || matchMedia("(display-mode: standalone)").matches;
  const steps = IS_IOS
    ? ["افتح <b>الإعدادات</b> في الآيفون ← <b>الخصوصية والأمان</b> ← <b>خدمات الموقع</b> وتأكد إنها شغّالة", `انزل لـ <b>${standalone ? "سفاري / مواقع سفاري" : "سفاري"}</b> واختر <b>«أثناء الاستخدام»</b> أو «اسأل»`, "ارجع لحيّنا واضغط زر موقعي مرة ثانية"]
    : standalone
      ? ["اضغط مطوّلاً على أيقونة <b>حيّنا</b> ← <b>معلومات التطبيق</b>", "<b>الأذونات</b> ← <b>الموقع</b> ← <b>السماح أثناء الاستخدام</b>", "إذا ما لقيتها: افتح كروم ← ⋮ ← الإعدادات ← إعدادات المواقع ← الموقع، وشيل حيّنا من «محظور»"]
      : ["اضغط أيقونة <b>القفل / الإعدادات</b> جنب الرابط فوق", "اختر <b>الأذونات</b> ← <b>الموقع</b> ← <b>سماح</b>", "حدّث الصفحة واضغط زر موقعي"];
  showSheet(`<div class="pad install-sheet"><div style="font-size:44px">📍</div><h2>الموقع مقفول لحيّنا</h2>
    <p class="muted">عشان نعرض لك عروض حيّك والأقرب لك، فعّل إذن الموقع:</p>
    <ol>${steps.map((s) => `<li>${s}</li>`).join("")}</ol>
    <button class="btn primary" id="locRetry">جرّبت، حدّد موقعي</button></div>`);
  $("locRetry").onclick = () => { hideSheet(); locate(true); };
}
export const getMyLocation = () => new Promise((resolve, reject) => {
  if (!navigator.geolocation) return reject(new Error("no geo"));
  navigator.geolocation.getCurrentPosition((p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }), reject, { enableHighAccuracy: true, timeout: 12000 });
});
$("locate").onclick = () => locate(true);
$("scopeBtn").onclick = cycleScope;
navigator.permissions?.query({ name: "geolocation" }).then((r) => { if (r.state === "granted") locate(!location.pathname.startsWith("/o/")); }).catch(() => {});

// ---------------- ثبّت حيّنا (أندرويد + آيفون) ----------------
const IS_IOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
const IS_MOBILE = IS_IOS || /android/i.test(navigator.userAgent);
const isStandalone = () => window.navigator.standalone === true || matchMedia("(display-mode: standalone)").matches;
let installEvt = null;
export const canOfferInstall = () => !isStandalone() && (IS_MOBILE || !!installEvt);
if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
function hideInstallTip(remember) {
  $("installTip").classList.add("hidden");
  if (remember) try { localStorage.setItem("hayyna_install_tip", String(Date.now())); } catch {}
}
function installSteps() {
  const inSafari = !/CriOS|FxiOS|EdgiOS/i.test(navigator.userAgent);
  const steps = IS_IOS
    ? [`اضغط زر المشاركة <span class="share-ico">⬆︎</span> ${inSafari ? "تحت في الشريط" : "جنب الرابط فوق"}`, "انزل واختر <b>«إضافة إلى الشاشة الرئيسية»</b>", "اضغط <b>«إضافة»</b>، وافتح حيّنا من أيقونته"]
    : ["اضغط قائمة المتصفح <b>⋮</b> فوق", "اختر <b>«تثبيت التطبيق»</b> أو <b>«إضافة إلى الشاشة الرئيسية»</b>", "افتح حيّنا من أيقونته"];
  showSheet(`<div class="pad install-sheet"><img src="/img/icon-192.png" alt=""><h2>ثبّت حيّنا على جوالك 📲</h2>
    <p class="muted">يفتح بشاشة كاملة بدون شريط المتصفح، مثل التطبيق تماماً</p>
    <ol>${steps.map((s) => `<li>${s}</li>`).join("")}</ol>
    ${IS_IOS && !inSafari ? `<p class="muted small">ما لقيت الخيار؟ افتح الموقع من سفاري وجرّب.</p>` : ""}
    <button class="btn primary" id="installOk">تمام</button></div>`);
  $("installOk").onclick = hideSheet;
}
export async function doInstall() {
  hideInstallTip(true);
  if (installEvt) {
    const e = installEvt; installEvt = null;
    e.prompt();
    const r = await e.userChoice.catch(() => null);
    if (r?.outcome === "accepted") toast("تم التثبيت 🎉 افتح حيّنا من أيقونته");
    return;
  }
  installSteps();
}
function maybeShowInstallTip() {
  if (!canOfferInstall()) return;
  let last = 0; try { last = Number(localStorage.getItem("hayyna_install_tip")) || 0; } catch {}
  if (last === 1 || Date.now() - last < 7 * 864e5) return; // انقفل قبل أقل من أسبوع
  $("installTip").classList.remove("hidden");
}
window.addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); installEvt = e; if (state.tab === "account") renderAccount(); });
window.addEventListener("appinstalled", () => { installEvt = null; hideInstallTip(true); });
$("installBtn").onclick = doInstall;
$("installClose").onclick = () => hideInstallTip(true);
setTimeout(maybeShowInstallTip, 6000);

// ---------------- الدخول ----------------
getRedirectResult(auth).catch(() => {});
// رجع من رابط الدخول بالإيميل
if (isSignInWithEmailLink(auth, location.href)) {
  let email = null; try { email = localStorage.getItem("hayyna_login_email"); } catch {}
  if (!email) email = prompt("اكتب إيميلك لتأكيد الدخول:");
  if (email) signInWithEmailLink(auth, email, location.href)
    .then(() => { try { localStorage.removeItem("hayyna_login_email"); } catch {} setTab("account"); })
    .catch((e) => toast("الرابط منتهي أو مستخدم، اطلب رابط جديد (" + (e.code || "") + ")", true))
    .finally(() => history.replaceState({}, "", "/"));
}
let refUnsub = null;
onAuthStateChanged(auth, async (u) => {
  state.user = u;
  refUnsub?.(); refUnsub = null; state.referrals = [];
  if (!u) { state.role = null; state.profile = null; renderAll(); return; }
  refUnsub = onSnapshot(query(collection(db, "referrals"), where("referrer", "==", u.uid)),
    (snap) => { state.referrals = snap.docs.map((d) => ({ ...d.data(), id: d.id })); if (state.tab === "account") renderAccount(); }, () => {});
  state.roleLoading = true; renderAccount();
  let pending = state.pendingRole; try { pending = pending || sessionStorage.getItem("hayyna_pending_role"); sessionStorage.removeItem("hayyna_pending_role"); } catch {}
  try {
    const ref = doc(db, "users", u.uid), snap = await getDoc(ref);
    const data = snap.exists() ? snap.data() : {};
    let role = data.role || null;
    if (role === "customer" && pending === "merchant") {
      role = "merchant";
      await setDoc(ref, { role, updatedAt: serverTimestamp() }, { merge: true });
      toast("صار حسابك حساب صاحب محل 🏪", true);
    }
    if (!role) {
      // حساب جديد: نوع الحساب حسب الزر اللي اختاره (والحسابات القديمة اللي عندها متجر = تاجر)
      const merchant = pending ? null : await getDoc(doc(db, "merchants", u.uid)).catch(() => null);
      role = pending || (merchant?.exists() ? "merchant" : "customer");
      await setDoc(ref, { role, updatedAt: serverTimestamp() }, { merge: true });
    }
    state.role = role;
    state.profile = { name: data.name || "", phone: data.phone || "" };
    const remote = new Set(data.favorites || []);
    const merged = new Set([...remote, ...state.favs]);
    state.favs = merged; saveFavsLocal();
    if (merged.size !== remote.size) setDoc(ref, { role, favorites: [...merged].slice(0, 500) }, { merge: true }).catch(() => {});
  } catch (e) { console.error(e); state.role = state.role || pending || "customer"; state.profile = state.profile || {}; }
  state.roleLoading = false; state.pendingRole = null;
  if (pending) setTab("account");
  renderAll();
});

// ---------------- البيانات ----------------
onSnapshot(doc(db, "config", "app"), (s) => { state.config = s.data() || {}; renderChips(); map?.setShowShops(state.config.showGooglePoi !== false); }, () => {});
onSnapshot(query(collection(db, "offers"), where("isApproved", "==", true)), (snap) => {
  state.offers = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
  const first = !state.loaded; state.loaded = true;
  renderAll(); if (first) route();
}, (e) => { console.error(e); toast("تعذّر تحميل العروض، حدّث الصفحة"); });
onSnapshot(query(collection(db, "stores"), where("approved", "==", true)), (snap) => {
  state.stores = snap.docs.map((d) => ({ ...d.data(), uid: d.id }));
  const first = !state.storesLoaded; state.storesLoaded = true;
  renderAll();
  if (first && location.pathname.startsWith("/s/")) route();
  else if (first && state.view?.type === "o" && !$("sheet").classList.contains("hidden")) openOffer(state.view.id, false);
}, (e) => console.error(e));
setInterval(renderAll, 60000);

// الخريطة (تتحمّل بعد الواجهة)
createMap($("map"), { key: MAPS_WEB_KEY, showShops: state.config.showGooglePoi !== false, onPoiClick: (p) => openPoi(p) }).then((m) => {
  map = m; if (state.me) map.setMe(state.me.lat, state.me.lng);
  map.setShowShops(state.config.showGooglePoi !== false);
  renderAll();
  if (state.view?.type === "o") { const o = state.offers.find((x) => x.id === state.view.id); if (o) { const { b } = nearestBranch(o); map.setView(b.latitude, b.longitude, 15); } }
}).catch((e) => { console.error(e); toast("تعذّر تحميل الخريطة"); });

try { const t = sessionStorage.getItem("hayyna_tab"); if (t && !location.pathname.match(/^\/(o|s)\//)) setTab(t); } catch {}
