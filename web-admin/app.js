// لوحة إدارة حيّنا على الويب — نفس قواعد Firestore تحميها (الأدمن فقط يقدر يقرأ ويعدّل).
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, signInWithPopup, onAuthStateChanged, signOut
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  getFirestore, collection, doc, onSnapshot, query, where, orderBy, limit,
  updateDoc, deleteDoc, setDoc, getDocs, getDoc, serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig, FIRESTORE_DB_ID, ADMIN_EMAILS, AUTO_HIDE_REPORTS } from "./config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, FIRESTORE_DB_ID);

// ---------------- الحالة ----------------
const state = {
  tab: localStorage.getItem("hayyna_admin_tab") || "home",
  offers: [], reports: [], statuses: [], pendingStores: [], feedback: [], config: {}, poi: [],
  inboxType: "الكل", showDone: false, offerQuery: "", offerFilter: "الكل", drafts: {},
};
const unsubs = [];
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const img = (url, w = 300) => !url ? "" : url.includes("res.cloudinary.com") && url.includes("/upload/")
  ? url.replace("/upload/", `/upload/f_auto,q_auto,c_limit,w_${w}/`) : url;
const thumbOf = (o) => {
  const u = (o.mediaUris && o.mediaUris[0]) || o.mediaUri || "";
  if (o.mediaType === "VIDEO" && u.includes("/video/upload/")) {
    return u.replace("/upload/", `/upload/so_1,f_jpg,q_auto,c_limit,w_300/`).replace(/\.[^./]+$/, ".jpg");
  }
  return img(u);
};
const isExpired = (o) => o.expiresAt && o.expiresAt.toMillis && o.expiresAt.toMillis() < Date.now();
const toast = (msg) => {
  const t = $("toast"); t.textContent = msg; t.classList.remove("hidden");
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.add("hidden"), 2800);
};
const run = async (fn, okMsg) => {
  try { await fn(); if (okMsg) toast(okMsg); } catch (e) { console.error(e); toast("صار خطأ: " + (e.message || e)); }
};

// ---------------- الدخول ----------------
$("loginBtn").onclick = () => run(() => signInWithPopup(auth, new GoogleAuthProvider()));

onAuthStateChanged(auth, (user) => {
  unsubs.splice(0).forEach((u) => u());
  if (!user) {
    $("login").classList.remove("hidden"); $("app").classList.add("hidden"); $("user").innerHTML = "";
    return;
  }
  if (!ADMIN_EMAILS.includes(user.email)) {
    $("loginError").textContent = "هذا الحساب مو حساب أدمن.";
    signOut(auth);
    return;
  }
  $("login").classList.add("hidden"); $("app").classList.remove("hidden");
  $("user").innerHTML = `<span>${esc(user.email)}</span><button class="btn" id="logout">خروج</button>`;
  $("logout").onclick = () => signOut(auth);
  listen();
  render();
});

// ---------------- الاستماع للبيانات ----------------
function listen() {
  const parse = (snap) => snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  unsubs.push(onSnapshot(collection(db, "offers"), (s) => { state.offers = parse(s); render(); }, console.error));
  unsubs.push(onSnapshot(query(collection(db, "reports"), orderBy("createdAt", "desc"), limit(200)), (s) => { state.reports = parse(s); render(); }, console.error));
  unsubs.push(onSnapshot(collection(db, "merchantStatus"), (s) => { state.statuses = parse(s).map((x) => ({ ...x, uid: x.id })); render(); }, console.error));
  unsubs.push(onSnapshot(query(collection(db, "stores"), where("approved", "==", false)), (s) => { state.pendingStores = parse(s).map((x) => ({ ...x, uid: x.id })); render(); }, console.error));
  unsubs.push(onSnapshot(query(collection(db, "feedback"), orderBy("createdAt", "desc"), limit(300)), (s) => { state.feedback = parse(s); render(); }, console.error));
  unsubs.push(onSnapshot(query(collection(db, "poiInterest"), orderBy("taps", "desc"), limit(50)), (s) => { state.poi = parse(s); render(); }, console.error));
  unsubs.push(onSnapshot(doc(db, "config", "app"), (s) => { state.config = s.data() || {}; render(); }, console.error));
}

