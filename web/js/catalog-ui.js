// واجهة المنتجات/الخدمات/المنيو: إدارة التاجر + عرضها في صفحة المتجر + السلة والطلب على واتساب + سجل الطلبات.
import {
  collection, doc, onSnapshot, query, where, setDoc, updateDoc, deleteDoc, getDocs, serverTimestamp, Timestamp, writeBatch
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { db, state, $, toast, showSheet, hideSheet, openPage, closePage, uploadImage, openStore } from "./app.js";
import * as X from "./logic.js";
import { cropImage } from "./cropper.js";
import {
  KINDS, catalogOf, vatOf, baseKindOf, kindOrder, limitOf, slotId, passCost, riyal, itemVisible, itemPaidUntil, itemGrace,
  vatSplit, fmt, orderCode, orderText, ORDER_STATUS, validVatNumber, statusText, orderSteps
} from "./catalog.js";

const { esc, cld } = X;
const cat = () => catalogOf(state.config);
const fmtDate = (t) => { const d = new Date(t); return `${d.getDate()}/${d.getMonth() + 1}`; };
const kindPill = (k) => `<span class="kpill" style="background:${KINDS[k].bg};color:${KINDS[k].color}">${KINDS[k].icon} ${KINDS[k].label}</span>`;

// =================== التاجر ===================
const M = { uid: null, items: [], passes: [], orders: [], unsubs: [], ctx: null, onChange: null };

/** يتابع منتجات التاجر وظهورها المدفوع وطلباته (يُستدعى من لوحة التاجر) */
export function watchMerchantCatalog(uid, onChange) {
  M.onChange = onChange;
  if (M.uid === uid) return;
  M.unsubs.forEach((u) => u()); M.unsubs = []; M.uid = uid; M.items = []; M.passes = []; M.orders = [];
  const fire = () => M.onChange?.();
  M.unsubs.push(onSnapshot(query(collection(db, "items"), where("merchantId", "==", uid)), (s) => { M.items = s.docs.map((d) => ({ ...d.data(), id: d.id })); fire(); refreshPage(); }, () => {}));
  M.unsubs.push(onSnapshot(query(collection(db, "itemPasses"), where("merchantId", "==", uid)), (s) => { M.passes = s.docs.map((d) => ({ ...d.data(), id: d.id })); fire(); refreshPage(); }, () => {}));
  M.unsubs.push(onSnapshot(query(collection(db, "orders"), where("storeId", "==", uid)), (s) => {
    M.orders = s.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => X.ms(b.createdAt) - X.ms(a.createdAt)); fire();
    if (ordersOpen && !$("sheet").classList.contains("hidden")) M.onOrders?.();
  }, () => {}));
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
  </div>`;
}
export function bindCatalogPanel(ctx) {
  M.ctx = ctx;
  $("ccManage")?.addEventListener("click", () => openManager());
  $("ccOrders")?.addEventListener("click", openStoreOrders);
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
      ${!direct() ? `<small class="hint">⏳ بيظهر بعد مراجعة فريق حيّنا (عادة خلال ساعات). وثّق متجرك عشان تنزل منتجاتك مباشرة.</small>` : ""}
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
function openStoreOrders() {
  ordersOpen = true;
  const draw = () => {
    if (!ordersOpen) return;
    const list = M.orders;
    showSheet(`<div class="pad"><div class="section-title">🧾 الطلبات (${list.length})</div>
      ${list.length ? list.slice(0, 100).map((o) => orderCard(o, true)).join("") : `<div class="empty-inline">ما وصلتك طلبات للحين</div>`}</div>`);
    $("sheetBody").querySelectorAll("[data-os]").forEach((b) => b.onclick = () => {
      const o = M.orders.find((x) => x.id === b.dataset.oid), st = b.dataset.os;
      if (st === "received") return pickEta(o);
      if (st === "cancelled" && !confirm("إلغاء الطلب؟ بلّغ العميل على واتساب بعد.")) return;
      setStatus(o, st);
    });
  };
  const setStatus = async (o, st, extra = {}) => {
    try { await updateDoc(doc(db, "orders", o.id), { status: st, updatedAt: serverTimestamp(), ...extra }); Object.assign(o, { status: st }, extra); ordersOpen = true; draw(); }
    catch (e) { console.error(e); toast("تعذّر التحديث"); }
  };
  // استلام الطلب: المحل يختار وقت تقريبي للتجهيز
  const pickEta = (o) => {
    ordersOpen = false;
    showSheet(`<div class="pad"><div class="section-title">👍 استلام الطلب #${esc(o.code)}</div>
      <p class="desc tr">${o.mode === "delivery" ? "متى يطلع للتوصيل تقريباً؟" : "متى يكون جاهز للاستلام تقريباً؟"}</p>
      <div class="chips wrap">${[10, 15, 20, 30, 45, 60].map((m) => `<button class="chip" data-eta="${m}">${m} دقيقة</button>`).join("")}</div>
      <button class="btn call" id="etaSkip" style="margin-top:10px">بدون وقت</button></div>`);
    $("sheetBody").querySelectorAll("[data-eta]").forEach((x) => x.onclick = () => setStatus(o, "received", { etaAt: Timestamp.fromMillis(Date.now() + +x.dataset.eta * 60000) }));
    $("etaSkip").onclick = () => setStatus(o, "received");
  };
  M.onOrders = draw; // تتحدّث لحظياً لو وصل طلب جديد
  draw();
}
const etaText = (o) => { const t = X.ms(o.etaAt); if (!t || !["received"].includes(o.status)) return ""; const m = Math.round((t - Date.now()) / 60000);
  return m > 0 ? `⏱️ ${o.mode === "delivery" ? "يطلع للتوصيل" : "جاهز"} خلال ~${m} دقيقة` : `⏱️ ${o.mode === "delivery" ? "بيطلع" : "بيكون جاهز"} قريب`; };
function stepper(o) {
  if (o.status === "cancelled") return "";
  const steps = orderSteps(o), at = Math.max(0, steps.findIndex(([k]) => k === o.status));
  return `<div class="ostep">${steps.map(([k, l], i) => `<div class="${i <= at ? "on" : ""}${i === at ? " cur" : ""}"><i></i><small>${l}</small></div>`).join("")}</div>`;
}
function orderCard(o, asStore) {
  const st = ORDER_STATUS[o.status] || ORDER_STATUS.sent, when = X.ms(o.createdAt), del = o.mode === "delivery";
  const next = asStore ? ({
    sent: [["received", "👍 استلمته"], ["cancelled", "✕ إلغاء"]],
    received: [[del ? "delivering" : "ready", del ? "🚚 طلع للتوصيل" : "🛍️ جاهز للاستلام"], ["cancelled", "✕ إلغاء"]],
    ready: [["done", "✅ تم الاستلام"]], delivering: [["done", "✅ تم التوصيل"]],
  }[o.status] || []) : (o.status === "sent" ? [["cancelled", "✕ إلغاء الطلب"]] : []);
  const store = !asStore ? state.stores.find((x) => x.uid === o.storeId) : null;
  const wa = store ? X.waDigits(store.phone || store.branches?.[0]?.phone || "") : "";
  const rated = !asStore && myRatings.has(o.id);
  return `<div class="ocard${["sent"].includes(o.status) && asStore ? " new" : ""}"><div class="oc-top"><b>#${esc(o.code)}</b><span class="pill ${st.cls}">${asStore ? st.t : statusText(o)}</span></div>
    <div class="oc-sub">${asStore ? esc([o.customerName, X.toLocalPhone(o.customerPhone)].filter(Boolean).join(" • ")) : `🏪 ${esc(o.storeName)}`} • ${when ? new Date(when).toLocaleString("ar-SA-u-ca-gregory", { dateStyle: "short", timeStyle: "short" }) : ""}</div>
    ${stepper(o)}${etaText(o) ? `<div class="oc-eta">${etaText(o)}</div>` : ""}
    <div class="oc-items">${(o.items || []).map((i) => `${i.qty} × ${esc(i.name)}`).join("، ")}</div>
    <div class="oc-tot">${del ? "🚚 توصيل" : "🏃 استلام"} • <b>${fmt(o.total)} ر.س</b>${o.note ? ` • 📝 ${esc(o.note)}` : ""}</div>
    ${!asStore && ["received", "ready", "delivering"].includes(o.status) ? `<div class="oc-note">المحل استلم طلبك وما يمكن إلغاؤه${wa ? ` — <a href="https://wa.me/${wa}" target="_blank" rel="noopener">💬 تواصل مع المحل</a>` : ""}</div>` : ""}
    ${asStore && o.mode === "delivery" && o.location ? `<a class="oc-map" href="https://maps.google.com/?q=${o.location.lat},${o.location.lng}" target="_blank" rel="noopener">📍 موقع العميل</a>` : ""}
    ${next.length || !asStore ? `<div class="oc-act">${next.map(([s, l]) => `<button class="chip" data-oid="${esc(o.id)}" data-os="${s}">${l}</button>`).join("")}
      ${!asStore && o.status === "done" ? (rated ? `<span class="pill">⭐ شكراً على تقييمك</span>` : `<button class="chip" data-rate="${esc(o.id)}">⭐ قيّم طلبك</button>`) : ""}
      ${!asStore ? `<button class="chip" data-again="${esc(o.id)}">↻ اطلب نفسه</button>` : ""}</div>` : ""}
  </div>`;
}

// ----- طلباتي (تتحدّث لحظياً + تنبيه لما تتغيّر الحالة) -----
const MY = { uid: null, unsub: null, list: [], loaded: false, open: false, err: "" };
const myRatings = new Set();
export function watchMyOrders() {
  // المفتاح يشمل حالة الخدمة: لو الإعدادات وصلت متأخرة نبدأ المتابعة بعدها
  const uid = state.user?.uid && cat().enabled && cat().orderEnabled ? state.user.uid : null;
  if (MY.uid === uid) return;
  MY.unsub?.(); MY.unsub = null; MY.uid = uid; MY.list = []; MY.loaded = false; myRatings.clear();
  if (!uid) return;
  getDocs(query(collection(db, "ratings"), where("uid", "==", uid))).then((r) => r.docs.forEach((d) => myRatings.add(d.id))).catch(() => {});
  MY.unsub = onSnapshot(query(collection(db, "orders"), where("customerUid", "==", uid)), (snap) => {
    const prev = new Map(MY.list.map((o) => [o.id, o.status]));
    MY.list = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => X.ms(b.createdAt) - X.ms(a.createdAt));
    if (MY.loaded) for (const o of MY.list) {
      const was = prev.get(o.id);
      if (was && was !== o.status && o.status !== "sent") toast(`🧾 طلبك #${o.code}: ${statusText(o)}`, true);
    }
    MY.loaded = true; MY.err = "";
    if (MY.open) drawMyOrders();
  }, (e) => { console.warn(e); MY.err = e.code || e.message || "خطأ"; if (MY.open) drawMyOrders(); });
}
export function openMyOrders() {
  if (!state.user) return;
  watchMyOrders(); MY.open = true;
  drawMyOrders();
}
function drawMyOrders() {
  const who = state.user?.email || X.toLocalPhone(state.profile?.phone || "") || "";
  const list = MY.list;
  if ($("sheet").classList.contains("hidden") && MY.open && MY.loaded) { /* انقفلت */ }
  showSheet(`<div class="pad my-orders"><div class="section-title">🧾 طلباتي</div>
    ${MY.err ? `<div class="err">تعذّر تحميل طلباتك (${esc(MY.err)}). حدّث الصفحة وجرّب مرة ثانية.</div>` : ""}
    ${!MY.loaded && !MY.err ? `<div class="spinner"></div>` : list.length ? list.slice(0, 50).map((o) => orderCard(o, false)).join("") : MY.err ? "" : `<div class="empty-inline">ما طلبت شي للحين<br><small>ادخل صفحة أي متجر واطلب منه 🛍️</small></div>`}
    ${who ? `<small class="hint">الطلبات المحفوظة لحسابك: ${esc(who)} — الطلبات من حساب ثاني ما تطلع هنا.</small>` : ""}</div>`);
  const b = $("sheetBody");
  b.querySelectorAll("[data-os]").forEach((x) => x.onclick = async () => {
    if (!confirm("إلغاء الطلب؟ بلّغ المحل على واتساب بعد.")) return;
    try { await updateDoc(doc(db, "orders", x.dataset.oid), { status: "cancelled", updatedAt: serverTimestamp() }); } catch { toast("تعذّر الإلغاء — يمكن المحل استلمه"); }
  });
  b.querySelectorAll("[data-again]").forEach((x) => x.onclick = () => {
    const o = list.find((y) => y.id === x.dataset.again), cart = cartOf(o.storeId);
    (o.items || []).forEach((i) => { cart[i.id] = { ...i }; }); saveCarts();
    MY.open = false; hideSheet(); setTimeout(() => openStore(o.storeId, true), 60);
  });
  b.querySelectorAll("[data-rate]").forEach((x) => x.onclick = () => rateOrder(list.find((y) => y.id === x.dataset.rate)));
}
export const myOrdersClosed = () => { MY.open = false; ordersOpen = false; };

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
  const box = $("storeRatings"); if (!box) return;
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
const cartSum = (sid) => Object.values(cartOf(sid)).reduce((a, l) => a + l.qty * l.price, 0);
const storeCache = {};

