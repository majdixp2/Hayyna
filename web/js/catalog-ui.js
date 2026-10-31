// واجهة المنتجات/الخدمات/المنيو: إدارة التاجر + عرضها في صفحة المتجر + السلة والطلب على واتساب + سجل الطلبات.
import {
  collection, doc, onSnapshot, query, where, setDoc, updateDoc, deleteDoc, getDoc, getDocs, getDocsFromServer, getDocFromServer, increment, serverTimestamp, Timestamp, writeBatch,
  disableNetwork, enableNetwork
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { db, state, $, toast, showSheet, hideSheet, openPage, closePage, uploadImage, openStore } from "./app.js";
import * as X from "./logic.js";
import { cropImage } from "./cropper.js";
import {
  KINDS, catalogOf, vatOf, baseKindOf, kindOrder, limitOf, slotId, passCost, riyal, itemVisible, itemPaidUntil, itemGrace,
  vatSplit, fmt, orderCode, orderText, ORDER_STATUS, validVatNumber, statusText, orderSteps, PAY, MODES, deliveryInfo, payMethods, feat,
  DEAL_TYPES, DEAL_SCOPES, storeDeals, dealPrice, cartTotals,
  STORE_CANCEL_REASONS, CUST_CANCEL_REASONS, noshowOf, blockedUntil, noshowCount
} from "./catalog.js";
import { play, soundOn, setSound, unlock } from "./sounds.js";
const F = (k) => feat(state.config, k); // مزايا الطلب من الأدمن

const { esc, cld } = X;
const cat = () => catalogOf(state.config);
const fmtDate = (t) => { const d = new Date(t); return `${d.getDate()}/${d.getMonth() + 1}`; };
const kindPill = (k) => `<span class="kpill" style="background:${KINDS[k].bg};color:${KINDS[k].color}">${KINDS[k].icon} ${KINDS[k].label}</span>`;

// =================== التاجر ===================
const M = { uid: null, items: [], passes: [], orders: [], unsubs: [], ctx: null, onChange: null };

/** يتابع منتجات التاجر وظهورها المدفوع وطلباته (يُستدعى من لوحة التاجر) */
export function watchMerchantCatalog(uid, onChange, branchIds = []) {
  M.onChange = onChange;
  if (M.uid === uid) return;
  M.unsubs.forEach((u) => u()); M.unsubs = []; M.uid = uid; M.items = []; M.passes = []; M.orders = []; M.branchIds = branchIds;
  // ⏰ الطلب المسبق: تنبيه لما يجي وقت تجهيزه
  if (F("preorder")) { loadPre(uid).catch(() => {}); clearInterval(M.dueT); M.dueT = setInterval(checkDue, 60000); setTimeout(checkDue, 5000); }
  const fire = () => M.onChange?.();
  M.unsubs.push(onSnapshot(query(collection(db, "items"), where("merchantId", "==", uid)), (s) => { M.items = s.docs.map((d) => ({ ...d.data(), id: d.id })); fire(); refreshPage(); }, () => {}));
  M.unsubs.push(onSnapshot(query(collection(db, "itemPasses"), where("merchantId", "==", uid)), (s) => { M.passes = s.docs.map((d) => ({ ...d.data(), id: d.id })); fire(); refreshPage(); }, () => {}));
  // 👥 موظف مخصص لفروع: طلبات فروعه بس (والطلبات اللي ما لها فرع)
  const oq = branchIds.length ? query(collection(db, "orders"), where("storeId", "==", uid), where("branchId", "in", [...branchIds.slice(0, 29), ""]))
    : query(collection(db, "orders"), where("storeId", "==", uid));
  M.unsubs.push(onSnapshot(oq, (s) => {
    const prev = new Map(M.orders.map((o) => [o.id, o]));
    M.orders = s.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => X.ms(b.createdAt) - X.ms(a.createdAt)); fire();
    loadCustStats(M.orders.filter((o) => ["sent", "received", "ready", "delivering"].includes(o.status)).map((o) => o.customerUid));
    const S = F("sounds");
    if (!M.ordersLoaded) {
      // أول تحميل: تنبيه واحد بعدد الطلبات اللي فعلاً تنتظرك (القديمة ما نعيد تنبيهها)
      const waiting = M.orders.filter((o) => o.status === "sent").length;
      if (waiting) { toast(waiting === 1 ? "🛎️ عندك طلب جديد" : `🛎️ عندك ${waiting} طلبات جديدة`, true); if (S) play("new"); }
    } else for (const o of M.orders) {
      const p = prev.get(o.id);
      if (!p) { if (o.status === "sent") { toast(X.ms(o.scheduledAt) ? `📅 طلب مسبق جديد #${o.code} — موعده ${X.preWhen(X.ms(o.scheduledAt))}` : `🛎️ طلب جديد #${o.code} من ${o.customerName || "عميل"}`, true); if (S) play("new"); } }
      else if (o.arrivedAt && !p.arrivedAt) { toast(`🚗 العميل وصل — طلب #${o.code}${o.car ? " • " + o.car : ""}${o.spot ? " • " + o.spot : ""}`, true); if (S) play("arrive"); }
      else if (o.cancelReq && !p.cancelReq) { toast(`🙋 العميل يطلب إلغاء #${o.code}: ${o.cancelReq}`, true); if (S) play("cancel"); }
      else if (o.status === "cancelled" && p.status !== "cancelled" && o.cancelBy !== "store") { toast(`✕ العميل ألغى طلب #${o.code}`, true); if (S) play("cancel"); }
    }
    M.ordersLoaded = true;
    if (ordersOpen && (M.ordersVisible ? M.ordersVisible() : !$("sheet").classList.contains("hidden"))) M.onOrders?.();
  }, () => {}));
}
function checkDue() {
  let seen = []; try { seen = JSON.parse(localStorage.getItem("lc_due") || "[]"); } catch {}
  const now = Date.now(), prep = myPrep() * 60000; let hit = false;
  for (const o of M.orders) {
    const t = X.ms(o.scheduledAt);
    if (!t || !["sent", "received"].includes(o.status) || seen.includes(o.id)) continue;
    if (t - prep <= now && t > now - 3600e3) {
      seen.push(o.id); hit = true;
      toast(`⏰ حان وقت تجهيز الطلب المسبق #${o.code} — موعده ${X.timeOf(t)}`, true);
      if (F("sounds")) play("new");
    }
  }
  if (hit) { try { localStorage.setItem("lc_due", JSON.stringify(seen.slice(-50))); } catch {} if (ordersOpen) M.onOrders?.(); }
}
// عدّاد «ما انستلم» لكل عميل (يظهر للمحل جنب الطلب)
const custStats = new Map();
function loadCustStats(uids) {
  if (!F("noshow")) return;
  [...new Set(uids.filter(Boolean))].filter((u) => !custStats.has(u)).forEach((u) => {
    custStats.set(u, null);
    getDoc(doc(db, "custStats", u)).then((d) => { custStats.set(u, d.exists() ? d.data() : null); if (d.exists() && ordersOpen) M.onOrders?.(); }).catch(() => {});
  });
}

/** بطاقة «منتجاتي» في لوحة التاجر */
export function catalogPanelHtml(category) {
  const c = cat();
  if (!c.enabled) return "";
  const base = baseKindOf(state.config, category), now = Date.now();
  const vis = M.items.filter((it) => itemVisible(it, M.passes, c, now)).length;
  const newOrders = M.orders.filter((o) => o.status === "sent").length;
  return `<div class="cat-card">
    ${M.items.some((x) => x.adminNote) ? `<div class="banner red" style="margin:0 0 8px">↩️ عندك ${M.items.filter((x) => x.adminNote).length} صنف يحتاج تعديل — افتح «إدارة منتجاتي»</div>` : ""}
    <div class="cc-top"><b>${KINDS[base].icon} ${KINDS[base].title}</b><small>${M.items.length ? `${M.items.length} صنف • ${vis} ظاهر للناس` : "أضف منتجاتك وخدماتك عشان يشوفها الزبون في صفحة متجرك"}</small></div>
    <div class="cc-kinds">${kindOrder(base).map((k) => { const n = M.items.filter((x) => x.kind === k).length, lim = limitOf(c, k);
      return `<span style="background:${KINDS[k].bg};color:${KINDS[k].color}">${KINDS[k].icon} ${KINDS[k].label}: <b>${n}</b>${lim ? `/${lim}` : ""}</span>`; }).join("")}</div>
    <div class="row2"><button class="btn dir" id="ccManage">🛍️ إدارة منتجاتي</button>${c.orderEnabled ? `<button class="btn call" id="ccOrders">🧾 الطلبات${newOrders ? ` <span class="nb">${newOrders}</span>` : ""}</button>` : ""}</div>
    ${M.items.length && F("quickDeal") && F("dealDiscount") ? `<button class="btn ghost" id="ccQuick">⚡ خصم سريع على المنيو</button>` : ""}
    ${c.orderEnabled && F("sounds") ? `<button class="btn ghost" id="ccSound">${soundOn() ? "🔔 صوت الطلبات: شغّال" : "🔕 صوت الطلبات: مكتوم"}</button>` : ""}
  </div>`;
}
export function bindCatalogPanel(ctx) {
  M.ctx = ctx;
  $("ccManage")?.addEventListener("click", () => openManager());
  $("ccOrders")?.addEventListener("click", openStoreOrders);
  $("ccQuick")?.addEventListener("click", openQuickDeal);
  $("ccSound")?.addEventListener("click", (e) => {
    const on = !soundOn(); setSound(on); if (on) { unlock(); play("new"); }
    e.currentTarget.textContent = on ? "🔔 صوت الطلبات: شغّال" : "🔕 صوت الطلبات: مكتوم";
    toast(on ? "🔔 بتسمع صوت لما يوصلك طلب (خلّ الصفحة مفتوحة)" : "🔕 كتمنا صوت الطلبات", true);
  });
}
/** أصناف التاجر (لربط العروض بالمنيو) */
export async function loadMyItems(uid) {
  if (M.uid === uid && M.items.length) return M.items;
  try { return (await getDocs(query(collection(db, "items"), where("merchantId", "==", uid)))).docs.map((d) => ({ ...d.data(), id: d.id })); } catch { return []; }
}
/** اختيار نطاق الخصم (الكل / نوع / قسم / أصناف) — يُستخدم في الخصم السريع ونموذج العرض */
export function scopePickerHtml(f, items) {
  const kinds = [...new Set(items.map((x) => x.kind))], secs = [...new Set(items.map((x) => x.section).filter(Boolean))];
  return `<div class="chips wrap">${Object.entries(DEAL_SCOPES).filter(([k]) => k === "all" || (k === "kind" && kinds.length > 1) || (k === "section" && secs.length) || (k === "items" && items.length))
      .map(([k, t]) => `<button class="chip${f.scope === k ? " on" : ""}" data-dscope="${k}">${t}</button>`).join("")}</div>
    ${f.scope === "kind" ? `<div class="chips wrap">${kinds.map((k) => `<button class="chip${f.kind === k ? " on" : ""}" data-dkind="${k}">${KINDS[k].icon} ${KINDS[k].title}</button>`).join("")}</div>` : ""}
    ${f.scope === "section" ? `<div class="chips wrap">${secs.map((x) => `<button class="chip${f.section === x ? " on" : ""}" data-dsec="${esc(x)}">${esc(x)}</button>`).join("")}</div>` : ""}
    ${f.scope === "items" ? `<div class="pick-list">${items.map((it) => `<label class="switch-row sub"><span>${KINDS[it.kind]?.icon || ""} ${esc(it.name)} <small>${fmt(it.price)} ر.س</small></span><input type="checkbox" data-ditem="${esc(it.id)}" ${f.itemIds.includes(it.id) ? "checked" : ""}></label>`).join("")}</div>` : ""}`;
}
export function bindScopePicker(root, f, redraw) {
  root.querySelectorAll("[data-dscope]").forEach((b) => b.onclick = () => { f.scope = b.dataset.dscope; redraw(); });
  root.querySelectorAll("[data-dkind]").forEach((b) => b.onclick = () => { f.kind = b.dataset.dkind; redraw(); });
  root.querySelectorAll("[data-dsec]").forEach((b) => b.onclick = () => { f.section = b.dataset.dsec; redraw(); });
  root.querySelectorAll("[data-ditem]").forEach((c) => c.onchange = () => { f.itemIds = c.checked ? [...new Set([...f.itemIds, c.dataset.ditem])] : f.itemIds.filter((x) => x !== c.dataset.ditem); });
}
export const scopeError = (f) => f.scope === "kind" && !f.kind ? "اختر النوع." : f.scope === "section" && !f.section ? "اختر القسم." : f.scope === "items" && !f.itemIds.length ? "اختر صنف واحد على الأقل." : "";

/** ⚡ الخصم السريع: نسبة على المنيو بدون ما يسوي عرض بصورة */
async function openQuickDeal() {
  const uid = M.uid, ref = doc(db, "quickDeals", uid);
  let cur = null; try { const q = await getDoc(ref); cur = q.exists() ? q.data() : null; } catch {}
  const on = cur && X.ms(cur.endsAt) > Date.now();
  const f = { pct: on ? String(cur.pct) : "", scope: on ? cur.scope || "all" : "all", kind: on ? cur.kind || "" : "", section: on ? cur.section || "" : "", itemIds: on ? cur.itemIds || [] : [], days: 1 };
  const draw = () => {
    showSheet(`<div class="pad form"><div class="section-title">⚡ خصم سريع على ${KINDS[baseKindOf(state.config, M.ctx.category())].title}</div>
      ${on ? `<div class="banner green" style="margin:0 0 8px">شغّال الحين: ${cur.pct}% لين ${new Date(X.ms(cur.endsAt)).toLocaleString(X.LOC, { dateStyle: "short", timeStyle: "short" })}</div>` : ""}
      <p class="desc tr">الخصم ينطبق لحاله على أسعار المنيو في صفحة متجرك، والزبون يشوف السعر القديم مشطوب.</p>
      <label class="label">نسبة الخصم %</label>
      <div class="chips wrap">${[10, 15, 20, 25, 30, 40, 50].map((n) => `<button class="chip${+f.pct === n ? " on" : ""}" data-qp="${n}">${n}%</button>`).join("")}</div>
      <input id="qPct" class="input" inputmode="numeric" maxlength="2" value="${esc(f.pct)}" placeholder="أو اكتب نسبة (1 - 90)">
      <label class="label">على وش؟</label>${scopePickerHtml(f, M.items)}
      <label class="label">المدة</label>
      <div class="chips wrap">${[[1, "اليوم بس"], [3, "3 أيام"], [7, "أسبوع"], [14, "أسبوعين"], [30, "شهر"]].map(([d, t]) => `<button class="chip${f.days === d ? " on" : ""}" data-qd="${d}">${t}</button>`).join("")}</div>
      <div id="qErr" class="err"></div>
      <button class="btn dir big" id="qSave">⚡ ${on ? "حدّث الخصم" : "شغّل الخصم"}</button>
      ${on ? `<button class="btn danger" id="qStop">⏹️ أوقف الخصم</button>` : ""}</div>`);
    const b = $("sheetBody");
    $("qPct").oninput = (e) => { f.pct = e.target.value.replace(/\D/g, "").slice(0, 2); };
    b.querySelectorAll("[data-qp]").forEach((x) => x.onclick = () => { f.pct = x.dataset.qp; draw(); });
    b.querySelectorAll("[data-qd]").forEach((x) => x.onclick = () => { f.days = +x.dataset.qd; draw(); });
    bindScopePicker(b, f, draw);
    $("qStop")?.addEventListener("click", async () => { try { await deleteDoc(ref); hideSheet(); toast("وقّفنا الخصم ⏹️", true); } catch { toast("تعذّر الإيقاف"); } });
    $("qSave").onclick = async () => {
      const pct = parseInt(f.pct, 10);
      if (!(pct >= 1 && pct <= 90)) return $("qErr").textContent = "اكتب نسبة من 1 إلى 90.";
      const se = scopeError(f); if (se) return $("qErr").textContent = se;
      const end = f.days === 1 ? new Date(new Date().setHours(23, 59, 0, 0)).getTime() : Date.now() + f.days * 86400000;
      try {
        await setDoc(ref, { merchantId: uid, pct, scope: f.scope, kind: f.scope === "kind" ? f.kind : "", section: f.scope === "section" ? f.section : "",
          itemIds: f.scope === "items" ? f.itemIds.slice(0, 50) : [], endsAt: Timestamp.fromMillis(end), updatedAt: serverTimestamp() });
        hideSheet(); toast(`⚡ شغّلنا خصم ${pct}% على منيوك`, true);
      } catch (e) { console.warn(e); $("qErr").textContent = "تعذّر الحفظ — حدّث الصفحة وجرّب."; }
    };
  };
  draw();
}