// ---------------- أوامر الإدارة (نفس منطق التطبيق) ----------------
const statusOf = (uid) => state.statuses.find((s) => s.uid === uid);
const setStatus = (uid, data) => setDoc(doc(db, "merchantStatus", uid), { ...data, updatedAt: serverTimestamp() });
async function setStoreApproved(uid, approved) {
  const ref = doc(db, "stores", uid);
  if ((await getDoc(ref)).exists()) await updateDoc(ref, { approved });
}
const actions = {
  async approve(o) {
    await updateDoc(doc(db, "offers", o.id), { isApproved: true });
    const count = (await getDocs(query(collection(db, "offers"), where("merchantId", "==", o.merchantId), where("isApproved", "==", true)))).size;
    const cur = statusOf(o.merchantId);
    if (count >= 2 && !cur?.trusted && !cur?.suspended) {
      await setStatus(o.merchantId, { trusted: true, suspended: false, name: o.restaurantName });
      await setStoreApproved(o.merchantId, true);
      toast(`تم الاعتماد ✅ وصار ${o.restaurantName} موثوق`);
    } else toast("تم اعتماد العرض ✅");
  },
  async trust(o) {
    await setStatus(o.merchantId, { trusted: true, suspended: false, name: o.restaurantName });
    await updateDoc(doc(db, "offers", o.id), { isApproved: true });
    await setStoreApproved(o.merchantId, true);
    toast(`صار ${o.restaurantName} موثوق ✅`);
  },
  async suspend(uid, name) {
    if (!confirm(`إيقاف ${name}؟ بتختفي كل عروضه وما يقدر ينشر.`)) return;
    await setStatus(uid, { trusted: false, suspended: true, name });
    const docs = (await getDocs(query(collection(db, "offers"), where("merchantId", "==", uid)))).docs;
    for (const d of docs) await updateDoc(d.ref, { isApproved: false }).catch(() => {});
    await setStoreApproved(uid, false).catch(() => {});
    toast(`تم إيقاف ${name}`);
  },
  unsuspend: (s) => setStatus(s.uid, { trusted: false, suspended: false, name: s.name || "" }),
  untrust: (s) => setStatus(s.uid, { trusted: false, suspended: false, name: s.name || "" }),
  async keep(o) {
    await updateDoc(doc(db, "offers", o.id), { reportsCount: 0 });
    for (const r of state.reports.filter((r) => r.offerId === o.id)) await deleteDoc(doc(db, "reports", r.id));
  },
  async deleteOffer(id) { if (confirm("حذف العرض نهائياً؟")) await deleteDoc(doc(db, "offers", id)); },
  dismissReport: (id) => deleteDoc(doc(db, "reports", id)),
  approveStore: (uid) => updateDoc(doc(db, "stores", uid), { approved: true }),
  feature: (o) => updateDoc(doc(db, "offers", o.id), { isFeatured: !o.isFeatured }),
  toggleApproved: (o) => updateDoc(doc(db, "offers", o.id), { isApproved: !o.isApproved }),
  reply: (id, reply) => updateDoc(doc(db, "feedback", id), { reply, status: "done", replySeen: !reply }),
  fbStatus: (id, status) => updateDoc(doc(db, "feedback", id), { status }),
  async deleteFb(id) { if (confirm("حذف الرسالة؟")) await deleteDoc(doc(db, "feedback", id)); },
  saveConfig: (data) => setDoc(doc(db, "config", "app"), data, { merge: true }),
};

// ---------------- الواجهة ----------------
function computed() {
  const suspendedIds = new Set(state.statuses.filter((s) => s.suspended).map((s) => s.uid));
  const pending = state.offers.filter((o) => !o.isApproved && !suspendedIds.has(o.merchantId));
  const hidden = state.offers.filter((o) => o.isApproved && (o.reportsCount || 0) >= AUTO_HIDE_REPORTS);
  const hiddenIds = new Set(hidden.map((o) => o.id));
  const otherReports = {};
  state.reports.filter((r) => !hiddenIds.has(r.offerId)).forEach((r) => (otherReports[r.offerId] ||= []).push(r));
  const pendingStores = state.pendingStores.filter((s) => !suspendedIds.has(s.uid));
  const newMsgs = state.feedback.filter((f) => (f.status || "new") === "new").length;
  return { pending, hidden, otherReports, pendingStores, newMsgs };
}

