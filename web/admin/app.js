// لوحة إدارة لكوفر على الويب — نفس قواعد Firestore تحميها (الأدمن فقط يقدر يقرأ ويعدّل).
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, signInWithPopup, onAuthStateChanged, signOut
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  getFirestore, collection, doc, onSnapshot, query, where, orderBy, limit,
  updateDoc, deleteDoc, setDoc, getDocs, getDoc, serverTimestamp, writeBatch, Timestamp, addDoc, deleteField
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { EFFECTS, creditsOf, itemList, exclusiveOf, DEFAULT_ITEMS, rewardOf } from "/js/credits.js";
import { KINDS, KIND_IDS, catalogOf, vatOf, baseKindOf, limitOf, itemVisible, itemPaidUp, riyal, ORDER_STATUS, FEATURES, feat, MODES, PAY, noshowOf, blockedUntil, noshowCount } from "/js/catalog.js";
import { firebaseConfig, FIRESTORE_DB_ID, ADMIN_EMAILS, AUTO_HIDE_REPORTS, MAPS_WEB_KEY } from "../config.js";
import { mountAdForm, adDays, AD_MAX_DAYS } from "/js/scout.js";
import { CATEGORIES } from "/js/logic.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, FIRESTORE_DB_ID);

// ---------------- الحالة ----------------
const state = {
  tab: localStorage.getItem("hayyna_admin_tab") || "home",
  offers: [], reports: [], statuses: [], pendingStores: [], feedback: [], config: {}, poi: [], verifs: [], referrals: [], names: {}, wallets: [], codes: null, itemsDraft: null, packsDraft: null,
  me: null, staff: [], log: [], logArea: "الكل", logQuery: "", logActor: "الكل", verifHist: [],
  inboxType: "الكل", showDone: false, offerQuery: "", offerFilter: "الكل", drafts: {},
  items: [], passes: [], orders: [], ratings: [], custStats: [], rateFilter: "الكل", rateQuery: "", ordFilter: "الكل", ordStore: "", itemQuery: "", itemFilter: "الكل", storesAll: [], storeQuery: "", storeFilter: "الكل",
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
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.add("hidden"), /خطأ/.test(msg) ? 8000 : 2800);
};
const run = async (fn, okMsg, label = "") => {
  try { await fn(); if (okMsg) toast(okMsg); }
  catch (e) {
    console.error(label, e);
    const perm = e.code === "permission-denied" || /permission/i.test(e.message || "");
    toast(`صار خطأ${label ? ` في «${label}»` : ""}: ${perm ? `ما عندك صلاحية (${auth.currentUser?.email || "؟"})` : (e.message || e)}`);
  }
};

// ---------------- الدخول ----------------
$("loginBtn").onclick = () => run(() => signInWithPopup(auth, new GoogleAuthProvider()));

// ---------------- الصلاحيات ----------------
// المالك (ADMIN_EMAILS) يقدر كل شي. الموظف: staff/{email} فيه perms
export const PERMS = [
  ["review", "📝 مراجعة العروض", "اعتماد العروض وحذفها والبلاغات"],
  ["verify", "🛡️ التوثيق", "قبول ورفض طلبات توثيق المتاجر"],
  ["messages", "📨 الرسائل", "قراءة رسائل التواصل والرد عليها"],
  ["merchants", "🏪 إدارة التجار", "الإيقاف، صفحات المتاجر، الإحالات، محلات Google"],
  ["credits", "💰 الرصيد", "الأكواد وتعديل أرصدة التجار"],
  ["scout", "📷 عروض الإعلانات", "رفع عروض من صور إعلانات لمحلات غير مسجّلة"],
];
const can = (p) => !!state.me && (state.me.owner || state.me.perms.has(p));
const canStores = () => can("review") || can("verify") || can("merchants");

onAuthStateChanged(auth, async (user) => {
  unsubs.splice(0).forEach((u) => u());
  if (!user) {
    state.me = null;
    $("login").classList.remove("hidden"); $("app").classList.add("hidden"); $("user").innerHTML = "";
    return;
  }
  const email = (user.email || "").toLowerCase();
  if (ADMIN_EMAILS.includes(email)) state.me = { owner: true, perms: new Set(PERMS.map(([k]) => k)), email, uid: user.uid, name: "المالك" };
  else {
    let st = null; try { const d = await getDoc(doc(db, "staff", email)); st = d.exists() ? d.data() : null; } catch {}
    if (!st || !st.active) {
      $("loginError").textContent = st ? "حسابك في الفريق موقوف حالياً. تواصل مع المالك." : "هذا الحساب ما عنده صلاحية دخول لوحة الإدارة.";
      signOut(auth); return;
    }
    state.me = { owner: false, perms: new Set(st.perms || []), email, uid: user.uid, name: st.name || email };
    if (!TABS.some(([k]) => k === state.tab && tabAllowed(k))) state.tab = "home";
  }
  $("login").classList.add("hidden"); $("app").classList.remove("hidden");
  $("user").innerHTML = `<span class="avatar">${esc((state.me.name || user.email || "?").trim().charAt(0).toUpperCase())}</span>
    <span class="who"><b>${esc(state.me.owner ? "المالك" : state.me.name)}</b><small>${esc(user.email)}</small></span><button class="btn" id="logout">خروج</button>`;
  $("logout").onclick = () => signOut(auth);
  listen();
  render();
});

// ---------------- الاستماع للبيانات ----------------
function listen() {
  const parse = (snap) => snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const sub = (q, fn) => unsubs.push(onSnapshot(q, (sn) => { fn(sn); render(); }, (e) => console.warn("listen", e.code)));
  if (can("review")) {
    sub(collection(db, "offers"), (sn) => { state.offers = parse(sn); });
    sub(query(collection(db, "reports"), orderBy("createdAt", "desc"), limit(200)), (sn) => { state.reports = parse(sn); });
  }
  if (canStores()) {
    sub(collection(db, "merchantStatus"), (sn) => { state.statuses = parse(sn).map((x) => ({ ...x, uid: x.id })); });
    sub(query(collection(db, "stores"), where("approved", "==", false)), (sn) => { state.pendingStores = parse(sn).map((x) => ({ ...x, uid: x.id })); });
    sub(collection(db, "stores"), (sn) => { state.storesAll = parse(sn).map((x) => ({ ...x, uid: x.id })); });
    // 🔢 رقم المتجر (اللي يدخل فيه الموظفين)
    sub(collection(db, "storeCodes"), (sn) => { state.storeCode = {}; sn.docs.forEach((d) => { const u = d.data().uid; if (u) state.storeCode[u] = d.id; }); });
  }
  if (can("messages")) sub(query(collection(db, "feedback"), orderBy("createdAt", "desc"), limit(300)), (sn) => { state.feedback = parse(sn); });
  if (can("merchants")) {
    sub(query(collection(db, "poiInterest"), orderBy("taps", "desc"), limit(50)), (sn) => { state.poi = parse(sn); });
    sub(collection(db, "referrals"), (sn) => { state.referrals = parse(sn).map((x) => ({ ...x, storeUid: x.id })); loadNames(); });
  }
  if (can("verify")) {
    sub(query(collection(db, "verificationRequests"), where("status", "==", "pending")), (sn) => { state.verifs = parse(sn).map((x) => ({ ...x, uid: x.id })); });
    sub(query(collection(db, "verificationRequests"), where("status", "in", ["approved", "rejected"])), (sn) => { state.verifHist = parse(sn).map((x) => ({ ...x, uid: x.id })); });
  }
  // عروض الإعلانات: المراجع يشوفها ضمن كل العروض، وفريق الإعلانات بس هذي
  if (can("scout") && !can("review")) sub(query(collection(db, "offers"), where("source", "==", "ad")), (sn) => { state.offers = parse(sn); });
  if (can("credits")) sub(collection(db, "wallets"), (sn) => { state.wallets = parse(sn); loadNames(); });
  if (can("review")) {
    sub(collection(db, "items"), (sn) => { state.items = parse(sn); });
    sub(collection(db, "itemPasses"), (sn) => { state.passes = parse(sn); });
  }
  if (can("merchants")) {
    sub(query(collection(db, "orders"), orderBy("createdAt", "desc"), limit(500)), (sn) => { state.orders = parse(sn); });
    sub(collection(db, "ratings"), (sn) => { state.ratings = parse(sn).sort((a, b) => (b.at?.toMillis?.() || 0) - (a.at?.toMillis?.() || 0)); });
    sub(collection(db, "custStats"), (sn) => { state.custStats = parse(sn); });
  }
  sub(doc(db, "config", "app"), (sn) => { state.config = sn.data() || {}; });
  // السجل: المالك يشوف الكل، والموظف عملياته (الترتيب على الجهاز)
  sub(state.me.owner ? query(collection(db, "adminLog"), orderBy("at", "desc"), limit(400)) : query(collection(db, "adminLog"), where("actor", "==", state.me.email)),
    (sn) => { state.log = parse(sn).sort((a, b) => (b.at?.toMillis?.() || 0) - (a.at?.toMillis?.() || 0)); });
  if (state.me.owner) sub(collection(db, "staff"), (sn) => { state.staff = parse(sn); });
}

// أسماء أصحاب الدعوات والمحافظ: اسم المتجر + اسم صاحبه وجواله (بدل رقم الحساب)
async function loadNames() {
  const need = [...new Set([...state.referrals.map((r) => r.referrer), ...state.wallets.map((w) => w.id)])].filter((u) => u && !(u in state.names));
  for (const u of need) {
    state.names[u] = "…";
    let store = "", person = "";
    try { store = (await getDoc(doc(db, "stores", u))).data()?.name || ""; } catch {}
    try { const x = (await getDoc(doc(db, "users", u))).data() || {}; person = [x.name, x.phone].filter(Boolean).join(" • "); } catch {}
    state.names[u] = [store, person].filter(Boolean).join(" — ") || "حساب بدون اسم";
  }
  if (need.length) render();
}
const PTS = { verified: 50, offer: 50 };
function refInfo(r) {
  const st = statusOf(r.storeUid);
  const joined = !!(st?.verified || st?.trusted);
  const hasOffer = state.offers.some((o) => o.merchantId === r.storeUid && o.isApproved);
  return { joined, hasOffer, canVerified: joined && !r.verifiedPts, canOffer: hasOffer && !r.offerPts };
}

// ---------------- سجل العمليات ----------------
const AREAS = { scout: "📷 الإعلانات", catalog: "🛍️ المنتجات", review: "📝 العروض", verify: "🛡️ التوثيق", merchants: "🏪 التجار", messages: "📨 الرسائل", credits: "💰 الرصيد", settings: "⚙️ الإعدادات", team: "👥 الفريق" };
async function audit(action, area, target = {}, details = "") {
  try {
    await addDoc(collection(db, "adminLog"), {
      at: serverTimestamp(), actor: state.me.email, actorUid: state.me.uid, action: String(action).slice(0, 60), area,
      targetType: target.type || "", targetId: String(target.id || "").slice(0, 120), targetName: String(target.name || "").slice(0, 200),
      details: String(details || "").slice(0, 1500), thumb: String(target.thumb || "").slice(0, 500), source: "web",
    });
  } catch (e) { console.warn("audit", e); }
}
const offerT = (o) => o ? { type: "offer", id: o.id, name: `${o.offerTitle} — ${o.restaurantName}`, thumb: thumbOf(o) } : {};
const uidT = (uid, name) => ({ type: "merchant", id: uid || "", name: name || statusOf(uid)?.name || state.names[uid] || String(uid || "").slice(0, 8) });

// ---------------- أوامر الإدارة (نفس منطق التطبيق) ----------------
const statusOf = (uid) => state.statuses.find((s) => s.uid === uid);
const setStatus = (uid, data) => setDoc(doc(db, "merchantStatus", uid), { ...data, updatedAt: serverTimestamp() }, { merge: true });
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
      toast(`تم الاعتماد ✅ وصار ${o.restaurantName} ⚡ ينشر مباشرة`);
    } else toast("تم اعتماد العرض ✅");
  },
  async trust(o) {
    await setStatus(o.merchantId, { trusted: true, suspended: false, name: o.restaurantName });
    await updateDoc(doc(db, "offers", o.id), { isApproved: true });
    await setStoreApproved(o.merchantId, true);
    toast(`صار ${o.restaurantName} ⚡ ينشر مباشرة ✅`);
  },
  async suspend(uid, name) {
    if (!confirm(`إيقاف ${name}؟ بتختفي كل عروضه وما يقدر ينشر.`)) return false;
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
  async deleteOffer(id) { if (!confirm("حذف العرض نهائياً؟")) return false; await deleteDoc(doc(db, "offers", id)); },
  dismissReport: (id) => deleteDoc(doc(db, "reports", id)),
  approveStore: (uid) => updateDoc(doc(db, "stores", uid), { approved: true }),
  feature: (o) => updateDoc(doc(db, "offers", o.id), { isFeatured: !o.isFeatured }),
  toggleApproved: (o) => updateDoc(doc(db, "offers", o.id), { isApproved: !o.isApproved }),
  async unexcl(o) { if (!confirm(`شيل «الحصري» عن «${o.offerTitle}»؟\n(الشارة بس، بدون خصم من رصيد التاجر)`)) return false; await updateDoc(doc(db, "offers", o.id), { exclusive: false }); },
  reply: (id, reply) => updateDoc(doc(db, "feedback", id), { reply, status: "done", replySeen: !reply }),
  fbStatus: (id, status) => updateDoc(doc(db, "feedback", id), { status }),
  async deleteFb(id) { if (!confirm("حذف الرسالة؟")) return false; await deleteDoc(doc(db, "feedback", id)); },
  awardRef: (uid, field) => updateDoc(doc(db, "referrals", uid), { [field]: field === "verifiedPts" ? PTS.verified : PTS.offer, [field.replace("Pts", "At")]: serverTimestamp() }),
  async removeRef(uid) { if (!confirm("حذف الإحالة؟ (لو كانت وهمية) — بتنحذف نقاطها")) return false; await deleteDoc(doc(db, "referrals", uid)); },
  saveConfig: (data) => setDoc(doc(db, "config", "app"), data, { merge: true }),
  async verify(uid, ok, reason) {
    const v = state.verifs.find((x) => x.uid === uid);
    await updateDoc(doc(db, "verificationRequests", uid), { status: ok ? "approved" : "rejected", rejectReason: reason || "", decidedAt: serverTimestamp(), decidedBy: state.me.email });
    const sref = doc(db, "stores", uid);
    if ((await getDoc(sref)).exists()) await updateDoc(sref, ok ? { verified: true, approved: true } : { verified: false });
    if (ok) await setStatus(uid, { trusted: true, suspended: false, verified: true, name: v?.storeName || "" });
    toast(ok ? "تم التوثيق ✓" : "تم الرفض");
  },
};

// ---------------- الواجهة ----------------
function computed() {
  const suspendedIds = new Set(state.statuses.filter((s) => s.suspended).map((s) => s.uid));
  const pending = state.offers.filter((o) => !o.isApproved && !o.draft && !o.adminNote && !suspendedIds.has(o.merchantId)); // المسودة تنراجع لما ينشرها التاجر
  const hidden = state.offers.filter((o) => o.isApproved && (o.reportsCount || 0) >= AUTO_HIDE_REPORTS);
  const hiddenIds = new Set(hidden.map((o) => o.id));
  const otherReports = {};
  state.reports.filter((r) => !hiddenIds.has(r.offerId)).forEach((r) => (otherReports[r.offerId] ||= []).push(r));
  const pendingStores = state.pendingStores.filter((s) => !suspendedIds.has(s.uid));
  const newMsgs = state.feedback.filter((f) => (f.status || "new") === "new").length;
  const refAwards = state.referrals.filter((r) => { const i = refInfo(r); return i.canVerified || i.canOffer; }).length;
  return { pending, hidden, otherReports, pendingStores, newMsgs, refAwards };
}

const TABS = [
  ["home", "🏠 الرئيسية"], ["review", "⏳ المراجعة"], ["stores", "🏪 المتاجر"],
  ["messages", "📨 الرسائل"], ["refs", "🎁 الإحالات"], ["offers", "📋 كل العروض"], ["ads", "📷 الإعلانات"], ["catalog", "🛍️ المنتجات"], ["credits", "💰 الرصيد"], ["promo", "📣 الإعلانات والعروض"],
  ["log", "📜 السجل"], ["team", "👥 الفريق"], ["settings", "⚙️ الإعدادات"],
];
// القائمة الجانبية: مجموعات + أيقونة خطية ولون لكل قسم (ألوان Google)
const G = { blue: "#1A73E8", red: "#EA4335", yel: "#F9AB00", grn: "#34A853", mut: "#5F6368" };
const ICONS = {
  home: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  log: '<path d="M8 6h13M8 12h13M8 18h13M3 6h1M3 12h1M3 18h1"/>',
  review: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  offers: '<path d="M20 12l-8 8-9-9V3h8z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
  ads: '<rect x="3" y="6" width="18" height="14" rx="2"/><circle cx="12" cy="13" r="3.5"/>',
  catalog: '<path d="M5 8h14l-1.5 12h-11z"/><path d="M9 8a3 3 0 016 0"/>',
  stores: '<path d="M3 9l1.5-5h15L21 9M4 9v11h16V9M9 20v-6h6v6"/>',
  messages: '<path d="M4 5h16v11H8l-4 4z"/>',
  refs: '<rect x="3" y="8" width="18" height="5" rx="1"/><path d="M5 13v8h14v-8M12 8v13"/>',
  credits: '<rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18M7 15h3"/>',
  promo: '<path d="M3 11v2l13 5V6z"/><path d="M16 9a3 3 0 010 6"/>',
  team: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.8-3.5 3.3-5.5 6.5-5.5s5.7 2 6.5 5.5M16 4.5a3.5 3.5 0 010 7M18 14.8c2 .7 3.2 2.5 3.6 5.2"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/>',
};
const icon = (k, color, size = 20) => `<svg class="ico" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[k] || ""}</svg>`;
const NAV = [
  ["عام", [["home", "الرئيسية", G.blue], ["log", "سجل العمليات", G.mut]]],
  ["المحتوى", [["review", "المراجعة", G.yel], ["offers", "كل العروض", G.red], ["ads", "عروض الإعلانات", G.grn], ["catalog", "المنتجات والطلبات", G.blue]]],
  ["التجار", [["stores", "المتاجر والتوثيق", G.grn], ["messages", "الرسائل", G.blue], ["refs", "الإحالات والسفراء", G.red]]],
  ["المالية والتسويق", [["credits", "الرصيد والباقات", G.grn], ["promo", "إعلانات وعروض لكوفر", G.yel]]],
  ["النظام", [["team", "الفريق والصلاحيات", G.blue], ["settings", "الإعدادات", G.mut]]],
];
const navLabel = (k) => NAV.flatMap(([, l]) => l).find(([x]) => x === k)?.[1] || "الرئيسية";
function tabAllowed(k) {
  return ({ home: true, log: true, review: can("review"), offers: can("review"), stores: canStores(), messages: can("messages"),
    refs: can("merchants"), credits: can("credits"), promo: !!state.me?.owner, catalog: can("review"), team: !!state.me?.owner, settings: !!state.me?.owner, ads: can("scout") })[k];
}

