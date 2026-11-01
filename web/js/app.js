// لكوفر على الويب — نفس تجربة التطبيق: خريطة، عروض، مفضلة، حسابي (زبون / تاجر).
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, OAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, onAuthStateChanged, signOut,
  sendSignInLinkToEmail, isSignInWithEmailLink, signInWithEmailLink, deleteUser
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  getFirestore, initializeFirestore, disableNetwork, enableNetwork, collection, doc, onSnapshot, query, where, updateDoc, setDoc, getDoc, getDocs, addDoc, deleteDoc, increment, serverTimestamp, Timestamp
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig, FIRESTORE_DB_ID, AUTO_HIDE_REPORTS, ADMIN_EMAILS, MAPS_WEB_KEY } from "../config.js";
import * as X from "./logic.js";
import { createMap, placeDetails, searchPlaces } from "./map.js";
import { canScout, mountAdForm } from "./scout.js";
import { startI18n } from "./i18n.js";
import { watchAnnouncements, renderAnnStrip, annAccountHtml, bindAnn } from "./announce.js";
import { creditsOf, activeEffects, isPaidUp, exclusiveOf } from "./credits.js";
import { renderStoreItems, openMyOrders, ordersOn, watchMyOrders, myOrdersClosed, renderStoreRatings, orderFromOffer, offerOrderable, restockBtn, bindRestock, checkRestock } from "./catalog-ui.js";
startI18n();

const { esc, cld, thumb, priceLabel, isPercentOnly, effectivePercent, branchesOf, km, distText, openNow, hoursText,
  remainingText, tiersText, startsText, deliveryText, badge, waDigits, norm, districtLabel, money, gallery, videoThumb } = X;

export const fbApp = initializeApp(firebaseConfig);
export const auth = getAuth(fbApp);
auth.languageCode = (() => { try { return localStorage.getItem("hayyna_lang") === "en" ? "en" : "ar"; } catch { return "ar"; } })(); // رسائل الدخول بالإيميل تطلع بالعربي
// يكتشف لحاله لو شبكة الجوال تقطع الاتصال المباشر ويحوّل لطريقة تشتغل (عشان تحديثات الطلبات توصل بدون تحديث الصفحة)
export const db = (() => { try { return initializeFirestore(fbApp, { experimentalAutoDetectLongPolling: true }, FIRESTORE_DB_ID); } catch { return getFirestore(fbApp, FIRESTORE_DB_ID); } })();
export const $ = (id) => document.getElementById(id);