const TABS = [
  ["home", "🏠 الرئيسية"], ["review", "⏳ المراجعة"], ["stores", "🏪 المتاجر"],
  ["messages", "📨 الرسائل"], ["offers", "📋 كل العروض"], ["settings", "⚙️ الإعدادات"],
];

function render() {
  if ($("app").classList.contains("hidden")) return;
  const c = computed();
  const badges = { review: c.pending.length + c.hidden.length, stores: c.pendingStores.length, messages: c.newMsgs };
  $("tabs").innerHTML = TABS.map(([k, t]) =>
    `<button class="tab ${state.tab === k ? "active" : ""}" data-tab="${k}">${t}${badges[k] ? `<span class="badge">${badges[k]}</span>` : ""}</button>`
  ).join("");
  $("tabs").querySelectorAll(".tab").forEach((b) => (b.onclick = () => { state.tab = b.dataset.tab; localStorage.setItem("hayyna_admin_tab", state.tab); render(); }));
  const focused = document.activeElement?.id;
  const caret = document.activeElement?.selectionStart;
  $("content").innerHTML = ({ home, review, stores, messages, offers, settings }[state.tab] || home)(c);
  bind();
  if (focused && $(focused)) { $(focused).focus(); try { $(focused).setSelectionRange(caret, caret); } catch {} }
}

function offerCard(o, extra = "", buttons = "") {
  return `<div class="card">
    <div class="row">
      <img class="thumb" src="${esc(thumbOf(o))}" data-view="${esc((o.mediaUris && o.mediaUris[0]) || o.mediaUri)}" alt="">
      <div style="flex:1;min-width:0">
        <div class="name">${esc(o.restaurantName)}</div>
        <div><b>${esc(o.offerTitle)}</b></div>
        <div class="muted">${esc(o.price)} ر.س ${o.originalPrice ? `<s>${esc(o.originalPrice)}</s>` : ""} • ${esc(o.district)} • 👁 ${o.viewsCount || 0}</div>
        ${o.description ? `<div class="muted">${esc(o.description).slice(0, 200)}</div>` : ""}
        ${(o.mediaUris || []).length > 1 ? `<div class="actions">${o.mediaUris.slice(1).map((u) => `<img class="thumb small" src="${esc(img(u, 150))}" data-view="${esc(u)}">`).join("")}</div>` : ""}
        ${extra}
      </div>
    </div>
    <div class="actions">${buttons}</div>
  </div>`;
}

function home(c) {
  const todo = [
    ["⏳ عروض تنتظر المراجعة", c.pending.length, "review"],
    ["🚩 عروض مخفية بسبب البلاغات", c.hidden.length, "review"],
    ["🏪 صفحات متاجر تنتظر الموافقة", c.pendingStores.length, "stores"],
    ["📨 رسائل جديدة", c.newMsgs, "messages"],
  ].filter((t) => t[1] > 0);
  const active = state.offers.filter((o) => o.isApproved && !isExpired(o)).length;
  const stat = (t, v) => `<div class="card stat"><div class="v">${v}</div><div class="t">${t}</div></div>`;
  return `<h2>يحتاج انتباهك</h2>
    ${todo.length ? `<div class="grid">${todo.map(([l, n, t]) => `<button class="card todo" data-go="${t}">${l}<b>${n} ›</b></button>`).join("")}</div>`
      : `<p class="muted">كل شي تمام، ما فيه شي ينتظرك ✅</p>`}
    <h2>إحصائيات</h2>
    <div class="stats">
      ${stat("المتاجر", new Set(state.offers.map((o) => o.merchantId)).size)}
      ${stat("عروض نشطة", active)}
      ${stat("كل العروض", state.offers.length)}
      ${stat("الأحياء", new Set(state.offers.map((o) => o.district).filter(Boolean)).size)}
      ${stat("المشاهدات", state.offers.reduce((a, o) => a + (o.viewsCount || 0), 0))}
      ${stat("طلبوا الاتجاه", state.offers.reduce((a, o) => a + (o.directionsCount || 0), 0))}
      ${stat("تواصلوا واتساب", state.offers.reduce((a, o) => a + (o.whatsappCount || 0), 0))}
      ${stat("موثوقين", state.statuses.filter((s) => s.trusted && !s.suspended).length)}
    </div>`;
}