let page = null; // { kind }
function refreshPage() { if (page && $("pageBody") && !$("page").classList.contains("hidden") && $("pageBody").querySelector(".cat-mgr")) drawManager(); }

function openManager() {
  const base = baseKindOf(state.config, M.ctx.category());
  page = { kind: base };
  openPage("🛍️ منتجاتي وخدماتي", "", "", () => { page = null; return true; });
  drawManager();
}
function statusOf(it, now) {
  const c = cat();
  if (it.adminNote) return `<span class="pill urgent">↩️ يحتاج تعديل</span>`;
  if (!it.approved) return `<span class="pill">⏳ بانتظار المراجعة</span>`;
  if (it.available === false) return `<span class="pill closed">⛔ نفد</span>`;
  const lim = limitOf(c, it.kind);
  if (lim && it.slot >= lim) return `<span class="pill urgent">مخفي (فوق الحد)</span>`;
  if (c.pricing.enabled) {
    const paid = itemPaidUntil(it, M.passes, now), g = itemGrace(it, c);
    if (paid > now) return `<span class="pill open">✅ ظاهر لين ${fmtDate(paid)}</span>`;
    if (g > now) return `<span class="pill">🕐 مجاني لين ${g === Infinity ? "إشعار آخر" : fmtDate(g)}</span>`;
    if (c.pricing[it.kind]?.active || c.pricing.all?.active) return `<span class="pill urgent">💤 مو ظاهر — ادفع للظهور</span>`;
  }
  return `<span class="pill open">✅ ظاهر</span>`;
}
function drawManager() {
  const c = cat(), now = Date.now(), base = baseKindOf(state.config, M.ctx.category()), k = page.kind, K = KINDS[k];
  const mine = M.items.filter((x) => x.kind === k).sort((a, b) => a.slot - b.slot);
  const lim = limitOf(c, k), full = lim && M.items.filter((x) => x.kind === k && x.slot < lim).length >= lim;
  const allPass = M.passes.filter((p) => p.scope === "all" && X.ms(p.endsAt) > now).reduce((a, p) => Math.max(a, X.ms(p.endsAt)), 0);
  const pr = c.pricing, canPay = pr.enabled && (pr.all.active || KINDS && Object.keys(KINDS).some((x) => pr[x].active));
  $("pageBody").innerHTML = `<div class="cat-mgr form">
    ${pr.enabled ? `<div class="pay-box"><div><b>📢 ظهور منتجاتك للزبائن</b><small>${allPass > now ? `📦 باقة كل المنتجات شغّالة لين ${fmtDate(allPass)}` : "كل منتج يظهر بعد ما تدفع لظهوره (أو باقة لكل منتجاتك)"} • رصيدك ${M.ctx.balance()} نقطة</small></div>
      ${canPay ? `<button class="btn dir" id="cmPay">ادفع للظهور</button>` : ""}</div>` : ""}
    <div class="chips">${kindOrder(base).map((x) => `<button class="chip${x === k ? " on" : ""}" data-kind="${x}" style="${x === k ? `background:${KINDS[x].color};color:#fff;border-color:${KINDS[x].color}` : ""}">${KINDS[x].icon} ${KINDS[x].label} (${M.items.filter((i) => i.kind === x).length}${limitOf(c, x) ? "/" + limitOf(c, x) : ""})</button>`).join("")}</div>
    <button class="btn big" id="cmAdd" ${full ? "disabled" : ""} style="background:${K.color};color:#fff">＋ إضافة ${K.add}</button>
    ${full ? `<small class="hint">وصلت للحد (${lim}). احذف صنف أو تواصل معنا لو تحتاج أكثر.</small>` : ""}
    <div class="ilist">${mine.length ? mine.map((it) => `<div class="irow${it.available === false ? " dim" : ""}" style="border-inline-start:4px solid ${K.color}">
        ${it.imageUrl ? `<img src="${esc(cld(it.imageUrl, 160))}" alt="">` : `<div class="iph" style="background:${K.bg}">${K.icon}</div>`}
        <div class="ir-main"><b>${esc(it.name)}</b><div class="price">${fmt(it.price)} ر.س${it.section ? ` <small>• ${esc(it.section)}</small>` : ""}</div><div class="pills">${statusOf(it, now)}</div>
          ${it.adminNote ? `<div class="admin-note">📝 ${esc(it.adminNote)} — اضغط ✎ وعدّله</div>` : ""}</div>
        <div class="mr-act"><button class="av-tog${it.available === false ? " off" : ""}" data-av="${esc(it.id)}">${it.available === false ? "⛔ نفد" : "✓ متوفر"}</button><button class="icon-btn" data-ie="${esc(it.id)}">✎</button><button class="icon-btn" data-idel="${esc(it.id)}">🗑</button></div>
      </div>`).join("") : `<div class="empty-inline">ما أضفت ${K.label} للحين</div>`}</div>
  </div>`;
  const body = $("pageBody");
  body.querySelectorAll("[data-kind]").forEach((b) => b.onclick = () => { page.kind = b.dataset.kind; drawManager(); });
  $("cmAdd").onclick = () => itemForm(null, k);
  $("cmPay")?.addEventListener("click", () => openPay(k));
  body.querySelectorAll("[data-ie]").forEach((b) => b.onclick = () => itemForm(M.items.find((x) => x.id === b.dataset.ie)));
  body.querySelectorAll("[data-av]").forEach((b) => b.onclick = async () => {
    const it = M.items.find((x) => x.id === b.dataset.av);
    try { await updateDoc(doc(db, "items", it.id), { available: it.available === false, approved: it.approved && direct(), updatedAt: serverTimestamp() }); toast(it.available === false ? "رجع متوفر ✅" : "صار «نفد»"); }
    catch (e) { console.error(e); toast("تعذّر التحديث"); }
  });
  body.querySelectorAll("[data-idel]").forEach((b) => b.onclick = async () => {
    const it = M.items.find((x) => x.id === b.dataset.idel);
    if (!confirm(`حذف «${it.name}»؟`)) return;
    try { await deleteDoc(doc(db, "items", it.id)); toast("انحذف"); } catch { toast("تعذّر الحذف"); }
  });
}
const direct = () => M.ctx.isAdmin() || M.ctx.direct() || !cat().review;

function itemForm(it, kind) {
  const e = it || null, k = e ? e.kind : kind, K = KINDS[k];
  const f = { name: e?.name || "", price: e ? String(e.price) : "", desc: e?.desc || "", section: e?.section || "", available: e ? e.available !== false : true, img: e?.imageUrl || "", file: null };
  const sections = [...new Set(M.items.filter((x) => x.kind === k).map((x) => x.section).filter(Boolean))];
  const vatNo = M.ctx.vatNumber();
  const draw = () => {
    showSheet(`<div class="pad form"><div class="section-title" style="color:${K.color}">${K.icon} ${e ? "تعديل" : "إضافة"} ${K.add}</div>
      <label class="upload wide">${f.file || f.img ? `<img src="${esc(f.file ? URL.createObjectURL(f.file) : cld(f.img, 600))}" alt="">` : "📷 صورة (اختياري)"}<input type="file" accept="image/*" id="ifImg" hidden></label>
      <label class="label">الاسم *</label><input id="ifName" class="input" maxlength="80" value="${esc(f.name)}" placeholder="${k === "menu" ? "مثال: شاورما صاج كبير" : k === "service" ? "مثال: تفصيل ثوب" : "مثال: عطر 100 مل"}">
      <label class="label">السعر (ر.س)${vatNo ? " — شامل الضريبة" : ""} *</label><input id="ifPrice" class="input" inputmode="decimal" value="${esc(f.price)}" placeholder="مثال: 23">
      <label class="label">القسم (اختياري)</label><input id="ifSec" class="input" maxlength="40" list="ifSecs" value="${esc(f.section)}" placeholder="${k === "menu" ? "مثال: مشروبات، وجبات" : "مثال: رجالي، نسائي"}">
      <datalist id="ifSecs">${sections.map((x) => `<option value="${esc(x)}">`).join("")}</datalist>
      <label class="label">وصف قصير</label><textarea id="ifDesc" class="input" rows="2" maxlength="300">${esc(f.desc)}</textarea>
      <label class="switch-row"><span>متوفر الحين</span><input type="checkbox" id="ifAv" ${f.available ? "checked" : ""}></label>
      ${!direct() ? `<small class="hint">⏳ بيظهر بعد مراجعة فريق لكوفر (عادة خلال ساعات). وثّق متجرك عشان تنزل منتجاتك مباشرة.</small>` : ""}
      <div id="ifErr" class="err"></div><button class="btn big" id="ifSave" style="background:${K.color};color:#fff">حفظ</button></div>`);
    const keep = () => { f.name = $("ifName").value; f.price = $("ifPrice").value; f.section = $("ifSec").value; f.desc = $("ifDesc").value; f.available = $("ifAv").checked; };
    $("ifImg").onchange = async (ev) => { const fl = ev.target.files[0]; if (!fl) return; keep(); const c = await cropImage(fl, { aspect: 1, title: "قص الصورة", outW: 1000 }); if (c) { f.file = c; draw(); } else draw(); };
    $("ifSave").onclick = async () => {
      keep();
      const err = (t) => { $("ifErr").textContent = t; };
      const price = Number(String(f.price).replace(/[٠-٩]/g, (c) => "٠١٢٣٤٥٦٧٨٩".indexOf(c)));
      if (!f.name.trim()) return err("اكتب الاسم.");
      if (!(price >= 0) || f.price === "") return err("اكتب سعر صحيح.");
      $("ifSave").disabled = true; err("");
      try {
        const url = f.file ? await uploadImage(f.file, 1000) : f.img;
        const data = { name: f.name.trim().slice(0, 80), price: Math.round(price * 100) / 100, desc: f.desc.trim().slice(0, 300), section: f.section.trim().slice(0, 40),
          imageUrl: url || "", available: f.available, approved: e?.adminNote ? false : direct(), adminNote: "", updatedAt: serverTimestamp() };
        if (e) await updateDoc(doc(db, "items", e.id), data);
        else {
          const used = new Set(M.items.filter((x) => x.kind === k).map((x) => x.slot));
          let n = 0; while (used.has(n)) n++;
          const lim = limitOf(cat(), k);
          if (lim && n >= lim) { $("ifSave").disabled = false; return err(`وصلت للحد (${lim}).`); }
          const id = slotId(M.uid, k, n);
          await setDoc(doc(db, "items", id), { ...data, id, merchantId: M.uid, kind: k, slot: n, createdAt: serverTimestamp() });
        }
        hideSheet(); toast(direct() ? "انحفظ ✅" : "انحفظ ✅ بيظهر بعد المراجعة", true);
      } catch (ex) { console.error(ex); $("ifSave").disabled = false; err(ex.code === "permission-denied" ? "رفض الحفظ (صلاحيات). تأكد إن الخدمة شغّالة أو تواصل معنا." : (ex.message || "تعذّر الحفظ")); }
    };
  };
  draw();
}

/** الدفع لظهور المنتجات: باقة كل المنتجات، أو منتجات محددة من نوع */
function openPay(kind) {
  const c = cat(), pr = c.pricing, now = Date.now();
  const kinds = Object.keys(KINDS).filter((k) => pr[k].active);
  const s = { scope: pr.all.active ? "all" : "kind", kind: pr[kind]?.active ? kind : kinds[0], days: 30, sel: null };
  const DAYS = [1, 3, 7, 14, 30, 60, 90, 180, 365];
  const draw = () => {
    const list = M.items.filter((x) => x.kind === s.kind && x.approved);
    if (!s.sel || s.selKind !== s.kind) { s.sel = new Set(list.filter((x) => itemPaidUntil(x, M.passes, now) <= now).map((x) => x.id)); s.selKind = s.kind; }
    const n = s.scope === "all" ? 1 : s.sel.size, ph = s.scope === "all" ? pr.all.priceH : pr[s.kind]?.priceH || 0;
    const cost = n ? passCost(ph, n, s.days) : 0;
    showSheet(`<div class="pad form"><div class="section-title">📢 ظهور منتجاتك</div>
      <p class="desc tr">رصيدك: <b>${M.ctx.balance()}</b> نقطة</p>
      <div class="chips">${pr.all.active ? `<button class="chip${s.scope === "all" ? " on" : ""}" data-sc="all">📦 باقة كل المنتجات (${riyal(pr.all.priceH)}/يوم)</button>` : ""}
        ${kinds.length ? `<button class="chip${s.scope === "kind" ? " on" : ""}" data-sc="kind">✔ منتجات محددة</button>` : ""}</div>
      ${s.scope === "kind" ? `<div class="chips">${kinds.map((k) => `<button class="chip${s.kind === k ? " on" : ""}" data-pk="${k}">${KINDS[k].icon} ${KINDS[k].label} (${riyal(pr[k].priceH)}/يوم)</button>`).join("")}</div>
        <div class="pick">${list.length ? list.map((x) => { const pu = itemPaidUntil(x, M.passes, now);
          return `<label class="switch-row sub"><span>${esc(x.name)}${pu > now ? ` <small class="hint">(ظاهر لين ${fmtDate(pu)})</small>` : ""}</span><input type="checkbox" data-pi="${esc(x.id)}" ${s.sel.has(x.id) ? "checked" : ""}></label>`; }).join("")
          : `<div class="empty-inline">ما فيه ${KINDS[s.kind].label} معتمد للحين</div>`}</div>` : ""}
      <label class="label">المدة</label>
      <div class="chips wrap">${DAYS.map((d) => `<button class="chip${s.days === d ? " on" : ""}" data-d="${d}">${d === 365 ? "سنة" : d === 180 ? "6 شهور" : d === 90 ? "3 شهور" : d === 60 ? "شهرين" : d === 30 ? "شهر" : d === 1 ? "يوم" : d + " أيام"}</button>`).join("")}</div>
      <div class="cost-line">💰 التكلفة: <b>${cost} نقطة</b>${s.scope === "kind" ? ` <small>(${n} × ${s.days} يوم × ${riyal(ph)})</small>` : ` <small>(${s.days} يوم × ${riyal(ph)})</small>`}</div>
      <div id="pyErr" class="err"></div><button class="btn dir big" id="pyGo" ${!cost ? "disabled" : ""}>ادفع ${cost} نقطة</button></div>`);
    const b = $("sheetBody");
    b.querySelectorAll("[data-sc]").forEach((x) => x.onclick = () => { s.scope = x.dataset.sc; draw(); });
    b.querySelectorAll("[data-pk]").forEach((x) => x.onclick = () => { s.kind = x.dataset.pk; draw(); });
    b.querySelectorAll("[data-d]").forEach((x) => x.onclick = () => { s.days = +x.dataset.d; draw(); });
    b.querySelectorAll("[data-pi]").forEach((x) => x.onchange = () => { x.checked ? s.sel.add(x.dataset.pi) : s.sel.delete(x.dataset.pi); draw(); });
    $("pyGo").onclick = async () => {
      const admin = M.ctx.isAdmin(), bal = M.ctx.balance();
      if (!admin && bal < cost) return $("pyErr").textContent = `رصيدك ما يكفي (تحتاج ${cost} نقطة).`;
      if (!confirm(`تأكيد الدفع: ${cost} نقطة؟`)) return;
      const wb = writeBatch(db), ref = doc(collection(db, "itemPasses"));
      const curAll = M.passes.filter((p) => p.scope === "all").reduce((a, p) => Math.max(a, X.ms(p.endsAt)), 0);
      const start = s.scope === "all" ? Math.max(Date.now(), curAll) : Date.now();
      wb.set(ref, { id: ref.id, merchantId: M.uid, scope: s.scope, kind: s.scope === "all" ? "" : s.kind, itemIds: s.scope === "all" ? [] : [...s.sel].slice(0, 200),
        days: s.days, cost, createdAt: serverTimestamp(), startsAt: Timestamp.fromMillis(start), endsAt: Timestamp.fromMillis(start + s.days * X.DAY) });
      if (!admin) {
        wb.set(doc(db, "wallets", M.uid), { balance: bal - cost, lastOp: { kind: "catalog", ref: ref.id }, updatedAt: serverTimestamp() });
        wb.set(doc(collection(db, "wallets", M.uid, "tx")), { kind: "spend", points: -cost, note: (s.scope === "all" ? `📦 باقة كل المنتجات ${s.days} يوم` : `${KINDS[s.kind].icon} ظهور ${n} ${KINDS[s.kind].label} ${s.days} يوم`).slice(0, 120), ref: ref.id, at: serverTimestamp() });
      }
      $("pyGo").disabled = true;
      try { await wb.commit(); hideSheet(); toast(`تم ✅ خصمنا ${cost} نقطة`, true); }
      catch (e) { console.error(e); $("pyGo").disabled = false; $("pyErr").textContent = "تعذّرت العملية. حدّث الصفحة وجرّب مرة ثانية."; }
    };
  };
  draw();
}