function render() {
  if ($("app").classList.contains("hidden")) return;
  const c = computed();
  const badges = { catalog: state.items.filter((x) => !x.approved && !x.adminNote).length, review: c.pending.length + c.hidden.length, stores: c.pendingStores.length + state.verifs.length, messages: c.newMsgs, refs: c.refAwards };
  if (!tabAllowed(state.tab)) state.tab = "home";
  $("tabs").innerHTML = NAV.map(([title, items]) => {
    const vis = items.filter(([k]) => tabAllowed(k));
    return vis.length ? `<div class="nav-group"><div class="nav-title">${title}</div>${vis.map(([k, l, col]) =>
      `<button class="tab ${state.tab === k ? "active" : ""}" data-tab="${k}">${icon(k, state.tab === k ? G.blue : col)}<span class="tab-l">${l}</span>${badges[k] ? `<span class="badge">${badges[k]}</span>` : ""}</button>`).join("")}</div>` : "";
  }).join("");
  $("pageTitle").textContent = navLabel(state.tab);
  $("tabs").querySelectorAll(".tab").forEach((b) => (b.onclick = () => { state.tab = b.dataset.tab; localStorage.setItem("hayyna_admin_tab", state.tab); render(); window.scrollTo(0, 0); }));
  const focused = document.activeElement?.id;
  const caret = document.activeElement?.selectionStart;
  $("content").innerHTML = ({ home, review, stores, messages, refs, offers, credits, catalog: catalogPage, settings, log: logPage, team, ads: adsPage, promo: promoPage }[state.tab] || home)(c);
  if (state.tab === "ads") attachAdForm();
  if (state.tab === "promo") attachPromo();
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
        <div class="muted">${o.postType === "new" ? "✨ جديدنا • " : ""}${o.postType === "new" && !(o.price > 0) && !(o.discountPercent > 0) ? "بدون سعر" : o.price > 0 ? `${esc(o.price)} ر.س ${o.originalPrice ? `<s>${esc(o.originalPrice)}</s>` : ""}` : `خصم ${(o.dailyPercents && o.dailyPercents.length) ? esc(o.dailyPercents.join("/")) : esc(o.discountPercent || 0)}% ${esc(o.itemsScope || "")}`}${o.startsAt && o.startsAt.toMillis && o.startsAt.toMillis() > Date.now() ? " • 🔜 قريباً" : ""}${o.grandOpening ? " • 🎉 افتتاح" : ""} • ${esc(o.district)} • 👁 ${o.viewsCount || 0}</div>
        ${o.description ? `<div class="muted">${esc(o.description).slice(0, 200)}</div>` : ""}
        ${(o.mediaUris || []).length > 1 ? `<div class="actions">${o.mediaUris.slice(1).map((u) => `<img class="thumb small" src="${esc(img(u, 150))}" data-view="${esc(u)}">`).join("")}</div>` : ""}
        ${extra}
      </div>
    </div>
    <div class="actions">${buttons}</div>
  </div>`;
}

// ---------------- الرئيسية (لوحة ملوّنة) ----------------
const ms = (t) => (t?.toMillis ? t.toMillis() : 0);
const num = (n) => Number(n || 0).toLocaleString("en-US");
const DAY = 864e5;
function dailySeries(list, field, days = 30) {
  const start = new Date(); start.setHours(0, 0, 0, 0);
  const t0 = start.getTime() - (days - 1) * DAY, out = new Array(days).fill(0);
  for (const x of list) { const t = ms(x[field]); if (t >= t0) { const i = Math.floor((t - t0) / DAY); if (i >= 0 && i < days) out[i]++; } }
  return out;
}
function lineChart(series, h = 220) {
  const W = 600, max = Math.max(1, ...series.flatMap((s) => s.data));
  const pts = (d) => d.map((v, i) => `${((i / (d.length - 1)) * W).toFixed(1)},${(h - 12 - (v / max) * (h - 30)).toFixed(1)}`).join(" ");
  const grid = [0.25, 0.5, 0.75].map((f) => `<line x1="0" x2="${W}" y1="${h * f}" y2="${h * f}" stroke="#EEF0F2" vector-effect="non-scaling-stroke"/>`).join("");
  const first = series[0];
  return `<svg class="line" viewBox="0 0 ${W} ${h}" preserveAspectRatio="none" role="img" aria-label="${esc(series.map((s) => s.label).join("، "))} آخر ٣٠ يوم">${grid}
    <polygon points="0,${h} ${pts(first.data)} ${W},${h}" fill="${first.color}" fill-opacity=".10"/>
    ${series.map((s) => `<polyline points="${pts(s.data)}" fill="none" stroke="${s.color}" stroke-width="3" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"/>`).join("")}</svg>`;
}
const legend = (items) => `<div class="legend">${items.map(([t, c]) => `<span><i style="background:${c}"></i>${t}</span>`).join("")}</div>`;
function donut(parts, total, label) {
  let acc = 0; const sum = parts.reduce((a, p) => a + p[1], 0) || 1;
  const stops = parts.map(([, v, c]) => { const a0 = acc; acc += (v / sum) * 360; return `${c} ${a0}deg ${acc}deg`; }).join(",");
  return `<div class="donut" style="background:${total ? `conic-gradient(${stops})` : "#EEF0F2"}" role="img" aria-label="${esc(parts.map(([t, v]) => `${t} ${v}`).join("، "))}"><div><b>${num(total)}</b><small>${label}</small></div></div>
    <div class="dlist">${parts.map(([t, v, c]) => `<div><span><i style="background:${c}"></i>${t}</span><b>${num(v)}</b></div>`).join("")}</div>`;
}
const hbars = (rows, colors) => {
  const max = Math.max(1, ...rows.map((r) => r[1]));
  return rows.length ? `<div class="hbars">${rows.map(([t, v], i) => `<div><div class="hb-top"><span>${esc(t)}</span><b>${num(v)}</b></div>
    <div class="hb-track"><div style="width:${Math.max(3, (v / max) * 100)}%;background:${colors[i % colors.length]}"></div></div></div>`).join("")}</div>` : `<p class="muted">ما فيه بيانات للحين</p>`;
};

function home(c) {
  const todo = [
    ["عروض تنتظر المراجعة", c.pending.length, "review", "y"],
    ["مخفية بسبب البلاغات", c.hidden.length, "review", "r"],
    ["طلبات توثيق", state.verifs.length, "stores", "b"],
    ["صفحات متاجر جديدة", c.pendingStores.length, "stores", "g"],
    ["رسائل جديدة", c.newMsgs, "messages", "b"],
    ["نقاط إحالة تنتظرك", c.refAwards, "refs", "y"],
  ].filter((t) => t[1] > 0 && tabAllowed(t[2]));
  const attention = `<section class="card panel"><div class="ph"><h2><span class="gdots"><i></i><i></i><i></i><i></i></span>يحتاج انتباهك</h2></div>
    ${todo.length ? `<div class="todos">${todo.map(([l, n, t, k]) => `<button class="todo ${k}" data-go="${t}"><span><i></i>${l}</span><b>${num(n)} ‹</b></button>`).join("")}</div>`
      : `<p class="muted">كل شي تمام، ما فيه شي ينتظرك ✅</p>`}</section>`;
  const hello = state.me.owner ? "" : `<p class="muted">أهلاً ${esc(state.me.name)} 👋 — صلاحياتك: ${PERMS.filter(([k]) => can(k)).map(([, l]) => l).join(" • ")}</p>`;
  if (!can("review") && !canStores()) return hello + attention + `<p class="muted">كل عملية تسويها تنحفظ في «سجل العمليات» باسمك.</p>`;

  const now = Date.now(), wk = now - 7 * DAY;
  const activeList = state.offers.filter((o) => o.isApproved && !isExpired(o));
  const merchants = new Set(state.offers.map((o) => o.merchantId)).size;
  const trusted = state.statuses.filter((s) => s.trusted && !s.suspended).length;
  const newOffers = state.offers.filter((o) => ms(o.createdAt) >= wk).length;
  const districts = {}; activeList.forEach((o) => { if (o.district) districts[o.district] = (districts[o.district] || 0) + 1; });
  const distRows = Object.entries(districts).sort((a, b) => b[1] - a[1]).slice(0, 5);
  const ordersOn = can("merchants");
  const ord = state.orders;
  const avg = state.ratings.length ? (state.ratings.reduce((a, r) => a + (r.stars || 0), 0) / state.ratings.length).toFixed(1) : null;
  const kpi = (label, val, sub, color, soft, ic) => `<div class="card kpi"><div class="kt"><span class="ktile" style="background:${soft}">${icon(ic, color, 22)}</span><span>${label}</span></div>
    <div class="kv">${val}</div>${sub ? `<span class="kd">${sub}</span>` : ""}<i class="kbar" style="background:${color}"></i></div>`;
  const kpis = `<section class="kpis">
    ${kpi("عروض نشطة", num(activeList.length), `${num(newOffers)} عرض جديد هالأسبوع`, G.blue, "#E8F0FE", "offers")}
    ${kpi("المتاجر", num(merchants), `${num(trusted)} ينشرون مباشرة`, G.red, "#FCE8E6", "stores")}
    ${ordersOn ? kpi("الطلبات", num(ord.length), avg ? `التقييم ${avg} ★ من ${num(state.ratings.length)}` : "آخر ٥٠٠ طلب", G.yel, "#FEF7E0", "catalog")
      : kpi("كل العروض", num(state.offers.length), "", G.yel, "#FEF7E0", "catalog")}
    ${kpi("الأحياء", num(Object.keys(districts).length), "فيها عروض نشطة", G.grn, "#E6F4EA", "home")}</section>`;

  const series = [{ label: "عروض جديدة", color: G.blue, data: dailySeries(state.offers, "createdAt") }];
  if (ordersOn) series.push({ label: "الطلبات", color: G.red, data: dailySeries(ord, "createdAt") });
  const views = state.offers.reduce((a, o) => a + (o.viewsCount || 0), 0), dirs = state.offers.reduce((a, o) => a + (o.directionsCount || 0), 0), wa = state.offers.reduce((a, o) => a + (o.whatsappCount || 0), 0);
  const st = (f) => ord.filter(f).length;
  const delivered = st((o) => o.status === "done"), failed = st((o) => o.status === "cancelled" || o.status === "noshow"), inProg = ord.length - delivered - failed;
  const pie = ordersOn
    ? `<div class="card panel side-chart"><div class="ph"><h2>حالة الطلبات</h2></div>${donut([["تمّت", delivered, G.blue], ["قيد التنفيذ", inProg, G.yel], ["ملغية / ما انستلمت", failed, G.red]], ord.length, "طلب")}</div>`
    : `<div class="card panel side-chart"><div class="ph"><h2>حالة العروض</h2></div>${donut([["نشطة", activeList.length, G.grn], ["تنتظر المراجعة", c.pending.length, G.yel], ["منتهية أو موقفة", Math.max(0, state.offers.length - activeList.length - c.pending.length), G.mut]], state.offers.length, "عرض")}</div>`;
  const charts = `<section class="row2">
    <div class="card panel grow"><div class="ph"><h2>${ordersOn ? "العروض الجديدة والطلبات" : "العروض الجديدة"} — آخر ٣٠ يوم</h2>${legend(series.map((s) => [s.label, s.color]))}</div>
      ${lineChart(series)}<div class="axis"><span>قبل ٣٠ يوم</span><span>قبل ٢٠</span><span>قبل ١٠</span><span>اليوم</span></div></div>
    ${pie}</section>
    <section class="row2 even">
      <div class="card panel"><div class="ph"><h2>أكثر الأحياء عروضاً</h2></div>${hbars(distRows, [G.blue, G.red, G.yel, G.grn, G.blue])}</div>
      <div class="card panel"><div class="ph"><h2>تفاعل الزبائن مع العروض</h2></div>${hbars([["المشاهدات", views], ["طلبوا الاتجاه", dirs], ["تواصلوا واتساب", wa]], [G.blue, G.red, G.grn])}
        <div class="pfoot"><span class="muted">⚡ ${num(trusted)} متجر ينشر مباشرة بدون مراجعة</span>${tabAllowed("stores") ? `<button class="linkbtn" data-go="stores">المتاجر</button>` : ""}</div></div>
    </section>`;

  const latest = [...c.pending, ...c.hidden].slice(0, 5);
  const table = can("review") ? `<section class="card panel"><div class="ph"><h2>آخر العروض بانتظار المراجعة</h2><button class="linkbtn" data-go="review">افتح المراجعة</button></div>
    ${latest.length ? `<div class="tblwrap"><table class="tbl nice"><thead><tr><th>العرض</th><th>المتجر</th><th>الحي</th><th>الحالة</th><th>إجراء</th></tr></thead><tbody>
    ${latest.map((o) => { const hid = c.hidden.includes(o); return `<tr><td><span class="ocell">${thumbOf(o) ? `<img class="thumb mini" src="${esc(thumbOf(o))}" data-view="${esc((o.mediaUris && o.mediaUris[0]) || o.mediaUri)}" alt="">` : `<span class="thumb mini"></span>`}<b>${esc(o.offerTitle)}</b></span></td>
      <td>${esc(o.restaurantName)}</td><td>${esc(o.district || "—")}</td>
      <td>${hid ? `<span class="pill r">عليه بلاغات (${o.reportsCount})</span>` : o.source === "ad" ? `<span class="pill b">من إعلان المحل</span>` : `<span class="pill y">ينتظر المراجعة</span>`}</td>
      <td><span class="acts">${hid ? `<button class="btn primary" data-act="keep" data-id="${o.id}">سليم، رجّعه</button>` : `<button class="btn primary" data-act="approve" data-id="${o.id}">اعتماد</button>`}
        <button class="btn" data-go="review">التفاصيل</button></span></td></tr>`; }).join("")}</tbody></table></div>` : `<p class="muted">ما فيه عروض تنتظر ✅</p>`}</section>` : "";
  return hello + attention + kpis + charts + table;
}

function review(c) {
  const reasons = (id) => state.reports.filter((r) => r.offerId === id).map((r) => esc(r.reason)).join("، ");
  return `<h2>${c.pending.length ? `⏳ عروض جديدة تنتظر المراجعة (${c.pending.length})` : "عروض جديدة: لا يوجد ✅"}</h2>
    <div class="grid">${c.pending.map((o) => offerCard(o, "",
      `<button class="btn primary" data-act="approve" data-id="${o.id}">اعتماد ✓</button>
       <button class="btn" data-act="trust" data-id="${o.id}">اعتماد + ⚡ ينشر مباشرة</button>
       <button class="btn" data-ret="offer" data-id="${o.id}">↩️ رجّعه للتاجر</button>
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
  const docLabel = (t) => t === "freelance" ? "وثيقة العمل الحر" : "السجل التجاري";
  const verifHtml = `<h2>${state.verifs.length ? `🛡️ طلبات التوثيق (${state.verifs.length})` : "طلبات التوثيق: لا يوجد ✅"}</h2>
    <div class="grid">${state.verifs.map((v) => `<div class="card">
      <div class="name">${esc(v.storeName || v.uid.slice(0, 8))}</div>
      ${v.docNumber ? `<div><b>${docLabel(v.docType)}: ${esc(v.docNumber)}</b> — <a target="_blank" rel="noopener" href="${v.docType === "freelance" ? "https://freelance.sa" : "https://mc.gov.sa"}">تحقق من الموقع الرسمي ›</a></div>` : ""}
      <div class="muted">${!v.locationChecked ? "📍 الموقع: ما تم التحقق" : v.locationOk ? `📍 كان عند المحل ✅ (${v.distanceMeters} م)` : `📍 كان بعيد ⚠️ (${v.distanceMeters} م)`}</div>
      ${v.photoUrl ? `<img class="thumb" style="width:100%;height:180px" src="${esc(img(v.photoUrl, 600))}" data-view="${esc(v.photoUrl)}">` : ""}
      <input type="text" id="reason_${v.uid}" placeholder="سبب الرفض (لو بترفض)">
      <div class="actions"><button class="btn primary" data-act="verifyOk" data-id="${v.uid}">✓ وثّق</button>
      <button class="btn danger" data-act="verifyNo" data-id="${v.uid}">رفض</button></div></div>`).join("")}</div>`;
  const hist = can("verify") ? `<details class="card" style="margin-top:10px"><summary>📁 طلبات التوثيق السابقة (${state.verifHist.length})</summary>
      ${state.verifHist.length ? `<table class="tbl"><thead><tr><th>المتجر</th><th>القرار</th><th>السبب</th><th>متى / مين</th></tr></thead><tbody>
      ${[...state.verifHist].sort((a, b) => (b.decidedAt?.toMillis?.() || b.createdAt?.toMillis?.() || 0) - (a.decidedAt?.toMillis?.() || a.createdAt?.toMillis?.() || 0)).map((v) => `<tr>
        <td>${esc(v.storeName || v.uid.slice(0, 8))}</td><td>${v.status === "approved" ? `<span class="pill">✓ موثّق</span>` : `<span class="pill off">مرفوض</span>`}</td>
        <td class="muted">${esc(v.rejectReason || "—")}</td><td class="muted">${v.decidedAt?.toDate ? v.decidedAt.toDate().toLocaleString("ar-SA") : "—"}${v.decidedBy ? ` • ${esc(v.decidedBy)}` : ""}</td></tr>`).join("")}</tbody></table>` : `<p class="muted">ما فيه طلبات سابقة</p>`}</details>` : "";
  return (can("verify") ? verifHtml + hist : "") + `<h2>${c.pendingStores.length ? `🏪 صفحات تنتظر الموافقة (${c.pendingStores.length})` : "صفحات تنتظر الموافقة: لا يوجد ✅"}</h2>
    <div class="grid">${c.pendingStores.map((s) => `<div class="card">
      <div class="row">${s.logoUrl ? `<img class="thumb small" src="${esc(img(s.logoUrl, 150))}" style="border-radius:50%">` : ""}
        <div><div class="name">${esc(s.name)}</div><div class="muted">${esc(s.category)}${s.priceFrom ? ` • تبدأ من ${s.priceFrom} ر.س` : ""}</div></div></div>
      ${s.about ? `<p class="muted">${esc(s.about)}</p>` : ""}
      <div class="actions">${[s.coverUrl, ...(s.menuImages || [])].filter(Boolean).map((u) => `<img class="thumb small" src="${esc(img(u, 200))}" data-view="${esc(u)}">`).join("")}</div>
      <div class="actions"><button class="btn primary" data-act="approveStore" data-id="${s.uid}">اعتماد الصفحة ✓</button>
        <button class="btn danger" data-act="suspendUid" data-id="${s.uid}" data-name="${esc(s.name)}">⛔ إيقاف التاجر</button></div></div>`).join("")}</div>
    ${allStoresHtml()}
    <h2>⚡ ينشرون مباشرة بدون مراجعة (${trusted.length})</h2>
    ${trusted.length ? `<div class="grid">${trusted.map((s) => `<div class="card row" style="align-items:center;justify-content:space-between">
      <b>${esc(s.name || s.uid.slice(0, 8))}</b><span><button class="btn" data-act="untrust" data-id="${s.uid}">إلغاء «ينشر مباشرة»</button>
      <button class="btn danger" data-act="suspendUid" data-id="${s.uid}" data-name="${esc(s.name)}">إيقاف</button></span></div>`).join("")}</div>` : `<p class="muted">ما فيه للحين</p>`}
    <details class="card fold" data-fold="poi" ${state.folds?.poi ? "open" : ""}>
      <summary><span>📍 محلات غير مسجّلة يطلبها العملاء <span class="pill">${state.poi.length}</span></span><span class="fold-hint">اضغط للعرض</span></summary>
      <p class="muted">من نقاط خرائط Google: 👆 ضغطات • 📣 دعوات • 🏪 "أنا صاحب المحل"</p>
      ${state.poi.length ? `<div class="grid">${state.poi.map((p) => `<a class="card row" style="justify-content:space-between;text-decoration:none;color:inherit" target="_blank" rel="noopener"
         href="https://www.google.com/maps/search/?api=1&query=place&query_place_id=${encodeURIComponent(p.id)}">
         <b>👆 ${p.taps || 0} &nbsp; 📣 ${p.invites || 0} &nbsp; 🏪 ${p.claims || 0}</b><span class="name">افتح في الخرائط ›</span></a>`).join("")}</div>` : `<p class="muted">ما فيه بيانات للحين</p>`}
    </details>
    <h2>⛔ حسابات موقوفة (${suspended.length})</h2>
    ${suspended.length ? `<div class="grid">${suspended.map((s) => `<div class="card row" style="align-items:center;justify-content:space-between">
      <b>${esc(s.name || s.uid.slice(0, 8))}</b><button class="btn" data-act="unsuspend" data-id="${s.uid}">إلغاء الإيقاف</button></div>`).join("")}</div>` : `<p class="muted">ما فيه حسابات موقوفة ✅</p>`}`;
}

function refs() {
  const totals = {};
  state.referrals.forEach((r) => { totals[r.referrer] = (totals[r.referrer] || 0) + (r.verifiedPts || 0) + (r.offerPts || 0); });
  const board = Object.entries(totals).sort((a, b) => b[1] - a[1]).slice(0, 20);
  const list = [...state.referrals].sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0));
  return `<h2>🎁 المحلات اللي جت عن طريق دعوة (${list.length})</h2>
    <p class="muted">+${PTS.verified} لما يتوثّق/يصير موثوق، و +${PTS.offer} لما ينزل أول عرض معتمد. تأكد إن المحل حقيقي ومو نفس الشخص قبل الاعتماد.</p>
    ${list.length ? `<div class="grid">${list.map((r) => { const i = refInfo(r); return `<div class="card">
      <div class="name">🏪 ${esc(r.storeName || r.storeUid.slice(0, 8))}</div>
      <div class="muted">دعاه: <b>${esc(state.names[r.referrer] || r.referrer.slice(0, 8))}</b></div>
      <div class="muted">${i.joined ? "✅ موثوق/موثّق" : "⏳ ما توثّق للحين"} • ${i.hasOffer ? "🔥 عنده عرض معتمد" : "ما نزّل عرض"} • النقاط: <b>${(r.verifiedPts || 0) + (r.offerPts || 0)}</b></div>
      <div class="actions">
        ${r.verifiedPts ? `<span class="pill">✓ توثيق +${r.verifiedPts}</span>` : `<button class="btn ${i.canVerified ? "primary" : ""}" data-act="awardV" data-id="${r.storeUid}" ${i.joined ? "" : "disabled"}>+${PTS.verified} توثيق</button>`}
        ${r.offerPts ? `<span class="pill">✓ أول عرض +${r.offerPts}</span>` : `<button class="btn ${i.canOffer ? "primary" : ""}" data-act="awardO" data-id="${r.storeUid}" ${i.hasOffer ? "" : "disabled"}>+${PTS.offer} أول عرض</button>`}
        <button class="btn danger" data-act="removeRef" data-id="${r.storeUid}">حذف</button>
      </div></div>`; }).join("")}</div>` : `<p class="muted">ما فيه إحالات للحين</p>`}
    <h2>🏅 أكثر السفراء نقاط</h2>
    ${board.length ? `<div class="grid">${board.map(([u, p], k) => `<div class="card row" style="justify-content:space-between"><b>${k + 1}. ${esc(state.names[u] || u.slice(0, 8))}</b><span>${p} نقطة ${p >= 10000 ? "💎" : p >= 5000 ? "🥇" : p >= 1000 ? "🥈" : "🥉"}</span></div>`).join("")}</div>` : `<p class="muted">—</p>`}`;
}

// وصف كل عملية في السجل: [العملية, القسم, الهدف, التفاصيل]
function describe(act, id, b, o) {
  const f = state.feedback.find((x) => x.id === id), v = state.verifs.find((x) => x.uid === id), r = state.referrals.find((x) => x.storeUid === id);
  const fbT = f ? { type: "message", id, name: `${f.type} — ${f.userName || f.contact || "زائر"}` } : { type: "message", id };
  const D = {
    approve: () => ["اعتماد عرض", "review", offerT(o)],
    deleteOffer: () => ["حذف عرض", "review", offerT(o), o ? `السعر: ${o.price || 0} • الحي: ${o.district || ""}` : ""],
    suspendOffer: () => ["إيقاف تاجر", "merchants", uidT(o?.merchantId, o?.restaurantName), "من مراجعة عرض: " + (o?.offerTitle || "")],
    suspendUid: () => ["إيقاف تاجر", "merchants", uidT(id, b.dataset.name)],
    keep: () => ["إرجاع عرض مخفي (البلاغات غير صحيحة)", "review", offerT(o)],
    dismissAll: () => ["تجاهل بلاغات", "review", { type: "offer", id, name: state.reports.find((x) => x.offerId === id)?.offerTitle || id }],
    deleteAndDismiss: () => ["حذف عرض مُبلّغ عنه", "review", o ? offerT(o) : { type: "offer", id, name: state.reports.find((x) => x.offerId === id)?.offerTitle || id }],
    approveStore: () => ["اعتماد صفحة متجر", "merchants", { type: "store", id, name: state.pendingStores.find((x) => x.uid === id)?.name || id }],
    untrust: () => ["إلغاء الثقة بتاجر", "merchants", uidT(id)],
    unsuspend: () => ["إلغاء إيقاف تاجر", "merchants", uidT(id)],
    feature: () => ["تبديل تمييز عرض", "review", offerT(o), o ? (o.isFeatured ? "أُلغي التمييز" : "صار مميّز ⭐") : ""],
    toggleApproved: () => ["تبديل حالة عرض", "review", offerT(o), o ? (o.isApproved ? "أُوقف" : "تفعّل") : ""],
    unexcl: () => ["شيل الحصري عن عرض", "review", offerT(o), "العرض مو حصري فعلاً — انشالت الشارة (بدون خصم من الرصيد)"],
    trust: () => ["اعتماد عرض + ⚡ ينشر مباشرة", "review", offerT(o)],
    reply: () => ["رد على رسالة", "messages", fbT, ($("reply_" + id)?.value || "").slice(0, 600)],
    progress: () => ["رسالة قيد المتابعة", "messages", fbT],
    done: () => ["إنهاء رسالة", "messages", fbT],
    deleteFb: () => ["حذف رسالة", "messages", fbT, f ? f.message.slice(0, 300) : ""],
    verifyOk: () => ["توثيق متجر ✓", "verify", { type: "store", id, name: v?.storeName || id }, v ? `${v.docType === "freelance" ? "عمل حر" : "سجل تجاري"}: ${v.docNumber || "—"}` : ""],
    verifyNo: () => ["رفض توثيق", "verify", { type: "store", id, name: v?.storeName || id }, "السبب: " + ($("reason_" + id)?.value || "—")],
    awardV: () => ["نقاط إحالة: توثيق", "merchants", { type: "referral", id, name: r?.storeName || id }, `+50 لـ ${state.names[r?.referrer] || ""}`],
    awardO: () => ["نقاط إحالة: أول عرض", "merchants", { type: "referral", id, name: r?.storeName || id }, `+50 لـ ${state.names[r?.referrer] || ""}`],
    removeRef: () => ["حذف إحالة", "merchants", { type: "referral", id, name: r?.storeName || id }],
    saveSupport: () => ["تغيير رقم الدعم", "settings", {}, $("supportNum")?.value],
    saveModel: () => ["تغيير نموذج الذكاء الاصطناعي", "settings", {}, $("aiModel")?.value],
    saveCats: () => ["تعديل التصنيفات", "settings", {}, catDraft().filter((x) => x.name).map((x) => `${x.name} (${KINDS[x.type]?.label || ""})`).join("، ")],
    savePoi: () => ["تعديل رسالة الدعوة", "settings", {}],
    saveExcl: () => ["تعديل شارة الحصري", "settings", {}, `${$("exIcon")?.value} ${$("exLabel")?.value} • ${$("exOn")?.checked ? "مفعّلة" : "موقفة"} • المكافأة: ${$("exRw")?.checked ? `${$("exPct")?.value}%` : "مطفية"}`],
    saveSpot: () => ["تعديل حد الإبراز في الحي", "credits", {}, `${$("crSpot")?.value} لكل حي`],
    fixSpot: () => ["تحديث خدمة «🚀 عرض سوبر» (الاسم والنوع)", "credits", {}],
    saveWelcome: () => ["تعديل هدية الترحيب", "credits", {}, `${$("crWelcome")?.value} نقطة`],
    saveStore: () => ["تعديل رابط متجر سلة", "credits", {}, $("crStore")?.value],
    saveItems: () => ["تعديل أسعار الخدمات", "credits", {}, itemsDraft().map((x) => `${x.name}: ${x.price}${x.unit === "day" ? "/يوم" : ""}${x.active ? "" : " (موقفة)"}`).join(" • ")],
    savePacks: () => ["تعديل باقات الشحن", "credits", {}, packsDraft().map((p) => `${p.points}=${p.price}ر`).join(" • ")],
    genCodes: () => ["إنشاء أكواد شحن", "credits", {}, `${$("cdCount")?.value} كود × ${$("cdPoints")?.value} نقطة`],
    staffAdd: () => ["إضافة موظف", "team", { type: "staff", id: ($("stEmail")?.value || "").trim().toLowerCase(), name: $("stName")?.value || "" }, PERMS.filter(([k]) => $("content").querySelector(`[data-newperm="${k}"]`)?.checked).map(([, l]) => l).join("، ")],
  };
  return D[act] ? D[act]() : null;
}

// ================= 📜 السجل =================
const fmtTime = (t) => { const d = t?.toDate ? t.toDate() : t?.toMillis ? new Date(t.toMillis()) : null; if (!d) return "…"; const diff = (Date.now() - d.getTime()) / 6e4;
  return diff < 1 ? "الحين" : diff < 60 ? `قبل ${Math.round(diff)} د` : diff < 1440 ? `قبل ${Math.round(diff / 60)} س` : d.toLocaleString("ar-SA", { dateStyle: "short", timeStyle: "short" }); };
function logPage() {
  const q = state.logQuery.trim().toLowerCase();
  const actors = [...new Set(state.log.map((l) => l.actor))];
  const list = state.log.filter((l) => (state.logArea === "الكل" || l.area === state.logArea) && (state.logActor === "الكل" || l.actor === state.logActor)
    && (!q || [l.action, l.targetName, l.details, l.actor].some((v) => (v || "").toLowerCase().includes(q))));
  const staffName = (e) => e === state.me.email && state.me.owner ? "أنت" : (state.staff.find((x) => x.id === e)?.name || e);
  return `<h2>📜 سجل العمليات ${state.me.owner ? "" : "(عملياتك)"}</h2>
    <p class="muted">كل عملية في لوحة الإدارة (الويب والتطبيق) تنحفظ هنا، وما تنعدّل ولا تنحذف.</p>
    <input type="text" id="logQuery" placeholder="ابحث: اسم متجر، عرض، سبب، إيميل…" value="${esc(state.logQuery)}">
    <div class="chips">${["الكل", ...Object.keys(AREAS)].map((a) => `<button class="chip ${state.logArea === a ? "on" : ""}" data-logarea="${a}">${a === "الكل" ? "الكل" : AREAS[a]}</button>`).join("")}</div>
    ${state.me.owner && actors.length > 1 ? `<div class="chips">${["الكل", ...actors].map((a) => `<button class="chip ${state.logActor === a ? "on" : ""}" data-logactor="${esc(a)}">👤 ${esc(a === "الكل" ? "كل الفريق" : staffName(a))}</button>`).join("")}</div>` : ""}
    <p class="muted">${list.length} عملية</p>
    ${list.length ? `<div class="card"><table class="tbl log"><thead><tr><th>متى</th><th>مين</th><th>العملية</th><th>على</th><th>التفاصيل</th></tr></thead><tbody>
      ${list.slice(0, 300).map((l) => `<tr><td class="muted" title="${esc(l.at?.toDate ? l.at.toDate().toLocaleString("ar-SA") : "")}">${fmtTime(l.at)}</td>
        <td>${esc(staffName(l.actor))}${l.source === "app" ? ` <span class="pill">📱</span>` : ""}</td>
        <td><span class="area">${AREAS[l.area] || ""}</span> <b>${esc(l.action)}</b></td>
        <td>${l.thumb ? `<img class="thumb small" src="${esc(l.thumb)}" style="width:36px;height:36px;vertical-align:middle"> ` : ""}${esc(l.targetName)}</td>
        <td class="muted">${esc(l.details)}</td></tr>`).join("")}</tbody></table></div>` : `<p class="muted">ما فيه عمليات للحين</p>`}`;
}

// ================= 👥 الفريق =================
function team() {
  return `<h2>👥 فريق الإدارة</h2>
    <div class="card">
      <p class="muted">أضف موظف بإيميل Google حقه، واختر صلاحياته. يدخل من نفس الرابط (/admin) بحسابه، ويشوف التبويبات المسموحة له بس. الإعدادات والأسعار والفريق لك أنت بس.</p>
      <div class="row" style="flex-wrap:wrap;align-items:center"><input type="text" id="stEmail" dir="ltr" placeholder="email@gmail.com" style="flex:1;min-width:200px">
        <input type="text" id="stName" placeholder="الاسم" style="width:160px"></div>
      <div class="chips" style="margin-top:8px">${PERMS.map(([k, l, h]) => `<label class="chip permchk" title="${esc(h)}"><input type="checkbox" data-newperm="${k}"> ${l}</label>`).join("")}</div>
      <div class="actions"><button class="btn primary" data-act="staffAdd">＋ إضافة للفريق</button></div>
    </div>
    ${state.staff.length ? `<div class="grid" style="margin-top:12px">${state.staff.map((m) => `<div class="card">
      <div class="row" style="justify-content:space-between;align-items:center"><div><b>${esc(m.name || m.id)}</b><div class="muted" dir="ltr">${esc(m.id)}</div></div>
        <label class="switch"><input type="checkbox" data-stactive="${esc(m.id)}" ${m.active ? "checked" : ""}> ${m.active ? "نشط" : "موقوف"}</label></div>
      <div class="chips" style="margin-top:8px">${PERMS.map(([k, l, h]) => `<label class="chip permchk ${(m.perms || []).includes(k) ? "on" : ""}" title="${esc(h)}"><input type="checkbox" data-stperm="${esc(m.id)}" data-k="${k}" ${(m.perms || []).includes(k) ? "checked" : ""}> ${l}</label>`).join("")}</div>
      <div class="muted">آخر عملية: ${(() => { const l = state.log.find((x) => x.actor === m.id); return l ? `${esc(l.action)} • ${fmtTime(l.at)}` : "—"; })()}</div>
      <div class="actions"><button class="btn" data-logof="${esc(m.id)}">📜 عملياته</button><button class="btn danger" data-stdel="${esc(m.id)}">إزالة من الفريق</button></div>
    </div>`).join("")}</div>` : `<p class="muted">ما فيه موظفين للحين</p>`}`;
}

// ================= 💰 الرصيد =================
const CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const newCode = () => { const a = crypto.getRandomValues(new Uint8Array(8)); const c = [...a].map((x) => CODE_CHARS[x % CODE_CHARS.length]).join(""); return `HY-${c.slice(0, 4)}-${c.slice(4)}`; };
const itemsDraft = () => state.itemsDraft || (state.itemsDraft = itemList(creditsOf(state.config).items).map((x) => ({ ...x })));
const packsDraft = () => state.packsDraft || (state.packsDraft = creditsOf(state.config).packs.map((x) => ({ ...x })));
const nameOfUid = (uid) => (state.names[uid] && state.names[uid] !== "…" ? state.names[uid] : statusOf(uid)?.name) || "…";
function jumpNav(items) { return `<div class="jump">${items.map(([id, l]) => `<a href="#${id}">${l}</a>`).join("")}</div>`; }

function credits() {
  const cr = creditsOf(state.config), items = itemsDraft(), packs = packsDraft();
  const total = state.wallets.reduce((a, w) => a + (w.balance || 0), 0);
  const codes = state.codes;
  const batches = {};
  (codes || []).forEach((c) => { const b = (batches[c.batch || "—"] ||= { name: c.batch || "—", points: c.points, total: 0, used: 0, at: c.createdAt }); b.total++; if (c.redeemedBy) b.used++; });
  const owner = state.me.owner;
  return jumpNav(owner ? [["cr-status", "الحالة"], ["cr-items", "الخدمات والأسعار"], ["cr-packs", "باقات الشحن"], ["cr-codes", "الأكواد"], ["cr-wallets", "أرصدة التجار"]] : [["cr-codes", "الأكواد"], ["cr-wallets", "أرصدة التجار"]]) + (owner ? `
    <h2 id="cr-status">💰 نظام رصيد العروض</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="crOn" ${cr.enabled ? "checked" : ""}> <b>${cr.enabled ? "مفعّل — النشر الجديد يحتاج رصيد" : "موقف — النشر مجاني للجميع"}</b></label>
      <div class="row" style="align-items:center;margin-top:8px"><span>⏳ فترة السماح للعروض المنشورة قبل التشغيل</span>
        <select id="crGrace">${[0, 7, 14, 30].map((d) => `<option value="${d}" ${cr.graceDays === d ? "selected" : ""}>${d ? d + " يوم" : "بدون سماح"}</option>`).join("")}</select>
        ${cr.enabled && cr.graceUntil ? `<span class="pill">${cr.graceUntil.toMillis() > Date.now() ? `السماح لين ${new Date(cr.graceUntil.toMillis()).toLocaleDateString("ar-SA-u-ca-gregory")}` : "انتهى السماح"}</span>` : ""}</div>
      <p class="muted">كل مرة تشغّله: العروض المنشورة قبلها تبقى ظاهرة بدون رصيد. تقدر تشغّله للتجربة وتوقفه بعدها بدون ما يأثر.</p>
      <div class="row" style="align-items:center;margin-top:8px"><span>🚀 أقصى عدد «عرض سوبر» في نفس الحي بنفس الوقت</span>
        <input type="number" id="crSpot" min="1" max="20" value="${cr.spotlightMax}" style="width:70px">
        <button class="btn primary" data-act="saveSpot">حفظ</button></div>
      ${itemList(cr.items).some((x) => /إبراز|سوبر/.test(x.name) && (x.effect !== "spotlight" || /إبراز/.test(x.name))) ? `<div class="note" style="margin-top:8px">⚠️ الاسم الجديد للخدمة «🚀 عرض سوبر» (بدل «إبراز»). <button class="btn" data-act="fixSpot">حدّث الاسم والنوع</button></div>` : ""}
      <div class="row" style="align-items:center;margin-top:8px"><span>🎁 هدية ترحيب لكل تاجر جديد</span>
        <input type="number" id="crWelcome" min="0" max="1000" value="${cr.welcome}" style="width:90px"> نقطة
        <button class="btn primary" data-act="saveWelcome">حفظ</button></div>
      <div style="margin-top:12px"><div>🛒 رابط متجر سلة <span class="muted">(يظهر للتاجر في الويب فقط، زر «اشترِ رصيد»)</span></div>
        <div class="row" style="align-items:center;margin-top:6px"><input type="url" id="crStore" dir="ltr" value="${esc(state.config.credits?.storeUrl || "")}" placeholder="https://salla.sa/..." style="flex:1;min-width:0">
        <button class="btn primary" data-act="saveStore">حفظ</button></div></div>
      <div class="stats" style="margin-top:12px">
        <div class="card stat"><div class="v">${state.wallets.length}</div><div class="t">محافظ تجار</div></div>
        <div class="card stat"><div class="v">${total}</div><div class="t">نقاط في أرصدة التجار</div></div>
      </div>
    </div>

    <h2 id="cr-items">🧾 الخدمات والأسعار</h2>
    <div class="card">
      <p class="muted">غيّر أي سعر وقت ما تبي، ويطبّق على أي شراء بعد الحفظ (المشتريات السابقة ما تتأثر). «يومي» = السعر × عدد الأيام اللي يختارها التاجر، «ثابت» = سعر واحد لمدة محددة.</p>
      <table class="tbl"><thead><tr><th>الخدمة (الاسم اللي يشوفه التاجر)</th><th>النوع</th><th>التسعير</th><th>المدة</th><th>السعر (نقطة)</th><th>مفعّلة</th><th></th></tr></thead><tbody>
      ${items.map((it, i) => `<tr>
        <td><input type="text" data-it="${i}" data-k="name" value="${esc(it.name)}" maxlength="40"></td>
        <td><select data-it="${i}" data-k="effect" ${it.id === "publish" ? "disabled" : ""}>${Object.entries(EFFECTS).map(([k, e]) => `<option value="${k}" ${it.effect === k ? "selected" : ""}>${e.label}</option>`).join("")}</select></td>
        <td><select data-it="${i}" data-k="unit"><option value="day" ${it.unit === "day" ? "selected" : ""}>يومي</option><option value="fixed" ${it.unit !== "day" ? "selected" : ""}>ثابت</option></select></td>
        <td>${it.unit === "day" ? `<span class="muted">يختارها التاجر</span>` : `<input type="number" min="1" max="90" data-it="${i}" data-k="days" value="${it.days || 1}" style="width:64px"> يوم`}</td>
        <td><input type="number" min="1" max="10000" data-it="${i}" data-k="price" value="${it.price}" style="width:80px"></td>
        <td><input type="checkbox" data-it="${i}" data-k="active" ${it.active ? "checked" : ""}></td>
        <td><button class="btn" data-itup="${i}" ${i === 0 ? "disabled" : ""}>↑</button>${it.id === "publish" ? "" : ` <button class="btn danger" data-itdel="${i}">حذف</button>`}</td></tr>`).join("")}
      </tbody></table>
      <p class="muted">${Object.entries(EFFECTS).map(([, e]) => `<b>${e.label}:</b> ${e.hint}`).join(" • ")}</p>
      <div class="actions"><button class="btn" data-act="itAdd">＋ خدمة جديدة</button><button class="btn" data-act="itReset">رجّع الافتراضي</button>
        <button class="btn primary" data-act="saveItems">حفظ الأسعار</button></div>
    </div>

    <h2 id="cr-packs">📦 باقات الشحن (تبيعها في سلة)</h2>
    <div class="card">
      <p class="muted">للعرض والتنظيم: نفس الباقات اللي تحطها كمنتجات في سلة، وتختار منها وقت توليد الأكواد.</p>
      ${packs.map((p, i) => `<div class="row" style="align-items:center;margin:4px 0"><input type="number" min="1" data-pk="${i}" data-k="points" value="${p.points}" style="width:90px"> نقطة بـ
        <input type="number" min="1" data-pk="${i}" data-k="price" value="${p.price}" style="width:90px"> ريال
        <span class="muted">(${(p.price / p.points).toFixed(2)} ريال/نقطة)</span><button class="btn danger" data-pkdel="${i}">حذف</button></div>`).join("")}
      <div class="actions"><button class="btn" data-act="pkAdd">＋ باقة</button><button class="btn primary" data-act="savePacks">حفظ الباقات</button></div>
    </div>` : "") + `

    <h2 id="cr-codes">🔑 أكواد الشحن</h2>
    <div class="card">
      <div class="row" style="align-items:center;flex-wrap:wrap">
        <span>قيمة الكود</span><select id="cdPoints">${packs.map((p) => `<option value="${p.points}">${p.points} نقطة (${p.price} ريال)</option>`).join("")}</select>
        <span>العدد</span><input type="number" id="cdCount" min="1" max="300" value="20" style="width:80px">
        <span>صلاحية</span><select id="cdExp"><option value="12">12 شهر</option><option value="6">6 شهور</option><option value="0">بدون انتهاء</option></select>
        <button class="btn primary" data-act="genCodes">إنشاء الأكواد</button></div>
      <p class="muted">بعد الإنشاء: «نسخ» الأكواد غير المستخدمة وألصقها في منتج سلة الرقمي (كل كود في سطر).</p>
      ${codes === null ? `<div class="actions"><button class="btn" data-act="loadCodes">عرض الأكواد</button></div>`
        : Object.values(batches).length ? `<table class="tbl"><thead><tr><th>الدفعة</th><th>القيمة</th><th>المستخدم</th><th></th></tr></thead><tbody>
          ${Object.values(batches).sort((a, b) => (b.at?.toMillis?.() || 0) - (a.at?.toMillis?.() || 0)).map((b) => `<tr><td>${esc(b.name)}</td><td>${b.points} نقطة</td><td>${b.used} / ${b.total}</td>
            <td><button class="btn" data-copyb="${esc(b.name)}">نسخ غير المستخدمة</button> <button class="btn" data-csvb="${esc(b.name)}">تنزيل</button> <button class="btn danger" data-delb="${esc(b.name)}">حذف غير المستخدمة</button></td></tr>`).join("")}
          </tbody></table>` : `<p class="muted">ما فيه أكواد للحين</p>`}
    </div>

    <h2 id="cr-wallets">👛 أرصدة التجار</h2>
    <p class="muted">تعديل يدوي للرصيد (تعويض، هدية، تصحيح خطأ): اكتب عدد النقاط — رقم عادي للإضافة (20) أو بالسالب للخصم (‎-10) — واكتب السبب، يظهر للتاجر في «الحركات».</p>
    ${state.wallets.length ? (() => {
      const q = (state.walletQuery || "").trim().toLowerCase();
      const all = [...state.wallets].sort((a, b) => (b.balance || 0) - (a.balance || 0));
      const hits = q ? all.filter((w) => [nameOfUid(w.id), w.id, state.storeCode?.[w.id] || ""].join(" ").toLowerCase().includes(q)) : all;
      const shown = hits.slice(0, state.walletShow || 50);
      return `<div class="card"><div class="row" style="align-items:center;flex-wrap:wrap;margin-bottom:10px">
        <input type="search" id="walletQuery" placeholder="ابحث باسم المتجر أو صاحبه أو الجوال أو رقم المتجر…" value="${esc(state.walletQuery || "")}" style="flex:1;min-width:220px">
        <span class="muted">${q ? `${hits.length} من ${all.length}` : `${all.length} محفظة`}</span></div>
      ${shown.length ? `<table class="tbl"><thead><tr><th>التاجر</th><th>الرصيد</th><th>تعديل يدوي</th></tr></thead><tbody>
      ${shown.map((w) => `<tr><td><b>${esc(nameOfUid(w.id))}</b>${state.storeCode?.[w.id] ? ` <span class="pill">${esc(state.storeCode[w.id])}</span>` : ""}</td><td><b>${w.balance || 0}</b> نقطة</td>
        <td><input type="number" id="adj_${w.id}" placeholder="النقاط: 20 أو ‎-10" style="width:130px"> <input type="text" id="adjn_${w.id}" placeholder="السبب، مثل: تعويض عن خطأ" style="width:200px">
        <button class="btn" data-adj="${w.id}">تطبيق التعديل</button></td></tr>`).join("")}</tbody></table>
      ${hits.length > shown.length ? `<div class="actions" style="justify-content:center"><button class="btn" data-walletmore>عرض ${Math.min(50, hits.length - shown.length)} زيادة (باقي ${hits.length - shown.length})</button></div>` : ""}`
      : `<p class="muted">ما لقينا متجر بهالبحث</p>`}</div>`;
    })() : `<p class="muted">ما فيه محافظ للحين</p>`}`;
}
async function adminCredit(uid, delta, note) {
  const ref = doc(db, "wallets", uid), snap = await getDoc(ref);
  const bal = (snap.data()?.balance || 0) + delta;
  if (bal < 0) throw new Error("الرصيد ما يصير بالسالب");
  await setDoc(ref, { balance: bal, lastOp: { kind: "admin", ref: "" }, updatedAt: serverTimestamp() });
  await addDoc(collection(db, "wallets", uid, "tx"), { kind: "admin", points: delta, note: note || (delta > 0 ? "إضافة من إدارة لكوفر" : "خصم من إدارة لكوفر"), ref: "", at: serverTimestamp() });
}
async function loadCodes() { state.codes = (await getDocs(collection(db, "creditCodes"))).docs.map((d) => ({ id: d.id, ...d.data() })); render(); }
const unusedOf = (batch) => (state.codes || []).filter((c) => (c.batch || "—") === batch && !c.redeemedBy);

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
    ({ "نشط": o.isApproved && !o.draft && !isExpired(o), "بانتظار المراجعة": !o.isApproved && !o.draft, "منتهي": isExpired(o), "مميز ⭐": o.isFeatured, "💎 حصري": o.exclusive, "📝 مسودات": o.draft }[state.offerFilter] ?? true)
  ).sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0));
  return `<input type="text" id="offerQuery" placeholder="ابحث باسم المتجر أو العرض أو الحي…" value="${esc(state.offerQuery)}">
    <div class="chips">${["الكل", "نشط", "بانتظار المراجعة", "منتهي", "مميز ⭐", "💎 حصري", "📝 مسودات"].map((t) => `<button class="chip ${state.offerFilter === t ? "on" : ""}" data-ofilter="${esc(t)}">${esc(t)}</button>`).join("")}</div>
    <p class="muted">${list.length} عرض</p>
    <div class="grid">${list.map((o) => offerCard(o,
      `<div style="margin-top:6px"><span class="pill ${o.isApproved ? "" : "off"}">${o.isApproved ? "نشط" : "معلق"}</span>
       ${isExpired(o) ? `<span class="pill off">منتهي</span>` : ""} ${o.isFeatured ? `<span class="pill">مميز ⭐</span>` : ""}
       ${o.exclusive ? `<span class="pill" style="background:#EDE4FF;color:#6D28D9">💎 حصري</span>` : ""} ${o.adminNote ? `<div class="note">↩️ مرجّع للتاجر: ${esc(o.adminNote)}</div>` : ""} ${o.draft ? `<span class="pill off">📝 مسودة (بانتظار رصيد التاجر)</span>` : ""}</div>`,
      `<button class="btn" data-act="feature" data-id="${o.id}">${o.isFeatured ? "إلغاء التمييز" : "⭐ تمييز"}</button>
       <button class="btn" data-act="toggleApproved" data-id="${o.id}">${o.isApproved ? "إيقاف" : "تفعيل"}</button>
       ${o.adminNote ? "" : `<button class="btn" data-ret="offer" data-id="${o.id}">↩️ رجّعه</button>`}
       ${o.exclusive ? `<button class="btn" data-act="unexcl" data-id="${o.id}" title="لو العرض مو حصري فعلاً">💎 شيل الحصري</button>` : ""}
       <button class="btn danger" data-act="deleteOffer" data-id="${o.id}">حذف</button>`)).join("")}</div>`;
}