function review(c) {
  const reasons = (id) => state.reports.filter((r) => r.offerId === id).map((r) => esc(r.reason)).join("، ");
  return `<h2>${c.pending.length ? `⏳ عروض جديدة تنتظر المراجعة (${c.pending.length})` : "عروض جديدة: لا يوجد ✅"}</h2>
    <div class="grid">${c.pending.map((o) => offerCard(o, "",
      `<button class="btn primary" data-act="approve" data-id="${o.id}">اعتماد ✓</button>
       <button class="btn" data-act="trust" data-id="${o.id}">اعتماد + توثيق التاجر</button>
       <button class="btn danger" data-act="deleteOffer" data-id="${o.id}">حذف</button>
       <button class="btn danger" data-act="suspendOffer" data-id="${o.id}">⛔ إيقاف التاجر</button>`)).join("")}</div>
    <h2>${c.hidden.length ? `🚩 مخفية بسبب البلاغات (${c.hidden.length})` : "مخفية بسبب البلاغات: لا يوجد ✅"}</h2>
    <div class="grid">${c.hidden.map((o) => offerCard(o, `<div class="note">${o.reportsCount} بلاغات: ${reasons(o.id)}</div>`,
      `<button class="btn primary" data-act="keep" data-id="${o.id}">سليم، رجّعه</button>
       <button class="btn danger" data-act="deleteOffer" data-id="${o.id}">حذف</button>
       <button class="btn danger" data-act="suspendOffer" data-id="${o.id}">⛔ إيقاف التاجر</button>`)).join("")}</div>
    ${Object.keys(c.otherReports).length ? `<h2>🚩 بلاغات أخرى</h2><div class="grid">${Object.entries(c.otherReports).map(([id, list]) => `
      <div class="card"><b>${esc(list[0].offerTitle)}</b><div class="muted">${esc(list[0].restaurantName)} • ${list.length} بلاغ</div>
        ${list.slice(0, 5).map((r) => `<div class="note">• ${esc(r.reason)}</div>`).join("")}
        <div class="actions"><button class="btn" data-act="dismissAll" data-id="${id}">تجاهل البلاغات</button>
        <button class="btn danger" data-act="deleteAndDismiss" data-id="${id}">احذف العرض</button></div></div>`).join("")}</div>` : ""}`;
}