/** طلبات المحل */
let ordersOpen = false;
export const storeItems = () => M.items; // أصناف المحل (شاشة الموظف: نفد اليوم)
// مجموعات الطلبات عند المحل: اللي فيها شغل مفتوحة، والمنتهية والملغية مطوية
const ORDER_GROUPS = [
  { k: "sched", t: "📅 مجدولة (موعدها لاحق)", st: ["sent", "received"], open: true },
  { k: "new", t: "🆕 الجديدة", st: ["sent"], open: true },
  { k: "prep", t: "👨‍🍳 جاري التجهيز", st: ["received"], open: true },
  { k: "ready", t: "🛍️ جاهز / 🚚 بالطريق", st: ["ready", "delivering"], open: true },
  { k: "done", t: "✅ المنتهية", st: ["done"], open: false },
  { k: "cancelled", t: "✕ الملغية / ما انستلمت", st: ["cancelled", "noshow"], open: false },
];
const groupOpen = new Map();
// 📅 الطلب المجدول يظل في «مجدولة» لين يجي وقت تجهيزه
const myPrep = () => X.preCfg(preCache[M.uid]).prepMin;
const isAhead = (o) => { const t = X.ms(o.scheduledAt); return !!t && t - myPrep() * 60000 > Date.now(); };
const REMIND_MIN = 30; // تذكير المحل لو الطلب جاهز وما انستلم
/** شاشة طلبات المحل. opts.el = ترسمها داخل عنصر (شاشة الموظف) بدل النافذة */
/** 📅 إعدادات الطلب المسبق (صاحب المحل) */
export function openPreSettings(onBack) {
  const sid = state.user?.uid; if (!sid) return;
  const store = state.stores.find((x) => x.uid === sid) || {};
  const f = { ...X.preCfg(preCache[sid]) }, mins = [15, 30, 45, 60, 90, 120];
  if (!mins.includes(f.prepMin)) mins.push(f.prepMin);
  const tSel = (id, v) => `<select id="${id}" class="input"><option value="-1" ${v < 0 ? "selected" : ""}>حسب الدوام</option>${X.minuteOptions().map((mm) => `<option value="${mm}" ${mm === v ? "selected" : ""}>${X.fmtMinute(mm)}</option>`).join("")}</select>`;
  const draw = () => {
    showSheet(`<div class="pad form"><div class="section-title">📅 الطلب المسبق</div>
      <p class="desc tr">الزبون يطلب الحين ويختار موعد لاحق — حتى لو المحل مغلق.</p>
      <label class="switch-row"><span><b>تفعيل «اطلب مسبقاً»</b></span><input type="checkbox" id="pOn" ${f.on ? "checked" : ""}></label>
      ${!f.on ? "" : `
      <label class="label">أبعد موعد يقدر الزبون يختاره</label>
      <div class="chips"><button class="chip${f.maxDays === 0 ? " on" : ""}" data-md="0">نفس اليوم بس</button><button class="chip${f.maxDays > 0 ? " on" : ""}" data-md="${f.maxDays || 2}">عدد أيام</button></div>
      ${f.maxDays > 0 ? `<div class="row2"><input id="pDays" class="input" inputmode="numeric" maxlength="2" value="${f.maxDays}"><span class="hint" style="align-self:center">يوم قدام (لين 14)</span></div>` : ""}
      <label class="label">⏱️ وقت التجهيز (أقرب موعد بعد كم؟)</label>
      <div class="chips wrap">${mins.map((m) => `<button class="chip${f.prepMin === m ? " on" : ""}" data-pm="${m}">${m} دقيقة</button>`).join("")}</div>
      <label class="switch-row"><span>🏃 استلام بموعد <small class="hint">(الزبون يختار وقت داخل الدوام كل ربع ساعة)</small></span><input type="checkbox" id="pPick" ${f.pickup ? "checked" : ""}></label>
      ${store.delivery ? `<label class="switch-row"><span>🚚 توصيل بموعد <small class="hint">(فترات تحددها أنت)</small></span><input type="checkbox" id="pDel" ${f.delivery ? "checked" : ""}></label>
      ${f.delivery ? `<div class="pre-del">
        <div class="row2"><div><label class="label">التوصيل من</label>${tSel("pFrom", f.delFrom)}</div><div><label class="label">لين</label>${tSel("pTo", f.delFrom < 0 ? -1 : f.delTo)}</div></div>
        <label class="label">طول الفترة</label>
        <div class="chips">${[30, 60, 120].map((m) => `<button class="chip${f.slotMin === m ? " on" : ""}" data-sm="${m}">${m === 30 ? "نص ساعة" : m === 60 ? "ساعة" : "ساعتين"}</button>`).join("")}</div>
        <label class="label">الحد الأقصى للطلبات في كل فترة</label>
        <input id="pCap" class="input" inputmode="numeric" maxlength="2" value="${f.slotCap}">
      </div>` : ""}` : ""}`}
      <div id="pErr" class="err"></div>
      <button class="btn dir big" id="pSave">حفظ ✓</button><button class="btn ghost" id="pBack">‹ رجوع</button></div>`);
    const b = $("sheetBody"), keepIn = () => {
      if ($("pDays")) f.maxDays = Math.max(1, Math.min(14, parseInt($("pDays").value.replace(/[٠-٩]/g, (c) => "٠١٢٣٤٥٦٧٨٩".indexOf(c)), 10) || 1));
      if ($("pCap")) f.slotCap = Math.max(1, Math.min(50, parseInt($("pCap").value.replace(/[٠-٩]/g, (c) => "٠١٢٣٤٥٦٧٨٩".indexOf(c)), 10) || 1));
      if ($("pFrom")) { f.delFrom = +$("pFrom").value; f.delTo = f.delFrom < 0 ? -1 : +$("pTo").value; if (f.delFrom >= 0 && f.delTo < 0) f.delTo = (f.delFrom + 240) % 1440; }
    };
    $("pOn").onchange = (e) => { keepIn(); f.on = e.target.checked; draw(); };
    $("pPick")?.addEventListener("change", (e) => { f.pickup = e.target.checked; });
    $("pDel")?.addEventListener("change", (e) => { keepIn(); f.delivery = e.target.checked; draw(); });
    $("pFrom")?.addEventListener("change", () => { keepIn(); draw(); });
    b.querySelectorAll("[data-md]").forEach((x) => x.onclick = () => { keepIn(); f.maxDays = +x.dataset.md; draw(); });
    b.querySelectorAll("[data-pm]").forEach((x) => x.onclick = () => { keepIn(); f.prepMin = +x.dataset.pm; draw(); });
    b.querySelectorAll("[data-sm]").forEach((x) => x.onclick = () => { keepIn(); f.slotMin = +x.dataset.sm; draw(); });
    $("pBack").onclick = () => onBack ? onBack() : hideSheet();
    $("pSave").onclick = async () => {
      keepIn();
      if (f.on && !f.pickup && !(store.delivery && f.delivery)) return $("pErr").textContent = "فعّل الاستلام أو التوصيل بموعد.";
      const data = { on: !!f.on, maxDays: f.maxDays | 0, prepMin: f.prepMin | 0, pickup: !!f.pickup, delivery: !!(store.delivery && f.delivery),
        delFrom: f.delFrom | 0, delTo: f.delTo | 0, slotMin: [30, 60, 120].includes(f.slotMin) ? f.slotMin : 60, slotCap: f.slotCap | 0 || 1, updatedAt: serverTimestamp() };
      $("pSave").disabled = true;
      try { await setDoc(doc(db, "preorderCfg", sid), data); preCache[sid] = data; toast(data.on ? "📅 الطلب المسبق مفعّل ✅" : "انقفل الطلب المسبق", true); onBack ? onBack() : hideSheet(); }
      catch (e) { console.error(e); $("pSave").disabled = false; $("pErr").textContent = "تعذّر الحفظ"; }
    };
  };
  draw();
}

// ⏸️ زر إيقاف استقبال الطلبات (صاحب المحل + موظف الطلبات)
let pauseUnsub = null, pauseSid = "", myPaused = false, pauseRedraw = null, ordBr = "";
const mySid = () => state.staff?.storeId || state.user?.uid || "";
function watchMyPause(redraw) {
  pauseRedraw = redraw;
  const sid = mySid();
  if (!sid || pauseSid === sid) return;
  pauseUnsub?.(); pauseSid = sid;
  loadPre(sid).then(() => pauseRedraw?.());
  pauseUnsub = onSnapshot(doc(db, "storeStatus", sid), (d) => { setPause(sid, d); myPaused = pauseCache[sid]; pauseRedraw?.(); }, (e) => console.warn("storeStatus", e));
}
// فروع المحل، والفروع اللي يقدر هذا الحساب يتحكم فيها (الموظف المخصص = فروعه بس)
const storeBranches = () => (state.stores.find((x) => x.uid === mySid())?.branches || []).filter((b) => b && b.id);
const myScope = () => { const bs = storeBranches(), ids = state.staff?.branchIds || []; return ids.length ? bs.filter((b) => ids.includes(b.id)) : bs; };
const canAll = () => !(state.staff?.branchIds || []).length;
const bName = (b) => b.name || X.districtLabel(b.district) || "الفرع";
async function savePause(paused, branches) {
  const sid = mySid(); if (!sid) return;
  const by = state.staff ? (state.staff.name || "موظف " + state.staff.n) : "صاحب المحل";
  await setDoc(doc(db, "storeStatus", sid), { paused, pausedAt: serverTimestamp(), by: String(by).slice(0, 60), pausedBranches: [...branches].slice(0, 50) });
}
/** حالة الاستقبال لهذا الحساب: كل شي شغّال / بعض الفروع موقفة / الكل موقف */
function pauseState() {
  const sid = mySid(), sc = myScope(), br = pauseBr[sid] || new Set();
  if (myPaused && canAll()) return { all: true, n: 0 };
  const off = sc.filter((b) => br.has(b.id));
  return { all: myPaused || (sc.length > 0 && off.length === sc.length), n: off.length, off };
}
async function togglePause() {
  const sid = mySid(); if (!sid) return;
  const sc = myScope(), br = new Set(pauseBr[sid] || []);
  try {
    // فرع واحد أو محل بفرع واحد: زر مباشر
    if (sc.length <= 1) {
      if (canAll() && storeBranches().length <= 1) { await savePause(!myPaused, []); toast(!myPaused ? "▶️ رجع استقبال الطلبات" : "⏸️ وقّفنا الطلبات — الزبائن يشوفون إن المحل موقف مؤقتاً", true); return; }
      const id = sc[0]?.id; if (!id) return;
      br.has(id) ? br.delete(id) : br.add(id);
      await savePause(myPaused, br); toast(br.has(id) ? `⏸️ وقّفنا طلبات فرع ${bName(sc[0])}` : `▶️ رجع استقبال طلبات فرع ${bName(sc[0])}`, true); return;
    }
    return pauseSheet();
  } catch (e) { console.error(e); toast("تعذّر التغيير"); }
}
function pauseSheet() {
  const sid = mySid(), sc = myScope();
  let all = myPaused, br = new Set(pauseBr[sid] || []);
  const back = () => { ordersOpen = true; if (M.ordersTarget) { hideSheet(); pauseRedraw?.(); } else openStoreOrders(); };
  const draw = () => {
    showSheet(`<div class="pad form"><div class="section-title">⏸️ إيقاف استقبال الطلبات</div>
      <p class="desc tr">الزبائن يشوفون إن الفرع موقف مؤقتاً، وما يقدرون يطلبون منه.</p>
      ${canAll() ? `<label class="switch-row"><span><b>⏸️ كل الفروع</b></span><input type="checkbox" id="pzAll" ${all ? "checked" : ""}></label>` : ""}
      ${all && canAll() ? "" : sc.map((b) => `<label class="switch-row sub"><span>⏸️ فرع ${esc(bName(b))}</span><input type="checkbox" data-pz="${esc(b.id)}" ${br.has(b.id) ? "checked" : ""}></label>`).join("")}
      <button class="btn dir big" id="pzSave">حفظ</button><button class="btn ghost" id="pzBack">‹ رجوع</button></div>`);
    $("pzAll")?.addEventListener("change", (e) => { all = e.target.checked; draw(); });
    $("sheetBody").querySelectorAll("[data-pz]").forEach((x) => x.onchange = () => { x.checked ? br.add(x.dataset.pz) : br.delete(x.dataset.pz); });
    $("pzBack").onclick = back;
    $("pzSave").onclick = async () => {
      try { await savePause(canAll() ? all : myPaused, br); toast("انحفظ ✅", true); back(); }
      catch (e) { console.error(e); toast("تعذّر التغيير"); }
    };
  };
  ordersOpen = false; draw();
}