const DEFAULT_CATS = ["مطاعم 🍽️", "مقاهي وحلويات ☕", "سوبرماركت وبقالة 🛒", "ملابس وأزياء 👕", "خياطة ومشاغل 🧵",
  "نظارات وبصريات 👓", "عطور وتجميل 💄", "جوالات وإلكترونيات 📱", "حلاقة وصالونات 💈", "خدمات أخرى 🧰"];
function catDraft() {
  if (!state.catDraft) state.catDraft = ((state.config.categories && state.config.categories.length) ? state.config.categories : DEFAULT_CATS).map((c) => ({ orig: c, name: c, type: baseKindOf(state.config, c) }));
  return state.catDraft;
}
// =================== 🏪 كل المتاجر ===================
function allStoresHtml() {
  const st = (uid) => statusOf(uid) || {};
  const q = state.storeQuery.trim().toLowerCase();
  const F = {
    "الكل": () => true,
    "✔ موثّق": (s) => s.verified || st(s.uid).verified,
    "غير موثّق": (s) => !(s.verified || st(s.uid).verified),
    "⚡ ينشر مباشرة": (s) => st(s.uid).trusted && !st(s.uid).suspended,
    "بانتظار الموافقة": (s) => !s.approved,
    "⛔ موقوف": (s) => st(s.uid).suspended,
  };
  const list = state.storesAll.filter((s) => (F[state.storeFilter] || F["الكل"])(s)
    && (!q || [s.name, s.category, s.phone, (state.storeCode || {})[s.uid], ...(s.branches || []).map((b) => b.district)].some((v) => String(v || "").toLowerCase().includes(q))))
    .sort((a, b) => (b.updatedAt?.toMillis?.() || 0) - (a.updatedAt?.toMillis?.() || 0));
  const wa = (p) => String(p || "").replace(/\D/g, "").replace(/^05/, "9665").replace(/^5(\d{8})$/, "9665$1");
  return `<h2>🏪 كل المتاجر (${state.storesAll.length})</h2>
    <input type="text" id="storeQuery" placeholder="ابحث بالاسم أو التصنيف أو الجوال أو رقم المتجر أو الحي…" value="${esc(state.storeQuery)}">
    <div class="chips">${Object.keys(F).map((k) => `<button class="chip ${state.storeFilter === k ? "on" : ""}" data-sfilter="${esc(k)}">${esc(k)} (${state.storesAll.filter(F[k]).length})</button>`).join("")}</div>
    <div class="grid">${list.slice(0, 150).map((s) => { const t = st(s.uid), offers = state.offers.filter((o) => o.merchantId === s.uid).length, items = state.items.filter((i) => i.merchantId === s.uid).length;
      return `<div class="card"><div class="row" style="align-items:center;gap:10px">
        ${s.logoUrl ? `<img class="thumb small" src="${esc(img(s.logoUrl, 150))}" style="border-radius:50%">` : `<div class="thumb small" style="display:flex;align-items:center;justify-content:center;border-radius:50%">🏪</div>`}
        <div style="flex:1;min-width:0"><div class="name">${esc(s.name || "—")}</div>
          <div class="muted">${esc(s.category || "")}${s.phone ? " • " + esc(s.phone) : ""}${(s.branches || []).length ? ` • ${(s.branches || []).length} فرع` : ""}</div>
          <div class="muted">🔢 رقم المتجر: ${(state.storeCode || {})[s.uid] ? `<b style="font-family:Montserrat,monospace;letter-spacing:2px;color:var(--ink,#111)">${esc(state.storeCode[s.uid])}</b> <button class="btn" style="padding:2px 8px;font-size:12px;display:inline-flex;width:auto" data-copy="${esc(state.storeCode[s.uid])}">نسخ</button>` : "ما أخذ رقم للحين (يتولّد لما يفتح «موظفين المتجر»)"}</div>
          <div style="margin-top:4px">${s.verified || t.verified ? `<span class="pill">✔ موثّق</span>` : `<span class="pill off">غير موثّق</span>`}
            ${t.trusted && !t.suspended ? `<span class="pill">⚡ ينشر مباشرة</span>` : ""} ${!s.approved ? `<span class="pill off">⏳ صفحته بانتظار الموافقة</span>` : ""} ${t.suspended ? `<span class="pill off">⛔ موقوف</span>` : ""}
            <span class="muted">🔥 ${offers} عرض • 🛍️ ${items} منتج</span></div></div></div>
        <div class="actions"><a class="btn" href="/s/${encodeURIComponent(s.uid)}" target="_blank" rel="noopener">👀 صفحته</a>
          ${s.phone ? `<a class="btn" href="https://wa.me/${wa(s.phone)}" target="_blank" rel="noopener">💬 واتساب</a>` : ""}
          ${!s.approved ? `<button class="btn primary" data-act="approveStore" data-id="${s.uid}">اعتماد الصفحة</button>` : ""}
          ${t.suspended ? `<button class="btn" data-act="unsuspend" data-id="${s.uid}">إلغاء الإيقاف</button>` : `<button class="btn danger" data-act="suspendUid" data-id="${s.uid}" data-name="${esc(s.name)}">⛔ إيقاف</button>`}</div></div>`; }).join("")}</div>
    ${list.length > 150 ? `<p class="muted">يعرض أول 150 — استخدم البحث</p>` : ""}`;
}

