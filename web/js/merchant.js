// لوحة التاجر على الويب: صفحة متجري (الفروع، الشعار، المنيو، التوصيل) + نشر وتعديل العروض.
import {
  collection, doc, onSnapshot, query, where, setDoc, updateDoc, deleteDoc, getDocs, getDoc, serverTimestamp, Timestamp, writeBatch, orderBy, limit
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { MAPS_WEB_KEY } from "../config.js";
import { staffCardHtml, bindStaffCard } from "./staff.js";
import { db, state, $, toast, showSheet, hideSheet, openPage, closePage, uploadImage, openOffer, openStore, getMyLocation, categories, takeClaim, pendingRef, clearRef } from "./app.js";
import * as X from "./logic.js";
import { createMap } from "./map.js";
import { cropImage } from "./cropper.js";
import { watchMerchantCatalog, catalogPanelHtml, bindCatalogPanel, loadMyItems, scopePickerHtml, bindScopePicker, scopeError } from "./catalog-ui.js";
import { vatOf, validVatNumber, feat, catalogOf, DEAL_TYPES, KINDS, fmt } from "./catalog.js";
import { creditsOf, itemList, costOf, activeEffects, isPaidUp, graceEnd, exclusiveOf, EFFECTS, spotlightsInDistrict, rewardOf, discounted, rewardText } from "./credits.js";

const { esc, cld, thumb, priceLabel, districtLabel, money } = X;
const DAILY_LIMIT = 3;
const m = { uid: null, profile: null, status: null, offers: [], unsubs: [], root: null, ctx: null, wallet: undefined, boosts: [] };
const cr = () => creditsOf(state.config);
const bal = () => m.wallet?.balance || 0;
const rw = () => rewardOf(state.config);
const fmtDate = (t) => { const d = new Date(t); return `${d.getDate()}/${d.getMonth() + 1}`; };

const canPublishDirectly = () => m.ctx?.isAdmin || (m.status?.trusted === true && m.status?.suspended !== true);

export function renderMerchant(root, ctx) {
  m.root = root; m.ctx = ctx;
  const uid = state.user.uid;
  if (m.uid !== uid) {
    m.unsubs.forEach((u) => u()); m.unsubs = []; m.uid = uid; m.profile = undefined; m.status = undefined; m.offers = [];
    m.unsubs.push(onSnapshot(doc(db, "merchants", uid), (s) => { m.profile = s.exists() ? s.data() : null; draw(); }, () => { m.profile = null; draw(); }));
    m.unsubs.push(onSnapshot(doc(db, "merchantStatus", uid), (s) => { m.status = s.exists() ? s.data() : null; draw(); }, () => { m.status = null; draw(); }));
    m.wallet = undefined; m.boosts = [];
    m.unsubs.push(onSnapshot(doc(db, "wallets", uid), (s) => { m.wallet = s.exists() ? s.data() : null; ensureWallet(); draw(); }, () => { m.wallet = null; draw(); }));
    m.unsubs.push(onSnapshot(query(collection(db, "boosts"), where("merchantId", "==", uid)), (snap) => { m.boosts = snap.docs.map((d) => ({ ...d.data(), id: d.id })); draw(); }, () => {}));
    watchMerchantCatalog(uid, () => draw());
    m.unsubs.push(onSnapshot(query(collection(db, "offers"), where("merchantId", "==", uid)), (snap) => {
      m.offers = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => (X.ms(b.createdAt) || 0) - (X.ms(a.createdAt) || 0)); draw();
    }, (e) => console.error(e)));
  }
  draw();
}

function draw() {
  const root = m.root; if (!root || !root.isConnected) return;
  if (m.profile === undefined || m.status === undefined) { root.innerHTML = `<div class="spinner"></div>`; return; }
  const p = m.profile || {}, st = m.status || {}, now = Date.now();
  const hasStore = (p.branches || []).length > 0;
  const active = m.offers.filter((o) => o.isApproved && !o.draft && !X.isExpired(o, now));
  const sum = (k) => m.offers.reduce((a, o) => a + (o[k] || 0), 0);
  const banner = st.suspended ? `<div class="banner red">⛔ حسابك موقوف حالياً. تواصل مع إدارة لكوفر.</div>`
    : canPublishDirectly() ? `<div class="banner green">⚡ حسابك ينشر مباشرة — عروضك تظهر بدون مراجعة</div>`
    : `<div class="banner amber">⏳ حساب جديد: عروضك تنراجع قبل ما تظهر (عادة خلال ساعات). بعد عرضين معتمدين تنشر مباشرة.</div>`;
  const warn = st.suspended || !canPublishDirectly();
  root.innerHTML = `
    ${m.ctx.isAdmin ? `<a class="btn dir" href="/admin/">🛡️ لوحة الإدارة</a>` : ""}
    ${warn ? banner : ""}
    <div class="store-card">
      ${p.logoUrl ? `<img src="${esc(cld(p.logoUrl, 120))}" alt="">` : `<div class="logo-ph">🏪</div>`}
      <div class="sc-main"><b>${esc(p.restaurantName || "متجرك")}</b>${st.verified ? ' <span class="verified">✔ موثّق</span>' : ""}<br>
        <small>${hasStore ? `${p.branches.length} ${p.branches.length === 1 ? "فرع" : "فروع"} • ${esc(p.cuisineType || "")}` : "ما جهّزت صفحة متجرك للحين"}</small></div>
      <button class="btn-soft" id="editStore">✎ صفحة متجري</button>
    </div>
    <button class="btn dir big" id="newOffer" ${st.suspended ? "disabled" : ""}>＋ عرض جديد</button>
    ${hasStore ? `<button class="btn call" id="viewStore">👀 شوف صفحة متجري مثل ما يشوفها الزبون</button>` : ""}
    ${hasStore ? catalogPanelHtml(p.cuisineType) : ""}
    ${hasStore ? staffCardHtml() : ""}
    ${m.offers.some((o) => o.adminNote) ? `<div class="banner red">↩️ عندك ${m.offers.filter((o) => o.adminNote).length === 1 ? "عرض يحتاج تعديل" : m.offers.filter((o) => o.adminNote).length + " عروض تحتاج تعديل"} من فريق لكوفر — شوف الملاحظة تحت العرض واضغط ✎</div>` : ""}
    <div class="section-title">عروضي (${m.offers.length})</div>
    <div class="mlist">${m.offers.length ? m.offers.map(offerRow).join("") : `<div class="empty-inline">ما نشرت عروض للحين — ابدأ بأول عرض 🔥</div>`}</div>
    ${walletCard()}
    <div class="stat-row">
      <div class="stat"><b>${active.length}</b><small>عرض نشط</small></div>
      <div class="stat"><b>${sum("viewsCount")}</b><small>مشاهدة</small></div>
      <div class="stat"><b>${sum("directionsCount")}</b><small>اتجاهات</small></div>
      <div class="stat"><b>${sum("whatsappCount")}</b><small>واتساب</small></div>
    </div>
    ${!warn ? banner : ""}
    ${!st.verified ? `<div class="hint-box">✔ توثيق المتجر (السجل التجاري / العمل الحر) متاح حالياً من تطبيق أندرويد.</div>` : ""}
    <button class="btn call" id="mContact">💬 تواصل معنا</button>`;
  $("editStore").onclick = () => openStoreForm();
  bindStaffCard(p.restaurantName || "");
  $("viewStore")?.addEventListener("click", () => openStore(state.user.uid, true));
  // جاي من "أنا صاحب المحل" على الخريطة: نفتح صفحة المتجر بموقع المحل
  const claim = takeClaim();
  if (claim) setTimeout(() => openStoreForm(claim), 50);
  $("newOffer").onclick = () => hasStore ? openOfferForm(null) : (toast("جهّز صفحة متجرك وموقعك أول 📍"), openStoreForm());
  $("mContact").onclick = m.ctx.contact;
  $("wRedeem")?.addEventListener("click", openRedeem);
  bindCatalogPanel({ isAdmin: () => !!m.ctx?.isAdmin, direct: () => canPublishDirectly() || (m.status?.verified === true && m.status?.suspended !== true), balance: bal,
    category: () => m.profile?.cuisineType || "", vatNumber: () => (vatOf(state.config).enabled && validVatNumber(m.profile?.vatNumber) ? m.profile.vatNumber : "") });
  $("wHistory")?.addEventListener("click", openHistory);
  root.querySelectorAll("[data-pub]").forEach((b) => b.onclick = () => publishDraft(m.offers.find((o) => o.id === b.dataset.pub)));
  root.querySelectorAll("[data-boost]").forEach((b) => b.onclick = () => openBoost(m.offers.find((o) => o.id === b.dataset.boost)));
  root.querySelectorAll("[data-edit]").forEach((b) => b.onclick = () => openOfferForm(m.offers.find((o) => o.id === b.dataset.edit)));
  root.querySelectorAll("[data-del]").forEach((b) => b.onclick = () => confirmDelete(m.offers.find((o) => o.id === b.dataset.del)));
  root.querySelectorAll("[data-view]").forEach((b) => b.onclick = () => { if (state.offers.some((o) => o.id === b.dataset.view)) openOffer(b.dataset.view, true); else toast("العرض بيظهر للناس بعد ما ينعتمد"); });
}

function offerRow(o) {
  const now = Date.now(), exp = X.isExpired(o, now), hidden = (o.reportsCount || 0) >= 3;
  const chip = o.adminNote ? `<span class="pill urgent">↩️ يحتاج تعديل</span>` : o.draft ? `<span class="pill urgent">📝 مسودة — بانتظار الرصيد</span>` : !o.isApproved ? `<span class="pill">⏳ بانتظار المراجعة</span>` : hidden ? `<span class="pill urgent">🚩 مخفي للمراجعة</span>`
    : exp ? `<span class="pill closed">منتهي</span>` : (() => { const r = X.remainingText(o, now); return r ? `<span class="pill ${r.cls}">${esc(r.t)}</span>` : `<span class="pill open">نشط</span>`; })();
  return `<div class="mrow${exp ? " dim" : ""}${o.draft ? " draft" : ""}">
    <img src="${esc(thumb(o, 160))}" alt="" data-view="${esc(o.id)}">
    <div class="mr-main" data-view="${esc(o.id)}"><b>${esc(o.offerTitle)}</b><div class="price">${esc(priceLabel(o))}</div>
      ${o.adminNote ? `<div class="admin-note">📝 ${esc(o.adminNote)}</div>` : ""}
      <div class="pills">${chip}${o.draft ? "" : paidChips(o)}<span class="pill closed">👁 ${o.viewsCount || 0}</span></div></div>
    ${o.draft ? `<button class="btn dir pub-now" data-pub="${esc(o.id)}">📢 انشر الحين</button>` : ""}
    <div class="mr-act">${cr().enabled && !exp && !o.draft ? `<button class="icon-btn" data-boost="${esc(o.id)}" aria-label="تمييز">🚀</button>` : ""}<button class="icon-btn" data-edit="${esc(o.id)}" aria-label="تعديل">✎</button><button class="icon-btn" data-del="${esc(o.id)}" aria-label="حذف">🗑</button></div>
  </div>`;
}