export function openStoreOrders(opts = {}) {
  ordersOpen = true;
  const target = opts.el || null;
  const box = () => target || $("sheetBody");
  const visible = () => target ? document.body.contains(target) : !$("sheet").classList.contains("hidden");
  M.ordersVisible = visible; M.ordersTarget = target;
  const showMain = (html) => { if (target) target.innerHTML = html; else showSheet(html); };
  const draw = () => {
    if (!ordersOpen) return;
    watchMyPause(() => { if (ordersOpen && visible()) draw(); });
    if (ordBr && !storeBranches().some((b) => b.id === ordBr)) ordBr = "";
    const list = ordBr ? M.orders.filter((o) => o.branchId === ordBr) : M.orders;
    const scroll = target ? null : $("sheetBody").scrollTop;
    showMain(`<div class="pad"><div class="section-title">🧾 الطلبات (${list.length})</div>
      ${(() => { const ps = pauseState(), multi = myScope().length > 1;
        return `<div class="pause-row${ps.all || ps.n ? " on" : ""}"><span>${ps.all ? "⏸️ الطلبات موقفة — الزبائن ما يقدرون يطلبون" : ps.n ? `⏸️ موقف: ${esc(ps.off.map(bName).join("، "))}` : "🟢 تستقبل طلبات"}</span>
        <button class="btn small ${ps.all ? "dir" : "ghost"}" id="pauseBtn">${multi ? "⏸️ إيقاف / تشغيل" : ps.all || ps.n ? "▶️ رجّع الاستقبال" : "⏸️ أوقف الطلبات"}</button></div>`; })()}
      ${!state.staff?.branchIds?.length && storeBranches().length > 1 ? `<div class="chips br-filter"><button class="chip${!ordBr ? " on" : ""}" data-obr="">كل الفروع</button>${storeBranches().map((b) => `<button class="chip${ordBr === b.id ? " on" : ""}" data-obr="${esc(b.id)}">${esc(bName(b))} <small>${M.orders.filter((o) => o.branchId === b.id && ["sent", "received", "ready", "delivering"].includes(o.status)).length || ""}</small></button>`).join("")}</div>` : ""}
      ${F("preorder") && !state.staff ? `<button class="pre-set" id="preSet">📅 الطلب المسبق: <b>${X.preCfg(preCache[mySid()]).on ? "مفعّل" : "مقفل"}</b> <span>إعدادات ‹</span></button>` : ""}
      ${list.length ? ORDER_GROUPS.map((g) => {
        const os = list.filter((o) => g.st.includes(o.status) && (g.k === "sched" ? isAhead(o) : ["new", "prep"].includes(g.k) ? !isAhead(o) : true)).slice(0, 100);
        if (g.k === "sched") os.sort((a, b) => X.ms(a.scheduledAt) - X.ms(b.scheduledAt));
        if (!os.length) return "";
        const open = groupOpen.has(g.k) ? groupOpen.get(g.k) : g.open;
        return `<details class="ogrp g-${g.k}" data-g="${g.k}"${open ? " open" : ""}><summary>${g.t} <span class="cnt">${os.length}</span></summary>${os.map((o) => orderCard(o, true)).join("")}</details>`;
      }).join("") : `<div class="empty-inline">ما وصلتك طلبات للحين</div>`}</div>`);
    if (scroll != null) $("sheetBody").scrollTop = scroll;
    const b = box();
    b.querySelector("#pauseBtn")?.addEventListener("click", togglePause);
    b.querySelectorAll("[data-obr]").forEach((x) => x.onclick = () => { ordBr = x.dataset.obr; draw(); });
    b.querySelector("#preSet")?.addEventListener("click", () => { if (!target) ordersOpen = false; openPreSettings(() => { if (target) hideSheet(); ordersOpen = true; draw(); }); });
    b.querySelectorAll("details[data-g]").forEach((d) => d.addEventListener("toggle", () => groupOpen.set(d.dataset.g, d.open)));
    b.querySelectorAll("[data-os]").forEach((x) => x.onclick = () => {
      const o = M.orders.find((y) => y.id === x.dataset.oid), st = x.dataset.os;
      if (st === "received") return F("eta") ? pickEta(o) : setStatus(o, "received");
      if (st === "ready") return pickReady(o);
      if (st === "cancelled") return storeCancel(o);
      if (st === "noshow") return markNoshow(o);
      setStatus(o, st, st === "ready" ? { readyAt: serverTimestamp() } : {});
    });
    b.querySelectorAll("[data-creq]").forEach((x) => x.onclick = () => {
      const o = M.orders.find((y) => y.id === x.dataset.oid);
      if (x.dataset.creq === "ok") setStatus(o, "cancelled", { cancelReqAns: "approved", cancelBy: "customer", cancelReason: o.cancelReq || "" });
      else setStatus(o, o.status, { cancelReqAns: "rejected" });
    });
  };
  const setStatus = async (o, st, extra = {}) => {
    try { await updOrder(o, { status: st, updatedAt: serverTimestamp(), ...extra }); Object.assign(o, { status: st }, extra); if (target) hideSheet(); ordersOpen = true; draw(); }
    catch (e) { console.error(e); toast("تعذّر التحديث"); if (target) hideSheet(); ordersOpen = true; draw(); }
  };
  // استلام الطلب: المحل يختار وقت تقريبي للتجهيز
  const pickEta = (o) => {
    if (!target) ordersOpen = false;
    showSheet(`<div class="pad"><div class="section-title">👍 استلام الطلب #${esc(o.code)}</div>
      <p class="desc tr">${o.mode === "delivery" ? "متى يطلع للتوصيل تقريباً؟" : "متى يكون جاهز للاستلام تقريباً؟"}</p>
      <div class="chips wrap">${[10, 15, 20, 30, 45, 60].map((m) => `<button class="chip" data-eta="${m}">${m} دقيقة</button>`).join("")}</div>
      <button class="btn call" id="etaSkip" style="margin-top:10px">بدون وقت</button></div>`);
    $("sheetBody").querySelectorAll("[data-eta]").forEach((x) => x.onclick = () => setStatus(o, "received", { etaAt: Timestamp.fromMillis(Date.now() + +x.dataset.eta * 60000) }));
    $("etaSkip").onclick = () => setStatus(o, "received");
  };
  // 🛍️ جاهز للاستلام: ملاحظة توصل للعميل (وين يستلم)
  const pickReady = (o) => {
    if (!target) ordersOpen = false;
    let quick = []; try { quick = JSON.parse(localStorage.getItem("lc_readyNotes") || "[]"); } catch {}
    const picks = [...new Set([...quick, "عند خدمة العملاء", "عند الكاشير", "كاشير رقم 1", "كاشير رقم 2", "كاشير رقم 3", o.mode === "curbside" ? "بنطلّعه لسيارتك" : "عند الباب"])].slice(0, 9);
    showSheet(`<div class="pad form"><div class="section-title">🛍️ طلب #${esc(o.code)} جاهز</div>
      <p class="desc tr">تبي تكتب للعميل وين يستلم؟ (اختياري)</p>
      <div class="chips wrap">${picks.map((r) => `<button class="chip" data-rn="${esc(r)}">${esc(r)}</button>`).join("")}</div>
      <input id="rnTxt" class="input" maxlength="80" placeholder="مثال: لدى كاشير رقم 3">
      <button class="btn dir big" id="rnGo">✅ جاهز — بلّغ العميل</button><button class="btn ghost" id="rnBack">‹ رجوع</button></div>`);
    $("sheetBody").querySelectorAll("[data-rn]").forEach((x) => x.onclick = () => { $("rnTxt").value = x.dataset.rn; });
    $("rnBack").onclick = () => { if (target) hideSheet(); ordersOpen = true; draw(); };
    $("rnGo").onclick = () => {
      const note = $("rnTxt").value.trim().slice(0, 80);
      if (note) { try { localStorage.setItem("lc_readyNotes", JSON.stringify([note, ...quick.filter((q) => q !== note)].slice(0, 4))); } catch {} }
      setStatus(o, "ready", { readyAt: serverTimestamp(), ...(note ? { readyNote: note } : {}) });
    };
  };
  // إلغاء من المحل: سبب من القائمة (يوصل للعميل)
  const storeCancel = (o) => {
    if (!target) ordersOpen = false; let reason = "";
    const d = () => {
      showSheet(`<div class="pad form"><div class="section-title">✕ إلغاء الطلب #${esc(o.code)}</div>
        <p class="desc tr">اختر السبب — يوصل للعميل في «طلباتي».</p>
        <div class="chips wrap">${STORE_CANCEL_REASONS.map((r) => `<button class="chip${reason === r ? " on" : ""}" data-r="${esc(r)}">${esc(r)}</button>`).join("")}</div>
        <input id="crTxt" class="input" maxlength="120" placeholder="أو اكتب سبب ثاني" value="${STORE_CANCEL_REASONS.includes(reason) ? "" : esc(reason)}">
        <button class="btn danger big" id="crGo">✕ ألغِ الطلب</button><button class="btn ghost" id="crBack">‹ رجوع</button></div>`);
      $("sheetBody").querySelectorAll("[data-r]").forEach((x) => x.onclick = () => { reason = x.dataset.r; d(); });
      $("crTxt").oninput = (e) => { reason = e.target.value; };
      $("crBack").onclick = () => { if (target) hideSheet(); ordersOpen = true; draw(); };
      $("crGo").onclick = () => { if (!reason.trim()) return toast("اختر سبب الإلغاء"); setStatus(o, "cancelled", { cancelBy: "store", cancelReason: reason.trim().slice(0, 120) }); };
    };
    d();
  };
  // 🚫 ما انستلم: الطلب + سجل + عدّاد العميل في عملية وحدة
  const markNoshow = async (o) => {
    if (!confirm(`العميل ما استلم طلب #${o.code}؟ بينحسب عليه، ولو تكرر يتوقف عن الطلب مؤقتاً.`)) return;
    try {
      const ns = noshowOf(state.config), now = Date.now();
      const cur = (await getDoc(doc(db, "custStats", o.customerUid)).catch(() => null))?.data?.() || null;
      const fresh = !cur || X.ms(cur.windowStart) <= now - ns.days * 864e5 + 60000;
      const count = fresh ? 1 : (cur.count || 0) + 1;
      const stats = { count, windowStart: fresh ? serverTimestamp() : cur.windowStart, lastOrder: o.id, updatedAt: serverTimestamp() };
      if (count >= ns.max) stats.blockedAt = serverTimestamp(); else if (cur?.blockedAt) stats.blockedAt = cur.blockedAt;
      const wb = writeBatch(db);
      wb.update(doc(db, "orders", o.id), { status: "noshow", updatedAt: serverTimestamp() });
      wb.set(doc(db, "noshows", o.id), { orderId: o.id, customerUid: o.customerUid, storeId: M.uid, at: serverTimestamp() });
      wb.set(doc(db, "custStats", o.customerUid), stats);
      await wb.commit();
      custStats.delete(o.customerUid);
      toast(count >= ns.max ? `🚫 سجّلناه — العميل وصل ${count} طلبات ما استلمها وتوقف عن الطلب ${ns.blockDays} أيام` : "🚫 سجّلنا إن الطلب ما انستلم", true);
    } catch (e) { console.error(e); toast("تعذّر التسجيل — حدّث الصفحة وجرّب"); }
  };
  M.onOrders = draw; // تتحدّث لحظياً لو وصل طلب جديد
  draw();
  // تذكير «جاهز من X دقيقة» يتحدّث كل دقيقة والشاشة مفتوحة
  clearInterval(M.tick); M.tick = setInterval(() => { if (!ordersOpen || !visible()) { clearInterval(M.tick); return; } if (M.orders.some((o) => o.status === "ready")) draw(); }, 60000);
}
const etaText = (o) => { const t = X.ms(o.etaAt); if (!t || !["received"].includes(o.status)) return ""; const m = Math.round((t - Date.now()) / 60000);
  return m > 0 ? `⏱️ ${o.mode === "delivery" ? "يطلع للتوصيل" : "جاهز"} خلال ~${m} دقيقة` : `⏱️ ${o.mode === "delivery" ? "بيطلع" : "بيكون جاهز"} قريب`; };