// =================== ↩️ رجّعه للتاجر (مع السبب) ===================
const RETURN_REASONS = ["📷 أعد رفع الصورة (غير واضحة أو ناقصة)", "💰 السعر غير واضح أو غير صحيح", "✏️ الاسم أو العنوان غير مناسب", "📝 الوصف ناقص", "🏷️ التصنيف غير صحيح"];
function returnToMerchant(type, id) {
  const isItem = type === "item";
  const x = isItem ? state.items.find((i) => i.id === id) : state.offers.find((o) => o.id === id);
  if (!x) return;
  const title = isItem ? x.name : x.offerTitle, uid = x.merchantId;
  const store = (state.storesAll || []).find((s) => s.uid === uid);
  const phone = String((isItem ? store?.phone : x.phone) || store?.phone || "").replace(/\D/g, "").replace(/^05/, "9665").replace(/^5(\d{8})$/, "9665$1");
  const box = document.createElement("div");
  box.className = "modal";
  box.innerHTML = `<div class="modal-card"><h3>↩️ رجّعه للتاجر</h3><p class="muted">«${esc(title)}» يبقى مخفي، والتاجر يشوف السبب ويعدّل، وبعدها يرجع لك للمراجعة.</p>
    <div class="chips">${RETURN_REASONS.map((r, i) => `<button class="chip" data-r="${i}">${esc(r)}</button>`).join("")}</div>
    <textarea id="retText" rows="3" maxlength="300" placeholder="السبب (يظهر للتاجر)"></textarea>
    ${phone ? `<label class="switch"><input type="checkbox" id="retWa" checked> أرسل له الملاحظة على واتساب بعد</label>` : ""}
    <div class="actions"><button class="btn primary" id="retGo">رجّعه</button><button class="btn" id="retNo">إلغاء</button></div></div>`;
  document.body.appendChild(box);
  const close = () => box.remove();
  box.querySelectorAll("[data-r]").forEach((b) => (b.onclick = () => { $("retText").value = RETURN_REASONS[+b.dataset.r]; }));
  $("retNo").onclick = close; box.onclick = (e) => { if (e.target === box) close(); };
  $("retGo").onclick = () => {
    const note = $("retText").value.trim().slice(0, 300);
    if (!note) { toast("اكتب السبب أو اختر واحد"); return; }
    const wa = $("retWa")?.checked;
    const w = wa ? window.open("", "_blank") : null;
    run(async () => {
      await updateDoc(doc(db, isItem ? "items" : "offers", id), isItem ? { approved: false, adminNote: note } : { isApproved: false, adminNote: note });
      await audit(isItem ? "إرجاع منتج للتاجر" : "إرجاع عرض للتاجر", isItem ? "catalog" : "review", isItem ? { type: "item", id, name: title } : offerT(x), note);
      close();
      const msg = `السلام عليكم 👋 من فريق لكوفر\nبخصوص ${isItem ? "منتجك" : "عرضك"} «${title}»:\n${note}\n\nعدّله من «حسابي» في لكوفر، وبنراجعه مرة ثانية على طول 🌿`;
      if (w) w.location = `https://wa.me/${phone}?text=${encodeURIComponent(msg)}`;
    }, "رجّعناه للتاجر ✅", "رجّعه للتاجر");
  };
}