/** يرسم منتجات المتجر داخل صفحة المتجر (عنصر #storeItems) */
export async function renderStoreItems(s) {
  const c = cat(), box = $("storeItems");
  if (!box || !c.enabled) return;
  let items = [], passes = [];
  try {
    [items, passes] = await Promise.all([
      getDocs(query(collection(db, "items"), where("merchantId", "==", s.uid), where("approved", "==", true))).then((r) => r.docs.map((d) => ({ ...d.data(), id: d.id }))),
      c.pricing.enabled ? getDocs(query(collection(db, "itemPasses"), where("merchantId", "==", s.uid))).then((r) => r.docs.map((d) => d.data())) : [],
    ]);
  } catch (e) { console.warn("items", e); return; }
  const now = Date.now();
  items = items.filter((it) => itemVisible(it, passes, c, now));
  storeCache[s.uid] = { s, items };
  if (!$("storeItems")) return;
  // نشيل من السلة اللي ما عاد موجود، ونحدّث الأسعار
  const cart = cartOf(s.uid);
  Object.keys(cart).forEach((id) => { const it = items.find((x) => x.id === id && x.available !== false); if (!it) delete cart[id]; else cart[id].price = it.price; });
  drawStoreItems(s.uid);
}
function drawStoreItems(sid) {
  const box = $("storeItems"), data = storeCache[sid];
  if (!box || !data) return;
  const { s, items } = data, c = cat(), base = baseKindOf(state.config, s.category), cart = cartOf(sid);
  if (!items.length) { box.innerHTML = ""; return; }
  const order = c.orderEnabled;
  box.innerHTML = kindOrder(base).map((k) => {
    const list = items.filter((x) => x.kind === k);
    if (!list.length) return "";
    const K = KINDS[k], secs = [...new Set([...list].sort((a, b) => a.slot - b.slot).map((x) => x.section || ""))]; // ترتيب الأقسام حسب ترتيب إضافة التاجر
    return `<div class="kind-sec" style="--kc:${K.color};--kb:${K.bg}">
      <div class="ks-title">${K.icon} ${K.title} <small>(${list.length})</small></div>
      ${secs.map((sec) => `${sec ? `<div class="ks-sub">${esc(sec)}</div>` : ""}${list.filter((x) => (x.section || "") === sec).sort((a, b) => a.slot - b.slot).map((it) => {
        const q = cart[it.id]?.qty || 0, out = it.available === false;
        return `<div class="icard${out ? " out" : ""}">
          ${it.imageUrl ? `<img src="${esc(cld(it.imageUrl, 240))}" data-full="${esc(cld(it.imageUrl, 1400))}" alt="">` : `<div class="iph">${K.icon}</div>`}
          <div class="ic-main"><b>${esc(it.name)}</b>${it.desc ? `<small>${esc(it.desc)}</small>` : ""}<div class="ic-price">${fmt(it.price)} ر.س</div></div>
          ${out ? `<span class="pill closed">نفد</span>` : order ? (q ? `<div class="qty"><button data-dec="${esc(it.id)}">−</button><b>${q}</b><button data-inc="${esc(it.id)}">＋</button></div>` : `<button class="add-btn" data-inc="${esc(it.id)}">＋ أضف</button>`) : ""}
        </div>`; }).join("")}`).join("")}
    </div>`;
  }).join("") + (order && cartCount(sid) ? `<div class="cart-bar"><span>🧾 سلتك: <b>${cartCount(sid)}</b> • <b>${fmt(cartSum(sid))} ر.س</b></span><button class="btn dir" id="cartGo">اطلب</button></div>` : "");
  box.querySelectorAll("[data-inc]").forEach((b) => b.onclick = () => { const it = items.find((x) => x.id === b.dataset.inc); const l = cart[it.id] || (cart[it.id] = { id: it.id, name: it.name, kind: it.kind, price: it.price, qty: 0 }); l.qty = Math.min(99, l.qty + 1); saveCarts(); drawStoreItems(sid); });
  box.querySelectorAll("[data-dec]").forEach((b) => b.onclick = () => { const l = cart[b.dataset.dec]; if (l) { l.qty--; if (l.qty <= 0) delete cart[b.dataset.dec]; } saveCarts(); drawStoreItems(sid); });
  box.querySelectorAll("img[data-full]").forEach((im) => im.onclick = () => { $("viewerImg").src = im.dataset.full; $("viewer").classList.remove("hidden"); });
  $("cartGo")?.addEventListener("click", () => openCheckout(sid));
}