function stores(c) {
  const trusted = state.statuses.filter((s) => s.trusted && !s.suspended);
  const suspended = state.statuses.filter((s) => s.suspended);
  return `<h2>${c.pendingStores.length ? `🏪 صفحات تنتظر الموافقة (${c.pendingStores.length})` : "صفحات تنتظر الموافقة: لا يوجد ✅"}</h2>
    <div class="grid">${c.pendingStores.map((s) => `<div class="card">
      <div class="row">${s.logoUrl ? `<img class="thumb small" src="${esc(img(s.logoUrl, 150))}" style="border-radius:50%">` : ""}
        <div><div class="name">${esc(s.name)}</div><div class="muted">${esc(s.category)}${s.priceFrom ? ` • تبدأ من ${s.priceFrom} ر.س` : ""}</div></div></div>
      ${s.about ? `<p class="muted">${esc(s.about)}</p>` : ""}
      <div class="actions">${[s.coverUrl, ...(s.menuImages || [])].filter(Boolean).map((u) => `<img class="thumb small" src="${esc(img(u, 200))}" data-view="${esc(u)}">`).join("")}</div>
      <div class="actions"><button class="btn primary" data-act="approveStore" data-id="${s.uid}">اعتماد الصفحة ✓</button>
        <button class="btn danger" data-act="suspendUid" data-id="${s.uid}" data-name="${esc(s.name)}">⛔ إيقاف التاجر</button></div></div>`).join("")}</div>
    <h2>⭐ متاجر موثوقة (${trusted.length})</h2>
    ${trusted.length ? `<div class="grid">${trusted.map((s) => `<div class="card row" style="align-items:center;justify-content:space-between">
      <b>${esc(s.name || s.uid.slice(0, 8))}</b><span><button class="btn" data-act="untrust" data-id="${s.uid}">إلغاء التوثيق</button>
      <button class="btn danger" data-act="suspendUid" data-id="${s.uid}" data-name="${esc(s.name)}">إيقاف</button></span></div>`).join("")}</div>` : `<p class="muted">ما فيه متاجر موثوقة للحين</p>`}
    <h2>📍 محلات غير مسجّلة يطلبها العملاء</h2>
    <p class="muted">من نقاط خرائط Google: 👆 ضغطات • 📣 دعوات • 🏪 "أنا صاحب المحل"</p>
    ${state.poi.length ? `<div class="grid">${state.poi.map((p) => `<a class="card row" style="justify-content:space-between;text-decoration:none;color:inherit" target="_blank" rel="noopener"
       href="https://www.google.com/maps/search/?api=1&query=place&query_place_id=${encodeURIComponent(p.id)}">
       <b>👆 ${p.taps || 0} &nbsp; 📣 ${p.invites || 0} &nbsp; 🏪 ${p.claims || 0}</b><span class="name">افتح في الخرائط ›</span></a>`).join("")}</div>` : `<p class="muted">ما فيه بيانات للحين</p>`}
    <h2>⛔ حسابات موقوفة (${suspended.length})</h2>
    ${suspended.length ? `<div class="grid">${suspended.map((s) => `<div class="card row" style="align-items:center;justify-content:space-between">
      <b>${esc(s.name || s.uid.slice(0, 8))}</b><button class="btn" data-act="unsuspend" data-id="${s.uid}">إلغاء الإيقاف</button></div>`).join("")}</div>` : `<p class="muted">ما فيه حسابات موقوفة ✅</p>`}`;
}

const FB_TYPES = ["🐞 مشكلة", "💡 اقتراح", "✨ فكرة إضافية", "🚩 شكوى على متجر", "أخرى"];
const statusLabel = (s) => ({ in_progress: "قيد المتابعة", done: "تمت ✅" }[s] || "جديدة");

function messages() {
  const list = state.feedback.filter((f) => (state.inboxType === "الكل" || f.type === state.inboxType) && (state.showDone || f.status !== "done"));
  return `<div class="chips">${["الكل", ...FB_TYPES].map((t) => `<button class="chip ${state.inboxType === t ? "on" : ""}" data-fbtype="${esc(t)}">${esc(t)}</button>`).join("")}
      <button class="chip ${state.showDone ? "on" : ""}" data-showdone="1">إظهار المنتهية</button></div>
    ${list.length ? "" : `<p class="muted">لا توجد رسائل ✅</p>`}
    <div class="grid">${list.map((f) => `<div class="card ${(f.status || "new") === "new" ? "new" : ""}">
      <div class="row" style="justify-content:space-between"><b>${esc(f.type)}</b><span class="pill">${statusLabel(f.status)}</span></div>
      <div class="muted">${[f.role, f.userName, f.contact].filter(Boolean).map(esc).join(" • ")}${f.createdAt?.toDate ? " • " + f.createdAt.toDate().toLocaleString("ar-SA") : ""}</div>
      <p style="white-space:pre-wrap">${esc(f.message)}</p>
      ${f.screenshotUrl ? `<img class="thumb" src="${esc(img(f.screenshotUrl))}" data-view="${esc(f.screenshotUrl)}">` : ""}
      <div class="muted">📱 ${esc(f.device)} • إصدار ${esc(f.appVersion)}</div>
      ${f.uid ? `<textarea id="reply_${f.id}" placeholder="ردّك (يظهر له في التطبيق)">${esc(state.drafts[f.id] ?? f.reply ?? "")}</textarea>` : (f.contact ? "" : `<div class="muted">زائر بدون طريقة تواصل</div>`)}
      <div class="actions">
        ${f.uid ? `<button class="btn primary" data-act="reply" data-id="${f.id}">أرسل الرد</button>` : ""}
        ${f.status !== "in_progress" && f.status !== "done" ? `<button class="btn" data-act="progress" data-id="${f.id}">قيد المتابعة</button>` : ""}
        ${f.status !== "done" ? `<button class="btn" data-act="done" data-id="${f.id}">تمت</button>` : ""}
        <button class="btn danger" data-act="deleteFb" data-id="${f.id}">حذف</button>
      </div></div>`).join("")}</div>`;
}