// =================== 🛍️ المنتجات والخدمات والمنيو ===================
const DAYMS = 864e5;
function catalogPage() {
  const cfg = state.config, cat = catalogOf(cfg), vat = vatOf(cfg), owner = !!state.me.owner, now = Date.now();
  const pending = state.items.filter((x) => !x.approved && !x.adminNote);
  const storeName = (uid) => nameOfUid(uid);
  const kindChip = (k) => `<span class="pill" style="background:${KINDS[k]?.bg};color:${KINDS[k]?.color}">${KINDS[k]?.icon} ${KINDS[k]?.label}</span>`;
  const itemCard = (x, buttons) => `<div class="card"><div class="row" style="align-items:center;gap:10px">
      ${x.imageUrl ? `<img class="thumb" src="${esc(img(x.imageUrl, 160))}" data-view="${esc(x.imageUrl)}" alt="">` : `<div class="thumb" style="display:flex;align-items:center;justify-content:center;font-size:28px">${KINDS[x.kind]?.icon || "🛍️"}</div>`}
      <div style="flex:1;min-width:0"><b>${esc(x.name)}</b> ${kindChip(x.kind)} ${x.available === false ? `<span class="pill off">نفد</span>` : ""}
        <div class="muted">${esc(storeName(x.merchantId))} • ${esc(x.price)} ر.س${x.section ? " • " + esc(x.section) : ""}</div>
        ${x.desc ? `<div class="muted">${esc(x.desc).slice(0, 160)}</div>` : ""}
        ${x.adminNote ? `<div class="note">↩️ ${esc(x.adminNote)}</div>` : ""}</div></div>
      <div class="actions">${buttons}</div></div>`;
  // فلترة كل المنتجات
  const q = state.itemQuery.trim().toLowerCase();
  const status = (x) => x.adminNote ? "↩️ مرجّعة للتاجر" : !x.approved ? "بانتظار المراجعة" : x.available === false ? "نفد" : !itemPaidUp(x, state.passes, cat, now) ? "غير مدفوع" : "ظاهر";
  const all = state.items.filter((x) => (!q || [x.name, storeName(x.merchantId), x.section].some((v) => String(v || "").toLowerCase().includes(q)))
    && (state.itemFilter === "الكل" || status(x) === state.itemFilter || (KINDS[state.itemFilter.split(" ")[1]] && x.kind === state.itemFilter.split(" ")[1])))
    .sort((a, b) => (b.updatedAt?.toMillis?.() || 0) - (a.updatedAt?.toMillis?.() || 0));
  // إحصائيات
  const stores = new Set(state.items.map((x) => x.merchantId)).size;
  const byKind = KIND_IDS.map((k) => [k, state.items.filter((x) => x.kind === k).length]);
  const ord = state.orders, dayAgo = now - DAYMS, weekAgo = now - 7 * DAYMS;
  const oms = (o) => o.createdAt?.toMillis?.() || 0;
  const ordSum = (list) => list.filter((o) => o.status !== "cancelled").reduce((a, o) => a + (o.total || 0), 0);
  const pr = cat.pricing;
  const priceRow = (key, label, unit) => `<tr><td>${label}</td>
      <td><input type="number" id="ctp_${key}" min="0.05" step="0.05" value="${riyal(pr[key].priceH)}" style="width:90px"> ر.س ${unit}</td>
      <td><label class="switch"><input type="checkbox" id="ctpa_${key}" ${pr[key].active ? "checked" : ""}> مفعّل</label></td></tr>`;
  const nav = owner ? [["ct-status", "الخدمة"], ["ct-feat", "مزايا الطلب"], ["ct-noshow", "ما انستلم"], ["ct-price", "التسعير"], ["ct-vat", "الضريبة"], ["ct-review", "المراجعة"], ["ct-all", "كل المنتجات"], ["ct-stats", "الطلبات"], ["ct-rates", "التقييمات"]]
    : [["ct-review", "المراجعة"], ["ct-all", "كل المنتجات"], ["ct-stats", "الطلبات"], ["ct-rates", "التقييمات"]];
  // جدول الطلبات: فلترة + توزيع حسب الاستلام والدفع
  const ordF = ord.filter((o) => (state.ordFilter === "الكل" || o.status === state.ordFilter) && (!state.ordStore || o.storeId === state.ordStore));
  const cnt = (f) => ord.filter(f).length, live = (o) => o.status !== "cancelled";
  const rq = state.rateQuery.trim().toLowerCase();
  const rates = state.ratings.filter((r) => (state.rateFilter === "الكل" || String(r.stars) === state.rateFilter) && (!rq || [r.text, r.name, nameOfUid(r.storeId)].some((v) => String(v || "").toLowerCase().includes(rq))));
  const avgStars = state.ratings.length ? (state.ratings.reduce((a, r) => a + (r.stars || 0), 0) / state.ratings.length).toFixed(1) : "—";
  return jumpNav(nav) + (owner ? `
    <h2 id="ct-status">🛍️ خدمة المنتجات والخدمات والمنيو</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" data-catcfg="enabled" ${cat.enabled ? "checked" : ""}> <b>تشغيل الخدمة</b></label>
      <p class="muted">${cat.enabled ? "✅ شغّالة: التاجر يضيف منتجاته، وتظهر للزبون في صفحة المتجر." : "⛔ مطفية: ما يظهر شي عند التاجر ولا الزبون، والأمور مثل قبل. البيانات محفوظة وترجع لما تشغّلها."}</p>
      <label class="switch"><input type="checkbox" data-catcfg="orderEnabled" ${cat.orderEnabled ? "checked" : ""}> 🧾 الطلب عبر واتساب (السلة + ملخص الطلب)</label>
      <label class="switch"><input type="checkbox" data-catcfg="review" ${cat.review ? "checked" : ""}> ⏳ منتجات التاجر الجديد تنراجع قبل ما تظهر (الموثّق ✔ واللي ⚡ ينشر مباشرة تنزل على طول)</label>
      <h3 style="margin:14px 0 6px">الحد الأقصى لكل تاجر</h3>
      <div class="row" style="flex-wrap:wrap;gap:14px">${KIND_IDS.map((k) => `<div class="row" style="align-items:center;gap:6px">${KINDS[k].icon} ${KINDS[k].label}
        <input type="number" id="ctl_${k}" min="1" max="999" value="${limitOf(cat, k) || ""}" ${limitOf(cat, k) ? "" : "disabled"} style="width:80px">
        <label class="switch"><input type="checkbox" data-ctopen="${k}" ${limitOf(cat, k) ? "" : "checked"}> مفتوح</label></div>`).join("")}</div>
      <p class="muted">لو نزّلت الحد، المنتجات اللي فوقه تختفي (ما تنحذف) وترجع لو رفعته.</p>
      <div class="actions"><button class="btn primary" data-cact="saveLimits">حفظ الحدود</button></div>
    </div>

    <h2 id="ct-feat">⚙️ مزايا الطلب</h2>
    <div class="card">
      <p class="muted">كل ميزة تقدر توقفها لكل المنصة. لو وقفتها تختفي عند التاجر والزبون، والبيانات تبقى محفوظة.</p>
      ${Object.entries(FEATURES).map(([k, l]) => `<label class="switch"><input type="checkbox" data-feat="${k}" ${feat(cfg, k) ? "checked" : ""}> ${esc(l)}</label>`).join("")}
      <h3 style="margin:14px 0 6px">🧾 متابعة الطلب عند العميل</h3>
      <p class="muted">لما يكون عند العميل طلب شغّال، يشوف حالته في كل الشاشات بدون ما يفتح «طلباتي». يتلوّن أخضر ويهتز الجوال لما يصير جاهز، وزر «🚗 وصلت» يطلع عليه في الاستلام من السيارة.</p>
      <div class="row" style="flex-wrap:wrap;gap:8px">${[["bar", "▬ شريط أسفل الشاشة"], ["bubble", "● دائرة عائمة"], ["off", "⛔ مقفل"]].map(([v, l]) =>
        `<label class="switch"><input type="radio" name="trackerMode" value="${v}" ${(["bar", "bubble", "off"].includes(cat.tracker) ? cat.tracker : "bar") === v ? "checked" : ""}> ${l}</label>`).join("")}</div>
    </div>

    <h2 id="ct-noshow">🚫 الطلبات اللي ما انستلمت</h2>
    <div class="card">
      <p class="muted">لما المحل يحط «ما انستلم» تنحسب على العميل. لو وصل للحد خلال الفترة، يتوقف عن الطلب مؤقتاً. المحل يشوف جنب الطلب الجديد «⚠️ عنده X طلبات ما استلمها».</p>
      <div class="row" style="flex-wrap:wrap;gap:12px;align-items:center">
        <label>الحد <input type="number" id="nsMax" min="1" max="20" value="${noshowOf(cfg).max}" style="width:70px"> طلبات</label>
        <label>خلال <input type="number" id="nsDays" min="1" max="365" value="${noshowOf(cfg).days}" style="width:80px"> يوم</label>
        <label>الإيقاف <input type="number" id="nsBlock" min="1" max="90" value="${noshowOf(cfg).blockDays}" style="width:70px"> أيام</label>
        <button class="btn" id="nsSave">حفظ</button>
      </div>
      ${(() => { const list = (state.custStats || []).filter((x) => (x.count || 0) > 0).sort((a, b) => (b.updatedAt?.toMillis?.() || 0) - (a.updatedAt?.toMillis?.() || 0));
        const nameOf = (uid) => { const o = state.orders.find((y) => y.customerUid === uid); return o ? `${o.customerName || ""} ${o.customerPhone || ""}`.trim() : uid; };
        return list.length ? `<table class="tbl" style="margin-top:10px"><thead><tr><th>العميل</th><th>ما استلم</th><th>الحالة</th><th></th></tr></thead><tbody>
          ${list.slice(0, 100).map((x) => { const until = blockedUntil(x, cfg); return `<tr><td>${esc(nameOf(x.id))}</td><td>${noshowCount(x, cfg)} <small class="muted">(من ${x.count})</small></td>
            <td>${until ? `🚫 موقوف لين ${new Date(until).toLocaleDateString("ar-SA-u-ca-gregory")}` : "—"}</td><td><button class="btn" data-nsforgive="${esc(x.id)}">سامحه وصفّر</button></td></tr>`; }).join("")}</tbody></table>`
          : `<p class="muted" style="margin-top:10px">ما فيه عملاء عليهم طلبات ما انستلمت 👍</p>`; })()}
    </div>

    <h2 id="ct-price">💰 تسعير ظهور المنتجات (بالنقاط من رصيد العروض)</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="ctPrOn" ${pr.enabled ? "checked" : ""}> <b>تفعيل التسعير</b> (مطفي = المنتجات مجانية)</label>
      <div class="row" style="align-items:center;margin-top:6px">⏳ فترة سماح للمنتجات الموجودة قبل التفعيل:
        <select id="ctGrace">${[0, 7, 14, 30].map((d) => `<option value="${d}" ${(pr.graceDays ?? 14) === d ? "selected" : ""}>${d ? d + " يوم" : "بدون سماح"}</option>`).join("")}</select>
        ${pr.enabled && pr.graceUntil?.toMillis ? `<span class="pill">${pr.graceUntil.toMillis() > now ? "السماح لين " + new Date(pr.graceUntil.toMillis()).toLocaleDateString("ar-SA-u-ca-gregory") : "انتهى السماح"}</span>` : ""}</div>
      <table class="tbl" style="margin-top:10px"><thead><tr><th>الطريقة</th><th>السعر باليوم</th><th></th></tr></thead><tbody>
        ${priceRow("menu", "🍽️ صنف منيو", "/يوم لكل صنف")}${priceRow("product", "🛍️ منتج", "/يوم لكل منتج")}${priceRow("service", "✂️ خدمة", "/يوم لكل خدمة")}
        ${priceRow("all", "📦 باقة كل المنتجات", "/يوم (كل منتجات التاجر)")}</tbody></table>
      <p class="muted">التاجر يختار: يدفع لمنتجات محددة، أو الباقة لكل منتجاته — المدة من يوم إلى سنة. الإجمالي يتقرّب لفوق (مثال: 3 منتجات × 7 أيام × 0.5 = 10.5 ← 11 نقطة). اللي تنتهي مدته يختفي ويرجع بالتمديد.</p>
      <div class="actions"><button class="btn primary" data-cact="savePrices">حفظ الأسعار</button></div>
    </div>

    <h2 id="ct-vat">🧾 ضريبة القيمة المضافة</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="vatOn" ${vat.enabled ? "checked" : ""}> إظهار تفصيل الضريبة في ملخص الطلب</label>
      <div class="row" style="align-items:center;margin-top:6px">النسبة <input type="number" id="vatRate" min="0" max="100" step="0.5" value="${vat.rate}" style="width:80px"> %</div>
      <p class="muted">الأسعار دايماً <b>شاملة الضريبة</b> (23 = 20 + 3). التفصيل يطلع بس للمحل اللي كتب <b>رقمه الضريبي</b> في صفحة متجره — المحلات غير المسجّلة (والأسر المنتجة) يطلع لها الإجمالي بدون ضريبة. الملخص اسمه «ملخص الطلب» مو «فاتورة ضريبية» رسمية.</p>
      <div class="actions"><button class="btn primary" data-cact="saveVat">حفظ</button></div>
    </div>` : "") + `

    <h2 id="ct-review">⏳ بانتظار المراجعة (${pending.length})</h2>
    ${pending.length ? `<div class="grid">${pending.map((x) => itemCard(x, `<button class="btn primary" data-cact="itApprove" data-id="${esc(x.id)}">اعتماد</button><button class="btn" data-ret="item" data-id="${esc(x.id)}">↩️ رجّعه للتاجر</button><button class="btn danger" data-cact="itDelete" data-id="${esc(x.id)}">حذف</button>`)).join("")}</div>` : `<p class="muted">لا يوجد ✅</p>`}

    <h2 id="ct-all">📋 كل المنتجات (${state.items.length})</h2>
    <input type="text" id="itemQuery" placeholder="ابحث باسم المنتج أو المحل…" value="${esc(state.itemQuery)}">
    <div class="chips">${["الكل", "ظاهر", "بانتظار المراجعة", "↩️ مرجّعة للتاجر", "غير مدفوع", "نفد", ...KIND_IDS.map((k) => `${KINDS[k].icon} ${k}`)].map((t) => `<button class="chip ${state.itemFilter === t ? "on" : ""}" data-ifilter="${esc(t)}">${esc(KINDS[t.split(" ")[1]] ? `${KINDS[t.split(" ")[1]].icon} ${KINDS[t.split(" ")[1]].label}` : t)}</button>`).join("")}</div>
    <p class="muted">${all.length} منتج</p>
    <div class="grid">${all.slice(0, 200).map((x) => itemCard(x, `<span class="pill ${status(x) === "ظاهر" ? "" : "off"}">${status(x)}</span>
      ${x.approved ? `<button class="btn" data-cact="itHide" data-id="${esc(x.id)}">إخفاء</button>` : `<button class="btn primary" data-cact="itApprove" data-id="${esc(x.id)}">اعتماد</button>`}
      ${x.adminNote ? "" : `<button class="btn" data-ret="item" data-id="${esc(x.id)}">↩️ رجّعه</button>`}
      <button class="btn danger" data-cact="itDelete" data-id="${esc(x.id)}">حذف</button>`)).join("")}</div>

    <h2 id="ct-stats">📊 الإحصائيات والطلبات</h2>
    <div class="grid">
      <div class="card stat"><div class="v">${stores}</div><div class="t">محل أضاف منتجات</div></div>
      ${byKind.map(([k, n]) => `<div class="card stat"><div class="v">${n}</div><div class="t">${KINDS[k].icon} ${KINDS[k].label}</div></div>`).join("")}
      ${can("merchants") ? `<div class="card stat"><div class="v">${ord.filter((o) => oms(o) > dayAgo).length}</div><div class="t">🧾 طلبات اليوم</div></div>
      <div class="card stat"><div class="v">${ord.filter((o) => oms(o) > weekAgo).length}</div><div class="t">🧾 طلبات آخر 7 أيام</div></div>
      <div class="card stat"><div class="v">${Math.round(ordSum(ord.filter((o) => oms(o) > weekAgo)))}</div><div class="t">💰 قيمة طلبات الأسبوع (ر.س)</div></div>` : ""}
    </div>
    ${can("merchants") && ord.length ? `
    <div class="grid">
      <div class="card stat"><div class="v">${cnt((o) => live(o) && (o.mode || "pickup") === "pickup")}</div><div class="t">🏃 استلام من المحل</div></div>
      <div class="card stat"><div class="v">${cnt((o) => live(o) && o.mode === "delivery")}</div><div class="t">🚚 توصيل</div></div>
      <div class="card stat"><div class="v">${cnt((o) => live(o) && o.mode === "curbside")}</div><div class="t">🚗 من السيارة</div></div>
      <div class="card stat"><div class="v">${cnt((o) => live(o) && o.pay === "cash")}</div><div class="t">💵 كاش</div></div>
      <div class="card stat"><div class="v">${cnt((o) => live(o) && o.pay === "card")}</div><div class="t">💳 بطاقة</div></div>
      <div class="card stat"><div class="v">${cnt((o) => o.status === "cancelled")}</div><div class="t">✕ ملغية</div></div>
    </div>
    <div class="row" style="flex-wrap:wrap;gap:8px;align-items:center;margin-top:10px">
      <div class="chips">${["الكل", ...Object.keys(ORDER_STATUS)].map((k) => `<button class="chip ${state.ordFilter === k ? "on" : ""}" data-ofl="${k}">${k === "الكل" ? "الكل" : esc(ORDER_STATUS[k].t)}</button>`).join("")}</div>
      <select id="ordStore"><option value="">كل المتاجر</option>${[...new Map(ord.map((o) => [o.storeId, o.storeName])).entries()].map(([id, n]) => `<option value="${esc(id)}" ${state.ordStore === id ? "selected" : ""}>${esc(n)}</option>`).join("")}</select>
    </div>
    <div class="card"><table class="tbl"><thead><tr><th>الطلب</th><th>المحل</th><th>الاستلام</th><th>الدفع</th><th>الإجمالي</th><th>الحالة</th></tr></thead><tbody>
      ${ordF.slice(0, 60).map((o) => `<tr><td>#${esc(o.code)}<br><small class="muted">${oms(o) ? new Date(oms(o)).toLocaleString("ar-SA-u-ca-gregory", { dateStyle: "short", timeStyle: "short" }) : ""}</small></td>
        <td>${esc(o.storeName)}<br><small class="muted">${esc(o.customerName || "")}</small></td>
        <td>${esc(MODES[o.mode] || MODES.pickup)}${o.car ? `<br><small class="muted">🚗 ${esc(o.car)}${o.arrivedAt ? " • وصل" : ""}</small>` : ""}</td>
        <td>${o.pay ? `${PAY[o.pay]?.icon} ${esc(PAY[o.pay]?.label || o.pay)}` : "—"}</td>
        <td>${(o.total || 0).toFixed(2)}${o.discount > 0 ? `<br><small class="muted">🔥 خصم عروض ${(+o.discount).toFixed(2)}</small>` : ""}</td><td>${esc(ORDER_STATUS[o.status]?.t || o.status)}</td></tr>`).join("")}</tbody></table>
      ${ordF.length > 60 ? `<p class="muted">يعرض أحدث 60 من ${ordF.length}</p>` : ""}</div>` : ""}

    ${can("merchants") ? `<h2 id="ct-rates">⭐ التقييمات (${state.ratings.length}) — المتوسط ${avgStars}</h2>
    <input type="text" id="rateQuery" placeholder="ابحث في التعليقات أو اسم المحل…" value="${esc(state.rateQuery)}">
    <div class="chips">${["الكل", "5", "4", "3", "2", "1"].map((k) => `<button class="chip ${state.rateFilter === k ? "on" : ""}" data-rfl="${k}">${k === "الكل" ? "الكل" : "★".repeat(+k)}</button>`).join("")}</div>
    ${rates.length ? `<div class="grid">${rates.slice(0, 100).map((r) => `<div class="card">
      <div class="row" style="justify-content:space-between;align-items:center"><b style="color:#B7791F">${"★".repeat(r.stars || 0)}${"☆".repeat(5 - (r.stars || 0))}</b>
        <small class="muted">${r.at?.toMillis ? new Date(r.at.toMillis()).toLocaleDateString("ar-SA-u-ca-gregory") : ""}</small></div>
      <div class="name">${esc(nameOfUid(r.storeId))}</div><div class="muted">من: ${esc(r.name || "زبون")} • طلب ${esc(r.orderId?.slice(0, 6) || "")}</div>
      ${r.text ? `<p style="margin:6px 0">${esc(r.text)}</p>` : ""}
      <div class="actions"><button class="btn danger" data-rdel="${esc(r.id)}">حذف التقييم</button></div></div>`).join("")}</div>` : `<p class="muted">ما فيه تقييمات</p>`}` : ""}`;
}
function bindCatalog(c) {
  const iq = $("itemQuery"); if (iq) iq.oninput = () => { state.itemQuery = iq.value; render(); };
  const rq = $("rateQuery"); if (rq) rq.oninput = () => { state.rateQuery = rq.value; render(); };
  c.querySelectorAll("[data-ofl]").forEach((b) => (b.onclick = () => { state.ordFilter = b.dataset.ofl; render(); }));
  c.querySelectorAll("[data-rfl]").forEach((b) => (b.onclick = () => { state.rateFilter = b.dataset.rfl; render(); }));
  const os = $("ordStore"); if (os) os.onchange = () => { state.ordStore = os.value; render(); };
  c.querySelectorAll("[data-feat]").forEach((x) => (x.onchange = () => run(async () => {
    await updateDoc(doc(db, "config", "app"), { ["catalog.features." + x.dataset.feat]: x.checked });
    await audit(`${x.checked ? "تشغيل" : "إيقاف"}: ${FEATURES[x.dataset.feat]}`, "catalog");
  }, x.checked ? "شغّالة ✅" : "موقفة")));
  c.querySelectorAll("[data-nsforgive]").forEach((b) => (b.onclick = () => {
    if (!confirm("تصفير العدّاد وفك الإيقاف عن هذا العميل؟")) return;
    run(async () => { await deleteDoc(doc(db, "custStats", b.dataset.nsforgive)); await audit("تصفير «ما انستلم» لعميل", "catalog", { type: "customer", id: b.dataset.nsforgive }); }, "تم ✅", "تصفير العدّاد");
  }));
  $("nsSave")?.addEventListener("click", () => run(async () => {
    const v = (id, lo, hi, d) => Math.min(hi, Math.max(lo, parseInt($(id).value, 10) || d));
    const ns = { max: v("nsMax", 1, 20, 3), days: v("nsDays", 1, 365, 60), blockDays: v("nsBlock", 1, 90, 7) };
    await updateDoc(doc(db, "config", "app"), { "catalog.noshow": ns });
    await audit("إعدادات «ما انستلم»", "catalog", {}, `${ns.max} طلبات خلال ${ns.days} يوم → إيقاف ${ns.blockDays} أيام`);
  }, "انحفظ ✅"));
  c.querySelectorAll("[data-rdel]").forEach((b) => (b.onclick = () => {
    const r = state.ratings.find((x) => x.id === b.dataset.rdel);
    if (!confirm("حذف التقييم نهائياً؟")) return;
    run(async () => { await deleteDoc(doc(db, "ratings", r.id)); await audit("حذف تقييم", "catalog", { type: "rating", id: r.id, name: nameOfUid(r.storeId) }, `${r.stars}★ — ${r.text || ""}`.slice(0, 300)); }, "انحذف ✅", "حذف التقييم");
  }));
  c.querySelectorAll("[data-ifilter]").forEach((b) => (b.onclick = () => { state.itemFilter = b.dataset.ifilter; render(); }));
  const setCat = (path, val) => updateDoc(doc(db, "config", "app"), { [path]: val }).catch(() => setDoc(doc(db, "config", "app"), { catalog: {} }, { merge: true }).then(() => updateDoc(doc(db, "config", "app"), { [path]: val })));
  document.querySelectorAll('input[name="trackerMode"]').forEach((x) => x.onchange = () => run(async () => {
    await setCat("catalog.tracker", x.value);
    await audit(`متابعة الطلب عند العميل: ${({ bar: "شريط", bubble: "دائرة", off: "مقفل" })[x.value]}`, "catalog");
  }, "انحفظ ✅", "متابعة الطلب"));
  c.querySelectorAll("[data-catcfg]").forEach((x) => (x.onchange = () => run(async () => {
    await setCat("catalog." + x.dataset.catcfg, x.checked);
    await audit(`${x.checked ? "تشغيل" : "إيقاف"}: ${x.parentElement.textContent.trim().slice(0, 40)}`, "catalog");
  }, "تم ✅")));
  c.querySelectorAll("[data-ctopen]").forEach((x) => (x.onchange = () => { const i = $("ctl_" + x.dataset.ctopen); i.disabled = x.checked; if (!x.checked && !i.value) i.value = 10; }));
  const prOn = $("ctPrOn");
  if (prOn) prOn.onchange = () => run(async () => {
    const days = +$("ctGrace").value, now = Date.now();
    await setCat("catalog.pricing.enabled", prOn.checked);
    if (prOn.checked) await updateDoc(doc(db, "config", "app"), { "catalog.pricing.enabledAt": Timestamp.fromMillis(now), "catalog.pricing.graceDays": days,
      "catalog.pricing.graceUntil": days ? Timestamp.fromMillis(now + days * DAYMS) : Timestamp.fromMillis(now) });
    await audit(`${prOn.checked ? "تشغيل" : "إيقاف"}: تسعير ظهور المنتجات`, "catalog", {}, prOn.checked ? `فترة السماح ${days} يوم` : "");
  }, prOn.checked ? "التسعير شغّال ✅" : "التسعير موقف — المنتجات مجانية");
  c.querySelectorAll("[data-cact]").forEach((b) => (b.onclick = () => {
    const act = b.dataset.cact, id = b.dataset.id, it = state.items.find((x) => x.id === id);
    const itT = it ? { type: "item", id, name: `${it.name} — ${nameOfUid(it.merchantId)}`, thumb: it.imageUrl ? img(it.imageUrl, 160) : "" } : {};
    const A = {
      saveLimits: async () => {
        const lim = {}; KIND_IDS.forEach((k) => { lim[k] = c.querySelector(`[data-ctopen="${k}"]`).checked ? 0 : Math.min(999, Math.max(1, parseInt($("ctl_" + k).value, 10) || 1)); });
        await setCat("catalog.limits", lim);
        await audit("تعديل حدود المنتجات", "catalog", {}, KIND_IDS.map((k) => `${KINDS[k].label}: ${lim[k] || "مفتوح"}`).join(" • "));
      },
      savePrices: async () => {
        const upd = {};
        for (const k of [...KIND_IDS, "all"]) {
          const h = Math.round(parseFloat($("ctp_" + k).value) * 100);
          if (!(h >= 5)) { toast("اكتب سعر صحيح (أقل شي 0.05)"); return false; }
          upd[`catalog.pricing.${k}`] = { priceH: h, active: $("ctpa_" + k).checked };
        }
        upd["catalog.pricing.graceDays"] = +$("ctGrace").value;
        await updateDoc(doc(db, "config", "app"), upd).catch(async () => { await setDoc(doc(db, "config", "app"), { catalog: { pricing: {} } }, { merge: true }); await updateDoc(doc(db, "config", "app"), upd); });
        await audit("تعديل أسعار ظهور المنتجات", "catalog", {}, [...KIND_IDS, "all"].map((k) => `${k === "all" ? "الباقة" : KINDS[k].label}: ${riyal(upd[`catalog.pricing.${k}`].priceH)}${upd[`catalog.pricing.${k}`].active ? "" : " (موقف)"}`).join(" • "));
      },
      saveVat: async () => {
        const rate = parseFloat($("vatRate").value);
        if (!(rate >= 0 && rate <= 100)) { toast("نسبة غير صحيحة"); return false; }
        await actions.saveConfig({ vat: { enabled: $("vatOn").checked, rate } });
        await audit("تعديل إعدادات الضريبة", "catalog", {}, `${$("vatOn").checked ? "مفعّلة" : "موقفة"} • ${rate}%`);
      },
      itApprove: async () => { await updateDoc(doc(db, "items", id), { approved: true, adminNote: "" }); await audit("اعتماد منتج", "catalog", itT); },
      itHide: async () => { await updateDoc(doc(db, "items", id), { approved: false }); await audit("إخفاء منتج", "catalog", itT); },
      itDelete: async () => { if (!confirm("حذف المنتج نهائياً؟")) return false; await deleteDoc(doc(db, "items", id)); await audit("حذف منتج", "catalog", itT, it ? `${it.price} ر.س` : ""); },
    };
    if (A[act]) run(async () => { if ((await A[act]()) !== false) toast("تم ✅"); }, null, b.textContent.trim().slice(0, 30));
  }));
}