function stepper(o) {
  if (["cancelled", "noshow"].includes(o.status)) return "";
  const steps = orderSteps(o);
  let at = Math.max(0, steps.findIndex(([k]) => k === o.status));
  if (o.mode === "curbside" && o.status === "ready" && o.arrivedAt) at = steps.findIndex(([k]) => k === "arrived");
  if (o.status === "done") at = steps.length - 1;
  return `<div class="ostep">${steps.map(([k, l], i) => `<div class="${i <= at ? "on" : ""}${i === at ? " cur" : ""}"><i></i><small>${l}</small></div>`).join("")}</div>`;
}
const minsSince = (t) => Math.floor((Date.now() - (X.ms(t) || 0)) / 60000);
function orderCard(o, asStore) {
  const st = ORDER_STATUS[o.status] || ORDER_STATUS.sent, when = X.ms(o.createdAt), del = o.mode === "delivery", curb = o.mode === "curbside";
  const live = ["sent", "received", "ready", "delivering"].includes(o.status);
  const ns = F("noshow") && !state.staff; // «ما انستلم» لصاحب المحل بس
  const next = asStore ? ({
    sent: [["received", "👍 استلمته"], ["cancelled", "✕ إلغاء"]],
    received: [[del ? "delivering" : "ready", del ? "🚚 طلع للتوصيل" : "🛍️ جاهز للاستلام"], ["cancelled", "✕ إلغاء"], ...(ns ? [["noshow", "🚫 ما انستلم"]] : [])],
    ready: [["done", curb ? "✅ سلّمته" : "✅ تم الاستلام"], ["cancelled", "✕ إلغاء"], ...(ns ? [["noshow", "🚫 ما انستلم"]] : [])],
    delivering: [["done", "✅ تم التوصيل"], ["cancelled", "✕ إلغاء"], ...(ns ? [["noshow", "🚫 ما انستلم"]] : [])],
  }[o.status] || []) : (o.status === "sent" ? [["cancelled", "✕ إلغاء الطلب"]] : []);
  const store = !asStore ? state.stores.find((x) => x.uid === o.storeId) : null;
  const wa = store ? X.waDigits(store.phone || store.branches?.[0]?.phone || "") : "";
  const rated = !asStore && myRatings.has(o.id);
  const nsc = asStore && ns && live ? noshowCount(custStats.get(o.customerUid), state.config) : 0;
  const readyMin = o.status === "ready" ? minsSince(o.readyAt || o.updatedAt) : 0;
  const custWa = asStore ? X.waDigits(o.customerPhone || "") : "";
  const pendingReq = o.cancelReq && !o.cancelReqAns && live;
  return `<div class="ocard s-${esc(o.status || "sent")}${["sent"].includes(o.status) && asStore ? " new" : ""}"><div class="oc-top"><b>#${esc(o.code)}</b><span class="pill ${st.cls}">${asStore ? st.t : statusText(o)}</span></div>
    <div class="oc-sub">${o.branchName ? `📍 ${esc(o.branchName)} • ` : ""}${asStore ? esc([o.customerName, X.toLocalPhone(o.customerPhone)].filter(Boolean).join(" • ")) : `🏪 ${esc(o.storeName)}`} • ${when ? new Date(when).toLocaleString(X.LOC, { dateStyle: "short", timeStyle: "short" }) : ""}</div>
    ${nsc ? `<div class="oc-warn">⚠️ هذا العميل عنده ${nsc === 1 ? "طلب" : nsc + " طلبات"} ما استلمها</div>` : ""}
    ${X.ms(o.scheduledAt) && live ? `<div class="oc-sched${asStore && !isAhead(o) && ["sent", "received"].includes(o.status) ? " due" : ""}">📅 ${asStore ? "الموعد" : "موعد طلبك"}: <b>${esc(X.preWhen(X.ms(o.scheduledAt)))}</b></div>` : ""}
    ${asStore && o.readyNote && o.status === "ready" ? `<div class="oc-note">📍 ${esc(o.readyNote)}</div>` : ""}
    ${stepper(o)}${F("eta") && etaText(o) ? `<div class="oc-eta">${etaText(o)}</div>` : ""}
    <div class="oc-items">${(o.items || []).map((i) => `${i.qty} × ${esc(i.name)}`).join("، ")}</div>
    <div class="oc-tot">${MODES[o.mode] || "🏃 استلام"} • <b>${fmt(o.total)} ر.س</b>${o.pay ? ` • ${PAY[o.pay]?.icon} ${PAY[o.pay]?.label}` : ""}${o.note ? ` • 📝 ${esc(o.note)}` : ""}</div>
    ${asStore && curb && (o.car || o.spot) ? `<div class="oc-tot">🚗 ${esc(o.car || "")}${o.spot ? ` • 📍 ${esc(o.spot)}` : ""}</div>` : ""}
    ${asStore && curb && o.arrivedAt && live ? `<div class="oc-arrived">🚗 العميل وصل عند المحل!${o.spot ? ` — ${esc(o.spot)}` : ""}</div>` : ""}
    ${!asStore && curb && live ? curbBox(o) : ""}
    ${pendingReq ? (asStore ? `<div class="oc-req">🙋 العميل يطلب الإلغاء: <b>${esc(o.cancelReq)}</b>
        <div class="oc-act"><button class="chip" data-creq="ok" data-oid="${esc(o.id)}">✅ وافق وألغِ</button><button class="chip" data-creq="no" data-oid="${esc(o.id)}">✕ ارفض (الطلب جاهز)</button></div></div>`
      : `<div class="oc-req">⏳ أرسلت طلب إلغاء (${esc(o.cancelReq)}) — بانتظار رد المحل</div>`) : ""}
    ${!asStore && o.cancelReqAns === "rejected" && live ? `<div class="oc-note">✕ المحل ما قدر يلغيه — الطلب جاهز أو تجهّز${wa ? ` • <a href="https://wa.me/${wa}" target="_blank" rel="noopener">💬 كلّم المحل</a>` : ""}</div>` : ""}
    ${o.status === "cancelled" && o.cancelReason ? `<div class="oc-note">${o.cancelBy === "store" ? "سبب إلغاء المحل" : "سبب الإلغاء"}: ${esc(o.cancelReason)}</div>` : ""}
    ${!asStore && o.status === "noshow" ? `<div class="oc-note">المحل سجّل إن الطلب ما انستلم. لو فيه خطأ تواصل مع المحل${wa ? ` — <a href="https://wa.me/${wa}" target="_blank" rel="noopener">💬 واتساب</a>` : ""}</div>` : ""}
    ${asStore && readyMin >= REMIND_MIN ? `<div class="oc-remind">⏰ جاهز من ${readyMin} دقيقة وما انستلم${custWa ? ` — <a href="https://wa.me/${custWa}?text=${encodeURIComponent(`هلا ${o.customerName || ""} 👋 طلبك #${o.code} من ${o.storeName} جاهز وينتظرك 🛍️`)}" target="_blank" rel="noopener">💬 ذكّره على واتساب</a>` : ""}</div>` : ""}
    ${!asStore && ["received", "ready", "delivering"].includes(o.status) && !o.cancelReq ? `<div class="oc-note">المحل استلم طلبك${wa ? ` — <a href="https://wa.me/${wa}" target="_blank" rel="noopener">💬 تواصل مع المحل</a>` : ""}</div>` : ""}
    ${asStore && o.mode === "delivery" && o.location ? `<a class="oc-map" href="https://maps.google.com/?q=${o.location.lat},${o.location.lng}" target="_blank" rel="noopener">📍 موقع العميل</a>` : ""}
    ${next.length || !asStore ? `<div class="oc-act">${next.map(([s, l]) => `<button class="chip${s === "noshow" ? " warn" : ""}" data-oid="${esc(o.id)}" data-os="${s}">${l}</button>`).join("")}
      ${!asStore && ["received", "ready", "delivering"].includes(o.status) && !o.cancelReq ? `<button class="chip" data-creqask="${esc(o.id)}">🙋 طلب إلغاء</button>` : ""}
      ${!asStore && o.status === "done" && F("ratings") ? (rated ? `<span class="pill">⭐ شكراً على تقييمك</span>` : `<button class="chip" data-rate="${esc(o.id)}">⭐ قيّم طلبك</button>`) : ""}
      ${!asStore ? `<button class="chip" data-again="${esc(o.id)}">↻ اطلب نفسه</button>` : ""}</div>` : ""}
  </div>`;
}
/** 🚗 خانة الاستلام من السيارة داخل بطاقة المتابعة (العميل) */
function curbBox(o) {
  const canArrive = ["received", "ready"].includes(o.status);
  if (o.arrivedAt) return `<div class="curb-box done"><b>🚗 بلّغنا المحل إنك وصلت — جايك 👍</b><small>${esc(o.car || "")}${o.spot ? ` • 📍 ${esc(o.spot)}` : ""}</small></div>`;
  return `<div class="curb-box${o.status === "ready" ? " hot" : ""}">
    <b>${o.status === "ready" ? "🚗 طلبك جاهز! أول ما توقف عند المحل اضغط «وصلت»" : o.status === "received" ? "🚗 المحل يجهّز طلبك — لو وصلت بدري اضغط «وصلت»" : "🚗 استلام من السيارة"}</b>
    <small>سيارتك: ${esc(o.car || "—")}${o.spot ? ` • 📍 ${esc(o.spot)}` : ""} <button class="link" data-curbedit="${esc(o.id)}">✎ عدّل</button></small>
    ${canArrive ? `<button class="btn dir" data-arrive="${esc(o.id)}">🚗 وصلت</button>` : ""}</div>`;
}

// ----- طلباتي (تتحدّث لحظياً + تنبيه لما تتغيّر الحالة) -----
const MY = { uid: null, unsub: null, list: [], loaded: false, open: false, err: "", at: 0, stats: null };
const myRatings = new Set();
export function watchMyOrders() {
  // المفتاح يشمل حالة الخدمة: لو الإعدادات وصلت متأخرة نبدأ المتابعة بعدها
  const uid = state.user?.uid && cat().enabled && cat().orderEnabled ? state.user.uid : null;
  if (MY.uid === uid) { renderTracker(); return; } // الإعداد تغيّر من الأدمن؟ نحدّث الشكل
  subscribeMy(uid);
}
function subscribeMy(uid) {
  MY.unsub?.(); MY.unsub = null; MY.uid = uid; MY.list = []; MY.loaded = false; myRatings.clear();
  renderTracker();
  if (!uid) return;
  getDocs(query(collection(db, "ratings"), where("uid", "==", uid))).then((r) => r.docs.forEach((d) => myRatings.add(d.id))).catch(() => {});
  MY.unsub = onSnapshot(myOrdersQuery(uid), (snap) => applyMyOrders(snap.docs), (e) => {
    console.warn(e); MY.err = e.code || e.message || "خطأ"; if (MY.open) drawMyOrders();
    // الاتصال المباشر انقطع: نعيد الاشتراك بعد شوي
    setTimeout(() => { if (MY.uid === uid) { const keep = MY.list; subscribeMy(uid); MY.list = keep; } }, 4000);
  });
}
const myOrdersQuery = (uid) => query(collection(db, "orders"), where("customerUid", "==", uid));
const sig = (o) => [o.readyNote || "", o.status, X.ms(o.etaAt) || "", X.ms(o.arrivedAt) || "", o.cancelReq || "", o.cancelReqAns || "", o.car || "", o.spot || ""].join("|");
function applyMyOrders(docs) {
  const prev = new Map(MY.list.map((o) => [o.id, sig(o)]));
  const prevStatus = new Map(MY.list.map((o) => [o.id, o.status]));
  MY.list = docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => X.ms(b.createdAt) - X.ms(a.createdAt));
  MY.at = Date.now();
  const changed = !MY.loaded || MY.list.length !== prev.size || MY.list.some((o) => prev.get(o.id) !== sig(o));
  if (MY.loaded) for (const o of MY.list) {
    const was = prevStatus.get(o.id);
    if (was && was !== o.status && o.status !== "sent") toast(`🧾 طلبك #${o.code}: ${statusText(o)}`, true);
    if (was && was !== o.status) {
      if (["ready", "delivering"].includes(o.status)) try { navigator.vibrate?.([180, 120, 180]); } catch {}
      if (!LIVE.includes(o.status)) TR.fin.set(o.id, Date.now());
    }
  }
  MY.loaded = true; MY.err = "";
  if (MY.open && changed) drawMyOrders(); else if (MY.open) stampMy();
  renderTracker();
}

// ----- 🧾 متابعة الطلب الحي: شريط أسفل الشاشة أو دائرة عائمة (إعداد الأدمن catalog.tracker) -----
const LIVE = ["sent", "received", "ready", "delivering"];
const TR = { fin: new Map(), timer: null };
const TR_LINGER = 8000; // المنتهي يبقى ظاهر ثواني عشان العميل يشوف النتيجة
const trMode = () => ["bar", "bubble", "off"].includes(cat().tracker) ? cat().tracker : "bar";
const trSteps = (o) => o.mode === "delivery" ? ["sent", "received", "delivering", "done"] : ["sent", "received", "ready", "done"];
function trProgress(o) {
  if (!LIVE.includes(o.status)) return 1;
  const st = trSteps(o), i = Math.max(0, st.indexOf(o.status));
  return (i + 1) / st.length;
}
const trEmoji = (o) => ({ sent: "📨", received: "👨‍🍳", ready: o.mode === "curbside" ? "🚗" : "🛍️", delivering: "🚚", done: "✅" })[o.status] || "✕";
const trShort = (o) => ({ sent: "بانتظار المحل", received: "يتجهّز", ready: o.mode === "curbside" && o.arrivedAt ? "المحل جايك" : "جاهز ✅", delivering: "بالطريق", done: "تم 👍", cancelled: "ملغي", noshow: "ما انستلم" })[o.status] || o.status;
const trReady = (o) => ["ready", "delivering"].includes(o.status);
function trShown() {
  const now = Date.now();
  for (const [id, at] of TR.fin) if (now - at > TR_LINGER) TR.fin.delete(id);
  return MY.list.filter((o) => LIVE.includes(o.status) || TR.fin.has(o.id))
    .sort((a, b) => (trReady(b) - trReady(a)) || (X.ms(b.createdAt) - X.ms(a.createdAt)));
}
function trPlace(el) {
  if (!el || el.dataset.mode !== "bar") return;
  const car = $("carousel"), onMap = state.tab === "map";
  const carH = onMap && car && car.offsetParent && car.children.length ? car.offsetHeight : 0;
  el.style.bottom = `calc(var(--nav) + ${carH + 8}px)`;
}
export function renderTracker() {
  let el = $("orderTracker");
  const list = trMode() === "off" || !MY.uid ? [] : trShown();
  if (!list.length) { if (el) el.remove(); clearInterval(TR.timer); TR.timer = null; return; }
  if (!el) { el = document.createElement("div"); el.id = "orderTracker"; document.body.appendChild(el); }
  const o = list[0], mode = trMode(), n = list.length, p = trProgress(o);
  const ready = trReady(o) ? " ready" : "";
  const canArrive = o.mode === "curbside" && !o.arrivedAt && ["received", "ready"].includes(o.status);
  el.dataset.mode = mode;
  el.className = "otr m-" + mode + ready;
  if (mode === "bubble") {
    const C = 2 * Math.PI * 27;
    el.innerHTML = `<button class="otr-bub" aria-label="طلبك: ${esc(statusText(o))}">
      <svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="32" r="27" class="trk"/><circle cx="32" cy="32" r="27" class="prg" stroke-dasharray="${C.toFixed(1)}" stroke-dashoffset="${(C * (1 - p)).toFixed(1)}"/></svg>
      <span class="e">${trEmoji(o)}</span><small>${esc(trShort(o))}</small></button>
      ${n > 1 ? `<span class="otr-n">${n}</span>` : ""}`;
  } else {
    el.innerHTML = `<div class="otr-bar" role="button" tabindex="0" aria-label="افتح طلباتي">
      <span class="otr-ic">${trEmoji(o)}</span>
      <span class="otr-tx"><small>${esc(o.storeName || "طلبك")}</small><b>${esc(statusText(o))}</b></span>
      ${n > 1 ? `<span class="otr-cnt">${n} طلبات</span>` : ""}
      ${canArrive ? `<button class="otr-go" data-trarrive="${esc(o.id)}">🚗 وصلت</button>` : `<span class="otr-chev">‹</span>`}
      <span class="otr-prog"><i style="width:${Math.round(p * 100)}%"></i></span></div>`;
  }
  el.querySelector(".otr-bar, .otr-bub").onclick = (e) => { if (e.target.closest("[data-trarrive]")) return; openMyOrders(); };
  const go = el.querySelector("[data-trarrive]");
  if (go) go.onclick = (e) => { e.stopPropagation(); markArrived(MY.list.find((y) => y.id === go.dataset.trarrive)); };
  trPlace(el);
  // نعيد التموضع (البطاقات تحت تتغير) ونخفي المنتهي بعد ثواني
  if (!TR.timer) TR.timer = setInterval(() => { if (TR.fin.size) renderTracker(); else trPlace($("orderTracker")); }, 1500);
}
window.addEventListener("resize", () => trPlace($("orderTracker")));
window.addEventListener("tabchange", () => trPlace($("orderTracker")));