function offers() {
  const q = state.offerQuery.trim().toLowerCase();
  const list = state.offers.filter((o) =>
    (!q || [o.restaurantName, o.offerTitle, o.district].some((v) => (v || "").toLowerCase().includes(q))) &&
    ({ "نشط": o.isApproved && !isExpired(o), "بانتظار المراجعة": !o.isApproved, "منتهي": isExpired(o), "مميز ⭐": o.isFeatured }[state.offerFilter] ?? true)
  ).sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0));
  return `<input type="text" id="offerQuery" placeholder="ابحث باسم المتجر أو العرض أو الحي…" value="${esc(state.offerQuery)}">
    <div class="chips">${["الكل", "نشط", "بانتظار المراجعة", "منتهي", "مميز ⭐"].map((t) => `<button class="chip ${state.offerFilter === t ? "on" : ""}" data-ofilter="${esc(t)}">${esc(t)}</button>`).join("")}</div>
    <p class="muted">${list.length} عرض</p>
    <div class="grid">${list.map((o) => offerCard(o,
      `<div style="margin-top:6px"><span class="pill ${o.isApproved ? "" : "off"}">${o.isApproved ? "نشط" : "معلق"}</span>
       ${isExpired(o) ? `<span class="pill off">منتهي</span>` : ""} ${o.isFeatured ? `<span class="pill">مميز ⭐</span>` : ""}</div>`,
      `<button class="btn" data-act="feature" data-id="${o.id}">${o.isFeatured ? "إلغاء التمييز" : "⭐ تمييز"}</button>
       <button class="btn" data-act="toggleApproved" data-id="${o.id}">${o.isApproved ? "إيقاف" : "تفعيل"}</button>
       <button class="btn danger" data-act="deleteOffer" data-id="${o.id}">حذف</button>`)).join("")}</div>`;
}

function settings() {
  const cfg = state.config;
  return `<h2>⚙️ رقم واتساب الدعم</h2>
    <div class="card"><div class="row" style="align-items:center"><input type="text" id="supportNum" value="${esc(cfg.supportWhatsapp || "0552793379")}" style="max-width:240px">
      <button class="btn primary" data-act="saveSupport">حفظ</button></div></div>
    <h2>✨ التعبئة الذكية</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="aiOn" ${cfg.aiAutofillEnabled ? "checked" : ""}> ${cfg.aiAutofillEnabled ? "مفعّلة" : "موقفة"}</label>
      <details><summary>إعدادات متقدمة (اسم النموذج)</summary>
        <div class="row" style="margin-top:8px"><input type="text" id="aiModel" value="${esc(cfg.aiModel || "gemini-3.8-flash")}" style="max-width:260px">
        <button class="btn primary" data-act="saveModel">حفظ</button></div></details>
    </div>
    <h2>📍 محلات Google على الخريطة</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="poiOn" ${cfg.showGooglePoi !== false ? "checked" : ""}> إظهار نقاط المحلات (باهتة)</label>
      <label class="switch" style="margin-top:8px"><input type="checkbox" id="inviteOn" ${cfg.poiInviteEnabled !== false ? "checked" : ""}> بطاقة "ادعه لحيّنا" عند الضغط</label>
      <p class="muted">نص الدعوة ({name} = اسم المحل)</p>
      <textarea id="inviteMsg">${esc(cfg.inviteMessage || 'السلام عليكم 👋 شفت محلكم "{name}" على خريطة حيّنا. سجّلوا محلكم مجاناً وانشروا عروضكم لأهل الحي، وخلّوا الزباين يوصلون لكم بسهولة 📍')}</textarea>
      <p class="muted">رابط تحميل التطبيق (اختياري)</p>
      <input type="text" id="appLink" value="${esc(cfg.appLink || "")}">
      <div class="actions"><button class="btn primary" data-act="savePoi">حفظ</button></div>
    </div>`;
}