// =================== رصيد العروض ===================
let ensuring = false;
async function ensureWallet() {
  // أول ما يتفعّل الرصيد: ننشئ محفظة التاجر (مع هدية الترحيب إن وجدت)
  if (ensuring || m.wallet !== null || !cr().enabled || !state.user) return;
  ensuring = true;
  try {
    const w = cr().welcome || 0, wb = writeBatch(db);
    wb.set(doc(db, "wallets", m.uid), { balance: w, lastOp: { kind: "welcome", ref: "" }, updatedAt: serverTimestamp() });
    if (w > 0) wb.set(doc(collection(db, "wallets", m.uid, "tx")), { kind: "welcome", points: w, note: "🎁 هدية الترحيب من لكوفر", ref: "", at: serverTimestamp() });
    await wb.commit();
    if (w > 0) toast(`🎁 أهلاً بك! أضفنا لك ${w} نقطة هدية`, true);
  } catch (e) { console.warn("wallet", e); }
  ensuring = false;
}
function walletCard() {
  if (!cr().enabled && !m.wallet) return "";
  const now = Date.now(), inGrace = m.offers.filter((o) => { const g = graceEnd(o, cr()); return g > now && g !== Infinity && !X.isExpired(o, now) && activeEffects(m.boosts, o.id, now).publishUntil <= now; });
  const graceNote = inGrace.length ? `<div class="hint-box">🕐 عندك ${inGrace.length} ${inGrace.length === 1 ? "عرض قديم مجاني" : "عروض قديمة مجانية"} لين ${fmtDate(graceEnd(inGrace[0], cr()))}. بعدها تحتاج تمديد بالرصيد من زر 🚀 عشان تبقى ظاهرة.</div>` : "";
  const store = state.config.credits?.storeUrl;
  return `<div class="wallet-card">
    <div class="wc-top"><div><small>💰 رصيد العروض</small><b>${bal()}</b><small>نقطة</small></div>
      <div class="wc-prices">${itemList(cr().items).filter((it) => it.active).slice(0, 4).map((it) => `<span>${esc(it.name)}: <b>${it.price}</b>${it.unit === "day" ? "/يوم" : ""}</span>`).join("")}</div></div>
    <div class="wc-act">
      <button class="btn call" id="wRedeem">🔑 شحن بكود</button>
      ${store ? `<a class="btn call" href="${esc(store)}" target="_blank" rel="noopener">🛒 اشترِ رصيد</a>` : ""}
      <button class="btn ghost" id="wHistory">📜 الحركات</button>
    </div></div>${graceNote}`;
}
function paidChips(o) {
  if (!cr().enabled && !m.boosts.length) return "";
  const now = Date.now(), ef = activeEffects(m.boosts, o.id, now), out = [];
  if (ef.feature) out.push(`<span class="pill feat">✨ مميّز</span>`);
  if (ef.listTop) out.push(`<span class="pill feat">📌 بارز</span>`);
  const g = graceEnd(o, cr());
  if (o.isApproved && !X.isExpired(o, now) && !isPaidUp({ ...o, id: o.id }, m.boosts, cr(), now)) out.push(`<span class="pill urgent">⚠️ غير منشور — مدّده بـ🚀</span>`);
  else if (ef.publishUntil > now) out.push(`<span class="pill">📢 منشور لين ${fmtDate(ef.publishUntil)}</span>`);
  else if (g > now && g !== Infinity) out.push(`<span class="pill urgent">🕐 مجاني لين ${fmtDate(g)} — بعدها يحتاج رصيد</span>`);
  return out.join("");
}
function openRedeem() {
  showSheet(`<div class="pad form"><div class="section-title">🔑 شحن الرصيد بكود</div>
    <p class="desc tr">اكتب الكود اللي وصلك بعد الشراء (مثال: HY-K7Q2-9XMP)</p>
    <input id="rCode" class="input" dir="ltr" autocomplete="off" placeholder="HY-XXXX-XXXX" maxlength="16" style="text-transform:uppercase;letter-spacing:2px;text-align:center">
    <div id="rErr" class="err"></div><button class="btn dir" id="rGo">شحن</button></div>`);
  $("rCode").focus();
  $("rGo").onclick = async () => {
    const raw = $("rCode").value.toUpperCase().replace(/[^A-Z0-9]/g, "");
    const code = raw.length === 10 && raw.startsWith("HY") ? `HY-${raw.slice(2, 6)}-${raw.slice(6)}` : $("rCode").value.trim().toUpperCase();
    const err = (t) => { $("rErr").textContent = t; };
    if (!/^HY-[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(code)) return err("الكود غير صحيح، تأكد منه.");
    $("rGo").disabled = true; err("");
    try {
      const snap = await getDoc(doc(db, "creditCodes", code));
      if (!snap.exists()) { $("rGo").disabled = false; return err("الكود غير صحيح، تأكد منه."); }
      const c = snap.data();
      if (c.redeemedBy) { $("rGo").disabled = false; return err("هذا الكود مستخدم من قبل."); }
      if (c.expiresAt && X.ms(c.expiresAt) < Date.now()) { $("rGo").disabled = false; return err("هذا الكود منتهي."); }
      const wb = writeBatch(db);
      wb.update(doc(db, "creditCodes", code), { redeemedBy: m.uid, redeemedAt: serverTimestamp() });
      wb.set(doc(db, "wallets", m.uid), { balance: bal() + c.points, lastOp: { kind: "redeem", ref: code }, updatedAt: serverTimestamp() });
      wb.set(doc(collection(db, "wallets", m.uid, "tx")), { kind: "redeem", points: c.points, note: "شحن بكود " + code.slice(-4), ref: code, at: serverTimestamp() });
      await wb.commit();
      hideSheet(); toast(`تم الشحن ✅ أضفنا ${c.points} نقطة`, true);
    } catch (e) { console.error(e); $("rGo").disabled = false; err("تعذّر الشحن، حاول مرة ثانية أو تواصل معنا."); }
  };
}
async function openHistory() {
  showSheet(`<div class="pad"><div class="spinner"></div></div>`);
  let list = [];
  try { list = (await getDocs(query(collection(db, "wallets", m.uid, "tx"), orderBy("at", "desc"), limit(50)))).docs.map((d) => d.data()); } catch {}
  showSheet(`<div class="pad"><div class="section-title">📜 حركات الرصيد</div>
    ${list.length ? `<div class="tx-list">${list.map((t) => `<div class="tx"><span>${esc(t.note || "")}</span><small>${t.at ? fmtDate(X.ms(t.at)) : ""}</small><b class="${t.points >= 0 ? "plus" : "minus"}">${t.points > 0 ? "+" : ""}${t.points}</b></div>`).join("")}</div>`
      : `<div class="empty-inline">ما فيه حركات للحين</div>`}</div>`);
}
/** يجهّز عملية صرف (خدمة + خصم) داخل batch. يرجّع null لو الرصيد ما يكفي. */
/** تكلفة الخدمة على هالعرض (النشر عليه خصم لو العرض حصري ومكافأة الحصري شغّالة) */
function priceFor(item, days, excl) {
  const it = { ...cr().items[item], id: item };
  const d = it.unit === "day" ? days : (it.days || 1);
  const full = costOf(it, d), r = rw();
  const disc = it.effect === "publish" && excl && r.on ? r.percent : 0;
  return { it, d, full, cost: disc ? discounted(full, disc) : full, disc };
}
function addSpend(wb, { item, offerId, days, startsAt, note, excl = false }) {
  const { it, d, cost, disc } = priceFor(item, days, excl);
  const admin = !!m.ctx?.isAdmin;
  if (!admin && bal() < cost) return null;
  const ref = doc(collection(db, "boosts"));
  const start = Math.max(Date.now(), startsAt || 0);
  wb.set(ref, { id: ref.id, merchantId: m.uid, offerId, item, effect: it.effect, days: d, cost, ...(disc ? { disc } : {}), createdAt: serverTimestamp(),
    startsAt: Timestamp.fromMillis(start), endsAt: Timestamp.fromMillis(start + d * X.DAY) });
  if (disc) note = `${note || it.name} (💎 ${disc >= 100 ? "مجاني" : `خصم ${disc}%`})`;
  if (!admin && cost > 0) {
    wb.set(doc(db, "wallets", m.uid), { balance: bal() - cost, lastOp: { kind: "spend", ref: ref.id }, updatedAt: serverTimestamp() });
    wb.set(doc(collection(db, "wallets", m.uid, "tx")), { kind: "spend", points: -cost, note: (note || it.name).slice(0, 120), ref: ref.id, at: serverTimestamp() });
  }
  return cost;
}
function openBoost(o) {
  if (!o) return;
  const items = itemList(cr().items).filter((it) => it.active);
  const now = Date.now(), ef = activeEffects(m.boosts, o.id, now);
  const spotUsed = spotlightsInDistrict(state.boosts || [], state.offers, o.district, now), spotMax = cr().spotlightMax;
  showSheet(`<div class="pad"><div class="section-title">🚀 ${esc(o.offerTitle)}</div>
    <p class="desc tr">رصيدك: <b>${bal()}</b> نقطة</p>
    <div class="boost-list">${items.map((it) => {
      const isPub = it.effect === "publish", isSpot = it.effect === "spotlight", full = isSpot && !ef.spotlight && spotUsed >= spotMax;
      return `<div class="boost-item${isSpot ? " spot" : ""}"><div><b>${esc(it.name)}</b><small>${esc(EFFECTS[it.effect]?.hint || "")}${isPub && ef.publishUntil > now ? ` • منشور لين ${fmtDate(ef.publishUntil)}` : ""}</small>
        ${isSpot ? `<small class="spot-left">${full ? `⛔ الأماكن في ${esc(o.district || "حيّك")} ممتلئة (${spotUsed}/${spotMax})، جرّب بعد كم يوم` : `باقي ${spotMax - spotUsed} من ${spotMax} أماكن في ${esc(o.district || "حيّك")}`}</small>` : ""}</div>
        ${it.unit === "day" ? `<select class="input bdays" data-bd="${it.id}">${[1, 3, 7, 14, 30].map((d) => { const pr = priceFor(it.id, d, !!o.exclusive); return `<option value="${d}">${d} ${d === 1 ? "يوم" : "أيام"} • ${pr.disc ? (pr.cost ? `${pr.cost} نقطة بدل ${pr.full} 💎` : "مجاني 💎") : `${pr.full} نقطة`}</option>`; }).join("")}</select>`
          : `<span class="bprice">${it.price} نقطة • ${it.days} ${it.days === 1 ? "يوم" : "أيام"}</span>`}
        <button class="btn dir" data-buy="${it.id}" ${full ? "disabled" : ""}>${isPub ? "تمديد النشر" : "اشترِ"}</button></div>`; }).join("")}</div>
    <div id="bErr" class="err"></div></div>`);
  $("sheetBody").querySelectorAll("[data-buy]").forEach((b) => b.onclick = async () => {
    const id = b.dataset.buy, it = cr().items[id];
    const days = it.unit === "day" ? +($("sheetBody").querySelector(`[data-bd="${id}"]`)?.value || 1) : it.days;
    // التمديد يبدأ من نهاية النشر الحالي (أو نهاية فترة السماح للعروض القديمة)
    const g = graceEnd(o, cr());
    const start = it.effect === "publish" ? Math.max(now, ef.publishUntil, g === Infinity ? 0 : g) : now;
    const wb = writeBatch(db), cost = addSpend(wb, { item: id, offerId: o.id, days, startsAt: start, note: `${it.name} — ${o.offerTitle}`, excl: !!o.exclusive });
    if (cost == null) return $("bErr").textContent = `رصيدك ما يكفي (تحتاج ${priceFor(id, days, !!o.exclusive).cost} نقطة). اشحن رصيدك أول.`;
    if (!confirm(cost === 0 ? `تأكيد: ${it.name} (مجاني لأن عرضك حصري 💎)؟` : `تأكيد: ${it.name} بـ ${cost} نقطة؟`)) return;
    b.disabled = true;
    try { await wb.commit(); hideSheet(); toast(cost ? `تم ✅ خصمنا ${cost} نقطة` : "تم ✅ مجاناً لأن عرضك حصري 💎", true); }
    catch (e) { console.error(e); b.disabled = false; $("bErr").textContent = "تعذّرت العملية. حدّث الصفحة وجرّب مرة ثانية."; }
  });
}

// =================== المسودات (العرض محفوظ، ينتظر الرصيد) ===================
function draftSaved(excl) {
  const r = rw(), exl = exclusiveOf(state.config), store = state.config.credits?.storeUrl;
  showSheet(`<div class="pad"><div class="section-title">انحفظ عرضك ✅</div>
    <p class="desc tr">يتنشر بضغطة وحدة أول ما يصير عندك رصيد. تلقاه في «حسابي» تحت «عروضي» وعليه زر <b>📢 انشر الحين</b>.</p>
    ${r.on && !excl ? `<div class="excl-offer"><b>💡 ترى ${esc(exl.icon)} ${esc(exl.label)} ${r.free ? "نشره مجاني" : `نشره بخصم ${r.percent}%`}</b><p>لو عرضك لعملاء لكوفر بس، اضغط «انشر الحين» واختر «خلّه حصري».</p></div>` : ""}
    <div class="row2"><button class="btn call" id="dsRedeem">🔑 شحن بكود</button>${store ? `<a class="btn call" href="${esc(store)}" target="_blank" rel="noopener">🛒 اشترِ رصيد</a>` : `<button class="btn dir" id="dsOk">تمام</button>`}</div></div>`);
  $("dsRedeem").onclick = openRedeem;
  $("dsOk")?.addEventListener("click", hideSheet);
}
function publishDraft(o) {
  if (!o) return;
  const now = Date.now(), DAY = X.DAY, tiers = (o.dailyPercents || []).length, st = X.ms(o.startsAt);
  const oldBase = st || X.ms(o.createdAt) || now;
  const days = Math.min(90, tiers || (o.expiresAt ? Math.max(1, Math.round((X.ms(o.expiresAt) - oldBase) / DAY)) : 7));
  const start = st && st > now ? st : now;
  const expiry = tiers ? X.midnight(start) + tiers * DAY : start + days * DAY;
  const full = priceFor("publish", days, false), ex = priceFor("publish", days, true), r = rw(), exl = exclusiveOf(state.config);
  const mine = o.exclusive ? ex : full, admin = !!m.ctx?.isAdmin;
  const canPay = admin || bal() >= mine.cost, exclOk = !o.exclusive && r.on && bal() >= ex.cost && ex.cost < full.cost;
  const store = state.config.credits?.storeUrl;
  const label = (p) => p.cost ? `بـ ${p.cost} نقطة` : "مجاناً";
  showSheet(`<div class="pad"><div class="section-title">📢 ${esc(o.offerTitle)}</div>
    <p class="desc tr">المدة: <b>${days} ${days === 1 ? "يوم" : "أيام"}</b> تبدأ ${start > now ? `من ${fmtDate(start)}` : "الحين"} • رصيدك <b>${bal()}</b> نقطة</p>
    ${canPay ? `<button class="btn dir big" id="pdGo">📢 انشره ${admin ? "" : label(mine)}</button>` : `<div class="hint-box">رصيدك ما يكفي: النشر يحتاج <b>${mine.cost}</b> نقطة.</div>`}
    ${exclOk ? `<div class="excl-offer"><b>${canPay ? "أو وفّر 👇" : "بس عندنا لك حل 👇"}</b><p>خلّه <b>${esc(exl.icon)} ${esc(exl.label)}</b> وانشره ${ex.cost ? `بـ <b>${ex.cost}</b> نقطة بدل ${full.cost}` : "<b>مجاناً</b>"}.</p>
      <small>الحصري يعني العرض لعملاء لكوفر بس، يوري التطبيق عند الكاشير.</small>
      <button class="btn dir big" id="pdExcl">${esc(exl.icon)} خلّه حصري وانشره ${label(ex)}</button></div>` : ""}
    ${!canPay ? `<div class="row2"><button class="btn call" id="pdRedeem">🔑 شحن بكود</button>${store ? `<a class="btn call" href="${esc(store)}" target="_blank" rel="noopener">🛒 اشترِ رصيد</a>` : ""}</div>` : ""}
    <div id="pdErr" class="err"></div></div>`);
  const go = async (excl, btn) => {
    const wb = writeBatch(db);
    wb.update(doc(db, "offers", o.id), { draft: false, exclusive: excl, startsAt: Timestamp.fromMillis(start), expiresAt: Timestamp.fromMillis(expiry) });
    const cost = addSpend(wb, { item: "publish", offerId: o.id, days, startsAt: start, note: `📢 نشر: ${o.offerTitle}`, excl });
    if (cost == null) return $("pdErr").textContent = "رصيدك ما يكفي.";
    btn.disabled = true;
    try { await wb.commit(); hideSheet(); toast(!o.isApproved ? "انرسل للمراجعة ⏳ بيظهر بعد الموافقة" : cost ? `تم نشر العرض ✅ خصمنا ${cost} نقطة` : "تم نشر العرض ✅ مجاناً 💎", true); }
    catch (e) { console.error(e); btn.disabled = false; $("pdErr").textContent = "تعذّر النشر. حدّث الصفحة وجرّب مرة ثانية."; }
  };
  $("pdGo")?.addEventListener("click", (ev) => go(!!o.exclusive, ev.currentTarget));
  $("pdExcl")?.addEventListener("click", (ev) => go(true, ev.currentTarget));
  $("pdRedeem")?.addEventListener("click", openRedeem);
}

function confirmDelete(o) {
  if (!o) return;
  showSheet(`<div class="pad"><div class="section-title">حذف العرض؟</div><p class="desc">${esc(o.offerTitle)}</p>
    <div class="row2"><button class="btn call" id="dNo">إلغاء</button><button class="btn danger" id="dYes">حذف</button></div></div>`);
  $("dNo").onclick = hideSheet;
  $("dYes").onclick = async () => { try { await deleteDoc(doc(db, "offers", o.id)); toast("انحذف العرض"); hideSheet(); } catch (e) { toast("تعذّر الحذف"); } };
}

// =================== صفحة متجري ===================
function hoursSelect(id, val) {
  return `<select id="${id}" class="input">${X.minuteOptions().map((mm) => `<option value="${mm}" ${mm === val ? "selected" : ""}>${X.fmtMinute(mm)}</option>`).join("")}</select>`;
}
function mediaTile(src, key, label = "") {
  return `<div class="tile"><img src="${esc(src)}" alt=""><button class="tile-x" data-rm="${esc(key)}" aria-label="إزالة">✕</button>${label ? `<span class="tile-l">${label}</span>` : ""}</div>`;
}

export function openStoreForm(claim = null) {
  const p = m.profile || {};
  const f = {
    name: p.restaurantName || state.user?.displayName || "", category: p.cuisineType || "", about: p.about || "",
    priceFrom: p.priceFrom > 0 ? String(p.priceFrom) : "", delivery: !!p.delivery,
    fee: p.deliveryFee > 0 ? String(p.deliveryFee) : "", min: p.deliveryMinOrder > 0 ? String(p.deliveryMinOrder) : "",
    free: p.deliveryFreeOver > 0 ? String(p.deliveryFreeOver) : "",
    vatReg: !!p.vatNumber, vatNumber: p.vatNumber || "",
    curbside: !!p.curbside, payCash: p.payCash !== false, payCard: p.payCard !== false,
    logo: p.logoUrl || "", logoFile: null, cover: p.coverUrl || "", coverFile: null,
    menu: (p.menuImages || []).map((u) => ({ url: u })), branches: JSON.parse(JSON.stringify(p.branches || [])),
  };
  if (!f.branches.length && (p.latitude || p.longitude)) f.branches.push({ id: "main", name: "الفرع الرئيسي", latitude: p.latitude, longitude: p.longitude, district: p.district || "", region: p.region || "", phone: p.phone || "", openMinute: p.openMinute ?? -1, closeMinute: p.closeMinute ?? -1, landmark: "" });
  if (claim && !p.restaurantName && claim.name) f.name = claim.name;
  let dirty = !!claim, saving = false;
  const render = () => {
    $("pageBody").innerHTML = `<div class="form">
      ${claim ? `<div class="hint-box">📍 حددنا موقع المحل من الخريطة. كمّل بيانات الفرع (الحي والجوال) واحفظ.</div>` : ""}
      <label class="label">اسم المتجر *</label><input id="sName" class="input" value="${esc(f.name)}" maxlength="100">
      <label class="label">التصنيف *</label>
      <div class="chips wrap">${[...new Set([...categories(), f.category].filter(Boolean))].map((c) => `<button class="chip${f.category === c ? " on" : ""}" data-cat="${esc(c)}">${esc(c)}</button>`).join("")}</div>
      <div class="row2 media-row">
        <div><label class="label">الشعار</label><label class="upload round">${f.logoFile || f.logo ? `<img src="${esc(f.logoFile ? URL.createObjectURL(f.logoFile) : cld(f.logo, 200))}" alt="">` : "＋"}<input type="file" accept="image/*" id="sLogo" hidden></label></div>
        <div><label class="label">صورة الواجهة (غلاف)</label><label class="upload wide">${f.coverFile || f.cover ? `<img src="${esc(f.coverFile ? URL.createObjectURL(f.coverFile) : cld(f.cover, 600))}" alt="">` : "📷 اختر صورة"}<input type="file" accept="image/*" id="sCover" hidden></label></div>
      </div>
      <label class="label">نبذة عن المتجر</label><textarea id="sAbout" class="input" rows="3" maxlength="500" placeholder="مثال: شاورما على الفحم من 2010…">${esc(f.about)}</textarea>
      <label class="label">الأسعار تبدأ من (ر.س)</label><input id="sPrice" class="input" inputmode="decimal" value="${esc(f.priceFrom)}" placeholder="مثال: 15">
      ${deliveryFields(f)}
      ${!feat(state.config, "curbside") ? "" : `<label class="switch-row"><span>🚗 أوفّر الاستلام من السيارة <small class="hint">(العميل يكتب وصف سيارته ويضغط «وصلت»)</small></span><input type="checkbox" id="sCurb" ${f.curbside ? "checked" : ""}></label>`}
      <div class="label">💳 طرق الدفع اللي أقبلها عند الاستلام</div>
      <div class="row2"><label class="switch-row"><span>💵 كاش</span><input type="checkbox" id="sCash" ${f.payCash ? "checked" : ""}></label>
        <label class="switch-row"><span>💳 بطاقة / شبكة</span><input type="checkbox" id="sCard" ${f.payCard ? "checked" : ""}></label></div>
      <label class="switch-row"><span>🧾 منشأتي مسجّلة في ضريبة القيمة المضافة <small class="hint">(يطلع تفصيل الضريبة ورقمك الضريبي في ملخص الطلب)</small></span><input type="checkbox" id="sVat" ${f.vatReg ? "checked" : ""}></label>
      ${f.vatReg ? `<label class="label">الرقم الضريبي (15 رقم يبدأ وينتهي بـ 3)</label><input id="sVatNo" class="input" inputmode="numeric" maxlength="15" dir="ltr" value="${esc(f.vatNumber)}" placeholder="3xxxxxxxxxxxxx3">` : ""}
      <label class="label">${X.menuLabel(f.category)} — صور (${f.menu.length}/10)</label>
      <div class="tiles">${f.menu.map((it, i) => mediaTile(it.file ? URL.createObjectURL(it.file) : cld(it.url, 300), "menu" + i)).join("")}
        ${f.menu.length < 10 ? `<label class="tile add">＋<input type="file" accept="image/*" multiple id="sMenu" hidden></label>` : ""}</div>
      <div class="section-title">📍 الفروع *</div>
      <div class="branches">${f.branches.map((b, i) => `<div class="branch"><div class="b-main"><div class="b-name">${esc(b.name || districtLabel(b.district) || "الفرع")}</div>
        <div class="b-sub">${esc([districtLabel(b.district), b.landmark, X.toLocalPhone(b.phone), X.hoursText(b)].filter(Boolean).join(" • "))}</div></div>
        <button class="icon-btn" data-be="${i}">✎</button>${f.branches.length > 1 ? `<button class="icon-btn" data-bd="${i}">🗑</button>` : ""}</div>`).join("")}</div>
      <button class="btn call" id="addBranch">＋ ${f.branches.length ? "أضف فرع" : "حدد موقع محلك"}</button>
    </div>`;
    const body = $("pageBody");
    const bind = (id, k) => { const el = $(id); if (el) el.oninput = (e) => { f[k] = e.target.value; dirty = true; }; };
    bind("sName", "name"); bind("sAbout", "about"); bind("sPrice", "priceFrom"); bind("sVatNo", "vatNumber");
    $("sVat").onchange = (e) => { f.vatReg = e.target.checked; dirty = true; render(); };
    $("sCurb")?.addEventListener("change", (e) => { f.curbside = e.target.checked; dirty = true; });
    $("sCash").onchange = (e) => { f.payCash = e.target.checked; if (!f.payCash && !f.payCard) { f.payCard = true; render(); } dirty = true; };
    $("sCard").onchange = (e) => { f.payCard = e.target.checked; if (!f.payCash && !f.payCard) { f.payCash = true; render(); } dirty = true; };
    bindDelivery(f, () => { dirty = true; render(); });
    body.querySelectorAll("[data-cat]").forEach((b) => b.onclick = () => { f.category = b.dataset.cat; dirty = true; render(); });
    // ✂️ التاجر يقص الشعار (دائري) والغلاف (عريض) بيده
    $("sLogo").onchange = async (e) => { const fl = e.target.files[0]; e.target.value = ""; if (!fl) return; const c = await cropImage(fl, { aspect: 1, round: true, title: "قص الشعار", outW: 600 }); if (c) { f.logoFile = c; dirty = true; render(); } };
    $("sCover").onchange = async (e) => { const fl = e.target.files[0]; e.target.value = ""; if (!fl) return; const c = await cropImage(fl, { aspect: 2, title: "قص صورة الواجهة" }); if (c) { f.coverFile = c; dirty = true; render(); } };
    $("sMenu")?.addEventListener("change", (e) => { [...e.target.files].slice(0, 10 - f.menu.length).forEach((file) => f.menu.push({ file })); dirty = true; render(); });
    body.querySelectorAll("[data-rm]").forEach((b) => b.onclick = () => { f.menu.splice(+b.dataset.rm.replace("menu", ""), 1); dirty = true; render(); });
    body.querySelectorAll("[data-be]").forEach((b) => b.onclick = () => branchEditor(f.branches[+b.dataset.be], (nb) => { f.branches[+b.dataset.be] = nb; dirty = true; render(); }));
    body.querySelectorAll("[data-bd]").forEach((b) => b.onclick = () => { f.branches.splice(+b.dataset.bd, 1); dirty = true; render(); });
    $("addBranch").onclick = () => branchEditor(null, (nb) => { f.branches.push(nb); dirty = true; render(); });
  };
  openPage("صفحة متجري", "", `<div id="sErr" class="err"></div><button class="btn dir big" id="sSave">حفظ صفحة المتجر ✓</button>`,
    () => (dirty && !saving ? confirm("في تعديلات ما انحفظت. تطلع بدون حفظ؟") : true));
  render();
  if (claim) {
    const addClaim = () => branchEditor({ id: "b" + Date.now().toString(36), name: f.branches.length ? (claim.name || "") : "الفرع الرئيسي", latitude: claim.lat, longitude: claim.lng,
      district: "", region: "", phone: state.profile?.phone || "", openMinute: 720, closeMinute: 0, landmark: "" }, (nb) => { f.branches.push(nb); dirty = true; render(); }, true);
    if (!f.branches.length) addClaim();
    else {
      // الحساب عنده متجر من قبل: نوضّح إن المحل بينضاف كفرع (مو متجر جديد) بدل ما يختلط مع موقع المتجر الحالي
      showSheet(`<div class="pad install-sheet"><div style="font-size:40px">🏪</div>
        <h2>عندك متجر مسجّل: ${esc(f.name || "متجري")}</h2>
        <p class="muted">${claim.name ? `«${esc(claim.name)}»` : "هذا المحل"} بينضاف <b>كفرع جديد</b> لمتجرك في موقعه على الخريطة.<br>إذا هو محل مستقل باسم ثاني، سجّله بحساب ثاني.</p>
        <button class="btn primary" id="claimAdd">أضفه كفرع لمتجري</button>
        <button class="btn ghost" id="claimNo" style="margin-top:8px">لا، إلغاء</button></div>`);
      $("claimAdd").onclick = () => { hideSheet(); setTimeout(addClaim, 80); };
      $("claimNo").onclick = () => { hideSheet(); dirty = false; closePage(false); };
    }
  }
  $("sSave").onclick = async () => {
    const err = (t) => { $("sErr").textContent = t; };
    if (!f.name.trim()) return err("اكتب اسم المتجر.");
    if (!f.category) return err("اختر تصنيف المتجر.");
    if (!f.branches.length) return err("حدد موقع محلك (فرع واحد على الأقل).");
    const vatNo = f.vatReg ? String(f.vatNumber).replace(/[٠-٩]/g, (c) => "٠١٢٣٤٥٦٧٨٩".indexOf(c)).replace(/\D/g, "") : "";
    if (f.vatReg && !validVatNumber(vatNo)) return err("الرقم الضريبي لازم 15 رقم يبدأ وينتهي بـ 3.");
    saving = true; $("sSave").disabled = true; $("sSave").textContent = "جاري الحفظ…"; err("");
    try {
      const logo = f.logoFile ? await uploadImage(f.logoFile, 600) : f.logo;
      const cover = f.coverFile ? await uploadImage(f.coverFile) : f.cover;
      const menu = []; for (const it of f.menu) menu.push(it.file ? await uploadImage(it.file) : it.url);
      const primary = f.branches[0];
      const profile = {
        restaurantName: f.name.trim().slice(0, 100), phone: primary.phone, latitude: primary.latitude, longitude: primary.longitude,
        region: primary.region || "", district: primary.district || "", cuisineType: f.category, openMinute: primary.openMinute ?? -1, closeMinute: primary.closeMinute ?? -1,
        logoUrl: logo || "", branches: f.branches.slice(0, 50), coverUrl: cover || "", about: f.about.trim().slice(0, 500),
        priceFrom: Number(f.priceFrom) > 0 ? Number(f.priceFrom) : 0, menuImages: menu.slice(0, 10),
        delivery: f.delivery, deliveryFee: f.delivery ? Number(f.fee) || 0 : 0, deliveryMinOrder: f.delivery ? Number(f.min) || 0 : 0,
        deliveryFreeOver: f.delivery ? Number(f.free) || 0 : 0, vatNumber: vatNo, curbside: f.curbside, payCash: f.payCash, payCard: f.payCard,
        updatedAt: serverTimestamp(),
      };
      const uid = state.user.uid, direct = canPublishDirectly();
      await setDoc(doc(db, "merchants", uid), profile);
      await setDoc(doc(db, "stores", uid), {
        uid, name: profile.restaurantName, logoUrl: profile.logoUrl, coverUrl: profile.coverUrl, about: profile.about, category: profile.cuisineType.slice(0, 60),
        priceFrom: profile.priceFrom, menuImages: profile.menuImages, branches: profile.branches, phone: (primary.phone || "").slice(0, 30), approved: direct,
        delivery: profile.delivery, deliveryFee: profile.deliveryFee, deliveryMinOrder: profile.deliveryMinOrder, deliveryFreeOver: profile.deliveryFreeOver, vatNumber: vatNo,
        curbside: f.curbside, payCash: f.payCash, payCard: f.payCard, updatedAt: serverTimestamp(),
      }, { merge: true });
      await syncOffers(profile, direct);
      await recordReferral(uid, profile.restaurantName);
      saving = false; dirty = false; closePage(false);
      toast(direct ? "تم حفظ صفحة متجرك ✅" : "تم الحفظ ✅ صفحة متجرك بتظهر للعملاء بعد المراجعة", true);
    } catch (e) {
      console.error(e); saving = false; $("sSave").disabled = false; $("sSave").textContent = "حفظ صفحة المتجر ✓";
      err(e.code === "permission-denied" ? "رفض الحفظ (صلاحيات). تواصل معنا." : (e.message || "تعذّر الحفظ"));
    }
  };
}

// المحل جاء عن طريق دعوة؟ نسجّلها مرة وحدة (أول دعوة تفوز)، والنقاط يعتمدها الأدمن
async function recordReferral(uid, storeName) {
  const ref = pendingRef();
  if (!ref || ref === uid) return clearRef();
  try {
    await setDoc(doc(db, "referrals", uid), { referrer: ref, storeUid: uid, storeName: (storeName || "").slice(0, 100), createdAt: serverTimestamp(), verifiedPts: 0, offerPts: 0 });
  } catch (e) { /* موجودة من قبل */ }
  clearRef();
}

async function syncOffers(profile, direct) {
  const byId = new Map(profile.branches.map((b) => [b.id, b]));
  const snap = await getDocs(query(collection(db, "offers"), where("merchantId", "==", state.user.uid)));
  for (const d of snap.docs) {
    const o = d.data();
    const updated = (o.branches || []).map((b) => byId.get(b.id)).filter(Boolean);
    const branches = (o.branches || []).length && !updated.length ? o.branches : updated;
    const primary = branches[0];
    const patch = {
      logoUrl: profile.logoUrl, branches, delivery: profile.delivery, deliveryFee: profile.deliveryFee, deliveryMinOrder: profile.deliveryMinOrder, deliveryFreeOver: profile.deliveryFreeOver,
      ...(primary ? { latitude: primary.latitude, longitude: primary.longitude, region: primary.region || o.region, district: primary.district || o.district,
        phone: primary.phone || o.phone, openMinute: primary.openMinute ?? o.openMinute, closeMinute: primary.closeMinute ?? o.closeMinute } : {}),
      ...(direct ? {} : { isApproved: false }),
    };
    await updateDoc(d.ref, patch).catch((e) => console.warn("sync", d.id, e));
  }
}

function branchEditor(branch, onDone, asNew = false) {
  const b = branch ? { ...branch } : { id: "b" + Date.now().toString(36), name: "", latitude: 0, longitude: 0, district: "", region: "", phone: "", openMinute: 720, closeMinute: 0, landmark: "" };
  const open24 = b.openMinute >= 0 && b.openMinute === b.closeMinute;
  showSheet(`<div class="pad form">
    <div class="section-title">${branch && !asNew ? "تعديل الفرع" : "فرع جديد"}</div>
    <label class="label">اسم الفرع (اختياري)</label><input id="bName" class="input" value="${esc(b.name)}" placeholder="مثال: فرع الحمدانية">
    <label class="label">موقع المحل * <small>(اضغط على الخريطة أو استخدم موقعك)</small></label>
    <div id="bMap" class="mini-map"></div>
    <button class="btn call" id="bHere">📍 أنا في المحل الحين — استخدم موقعي</button>
    <div class="row2"><div><label class="label">الحي *</label><input id="bDistrict" class="input" value="${esc(b.district)}" placeholder="مثال: الحمدانية"></div>
      <div><label class="label">المدينة</label><input id="bRegion" class="input" value="${esc(b.region)}" placeholder="مثال: جدة"></div></div>
    <label class="label">علامة مميزة (اختياري)</label><input id="bLandmark" class="input" value="${esc(b.landmark || "")}" maxlength="80" placeholder="مثال: بجوار البريد السعودي">
    <label class="label">رقم واتساب / جوال الفرع *</label><input id="bPhone" class="input" inputmode="tel" value="${esc(X.toLocalPhone(b.phone))}" placeholder="05xxxxxxxx">
    <label class="switch-row"><span>مفتوح 24 ساعة</span><input type="checkbox" id="b24" ${open24 ? "checked" : ""}></label>
    <div class="row2" id="bHours"><div><label class="label">يفتح</label>${hoursSelect("bOpen", b.openMinute >= 0 ? b.openMinute : 720)}</div>
      <div><label class="label">يقفل</label>${hoursSelect("bClose", b.closeMinute >= 0 ? b.closeMinute : 0)}</div></div>
    <div id="bErr" class="err"></div>
    <button class="btn dir" id="bSave">${branch && !asNew ? "حفظ الفرع" : "أضف الفرع"}</button></div>`);
  $("bHours").classList.toggle("hidden", open24);
  $("b24").onchange = (e) => $("bHours").classList.toggle("hidden", e.target.checked);
  let pos = (b.latitude || b.longitude) ? { lat: b.latitude, lng: b.longitude } : null, mapObj = null;
  const center = pos || state.me || { lat: 21.5433, lng: 39.1728 };
  createMap($("bMap"), { key: MAPS_WEB_KEY, center: [center.lat, center.lng], zoom: pos ? 16 : 13, showShops: true,
    onMapClick: (p) => { pos = p; mapObj?.setPin(p.lat, p.lng); } }).then((mo) => { mapObj = mo; if (pos) mo.setPin(pos.lat, pos.lng); });
  $("bHere").onclick = async () => {
    try { const p = await getMyLocation(); pos = p; mapObj?.setView(p.lat, p.lng, 17); mapObj?.setPin(p.lat, p.lng); toast("تم تحديد الموقع 📍"); }
    catch { toast("ما قدرنا نحدد موقعك. اسمح للمتصفح بالموقع أو اضغط على الخريطة"); }
  };
  $("bSave").onclick = () => {
    const phone = X.normalizeSaudiPhone($("bPhone").value), district = $("bDistrict").value.trim();
    const err = (t) => $("bErr").textContent = t;
    if (!pos) return err("حدد موقع المحل على الخريطة.");
    if (!district) return err("اكتب اسم الحي.");
    if (!phone) return err("اكتب رقم جوال صحيح مثل 05xxxxxxxx.");
    const is24 = $("b24").checked;
    onDone({ ...b, name: $("bName").value.trim(), latitude: pos.lat, longitude: pos.lng, district, region: $("bRegion").value.trim(),
      landmark: $("bLandmark").value.trim().slice(0, 80), phone, openMinute: is24 ? 0 : +$("bOpen").value, closeMinute: is24 ? 0 : +$("bClose").value });
    hideSheet();
  };
}

function deliveryFields(f) {
  return `<label class="switch-row"><span>🛵 عندنا توصيل</span><input type="checkbox" id="dOn" ${f.delivery ? "checked" : ""}></label>
    ${f.delivery ? `<div class="row2"><div><label class="label">رسوم التوصيل (0 = مجاني)</label><input id="dFee" class="input" inputmode="decimal" value="${esc(f.fee)}" placeholder="0"></div>
      <div><label class="label">أقل طلب للتوصيل (اختياري)</label><input id="dMin" class="input" inputmode="decimal" value="${esc(f.min)}" placeholder="مثال: 40"></div></div>
      <label class="label">توصيل مجاني للطلبات فوق (اختياري)</label><input id="dFree" class="input" inputmode="decimal" value="${esc(f.free || "")}" placeholder="مثال: 50">` : ""}`;
}
function bindDelivery(f, rerender) {
  $("dOn").onchange = (e) => { f.delivery = e.target.checked; rerender(); };
  if ($("dFee")) $("dFee").oninput = (e) => { f.fee = e.target.value; };
  if ($("dMin")) $("dMin").oninput = (e) => { f.min = e.target.value; };
  if ($("dFree")) $("dFree").oninput = (e) => { f.free = e.target.value; };
}

// =================== نموذج العرض ===================
export async function openOfferForm(editing) {
  if (m.profile === undefined && state.user) {
    // فتح التعديل من صفحة العرض قبل ما تنفتح لوحة التاجر
    const uid = state.user.uid;
    m.profile = await getDoc(doc(db, "merchants", uid)).then((s) => s.exists() ? s.data() : null).catch(() => null);
    m.status = await getDoc(doc(db, "merchantStatus", uid)).then((s) => s.exists() ? s.data() : null).catch(() => null);
    if (!m.ctx) m.ctx = { isAdmin: false };
  }
  const p = m.profile || {};
  if (!(p.branches || []).length) { toast("جهّز صفحة متجرك أول 📍"); openStoreForm(); return; }
  const e = editing || null;
  // العروض مع المنيو: نوع العرض + ربطه بالأصناف
  const dealsOn = catalogOf(state.config).enabled && Object.values(DEAL_TYPES).some((t) => feat(state.config, t.feat));
  const myItems = dealsOn ? (await loadMyItems(state.user.uid)).filter((x) => x.approved && !x.adminNote) : [];
  const ed = e?.deal || {};
  const f = {
    dealType: ed.type || (e ? "promo" : ""), dScope: { scope: ed.scope || "all", kind: ed.kind || "", section: ed.section || "", itemIds: ed.type === "discount" ? ed.itemIds || [] : [] },
    packIds: ed.type === "bundle" ? ed.itemIds || [] : [], bogoItem: ed.itemId || "", getPct: ed.getPct === 50 ? 50 : 100, orderable: !!ed.orderable,
    title: e?.offerTitle || "", desc: e?.description || "", category: e?.cuisineType || p.cuisineType || categories()[0],
    percentMode: e ? X.isPercentOnly(e) : false, price: e && e.price > 0 ? X.money(e.price) : "", original: e?.originalPrice ? X.money(e.originalPrice) : "",
    pct: e?.discountPercent > 0 && !(e.dailyPercents || []).length ? String(e.discountPercent) : "", tiered: (e?.dailyPercents || []).length > 0,
    days: (e?.dailyPercents || []).length ? e.dailyPercents.map(String) : ["", ""], scope: e?.itemsScope || "",
    tags: (e?.tags || []).join("، "), delivery: e ? !!e.delivery : !!p.delivery,
    fee: X.money((e ? e.deliveryFee : p.deliveryFee) || 0).replace(/^0$/, ""), min: X.money((e ? e.deliveryMinOrder : p.deliveryMinOrder) || 0).replace(/^0$/, ""),
    free: X.money((e ? e.deliveryFreeOver : p.deliveryFreeOver) || 0).replace(/^0$/, ""), customDays: "",
    opening: !!e?.grandOpening, start: X.ms(e?.startsAt) || null, durIdx: 1, keepExpiry: !!e,
    all: !e || !(e.branches || []).length || p.branches.every((b) => (e.branches || []).some((x) => x.id === b.id)),
    sel: new Set((e?.branches || []).map((b) => b.id)),
    media: e ? X.gallery(e).map((u) => ({ url: u })) : [], isVideo: e?.mediaType === "VIDEO",
    kind: e?.postType === "new" ? "new" : "offer", exclusive: !!e?.exclusive,
  };
  const paid = cr().enabled && !e; // النشر الجديد بالرصيد
  const exclLocked = !!e?.exclusive && rw().on && !m.ctx?.isAdmin; // الحصري ما ينشال بعد ما انتشر بخصم
  if (paid && f.durIdx === 0) f.durIdx = 3; // بدون انتهاء ما يصلح مع الرصيد
  const pubItem = cr().items.publish || { price: 0, unit: "day" };
  const daysOf = () => {
    if (f.percentMode && f.tiered) return f.days.length;
    if (f.durIdx === -1) return +f.customDays || 0;
    return X.DURATIONS[f.durIdx]?.days || 0;
  };
  const NEW_DUR = X.DURATIONS.findIndex((d) => d.days === 14); // جديدنا: أسبوعين افتراضياً
  if (!e && f.kind === "new") f.durIdx = NEW_DUR;
  let dirty = false, saving = false;
  const startStr = () => { if (!f.start) return ""; const d = new Date(f.start); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };
  const render = () => {
    const isNew = f.kind === "new";
    if (isNew) f.percentMode = false;
    const dt = !isNew && dealsOn ? f.dealType : "";
    if (dt === "discount") f.percentMode = true;
    if (dt === "bundle" || dt === "bogo") f.percentMode = false;
    const bogoIt = myItems.find((x) => x.id === f.bogoItem);
    if (dt === "bogo" && bogoIt) { f.price = X.money(bogoIt.price); f.original = ""; }
    const packSum = myItems.filter((x) => f.packIds.includes(x.id)).reduce((a, x) => a + (+x.price || 0), 0);
    const pr = paid ? priceFor("publish", Math.max(1, daysOf()), f.exclusive) : null, r = rw();
    $("pageBody").innerHTML = `<div class="form">
      ${paid && !m.ctx?.isAdmin ? `<div class="bal-bar${bal() < (pr?.cost || 0) ? " low" : ""}"><span>💰 رصيدك: <b>${bal()}</b> نقطة</span><span>📢 النشر: ${pubItem.price} ${pubItem.unit === "day" ? "نقاط باليوم" : "نقاط"}</span>${r.on ? `<span class="bb-excl">💎 الحصري: ${r.free ? "نشره مجاني" : `خصم ${r.percent}%`}</span>` : ""}</div>` : ""}
      <div class="kind-row">
        <button class="kind-btn${!isNew ? " on" : ""}" data-kind="offer"><b>🔥 عرض</b><small>خصم أو سعر خاص</small></button>
        <button class="kind-btn new${isNew ? " on" : ""}" data-kind="new"><b>✨ جديدنا</b><small>صنف أو منتج جديد</small></button>
      </div>
      ${!isNew && dealsOn ? `<div class="section-title">وش نوع عرضك؟</div><div class="deal-grid">${Object.entries(DEAL_TYPES).filter(([, t]) => feat(state.config, t.feat)).map(([k, t]) => {
        const need = (k === "discount" || k === "bogo") && !myItems.length;
        return `<button class="deal-type${f.dealType === k ? " on" : ""}" data-dt="${k}" ${need ? "disabled" : ""}><b>${t.icon} ${t.t}</b><small>${need ? "أضف أصناف للمنيو أول" : t.d}</small></button>`; }).join("")}</div>` : ""}
      <label class="label">${isNew ? "صور المنتج (حتى 3) أو فيديو *" : "صور العرض (حتى 3) أو فيديو *"}</label>
      <div class="tiles">${f.media.map((it, i) => f.isVideo ? `<div class="tile"><div class="vid">🎬 فيديو</div><button class="tile-x" data-rm="${i}">✕</button></div>`
        : mediaTile(it.file ? URL.createObjectURL(it.file) : cld(it.url, 300), String(i), i === 0 ? "الغلاف" : "").replace(/<\/div>$/, it.file ? `<button class="tile-crop" data-crop="${i}" title="قص">✂️</button></div>` : "</div>")).join("")}
        ${!f.isVideo && f.media.length < 3 ? `<label class="tile add">＋ صور<input type="file" accept="image/*" multiple id="oImgs" hidden></label>` : ""}
        ${!f.media.length ? `<label class="tile add">🎬 فيديو<input type="file" accept="video/*" id="oVid" hidden></label>` : ""}</div>
      <label class="label">${isNew ? "اسم المنتج الجديد *" : "عنوان العرض *"}</label><input id="oTitle" class="input" value="${esc(f.title)}" maxlength="150" placeholder="${isNew ? "مثال: برجر الترافل الجديد" : "مثال: خصم 30% على كل المنتجات"}">
      ${dt === "bogo" ? `<label class="label">➕ على أي صنف؟ *</label><div class="chips wrap">${myItems.map((it) => `<button class="chip${f.bogoItem === it.id ? " on" : ""}" data-bogo="${esc(it.id)}">${esc(it.name)} • ${fmt(it.price)}</button>`).join("")}</div>
        <div class="chips"><button class="chip${f.getPct === 100 ? " on" : ""}" data-getpct="100">1+1 مجاناً</button><button class="chip${f.getPct === 50 ? " on" : ""}" data-getpct="50">الثاني بنص السعر</button></div>` : ""}
      ${isNew || dt === "discount" || dt === "bundle" || dt === "bogo" ? "" : `<div class="chips"><button class="chip${!f.percentMode ? " on" : ""}" data-mode="price">💰 سعر محدد</button><button class="chip${f.percentMode ? " on" : ""}" data-mode="pct">٪ خصم بالنسبة</button></div>`}
      ${dt === "bogo" ? "" : !f.percentMode ? `<div class="row2"><div><label class="label">${isNew ? "السعر (اختياري)" : dt === "bundle" ? "سعر الباقة (ر.س) *" : "السعر (ر.س) *"}</label><input id="oPrice" class="input" inputmode="decimal" value="${esc(f.price)}"></div>
        <div><label class="label">${isNew ? "قبل الخصم (لو عليه خصم)" : "قبل الخصم"}</label><input id="oOrig" class="input" inputmode="decimal" value="${esc(f.original)}"></div></div>`
      : `<label class="switch-row"><span>الخصم يتغيّر حسب اليوم (مثل: أول يوم 50% وبعدها 30%)</span><input type="checkbox" id="oTier" ${f.tiered ? "checked" : ""}></label>
        ${!f.tiered ? `<label class="label">نسبة الخصم % *</label><input id="oPct" class="input" inputmode="numeric" value="${esc(f.pct)}" maxlength="2">`
        : `${f.days.map((v, i) => `<div class="tier"><span>اليوم ${X.ordinal(i)}</span><input class="input" inputmode="numeric" data-day="${i}" value="${esc(v)}" maxlength="2" placeholder="%">${f.days.length > 2 ? `<button class="icon-btn" data-dayrm="${i}">✕</button>` : ""}</div>`).join("")}
          ${f.days.length < 14 ? `<button class="btn ghost" id="oAddDay">＋ أضف يوم</button>` : ""}<small class="hint">مدة العرض = ${f.days.length} أيام، والخصم يتغيّر تلقائياً.</small>`}
        <label class="label">على وش الخصم؟</label>${dt === "discount" && myItems.length ? scopePickerHtml(f.dScope, myItems) : `<input id="oScope" class="input" value="${esc(f.scope)}" maxlength="40" placeholder="على جميع الأصناف">`}`}
      ${dt === "bundle" && myItems.length ? `<label class="label">🎁 وش فيها الباقة؟ (اختياري — من منيوك)</label><div class="pick-list">${myItems.map((it) => `<label class="switch-row sub"><span>${KINDS[it.kind]?.icon || ""} ${esc(it.name)} <small>${fmt(it.price)} ر.س</small></span><input type="checkbox" data-pack="${esc(it.id)}" ${f.packIds.includes(it.id) ? "checked" : ""}></label>`).join("")}</div>
        ${packSum ? `<div class="hint-box" id="packHint">مجموع الأصناف <b>${fmt(packSum)} ر.س</b>${+f.price > 0 && +f.price < packSum ? ` — الزبون يوفّر <b>${fmt(packSum - +f.price)} ر.س</b> 🎉` : ""}</div>` : `<small class="hint">لو الباقة شي جديد ما هو في المنيو، اكتب محتواها في التفاصيل.</small>`}` : ""}
      ${dt === "promo" && dealsOn && catalogOf(state.config).orderEnabled ? `<label class="switch-row"><span>🛒 الزبون يقدر يطلبه من لكوفر <small class="hint">(لازم سعر)</small></span><input type="checkbox" id="oOrderable" ${f.orderable ? "checked" : ""}></label>` : ""}
      <label class="label">${isNew ? "وصف المنتج" : "تفاصيل العرض"}</label><textarea id="oDesc" class="input" rows="3" maxlength="2000">${esc(f.desc)}</textarea>
      <label class="label">الأصناف (للبحث)</label><input id="oTags" class="input" value="${esc(f.tags)}" placeholder="شاورما، برجر، بروست">
      ${deliveryFields(f)}
      <label class="label">التصنيف</label>
      <div class="chips wrap">${[...new Set([...categories(), f.category].filter(Boolean))].map((c) => `<button class="chip${f.category === c ? " on" : ""}" data-cat="${esc(c)}">${esc(c)}</button>`).join("")}</div>
      <div class="section-title">🏪 وين يظهر العرض؟</div>
      <label class="switch-row"><span>كل الفروع (${p.branches.length})</span><input type="checkbox" id="oAll" ${f.all ? "checked" : ""}></label>
      ${!f.all ? p.branches.map((b) => `<label class="switch-row sub"><span>${esc(b.name || districtLabel(b.district))}</span><input type="checkbox" data-br="${esc(b.id)}" ${f.sel.has(b.id) ? "checked" : ""}></label>`).join("") : ""}
      <div class="section-title">🔜 متى يبدأ؟</div>
      <div class="row2"><button class="chip${!f.start ? " on" : ""}" id="oNow">الحين</button><input type="date" id="oStart" class="input" value="${startStr()}"></div>
      ${isNew ? "" : `<label class="switch-row"><span>🎉 عرض افتتاح (محل جديد)</span><input type="checkbox" id="oOpen" ${f.opening ? "checked" : ""}></label>`}
      ${exclusiveOf(state.config).enabled ? `<label class="switch-row"><span>${esc(exclusiveOf(state.config).icon)} ${esc(exclusiveOf(state.config).label)} <small class="hint">(العرض لعملاء لكوفر بس، يوري التطبيق عند الكاشير)</small>${r.on && cr().enabled ? `<small class="excl-reward">🎁 ${r.free ? "نشر العرض الحصري مجاني" : `خصم ${r.percent}% على نشر العرض الحصري`}</small>` : ""}</span><input type="checkbox" id="oExcl" ${f.exclusive ? "checked" : ""} ${exclLocked ? "disabled" : ""}></label>
        ${exclLocked ? `<small class="hint">💎 العرض انتشر كحصري، فما تقدر تشيل الحصري. لو تبيه لكل الناس احذفه وانشره من جديد.</small>` : ""}` : ""}
      ${cr().enabled && e ? `<div class="hint-box">⏰ لتمديد مدة العرض أو تمييزه استخدم زر 🚀 في قائمة عروضك.</div>` : f.percentMode && f.tiered ? "" : `<div class="section-title">⏰ ${isNew ? "كم يظهر كـ «جديد»؟" : "مدة العرض"}</div><div class="chips wrap">
        ${e ? `<button class="chip${f.keepExpiry ? " on" : ""}" data-dur="keep">بدون تغيير (${esc(X.remainingText(e)?.t || "بدون انتهاء")})</button>` : ""}
        ${X.DURATIONS.map((d, i) => paid && !d.days ? "" : `<button class="chip${!f.keepExpiry && f.durIdx === i ? " on" : ""}" data-dur="${i}">${d.label}</button>`).join("")}
        <button class="chip${!f.keepExpiry && f.durIdx === -1 ? " on" : ""}" data-dur="-1">✎ عدد أيام</button></div>
        ${!f.keepExpiry && f.durIdx === -1 ? `<input id="oDays" class="input" inputmode="numeric" maxlength="2" value="${esc(f.customDays)}" placeholder="كم يوم؟ مثال: 2">` : ""}`}
      ${paid ? `<div class="cost-line" id="oCost">💰 تكلفة النشر: <b>${pr.disc ? (pr.cost ? `<s>${pr.full}</s> ${pr.cost} نقطة 💎` : `<s>${pr.full}</s> مجاني 💎`) : `${daysOf()} ${daysOf() === 1 ? "يوم" : "أيام"} × ${pubItem.price} = ${pr.full} نقطة`}</b>${m.ctx?.isAdmin ? "" : ` • رصيدك ${bal()}${bal() < pr.cost ? ` <span class="err">— ما يكفي، بس لا تشيل هم: نحفظ عرضك مسودة</span>` : ""}`}</div>` : ""}
      <small class="hint">بعد انتهاء المدة يختفي العرض تلقائياً من الخريطة. ✨ التعبئة الذكية من الصورة متاحة في تطبيق أندرويد.</small>
    </div>`;
    const set = (id, k) => { const el = $(id); if (el) el.oninput = (ev) => { f[k] = ev.target.value; dirty = true; }; };
    set("oTitle", "title"); set("oPrice", "price"); set("oOrig", "original"); set("oPct", "pct"); set("oScope", "scope"); set("oDesc", "desc"); set("oTags", "tags");
    const body = $("pageBody");
    body.querySelectorAll("[data-dt]").forEach((b) => b.onclick = () => { f.dealType = b.dataset.dt; dirty = true; render(); });
    body.querySelectorAll("[data-bogo]").forEach((b) => b.onclick = () => { f.bogoItem = b.dataset.bogo; const it = myItems.find((x) => x.id === f.bogoItem); if (it && !f.title.trim()) f.title = `${f.getPct === 50 ? "الثاني بنص السعر" : "1+1 مجاناً"} على ${it.name}`; dirty = true; render(); });
    body.querySelectorAll("[data-getpct]").forEach((b) => b.onclick = () => { f.getPct = +b.dataset.getpct; dirty = true; render(); });
    body.querySelectorAll("[data-pack]").forEach((c) => c.onchange = () => { f.packIds = c.checked ? [...new Set([...f.packIds, c.dataset.pack])] : f.packIds.filter((x) => x !== c.dataset.pack); const y = window.scrollY, pb = $("pageBody").scrollTop; render(); $("pageBody").scrollTop = pb; });
    $("oOrderable")?.addEventListener("change", (ev) => { f.orderable = ev.target.checked; dirty = true; });
    if (dt === "discount") bindScopePicker(body, f.dScope, () => { dirty = true; render(); });
    // الباقة: نحدّث «كم يوفّر الزبون» وهو يكتب (بدون إعادة رسم الصفحة)
    if (dt === "bundle") $("oPrice")?.addEventListener("input", () => { const h = $("packHint"), v = +f.price; if (h && packSum) h.innerHTML = `مجموع الأصناف <b>${fmt(packSum)} ر.س</b>${v > 0 && v < packSum ? ` — الزبون يوفّر <b>${fmt(packSum - v)} ر.س</b> 🎉` : ""}`; });
    bindDelivery(f, () => { dirty = true; render(); });
    $("oImgs")?.addEventListener("change", (ev) => { [...ev.target.files].slice(0, 3 - f.media.length).forEach((file) => f.media.push({ file })); f.isVideo = false; dirty = true; render(); });
    $("oVid")?.addEventListener("change", (ev) => { const file = ev.target.files[0]; if (!file) return; if (file.size > 95 * 1024 * 1024) return toast("الفيديو كبير، الحد 95 ميجا"); f.media = [{ file }]; f.isVideo = true; dirty = true; render(); });
    body.querySelectorAll("[data-rm]").forEach((b) => b.onclick = () => { f.media.splice(+b.dataset.rm, 1); if (!f.media.length) f.isVideo = false; dirty = true; render(); });
    // ✂️ قص اختياري لصور العرض (بعض التجار يبون الصورة كاملة مثل المنيو المطبوع)
    body.querySelectorAll("[data-crop]").forEach((b) => b.onclick = async (ev) => { ev.preventDefault(); const it = f.media[+b.dataset.crop]; if (!it?.file) return; const c = await cropImage(it.file, { aspect: 4 / 3, title: "قص صورة العرض" }); if (c) { it.file = c; dirty = true; render(); } });
    body.querySelectorAll("[data-mode]").forEach((b) => b.onclick = () => { f.percentMode = b.dataset.mode === "pct"; dirty = true; render(); });
    $("oTier")?.addEventListener("change", (ev) => { f.tiered = ev.target.checked; render(); });
    body.querySelectorAll("[data-day]").forEach((i) => i.oninput = (ev) => { f.days[+i.dataset.day] = ev.target.value.replace(/\D/g, "").slice(0, 2); });
    body.querySelectorAll("[data-dayrm]").forEach((b) => b.onclick = () => { f.days.splice(+b.dataset.dayrm, 1); render(); });
    $("oAddDay")?.addEventListener("click", () => { f.days.push(f.days[f.days.length - 1] || ""); render(); });
    body.querySelectorAll("[data-cat]").forEach((b) => b.onclick = () => { f.category = b.dataset.cat; render(); });
    $("oAll").onchange = (ev) => { f.all = ev.target.checked; render(); };
    body.querySelectorAll("[data-br]").forEach((c) => c.onchange = () => { c.checked ? f.sel.add(c.dataset.br) : f.sel.delete(c.dataset.br); });
    $("oNow").onclick = () => { f.start = null; render(); };
    $("oStart").onchange = (ev) => { if (!ev.target.value) { f.start = null; } else { const [y, mo, d] = ev.target.value.split("-").map(Number); f.start = new Date(y, mo - 1, d).getTime(); } render(); };
    $("oOpen")?.addEventListener("change", (ev) => { f.opening = ev.target.checked; });
    $("oExcl")?.addEventListener("change", (ev) => { f.exclusive = ev.target.checked; dirty = true; if (paid) render(); });
    body.querySelectorAll("[data-kind]").forEach((b) => b.onclick = () => {
      if (f.kind === b.dataset.kind) return;
      f.kind = b.dataset.kind; dirty = true;
      if (!e) f.durIdx = f.kind === "new" ? NEW_DUR : 1;
      if (f.kind === "new") f.opening = false;
      render();
    });
    $("oDays")?.addEventListener("input", (ev) => { f.customDays = ev.target.value.replace(/\D/g, "").slice(0, 2); const c = $("oCost"); if (c) { ev.target.value = f.customDays; } });
    $("oDays")?.addEventListener("change", () => { if (paid) render(); });
    body.querySelectorAll("[data-dur]").forEach((b) => b.onclick = () => { if (b.dataset.dur === "keep") f.keepExpiry = true; else { f.keepExpiry = false; f.durIdx = +b.dataset.dur; } render(); });
  };
  openPage(e ? "تعديل العرض" : "عرض جديد", "", `<div id="oErr" class="err"></div><button class="btn dir big" id="oSave">${e ? "حفظ التعديلات ✓" : "انشر العرض ✨"}</button>`,
    () => (dirty && !saving ? confirm("المعلومات اللي كتبتها بتضيع. تطلع؟") : true));
  render();
  let asDraft = false;
  // رصيد ما يكفي: نقترح الحصري (لو مكافأته تغطي) أو نحفظه مسودة — التاجر ما يخسر اللي كتبه
  const shortfall = (days) => {
    const full = priceFor("publish", days, false), ex = priceFor("publish", days, true), r = rw();
    const exclOk = r.on && !f.exclusive && bal() >= ex.cost;
    const exl = exclusiveOf(state.config);
    showSheet(`<div class="pad"><div class="section-title">رصيدك ما يكفي 🙁</div>
      <p class="desc tr">نشر العرض يحتاج <b>${(f.exclusive ? ex : full).cost}</b> نقطة، ورصيدك <b>${bal()}</b>.</p>
      ${exclOk ? `<div class="excl-offer"><b>بس عندنا لك حل 👇</b><p>خلّ عرضك <b>${esc(exl.icon)} ${esc(exl.label)}</b> وانشره ${ex.cost ? `بـ <b>${ex.cost}</b> نقطة بدل ${full.cost}` : "<b>مجاناً</b>"} الحين.</p>
        <small>الحصري يعني العرض لعملاء لكوفر بس، يوري التطبيق عند الكاشير.</small>
        <button class="btn dir big" id="sfExcl">${esc(exl.icon)} خلّه حصري وانشره ${ex.cost ? `بـ ${ex.cost} نقطة` : "مجاناً"}</button></div>` : ""}
      <button class="btn ${exclOk ? "call" : "dir big"}" id="sfDraft">📝 احفظه مسودة لين أشحن</button>
      <small class="hint">المسودة ما تظهر للناس، وتنشرها بضغطة وحدة من «حسابي» أول ما يصير عندك رصيد.</small></div>`);
    $("sfExcl")?.addEventListener("click", () => { hideSheet(); f.exclusive = true; render(); $("oSave").onclick(); });
    $("sfDraft").onclick = () => { hideSheet(); asDraft = true; $("oSave").onclick(); };
  };
  $("oSave").onclick = async () => {
    const err = (t) => { $("oErr").textContent = t; return null; };
    const now = Date.now(), DAY = X.DAY;
    const price = f.percentMode ? 0 : Number(f.price), original = f.percentMode ? null : (Number(f.original) || null);
    const pct = parseInt(f.pct, 10), steps = f.days.map((d) => parseInt(d, 10)).filter((n) => !isNaN(n));
    const useTiers = f.percentMode && f.tiered;
    const branches = f.all ? p.branches : p.branches.filter((b) => f.sel.has(b.id));
    const created = X.ms(e?.createdAt);
    const base = f.start || (useTiers && created ? created : now);
    const expiry = useTiers ? X.midnight(base) + steps.length * DAY
      : f.keepExpiry && e ? X.ms(e.expiresAt)
      : f.durIdx === -1 ? ((+f.customDays >= 1 && +f.customDays <= 90) ? base + (+f.customDays) * DAY : null)
      : (X.DURATIONS[f.durIdx].days ? base + X.DURATIONS[f.durIdx].days * DAY : null);
    const dt = f.kind !== "new" && dealsOn ? f.dealType : "";
    if (!f.media.length) return err("أرفق صورة أو فيديو للعرض.");
    if (f.kind !== "new" && dealsOn && !dt) return err("اختر نوع عرضك من فوق 👆");
    if (dt === "discount" && myItems.length) { const se = scopeError(f.dScope); if (se) return err(se); }
    if (dt === "bogo" && !myItems.some((x) => x.id === f.bogoItem)) return err("اختر الصنف اللي عليه العرض.");
    if (dt === "promo" && f.orderable && !(Number(f.price) > 0)) return err("عشان الزبون يطلبه، اكتب سعر العرض.");
    if (!f.title.trim()) return err(f.kind === "new" ? "اكتب اسم المنتج." : "اكتب عنوان العرض.");
    if (!useTiers && !f.keepExpiry && f.durIdx === -1 && !(+f.customDays >= 1 && +f.customDays <= 90)) return err("اكتب عدد أيام العرض (من 1 إلى 90).");
    const isNew = f.kind === "new";
    if (!f.percentMode && !(price > 0) && !(isNew && !String(f.price).trim())) return err("اكتب سعر صحيح.");
    if (isNew && original && !(price > 0)) return err("اكتب السعر بعد الخصم.");
    if (useTiers && (steps.length < 2 || steps.length !== f.days.length || steps.some((n) => n < 1 || n > 99))) return err("اكتب نسبة الخصم لكل يوم (من 1 إلى 99).");
    if (f.percentMode && !useTiers && !(pct >= 1 && pct <= 99)) return err("اكتب نسبة الخصم (من 1 إلى 99).");
    if (!branches.length) return err("اختر فرع واحد على الأقل.");
    if (f.start && f.start > now + 60 * DAY) return err("تاريخ البداية بعيد، اختر تاريخ خلال شهرين.");
    if (expiry != null && expiry <= now) return err("حسب التاريخ والمدة العرض منتهي، عدّل البداية أو المدة.");
    const direct = canPublishDirectly();
    const pubDays = paid ? (useTiers ? steps.length : Math.max(1, Math.round(((expiry || 0) - base) / DAY))) : 0;
    if (!e && !direct && m.offers.filter((o) => (X.ms(o.createdAt) || 0) > now - DAY).length >= DAILY_LIMIT)
      return err(`وصلت للحد اليومي (${DAILY_LIMIT} عروض) للحسابات الجديدة. جرّب بكرة 🙏`);
    // الرصيد ما يكفي: ما نرفض أبداً — نعرض الحصري (لو يكفي له) أو نحفظه مسودة
    if (paid && !m.ctx?.isAdmin && !asDraft && bal() < priceFor("publish", pubDays, f.exclusive).cost) return shortfall(pubDays);
    saving = true; $("oSave").disabled = true; $("oSave").textContent = asDraft ? "جاري حفظ المسودة…" : "جاري رفع العرض…"; err("");
    try {
      const urls = []; for (const it of f.media) urls.push(it.file ? await uploadImage(it.file) : it.url);
      const primary = branches[0];
      const discount = useTiers ? steps[0] : f.percentMode ? pct : (original && original > price ? Math.floor((original - price) / original * 100) : 0);
      const data = {
        restaurantName: (p.restaurantName || "").slice(0, 100), offerTitle: f.title.trim().slice(0, 150),
        description: (f.desc.trim() || (isNew ? "منتج جديد متوفر الحين." : "عرض متاح لجميع العملاء.")).slice(0, 2000), cuisineType: f.category.slice(0, 60),
        postType: isNew ? "new" : "offer",
        price, originalPrice: original, currency: "ر.س", discountBadge: discount >= 1 ? `خصم ${discount}% 🔥` : "",
        mediaType: f.isVideo ? "VIDEO" : "IMAGE", mediaUri: urls[0], mediaUris: urls.slice(0, 3), isPromotionalVideo: f.isVideo,
        district: primary.district || "", region: primary.region || "", latitude: primary.latitude, longitude: primary.longitude,
        phone: primary.phone || "", openMinute: primary.openMinute ?? -1, closeMinute: primary.closeMinute ?? -1,
        workingHours: X.hoursText(primary) ? "يومياً " + X.hoursText(primary) : "",
        expiresAt: expiry != null ? Timestamp.fromMillis(expiry) : null, startsAt: f.start ? Timestamp.fromMillis(f.start) : null,
        discountPercent: f.percentMode ? discount : 0, dailyPercents: useTiers ? steps : [], itemsScope: f.percentMode ? f.scope.trim().slice(0, 40) : "",
        grandOpening: isNew ? false : f.opening, exclusive: !!f.exclusive && exclusiveOf(state.config).enabled, tags: f.tags.split(/[,،\n#]/).map((t) => t.trim()).filter((t) => t && t.length <= 25).slice(0, 12),
        delivery: f.delivery, deliveryFee: f.delivery ? Number(f.fee) || 0 : 0, deliveryMinOrder: f.delivery ? Number(f.min) || 0 : 0,
        deliveryFreeOver: f.delivery ? Number(f.free) || 0 : 0,
        logoUrl: p.logoUrl || "", branches: branches.slice(0, 50),
        deal: !dt ? null
          : dt === "discount" ? { type: "discount", scope: myItems.length ? f.dScope.scope : "all", kind: f.dScope.scope === "kind" ? f.dScope.kind : "", section: f.dScope.scope === "section" ? f.dScope.section : "", itemIds: f.dScope.scope === "items" ? f.dScope.itemIds.slice(0, 50) : [] }
          : dt === "bundle" ? { type: "bundle", itemIds: f.packIds.filter((id) => myItems.some((x) => x.id === id)).slice(0, 20) }
          : dt === "bogo" ? { type: "bogo", itemId: f.bogoItem, getPct: f.getPct }
          : { type: "promo", orderable: !!f.orderable && price > 0 },
      };
      if (dt === "discount" && myItems.length) data.itemsScope = f.dScope.scope === "all" ? "على كل المنيو" : f.dScope.scope === "kind" ? `على ${KINDS[f.dScope.kind]?.title || ""}` : f.dScope.scope === "section" ? `على ${f.dScope.section}`.slice(0, 40) : "على أصناف مختارة";
      if (dt === "bundle" && !original) { const sum = myItems.filter((x) => f.packIds.includes(x.id)).reduce((a, x) => a + (+x.price || 0), 0); if (sum > price) { data.originalPrice = Math.round(sum * 100) / 100; data.discountBadge = `خصم ${Math.floor((sum - price) / sum * 100)}% 🔥`; } }
      if (e) {
        // عرض مرجّع من الأدمن: يرجع للمراجعة بعد التعديل، والملاحظة تنمسح
        await updateDoc(doc(db, "offers", e.id), { ...data, adminNote: "", isApproved: e.adminNote ? false : direct ? e.isApproved : false });
      } else {
        const ref = doc(collection(db, "offers"));
        const offerDoc = { ...data, id: ref.id, merchantId: state.user.uid, isApproved: direct, isFeatured: false,
          viewsCount: 0, directionsCount: 0, whatsappCount: 0, reportsCount: 0, createdAt: serverTimestamp() };
        if (asDraft) await setDoc(ref, { ...offerDoc, draft: true });
        else if (paid) {
          // العرض + خصم النشر في عملية وحدة: يا يصيرون كلهم يا ولا شي
          const wb = writeBatch(db);
          wb.set(ref, offerDoc);
          if (addSpend(wb, { item: "publish", offerId: ref.id, days: pubDays, startsAt: base, note: `📢 نشر: ${data.offerTitle}`, excl: data.exclusive }) == null) throw Object.assign(new Error("balance"), { code: "balance" });
          await wb.commit();
        } else await setDoc(ref, offerDoc);
      }
      await setDoc(doc(db, "merchants", state.user.uid), { delivery: data.delivery, deliveryFee: data.deliveryFee, deliveryMinOrder: data.deliveryMinOrder, deliveryFreeOver: data.deliveryFreeOver }, { merge: true }).catch(() => {});
      saving = false; dirty = false; closePage(false);
      if (asDraft) { draftSaved(data.exclusive); return; }
      toast(!direct ? "وصل للمراجعة ⏳ بيظهر على الخريطة بعد الموافقة" : e ? "تم حفظ التعديلات ✅" : isNew ? "تم نشر منتجك الجديد ✨" : "تم نشر العرض ✅", true);
    } catch (ex) {
      console.error(ex); saving = false; asDraft = false; $("oSave").disabled = false; $("oSave").textContent = e ? "حفظ التعديلات ✓" : "انشر العرض ✨";
      err(ex.code === "balance" ? "رصيدك ما يكفي للنشر. اشحن رصيدك من لوحة التاجر."
        : ex.code === "permission-denied" ? (paid ? "رفض النشر: تأكد من رصيدك وحدّث الصفحة، أو تواصل معنا." : "رفض النشر (صلاحيات). تأكد إن حسابك مو موقوف أو تواصل معنا.")
        : (ex.message || "تعذّر النشر"));
    }
  };
}