/** 🚗 وصلت: ننبّه المحل + رسالة واتساب جاهزة (من «طلباتي» أو من شريط المتابعة) */
async function markArrived(o) {
  if (!o) return;
  const store = state.stores.find((y) => y.uid === o.storeId);
  const wa = store ? X.waDigits(store.phone || store.branches?.[0]?.phone || "") : "";
  const w = wa ? window.open("", "_blank") : null;
  try { await updateDoc(doc(db, "orders", o.id), { arrivedAt: serverTimestamp(), updatedAt: serverTimestamp() }); } catch (e) { console.warn(e); }
  if (w) w.location = `https://wa.me/${wa}?text=${encodeURIComponent(`وصلت عند المحل 🚗 ${o.car || ""}${o.spot ? " — " + o.spot : ""}\nطلب #${o.code}`)}`;
  toast("بلّغنا المحل إنك وصلت 🚗", true);
}
// الجوال يوقف الاتصال المباشر لما الصفحة تروح للخلفية (مثلاً فتح واتساب)، والمكتبة أحياناً ما ترجع تتصل لحالها:
// عند الرجوع نعيد الاتصال بالقوة ونسحب آخر حالة من السيرفر، وكل 10 ثواني والشاشة مفتوحة.
let hiddenAt = 0, reconnecting = false;
async function reconnect() {
  if (reconnecting) return; reconnecting = true;
  try { await disableNetwork(db); await enableNetwork(db); } catch {} finally { reconnecting = false; }
}
async function refreshMyOrders(force) {
  if (!MY.uid) return;
  if (force) await reconnect();
  try { const s = await getDocsFromServer(myOrdersQuery(MY.uid)); applyMyOrders(s.docs); }
  catch { if (!force) { await reconnect(); getDocsFromServer(myOrdersQuery(MY.uid)).then((s) => applyMyOrders(s.docs)).catch(() => {}); } }
}
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") { hiddenAt = Date.now(); return; }
  if (hiddenAt && Date.now() - hiddenAt > 5000) reconnect().then(() => refreshMyOrders());
  else refreshMyOrders();
});
window.addEventListener("online", () => reconnect().then(() => refreshMyOrders()));
let myPoll = null;
export function openMyOrders() {
  if (!state.user) return;
  watchMyOrders(); MY.open = true;
  loadMyStats();
  drawMyOrders(); refreshMyOrders();
  clearInterval(myPoll);
  myPoll = setInterval(() => { if (!MY.open) { clearInterval(myPoll); myPoll = null; return; } if (document.visibilityState === "visible") refreshMyOrders(); }, 10000);
}
function loadMyStats() {
  if (!state.user || !F("noshow")) return;
  getDoc(doc(db, "custStats", state.user.uid)).then((d) => { MY.stats = d.exists() ? d.data() : null; }).catch(() => {});
}
function stampMy() { const el = $("myStamp"); if (el) el.textContent = MY.at ? `آخر تحديث ${new Date(MY.at).toLocaleTimeString(X.LOC, { hour: "numeric", minute: "2-digit", second: "2-digit" })}` : ""; }
function drawMyOrders() {
  const who = state.user?.email || X.toLocalPhone(state.profile?.phone || "") || "";
  const list = MY.list;
  const scroll = $("sheet").classList.contains("hidden") ? 0 : $("sheetBody").scrollTop;
  showSheet(`<div class="pad my-orders"><div class="section-title">🧾 طلباتي</div>
    <div class="my-live"><span>● مباشر</span><small id="myStamp"></small><button class="link" id="myRefresh">↻ تحديث</button></div>
    ${MY.err ? `<div class="err">تعذّر تحميل طلباتك (${esc(MY.err)}). حدّث الصفحة وجرّب مرة ثانية.</div>` : ""}
    ${!MY.loaded && !MY.err ? `<div class="spinner"></div>` : list.length ? list.slice(0, 50).map((o) => orderCard(o, false)).join("") : MY.err ? "" : `<div class="empty-inline">ما طلبت شي للحين<br><small>ادخل صفحة أي متجر واطلب منه 🛍️</small></div>`}
    ${who ? `<small class="hint">الطلبات المحفوظة لحسابك: ${esc(who)} — الطلبات من حساب ثاني ما تطلع هنا.</small>` : ""}</div>`);
  $("sheetBody").scrollTop = scroll;
  stampMy();
  const b = $("sheetBody");
  $("myRefresh").onclick = () => { $("myRefresh").textContent = "…"; refreshMyOrders(true).finally(() => { const r = $("myRefresh"); if (r) r.textContent = "↻ تحديث"; }); };
  b.querySelectorAll("[data-os]").forEach((x) => x.onclick = async () => {
    if (!confirm("إلغاء الطلب؟")) return;
    const mo = MY.list.find((y) => y.id === x.dataset.oid) || { id: x.dataset.oid };
    try { await updOrder(mo, { status: "cancelled", cancelBy: "customer", updatedAt: serverTimestamp() }); } catch { toast("ما تقدر تلغيه — المحل استلم طلبك 👍 تقدر ترسل «طلب إلغاء»"); refreshMyOrders(true); }
  });
  b.querySelectorAll("[data-creqask]").forEach((x) => x.onclick = () => askCancel(list.find((y) => y.id === x.dataset.creqask)));
  b.querySelectorAll("[data-curbedit]").forEach((x) => x.onclick = () => editCurb(list.find((y) => y.id === x.dataset.curbedit)));
  b.querySelectorAll("[data-again]").forEach((x) => x.onclick = () => {
    const o = list.find((y) => y.id === x.dataset.again), cart = cartOf(o.storeId);
    (o.items || []).forEach((i) => { cart[i.id] = { ...i }; }); saveCarts();
    MY.open = false; hideSheet(); setTimeout(() => openStore(o.storeId, true), 60);
  });
  b.querySelectorAll("[data-rate]").forEach((x) => x.onclick = () => rateOrder(list.find((y) => y.id === x.dataset.rate)));
  // 🚗 وصلت: ننبّه المحل في لكوفر + رسالة واتساب جاهزة
  b.querySelectorAll("[data-arrive]").forEach((x) => x.onclick = () => markArrived(list.find((y) => y.id === x.dataset.arrive)));
}
/** 🙋 طلب إلغاء (بعد ما يستلمه المحل) */
function askCancel(o) {
  MY.open = false; let reason = "";
  const d = () => {
    showSheet(`<div class="pad form"><div class="section-title">🙋 طلب إلغاء #${esc(o.code)}</div>
      <p class="desc tr">المحل استلم طلبك ويمكن بدأ يجهّزه، فالإلغاء يحتاج موافقته. وش السبب؟</p>
      <div class="chips wrap">${CUST_CANCEL_REASONS.map((r) => `<button class="chip${reason === r ? " on" : ""}" data-r="${esc(r)}">${esc(r)}</button>`).join("")}</div>
      <input id="caTxt" class="input" maxlength="200" placeholder="أو اكتب السبب" value="${CUST_CANCEL_REASONS.includes(reason) ? "" : esc(reason)}">
      <button class="btn danger big" id="caGo">أرسل طلب الإلغاء</button><button class="btn ghost" id="caBack">‹ رجوع</button></div>`);
    $("sheetBody").querySelectorAll("[data-r]").forEach((x) => x.onclick = () => { reason = x.dataset.r; d(); });
    $("caTxt").oninput = (e) => { reason = e.target.value; };
    $("caBack").onclick = () => { MY.open = true; drawMyOrders(); };
    $("caGo").onclick = async () => {
      if (!reason.trim()) return toast("اختر السبب");
      try { await updateDoc(doc(db, "orders", o.id), { cancelReq: reason.trim().slice(0, 200), cancelReqAt: serverTimestamp(), updatedAt: serverTimestamp() }); toast("انرسل طلب الإلغاء للمحل ⏳", true); }
      catch (e) { console.warn(e); toast("تعذّر الإرسال — يمكن الطلب انتهى"); }
      MY.open = true; drawMyOrders(); refreshMyOrders();
    };
  };
  d();
}
/** ✎ وصف السيارة + وين واقف */
function editCurb(o) {
  MY.open = false;
  showSheet(`<div class="pad form"><div class="section-title">🚗 سيارتك</div>
    <label class="label">وصف السيارة</label><input id="cbCar" class="input" maxlength="60" value="${esc(o.car || "")}" placeholder="مثال: كامري بيضاء">
    <label class="label">وين واقف؟ (اختياري)</label><input id="cbSpot" class="input" maxlength="60" value="${esc(o.spot || "")}" placeholder="مثال: عند الباب الجانبي">
    <button class="btn dir big" id="cbGo">حفظ</button><button class="btn ghost" id="cbBack">‹ رجوع</button></div>`);
  $("cbBack").onclick = () => { MY.open = true; drawMyOrders(); };
  $("cbGo").onclick = async () => {
    const car = $("cbCar").value.trim().slice(0, 60), spot = $("cbSpot").value.trim().slice(0, 60);
    try { await updateDoc(doc(db, "orders", o.id), { car, spot, updatedAt: serverTimestamp() }); try { localStorage.setItem("hayyna_car", car); } catch {} toast("انحفظ 👍", true); }
    catch (e) { console.warn(e); toast("تعذّر الحفظ"); }
    MY.open = true; drawMyOrders(); refreshMyOrders();
  };
}
export const myOrdersClosed = () => { MY.open = false; if (!M.ordersTarget) ordersOpen = false; }; // شاشة الموظف تبقى شغّالة

function rateOrder(o) {
  let stars = 5; MY.open = false;
  const draw = () => {
    showSheet(`<div class="pad"><div class="section-title">⭐ قيّم طلبك من ${esc(o.storeName)}</div>
      <div class="stars">${[1, 2, 3, 4, 5].map((n) => `<button data-st="${n}" class="${n <= stars ? "on" : ""}">★</button>`).join("")}</div>
      <textarea id="rtText" class="input" rows="3" maxlength="300" placeholder="كلمة عن تجربتك (اختياري)"></textarea>
      <div id="rtErr" class="err"></div><button class="btn dir big" id="rtGo">أرسل التقييم</button></div>`);
    $("sheetBody").querySelectorAll("[data-st]").forEach((x) => x.onclick = () => { const t = $("rtText").value; stars = +x.dataset.st; draw(); $("rtText").value = t; });
    $("rtGo").onclick = async () => {
      $("rtGo").disabled = true;
      try {
        await setDoc(doc(db, "ratings", o.id), { orderId: o.id, storeId: o.storeId, uid: state.user.uid, name: (state.profile?.name || o.customerName || "").split(" ")[0].slice(0, 30),
          stars, text: $("rtText").value.trim().slice(0, 300), at: serverTimestamp() });
        myRatings.add(o.id); toast("شكراً على تقييمك 🌟", true); MY.open = true; drawMyOrders();
      } catch (e) { console.error(e); $("rtGo").disabled = false; $("rtErr").textContent = "تعذّر الإرسال."; }
    };
  };
  draw();
}

/** ملخص التقييمات في صفحة المتجر */
export async function renderStoreRatings(uid) {
  const box = $("storeRatings"); if (!box || !F("ratings")) return;
  let list = [];
  try { list = (await getDocs(query(collection(db, "ratings"), where("storeId", "==", uid)))).docs.map((d) => d.data()).sort((a, b) => X.ms(b.at) - X.ms(a.at)); } catch { return; }
  if (!list.length || !$("storeRatings")) return;
  const avg = list.reduce((a, r) => a + (r.stars || 0), 0) / list.length;
  box.innerHTML = `<div class="rating-sum"><b>⭐ ${avg.toFixed(1)}</b> <small>(${list.length} ${list.length === 1 ? "تقييم" : "تقييمات"})</small></div>
    ${list.filter((r) => r.text).slice(0, 3).map((r) => `<div class="rv"><span>${"★".repeat(r.stars)}${"☆".repeat(5 - r.stars)}</span> <b>${esc(r.name || "زبون")}</b><p>${esc(r.text)}</p></div>`).join("")}`;
}

// =================== الزبون: صفحة المتجر + السلة ===================
const carts = {}; // storeId -> { itemId: {id,name,kind,price,qty} }
try { Object.assign(carts, JSON.parse(localStorage.getItem("hayyna_carts") || "{}")); } catch {}
const saveCarts = () => { try { localStorage.setItem("hayyna_carts", JSON.stringify(carts)); } catch {} };
const cartOf = (sid) => (carts[sid] = carts[sid] || {});
const cartCount = (sid) => Object.values(cartOf(sid)).reduce((a, l) => a + l.qty, 0);
const totalsOf = (sid) => cartTotals(Object.values(cartOf(sid)), storeCache[sid]?.D);
const cartSum = (sid) => totalsOf(sid).sub;
const storeCache = {};
/** عروض المتجر الشغّالة (من العروض المحمّلة + الخصم السريع) */
async function loadDeals(sid, items) {
  let quick = null;
  if (feat(state.config, "quickDeal")) { try { const q = await getDoc(doc(db, "quickDeals", sid)); quick = q.exists() ? q.data() : null; } catch {} }
  return storeDeals(state.config, (state.offers || []).filter((o) => o.merchantId === sid), quick, items);
}
/** نحدّث أسعار السلة حسب المنيو والعروض (ونشيل اللي ما عاد موجود) */
function syncCart(sid) {
  const { items, D } = storeCache[sid], cart = cartOf(sid);
  Object.keys(cart).forEach((id) => {
    const pk = D.packs.find((x) => x.id === id);
    if (pk && pk.available === false) { delete cart[id]; return; } // نفد اليوم
    if (pk) { Object.assign(cart[id], { name: pk.name, price: pk.price, was: pk.was || 0 }); return; }
    const it = items.find((x) => x.id === id && x.available !== false);
    if (!it) { delete cart[id]; return; }
    const dp = dealPrice(it, D); Object.assign(cart[id], { price: dp.price, was: dp.was });
  });
}

/** يرسم منتجات المتجر داخل صفحة المتجر (عنصر #storeItems) */
export async function renderStoreItems(s) {
  const c = cat(), box = $("storeItems");
  if (!box || !c.enabled) return;
  let items = [], passes = [];
  try {
    [items, , passes] = await Promise.all([
      getDocs(query(collection(db, "items"), where("merchantId", "==", s.uid), where("approved", "==", true))).then((r) => r.docs.map((d) => ({ ...d.data(), id: d.id }))),
      c.orderEnabled ? fetchStoreStatus(s.uid).then(() => []) : [],
      c.pricing.enabled ? getDocs(query(collection(db, "itemPasses"), where("merchantId", "==", s.uid))).then((r) => r.docs.map((d) => d.data())) : [],
    ]);
  } catch (e) { console.warn("items", e); return; }
  const now = Date.now();
  items = items.filter((it) => itemVisible(it, passes, c, now));
  const D = await loadDeals(s.uid, items);
  storeCache[s.uid] = { s, items, D };
  if (!$("storeItems")) return;
  syncCart(s.uid); saveCarts();
  drawStoreItems(s.uid);
}
function drawStoreItems(sid) {
  const box = $("storeItems"), data = storeCache[sid];
  if (!box || !data) return;
  const { s, items, D } = data, c = cat(), base = baseKindOf(state.config, s.category), cart = cartOf(sid);
  const blk = c.orderEnabled ? orderBlock(s, sid) : "", pre = !!blk && !blk.startsWith("⏸️") && preOn(sid);
  const order = c.orderEnabled && (!blk || pre), packs = c.orderEnabled ? D.packs : [];
  if (!items.length && !packs.length) { box.innerHTML = ""; return; }
  const qtyBtns = (id, q) => !order ? "" : q ? `<div class="qty"><button data-dec="${esc(id)}">−</button><b>${q}</b><button data-inc="${esc(id)}">＋</button></div>` : `<button class="add-btn" data-inc="${esc(id)}">＋ أضف</button>`;
  box.innerHTML = (blk ? `<div class="closed-bar">${esc(blk)}${pre ? `<small>📅 تقدر تطلب الحين بموعد لاحق</small>` : ""}</div>` : "") + (D.banners.length ? `<div class="deal-bar">${D.banners.map((b) => `<span class="deal-chip${b.quick ? " quick" : ""}">${b.icon} ${esc(b.t)}</span>`).join("")}</div>` : "")
    + (packs.length ? `<div class="kind-sec deal-sec"><div class="ks-title">🎁 عروض تقدر تطلبها <small>(${packs.length})</small></div>${packs.map((p) => `<div class="icard deal">
        ${p.imageUrl ? `<img src="${esc(cld(p.imageUrl, 240))}" data-full="${esc(cld(p.imageUrl, 1400))}" alt="">` : `<div class="iph">🎁</div>`}
        <div class="ic-main"><b>${esc(p.name)}</b>${p.desc ? `<small>${esc(p.desc)}</small>` : ""}<div class="ic-price">${fmt(p.price)} ر.س${p.was ? ` <s>${fmt(p.was)}</s>` : ""}</div></div>
        ${p.available === false ? `<div class="sold-col"><span class="pill closed">❌ نفد اليوم</span>${restockBtn(p.offerId)}</div>` : qtyBtns(p.id, cart[p.id]?.qty || 0)}</div>`).join("")}</div>` : "")
    + kindOrder(base).map((k) => {
    const list = items.filter((x) => x.kind === k);
    if (!list.length) return "";
    const K = KINDS[k], secs = [...new Set([...list].sort((a, b) => a.slot - b.slot).map((x) => x.section || ""))]; // ترتيب الأقسام حسب ترتيب إضافة التاجر
    return `<div class="kind-sec" style="--kc:${K.color};--kb:${K.bg}">
      <div class="ks-title">${K.icon} ${K.title} <small>(${list.length})</small></div>
      ${secs.map((sec) => `${sec ? `<div class="ks-sub">${esc(sec)}</div>` : ""}${list.filter((x) => (x.section || "") === sec).sort((a, b) => a.slot - b.slot).map((it) => {
        const q = cart[it.id]?.qty || 0, out = it.available === false;
        return `<div class="icard${out ? " out" : ""}">
          ${it.imageUrl ? `<img src="${esc(cld(it.imageUrl, 240))}" data-full="${esc(cld(it.imageUrl, 1400))}" alt="">` : `<div class="iph">${K.icon}</div>`}
          <div class="ic-main"><b>${esc(it.name)}</b>${it.desc ? `<small>${esc(it.desc)}</small>` : ""}<div class="ic-price">${(() => { const dp = dealPrice(it, D), b = D.bogo[it.id];
            return `${fmt(dp.price)} ر.س${dp.pct ? ` <s>${fmt(dp.was)}</s> <span class="deal-pct">−${dp.pct}%</span>` : ""}${b ? ` <span class="deal-pct bogo">${esc(b.label)}</span>` : ""}`; })()}</div></div>
          ${out ? `<span class="pill closed">نفد</span>` : order ? qtyBtns(it.id, q) : ""}
        </div>`; }).join("")}`).join("")}
    </div>`;
  }).join("") + (order && cartCount(sid) ? (() => { const di = deliveryInfo(s, cartSum(sid));
    return `<div class="cart-bar"><span>🧾 سلتك: <b>${cartCount(sid)}</b> • <b>${fmt(cartSum(sid))} ر.س</b>${!F("freeHint") ? "" : di?.left > 0 ? `<small>🚚 باقي ${fmt(di.left)} ر.س للتوصيل المجاني</small>` : di?.free && di.freeOver > 0 ? `<small>🚚 التوصيل صار مجاني 🎉</small>` : ""}</span><button class="btn dir" id="cartGo">${pre ? "📅 اطلب مسبقاً" : "اطلب"}</button></div>`; })() : "");
  box.querySelectorAll("[data-inc]").forEach((b) => b.onclick = () => { addToCart(sid, b.dataset.inc); drawStoreItems(sid); });
  box.querySelectorAll("[data-dec]").forEach((b) => b.onclick = () => { const l = cart[b.dataset.dec]; if (l) { l.qty--; if (l.qty <= 0) delete cart[b.dataset.dec]; } saveCarts(); drawStoreItems(sid); });
  box.querySelectorAll("img[data-full]").forEach((im) => im.onclick = () => { $("viewerImg").src = im.dataset.full; $("viewer").classList.remove("hidden"); });
  $("cartGo")?.addEventListener("click", () => openCheckout(sid));
  bindRestock(box, () => drawStoreItems(sid));
}