function bind() {
  const byId = (id) => state.offers.find((o) => o.id === id);
  const c = $("content");
  c.querySelectorAll("[data-go]").forEach((b) => (b.onclick = () => { state.tab = b.dataset.go; render(); }));
  c.querySelectorAll("[data-view]").forEach((i) => (i.onclick = () => { $("viewerImg").src = img(i.dataset.view, 1600); $("viewer").classList.remove("hidden"); }));
  c.querySelectorAll("[data-fbtype]").forEach((b) => (b.onclick = () => { state.inboxType = b.dataset.fbtype; render(); }));
  c.querySelectorAll("[data-showdone]").forEach((b) => (b.onclick = () => { state.showDone = !state.showDone; render(); }));
  c.querySelectorAll("[data-ofilter]").forEach((b) => (b.onclick = () => { state.offerFilter = b.dataset.ofilter; render(); }));
  c.querySelectorAll("textarea[id^=reply_]").forEach((t) => (t.oninput = () => { state.drafts[t.id.slice(6)] = t.value; }));
  const oq = $("offerQuery"); if (oq) oq.oninput = () => { state.offerQuery = oq.value; render(); };
  const poiOn = $("poiOn"); if (poiOn) poiOn.onchange = () => run(() => actions.saveConfig({ showGooglePoi: poiOn.checked }), "تم ✅");
  const invOn = $("inviteOn"); if (invOn) invOn.onchange = () => run(() => actions.saveConfig({ poiInviteEnabled: invOn.checked }), "تم ✅");
  const ai = $("aiOn"); if (ai) ai.onchange = () => run(() => actions.saveConfig({ aiAutofillEnabled: ai.checked }), ai.checked ? "التعبئة الذكية مفعّلة ✨" : "التعبئة الذكية موقفة");
  c.querySelectorAll("[data-act]").forEach((b) => (b.onclick = () => {
    const id = b.dataset.id, o = byId(id);
    const map = {
      approve: () => actions.approve(o),
      trust: () => actions.trust(o),
      deleteOffer: () => actions.deleteOffer(id),
      suspendOffer: () => actions.suspend(o.merchantId, o.restaurantName),
      suspendUid: () => actions.suspend(id, b.dataset.name || ""),
      keep: () => actions.keep(o),
      dismissAll: async () => { for (const r of state.reports.filter((r) => r.offerId === id)) await actions.dismissReport(r.id); },
      deleteAndDismiss: async () => { await actions.deleteOffer(id); for (const r of state.reports.filter((r) => r.offerId === id)) await actions.dismissReport(r.id); },
      approveStore: () => actions.approveStore(id),
      untrust: () => actions.untrust(statusOf(id)),
      unsuspend: () => actions.unsuspend(statusOf(id)),
      feature: () => actions.feature(o),
      toggleApproved: () => actions.toggleApproved(o),
      reply: async () => { await actions.reply(id, $("reply_" + id).value.trim()); delete state.drafts[id]; },
      progress: () => actions.fbStatus(id, "in_progress"),
      done: () => actions.fbStatus(id, "done"),
      deleteFb: () => actions.deleteFb(id),
      saveSupport: () => actions.saveConfig({ supportWhatsapp: $("supportNum").value.trim() }),
      saveModel: () => actions.saveConfig({ aiModel: $("aiModel").value.trim() }),
      savePoi: () => actions.saveConfig({ inviteMessage: $("inviteMsg").value.trim(), appLink: $("appLink").value.trim() }),
    };
    if (map[b.dataset.act]) run(map[b.dataset.act], ["approve", "trust", "suspendOffer", "suspendUid"].includes(b.dataset.act) ? null : "تم ✅");
  }));
}

$("viewerClose").onclick = () => $("viewer").classList.add("hidden");
$("viewer").onclick = (e) => { if (e.target.id === "viewer") $("viewer").classList.add("hidden"); };