function settings() {
  const cfg = state.config;
  const cats = catDraft();
  const ex = exclusiveOf(cfg), rw = { enabled: !!cfg.exclusive?.reward?.enabled, percent: cfg.exclusive?.reward?.percent ?? 100 };
  const COLORS = ["#7C3AED", "#10318F", "#E05A47", "#2563EB", "#B7791F", "#111827"];
  return jumpNav([["st-cats", "التصنيفات"], ["st-excl", "شارة الحصري"], ["st-support", "الدعم"], ["st-ai", "التعبئة الذكية"], ["st-verify", "التوثيق"], ["st-poi", "محلات Google"]]) + `<h2 id="st-excl">💎 شارة «حصري في لكوفر»</h2>
    <div class="card">
      <p class="muted">مجانية: التاجر يعلّم على العرض «حصري لعملاء لكوفر»، وتطلع الشارة على الخريطة والبطاقات.</p>
      <label class="switch"><input type="checkbox" id="exOn" ${ex.enabled ? "checked" : ""}> إظهار الخيار والشارة</label>
      <div class="row" style="align-items:center;margin-top:8px;flex-wrap:wrap"><span>النص</span><input type="text" id="exLabel" value="${esc(ex.label)}" maxlength="20" style="width:180px">
        <span>الأيقونة</span><input type="text" id="exIcon" value="${esc(ex.icon)}" maxlength="4" style="width:60px">
        <span>اللون</span>${COLORS.map((c) => `<button class="swatch${ex.color === c ? " on" : ""}" data-excolor="${c}" style="background:${c}"></button>`).join("")}
        <span class="xbadge" id="exPrev" style="background:${esc(ex.color)}">${esc(ex.icon)} ${esc(ex.label)}</span></div>
      <div class="reward-box">
        <label class="switch"><input type="checkbox" id="exRw" ${rw.enabled ? "checked" : ""}> 🎁 مكافأة الحصري: خصم على <b>نشر</b> العروض الحصرية</label>
        <div class="row" style="align-items:center;margin-top:6px;flex-wrap:wrap"><span>نسبة الخصم</span>
          <input type="number" id="exPct" min="1" max="100" value="${esc(rw.percent)}" style="width:90px"> <span>%</span>
          ${[25, 50, 100].map((p) => `<button class="chip" data-expct="${p}">${p === 100 ? "مجاني (100%)" : p + "%"}</button>`).join("")}</div>
        <p class="muted" style="margin:6px 0 0">${rewardOf(cfg).on ? `شغّالة الحين: ${rewardOf(cfg).free ? "نشر الحصري مجاني" : `خصم ${rewardOf(cfg).percent}%`}` : "مطفية: الشارة موجودة بدون خصم"}. الخصم على النشر بس (التمييز والإبراز بسعرهم)، ويُحسب وقت الشراء — لو طفيتها اللي اشتروا ما يتأثرون. لما تكون شغّالة، التاجر ما يقدر يشيل الحصري عن عرضه بعد النشر.</p>
      </div>
      <div class="actions"><button class="btn primary" data-act="saveExcl">حفظ</button></div>
    </div>
    <h2 id="st-cats">🏷️ تصنيفات المتاجر</h2>
    <div class="card">
      <p class="muted">تظهر للتجار عند إعداد المتجر ونشر العروض، وللزبائن في الفلاتر. تعديل الاسم يحدّث العروض والمتاجر اللي عليه.
        <b>النوع الأساسي</b> يحدد وش يطلع أول في صفحة المتجر (🍽️ منيو / 🛍️ منتجات / ✂️ خدمات)، والتاجر يقدر يضيف الأنواع الثانية بعد.</p>
      ${cats.map((c, i) => `<div class="row" style="align-items:center;margin:4px 0">
        <input type="text" data-catname="${i}" value="${esc(c.name)}" maxlength="40" style="flex:1">
        <select data-cattype="${i}">${KIND_IDS.map((k) => `<option value="${k}" ${c.type === k ? "selected" : ""}>${KINDS[k].icon} ${KINDS[k].label}</option>`).join("")}</select>
        <button class="btn" data-catup="${i}" ${i === 0 ? "disabled" : ""}>↑</button>
        <button class="btn danger" data-catdel="${i}" ${cats.length < 2 ? "disabled" : ""}>حذف</button></div>`).join("")}
      <div class="row" style="align-items:center;margin-top:8px"><input type="text" id="catNew" placeholder="تصنيف جديد مع إيموجي، مثال: صيانة 🔧 أو نادي رياضي 🏋️" maxlength="40" style="flex:1">
        <select id="catNewType">${KIND_IDS.map((k) => `<option value="${k}">${KINDS[k].icon} ${KINDS[k].label}</option>`).join("")}</select>
        <button class="btn" data-act="catAdd">إضافة</button></div>
      <div class="actions"><button class="btn primary" data-act="saveCats">حفظ التصنيفات</button></div>
    </div>
    <h2 id="st-support">⚙️ رقم واتساب الدعم</h2>
    <div class="card"><div class="row" style="align-items:center"><input type="text" id="supportNum" value="${esc(cfg.supportWhatsapp || "0552793379")}" style="max-width:240px">
      <button class="btn primary" data-act="saveSupport">حفظ</button></div></div>
    <h2 id="st-ai">✨ التعبئة الذكية</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="aiOn" ${cfg.aiAutofillEnabled ? "checked" : ""}> ${cfg.aiAutofillEnabled ? "مفعّلة" : "موقفة"}</label>
      <details><summary>إعدادات متقدمة (اسم النموذج)</summary>
        <div class="row" style="margin-top:8px"><input type="text" id="aiModel" value="${esc(cfg.aiModel || "gemini-3.8-flash")}" style="max-width:260px">
        <button class="btn primary" data-act="saveModel">حفظ</button></div></details>
    </div>
    <h2 id="st-verify">🛡️ توثيق المتاجر</h2>
    <div class="card">
      <p class="muted">التوثيق اختياري للتاجر: الموثّق ياخذ ✓ وينشر مباشرة، وغيره عروضه تمر بالمراجعة.</p>
      ${[["verifyEnabled", "تفعيل التوثيق (زر وثّق متجرك للتجار)"], ["verifyRequireDoc", "رقم السجل التجاري / العمل الحر"], ["verifyRequirePhoto", "صورة حيّة لواجهة المحل"], ["verifyRequireLocation", "التأكد إنه عند المحل (GPS)"]]
        .map(([k, l]) => `<label class="switch" style="margin:6px 0"><input type="checkbox" data-cfg="${k}" ${cfg[k] !== false ? "checked" : ""}> ${l}</label>`).join("")}
    </div>
    <h2 id="st-poi">📍 محلات Google على الخريطة</h2>
    <div class="card">
      <label class="switch"><input type="checkbox" id="poiOn" ${cfg.showGooglePoi !== false ? "checked" : ""}> إظهار نقاط المحلات (باهتة)</label>
      <label class="switch"><input type="checkbox" id="hideAdFar" ${cfg.hideAdFar === true ? "checked" : ""}> 📷 إخفاء عروض الإعلانات لما الخريطة مصغّرة (لما تزدحم الخريطة)</label>
      <label class="switch" style="margin-top:8px"><input type="checkbox" id="inviteOn" ${cfg.poiInviteEnabled !== false ? "checked" : ""}> بطاقة "ادعه إلى لكوفر" عند الضغط</label>
      <p class="muted">نص الدعوة ({name} = اسم المحل)</p>
      <textarea id="inviteMsg">${esc(cfg.inviteMessage || 'السلام عليكم 👋 شفت محلكم "{name}" على خريطة لكوفر. سجّلوا محلكم مجاناً وانشروا عروضكم لأهل الحي، وخلّوا الزباين يوصلون لكم بسهولة 📍')}</textarea>
      <p class="muted">رابط تحميل التطبيق (اختياري)</p>
      <input type="text" id="appLink" value="${esc(cfg.appLink || "")}">
      <div class="actions"><button class="btn primary" data-act="savePoi">حفظ</button></div>
    </div>`;
}

// ---------------- 📷 عروض من إعلانات ----------------
// الفورم يتركّب مرة وحدة ويرجع ينحط بعد كل تحديث للصفحة (عشان ما تضيع الصورة والبيانات)
let adHost = null, adForm = null, adKey = "";
function attachAdForm() {
  const slot = $("adSlot"); if (!slot) return;
  const cats = state.config.categories?.length ? state.config.categories : CATEGORIES.slice(1);
  const key = JSON.stringify([!!state.config.aiAutofillEnabled, state.config.aiModel || "", adDays(state.config), cats]);
  const remount = () => {
    adKey = key;
    adForm = mountAdForm(adHost, {
      db, fbApp: app, user: auth.currentUser, config: state.config, mapsKey: MAPS_WEB_KEY, categories: cats, toast,
      onDone: (id, f) => { audit("رفع عرض من إعلان", "scout", { type: "offer", id, name: `${f.title} — ${f.name || f.place?.name || ""}` }, f.place?.address || ""); remount(); },
    });
  };
  if (!adHost) { adHost = document.createElement("div"); remount(); }
  else if (key !== adKey && !adForm?.dirty()) remount(); // الإعدادات وصلت/تغيّرت والفورم لسا فاضي
  slot.appendChild(adHost);
}
const adLeft = (o) => { const ms = (o.expiresAt?.toMillis?.() || 0) - Date.now(); if (ms <= 0) return "انتهى"; const d = Math.ceil(ms / 864e5); return d === 1 ? "باقي يوم" : `باقي ${d} أيام`; };
function adsPage() {
  const ads = state.offers.filter((o) => o.source === "ad").sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0));
  const live = ads.filter((o) => !isExpired(o));
  const near = (o) => state.storesAll.filter((s) => s.approved).map((s) => {
    const b = (s.branches || [])[0]; const d = b ? Math.hypot(b.latitude - o.latitude, (b.longitude - o.longitude) * Math.cos(o.latitude * Math.PI / 180)) * 111 : 1e9;
    return { s, d };
  }).sort((a, b) => a.d - b.d).slice(0, 15);
  return `<h2>📷 عروض من إعلانات</h2>
    <p class="muted">ارفع صورة إعلان لمحل مو مسجّل في لكوفر: الذكاء الاصطناعي يقرأه ويدوّر على المحل في Google، وبعد ما تتأكد ينزل على موقعه في الخريطة بعلامة «📷 من إعلان المحل».
    تقدر بعد ترفع من التطبيق: اضغط على أي محل في الخريطة ← «📷 ارفع عرض».</p>
    <div class="ads-grid">
      <div class="card" id="adSlot"></div>
      <div>
        ${state.me.owner ? `<div class="card"><b>⏱️ مدة العرض الافتراضية</b>
          <div class="row" style="margin-top:8px"><input type="number" id="adDaysCfg" min="1" max="${AD_MAX_DAYS}" value="${adDays(state.config)}" style="max-width:90px"> <span class="muted">يوم (الحد ${AD_MAX_DAYS})</span> <button class="btn" id="adDaysSave">حفظ</button></div>
          <p class="muted" style="margin:6px 0 0">العرض يختفي لحاله بعد المدة، عشان ما يبقى عرض منتهي والمحل ما يدري.</p></div>` : ""}
        <h3>المنشورة (${live.length})${ads.length > live.length ? ` <span class="muted">• منتهية ${ads.length - live.length}</span>` : ""}</h3>
        ${ads.length ? ads.map((o) => offerCard(o,
          `<div class="muted">${isExpired(o) ? "⛔ انتهى" : "🟢 " + adLeft(o)} • 💬 ${o.whatsappCount || 0} • 🧭 ${o.directionsCount || 0}</div>`,
          `<a class="btn" target="_blank" rel="noopener" href="/o/${encodeURIComponent(o.id)}">فتح ↗</a>
           ${!isExpired(o) ? `<button class="btn" data-adext="${esc(o.id)}">+${adDays(state.config)} أيام</button>` : `<button class="btn" data-adext="${esc(o.id)}">🔁 رجّعه ${adDays(state.config)} أيام</button>`}
           ${can("review") && state.storesAll.length ? `<select class="btn" data-admove="${esc(o.id)}"><option value="">🔁 نقل لمتجر سجّل…</option>${near(o).map(({ s, d }) => `<option value="${esc(s.uid)}">${esc(s.name)}${d < 1e8 ? ` (${d < 1 ? Math.round(d * 1000) + " م" : d.toFixed(1) + " كم"})` : ""}</option>`).join("")}</select>` : ""}
           <button class="btn danger" data-addel="${esc(o.id)}">حذف</button>`)).join("") : `<p class="muted">ما رفعت عروض من إعلانات للحين.</p>`}
      </div>
    </div>`;
}
function bindAds(c) {
  const byId = (id) => state.offers.find((o) => o.id === id);
  $("adDaysSave")?.addEventListener("click", () => run(async () => {
    const d = Math.min(AD_MAX_DAYS, Math.max(1, parseInt($("adDaysCfg").value, 10) || 7));
    await actions.saveConfig({ adScoutDays: d }); await audit("مدة عروض الإعلانات", "settings", {}, `${d} يوم`);
  }, "انحفظ ✅"));
  c.querySelectorAll("[data-adext]").forEach((b) => (b.onclick = () => run(async () => {
    const o = byId(b.dataset.adext), from = Math.max(Date.now(), o.expiresAt?.toMillis?.() || 0);
    const until = Math.min(from + adDays(state.config) * 864e5, Date.now() + AD_MAX_DAYS * 864e5 - 6e4);
    await updateDoc(doc(db, "offers", o.id), { expiresAt: Timestamp.fromMillis(until), scoutedBy: state.me.uid });
    await audit("تمديد عرض إعلان", "scout", offerT(o), new Date(until).toLocaleDateString("ar-SA"));
  }, "تمدّد ✅", "التمديد")));
  c.querySelectorAll("[data-addel]").forEach((b) => (b.onclick = () => {
    const o = byId(b.dataset.addel); if (!confirm(`حذف «${o.offerTitle}» من الخريطة؟`)) return;
    run(async () => { await deleteDoc(doc(db, "offers", o.id)); await audit("حذف عرض إعلان", "scout", offerT(o)); }, "انحذف ✅", "الحذف");
  }));
  c.querySelectorAll("[data-admove]").forEach((sel) => (sel.onchange = () => {
    const o = byId(sel.dataset.admove), st = state.storesAll.find((s) => s.uid === sel.value); if (!st) return;
    if (!confirm(`ننقل «${o.offerTitle}» لمتجر «${st.name}»؟ بيصير عرضه ويقدر يعدّله ويمدده.`)) { sel.value = ""; return; }
    run(async () => {
      await updateDoc(doc(db, "offers", o.id), { merchantId: st.uid, source: "claimed", restaurantName: (st.name || o.restaurantName).slice(0, 100), logoUrl: st.logoUrl || "" });
      await audit("نقل عرض إعلان لمتجر", "scout", offerT(o), st.name);
    }, "انتقل للمتجر ✅", "النقل");
  }));
}