function openCheckout(sid) {
  const { s } = storeCache[sid], cart = cartOf(sid), vat = vatOf(state.config);
  const branches = s.branches || [];
  const f = { mode: "pickup", branch: branches[0]?.id || "", name: state.profile?.name || state.user?.displayName || "", phone: X.toLocalPhone(state.profile?.phone || ""), note: "", loc: state.me || null };
  const draw = () => {
    const lines = Object.values(cart);
    if (!lines.length) { openStore(sid, false); return; }
    const sub = lines.reduce((a, l) => a + l.qty * l.price, 0);
    const fee = f.mode === "delivery" ? (s.deliveryFreeOver > 0 && sub >= s.deliveryFreeOver ? 0 : (s.deliveryFee || 0)) : 0;
    const total = Math.round((sub + fee) * 100) / 100, vatNo = vat.enabled && validVatNumber(s.vatNumber) ? s.vatNumber : "";
    const { vat: vatAmt, net } = vatSplit(total, vat.rate);
    const minOk = f.mode !== "delivery" || !(s.deliveryMinOrder > 0) || sub >= s.deliveryMinOrder;
    showSheet(`<div class="pad form checkout"><div class="section-title">🧾 طلبك من ${esc(s.name)}</div>
      ${lines.map((l) => `<div class="co-line"><span>${esc(l.name)}</span><div class="qty"><button data-dec="${esc(l.id)}">−</button><b>${l.qty}</b><button data-inc="${esc(l.id)}">＋</button></div><b>${fmt(l.qty * l.price)}</b></div>`).join("")}
      <div class="chips">${`<button class="chip${f.mode === "pickup" ? " on" : ""}" data-mode="pickup">🏃 استلام من المحل</button>`}${s.delivery ? `<button class="chip${f.mode === "delivery" ? " on" : ""}" data-mode="delivery">🚚 توصيل</button>` : ""}</div>
      ${branches.length > 1 ? `<label class="label">الفرع</label><select id="coBr" class="input">${branches.map((b) => `<option value="${esc(b.id)}" ${f.branch === b.id ? "selected" : ""}>${esc(b.name || X.districtLabel(b.district) || "فرع")}</option>`).join("")}</select>` : ""}
      ${f.mode === "delivery" ? `<button class="btn call" id="coLoc">📍 ${f.loc ? "موقعي محدد ✓ (حدّثه)" : "حدد موقعي للتوصيل"}</button>` : ""}
      <div class="row2"><div><label class="label">اسمك</label><input id="coName" class="input" maxlength="60" value="${esc(f.name)}"></div>
        <div><label class="label">جوالك</label><input id="coPhone" class="input" inputmode="tel" maxlength="14" value="${esc(f.phone)}" placeholder="05xxxxxxxx"></div></div>
      <label class="label">ملاحظة (اختياري)</label><input id="coNote" class="input" maxlength="300" value="${esc(f.note)}" placeholder="مثال: بدون بصل">
      <div class="co-sum">
        ${fee ? `<div><span>المجموع</span><b>${fmt(sub)}</b></div><div><span>🚚 التوصيل</span><b>${fmt(fee)}</b></div>` : f.mode === "delivery" && s.deliveryFreeOver > 0 && sub >= s.deliveryFreeOver ? `<div><span>🚚 التوصيل</span><b>مجاني 🎉</b></div>` : ""}
        ${vatNo ? `<div><span>المبلغ قبل الضريبة</span><b>${fmt(net)}</b></div><div><span>ضريبة القيمة المضافة ${vat.rate}%</span><b>${fmt(vatAmt)}</b></div>` : ""}
        <div class="tot"><span>💰 الإجمالي${vatNo ? " (شامل الضريبة)" : ""}</span><b>${fmt(total)} ر.س</b></div></div>
      ${!minOk ? `<div class="err">أقل طلب للتوصيل ${fmt(s.deliveryMinOrder)} ر.س</div>` : ""}
      ${!state.user ? `<small class="hint">💡 سجّل دخولك عشان تنحفظ طلباتك وتقدر تعيدها بضغطة.</small>` : ""}
      <div id="coErr" class="err"></div>
      <button class="btn wa big" id="coSend" ${minOk ? "" : "disabled"}>📨 أرسل الطلب على واتساب</button>
      <button class="btn ghost" id="coBack">‹ رجوع للمتجر</button></div>`);
    const keep = () => { f.name = $("coName").value; f.phone = $("coPhone").value; f.note = $("coNote").value; if ($("coBr")) f.branch = $("coBr").value; };
    const b = $("sheetBody");
    b.querySelectorAll("[data-inc]").forEach((x) => x.onclick = () => { keep(); cart[x.dataset.inc].qty = Math.min(99, cart[x.dataset.inc].qty + 1); saveCarts(); draw(); });
    b.querySelectorAll("[data-dec]").forEach((x) => x.onclick = () => { keep(); const l = cart[x.dataset.dec]; l.qty--; if (l.qty <= 0) delete cart[x.dataset.dec]; saveCarts(); draw(); });
    b.querySelectorAll("[data-mode]").forEach((x) => x.onclick = () => { keep(); f.mode = x.dataset.mode; draw(); });
    $("coLoc")?.addEventListener("click", () => { keep(); navigator.geolocation?.getCurrentPosition((p) => { f.loc = { lat: +p.coords.latitude.toFixed(6), lng: +p.coords.longitude.toFixed(6) }; draw(); }, () => toast("ما قدرنا نحدد موقعك")); });
    $("coBack").onclick = () => openStore(sid, false);
    $("coSend").onclick = async () => {
      keep();
      const phoneOk = X.normalizeSaudiPhone(f.phone);
      if (!f.name.trim()) return $("coErr").textContent = "اكتب اسمك.";
      if (!phoneOk) return $("coErr").textContent = "اكتب رقم جوال صحيح مثل 05xxxxxxxx.";
      if (f.mode === "delivery" && !f.loc) return $("coErr").textContent = "حدد موقعك للتوصيل.";
      const br = branches.find((x) => x.id === f.branch) || branches[0] || {};
      const to = X.waDigits(br.phone || s.phone || "");
      if (!to) return $("coErr").textContent = "المحل ما عنده رقم واتساب.";
      const ref = doc(collection(db, "orders"));
      const o = { id: ref.id, code: orderCode(), storeId: sid, storeName: s.name, branchId: br.id || "", branchName: branches.length > 1 ? (br.name || X.districtLabel(br.district) || "") : "",
        customerUid: state.user?.uid || "", customerName: f.name.trim().slice(0, 60), customerPhone: phoneOk,
        items: lines.map((l) => ({ id: l.id, name: l.name, kind: l.kind, qty: l.qty, price: l.price })), subtotal: sub, deliveryFee: fee, total,
        vatRate: vatNo ? vat.rate : 0, vatAmount: vatNo ? vatAmt : 0, vatNumber: vatNo, mode: f.mode, location: f.mode === "delivery" ? f.loc : null,
        note: f.note.trim().slice(0, 300), status: "sent" };
      const url = `https://wa.me/${to}?text=${encodeURIComponent(orderText(o))}`;
      const w = window.open("", "_blank"); // نفتح النافذة الحين (قبل الانتظار) عشان المتصفح ما يمنعها
      if (state.user) { try { await setDoc(ref, { ...o, createdAt: serverTimestamp(), updatedAt: serverTimestamp() }); } catch (e) { console.warn("order save", e); } }
      if (w) w.location = url; else location.href = url;
      carts[sid] = {}; saveCarts();
      hideSheet(); toast(`انرسل طلبك #${o.code} ✅ كمّل في واتساب`, true);
    };
  };
  draw();
}

export const ordersOn = () => cat().enabled && cat().orderEnabled;