function addToCart(sid, id, n = 1) {
  const { items, D } = storeCache[sid], cart = cartOf(sid);
  const pk = D.packs.find((x) => x.id === id), it = pk ? null : items.find((x) => x.id === id);
  if (!pk && !it) return;
  if (pk && pk.available === false) return toast("❌ هذا العرض نفد اليوم");
  const dp = it ? dealPrice(it, D) : { price: pk.price, was: pk.was || 0 };
  const l = cart[id] || (cart[id] = { id, name: (pk || it).name, kind: pk ? "deal" : it.kind, price: dp.price, was: dp.was, qty: 0 });
  Object.assign(l, { price: dp.price, was: dp.was }); l.qty = Math.min(99, l.qty + n); saveCarts();
}
/** 🛒 اطلب من شاشة العرض: لو المتجر عنده منيو مفتوح نضيفه للسلة ونروح للمتجر، وإلا نطلبه مباشرة */
// ---------------- 🔔 ذكّرني لما يتوفر (العرض نفد اليوم) ----------------
const RS_KEY = "hayyna_restock";
export const restockList = () => { try { return JSON.parse(localStorage.getItem(RS_KEY) || "[]"); } catch { return []; } };
const saveRs = (l) => { try { localStorage.setItem(RS_KEY, JSON.stringify(l.slice(-50))); } catch {} };
export const restockBtn = (offerId) => restockList().some((x) => x.id === offerId)
  ? `<button class="chip on" data-rs="${esc(offerId)}">🔔 بنذكّرك ✓</button>` : `<button class="chip" data-rs="${esc(offerId)}">🔔 ذكّرني لما يتوفر</button>`;
export function bindRestock(root, redraw) { root?.querySelectorAll("[data-rs]").forEach((b) => b.onclick = () => toggleRestock(b.dataset.rs, redraw)); }
export function toggleRestock(id, redraw) {
  const l = restockList(), i = l.findIndex((x) => x.id === id);
  if (i >= 0) { l.splice(i, 1); saveRs(l); toast("لغينا التذكير"); }
  else {
    const o = state.offers.find((x) => x.id === id);
    l.push({ id, t: o?.offerTitle || "", at: Date.now() }); saveRs(l);
    toast("🔔 بنذكّرك أول ما يرجع متوفر", true);
    updateDoc(doc(db, "offers", id), { notifyCount: increment(1) }).catch(() => {}); // المحل يشوف كم واحد ينتظر
    try { if ("Notification" in window && Notification.permission === "default") Notification.requestPermission().catch(() => {}); } catch {}
  }
  redraw?.();
}
/** نتأكد لما تتحدّث العروض: لو رجع متوفر ننبّه ونشيله من القائمة */
export function checkRestock() {
  const l = restockList(); if (!l.length || !state.loaded) return;
  const now = Date.now(), keep = [];
  for (const r of l) {
    const o = state.offers.find((x) => x.id === r.id);
    if (!o || X.isExpired(o, now)) continue;
    if (X.isSoldOut(o, now)) { keep.push(r); continue; }
    toast(`🔔 «${o.offerTitle}» رجع متوفر — اطلبه الحين`, true); navigator.vibrate?.([80, 60, 80]);
    try { if ("Notification" in window && Notification.permission === "granted") new Notification("لكوفر 🔔", { body: `«${o.offerTitle}» رجع متوفر`, icon: "/img/icon-192.png" }); } catch {}
  }
  if (keep.length !== l.length) saveRs(keep);
}

export async function orderFromOffer(o) {
  if (X.isSoldOut(o)) return toast("❌ هذا العرض نفد اليوم");
  const sid = o.merchantId, s = state.stores.find((x) => x.uid === sid), d = o.deal || {};
  if (!s) return toast("صفحة المتجر مو جاهزة");
  await fetchStoreStatus(sid);
  const blk = orderBlock(s, sid);
  if (blk && (blk.startsWith("⏸️") || !preOn(sid))) return toast(blk, true);
  const c = cat();
  let items = [];
  if (c.enabled) {
    try {
      const [its, passes] = await Promise.all([
        getDocs(query(collection(db, "items"), where("merchantId", "==", sid), where("approved", "==", true))).then((r) => r.docs.map((x) => ({ ...x.data(), id: x.id }))),
        c.pricing.enabled ? getDocs(query(collection(db, "itemPasses"), where("merchantId", "==", sid))).then((r) => r.docs.map((x) => x.data())) : [],
      ]);
      items = its.filter((it) => itemVisible(it, passes, c, Date.now()));
    } catch (e) { console.warn(e); }
  }
  const D = await loadDeals(sid, items);
  storeCache[sid] = { s, items, D };
  const menuOpen = items.some((x) => x.available !== false);
  if (d.type === "discount") return openStore(sid, true);
  if (d.type === "bogo" && d.itemId) { if (items.some((x) => x.id === d.itemId)) addToCart(sid, d.itemId, 2); return openStore(sid, true); }
  const pk = D.packs.find((x) => x.offerId === o.id);
  if (!pk) return openStore(sid, true);
  if (menuOpen) { addToCart(sid, pk.id); toast("انضاف العرض لسلتك 🛒 كمّل من صفحة المتجر", true); return openStore(sid, true); }
  // المتجر ما عنده منيو: طلب مباشر للعرض بس
  carts[sid] = {}; addToCart(sid, pk.id); openCheckout(sid);
}
export const offerOrderable = (o) => {
  const d = o?.deal; if (!d || X.isSoldOut(o) || !ordersOn() || !DEAL_TYPES[d.type] || !feat(state.config, DEAL_TYPES[d.type].feat)) return null;
  if (d.type === "discount") return "🛍️ تسوّق بالخصم من المنيو";
  if (d.type === "bogo") return "🛒 اطلب العرض (1+1)";
  if ((d.type === "bundle" || (d.type === "promo" && d.orderable)) && o.price > 0) return "🛒 اطلب العرض";
  return null;
};

// ⏸️🌙 هل المحل يستقبل طلبات الحين؟ (إيقاف يدوي من المحل/الموظف، أو خارج الدوام)
const pauseCache = {}, preCache = {}, pauseBr = {};
/** ⏸️ موقف؟ (المحل كله، أو هذا الفرع) */
export const isPaused = (sid, branchId) => !!pauseCache[sid] || (branchId != null && !!pauseBr[sid]?.has(branchId));
const setPause = (sid, d) => { const x = d?.exists?.() ? d.data() : null; pauseCache[sid] = x?.paused === true; pauseBr[sid] = new Set(x?.pausedBranches || []); };
/** 📅 الطلب المسبق مفعّل لهالمحل؟ */
export const preOn = (sid) => { const c = X.preCfg(preCache[sid]); return F("preorder") && cat().orderEnabled && c.on && (c.pickup || c.delivery); };
async function loadPre(sid) {
  if (!F("preorder")) return;
  try { const d = await getDocFromServer(doc(db, "preorderCfg", sid)); preCache[sid] = d.exists() ? d.data() : null; }
  catch { try { const d = await getDoc(doc(db, "preorderCfg", sid)); preCache[sid] = d.exists() ? d.data() : null; } catch {} }
}
export async function fetchStoreStatus(sid) {
  loadPre(sid).catch(() => {});
  try { setPause(sid, await getDocFromServer(doc(db, "storeStatus", sid))); }
  catch { try { setPause(sid, await getDoc(doc(db, "storeStatus", sid))); } catch {} }
  await loadPre(sid);
  return !!pauseCache[sid];
}
/** إلغاء طلب محجوز له فترة توصيل: نفك الحجز في نفس العملية */
async function updOrder(o, patch) {
  if (patch.status === "cancelled" && o.slotKey && o.status !== "cancelled") {
    try {
      const id = `${o.storeId}_${o.slotKey}`, sd = await getDocFromServer(doc(db, "slotCounts", id)), n = sd.exists() ? (sd.data().n | 0) : 0;
      if (n > 0) {
        const b = writeBatch(db);
        b.update(doc(db, "orders", o.id), patch);
        b.set(doc(db, "slotCounts", id), { storeId: o.storeId, slotKey: o.slotKey, n: n - 1, last: o.id });
        return await b.commit();
      }
    } catch (e) { console.warn("slot release", e); }
  }
  return updateDoc(doc(db, "orders", o.id), patch);
}
// branchId محدد = نفحص هذا الفرع؛ بدونه = المحل مغلق لو كل فروعه مغلقة. الفرع اللي ما حدد دوامه = مفتوح.
export function orderBlock(s, sid, branchId) {
  if (pauseCache[sid]) return "⏸️ المحل موقف استقبال الطلبات مؤقتاً — جرّب بعد شوي";
  const bs = (s.branches || []).filter(Boolean);
  if (!bs.length) return "";
  if (branchId != null && pauseBr[sid]?.has(branchId)) return "⏸️ هذا الفرع موقف الطلبات مؤقتاً — جرّب فرع ثاني";
  if (branchId == null && bs.every((b) => pauseBr[sid]?.has(b.id))) return "⏸️ المحل موقف استقبال الطلبات مؤقتاً — جرّب بعد شوي";
  const pick = branchId != null ? [bs.find((x) => x.id === branchId) || bs[0]] : bs;
  if (pick.some((b) => X.openNow(b) !== false)) return "";
  const b = pick[0];
  return `🌙 ${branchId != null && bs.length > 1 ? "هذا الفرع" : "المحل"} مغلق الحين — يفتح ${X.fmtMinute(b.openMinute)}${branchId != null && bs.length > 1 ? " (جرّب فرع ثاني)" : ""}`;
}