function bind() {
  if (state.tab === "ads") bindAds($("content"));
  const byId = (id) => state.offers.find((o) => o.id === id);
  const c = $("content");
  c.querySelectorAll("details[data-fold]").forEach((d) => (d.ontoggle = () => { (state.folds ||= {})[d.dataset.fold] = d.open; }));
  c.querySelectorAll("[data-go]").forEach((b) => (b.onclick = () => { state.tab = b.dataset.go; localStorage.setItem("hayyna_admin_tab", state.tab); render(); window.scrollTo(0, 0); }));
  c.querySelectorAll("[data-view]").forEach((i) => (i.onclick = () => { $("viewerImg").src = img(i.dataset.view, 1600); $("viewer").classList.remove("hidden"); }));
  c.querySelectorAll("[data-fbtype]").forEach((b) => (b.onclick = () => { state.inboxType = b.dataset.fbtype; render(); }));
  c.querySelectorAll("[data-showdone]").forEach((b) => (b.onclick = () => { state.showDone = !state.showDone; render(); }));
  c.querySelectorAll("[data-ofilter]").forEach((b) => (b.onclick = () => { state.offerFilter = b.dataset.ofilter; render(); }));
  c.querySelectorAll("textarea[id^=reply_]").forEach((t) => (t.oninput = () => { state.drafts[t.id.slice(6)] = t.value; }));
  const oq = $("offerQuery"); if (oq) oq.oninput = () => { state.offerQuery = oq.value; render(); };
  const cfgSwitch = (key, val, label) => run(async () => { await actions.saveConfig({ [key]: val }); await audit(`${val ? "تشغيل" : "إيقاف"}: ${label}`, "settings"); }, "تم ✅");
  c.querySelectorAll("[data-cfg]").forEach((x) => (x.onchange = () => cfgSwitch(x.dataset.cfg, x.checked, x.parentElement.textContent.trim())));
  const poiOn = $("poiOn"); if (poiOn) poiOn.onchange = () => cfgSwitch("showGooglePoi", poiOn.checked, "نقاط محلات Google");
  const hideAdFar = $("hideAdFar"); if (hideAdFar) hideAdFar.onchange = () => cfgSwitch("hideAdFar", hideAdFar.checked, "إخفاء عروض الإعلانات والخريطة مصغّرة");
  const invOn = $("inviteOn"); if (invOn) invOn.onchange = () => cfgSwitch("poiInviteEnabled", invOn.checked, "بطاقة ادعه إلى لكوفر");
  c.querySelectorAll("[data-catname]").forEach((x) => (x.oninput = () => { catDraft()[+x.dataset.catname].name = x.value; }));
  c.querySelectorAll("[data-cattype]").forEach((x) => (x.onchange = () => { catDraft()[+x.dataset.cattype].type = x.value; }));
  const cn = $("catNew"); if (cn) cn.oninput = () => { const g = baseKindOf({}, cn.value); $("catNewType").value = g; }; // نخمّن النوع من الاسم
  bindCatalog(c);
  c.querySelectorAll("[data-ret]").forEach((b) => (b.onclick = () => returnToMerchant(b.dataset.ret, b.dataset.id)));
  c.querySelectorAll("[data-sfilter]").forEach((b) => (b.onclick = () => { state.storeFilter = b.dataset.sfilter; render(); }));
  c.querySelectorAll("[data-copy]").forEach((b) => (b.onclick = async () => { try { await navigator.clipboard.writeText(b.dataset.copy); toast("نسخنا الرقم ✅"); } catch { prompt("انسخ:", b.dataset.copy); } }));
  const wq = $("walletQuery"); if (wq) wq.oninput = () => { state.walletQuery = wq.value; state.walletShow = 50; render(); };
  c.querySelectorAll("[data-walletmore]").forEach((b) => (b.onclick = () => { state.walletShow = (state.walletShow || 50) + 50; render(); }));
  const sq = $("storeQuery"); if (sq) sq.oninput = () => { state.storeQuery = sq.value; render(); };
  c.querySelectorAll("[data-catup]").forEach((b) => (b.onclick = () => { const d = catDraft(), i = +b.dataset.catup; d.splice(i - 1, 0, d.splice(i, 1)[0]); render(); }));
  c.querySelectorAll("[data-catdel]").forEach((b) => (b.onclick = () => { if (confirm("حذف التصنيف؟ العروض الحالية تبقى عليه.")) { catDraft().splice(+b.dataset.catdel, 1); render(); } }));
  const ai = $("aiOn"); if (ai) ai.onchange = () => run(async () => { await actions.saveConfig({ aiAutofillEnabled: ai.checked }); await audit(`${ai.checked ? "تشغيل" : "إيقاف"}: التعبئة الذكية`, "settings"); }, ai.checked ? "التعبئة الذكية مفعّلة ✨" : "التعبئة الذكية موقفة");
  // ---- السجل ----
  const lq = $("logQuery"); if (lq) lq.oninput = () => { state.logQuery = lq.value; render(); };
  c.querySelectorAll("[data-logarea]").forEach((b) => (b.onclick = () => { state.logArea = b.dataset.logarea; render(); }));
  c.querySelectorAll("[data-logactor]").forEach((b) => (b.onclick = () => { state.logActor = b.dataset.logactor; render(); }));
  c.querySelectorAll("[data-logof]").forEach((b) => (b.onclick = () => { state.logActor = b.dataset.logof; state.logArea = "الكل"; state.tab = "log"; render(); }));
  // ---- الفريق ----
  c.querySelectorAll("[data-stactive]").forEach((x) => (x.onchange = () => run(async () => {
    const m = state.staff.find((s) => s.id === x.dataset.stactive);
    await updateDoc(doc(db, "staff", m.id), { active: x.checked });
    await audit(x.checked ? "تفعيل موظف" : "إيقاف موظف", "team", { type: "staff", id: m.id, name: m.name || m.id });
  }, "تم ✅")));
  c.querySelectorAll("[data-stperm]").forEach((x) => (x.onchange = () => run(async () => {
    const m = state.staff.find((s) => s.id === x.dataset.stperm), set = new Set(m.perms || []);
    x.checked ? set.add(x.dataset.k) : set.delete(x.dataset.k);
    await updateDoc(doc(db, "staff", m.id), { perms: [...set] });
    await audit("تعديل صلاحيات موظف", "team", { type: "staff", id: m.id, name: m.name || m.id }, `${x.checked ? "أُضيفت" : "أُزيلت"}: ${PERMS.find(([k]) => k === x.dataset.k)[1]}`);
  }, "تم ✅")));
  c.querySelectorAll("[data-stdel]").forEach((b) => (b.onclick = () => run(async () => {
    const m = state.staff.find((s) => s.id === b.dataset.stdel);
    if (!confirm(`إزالة ${m.name || m.id} من الفريق؟ (عملياته تبقى في السجل)`)) return;
    await deleteDoc(doc(db, "staff", m.id));
    await audit("إزالة موظف", "team", { type: "staff", id: m.id, name: m.name || m.id });
  }, "تمت الإزالة")));
  // ---- الرصيد ----
  c.querySelectorAll("[data-it]").forEach((x) => (x.onchange = x.oninput = () => {
    const it = itemsDraft()[+x.dataset.it], k = x.dataset.k;
    it[k] = x.type === "checkbox" ? x.checked : (k === "price" || k === "days") ? Math.max(1, parseInt(x.value, 10) || 1) : x.value;
    if (k === "unit" || k === "effect") render();
    // تغيّرت المدة: نحدّث المدة المكتوبة في اسم الخدمة (مثل «إبراز أسبوع» ← «إبراز يوم») عشان ما يتناقض الاسم مع المدة
    if (k === "days" && it.unit !== "day") {
      const n = it.days, dur = n === 1 ? "يوم" : n === 2 ? "يومين" : n === 7 ? "أسبوع" : n === 14 ? "أسبوعين" : n === 30 ? "شهر" : n <= 10 ? `${n} أيام` : `${n} يوم`;
      const re = /\d+\s*(?:يوم|أيام|ايام)|يومين|أسبوعين|اسبوعين|أسبوع|اسبوع|شهرين|شهر|يوم/;
      if (re.test(it.name)) { it.name = it.name.replace(re, dur); const nm = c.querySelector(`[data-it="${x.dataset.it}"][data-k="name"]`); if (nm) nm.value = it.name; }
    }
  }));
  c.querySelectorAll("[data-itup]").forEach((b) => (b.onclick = () => { const d = itemsDraft(), i = +b.dataset.itup; d.splice(i - 1, 0, d.splice(i, 1)[0]); render(); }));
  c.querySelectorAll("[data-itdel]").forEach((b) => (b.onclick = () => { if (confirm("حذف الخدمة؟ (المشتريات السابقة تبقى)")) { itemsDraft().splice(+b.dataset.itdel, 1); render(); } }));
  c.querySelectorAll("[data-pk]").forEach((x) => (x.oninput = () => { packsDraft()[+x.dataset.pk][x.dataset.k] = Math.max(1, parseInt(x.value, 10) || 1); }));
  c.querySelectorAll("[data-pkdel]").forEach((b) => (b.onclick = () => { packsDraft().splice(+b.dataset.pkdel, 1); render(); }));
  const crGrace = $("crGrace"); if (crGrace) crGrace.onchange = () => { if (!state.config.credits?.enabled) run(() => updateDoc(doc(db, "config", "app"), { "credits.graceDays": parseInt(crGrace.value, 10) || 0 }), "تم ✅"); else toast("المدة الجديدة تنطبق لما تشغّل الرصيد المرة الجاية"); };
  const crOn = $("crOn"); if (crOn) crOn.onchange = () => run(async () => {
    if (crOn.checked && !confirm(`تفعيل الرصيد؟ أي عرض جديد يحتاج رصيد، والعروض الحالية مجانية ${$("crGrace").value === "0" ? "تنتهي الحين" : `${$("crGrace").value} يوم`} وبعدها تحتاج تمديد.`)) { crOn.checked = false; return; }
    const cur = state.config.credits || {};
    // كل تشغيل يبدأ من جديد: العروض المنشورة قبل هاللحظة مجانية لين نهاية فترة السماح
    const grace = parseInt($("crGrace").value, 10) || 0;
    await updateDoc(doc(db, "config", "app"), { "credits.enabled": crOn.checked, "credits.graceDays": grace,
      ...(crOn.checked ? { "credits.enabledAt": serverTimestamp(), "credits.graceUntil": Timestamp.fromMillis(Date.now() + grace * 864e5) } : {}),
      ...(cur.items ? {} : { "credits.items": DEFAULT_ITEMS }) });
    await audit(crOn.checked ? "تشغيل نظام الرصيد" : "إيقاف نظام الرصيد", "credits", {}, crOn.checked ? `فترة السماح: ${$("crGrace").value} يوم` : "");
  }, crOn.checked ? "تم تفعيل الرصيد ✅" : "تم إيقاف الرصيد");
  c.querySelectorAll("[data-copyb]").forEach((b) => (b.onclick = async () => { const list = unusedOf(b.dataset.copyb).map((x) => x.id).join("\n"); try { await navigator.clipboard.writeText(list); toast(`نسخنا ${unusedOf(b.dataset.copyb).length} كود ✅`); } catch { prompt("انسخ:", list); } }));
  c.querySelectorAll("[data-csvb]").forEach((b) => (b.onclick = () => { const list = unusedOf(b.dataset.csvb).map((x) => x.id).join("\n"); const a = document.createElement("a"); a.href = URL.createObjectURL(new Blob([list], { type: "text/plain" })); a.download = `hayyna-codes-${b.dataset.csvb}.txt`; a.click(); }));
  c.querySelectorAll("[data-delb]").forEach((b) => (b.onclick = () => run(async () => {
    const list = unusedOf(b.dataset.delb); if (!list.length || !confirm(`حذف ${list.length} كود غير مستخدم؟ (لا تحذفها لو رفعتها في سلة)`)) return;
    for (let i = 0; i < list.length; i += 400) { const wb = writeBatch(db); list.slice(i, i + 400).forEach((x) => wb.delete(doc(db, "creditCodes", x.id))); await wb.commit(); }
    await audit("حذف أكواد غير مستخدمة", "credits", { type: "codes", id: b.dataset.delb, name: b.dataset.delb }, `${list.length} كود`);
    await loadCodes();
  }, "تم الحذف")));
  c.querySelectorAll("[data-adj]").forEach((b) => (b.onclick = () => run(async () => {
    const uid = b.dataset.adj, delta = parseInt($("adj_" + uid).value, 10);
    if (!delta) return toast("اكتب رقم مثل 20 أو -10");
    await adminCredit(uid, delta, $("adjn_" + uid).value.trim().slice(0, 120));
    await audit("تعديل رصيد تاجر", "credits", uidT(uid), `${delta > 0 ? "+" : ""}${delta} نقطة${$("adjn_" + uid).value.trim() ? " • " + $("adjn_" + uid).value.trim() : ""}`);
  }, "تم تعديل الرصيد ✅")));
  // ---- شارة الحصري ----
  c.querySelectorAll("[data-expct]").forEach((b) => (b.onclick = () => { $("exPct").value = b.dataset.expct; $("exRw").checked = true; }));
  c.querySelectorAll("[data-excolor]").forEach((b) => (b.onclick = () => { c.querySelectorAll("[data-excolor]").forEach((x) => x.classList.toggle("on", x === b)); $("exPrev").style.background = b.dataset.excolor; }));
  ["exLabel", "exIcon"].forEach((id) => { const el = $(id); if (el) el.oninput = () => { $("exPrev").textContent = `${$("exIcon").value} ${$("exLabel").value}`; }; });
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
      unexcl: () => actions.unexcl(o),
      reply: async () => { await actions.reply(id, $("reply_" + id).value.trim()); delete state.drafts[id]; },
      progress: () => actions.fbStatus(id, "in_progress"),
      done: () => actions.fbStatus(id, "done"),
      deleteFb: () => actions.deleteFb(id),
      saveSupport: () => actions.saveConfig({ supportWhatsapp: $("supportNum").value.trim() }),
      saveModel: () => actions.saveConfig({ aiModel: $("aiModel").value.trim() }),
      verifyOk: () => actions.verify(id, true, ""),
      awardV: () => actions.awardRef(id, "verifiedPts"),
      awardO: () => actions.awardRef(id, "offerPts"),
      removeRef: () => actions.removeRef(id),
      verifyNo: () => actions.verify(id, false, $("reason_" + id).value.trim()),
      catAdd: () => { const v = $("catNew").value.trim(); if (v && !catDraft().some((x) => x.name.trim() === v)) catDraft().push({ orig: null, name: v, type: $("catNewType").value }); render(); },
      saveCats: async () => {
        const d = catDraft().map((x) => ({ ...x, name: x.name.trim() })).filter((x) => x.name);
        const names = [...new Set(d.map((x) => x.name))];
        const types = {}; d.forEach((x) => { types[x.name] = x.type || baseKindOf({}, x.name); });
        await updateDoc(doc(db, "config", "app"), { categories: names, categoryTypes: types });
        let moved = 0;
        for (const x of d) if (x.orig && x.orig !== x.name) {
          for (const od of (await getDocs(query(collection(db, "offers"), where("cuisineType", "==", x.orig)))).docs) { await updateDoc(od.ref, { cuisineType: x.name }); moved++; }
          for (const sd of (await getDocs(query(collection(db, "stores"), where("category", "==", x.orig)))).docs) await updateDoc(sd.ref, { category: x.name });
        }
        state.catDraft = null; if (moved) toast(`تحدّث ${moved} عرض للاسم الجديد`);
      },
      saveSpot: () => updateDoc(doc(db, "config", "app"), { "credits.spotlightMax": Math.min(20, Math.max(1, parseInt($("crSpot").value, 10) || 3)) }),
      fixSpot: async () => {
        const items = { ...creditsOf(state.config).items };
        Object.keys(items).forEach((k) => { if (/إبراز|سوبر/.test(items[k].name)) items[k] = { ...items[k], effect: "spotlight", name: items[k].name.replace(/الإبراز|إبراز/, "عرض سوبر") }; });
        await updateDoc(doc(db, "config", "app"), { "credits.items": items }); state.itemsDraft = null;
      },
      saveWelcome: () => updateDoc(doc(db, "config", "app"), { "credits.welcome": Math.max(0, parseInt($("crWelcome").value, 10) || 0) }),
      saveStore: () => updateDoc(doc(db, "config", "app"), { "credits.storeUrl": $("crStore").value.trim().slice(0, 300) }),
      itAdd: () => { itemsDraft().push({ id: "x" + Date.now().toString(36), name: "خدمة جديدة", effect: "feature", unit: "fixed", price: 10, days: 7, active: false }); render(); },
      itReset: () => { if (confirm("ترجع الخدمات والأسعار للافتراضي؟ (ما تنحفظ إلا لما تضغط حفظ)")) { state.itemsDraft = null; state.config = { ...state.config, credits: { ...(state.config.credits || {}), items: undefined } }; state.itemsDraft = itemList(DEFAULT_ITEMS).map((x) => ({ ...x })); render(); } },
      saveItems: async () => {
        const map = {};
        const curItems = (state.config.credits || {}).items || {};
        itemsDraft().forEach((it, i) => { const id = it.id; map[id] = { name: String(it.name || "").trim().slice(0, 40) || "خدمة", effect: id === "publish" ? "publish" : it.effect, unit: it.unit === "day" ? "day" : "fixed", price: Math.max(1, it.price | 0), days: it.unit === "day" ? 1 : Math.min(90, Math.max(1, it.days | 0)), active: !!it.active, order: i, ...(curItems[id]?.promo ? { promo: curItems[id].promo } : {}) }; });
        await updateDoc(doc(db, "config", "app"), { "credits.items": map }); state.itemsDraft = null;
      },
      pkAdd: () => { packsDraft().push({ points: 200, price: 195 }); render(); },
      savePacks: async () => { await updateDoc(doc(db, "config", "app"), { "credits.packs": packsDraft().map((p) => ({ points: p.points | 0, price: p.price | 0 })) }); state.packsDraft = null; },
      loadCodes: () => loadCodes(),
      genCodes: async () => {
        const points = parseInt($("cdPoints").value, 10), count = Math.min(300, Math.max(1, parseInt($("cdCount").value, 10) || 0)), months = parseInt($("cdExp").value, 10);
        if (!confirm(`إنشاء ${count} كود بقيمة ${points} نقطة؟`)) return;
        const d = new Date(), batch = `${points}نقطة-${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}-${String(d.getHours()).padStart(2, "0")}${String(d.getMinutes()).padStart(2, "0")}`;
        const exp = months ? Timestamp.fromMillis(Date.now() + months * 30 * 864e5) : null;
        for (let i = 0; i < count; i += 400) {
          const wb = writeBatch(db);
          for (let k = i; k < Math.min(count, i + 400); k++) wb.set(doc(db, "creditCodes", newCode()), { points, batch, createdAt: serverTimestamp(), expiresAt: exp, redeemedBy: null, redeemedAt: null });
          await wb.commit();
        }
        await loadCodes();
      },
      saveExcl: () => {
        const pct = Math.round(+$("exPct").value);
        if (!(pct >= 1 && pct <= 100)) { toast("نسبة الخصم من 1 إلى 100"); return false; }
        return actions.saveConfig({ exclusive: { enabled: $("exOn").checked, label: $("exLabel").value.trim().slice(0, 20) || "حصري في لكوفر", icon: $("exIcon").value.trim().slice(0, 4) || "💎", color: $("content").querySelector("[data-excolor].on")?.dataset.excolor || "#7C3AED",
          reward: { enabled: $("exRw").checked, percent: pct } } });
      },
      staffAdd: async () => {
        const email = $("stEmail").value.trim().toLowerCase(), name = $("stName").value.trim().slice(0, 60);
        const perms = PERMS.map(([k]) => k).filter((k) => $("content").querySelector(`[data-newperm="${k}"]`)?.checked);
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { toast("اكتب إيميل صحيح"); return false; }
        if (ADMIN_EMAILS.includes(email)) { toast("هذا إيميل المالك"); return false; }
        if (!perms.length) { toast("اختر صلاحية وحدة على الأقل"); return false; }
        await setDoc(doc(db, "staff", email), { email, name, perms, active: true, addedAt: serverTimestamp(), addedBy: state.me.email });
      },
      savePoi: () => actions.saveConfig({ inviteMessage: $("inviteMsg").value.trim(), appLink: $("appLink").value.trim() }),
    };
    const act = b.dataset.act, fn = map[act];
    if (!fn) return;
    const before = o ? { ...o } : null; // نسخة قبل الحذف للسجل
    const desc = describe(act, id, b, before); // نوصف العملية قبل التنفيذ (القيم المكتوبة لسه موجودة)
    run(async () => {
      const r = await fn();
      if (r === false) return;
      if (desc) await audit(...desc);
    }, ["approve", "trust", "suspendOffer", "suspendUid", "verifyOk", "verifyNo"].includes(act) ? null : "تم ✅", b.textContent.trim().slice(0, 30));
  }));
}

$("viewerClose").onclick = () => $("viewer").classList.add("hidden");
$("viewer").onclick = (e) => { if (e.target.id === "viewer") $("viewer").classList.add("hidden"); };