export const state = {
  offers: [], stores: [], q: "", cat: "الكل", sort: "near", openOnly: false, browse: null,
  me: null, selected: null, branchId: null, view: null, tab: "map",
  favs: new Set(), user: null, role: null, profile: null, scope: "all", roleLoading: false, pendingRole: null,
  config: {}, loaded: false, storesLoaded: false, referrals: [], boosts: [], fresh: false, kind: "all", scout: false, seenAt: Date.now(), newCount: 0,
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
  { min: 0, name: "سفير الحي", ico: "🥉" }, { min: 1000, name: "سفير فضي", ico: "🥈" },
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
// زر الرجوع: كل نافذة/تبويب تنحفظ في سجل المتصفح عشان الرجوع يقفلها بدل ما يطلع من الموقع
let ignorePop = 0;
export function popSilently() { ignorePop++; history.back(); }
export function showSheet(html) {
  if ($("sheet").classList.contains("hidden") && !/^\/(o|s)\//.test(location.pathname)) history.pushState({ sheet: 1 }, "", location.pathname);
  $("sheetBody").innerHTML = html; $("sheetBody").scrollTop = 0;
  $("sheet").classList.remove("hidden"); $("sheetBackdrop").classList.remove("hidden");
}
export function hideSheet(fromPop) {
  const wasOpen = !$("sheet").classList.contains("hidden");
  $("sheet").classList.add("hidden"); $("sheetBackdrop").classList.add("hidden"); state.view = null; myOrdersClosed();
  if (wasOpen && !fromPop && history.state?.sheet) popSilently();
}
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
  if (!fromPop && history.state?.page) popSilently();
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
// نزل اليوم (آخر 24 ساعة)
const isFresh = (o, now = Date.now()) => { const t = X.ms(o.startsAt) || X.ms(o.createdAt); return !!t && t <= now && now - t < 864e5; };
// الخدمات المدفوعة (تمييز / بارز) + شارة الحصري
const isFeat = (o) => !!o.isFeatured || activeEffects(state.boosts, o.id).feature;
const isListTop = (o) => activeEffects(state.boosts, o.id).listTop;
const isSpot = (o) => activeEffects(state.boosts, o.id).spotlight;
// 🚀 السوبر على هالفرع؟ (السوبر صار لكل فرع؛ القديم يشمل كل الفروع)
const isSpotAt = (o, bid) => { const e = activeEffects(state.boosts, o.id); return e.spotlight && (e.spotAll || !bid || e.spotBranches.has(bid)); };
const spotPill = (o) => isSpot(o) ? `<span class="pill spot">🚀 عرض سوبر</span>` : "";
const exclBadge = (o, cls = "") => { const ex = exclusiveOf(state.config); return o.exclusive && ex.enabled ? `<span class="xbadge ${cls}" style="background:${esc(ex.color)}">${esc(ex.icon)} ${esc(ex.label)}</span>` : ""; };
function filtered({ ignoreQuery = false } = {}) {
  const now = Date.now(), q = ignoreQuery ? "" : norm(state.q), catKey = state.cat.replace(/[^\p{L}\s]/gu, "").trim();
  const list = state.offers.filter((o) => {
    if (X.isExpired(o, now) || (o.reportsCount || 0) >= AUTO_HIDE_REPORTS) return false;
    if (!isPaidUp(o, state.boosts, creditsOf(state.config), now)) return false;
    if (state.cat !== "الكل" && !String(o.cuisineType || "").includes(catKey)) return false;
    if (state.openOnly && !branchesOf(o).some((b) => openNow(b) === true)) return false;
    if (state.fresh && !isFresh(o, now)) return false;
    if (state.kind === "new" && o.postType !== "new") return false;
    if (!q) return true;
    const hay = [o.restaurantName, o.offerTitle, o.description, o.district, o.cuisineType, o.itemsScope, ...(o.tags || []),
      ...branchesOf(o).flatMap((b) => [b.district, b.landmark, b.name])];
    return hay.some((h) => norm(h).includes(q));
  }).map((o) => ({ o, ...nearestBranch(o) }));
  const feat = (a, b) => ((isSpot(b.o) ? 2 : 0) + (isFeat(b.o) ? 1 : 0)) - ((isSpot(a.o) ? 2 : 0) + (isFeat(a.o) ? 1 : 0));
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
function markerHtml(o, extra, on, bid) {
  const sp = bid === undefined ? isSpot(o) : isSpotAt(o, bid);
  const bd = badge(o);
  const logo = o.logoUrl ? `<img class="lg" src="${esc(cld(o.logoUrl, 80))}" alt="">` : "";
  const ex = exclusiveOf(state.config);
  return `<div class="mk${on ? " on" : ""}${o.source === "ad" ? " isad" : ""}${isFeat(o) ? " feat" : ""}${sp ? " spot" : ""}${o.postType === "new" ? " isnew" : ""}">${sp && !on ? `<span class="mk-name">🚀 ${esc(o.restaurantName)}</span>` : ""}${o.exclusive && ex.enabled ? `<span class="xdot" style="background:${esc(ex.color)}">${esc(ex.icon)}</span>` : ""}<div class="ring"><img src="${esc(thumb(o, 140))}" alt="" loading="lazy"></div>${logo}`
    + (extra > 0 ? `<span class="more">+${extra}</span>` : "")
    + (bd ? `<span class="pct${bd.soon ? " soon" : bd.isNew ? " new" : ""}">${esc(bd.t)}</span>` : "") + `</div>`;
}
// ترتيب الأولوية على الخريطة: 🚀 سوبر ← ✨ مميز ← عادي ← 📷 عروض الإعلانات
const rankOf = (o, bid) => (bid === undefined ? isSpot(o) : isSpotAt(o, bid)) ? 3 : isFeat(o) ? 2 : o.source === "ad" ? 0 : 1;
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
    arr.sort((x, y) => rankOf(y, b.id) - rankOf(x, b.id) || effectivePercent(y) - effectivePercent(x));
    const top = arr[0], on = state.selected === top.id;
    items.push({ lat: b.latitude, lng: b.longitude, html: markerHtml(top, arr.length - 1, on, b.id), z: on ? 1000 : [5, 10, 500, 700][rankOf(top, b.id)],
      onClick: () => { state.selected = top.id; state.branchPick = { sid: top.merchantId, id: b.id }; renderAll(); scrollCardIntoView(top.id); if (arr.length > 1) openGroup(arr); } });
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
  return `<div class="card${state.selected === o.id ? " on" : ""}${isFeat(o) ? " feat" : ""}" data-id="${esc(o.id)}" role="button">
    <div class="thumb"><img src="${esc(thumb(o))}" alt="" loading="lazy">${bd ? `<span class="badge${bd.soon ? " soon" : bd.isNew ? " new" : ""}">${esc(bd.t)}</span>` : ""}</div>
    <div class="info">
      <div class="store-row">${o.logoUrl ? `<img class="logo" src="${esc(cld(o.logoUrl, 60))}" alt="">` : ""}<span>${esc(o.restaurantName)}</span>
        <button class="heart" data-fav="${esc(o.id)}" aria-label="مفضلة">${heartSvg(fav)}</button></div>
      <div class="title">${esc(o.offerTitle)}</div>
      <div class="price">${esc(priceLabel(o))}${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)}</s>` : ""}</div>
      <div class="meta">${esc(meta)}</div>
      <div class="pills">${X.isSoldOut(o) ? '<span class="pill closed">❌ نفد اليوم</span>' : ""}${spotPill(o)}${exclBadge(o)}${op === true ? '<span class="pill open">● مفتوح</span>' : op === false ? '<span class="pill closed">● مغلق</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
    </div></div>`;
}
function listCard(o) {
  const { b, d } = nearestBranch(o), bd = badge(o), rem = remainingText(o), op = openNow(b), fav = state.favs.has(o.id);
  return `<div class="lcard${isFeat(o) ? " feat" : ""}" data-id="${esc(o.id)}" role="button">
    <div class="lthumb"><img src="${esc(thumb(o, 500))}" alt="" loading="lazy">${bd ? `<span class="badge${bd.soon ? " soon" : bd.isNew ? " new" : ""}">${esc(bd.t)}</span>` : ""}
      <button class="heart float" data-fav="${esc(o.id)}" aria-label="مفضلة">${heartSvg(fav)}</button></div>
    <div class="linfo">
      <div class="store-row">${o.logoUrl ? `<img class="logo" src="${esc(cld(o.logoUrl, 60))}" alt="">` : ""}<span>${esc(o.restaurantName)}</span></div>
      <div class="title">${esc(o.offerTitle)}</div>
      <div class="price">${esc(priceLabel(o, true))}${!isPercentOnly(o) && o.originalPrice > o.price ? `<s>${money(o.originalPrice)}</s>` : ""}</div>
      <div class="meta">${esc([districtLabel(b.district || o.district), distText(d), deliveryText(o) || ""].filter(Boolean).join(" • "))}</div>
      <div class="pills">${X.isSoldOut(o) ? '<span class="pill closed">❌ نفد اليوم</span>' : ""}${spotPill(o)}${exclBadge(o)}${op === true ? '<span class="pill open">● مفتوح</span>' : op === false ? '<span class="pill closed">● مغلق</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
    </div></div>`;
}
function scrollCardIntoView(id) {
  const el = document.querySelector(`#carousel .card[data-id="${CSS.escape(id)}"]`);
  if (el) el.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
}

// ---------------- 🚀 بطاقة «عرض سوبر قريب منك» (مرة باليوم) ----------------
let spotShown = false;
// للتجربة: رابط فيه ?spot=1 يتجاهل قيد "مرة باليوم"
const SPOT_TEST = new URLSearchParams(location.search).has("spot");
function maybeSpotlight(list) {
  if (spotShown || !state.loaded || state.view || location.pathname !== "/" || state.tab !== "map") return;
  const today = new Date().toISOString().slice(0, 10);
  let last = ""; try { last = localStorage.getItem("hayyna_spot_day") || ""; } catch {}
  if (last === today && !SPOT_TEST) { spotShown = true; return; }
  // قريب منك: بالموقع (3 كم)، وإلا بالحي اللي يتصفحه أو حي حسابه
  const ref = state.me || state.browse;
  const dist = state.profile?.district || "";
  const near = list.filter(({ o }) => isSpot(o) && (ref ? branchesOf(o).some((b) => isSpotAt(o, b.id) && km(ref, { lat: b.latitude, lng: b.longitude }) <= 3)
    : dist && branchesOf(o).some((b) => isSpotAt(o, b.id) && b.district === dist)));
  if (!near.length) return;
  spotShown = true; // ما نعيد الاختيار في نفس الجلسة
  const { o, d } = near[Math.floor(Math.random() * near.length)];
  let tries = 0;
  const show = () => {
    // فيه نافذة مفتوحة؟ ننتظر لين تتسكّر (بدل ما تضيع البطاقة)
    if (!$("sheet").classList.contains("hidden") || !$("page").classList.contains("hidden") || state.view || state.tab !== "map") { if (++tries < 40) setTimeout(show, 3000); return; }
    try { localStorage.setItem("hayyna_spot_day", today); } catch {} // تنحسب «طلعت» بس لما تطلع فعلاً
    showSheet(`<div class="spot-card">
      <div class="spot-head">🚀 عرض سوبر قريب منك</div>
      <div class="spot-img"><img src="${esc(thumb(o, 700))}" alt="">${badge(o) ? `<span class="badge${badge(o).isNew ? " new" : ""}">${esc(badge(o).t)}</span>` : ""}</div>
      <div class="pad">
        <div class="store-row">${o.logoUrl ? `<img class="logo" src="${esc(cld(o.logoUrl, 60))}" alt="">` : ""}<span class="nt">${esc(o.restaurantName)}</span></div>
        <div class="title">${esc(o.offerTitle)}</div>
        <div class="price">${esc(priceLabel(o, true))}</div>
        <div class="meta">${esc([districtLabel(o.district), distText(d)].filter(Boolean).join(" • "))}</div>
        <div style="margin-top:12px;display:grid;grid-template-columns:1fr 2fr;gap:8px"><button class="btn call" id="spLater">لاحقاً</button><button class="btn dir" id="spGo">شوف العرض</button></div>
      </div></div>`);
    $("spLater").onclick = hideSheet;
    $("spGo").onclick = () => { hideSheet(); setTimeout(() => openOffer(o.id, true), 60); };
  };
  setTimeout(show, 1200);
}

// ---------------- الرسم ----------------
export function renderAll() {
  window.__hayynaNoCrop = (state.config?.catalog?.features || {}).crop === false;
  watchMyOrders(); // طلباتي تتحدّث لحظياً (وتنبيه لما تتغيّر الحالة)
  renderAnnStrip();
  const list = filtered();
  renderMap(list);
  const car = $("carousel"), sl = car.scrollLeft;
  car.innerHTML = list.map(carouselCard).join("");
  car.scrollLeft = sl;
  car.classList.toggle("hidden", !list.length);
  document.querySelectorAll(".car-arrow").forEach((a) => a.classList.toggle("hidden", list.length < 2));
  const empty = $("empty");
  if (state.loaded && !list.length) {
    empty.innerHTML = state.offers.length ? (state.q && storeMatches().length ? `<b>ما فيه عروض بهالاسم</b>بس لقينا محلات فوق 👆` : `<b>ما لقينا عروض مطابقة</b>جرّب كلمة ثانية أو شيل الفلاتر`) : `<b>لا توجد عروض حالياً</b>المحلات تنزّل عروضها أول بأول 🔥`;
    empty.classList.remove("hidden");
  } else empty.classList.add("hidden");
  $("filterDot").classList.toggle("hidden", !(state.openOnly || state.sort !== "near" || state.fresh || state.kind !== "all"));
  $("placeLabel").textContent = state.browse ? state.browse.name : "حولي";
  renderScope(list.length);
  maybeSpotlight(list);
  // قائمة العروض
  $("sortbar").innerHTML = [["near", "📍 الأقرب"], ["discount", "🔥 أكبر خصم"], ["cheap", "💰 الأرخص"], ["new", "🆕 الأحدث"]]
    .map(([k, l]) => `<button class="chip${state.sort === k ? " on" : ""}" data-sort="${k}">${l}</button>`).join("")
    + `<button class="chip${state.openOnly ? " on" : ""}" data-open="1">● مفتوح الآن</button>`
    + `<button class="chip${state.kind === "new" ? " on" : ""}" data-kind="1">✨ جديدنا</button>`
    + `<button class="chip${state.fresh ? " on" : ""}" data-fresh="1">🆕 نزل اليوم${state.fresh ? " ✕" : ""}</button>`;
  const pins = list.filter(({ o }) => isListTop(o));
  $("list").innerHTML = (pins.length ? `<div class="pinned"><div class="ptitle">📌 إعلانات بارزة</div>${pins.map(({ o }) => listCard(o).replace('class="lcard"', 'class="lcard pin"')).join("")}</div>` : "")
    + (list.length ? list.filter(({ o }) => !isListTop(o)).map(({ o }) => listCard(o)).join("") : `<div class="empty-inline">ما فيه عروض مطابقة</div>`);
  // المفضلة
  const favList = state.offers.filter((o) => state.favs.has(o.id) && !X.isExpired(o));
  $("favs").innerHTML = favList.length ? favList.map(listCard).join("")
    : `<div class="empty-inline"><div style="font-size:40px">🤍</div><b>ما عندك مفضلة للحين</b><br>اضغط القلب على أي عرض عشان تحفظه هنا</div>`;
  renderStoreHits();
  // 🟡 نقطة الشعار: عروض نزلت حولك بعد ما فتحت (أو بعد آخر تحديث)
  state.newCount = list.filter(({ o }) => (X.ms(o.startsAt) || X.ms(o.createdAt) || 0) > state.seenAt && !X.isUpcoming(o)).length;
  $("brandDot")?.classList.toggle("hidden", !state.newCount);
  if (state.tab === "account") renderAccount();
}

// ---------------- شعار لكوفر: ضغطة = قائمة، ضغطة طويلة = تحديث ----------------
let refreshing = false;
async function refreshOffers() {
  if (refreshing) return; refreshing = true;
  const btn = $("brandBtn"); btn.classList.add("spin");
  const fresh = filtered().filter(({ o }) => (X.ms(o.startsAt) || X.ms(o.createdAt) || 0) > state.seenAt && !X.isUpcoming(o));
  try { await disableNetwork(db); await enableNetwork(db); } catch {}
  if (state.me) locate(false);
  await new Promise((r) => setTimeout(r, 900));
  state.seenAt = Date.now();
  if (fresh.length && map) { map.fitBounds(fresh.map(({ b }) => [b.latitude, b.longitude])); toast(`🆕 ${fresh.length === 1 ? "عرض جديد" : fresh.length + " عروض جديدة"} حولك`); }
  else toast("✅ العروض محدّثة");
  renderAll();
  btn.classList.remove("spin"); refreshing = false;
}
function closeBrandMenu() { $("brandMenu").classList.add("hidden"); $("brandBtn").setAttribute("aria-expanded", "false"); }
function openBrandMenu() {
  const m = $("brandMenu"), n = state.newCount, team = state.scout;
  const item = (act, ico, label, sub = "") => `<button role="menuitem" data-bm="${act}"><span class="bi">${ico}</span><span class="bl">${label}${sub ? `<small>${sub}</small>` : ""}</span></button>`;
  m.innerHTML = item("refresh", "🔄", "تحديث العروض", n ? `نزل ${n === 1 ? "عرض جديد" : n + " عروض جديدة"} حولك` : "")
    + item("fresh", "🆕", state.fresh ? "كل العروض" : "نزل اليوم")
    + item("places", "📍", "تصفّح حي")
    + item("theme", isDark() ? "☀️" : "🌙", isDark() ? "المظهر الفاتح" : "المظهر الداكن")
    + item("share", "↗️", "شارك لكوفر مع صديق")
    + (team ? `<div class="bm-sep">للفريق</div>` + item("ad", "📷", "ارفع عرض من إعلان", "اكتب اسم المحل وارفع الإعلان") + item("admin", "🛠️", "لوحة الإدارة") : "")
    + `<div class="bm-tip">💡 ضغطة طويلة على الشعار = تحديث سريع</div>`;
  m.classList.remove("hidden"); $("brandBtn").setAttribute("aria-expanded", "true");
  m.querySelectorAll("[data-bm]").forEach((b) => b.onclick = () => {
    closeBrandMenu();
    ({
      refresh: refreshOffers,
      fresh: () => { state.fresh = !state.fresh; fitted = false; renderAll(); },
      places: openPlaces,
      theme: () => { try { localStorage.setItem("hayyna_theme", isDark() ? "light" : "dark"); } catch {} applyTheme(); },
      share: () => share("لكوفر 📍 كل عروض المحلات اللي حولك على خريطة وحدة", withRef(location.origin), "لكوفر"),
      ad: openAdSearch,
      admin: () => { location.href = "/admin/"; },
    })[b.dataset.bm]?.();
  });
}
(() => {
  const btn = $("brandBtn"); let t = null, long = false;
  btn.addEventListener("pointerdown", () => { long = false; t = setTimeout(() => { long = true; navigator.vibrate?.(30); closeBrandMenu(); refreshOffers(); }, 550); });
  ["pointerup", "pointerleave", "pointercancel"].forEach((e) => btn.addEventListener(e, () => clearTimeout(t)));
  btn.addEventListener("contextmenu", (e) => e.preventDefault());
  btn.addEventListener("click", (e) => { e.stopPropagation(); if (long) return; $("brandMenu").classList.contains("hidden") ? openBrandMenu() : closeBrandMenu(); });
  document.addEventListener("click", (e) => { if (!e.target.closest("#brandMenu")) closeBrandMenu(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeBrandMenu(); });
})();
// 📷 للفريق: رفع عرض من إعلان بالبحث عن المحل (بدون ما تدوّر عليه في الخريطة)
function openAdSearch() {
  showSheet(`<div class="pad" id="adHost"></div>`);
  const c = state.me || map?.center?.();
  mountAdForm($("adHost"), {
    db, fbApp, user: state.user, config: state.config, mapsKey: MAPS_WEB_KEY, categories: categories(), toast,
    near: c ? { lat: c.lat, lng: c.lng } : null,
    onDone: (id) => { hideSheet(); setTimeout(() => openOffer(id, true), 900); },
  });
}

// ---------------- البحث عن محل (المتاجر المسجّلة + محلات Google) ----------------
function storeMatches() {
  const q = norm(state.q); if (q.length < 2) return [];
  return state.stores.map((s) => {
    const bs = s.branches || [];
    const hay = [s.name, s.category, s.about, ...bs.flatMap((b) => [b.name, b.district, b.landmark])].map(norm);
    const score = norm(s.name).startsWith(q) ? 3 : norm(s.name).includes(q) ? 2 : hay.some((h) => h.includes(q)) ? 1 : 0;
    const b = state.me && bs.length ? bs.reduce((a, c) => km(state.me, { lat: c.latitude, lng: c.longitude }) < km(state.me, { lat: a.latitude, lng: a.longitude }) ? c : a) : bs[0];
    return { s, b, score, d: state.me && b ? km(state.me, { lat: b.latitude, lng: b.longitude }) : null };
  }).filter((x) => x.score > 0).sort((a, b) => b.score - a.score || (a.d ?? 1e9) - (b.d ?? 1e9)).slice(0, 8);
}
function renderStoreHits() {
  const el = $("storeHits"); if (!el) return;
  const q = state.q.trim(), hits = storeMatches();
  const google = q.length >= 2; // يحمّل خرائط Google لحاله لو الخريطة احتياطية
  if (!hits.length && !google) { el.classList.add("hidden"); el.innerHTML = ""; return; }
  el.innerHTML = hits.map(({ s, d }, i) => `<button class="sh" data-sh="${i}">${s.logoUrl ? `<img src="${esc(cld(s.logoUrl, 80))}" alt="">` : "🏪"} <span>${esc(s.name)}</span>${d != null ? `<small>${esc(distText(d))}</small>` : ""}</button>`).join("")
    + (google ? `<button class="sh g" data-shg="1">🔎 «${esc(q.slice(0, 20))}» في محلات Google</button>` : "");
  el.classList.remove("hidden");
  el.querySelectorAll("[data-sh]").forEach((b) => b.onclick = () => {
    const { s, b: br } = hits[+b.dataset.sh];
    if (br && map) map.panTo(br.latitude, br.longitude, 16);
    openStore(s.uid, true);
  });
  el.querySelector("[data-shg]")?.addEventListener("click", searchGoogleShops);
}
async function searchGoogleShops() {
  const q = state.q.trim(); if (!q) return;
  showSheet(`<div class="pad"><div class="spinner"></div></div>`);
  const near = state.me || map?.center?.();
  let res = [];
  try { res = await searchPlaces(MAPS_WEB_KEY, q, near ? { lat: near.lat, lng: near.lng } : null); } catch (e) { console.warn(e); }
  if (!res.length) { showSheet(`<div class="pad"><div class="section-title">🔎 ${esc(q)}</div><p class="desc">ما لقينا محلات بهالاسم في Google. جرّب اسم ثاني.</p></div>`); return; }
  showSheet(`<div class="pad"><div class="section-title">🔎 محلات Google: ${esc(q)}</div>
    <div class="branches">${res.map((p, i) => {
      const d = state.me ? km(state.me, { lat: p.lat, lng: p.lng }) : null;
      return `<button class="branch" data-gp="${i}"><div class="b-main"><div class="b-name">${esc(p.name)}</div><div class="b-sub">${esc(p.address)}</div></div><div class="b-sub">${esc(distText(d))}</div></button>`;
    }).join("")}</div></div>`);
  $("sheetBody").querySelectorAll("[data-gp]").forEach((b) => b.onclick = () => {
    const p = res[+b.dataset.gp];
    // لو المحل مسجّل عندنا نفتح صفحته، وإلا بطاقة المحل (ادعه / ارفع عرض)
    map?.panTo(p.lat, p.lng, 17);
    openPoi({ placeId: p.placeId, lat: p.lat, lng: p.lng });
  });
}

// ---------------- تفاصيل العرض ----------------
function galleryHtml(o) {
  const items = gallery(o);
  const inner = items.map((u) => o.mediaType === "VIDEO"
    ? `<video src="${esc(u)}" poster="${esc(videoThumb(u, 800))}" controls playsinline preload="none"></video>`
    : `<img src="${esc(cld(u, 900))}" data-full="${esc(cld(u, 1600))}" alt="">`).join("");
  return `<div class="gallery">${inner}</div>${items.length > 1 ? `<div class="dots">${items.map(() => "<i></i>").join("")}</div>` : ""}`;
}
/** ذرات لمعان ✨ أول ما تنفتح صفحة عرض سوبر (ثانيتين بس، وتتوقف مع «تقليل الحركة») */
function sparkle(el) {
  if (!el || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  for (let i = 0; i < 14; i++) {
    const s = document.createElement("span"); s.className = "spark"; s.textContent = i % 3 ? "✦" : "✨";
    s.style.left = Math.random() * 100 + "%"; s.style.top = Math.random() * 100 + "%"; s.style.animationDelay = (Math.random() * 0.8).toFixed(2) + "s";
    el.appendChild(s); setTimeout(() => s.remove(), 2400);
  }
}
export function openOffer(id, push) {
  const o = state.offers.find((x) => x.id === id);
  if (!o) { if (state.loaded) { toast("العرض انتهى أو غير متاح"); go("/", true); } return; }
  state.view = { type: "o", id }; state.selected = id;
  if (push) history.pushState({ v: "o", id }, "", `/o/${encodeURIComponent(id)}`);
  document.title = `${o.offerTitle} — ${o.restaurantName} | لكوفر`;
  if (!sessionStorage.getItem("v_" + id)) { sessionStorage.setItem("v_" + id, "1"); bump(id, "viewsCount"); }
  const bs = branchesOf(o);
  // الفرع: اللي ضغطه من الخريطة (أو اختاره قبل) لنفس المحل، وإلا الأقرب
  const pick = state.branchPick?.sid === o.merchantId ? state.branchPick.id : null;
  let br = bs.find((b) => b.id === pick) || nearestBranch(o).b;
  if (br) state.branchPick = { sid: o.merchantId, id: br.id };
  if (map && br) map.panTo(br.latitude, br.longitude);
  const draw = () => {
    const d = state.me ? km(state.me, { lat: br.latitude, lng: br.longitude }) : null;
    const rem = remainingText(o), op = openNow(br), tiers = tiersText(o), starts = startsText(o), del = deliveryText(o);
    const store = state.stores.find((s) => s.uid === o.merchantId), fav = state.favs.has(o.id);
    const isOwner = state.user && state.user.uid === o.merchantId;
    const sup = isSpot(o) && (state.config?.catalog?.features || {}).superGlow !== false;
    showSheet(`${sup ? `<div class="super-wrap"><div class="super-ribbon">🚀 عرض سوبر</div>${galleryHtml(o)}<i class="super-shine"></i></div>` : galleryHtml(o)}
      <div class="pad${sup ? " super-pad" : ""}">
        <div class="store-link">
          <div class="name">${o.logoUrl ? `<img src="${esc(cld(o.logoUrl, 100))}" alt="">` : ""}<span>${esc(o.restaurantName)}${store && store.verified ? ' <span class="verified" title="متجر موثّق">✔</span>' : ""}</span></div>
          ${store || isOwner ? `<button class="btn-soft" data-store="${esc(o.merchantId)}">🏪 ${isOwner ? "صفحة متجري" : "صفحة المتجر"} ›</button>` : ""}
          ${isOwner ? "" : `<button class="icon-report" data-act="report" title="بلاغ عن العرض" aria-label="بلاغ">🚩</button>`}
        </div>
        ${o.source === "ad" ? `<div class="ad-src">📷 <div><b>من إعلان المحل</b><small>المحل مو مسجّل في لكوفر — تأكد من العرض مع المحل قبل ما تروح</small></div>${isOwner ? "" : `<button class="btn-soft" data-act="adclaim">أنا صاحب المحل</button>`}</div>` : ""}
        <h1 class="title">${esc(o.offerTitle)}</h1>
        ${X.isSoldOut(o) ? `<div class="sold-box"><div><b>❌ هذا العرض نفد اليوم</b><small>يرجع غالباً بكرة — نذكّرك أول ما يتوفر</small></div>${restockBtn(o.id)}</div>` : ""}
        <div class="pills">${X.isSoldOut(o) ? '<span class="pill closed">❌ نفد اليوم — يرجع بكرة</span>' : ""}${exclBadge(o)}${o.deal?.type === "bundle" ? '<span class="pill">🎁 باقة</span>' : o.deal?.type === "bogo" ? `<span class="pill urgent">➕ ${o.deal.getPct === 50 ? "الثاني بنص السعر" : "1+1 مجاناً"}</span>` : ""}${o.grandOpening ? '<span class="pill">🎉 افتتاح</span>' : ""}${op === true ? '<span class="pill open">● مفتوح الآن</span>' : op === false ? '<span class="pill closed">● مغلق الآن</span>' : ""}${rem ? `<span class="pill ${rem.cls}">${esc(rem.t)}</span>` : ""}</div>
        <div class="${isOwner ? "row3" : "row2"}">
          <button class="btn-out" data-act="fav">${heartSvg(fav)} ${fav ? "محفوظ" : "حفظ"}</button>
          <button class="btn-out" data-act="share">↗︎ مشاركة</button>
          ${isOwner ? `<button class="btn-out" data-act="edit">✎ تعديل</button>` : ""}
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
        ${!isOwner && offerOrderable(o) ? `<button class="btn dir big deal-go" data-act="dealgo">${esc(offerOrderable(o))}</button>` : ""}
        <div class="actions">
          <a class="btn dir" data-act="dir" href="https://www.google.com/maps/dir/?api=1&destination=${br.latitude},${br.longitude}" target="_blank" rel="noopener">🧭 الاتجاهات إلى المحل</a>
          <a class="btn wa" data-act="wa" href="https://wa.me/${waDigits(br.phone || o.phone)}?text=${encodeURIComponent(`مرحباً، أود طلب العرض: ${o.offerTitle} (${priceLabel(o)}) المعلن عنه في لكوفر\n${offerUrl(o)}`)}" target="_blank" rel="noopener">💬 طلب واتساب</a>
          <a class="btn call" href="tel:${esc(br.phone || o.phone)}">📞 اتصال</a>
        </div>
      </div>`);
    const body = $("sheetBody");
    body.querySelectorAll("[data-branch]").forEach((el) => el.onclick = () => {
      br = bs.find((b) => b.id === el.dataset.branch) || br; state.branchPick = { sid: o.merchantId, id: br.id };
      if (map) map.panTo(br.latitude, br.longitude); draw();
    });
    body.querySelector("[data-store]")?.addEventListener("click", (e) => openStore(e.currentTarget.dataset.store, true));
    body.querySelector('[data-act="dir"]').addEventListener("click", () => bump(o.id, "directionsCount"));
    body.querySelector('[data-act="wa"]').addEventListener("click", () => bump(o.id, "whatsappCount"));
    if (sup) sparkle(body.querySelector(".super-wrap"));
    body.querySelector('[data-act="fav"]').onclick = () => { toggleFav(o.id); draw(); };
    body.querySelector('[data-act="share"]').onclick = () => share(`🔥 ${o.offerTitle}\n📍 ${o.restaurantName}${o.district ? " — " + o.district : ""}\n💰 ${priceLabel(o, true)}`, offerUrl(o), o.offerTitle);
    body.querySelector('[data-act="report"]')?.addEventListener("click", () => reportOffer(o));
    bindRestock(body, draw);
    body.querySelector('[data-act="adclaim"]')?.addEventListener("click", () => {
      try { sessionStorage.setItem("hayyna_claim", JSON.stringify({ name: o.restaurantName, lat: o.latitude, lng: o.longitude, placeId: o.placeId || "" })); } catch {}
      poiLog(o.placeId || o.id, "claims"); hideSheet(); setTab("account"); if (!state.user) openLogin("merchant");
    });
    body.querySelector('[data-act="dealgo"]')?.addEventListener("click", (e) => { e.currentTarget.disabled = true; orderFromOffer(o).finally(() => { const b = body.querySelector('[data-act="dealgo"]'); if (b) b.disabled = false; }); });
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
export async function openStore(uid, push) {
  let s = state.stores.find((x) => x.uid === uid), preview = false;
  // صاحب المتجر يشوف صفحته حتى لو ما انعتمدت للحين (معاينة)
  if (!s && state.user?.uid === uid) {
    try { const d = await getDoc(doc(db, "stores", uid)); if (d.exists()) { s = { ...d.data(), uid }; preview = !s.approved; } } catch {}
    if (!s) { toast("جهّز صفحة متجرك أول من «حسابي» 🏪"); return; }
  }
  if (!s) { if (state.storesLoaded) { toast("صفحة المتجر غير متاحة"); go("/", true); } return; }
  state.view = { type: "s", id: uid };
  if (push) history.pushState({ v: "s", id: uid }, "", `/s/${encodeURIComponent(uid)}`);
  document.title = `${s.name} | لكوفر`;
  const offers = state.offers.filter((o) => o.merchantId === uid && !X.isExpired(o) && (o.reportsCount || 0) < AUTO_HIDE_REPORTS);
  const del = deliveryText(s), phone = s.phone || (s.branches || [])[0]?.phone || "";
  showSheet(`${s.coverUrl ? `<img class="cover" src="${esc(cld(s.coverUrl, 1000))}" alt="">` : `<div class="cover"></div>`}
    <div class="pad">
      ${preview ? `<div class="hint warn">👀 معاينة: صفحتك بانتظار المراجعة، وما يشوفها الزبائن للحين.</div>` : ""}
      <div class="store-head">${s.logoUrl ? `<img src="${esc(cld(s.logoUrl, 200))}" alt="">` : `<div class="logo-ph">🏪</div>`}<div class="nm">${esc(s.name)}${s.verified ? ' <span class="verified">✔ موثّق</span>' : ""}</div></div>
      ${s.category ? `<div class="pills"><span class="pill">${esc(s.category)}</span></div>` : ""}
      <div id="storeRatings"></div>
      ${s.priceFrom > 0 ? `<div class="box">💰 الأسعار تبدأ من ${money(s.priceFrom)} ر.س</div>` : ""}
      ${del ? `<div class="info-pill">${esc(del)}</div>` : ""}
      ${s.about ? `<p class="desc">${esc(s.about)}</p>` : ""}
      ${phone ? `<div class="actions"><a class="btn wa" href="https://wa.me/${waDigits(phone)}" target="_blank" rel="noopener">💬 واتساب</a><a class="btn call" href="tel:${esc(phone)}">📞 اتصال</a></div>` : ""}
      ${offers.length ? `<div class="section-title">🔥 العروض الحالية (${offers.length})</div><div class="hscroll">${offers.map((o) =>
        `<button class="mini" data-offer="${esc(o.id)}"><img src="${esc(thumb(o))}" alt=""><div>${esc(o.offerTitle)}<small>${esc(priceLabel(o))}</small></div></button>`).join("")}</div>` : ""}
      <div id="storeItems"></div>
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
  body.querySelector('[data-act="share"]').onclick = () => share(`🏪 ${s.name} على لكوفر`, storeUrl(uid), s.name);
  renderStoreItems(s); // المنيو/المنتجات/الخدمات + السلة (لو الخدمة شغّالة)
  renderStoreRatings(uid);
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
  const invite = (cfg.inviteMessage || 'السلام عليكم 👋 شفت محلكم "{name}" على خريطة لكوفر. سجّلوا محلكم مجاناً وانشروا عروضكم لأهل الحي، وخلّوا الزباين يوصلون لكم بسهولة 📍')
    .replace(/\{name\}/g, name || "محلكم") + "\n" + appLink;
  const isMerchant = state.role === "merchant" || (state.user && ADMIN_EMAILS.includes(state.user.email));
  const inviteBtn = mobile
    ? `<a class="btn wa" id="poiInvite" href="https://wa.me/${mobile}?text=${encodeURIComponent(invite)}" target="_blank" rel="noopener">💬 ${mine ? "ذكّره على واتساب المحل" : "ادعه على واتساب المحل"}</a>`
    : `<button class="btn wa" id="poiInvite">📨 ${mine ? "ذكّره مرة ثانية" : "ادعه إلى لكوفر"}</button>`;
  let inviteHtml = "";
  if (cfg.poiInviteEnabled !== false) {
    if (locked && !mine) inviteHtml = `<div class="poi-locked">📨 تمت دعوة هذا المحل من أحد أهل الحي<br><small>ننتظره يسجّل — تقدر تدعوه بعد ${daysLeft(until)} ${daysLeft(until) === 1 ? "يوم" : "أيام"}</small></div>`;
    else if (!state.user) inviteHtml = `<button class="btn wa" id="poiLoginInvite">📨 سجّل دخولك وادعه</button>
      <p class="poi-pts">🎁 الدعوة تنحسب باسمك، ولو سجّل المحل تاخذ لين <b>${POINTS.verified + POINTS.offer} نقطة</b></p>`;
    else inviteHtml = (mine ? `<div class="poi-locked mine">✅ دعيت هذا المحل — ننتظره يسجّل (باقي ${daysLeft(until)} ${daysLeft(until) === 1 ? "يوم" : "أيام"} محجوز لك)</div>` : "")
      + inviteBtn + (mine ? "" : `<p class="poi-pts">🎁 لو سجّل المحل عن طريق دعوتك تاخذ لين <b>${POINTS.verified + POINTS.offer} نقطة</b></p>`);
  }
  const adOffers = state.offers.filter((o) => o.placeId === placeId && o.source === "ad" && !X.isExpired(o));
  showSheet(`<div class="pad poi-card">
    <div class="poi-ico">🏪</div>
    <div class="section-title" style="text-align:center">${esc(shown)}</div>
    <p class="desc tr" style="text-align:center">هذا المحل مو مسجّل في لكوفر للحين</p>
    ${adOffers.length ? `<div class="section-title">📷 من إعلانات المحل (${adOffers.length})</div><div class="hscroll">${adOffers.map((o) =>
      `<button class="mini" data-offer="${esc(o.id)}"><img src="${esc(thumb(o))}" alt=""><div>${esc(o.offerTitle)}<small>${esc(priceLabel(o))}</small></div></button>`).join("")}</div>` : ""}
    ${inviteHtml}
    ${phone && !mobile ? `<a class="btn call" href="tel:${esc(phone.replace(/[^\d+]/g, ""))}">📞 اتصل بالمحل (${esc(phone)})</a>` : ""}
    ${!state.user || isMerchant ? `<button class="btn dir" id="poiClaim">🏪 أنا صاحب المحل — سجّله</button>`
      : `<p class="poi-owner-hint">صاحب المحل؟ سجّل خروج وادخل بحساب <b>صاحب محل</b> عشان تسجّله</p>`}
    <div class="${state.scout ? "row2" : ""}">
      <a class="btn call" href="https://www.google.com/maps/search/?api=1&query=${lat},${lng}&query_place_id=${encodeURIComponent(placeId)}" target="_blank" rel="noopener">فتح في Google Maps</a>
      ${state.scout ? `<button class="btn dir" id="poiAd">📷 ارفع عرض</button>` : ""}
    </div>
  </div>`);
  $("sheetBody").querySelectorAll("[data-offer]").forEach((el) => el.onclick = () => openOffer(el.dataset.offer, true));
  $("poiAd")?.addEventListener("click", () => openAdUpload({ placeId, lat, lng, name, phone }));
  $("poiLoginInvite")?.addEventListener("click", () => { hideSheet(); setTab("account"); openLogin("customer"); });
  $("poiInvite")?.addEventListener("click", () => {
    poiLog(placeId, "invites");
    // نحجز الدعوة باسمه كم يوم عشان ما يتكرر الإزعاج للمحل
    setDoc(doc(db, "poiInvites", placeId), { by: state.user.uid, at: serverTimestamp(), until: Timestamp.fromMillis(Date.now() + INVITE_LOCK_DAYS * 864e5) }).catch(() => {});
    if (!mobile) share(invite, "", "لكوفر");
  });
  $("poiClaim")?.addEventListener("click", () => {
    poiLog(placeId, "claims");
    try { sessionStorage.setItem("hayyna_claim", JSON.stringify({ name, lat, lng, placeId })); } catch {}
    hideSheet();
    if (!state.user) { setTab("account"); openLogin("merchant"); return; }
    setTab("account");
  });
}
// 📷 الفريق يرفع عرض من صورة إعلان على موقع هذا المحل
async function openAdUpload(base) {
  showSheet(`<div class="pad"><div class="spinner"></div></div>`);
  const d = await placeDetails(MAPS_WEB_KEY, base.placeId).catch(() => null);
  const place = { ...base, ...(d || {}), lat: base.lat, lng: base.lng, name: base.name || d?.name || "", phone: base.phone || d?.phone || "" };
  showSheet(`<div class="pad" id="adHost"></div>`);
  mountAdForm($("adHost"), {
    db, fbApp, user: state.user, config: state.config, mapsKey: MAPS_WEB_KEY, categories: categories(), toast, place,
    onDone: (id) => { hideSheet(); setTimeout(() => openOffer(id, true), 900); },
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

// ---------------- الإعدادات: المظهر واللغة والحساب ----------------
export const prefs = {
  get theme() { try { return localStorage.getItem("hayyna_theme") || "light"; } catch { return "light"; } },
  // لغة الخريطة: افتراضياً تتبع لغة الموقع
  get mapLang() { try { return localStorage.getItem("hayyna_mapLang") || "app"; } catch { return "app"; } },
  get lang() { try { return localStorage.getItem("hayyna_lang") || "ar"; } catch { return "ar"; } },
};
const darkMQ = matchMedia("(prefers-color-scheme: dark)");
export const isDark = () => prefs.theme === "dark" || (prefs.theme === "auto" && darkMQ.matches);
export function applyTheme() {
  const dark = isDark();
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", dark ? "#0A1230" : "#10318F");
  map?.setDark?.(dark);
}
darkMQ.addEventListener?.("change", () => { if (prefs.theme === "auto") applyTheme(); });
function openSettings() {
  const seg = (name, cur, opts) => `<div class="seg" data-seg="${name}">${opts.map(([v, l]) => `<button class="${cur === v ? "on" : ""}" data-v="${v}">${l}</button>`).join("")}</div>`;
  showSheet(`<div class="pad settings">
    <div class="section-title">⚙️ الإعدادات</div>
    <div class="set-label">🌓 المظهر</div>
    ${seg("theme", prefs.theme, [["auto", "تلقائي"], ["light", "☀️ فاتح"], ["dark", "🌙 داكن"]])}
    <div class="set-label">🌐 اللغة</div>
    ${seg("lang", prefs.lang, [["ar", "العربية"], ["en", "English"]])}
    <div class="set-label">🗺️ لغة الخريطة</div>
    ${seg("mapLang", prefs.mapLang, [["app", "مثل الموقع"], ["ar", "العربية"], ["en", "English"]])}
    ${state.user ? `<div class="set-label">👤 الحساب</div>
      <button class="btn ghost danger-text" id="setDelete">🗑️ حذف الحساب نهائياً</button>
      <small class="hint">يحذف بياناتك ومفضلتك${state.role === "merchant" ? " وصفحة متجرك وعروضك" : ""}، وما يمكن استرجاعها.</small>` : ""}
    <div class="legal"><a href="/privacy.html">سياسة الخصوصية</a></div>
  </div>`);
  const body = $("sheetBody");
  body.querySelectorAll("[data-seg] button").forEach((b) => b.onclick = () => {
    const name = b.parentElement.dataset.seg, v = b.dataset.v;
    try { localStorage.setItem("hayyna_" + name, v); } catch {}
    b.parentElement.querySelectorAll("button").forEach((x) => x.classList.toggle("on", x === b));
    if (name === "theme") applyTheme();
    if (name === "mapLang") { toast("نطبّق لغة الخريطة…"); setTimeout(() => location.reload(), 350); }
    if (name === "lang") { toast(v === "en" ? "Switching to English…" : "جاري التحويل للعربية…"); setTimeout(() => location.replace("/"), 350); }
  });
  $("setDelete")?.addEventListener("click", () => { hideSheet(); confirmDeleteAccount(); });
}

// ---------------- حسابي (أهل الحي) ----------------
function renderCustomer(el, { name, pr, u, support, foot }) {
  const now = Date.now();
  const live = state.offers.filter((o) => !X.isExpired(o, now));
  const favList = live.filter((o) => state.favs.has(o.id));
  const dist = userDistrict();
  const near = state.me ? live.filter((o) => branchesOf(o).some((b) => km(state.me, { lat: b.latitude, lng: b.longitude }) <= NEAR_KM)).length : null;
  const fresh = live.filter((o) => isFresh(o, now)).length;
  const endingFav = favList.filter((o) => { const e = X.ms(o.expiresAt); return e && e - now < 864e5; }).length;
  const mini = (o) => `<div class="mcard" data-id="${esc(o.id)}" role="button"><div class="mthumb"><img src="${esc(thumb(o, 300))}" alt="" loading="lazy">
      ${badge(o) ? `<span class="badge${badge(o).isNew ? " new" : ""}">${esc(badge(o).t)}</span>` : ""}</div>
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
        <button class="qs" data-go="fresh"><b>${fresh}</b><small>🆕 نزل اليوم</small></button>
      </div>

      <div class="sec-head"><b>مفضلتي ❤️</b>${favList.length ? `<button class="link" data-go="favs">عرض الكل ‹</button>` : ""}</div>
      ${endingFav ? `<div class="hint warn">⏰ ${endingFav === 1 ? "عرض من مفضلتك ينتهي" : endingFav + " عروض من مفضلتك تنتهي"} خلال 24 ساعة — لا يفوتك</div>` : ""}
      ${favList.length ? `<div class="favrow">${favList.slice(0, 10).map(mini).join("")}</div>`
        : `<div class="fav-empty"><span>🤍</span><div><b>مفضلتك فاضية</b><br><small>اضغط القلب على أي عرض يعجبك، ويظهر هنا عشان ترجع له بسرعة</small></div></div>`}

      ${ambassadorCard()}


      <div class="menu">
        ${ordersOn() ? row("myOrders", "🧾", "طلباتي", "طلباتك من المحلات، وتعيدها بضغطة") : ""}
        ${row("settingsRow", "⚙️", "الإعدادات", "المظهر، اللغة، الحساب")}
        ${canOfferInstall() ? row("installAcc", "📲", "ثبّت لكوفر على جوالك", "يفتح بشاشة كاملة مثل التطبيق") : ""}
        ${row("contactBtn", "💬", "تواصل معنا", "اقتراح، مشكلة، أو محل تبيه ينضم")}
        ${row("", "🟢", "واتساب الدعم", esc(support), `https://wa.me/${waDigits(support)}`)}
        ${row("", "🔒", "سياسة الخصوصية", "", "/privacy.html")}
      </div>
    </div>` + foot;
  el.querySelector(".acc-foot .install-row")?.remove(); // موجود في القائمة فوق
  el.querySelector(".acc-foot .legal")?.remove();
  el.querySelectorAll("[data-go]").forEach((b) => b.onclick = () => { const g = b.dataset.go; if (g === "locate") { setTab("map"); locate(true); } else if (g === "fresh") { state.fresh = true; state.scope = "all"; renderAll(); setTab("list"); } else setTab(g); });
  $("contactBtn").onclick = openContact;
  $("settingsRow").onclick = openSettings;
  $("myOrders")?.addEventListener("click", openMyOrders);
  el.querySelector(".acc-foot #settingsBtn")?.remove(); // موجودة في القائمة فوق
  $("inviteFriend").onclick = inviteFriend;
  $("howPts")?.addEventListener("click", howPoints);
}
function ambassadorCard() {
  const refs = state.referrals, pts = pointsOf(refs), t = tierOf(pts);
  const st = (r) => r.offerPts ? ["🔥", "نزّل عروض", "done"] : r.verifiedPts ? ["✅", "انضم وتوثّق", "done"] : ["⏳", "تحت المراجعة", "wait"];
  return `<div class="amb tier-${TIERS.indexOf(TIERS.find((x) => x.name === t.name))}">
    <div class="amb-top"><div class="amb-ico">${t.ico}</div>
      <div class="amb-main"><b>${t.name}</b><small>${refs.length ? `دعيت ${refs.length} ${refs.length === 1 ? "محل" : "محلات"}` : "ادع المحلات اللي تحبها إلى لكوفر"}</small></div>
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
    <p class="muted">النقاط تنحسب بعد مراجعة فريق لكوفر، وقريباً تقدر تستبدلها بخصومات وكوبونات من المحلات 🎁</p>
    <button class="btn primary" id="howOk">تمام</button></div>`);
  $("howOk").onclick = hideSheet;
}
async function inviteFriend() {
  const link = withRef(state.config.appLink || location.origin);
  const text = `شف لكوفر 📍 تطبيق يعرض لك عروض وخصومات المحلات اللي حولك على الخريطة — مطاعم، مقاهي، ملابس وأكثر. جرّبه:`;
  if (navigator.share) { try { await navigator.share({ title: "لكوفر", text, url: link }); return; } catch (e) { if (e?.name === "AbortError") return; } }
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
    el.innerHTML = `<div class="acc-hero"><img src="/img/icon-192.png" alt=""><h1>أهلاً بك في لكوفر</h1><p>سجّل دخولك عشان تحفظ مفضلتك، أو تنشر عروض محلك</p></div>
      <div class="pad">
        <button class="btn dir big login-main" data-login="customer">تسجيل الدخول</button>
        <small class="hint" style="text-align:center;display:block">احفظ المفضلة، واطلب من المحلات، وتابع طلباتك</small>
        <div class="biz-sec"><div class="biz-t">لأصحاب المحلات وموظفيها</div>
          <button class="role-card small" data-login="merchant"><div class="rc-ico">🏪</div><div><b>أنا صاحب محل</b><br><small>سجّل محلك وانشر عروضك</small></div></button>
          ${(state.config?.catalog?.features || {}).staff !== false ? `<button class="role-card small" id="staffLogin"><div class="rc-ico">👨‍🍳</div><div><b>دخول موظف</b><br><small>برقم المتجر ورقمك ورمزك السري</small></div></button>` : ""}
        </div>
        <button class="btn call" id="contactBtn">💬 تواصل معنا</button>
        <button class="btn call" id="settingsBtn">⚙️ الإعدادات</button>
        ${canOfferInstall() ? `<button class="btn install-row" id="installAcc">📲 ثبّت لكوفر على جوالك</button>` : ""}
        <div class="legal"><a href="/privacy.html">سياسة الخصوصية</a></div>
      </div>`;
    { const a = annAccountHtml(); if (a) el.querySelector(".acc-hero")?.insertAdjacentHTML("afterend", `<div class="pad" style="padding-bottom:0">${a}</div>`); bindAnn(el); }
    el.querySelectorAll("[data-login]").forEach((b) => b.onclick = () => openLogin(b.dataset.login));
    $("staffLogin")?.addEventListener("click", async () => (await import("./staff.js")).openStaffLogin());
    $("contactBtn").onclick = openContact;
    $("settingsBtn").onclick = openSettings;
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
  const foot = `<div class="pad acc-foot">${canOfferInstall() ? `<button class="btn install-row" id="installAcc">📲 ثبّت لكوفر على جوالك</button>` : ""}<button class="btn call" id="settingsBtn">⚙️ الإعدادات</button><button class="btn ghost" id="logoutBtn">تسجيل الخروج</button><div class="legal"><a href="/privacy.html">سياسة الخصوصية</a></div></div>`;
  if (state.role === "merchant" || isAdmin) {
    el.innerHTML = head + (annAccountHtml() ? `<div class="pad" style="padding-bottom:0">${annAccountHtml()}</div>` : "") + `<div id="merchantRoot" class="pad"><div class="spinner"></div></div>` + foot;
    import("./merchant.js").then((m) => m.renderMerchant($("merchantRoot"), { isAdmin, support, logout: doLogout, contact: openContact }));
  } else {
    renderCustomer(el, { name, pr, u, support, foot });
  }
  if (state.role !== "merchant" && !isAdmin) { const a = annAccountHtml(); if (a) $("account").querySelector(".acc-user")?.insertAdjacentHTML("afterend", `<div class="pad" style="padding-bottom:0">${a}</div>`); }
  bindAnn($("account"));
  $("editProfile").onclick = () => openProfileEdit();
  $("logoutBtn").onclick = doLogout;
  if ($("installAcc")) $("installAcc").onclick = doInstall;
  if ($("settingsBtn")) $("settingsBtn").onclick = openSettings;
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

function confirmDeleteAccount() {
  showSheet(`<div class="pad"><div class="section-title">حذف الحساب؟</div>
    <p class="desc">بننحذف بياناتك (الاسم، الجوال، المفضلة) وتسجيل دخولك. ${state.role === "merchant" ? "وتنحذف معها كل عروضك وصفحة متجرك وإحصائياتها، وما تقدر تسترجعها." : ""}</p>
    <div class="row2"><button class="btn call" id="daNo">إلغاء</button><button class="btn danger" id="daYes">احذف حسابي</button></div></div>`);
  $("daNo").onclick = hideSheet;
  $("daYes").onclick = async () => {
    const u = auth.currentUser; if (!u) return;
    try {
      if (state.role === "merchant") {
        // نفس التطبيق: نحذف عروض التاجر وصفحة متجره وملفه مباشرة
        const mine = await getDocs(query(collection(db, "offers"), where("merchantId", "==", u.uid))).catch(() => null);
        for (const d of mine?.docs || []) await deleteDoc(d.ref).catch(() => {});
        await deleteDoc(doc(db, "stores", u.uid)).catch(() => {});
        await deleteDoc(doc(db, "merchants", u.uid)).catch(() => {});
      }
      await deleteDoc(doc(db, "users", u.uid)).catch(() => {});
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
  showSheet(`<div class="pad"><div class="section-title">${role === "merchant" ? "🏪 دخول صاحب المحل" : "👋 تسجيل الدخول"}</div>
    ${role === "merchant" ? `<div class="hint warn">هذا لأصحاب المحلات بس. تبي تتصفح العروض وتطلب؟ <button class="link" id="lgAsCust">ادخل كزبون</button></div>` : ""}
    <button class="btn login google" id="lgGoogle"><img src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg" alt=""> المتابعة بحساب Google</button>
    ${APPLE_ENABLED ? `<button class="btn login apple" id="lgApple"> المتابعة بحساب Apple</button>` : ""}
    <div class="or"><span>أو بالإيميل</span></div>
    <input id="lgEmail" class="input" type="email" dir="ltr" placeholder="name@email.com" autocomplete="email">
    <button class="btn call" id="lgEmailBtn">✉️ أرسل لي رابط الدخول</button>
    <small class="hint">بنرسل لك رابط على إيميلك، تضغطه وتدخل مباشرة بدون كلمة سر.</small></div>`);
  $("lgGoogle").onclick = () => loginWith(new GoogleAuthProvider());
  $("lgAsCust")?.addEventListener("click", () => openLogin("customer"));
  if (APPLE_ENABLED) $("lgApple").onclick = () => {
    const p = new OAuthProvider("apple.com"); p.addScope("email"); p.addScope("name"); p.setCustomParameters({ locale: auth.languageCode }); loginWith(p);
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
export function setTab(t, fromPop) {
  if (!fromPop && t !== state.tab) {
    if (state.tab === "map") history.pushState({ tab: t }, "", "/");
    else if (t === "map" && history.state?.tab) popSilently();
    else if (history.state?.tab) history.replaceState({ tab: t }, "", "/");
  }
  state.tab = t;
  document.querySelectorAll(".tab").forEach((s) => s.classList.toggle("on", s.id === "tab-" + t));
  document.querySelectorAll("#nav button").forEach((b) => b.classList.toggle("on", b.dataset.tab === t));
  if (t === "map") { setTimeout(() => map?.resize(), 50); setTimeout(() => state.loaded && maybeSpotlight(filtered()), 400); } // بطاقة "قريب منك" لما يرجع للخريطة
  if (t === "account") renderAccount();
  try { sessionStorage.setItem("hayyna_tab", t); } catch {}
  window.dispatchEvent(new Event("tabchange"));
}
$("nav").onclick = (e) => { const b = e.target.closest("[data-tab]"); if (b) { hideSheet(); if (location.pathname !== "/") history.replaceState({ tab: b.dataset.tab }, "", "/"); setTab(b.dataset.tab); } };

function route() {
  const m = location.pathname.match(/^\/(o|s)\/([^/]+)/);
  if (!m) { hideSheet(); document.title = "لكوفر — عروض المحلات حولك على الخريطة"; return; }
  const id = decodeURIComponent(m[2]);
  if (m[1] === "o") {
    setTab("map"); openOffer(id, false);
    const o = state.offers.find((x) => x.id === id);
    if (o && map) { const { b } = nearestBranch(o); map.setView(b.latitude, b.longitude, 15); }
  } else openStore(id, false);
}
export function go(path, replace) { (replace ? history.replaceState : history.pushState).call(history, {}, "", path); route(); }
let exitArmed = false;
window.addEventListener("popstate", () => {
  if (ignorePop) { ignorePop--; return; }
  $("viewer").classList.add("hidden");
  if (!$("page").classList.contains("hidden")) { closePage(true); return; }
  if (/^\/(o|s)\//.test(location.pathname)) { route(); return; }
  if (!$("sheet").classList.contains("hidden")) { hideSheet(true); document.title = "لكوفر — عروض المحلات حولك على الخريطة"; return; }
  if (state.tab !== "map") { setTab("map", true); return; }
  // على الخريطة وما فيه شي مفتوح: رجوع مرة ثانية يطلع
  if (exitArmed) return;
  exitArmed = true;
  toast("اضغط رجوع مرة ثانية للخروج من لكوفر");
  setTimeout(() => { exitArmed = false; history.pushState({ guard: 1 }, "", location.pathname); }, 2500);
});
const closeSheet = () => { if (location.pathname !== "/") { history.state?.v ? history.back() : go("/", true); } else hideSheet(); };
$("sheetClose").onclick = closeSheet;
$("sheetBackdrop").onclick = closeSheet;
$("viewerClose").onclick = () => $("viewer").classList.add("hidden");
$("viewer").onclick = (e) => { if (e.target.id === "viewer") $("viewer").classList.add("hidden"); };

// سحب الشرائط الأفقية بالماوس (الكمبيوتر) — اللمس يشتغل لحاله
(() => {
  const SEL = ".carousel, .chips, .sortbar, .favrow, .hscroll, .row-chips";
  let drag = null;
  document.addEventListener("pointerdown", (e) => {
    if (e.pointerType !== "mouse" || e.button !== 0) return;
    const el = e.target.closest(SEL); if (!el || el.scrollWidth <= el.clientWidth) return;
    drag = { el, x: e.clientX, left: el.scrollLeft, moved: false };
  });
  document.addEventListener("pointermove", (e) => {
    if (!drag) return;
    const dx = e.clientX - drag.x;
    if (!drag.moved && Math.abs(dx) > 6) { drag.moved = true; drag.el.classList.add("dragging"); }
    if (drag.moved) { drag.el.scrollLeft = drag.left - dx; e.preventDefault(); }
  });
  const end = () => { if (!drag) return; const d = drag; drag = null; d.el.classList.remove("dragging");
    if (d.moved) { const stop = (ev) => { ev.stopPropagation(); ev.preventDefault(); }; window.addEventListener("click", stop, { capture: true, once: true }); setTimeout(() => window.removeEventListener("click", stop, { capture: true }), 50); } };
  document.addEventListener("pointerup", end); document.addEventListener("pointercancel", end);
  // عجلة الماوس على الشريط = تحريك أفقي
  document.addEventListener("wheel", (e) => {
    const el = e.target.closest(".carousel, .favrow, .hscroll"); if (!el || Math.abs(e.deltaX) > Math.abs(e.deltaY) || el.scrollWidth <= el.clientWidth) return;
    el.scrollBy({ left: e.deltaY, behavior: "auto" }); e.preventDefault();
  }, { passive: false });
})();
// أسهم للشريط السفلي على الكمبيوتر
$("carNext")?.addEventListener("click", () => $("carousel").scrollBy({ left: -340, behavior: "smooth" }));
$("carPrev")?.addEventListener("click", () => $("carousel").scrollBy({ left: 340, behavior: "smooth" }));

// أحداث البطاقات (خريطة + قائمة + مفضلة + لوحة)
document.addEventListener("click", (e) => {
  const h = e.target.closest("[data-fav]"); if (h) { e.stopPropagation(); toggleFav(h.dataset.fav); return; }
  const s = e.target.closest("#sortbar [data-sort]"); if (s) { state.sort = s.dataset.sort; renderAll(); return; }
  const op = e.target.closest("#sortbar [data-open]"); if (op) { state.openOnly = !state.openOnly; renderAll(); return; }
  const fr = e.target.closest("#sortbar [data-fresh]"); if (fr) { state.fresh = !state.fresh; renderAll(); return; }
  const kd = e.target.closest("#sortbar [data-kind]"); if (kd) { state.kind = state.kind === "new" ? "all" : "new"; renderAll(); return; }
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
$("q").oninput = (e) => setQuery(e.target.value, "q");
$("clearQ").onclick = () => { setQuery(""); };
// البحث مشترك بين الخريطة وقائمة العروض
function setQuery(v, from) {
  state.q = v;
  if (from !== "q") $("q").value = v;
  if (from !== "lq") $("lq").value = v;
  $("clearQ").classList.toggle("hidden", !v); $("lqClear").classList.toggle("hidden", !v);
  clearTimeout(qt); qt = setTimeout(() => { fitted = false; renderAll(); }, from ? 200 : 0);
}
$("lq").oninput = (e) => setQuery(e.target.value, "lq");
$("lqClear").onclick = () => setQuery("");
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
    ? ["افتح <b>الإعدادات</b> في الآيفون ← <b>الخصوصية والأمان</b> ← <b>خدمات الموقع</b> وتأكد إنها شغّالة", `انزل لـ <b>${standalone ? "سفاري / مواقع سفاري" : "سفاري"}</b> واختر <b>«أثناء الاستخدام»</b> أو «اسأل»`, "ارجع إلى لكوفر واضغط زر موقعي مرة ثانية"]
    : standalone
      ? ["اضغط مطوّلاً على أيقونة <b>لكوفر</b> ← <b>معلومات التطبيق</b>", "<b>الأذونات</b> ← <b>الموقع</b> ← <b>السماح أثناء الاستخدام</b>", "إذا ما لقيتها: افتح كروم ← ⋮ ← الإعدادات ← إعدادات المواقع ← الموقع، وشيل لكوفر من «محظور»"]
      : ["اضغط أيقونة <b>القفل / الإعدادات</b> جنب الرابط فوق", "اختر <b>الأذونات</b> ← <b>الموقع</b> ← <b>سماح</b>", "حدّث الصفحة واضغط زر موقعي"];
  showSheet(`<div class="pad install-sheet"><div style="font-size:44px">📍</div><h2>الموقع مقفول على لكوفر</h2>
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

// ---------------- ثبّت لكوفر (أندرويد + آيفون) ----------------
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
    ? [`اضغط زر المشاركة <span class="share-ico">⬆︎</span> ${inSafari ? "تحت في الشريط" : "جنب الرابط فوق"}`, "انزل واختر <b>«إضافة إلى الشاشة الرئيسية»</b>", "اضغط <b>«إضافة»</b>، وافتح لكوفر من أيقونته"]
    : ["اضغط قائمة المتصفح <b>⋮</b> فوق", "اختر <b>«تثبيت التطبيق»</b> أو <b>«إضافة إلى الشاشة الرئيسية»</b>", "افتح لكوفر من أيقونته"];
  showSheet(`<div class="pad install-sheet"><img src="/img/icon-192.png" alt=""><h2>ثبّت لكوفر على جوالك 📲</h2>
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
    if (r?.outcome === "accepted") toast("تم التثبيت 🎉 افتح لكوفر من أيقونته");
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
  // 👨‍🍳 حساب موظف (رقم + رمز سري): شاشة الطلبات بس
  if (u && String(u.email || "").endsWith("@staff.lacover.app")) { state.role = "staff"; (await import("./staff.js")).enterStaffMode(u); return; }
  if (document.body.classList.contains("staff-mode")) (await import("./staff.js")).leaveStaffMode();
  state.scout = false;
  if (!u) { state.role = null; state.profile = null; renderAll(); return; }
  canScout(db, u, ADMIN_EMAILS).then((v) => { state.scout = v; }); // فريق الإعلانات: يرفع عروض لمحلات غير مسجّلة
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

// رابط QR دخول الموظف: /?staff=48213-2 (الأدمن يقدر يوقفه)
let staffLinkDone = false;
function staffLink() {
  if (staffLinkDone) return; staffLinkDone = true;
  let v = null; try { const u = new URL(location.href); v = u.searchParams.get("staff"); if (v) { u.searchParams.delete("staff"); history.replaceState(history.state, "", u.pathname + (u.search || "") + u.hash); } } catch {}
  if (!v || !/^[1-9][0-9]{4}-[0-9]{1,2}$/.test(v)) return;
  const f = state.config?.catalog?.features || {};
  if (f.staff === false || f.staffQr === false) return toast("دخول الموظفين بالـ QR موقف حالياً");
  if (state.user) return toast("أنت مسجّل دخول — سجّل خروج أول عشان تدخل كموظف");
  import("./staff.js").then((m) => { setTab("account"); m.openStaffLogin(v); });
}

// ---------------- البيانات ----------------
watchAnnouncements(() => { renderAnnStrip(); if (state.tab === "account") renderAccount(); });
onSnapshot(doc(db, "config", "app"), (s) => { state.config = s.data() || {}; document.body.classList.toggle("hideAdFar", state.config.hideAdFar === true); staffLink(); renderChips(); map?.setShowShops(state.config.showGooglePoi !== false); if (state.loaded) renderAll(); }, () => {});
onSnapshot(query(collection(db, "boosts"), where("endsAt", ">", Timestamp.now())), (snap) => {
  state.boosts = snap.docs.map((d) => ({ ...d.data(), id: d.id })); if (state.loaded) renderAll();
}, () => {});
onSnapshot(query(collection(db, "offers"), where("isApproved", "==", true)), (snap) => {
  state.offers = snap.docs.map((d) => ({ ...d.data(), id: d.id })).filter((o) => !o.draft); // المسودات ما تظهر للناس
  const first = !state.loaded; state.loaded = true;
  checkRestock(); // 🔔 عرض كان نافد ورجع؟
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
  setTimeout(() => m?.setDark?.(isDark()), 0);
  map = m; if (state.me) map.setMe(state.me.lat, state.me.lng);
  map.setShowShops(state.config.showGooglePoi !== false);
  renderAll();
  if (state.view?.type === "o") { const o = state.offers.find((x) => x.id === state.view.id); if (o) { const { b } = nearestBranch(o); map.setView(b.latitude, b.longitude, 15); } }
}).catch((e) => { console.error(e); toast("تعذّر تحميل الخريطة"); });

// سجل الرجوع: نقطة بداية + حارس، عشان أول رجوع ما يطلّعك من الموقع
(() => {
  const here = location.pathname + location.search + location.hash;
  history.replaceState({ root: 1 }, "", "/");
  history.pushState({ guard: 1 }, "", "/");
  // ملاحظة: نفحص "here" (المسار الأصلي)، لأن location صار "/" بعد السطر فوق — كان يكسر روابط /o و /s المباشرة
  if (here !== "/" && /^\/(o|s)\//.test(here)) history.pushState({ v: 1 }, "", here);
})();
try { const t = sessionStorage.getItem("hayyna_tab"); if (t && !location.pathname.match(/^\/(o|s)\//)) setTab(t); } catch {}

applyTheme();