function openCheckout(sid) {
  const { s } = storeCache[sid], cart = cartOf(sid), vat = vatOf(state.config);
  const branches = s.branches || [];
  const pays = payMethods(s), curb = s.curbside && F("curbside");
  let savedCar = ""; try { savedCar = localStorage.getItem("hayyna_car") || ""; } catch {}
  let myStats = null;
  if (state.user && F("noshow")) getDoc(doc(db, "custStats", state.user.uid)).then((d) => { myStats = d.exists() ? d.data() : null; if (myStats && blockedUntil(myStats, state.config) && $("coSend")) draw(); }).catch(() => {});
  fetchStoreStatus(sid).then(() => { if ($("coSend")) { keep?.(); draw(); } });
  // 📅 كم طلب في كل فترة توصيل (عشان نبيّن الممتلئة)
  const slotN = new Map();
  const loadSlots = () => getDocs(query(collection(db, "slotCounts"), where("storeId", "==", sid)))
    .then((r) => { slotN.clear(); r.docs.forEach((d) => slotN.set(d.data().slotKey, d.data().n | 0)); if ($("coSend")) { keep?.(); draw(); } }).catch(() => {});
  if (F("preorder")) loadSlots();
  let keep = null;
  // 📍 الفرع: اللي اختاره الزبون من الخريطة/العرض، وإلا أقرب فرع مفتوح، وإلا أول فرع
  const ref = state.me || state.browse;
  const byDist = [...branches].sort((a, b) => ref ? X.km(ref, { lat: a.latitude, lng: a.longitude }) - X.km(ref, { lat: b.latitude, lng: b.longitude }) : 0);
  const picked = state.branchPick?.sid === sid ? branches.find((b) => b.id === state.branchPick.id) : null;
  const firstBr = picked || byDist.find((b) => X.openNow(b) !== false) || byDist[0];
  let pickOpen = false;
  const f = { mode: "pickup", pay: pays[0], car: savedCar, branch: firstBr?.id || "", name: state.profile?.name || state.user?.displayName || "", phone: X.toLocalPhone(state.profile?.phone || ""), note: "", loc: state.me || null, when: "now", day: 0, slot: 0 };
  const draw = () => {
    if (!Object.values(cart).length) { openStore(sid, false); return; }
    const T = cartTotals(Object.values(cart), storeCache[sid].D), lines = T.lines, sub = T.sub;
    const di = deliveryInfo(s, sub);
    const fee = f.mode === "delivery" && di ? di.fee : 0;
    const total = Math.round((sub + fee) * 100) / 100, vatNo = vat.enabled && validVatNumber(s.vatNumber) ? s.vatNumber : "";
    const { vat: vatAmt, net } = vatSplit(total, vat.rate);
    const minOk = f.mode !== "delivery" || !(s.deliveryMinOrder > 0) || sub >= s.deliveryMinOrder;
    const blk = orderBlock(s, sid, f.branch), paused = isPaused(sid, f.branch);
    // 📅 اطلب مسبقاً
    const pre = !paused && preOn(sid), pc = X.preCfg(preCache[sid]), closedNow = !!blk && !paused;
    if (closedNow && pre) f.when = "later";
    if (!pre) f.when = "now";
    const later = f.when === "later", br0 = branches.find((x) => x.id === f.branch) || branches[0];
    const modeOkLater = f.mode === "delivery" ? pc.delivery : pc.pickup;
    const days = later ? X.preDays(pc) : [];
    if (later && !days.includes(f.day)) f.day = days.find((d) => X.preTimes(pc, br0, f.mode, d).length) ?? days[0];
    const bKey = (k) => branches.length > 1 && f.branch ? `${k}_${f.branch}` : k; // فترات التوصيل لكل فرع
    const isFull = (x) => f.mode === "delivery" && (slotN.get(bKey(x.key)) || 0) >= pc.slotCap;
    const times = later && modeOkLater ? X.preTimes(pc, br0, f.mode, f.day) : [];
    if (!times.some((x) => x.t === f.slot && !isFull(x))) f.slot = 0;
    const blocked = paused || (closedNow && !later);
    const brName = (b) => b?.name || X.districtLabel(b?.district) || "الفرع";
    const brMeta = (b) => [state.me ? X.distText(X.km(state.me, { lat: b.latitude, lng: b.longitude })) + " منك" : "", X.openNow(b) === true ? "مفتوح" : X.openNow(b) === false ? "مغلق" : ""].filter(Boolean).join(" • ");
    showSheet(`<div class="pad form checkout"><div class="section-title">🧾 طلبك من ${esc(s.name)}</div>
      ${br0 ? `<div class="co-branch"><div class="cb-main"><small>📍 طلبك من</small><b>فرع ${esc(brName(br0))}</b>${brMeta(br0) ? `<small>${esc(brMeta(br0))}</small>` : ""}</div>
        ${branches.length > 1 ? `<button class="chip" id="coBrChange">${pickOpen ? "إغلاق" : "تغيير"}</button>` : ""}</div>
        ${pickOpen ? `<div class="co-brlist">${byDist.map((b) => `<button class="co-bropt${b.id === f.branch ? " on" : ""}" data-br="${esc(b.id)}"><b>فرع ${esc(brName(b))}</b><small>${esc(brMeta(b))}</small></button>`).join("")}</div>` : ""}` : ""}
      ${lines.map((l) => `<div class="co-line"><span>${esc(l.name)}${l.was > l.price ? ` <s>${fmt(l.was)}</s>` : ""}${l.bogo ? `<small class="deal-pct bogo">${esc(l.bogo)}${l.off > 0 ? ` −${fmt(l.off)}` : " (أضف واحد ثاني)"}</small>` : ""}</span><div class="qty"><button data-dec="${esc(l.id)}">−</button><b>${l.qty}</b><button data-inc="${esc(l.id)}">＋</button></div><b>${fmt(l.qty * l.price - l.off)}</b></div>`).join("")}
      ${T.saved > 0 ? `<div class="saved-line">🔥 وفّرت <b>${fmt(T.saved)} ر.س</b> بالعروض</div>` : ""}
      <label class="label">طريقة الاستلام</label>
      ${s.delivery || curb ? `<div class="modes">
        <button class="mode${f.mode === "pickup" ? " on" : ""}" data-mode="pickup"><b>🏃 استلام من المحل</b></button>
        ${curb ? `<button class="mode${f.mode === "curbside" ? " on" : ""}" data-mode="curbside"><b>🚗 استلام من السيارة</b><small>المحل يطلّع طلبك لسيارتك</small></button>` : ""}
        ${di ? `<button class="mode${f.mode === "delivery" ? " on" : ""}" data-mode="delivery"><b>${esc(di.label)}</b>${s.deliveryMinOrder > 0 ? `<small>أقل طلب ${fmt(s.deliveryMinOrder)} ر.س${sub < s.deliveryMinOrder ? ` — باقي ${fmt(s.deliveryMinOrder - sub)} ر.س عشان يصير متاح` : ""}</small>` : ""}</button>` : ""}
      </div>` : `<div class="hint-box">🏃 الطلب استلام من المحل — المحل ما يوفر توصيل</div>`}
      ${pre ? `<label class="label">⏰ متى تبي طلبك؟</label>
        <div class="chips"><button class="chip${!later ? " on" : ""}" data-when="now" ${closedNow ? "disabled" : ""}>⚡ الحين${closedNow ? " (مغلق)" : ""}</button><button class="chip${later ? " on" : ""}" data-when="later">📅 وقت لاحق</button></div>
        ${!later ? "" : !modeOkLater ? `<div class="hint-box">${f.mode === "delivery" ? "المحل ما يوفر توصيل بموعد — اختر الاستلام" : "المحل ما يوفر استلام بموعد — اختر التوصيل"}</div>` : `
          ${days.length > 1 ? `<div class="chips">${days.map((d) => `<button class="chip${d === f.day ? " on" : ""}" data-day="${d}">${X.dayLabel(d)}</button>`).join("")}</div>` : ""}
          ${times.length ? `<div class="slots">${times.map((x) => { const full = isFull(x);
            return `<button class="slot${x.t === f.slot ? " on" : ""}" data-slot="${x.t}" ${full ? "disabled" : ""}>${X.timeOf(x.t)}${x.end ? ` – ${X.timeOf(x.end)}` : ""}${full ? "<small>ممتلئة</small>" : ""}</button>`; }).join("")}</div>`
            : `<div class="hint-box">ما فيه أوقات متاحة ${esc(X.dayLabel(f.day))}${days.length > 1 ? " — جرّب يوم ثاني" : ""}</div>`}
          ${f.mode === "delivery" && !state.user ? `<div class="err">سجّل دخولك عشان تحجز موعد توصيل</div>` : ""}`}` : ""}
      ${f.mode === "delivery" && di?.left > 0 && F("freeHint") ? `<div class="free-hint">💡 أضف <b>${fmt(di.left)} ر.س</b> وصار التوصيل مجاني 🎉
        <div class="fh-bar"><i style="width:${Math.min(100, (sub / di.freeOver) * 100).toFixed(0)}%"></i></div><small>${fmt(sub)} من ${fmt(di.freeOver)}</small>
        <button class="link" id="coMore">‹ تصفّح المنيو وكمّل</button></div>` : ""}
      ${f.mode === "curbside" ? `<label class="label">🚗 وصف سيارتك (عشان المحل يعرفك)</label><input id="coCar" class="input" maxlength="60" value="${esc(f.car)}" placeholder="مثال: كامري بيضاء">` : ""}
      ${!F("payChoice") ? "" : `<label class="label">💳 الدفع عند الاستلام</label>`}
      ${!F("payChoice") ? "" : pays.length > 1 ? `<div class="chips">${pays.map((p) => `<button class="chip${f.pay === p ? " on" : ""}" data-pay="${p}">${PAY[p].icon} ${PAY[p].label}</button>`).join("")}</div>`
        : `<div class="hint-box">${PAY[pays[0]].icon} ${PAY[pays[0]].label} عند الاستلام (المحل يقبل هذي الطريقة بس)</div>`}

      ${f.mode === "delivery" ? `<button class="btn call" id="coLoc">📍 ${f.loc ? "موقعي محدد ✓ (حدّثه)" : "حدد موقعي للتوصيل"}</button>` : ""}
      <div class="row2"><div><label class="label">اسمك</label><input id="coName" class="input" maxlength="60" value="${esc(f.name)}"></div>
        <div><label class="label">جوالك</label><input id="coPhone" class="input" inputmode="tel" maxlength="14" value="${esc(f.phone)}" placeholder="05xxxxxxxx"></div></div>
      <label class="label">ملاحظة (اختياري)</label><input id="coNote" class="input" maxlength="300" value="${esc(f.note)}" placeholder="مثال: بدون بصل">
      <div class="co-sum">
        ${fee ? `<div><span>المجموع</span><b>${fmt(sub)}</b></div><div><span>🚚 التوصيل</span><b>${fmt(fee)}</b></div>` : f.mode === "delivery" ? `<div><span>🚚 التوصيل</span><b>مجاني 🎉</b></div>` : ""}
        ${vatNo ? `<div><span>المبلغ قبل الضريبة</span><b>${fmt(net)}</b></div><div><span>ضريبة القيمة المضافة ${vat.rate}%</span><b>${fmt(vatAmt)}</b></div>` : ""}
        <div class="tot"><span>💰 الإجمالي${vatNo ? " (شامل الضريبة)" : ""}</span><b>${fmt(total)} ر.س</b></div></div>
      ${!minOk ? `<div class="err">أقل طلب للتوصيل ${fmt(s.deliveryMinOrder)} ر.س — أضف ${fmt(s.deliveryMinOrder - sub)} ر.س أو اختر الاستلام</div>` : ""}
      ${blockedUntil(myStats, state.config) ? `<div class="err">🚫 الطلب موقف عن حسابك مؤقتاً لين ${new Date(blockedUntil(myStats, state.config)).toLocaleDateString(X.LOC)} بسبب طلبات سابقة ما انستلمت. لو فيه خطأ تواصل معنا.</div>` : ""}
      ${!state.user ? `<small class="hint">💡 سجّل دخولك عشان تنحفظ طلباتك وتقدر تعيدها بضغطة.</small>` : ""}
      ${blocked ? `<div class="closed-bar">${esc(blk)}</div>` : closedNow ? `<div class="hint-box">🌙 المحل مغلق الحين — طلبك يوصله ويتجهّز على موعدك</div>` : ""}
      <div id="coErr" class="err"></div>
      <button class="btn wa big" id="coSend" ${minOk && !blocked && !blockedUntil(myStats, state.config) ? "" : "disabled"}>${(later ? "📅 أرسل الطلب المسبق" : "📨 أرسل الطلب") + (branches.length > 1 && br0 ? ` لفرع ${esc(brName(br0))}` : " على واتساب")}</button>
      <button class="btn ghost" id="coBack">‹ رجوع للمتجر</button></div>`);
    keep = () => { f.name = $("coName").value; f.phone = $("coPhone").value; f.note = $("coNote").value; if ($("coCar")) f.car = $("coCar").value; };
    const b = $("sheetBody");
    b.querySelectorAll("[data-inc]").forEach((x) => x.onclick = () => { keep(); cart[x.dataset.inc].qty = Math.min(99, cart[x.dataset.inc].qty + 1); saveCarts(); draw(); });
    b.querySelectorAll("[data-dec]").forEach((x) => x.onclick = () => { keep(); const l = cart[x.dataset.dec]; l.qty--; if (l.qty <= 0) delete cart[x.dataset.dec]; saveCarts(); draw(); });
    b.querySelectorAll("[data-mode]").forEach((x) => x.onclick = () => { keep(); f.mode = x.dataset.mode; draw(); });
    b.querySelectorAll("[data-pay]").forEach((x) => x.onclick = () => { keep(); f.pay = x.dataset.pay; draw(); });
    b.querySelectorAll("[data-when]").forEach((x) => x.onclick = () => { keep(); f.when = x.dataset.when; draw(); });
    b.querySelectorAll("[data-day]").forEach((x) => x.onclick = () => { keep(); f.day = +x.dataset.day; f.slot = 0; draw(); });
    b.querySelectorAll("[data-slot]").forEach((x) => x.onclick = () => { keep(); f.slot = +x.dataset.slot; draw(); });
    $("coMore")?.addEventListener("click", () => openStore(sid, false));
    $("coBrChange")?.addEventListener("click", () => { keep(); pickOpen = !pickOpen; draw(); });
    b.querySelectorAll("[data-br]").forEach((x) => x.onclick = () => { keep(); f.branch = x.dataset.br; state.branchPick = { sid, id: f.branch }; pickOpen = false; f.slot = 0; draw(); });
    $("coLoc")?.addEventListener("click", () => { keep(); navigator.geolocation?.getCurrentPosition((p) => { f.loc = { lat: +p.coords.latitude.toFixed(6), lng: +p.coords.longitude.toFixed(6) }; draw(); }, () => toast("ما قدرنا نحدد موقعك")); });
    $("coBack").onclick = () => openStore(sid, false);
    $("coSend").onclick = async () => {
      keep();
      const phoneOk = X.normalizeSaudiPhone(f.phone);
      if (!f.name.trim()) return $("coErr").textContent = "اكتب اسمك.";
      if (!phoneOk) return $("coErr").textContent = "اكتب رقم جوال صحيح مثل 05xxxxxxxx.";
      if (f.mode === "delivery" && !f.loc) return $("coErr").textContent = "حدد موقعك للتوصيل.";
      if (f.mode === "curbside" && !f.car.trim()) return $("coErr").textContent = "اكتب وصف سيارتك (مثال: كامري بيضاء).";
      if (later) {
        if (!modeOkLater) return $("coErr").textContent = f.mode === "delivery" ? "المحل ما يوفر توصيل بموعد — اختر الاستلام." : "المحل ما يوفر استلام بموعد.";
        if (!f.slot) return $("coErr").textContent = "اختر وقت طلبك.";
        if (f.mode === "delivery" && !state.user) return $("coErr").textContent = "سجّل دخولك عشان تحجز موعد توصيل.";
      }
      if (f.mode === "curbside") { try { localStorage.setItem("hayyna_car", f.car.trim()); } catch {} }
      const br = branches.find((x) => x.id === f.branch) || branches[0] || {};
      const to = X.waDigits(br.phone || s.phone || "");
      if (!to) return $("coErr").textContent = "المحل ما عنده رقم واتساب.";
      // ⏸️ تأكد من السيرفر إن المحل ما أوقف الطلبات، وإن الفرع مفتوح
      await fetchStoreStatus(sid);
      const blk2 = orderBlock(s, sid, br.id ?? null);
      if (blk2 && (isPaused(sid, br.id ?? null) || !later || !preOn(sid))) { toast(blk2, true); return draw(); }
      if (later && !preOn(sid)) { toast("المحل قفل الطلب المسبق", true); return draw(); }
      if (later && f.slot < Date.now() + X.preCfg(preCache[sid]).prepMin * 60000) { f.slot = 0; toast("الوقت اللي اخترته فات — اختر وقت ثاني", true); return draw(); }
      // 📅 فترة التوصيل: نتأكد من السيرفر إنها ما امتلت
      const slotKey = later && f.mode === "delivery" ? (branches.length > 1 && br.id ? `${X.slotKeyOf(f.slot)}_${br.id}`.slice(0, 60) : X.slotKeyOf(f.slot)) : "";
      let slotNow = 0;
      if (slotKey) {
        try { const sd = await getDocFromServer(doc(db, "slotCounts", `${sid}_${slotKey}`)); slotNow = sd.exists() ? (sd.data().n | 0) : 0; } catch (e) { console.warn("slot", e); }
        if (slotNow >= X.preCfg(preCache[sid]).slotCap) { slotN.set(slotKey, slotNow); f.slot = 0; toast("😕 هالفترة امتلت الحين — اختر وقت ثاني", true); return draw(); }
      }
      // ❌ تأكد من السيرفر إن العروض اللي في السلة ما نفدت (يمكن الموظف علّمها الحين)
      const dealIds = Object.keys(cart).filter((id) => id.startsWith("deal_")).map((id) => id.slice(5));
      for (const oid of dealIds) {
        try {
          const od = await getDocFromServer(doc(db, "offers", oid));
          if (od.exists() && X.isSoldOut(od.data())) {
            const nm = cart["deal_" + oid]?.name || "العرض"; delete cart["deal_" + oid]; saveCarts();
            toast(`❌ «${nm}» نفد اليوم — شلناه من سلتك`, true);
            return Object.keys(cart).length ? draw() : openStore(sid, false);
          }
        } catch (e) { console.warn("soldout check", e); }
      }
      const ref = doc(collection(db, "orders"));
      const o = { id: ref.id, code: orderCode(), storeId: sid, storeName: s.name, branchId: br.id || "", branchName: branches.length > 1 ? (br.name || X.districtLabel(br.district) || "") : "",
        customerUid: state.user?.uid || "", customerName: f.name.trim().slice(0, 60), customerPhone: phoneOk,
        items: lines.map((l) => ({ id: l.id, name: l.name, kind: l.kind, qty: l.qty, price: l.price, ...(l.was > l.price ? { was: l.was } : {}), ...(l.off > 0 ? { off: l.off, bogo: l.bogo } : {}) })),
        subtotal: sub, discount: T.saved, deliveryFee: fee, total,
        vatRate: vatNo ? vat.rate : 0, vatAmount: vatNo ? vatAmt : 0, vatNumber: vatNo, mode: f.mode, location: f.mode === "delivery" ? f.loc : null,
        pay: F("payChoice") ? f.pay : "", car: f.mode === "curbside" ? f.car.trim().slice(0, 60) : "",
        note: f.note.trim().slice(0, 300), status: "sent",
        ...(later ? { scheduledAt: Timestamp.fromMillis(f.slot), ...(slotKey ? { slotKey } : {}) } : {}) };
      const url = `https://wa.me/${to}?text=${encodeURIComponent(orderText(o))}`;
      const w = window.open("", "_blank"); // نفتح النافذة الحين (قبل الانتظار) عشان المتصفح ما يمنعها
      if (state.user) {
        try {
          const full = { ...o, createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
          if (slotKey) {
            const b = writeBatch(db);
            b.set(ref, full);
            b.set(doc(db, "slotCounts", `${sid}_${slotKey}`), { storeId: sid, slotKey, n: slotNow + 1, last: ref.id });
            await b.commit();
          } else await setDoc(ref, full);
        }
        catch (e) {
          console.warn("order save", e);
          if (slotKey) { w?.close(); toast("😕 ما قدرنا نحجز هالفترة (يمكن امتلت) — اختر وقت ثاني", true); f.slot = 0; loadSlots(); return draw(); }
          if (e?.code === "permission-denied" && (await fetchStoreStatus(sid), isPaused(sid, br.id ?? null))) { w?.close(); toast(orderBlock(s, sid, br.id ?? null), true); return draw(); }
        }
      }
      if (w) w.location = url; else location.href = url;
      carts[sid] = {}; saveCarts();
      hideSheet(); toast(later ? `📅 انحجز طلبك #${o.code} — ${X.preWhen(f.slot)} ✅ كمّل في واتساب` : `انرسل طلبك #${o.code} ✅ كمّل في واتساب`, true);
    };
  };
  draw();
}

export const ordersOn = () => cat().enabled && cat().orderEnabled;