// ===================== 📣 الإعلانات والعروض على أسعار لكوفر =====================
const PR = { anns: [], unsub: null, color: "navy", draft: {}, edit: null, pedit: null };
const dtv = (ms) => { if (!ms) return ""; const d = new Date(ms - new Date().getTimezoneOffset() * 60000); return d.toISOString().slice(0, 16); };
const tsMs = (t) => (t && t.toMillis ? t.toMillis() : 0);
const ACTIONS = [["store", "🏪 يفتح متجر (تختاره من القائمة)"], ["offer", "🔥 يفتح عرض (تختاره من القائمة)"], ["url", "🔗 رابط خارجي"], ["publish", "➕ يفتح «نشر عرض» (للتجار بس)"], ["credits", "💰 يفتح شراء الرصيد (للتجار)"], ["none", "بدون زر"]];
const storeLabel = (s) => `${s.name || "—"}${(state.storeCode || {})[s.uid] ? " • " + state.storeCode[s.uid] : ""}${(s.branches || [])[0]?.district ? " • " + s.branches[0].district : ""}${s.approved ? "" : " (غير معتمد)"}`;
const liveOffers = () => state.offers.filter((o) => o.isApproved !== false && !o.draft && !(tsMs(o.expiresAt) && tsMs(o.expiresAt) < Date.now()));
const offerLabel = (o) => `${o.restaurantName || "—"} — ${o.offerTitle || ""}`;
/** وش يفتح الزر (للعرض في الجدول) */
function targetText(a) {
  if (a.action === "store") { const s = state.storesAll.find((x) => x.uid === a.value); return s ? `🏪 ${esc(s.name)}` : `<span style="color:#B42318">⚠️ متجر غير معروف — عدّله</span>`; }
  if (a.action === "offer") { const o = state.offers.find((x) => x.id === a.value); return o ? `🔥 ${esc(offerLabel(o))}` : `<span style="color:#B42318">⚠️ العرض غير موجود — عدّله</span>`; }
  if (a.action === "url") return `🔗 ${esc((a.value || "").slice(0, 40))}`;
  return (ACTIONS.find(([k]) => k === a.action) || [, "—"])[1];
}
/** خانة «وش يفتح»: قائمة منسدلة للمتاجر والعروض بدل كتابة الرقم */
function valueField(action, val = "") {
  if (action === "store") {
    const list = [...state.storesAll].sort((a, b) => (b.approved === true) - (a.approved === true) || String(a.name || "").localeCompare(String(b.name || ""), "ar"));
    return `<p class="muted">اختر المتجر (ابحث بالاسم أو رقم المتجر)</p><input type="text" id="anPick" placeholder="🔍 مثال: شانز أو 14055" style="width:100%;margin-bottom:6px">
      <select id="anValue" size="6" style="width:100%"><option value="">— اختر —</option>${list.map((s) => `<option value="${esc(s.uid)}" ${s.uid === val ? "selected" : ""}>${esc(storeLabel(s))}</option>`).join("")}</select>`;
  }
  if (action === "offer") {
    const list = liveOffers().sort((a, b) => String(a.restaurantName || "").localeCompare(String(b.restaurantName || ""), "ar"));
    return `<p class="muted">اختر العرض (ابحث باسم المتجر أو العرض)</p><input type="text" id="anPick" placeholder="🔍 ابحث…" style="width:100%;margin-bottom:6px">
      <select id="anValue" size="6" style="width:100%"><option value="">— اختر —</option>${list.map((o) => `<option value="${esc(o.id)}" ${o.id === val ? "selected" : ""}>${esc(offerLabel(o))}</option>`).join("")}</select>`;
  }
  if (action === "url") return `<p class="muted">الرابط</p><input type="url" id="anValue" maxlength="300" placeholder="https://..." value="${esc(val)}" style="width:100%">`;
  return `<input type="hidden" id="anValue" value=""><p class="muted">${action === "publish" ? "⚠️ هذا الزر للتجار بس — خلّ «لمين» = التجار" : action === "credits" ? "يفتح رابط سلة (أو صفحة الرصيد) للتجار" : "الإعلان بدون زر"}</p>`;
}
function promoPage() {
  const cr = state.config.credits || {}, items = itemList(creditsOf(state.config).items);
  const now = Date.now();
  const promos = items.filter((it) => it.promo);
  const pe = PR.pedit, ae = PR.edit && PR.anns.find((x) => x.id === PR.edit);
  return `<h2>🏷️ عروض على أسعار لكوفر</h2>
  <div class="card">
    <p class="muted">خصم على سعر خدمة لفترة محددة (مثل اليوم الوطني). التاجر يشوف السعر القديم مشطوب والجديد، وسبب العرض، وكم باقي. لو عرضه حصري ناخذ الخصم الأكبر (ما نجمعهم). كل خدمة عليها عرض واحد.</p>
    ${promos.length ? `<table class="tbl"><thead><tr><th>الخدمة</th><th>الخصم</th><th>السبب</th><th>الفترة</th><th>لمين</th><th>الحالة</th><th></th></tr></thead><tbody>${promos.map((it) => { const p = it.promo, st = tsMs(p.startsAt), en = tsMs(p.endsAt);
      return `<tr ${pe === it.id ? 'style="background:#FFF7E0"' : ""}><td>${esc(it.name)}</td><td><b>${p.pct}%</b></td><td>${esc(p.reason || "")}</td><td class="muted">${new Date(st).toLocaleString("ar-SA-u-ca-gregory-nu-latn")}<br>${new Date(en).toLocaleString("ar-SA-u-ca-gregory-nu-latn")}</td>
        <td>${p.newDays > 0 ? `الجدد (أول ${p.newDays} يوم)` : "كل التجار"}</td><td>${now < st ? "🔜 قريباً" : now < en ? "🟢 شغّال" : "⏹️ منتهي"}</td><td style="white-space:nowrap"><button class="btn" data-predit="${esc(it.id)}" title="تعديل">✏️</button> <button class="btn danger" data-prdel="${esc(it.id)}">حذف</button></td></tr>`; }).join("")}</tbody></table>` : `<p class="muted">ما فيه عروض على الأسعار الحين.</p>`}
    <div id="prForm">
    <h3>${pe ? `✏️ تعديل عرض «${esc(creditsOf(state.config).items[pe]?.name || pe)}»` : "＋ عرض جديد"}</h3>
    <div class="row" style="flex-wrap:wrap;align-items:center;gap:8px">
      <span>الخدمة</span><select id="prItem" ${pe ? "disabled" : ""}>${pe ? "" : `<option value="*">كل الخدمات</option>`}${items.filter((it) => it.active || it.id === pe).map((it) => `<option value="${esc(it.id)}" ${it.id === pe ? "selected" : ""}>${esc(it.name)} (${it.price} نقطة)</option>`).join("")}</select>
      <span>الخصم %</span><input type="number" id="prPct" min="1" max="100" value="50" style="width:70px">
      <span>السبب</span><input type="text" id="prReason" maxlength="60" placeholder="🇸🇦 بمناسبة اليوم الوطني" style="min-width:220px">
    </div>
    <div class="row" style="flex-wrap:wrap;align-items:center;gap:8px;margin-top:8px">
      <span>من</span><input type="datetime-local" id="prFrom" value="${dtv(now)}">
      <span>إلى</span><input type="datetime-local" id="prTo" value="${dtv(now + 7 * 864e5)}">
      <label class="switch"><input type="checkbox" id="prNew"> للتجار الجدد بس، أول</label><input type="number" id="prNewDays" min="1" max="365" value="14" style="width:64px"> يوم من تسجيلهم
    </div>
    ${pe ? "" : `<label class="switch" style="margin-top:8px"><input type="checkbox" id="prAnn" checked> 📣 سوّ إعلان للتجار عن العرض (يتعبّى لحاله)</label>`}
    <div class="actions"><button class="btn primary" id="prSave">${pe ? "حفظ التعديل" : "حفظ العرض"}</button>${pe ? ` <button class="btn" id="prCancel">إلغاء التعديل</button>` : ""}</div>
    </div>
    <h3>🛒 رابط عرض سلة (اختياري)</h3>
    <p class="muted">لو سويت خصم على باقات الرصيد في سلة، حط الرابط هنا ويطلع للتاجر زر «اشترِ رصيد بخصم».</p>
    <div class="row"><input type="url" id="prStore" value="${esc(cr.promoStoreUrl || "")}" placeholder="https://..." style="flex:1"><button class="btn" id="prStoreSave">حفظ</button></div>
  </div>

  <h2>📣 مستطيل إعلانات لكوفر</h2>
  <div class="card">
    <p class="muted">يطلع شريط رفيع تحت الخريطة (الزبون يقدر يخفيه لين بكرة)، وفي «حسابي» بنر داخل الصفحة يتقلّب لو أكثر من إعلان. الشارة الحمراء تنحسب لحالها من تاريخ النهاية لو تركتها فاضية.</p>
    ${PR.anns.length ? `<table class="tbl"><thead><tr><th>الإعلان</th><th>الزر يفتح</th><th>لمين / وين</th><th>الفترة</th><th>👁</th><th>👆</th><th>الحالة</th><th></th></tr></thead><tbody>${PR.anns.map((a) => { const st = tsMs(a.startsAt), en = tsMs(a.endsAt), on = a.active !== false;
      return `<tr ${PR.edit === a.id ? 'style="background:#FFF7E0"' : ""}><td><span style="display:inline-block;width:10px;height:10px;border-radius:3px;background:${esc({ navy: "#10318F", gold: "#F4B400", green: "#15803D" }[a.color] || a.color || "#10318F")}"></span> <b>${esc(a.title)}</b><br><small class="muted">${esc(a.sub || "")}</small></td><td><small>${targetText(a)}</small></td>
        <td>${{ all: "الكل", customers: "الزبائن", merchants: "التجار" }[a.audience] || "الكل"}<br><small>${[a.places?.map ? "شريط الخريطة" : "", a.places?.account ? "حسابي" : ""].filter(Boolean).join(" + ")}</small></td>
        <td class="muted">${new Date(st).toLocaleDateString("ar-SA-u-ca-gregory-nu-latn")}<br>${new Date(en).toLocaleDateString("ar-SA-u-ca-gregory-nu-latn")}</td><td>${a.views || 0}</td><td>${a.clicks || 0}${a.views ? ` <small class="muted">(${Math.round(((a.clicks || 0) / a.views) * 100)}%)</small>` : ""}</td>
        <td>${!on ? "⏸️ موقف" : now < st ? "🔜 قريباً" : now < en ? "🟢 شغّال" : "⏹️ منتهي"}</td>
        <td style="white-space:nowrap"><button class="btn" data-anedit="${esc(a.id)}" title="تعديل">✏️</button> <button class="btn" data-anon="${esc(a.id)}">${on ? "إيقاف" : "تشغيل"}</button> <button class="btn danger" data-andel="${esc(a.id)}">حذف</button></td></tr>`; }).join("")}</tbody></table>` : `<p class="muted">ما فيه إعلانات للحين.</p>`}
    <div id="anForm">
    <h3>${ae ? `✏️ تعديل «${esc(ae.title)}»` : "＋ إعلان جديد"}</h3>
    <div class="grid2" style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
      <div><p class="muted">العنوان الكبير *</p><input type="text" id="anTitle" maxlength="60" placeholder="🎉 خصم 10 ريال على أي طلب" style="width:100%"></div>
      <div><p class="muted">السطر الصغير</p><input type="text" id="anSub" maxlength="90" placeholder="من شانز كافيه — لين نهاية الأسبوع" style="width:100%"></div>
      <div><p class="muted">نص الزر</p><input type="text" id="anCta" maxlength="30" placeholder="اطلب الحين" style="width:100%"></div>
      <div><p class="muted">الشارة الحمراء (اختياري)</p><input type="text" id="anBadge" maxlength="30" placeholder="فاضي = ينتهي خلال X أيام" style="width:100%"></div>
      <div><p class="muted">الزر يسوي</p><select id="anAction" style="width:100%">${ACTIONS.map(([k, l]) => `<option value="${k}">${l}</option>`).join("")}</select></div>
      <div id="anValWrap">${valueField("store")}</div>
      <div><p class="muted">لمين</p><select id="anAud" style="width:100%"><option value="all">الكل</option><option value="customers">الزبائن</option><option value="merchants">التجار</option></select></div>
      <div><p class="muted">اللون (جاهز أو اختر أي لون)</p><div class="row" id="anColors" style="flex-wrap:wrap;align-items:center;gap:8px">${[["navy", "كحلي"], ["gold", "ذهبي"], ["green", "أخضر"]].map(([k, l]) => `<label><input type="radio" name="anColor" value="${k}" ${PR.color === k ? "checked" : ""}> ${l}</label>`).join(" ")}
        <label>🎨 <input type="color" id="anColorPick" value="${/^#/.test(PR.color) ? PR.color : "#E5484D"}"></label><input type="text" id="anColorHex" maxlength="7" placeholder="#RRGGBB" value="${/^#/.test(PR.color) ? PR.color : ""}" style="width:90px"></div></div>
      <div><p class="muted">من</p><input type="datetime-local" id="anFrom" value="${dtv(now)}"></div>
      <div><p class="muted">إلى</p><input type="datetime-local" id="anTo" value="${dtv(now + 7 * 864e5)}"></div>
    </div>
    <p class="muted" style="margin-top:10px">معاينة</p><div id="anPrev" style="max-width:360px"></div>
    <div class="row" style="margin-top:8px"><label class="switch"><input type="checkbox" id="anMap" checked> شريط تحت الخريطة</label><label class="switch"><input type="checkbox" id="anAcc" checked> في «حسابي»</label></div>
    <details style="margin-top:8px"><summary>🌐 النص بالإنجليزي (اختياري)</summary>
      <div class="row" style="flex-wrap:wrap;gap:8px"><input type="text" id="anTitleEn" placeholder="Title" maxlength="60"><input type="text" id="anSubEn" placeholder="Subtitle" maxlength="90"><input type="text" id="anCtaEn" placeholder="Button" maxlength="30"></div></details>
    <div class="actions"><button class="btn primary" id="anSave">${ae ? "حفظ التعديل" : "نشر الإعلان"}</button>${ae ? ` <button class="btn" id="anCancel">إلغاء التعديل</button>` : ""}</div>
    </div>
  </div>`;
}
function attachPromo() {
  if (!PR.unsub) PR.unsub = onSnapshot(query(collection(db, "announcements"), orderBy("createdAt", "desc"), limit(50)), (s) => { PR.anns = s.docs.map((d) => ({ ...d.data(), id: d.id })); if (state.tab === "promo") render(); }, (e) => console.warn(e));
  const v = (id) => $(id)?.value?.trim() || "";
  const ms = (id) => { const x = Date.parse($(id).value); return isNaN(x) ? 0 : x; };
  // ✍️ المسودة: نحفظ اللي انكتب عشان إعادة الرسم (تحديثات مباشرة) ما تمسحه
  const D = PR.draft;
  const setVal = (id, val) => { const el = $(id); if (!el || val === undefined) return; if (el.type === "checkbox") el.checked = !!val; else el.value = val; };
  if (D.anAction) { $("anAction").value = D.anAction; }
  $("anValWrap").innerHTML = valueField($("anAction").value, D.anValue || "");
  Object.entries(D).forEach(([k, val]) => { if (k !== "anAction" && k !== "anValue") setVal(k, val); });
  const bindPick = () => {
    const pick = $("anPick"), sel = $("anValue");
    if (pick && sel && sel.tagName === "SELECT") {
      if (D.anPick) pick.value = D.anPick;
      const filter = () => { const q = pick.value.trim().toLowerCase(); [...sel.options].forEach((o, i) => { o.hidden = i > 0 && !!q && !o.textContent.toLowerCase().includes(q) && o.value !== sel.value; }); };
      pick.oninput = () => { D.anPick = pick.value; filter(); }; filter();
    }
  };
  bindPick();
  ["prForm", "anForm"].forEach((f) => $(f)?.addEventListener("input", (e) => { const el = e.target; if (el.id && el.name !== "anColor") D[el.id] = el.type === "checkbox" ? el.checked : el.value; }));
  ["prForm", "anForm"].forEach((f) => $(f)?.addEventListener("change", (e) => { const el = e.target; if (el.id && el.name !== "anColor") D[el.id] = el.type === "checkbox" ? el.checked : el.value; }));
  $("anAction").addEventListener("change", () => { D.anValue = ""; D.anPick = ""; $("anValWrap").innerHTML = valueField($("anAction").value, ""); bindPick();
    if ($("anAction").value === "publish" || $("anAction").value === "credits") { $("anAud").value = "merchants"; D.anAud = "merchants"; } prev(); });

  // ---- عروض الأسعار ----
  $("prSave").onclick = async () => {
    const pct = Math.min(100, Math.max(1, parseInt($("prPct").value, 10) || 0)), from = ms("prFrom"), to = ms("prTo"), item = PR.pedit || $("prItem").value;
    if (!(to > from)) return toast("تاريخ النهاية لازم بعد البداية");
    const promo = { pct, reason: v("prReason").slice(0, 60), startsAt: Timestamp.fromMillis(from), endsAt: Timestamp.fromMillis(to), newDays: $("prNew").checked ? Math.max(1, parseInt($("prNewDays").value, 10) || 14) : 0 };
    const ids = item === "*" ? itemList(creditsOf(state.config).items).filter((it) => it.active).map((it) => it.id) : [item];
    const cur = (state.config.credits || {}).items;
    const upd = {};
    if (!cur) { const all = creditsOf(state.config).items; Object.entries(all).forEach(([k, it]) => { upd[`credits.items.${k}`] = ids.includes(k) ? { ...it, promo } : it; }); }
    else ids.forEach((k) => { upd[`credits.items.${k}.promo`] = promo; });
    const wasEdit = !!PR.pedit, mkAnn = !wasEdit && $("prAnn")?.checked;
    try {
      await updateDoc(doc(db, "config", "app"), upd); toast(wasEdit ? "انحفظ التعديل ✅" : "انحفظ العرض ✅");
      if (mkAnn) {
        const name = item === "*" ? "كل الخدمات" : creditsOf(state.config).items[item]?.name || "";
        await addDoc(collection(db, "announcements"), { title: `${promo.reason ? promo.reason.split(" ")[0] + " " : "🏷️ "}خصم ${pct}% على ${name}`.slice(0, 60), sub: promo.reason || "لفترة محدودة", cta: "انشر عرضك الحين",
          action: "publish", value: "", color: "navy", badge: "", audience: "merchants", places: { map: true, account: true }, startsAt: promo.startsAt, endsAt: promo.endsAt, active: true, views: 0, clicks: 0, createdAt: serverTimestamp() });
        toast("وسوّينا إعلان للتجار 📣 تقدر تعدّله بالقلم ✏️");
      }
      ["prItem", "prPct", "prReason", "prFrom", "prTo", "prNew", "prNewDays", "prAnn"].forEach((k) => delete PR.draft[k]); PR.pedit = null; render();
    } catch (e) { console.error(e); toast("تعذّر الحفظ"); }
  };
  $("prCancel")?.addEventListener("click", () => { ["prItem", "prPct", "prReason", "prFrom", "prTo", "prNew", "prNewDays"].forEach((k) => delete PR.draft[k]); PR.pedit = null; render(); });
  $("content").querySelectorAll("[data-predit]").forEach((b) => b.onclick = () => {
    const it = creditsOf(state.config).items[b.dataset.predit], p = it?.promo; if (!p) return;
    PR.pedit = b.dataset.predit;
    Object.assign(PR.draft, { prPct: String(p.pct), prReason: p.reason || "", prFrom: dtv(tsMs(p.startsAt)), prTo: dtv(tsMs(p.endsAt)), prNew: p.newDays > 0, prNewDays: String(p.newDays || 14) });
    render(); $("prForm")?.scrollIntoView({ behavior: "smooth", block: "center" });
  });
  $("content").querySelectorAll("[data-prdel]").forEach((b) => b.onclick = async () => {
    if (!confirm("حذف العرض؟ الأسعار ترجع طبيعية فوراً")) return;
    try { await updateDoc(doc(db, "config", "app"), { [`credits.items.${b.dataset.prdel}.promo`]: deleteField() }); toast("انحذف"); } catch (e) { console.error(e); toast("تعذّر"); }
  });
  $("prStoreSave").onclick = () => updateDoc(doc(db, "config", "app"), { "credits.promoStoreUrl": v("prStore").slice(0, 300) }).then(() => toast("انحفظ ✅")).catch(() => toast("تعذّر"));

  // ---- الإعلانات ----
  const PRESET = { navy: ["#10318F", "#0B2266", "#fff"], gold: ["#F4B400", "#E0A300", "#0B2266"], green: ["#15803D", "#106631", "#fff"] };
  const colorsOf = (c) => { if (PRESET[c]) return PRESET[c]; if (!/^#[0-9a-f]{6}$/i.test(c || "")) return PRESET.navy; const n = parseInt(c.slice(1), 16), r = n >> 16, g = (n >> 8) & 255, b = n & 255;
    return [c, "#" + [r, g, b].map((x) => Math.round(x * 0.82).toString(16).padStart(2, "0")).join(""), (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.6 ? "#111827" : "#fff"]; };
  const prev = () => { const [bg, dk, fg] = colorsOf(PR.color); const end = ms("anTo"), d = Math.ceil((end - Date.now()) / 864e5);
    $("anPrev").innerHTML = `<div style="border-radius:18px;overflow:hidden;color:${fg};direction:rtl"><div style="background:${bg};padding:12px 14px"><div style="display:flex;justify-content:space-between"><b style="opacity:.8">%</b><span style="background:#E5484D;color:#fff;border-radius:8px;padding:1px 8px;font-size:12px">${esc(v("anBadge") || (d <= 1 ? "آخر يوم" : `ينتهي خلال ${d} أيام`))}</span></div>
      <div style="font-weight:800;font-size:18px;margin-top:6px">${esc(v("anTitle") || "العنوان الكبير")}</div><div style="opacity:.9;font-size:13px">${esc(v("anSub"))}</div></div>
      ${$("anAction").value !== "none" ? `<div style="background:${dk};padding:10px 14px;font-weight:800">‹ ${esc(v("anCta") || "اعرف أكثر")}</div>` : ""}</div>`; };
  $("content").querySelectorAll('input[name="anColor"]').forEach((r) => r.onchange = () => { PR.color = r.value; $("anColorHex").value = ""; prev(); });
  const setHex = (h) => { if (!/^#[0-9a-f]{6}$/i.test(h)) return; PR.color = h.toUpperCase(); $("content").querySelectorAll('input[name="anColor"]').forEach((r) => r.checked = false); $("anColorPick").value = h; $("anColorHex").value = PR.color; prev(); };
  $("anColorPick").oninput = (e) => setHex(e.target.value);
  $("anColorHex").oninput = (e) => setHex(e.target.value.trim());
  ["anTitle", "anSub", "anCta", "anBadge", "anTo"].forEach((id) => { $(id).addEventListener("input", prev); $(id).addEventListener("change", prev); });
  prev();
  $("anSave").onclick = async () => {
    const from = ms("anFrom"), to = ms("anTo");
    if (!v("anTitle")) return toast("اكتب العنوان");
    if (!(to > from)) return toast("تاريخ النهاية لازم بعد البداية");
    if (!$("anMap").checked && !$("anAcc").checked) return toast("اختر وين يطلع");
    const action = $("anAction").value, value = v("anValue");
    if (action === "url" && !/^https?:\/\//.test(value)) return toast("اكتب رابط يبدأ بـ https://");
    if (action === "store" && !state.storesAll.some((s) => s.uid === value)) return toast("اختر المتجر من القائمة");
    if (action === "offer" && !state.offers.some((o) => o.id === value)) return toast("اختر العرض من القائمة");
    if ((action === "publish" || action === "credits") && $("anAud").value !== "merchants") return toast("هالزر للتجار بس — غيّر «لمين» إلى التجار");
    const data = { title: v("anTitle"), sub: v("anSub"), cta: v("anCta") || (action === "none" ? "" : "اعرف أكثر"), badge: v("anBadge"), action, value: ["store", "offer", "url"].includes(action) ? value : "",
      color: PR.color, audience: $("anAud").value, places: { map: $("anMap").checked, account: $("anAcc").checked },
      titleEn: v("anTitleEn"), subEn: v("anSubEn"), ctaEn: v("anCtaEn"), startsAt: Timestamp.fromMillis(from), endsAt: Timestamp.fromMillis(to) };
    try {
      if (PR.edit) { await updateDoc(doc(db, "announcements", PR.edit), data); toast("انحفظ التعديل ✅"); }
      else { await addDoc(collection(db, "announcements"), { ...data, active: true, views: 0, clicks: 0, createdAt: serverTimestamp() }); toast("انتشر الإعلان ✅"); }
      Object.keys(PR.draft).filter((k) => k.startsWith("an")).forEach((k) => delete PR.draft[k]); PR.edit = null; PR.color = "navy"; render();
    } catch (e) { console.error(e); toast("تعذّر الحفظ"); }
  };
  $("anCancel")?.addEventListener("click", () => { Object.keys(PR.draft).filter((k) => k.startsWith("an")).forEach((k) => delete PR.draft[k]); PR.edit = null; PR.color = "navy"; render(); });
  $("content").querySelectorAll("[data-anedit]").forEach((b) => b.onclick = () => {
    const a = PR.anns.find((x) => x.id === b.dataset.anedit); if (!a) return;
    PR.edit = a.id; PR.color = a.color || "navy";
    Object.keys(PR.draft).filter((k) => k.startsWith("an")).forEach((k) => delete PR.draft[k]);
    Object.assign(PR.draft, { anTitle: a.title || "", anSub: a.sub || "", anCta: a.cta || "", anBadge: a.badge || "", anAction: a.action || "none", anValue: a.value || "", anAud: a.audience || "all",
      anFrom: dtv(tsMs(a.startsAt)), anTo: dtv(tsMs(a.endsAt)), anMap: !!a.places?.map, anAcc: !!a.places?.account, anTitleEn: a.titleEn || "", anSubEn: a.subEn || "", anCtaEn: a.ctaEn || "" });
    render(); $("anForm")?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
  $("content").querySelectorAll("[data-anon]").forEach((b) => b.onclick = () => { const a = PR.anns.find((x) => x.id === b.dataset.anon); updateDoc(doc(db, "announcements", a.id), { active: a.active === false }).catch(() => toast("تعذّر")); });
  $("content").querySelectorAll("[data-andel]").forEach((b) => b.onclick = () => { if (confirm("حذف الإعلان؟")) deleteDoc(doc(db, "announcements", b.dataset.andel)).catch(() => toast("تعذّر")); });
}
