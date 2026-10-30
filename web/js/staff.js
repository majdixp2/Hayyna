// 👥 موظفين المتجر: صاحب المحل يضيف موظفين برقم + رمز سري (بدون إيميلاتهم)، والموظف يشوف الطلبات و«نفد اليوم» بس.
// تقنياً: كل موظف له حساب داخلي باسم مخفي (رقم المتجر-رقم الموظف-عشوائي@staff.lacover.app) وكلمة مروره هي الرمز السري.
import { initializeApp, deleteApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import { getAuth, createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut, updatePassword } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  collection, doc, onSnapshot, query, where, limit, getDoc, getDocs, setDoc, updateDoc, writeBatch, serverTimestamp, Timestamp
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig } from "../config.js";
import { db, auth, state, $, toast, showSheet, hideSheet, openPage } from "./app.js";
import { feat } from "./catalog.js";
import * as X from "./logic.js";

export const STAFF_DOMAIN = "staff.lacover.app";
export const isStaffEmail = (e) => String(e || "").toLowerCase().endsWith("@" + STAFF_DOMAIN);
export const ROLES = {
  cashier: { t: "💳 كاشير", perms: ["orders", "stock"], hint: "يستقبل الطلبات ويغيّر حالتها، ويعلّم الأصناف «نفد»" },
  kitchen: { t: "👨‍🍳 مطبخ", perms: ["orders"], hint: "يشوف الطلبات ويحدّث حالتها (يتجهّز / جاهز)" },
};
export const PERMS = { orders: "🧾 الطلبات (استقبال وتغيير الحالة)", stock: "🍽️ «نفد اليوم» للأصناف" };
const { esc } = X;
const staffOn = () => feat(state.config, "staff");
const qrOn = () => feat(state.config, "staffQr");
const rand = () => Math.random().toString(36).slice(2, 8).replace(/[^a-z0-9]/g, "x").padEnd(6, "x");
const PIN_RE = /^[0-9]{6}$/;
const toEn = (s) => String(s || "").replace(/[٠-٩]/g, (d) => "٠١٢٣٤٥٦٧٨٩".indexOf(d)).replace(/[۰-۹]/g, (d) => "۰۱۲۳۴۵۶۷۸۹".indexOf(d));

// حساب داخلي جديد بدون ما يطلع صاحب المحل من حسابه (تطبيق Firebase ثاني مؤقت)
async function createAccount(email, pin) {
  const app2 = initializeApp(firebaseConfig, "staff-" + Date.now());
  try { const cred = await createUserWithEmailAndPassword(getAuth(app2), email, pin); return cred.user.uid; }
  finally { try { await signOut(getAuth(app2)); } catch {} deleteApp(app2).catch(() => {}); }
}

// =================== صاحب المحل ===================
const O = { code: null, list: [], unsub: null };

async function myCode() {
  if (O.code) return O.code;
  const uid = state.user.uid;
  const q = await getDocs(query(collection(db, "storeCodes"), where("uid", "==", uid), limit(1)));
  if (!q.empty) return (O.code = q.docs[0].id);
  for (let i = 0; i < 8; i++) {
    const code = String(10000 + Math.floor(Math.random() * 90000));
    try { await setDoc(doc(db, "storeCodes", code), { uid, at: serverTimestamp() }); return (O.code = code); } catch { /* مأخوذ: نجرّب غيره */ }
  }
  throw new Error("تعذّر إنشاء رقم المتجر، جرّب مرة ثانية");
}

/** بطاقة «موظفين المتجر» داخل لوحة التاجر */
export function staffCardHtml() {
  if (!staffOn()) return "";
  return `<button class="m-card" id="staffCard"><div class="mc-ico">👥</div><div class="mc-main"><b>موظفين المتجر</b><small>العاملين يدخلون برقم ورمز سري ويشوفون الطلبات بس</small></div><span>›</span></button>`;
}
export function bindStaffCard(storeName) {
  $("staffCard")?.addEventListener("click", () => openStaffManager(storeName));
}

export async function openStaffManager(storeName) {
  openPage("👥 موظفين المتجر", `<div class="pad"><div class="spinner"></div></div>`, "", () => { O.unsub?.(); O.unsub = null; });
  let code;
  try { code = await myCode(); } catch (e) { $("pageBody").innerHTML = `<div class="pad"><p class="desc">${esc(e.message)}</p></div>`; return; }
  O.unsub?.();
  O.unsub = onSnapshot(query(collection(db, "storeStaff"), where("storeId", "==", state.user.uid)), (s) => {
    O.list = s.docs.map((d) => ({ ...d.data(), uid: d.id })).sort((a, b) => a.n - b.n); draw();
  }, (e) => { console.error(e); toast("تعذّر تحميل الموظفين"); });
  const draw = () => {
    if (!$("pageBody")) return;
    $("pageBody").innerHTML = `<div class="pad staff-mgr">
      <div class="st-code"><small>رقم المتجر</small><b dir="ltr">${esc(code)}</b><span>يكتبه الموظف مع رقمه ورمزه السري عند «دخول موظف»</span></div>
      ${O.list.length ? O.list.map((m) => `<div class="st-row${m.active ? "" : " off"}">
        <div class="st-n">${m.n}</div>
        <div class="st-main"><b>${esc(m.name || "موظف " + m.n)}</b><small>${esc(ROLES[m.role]?.t || "")} • ${m.perms.map((p) => p === "orders" ? "🧾 الطلبات" : "🍽️ نفد اليوم").join(" • ")}${m.active ? "" : " • ⏸️ موقوف"}</small></div>
        <button class="btn-soft" data-st="${esc(m.uid)}">إدارة</button></div>`).join("")
        : `<div class="empty-inline">ما أضفت موظفين للحين.<br>أضف الكاشير أو المطبخ، وكل واحد يدخل برقمه ورمزه السري.</div>`}
      <button class="btn dir big" id="stAdd">➕ إضافة موظف</button>
      <p class="hint">🔒 الموظف ما يشوف العروض ولا الأسعار ولا الرصيد ولا صفحة المتجر. وتقدر توقفه بضغطة.</p>
    </div>`;
    $("stAdd").onclick = () => staffForm(null, code, storeName);
    $("pageBody").querySelectorAll("[data-st]").forEach((b) => b.onclick = () => staffActions(O.list.find((m) => m.uid === b.dataset.st), code, storeName));
  };
  draw();
}

function staffForm(m, code, storeName) {
  const used = new Set(O.list.map((x) => x.n));
  let n = m?.n || 1; while (!m && used.has(n)) n++;
  if (!m && n > 50) return toast("وصلت للحد (50 موظف)");
  let role = m?.role || "cashier", perms = new Set(m?.perms || ROLES.cashier.perms);
  const draw = () => {
    showSheet(`<div class="pad form staff-form"><div class="section-title">${m ? "✎ تعديل الموظف" : "➕ موظف جديد"} <span class="pill">رقم ${n}</span></div>
      <label class="label">الاسم (اختياري)</label><input id="stName" class="input" maxlength="40" placeholder="مثال: أحمد — الكاشير" value="${esc(m?.name || $("stName")?.value || "")}">
      <label class="label">الدور</label>
      <div class="chips wrap">${Object.entries(ROLES).map(([k, r]) => `<button class="chip${role === k ? " on" : ""}" data-role="${k}">${r.t}</button>`).join("")}</div>
      <small class="hint">${esc(ROLES[role].hint)}</small>
      <label class="label">الصلاحيات</label>
      ${Object.entries(PERMS).map(([k, l]) => `<label class="switch-row"><span>${l}</span><input type="checkbox" data-perm="${k}" ${perms.has(k) ? "checked" : ""}></label>`).join("")}
      ${m ? "" : `<label class="label">الرمز السري (6 أرقام)</label>
      <input id="stPin" class="input pin" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="new-password" placeholder="••••••">
      <input id="stPin2" class="input pin" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="new-password" placeholder="أعد كتابة الرمز">`}
      <button class="btn dir big" id="stSave">${m ? "حفظ" : "إضافة الموظف"}</button></div>`);
    const b = $("sheetBody");
    b.querySelectorAll("[data-role]").forEach((x) => x.onclick = () => { role = x.dataset.role; perms = new Set(ROLES[role].perms); draw(); });
    b.querySelectorAll("[data-perm]").forEach((x) => x.onchange = () => { x.checked ? perms.add(x.dataset.perm) : perms.delete(x.dataset.perm); });
    $("stSave").onclick = async () => {
      const name = $("stName").value.trim().slice(0, 40);
      if (!perms.size) return toast("اختر صلاحية وحدة على الأقل");
      const btn = $("stSave"); btn.disabled = true;
      try {
        if (m) {
          await updateDoc(doc(db, "storeStaff", m.uid), { name, role, perms: [...perms] });
          toast("انحفظ ✅"); hideSheet(); return;
        }
        const pin = toEn($("stPin").value), pin2 = toEn($("stPin2").value);
        if (!PIN_RE.test(pin)) { btn.disabled = false; return toast("الرمز السري لازم 6 أرقام"); }
        if (pin !== pin2) { btn.disabled = false; return toast("الرمزين مو متطابقين"); }
        btn.textContent = "ننشئ الحساب…";
        await addMember({ n, name, role, perms: [...perms], pin, code, storeName });
        hideSheet(); showCredentials(code, n, name);
      } catch (e) {
        console.error(e); btn.disabled = false; btn.textContent = m ? "حفظ" : "إضافة الموظف";
        toast(e.code === "auth/operation-not-allowed" ? "فعّل Email/Password في Firebase أول (من الإعدادات)" : e.code === "auth/weak-password" ? "الرمز السري ضعيف، اختر 6 أرقام ثانية" : "تعذّر الحفظ: " + (e.code || e.message), true);
      }
    };
  };
  draw();
}

async function addMember({ n, name, role, perms, pin, code, storeName, replace }) {
  const email = `${code}-${n}-${rand()}@${STAFF_DOMAIN}`;
  const uid = await createAccount(email, pin);
  const wb = writeBatch(db);
  if (replace) wb.delete(doc(db, "storeStaff", replace.uid));
  wb.set(doc(db, "storeStaff", uid), { storeId: state.user.uid, storeName: String(storeName || "").slice(0, 100), code, n, name, role, perms,
    active: replace ? replace.active : true, createdAt: serverTimestamp(), email });
  wb.set(doc(db, "staffLogin", `${code}-${n}`), { email, storeId: state.user.uid, n });
  await wb.commit();
}

function staffActions(m, code, storeName) {
  if (!m) return;
  showSheet(`<div class="pad"><div class="section-title">👤 ${esc(m.name || "موظف " + m.n)} <span class="pill">رقم ${m.n}</span></div>
    <button class="btn call" id="saInfo">🔑 بيانات الدخول${qrOn() ? " و QR" : ""}</button>
    <button class="btn call" id="saEdit">✎ تعديل الاسم والصلاحيات</button>
    <button class="btn call" id="saPin">🔁 رمز سري جديد</button>
    <button class="btn call" id="saToggle">${m.active ? "⏸️ إيقاف مؤقت" : "▶️ تفعيل"}</button>
    <button class="btn danger" id="saDel">🗑️ حذف الموظف</button></div>`);
  $("saInfo").onclick = () => showCredentials(code, m.n, m.name);
  $("saEdit").onclick = () => staffForm(m, code, storeName);
  $("saToggle").onclick = async () => {
    try { await updateDoc(doc(db, "storeStaff", m.uid), { active: !m.active }); toast(m.active ? "⏸️ انوقف — ما يقدر يدخل الحين" : "▶️ رجع يشتغل"); hideSheet(); }
    catch (e) { toast("تعذّر: " + (e.code || e.message)); }
  };
  $("saDel").onclick = async () => {
    if (!confirm(`حذف «${m.name || "موظف " + m.n}»؟ بيطلع من الحساب ويتقفل دخوله فوراً.`)) return;
    try { const wb = writeBatch(db); wb.delete(doc(db, "storeStaff", m.uid)); wb.delete(doc(db, "staffLogin", `${code}-${m.n}`)); await wb.commit(); toast("انحذف"); hideSheet(); }
    catch (e) { toast("تعذّر الحذف: " + (e.code || e.message)); }
  };
  $("saPin").onclick = () => {
    showSheet(`<div class="pad form"><div class="section-title">🔁 رمز سري جديد — ${esc(m.name || "موظف " + m.n)}</div>
      <p class="desc tr">الرمز القديم يتقفل فوراً، والموظف يدخل بالرمز الجديد (نفس رقمه).</p>
      <input id="npPin" class="input pin" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="new-password" placeholder="••••••">
      <input id="npPin2" class="input pin" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="new-password" placeholder="أعد كتابة الرمز">
      <button class="btn dir big" id="npGo">حفظ الرمز الجديد</button></div>`);
    $("npGo").onclick = async () => {
      const pin = toEn($("npPin").value), pin2 = toEn($("npPin2").value);
      if (!PIN_RE.test(pin)) return toast("الرمز السري لازم 6 أرقام");
      if (pin !== pin2) return toast("الرمزين مو متطابقين");
      $("npGo").disabled = true;
      try { await addMember({ n: m.n, name: m.name, role: m.role, perms: m.perms, pin, code, storeName, replace: m }); toast("✅ تغيّر الرمز السري"); hideSheet(); }
      catch (e) { console.error(e); $("npGo").disabled = false; toast("تعذّر: " + (e.code || e.message), true); }
    };
  };
}

let qrLib = null;
function loadQr() {
  if (window.QRCode) return Promise.resolve();
  return qrLib || (qrLib = new Promise((res, rej) => {
    const s = document.createElement("script"); s.src = "https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js";
    s.onload = res; s.onerror = rej; document.head.appendChild(s);
  }));
}
function showCredentials(code, n, name) {
  const link = `${location.origin}/?staff=${code}-${n}`;
  showSheet(`<div class="pad" style="text-align:center"><div class="section-title">🔑 دخول ${esc(name || "الموظف")}</div>
    <div class="st-cred"><div><small>رقم المتجر</small><b dir="ltr">${esc(code)}</b></div><div><small>رقم الموظف</small><b dir="ltr">${n}</b></div><div><small>الرمز السري</small><b>اللي اخترته</b></div></div>
    ${qrOn() ? `<div id="stQr" class="st-qr"></div><p class="desc tr">الموظف يصوّر الـ QR بكاميرا جواله، ويكتب الرمز السري بس.</p>` : ""}
    <p class="desc tr">من التطبيق أو الموقع: «حسابي» ← «👨‍🍳 دخول موظف».</p>
    <button class="btn call" id="stCopy">📋 انسخ بيانات الدخول</button></div>`);
  $("stCopy").onclick = async () => {
    const txt = `دخول موظف في لكوفر 👨‍🍳\nرقم المتجر: ${code}\nرقم الموظف: ${n}\n${qrOn() ? link : location.origin}`;
    try { await navigator.clipboard.writeText(txt); toast("انسخت ✅"); } catch { prompt("انسخ:", txt); }
  };
  if (qrOn()) loadQr().then(() => { const el = $("stQr"); if (el) new window.QRCode(el, { text: link, width: 200, height: 200, colorDark: "#10318F", colorLight: "#ffffff" }); }).catch(() => { const el = $("stQr"); if (el) el.textContent = "تعذّر رسم الـ QR"; });
}

// =================== دخول الموظف ===================
export function openStaffLogin(prefill = "") {
  if (!staffOn()) return toast("دخول الموظفين موقف حالياً");
  const [pc, pn] = String(prefill).split("-");
  let saved = ""; try { saved = localStorage.getItem("hayyna_staff_code") || ""; } catch {}
  showSheet(`<div class="pad form staff-login"><div class="section-title">👨‍🍳 دخول موظف</div>
    <p class="desc tr">البيانات يعطيك إياها صاحب المحل.</p>
    <div class="row2"><div><label class="label">رقم المتجر</label><input id="slCode" class="input pin" inputmode="numeric" maxlength="5" dir="ltr" value="${esc(pc || saved)}"></div>
      <div><label class="label">رقم الموظف</label><input id="slN" class="input pin" inputmode="numeric" maxlength="2" dir="ltr" value="${esc(pn || "")}"></div></div>
    <label class="label">الرمز السري</label><input id="slPin" class="input pin" type="password" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="current-password" placeholder="••••••">
    <button class="btn dir big" id="slGo">دخول</button></div>`);
  setTimeout(() => $((pc || saved) && pn ? "slPin" : pc || saved ? "slN" : "slCode")?.focus(), 250);
  const go = async () => {
    const code = toEn($("slCode").value).trim(), n = parseInt(toEn($("slN").value), 10), pin = toEn($("slPin").value);
    if (!/^[1-9][0-9]{4}$/.test(code)) return toast("رقم المتجر 5 أرقام");
    if (!(n >= 1 && n <= 50)) return toast("اكتب رقم الموظف");
    if (!PIN_RE.test(pin)) return toast("الرمز السري 6 أرقام");
    const btn = $("slGo"); btn.disabled = true; btn.textContent = "ندخّلك…";
    try {
      const d = await getDoc(doc(db, "staffLogin", `${code}-${n}`));
      if (!d.exists()) throw { code: "nf" };
      await signInWithEmailAndPassword(auth, d.data().email, pin);
      try { localStorage.setItem("hayyna_staff_code", code); } catch {}
      hideSheet();
    } catch (e) {
      btn.disabled = false; btn.textContent = "دخول";
      toast(e.code === "nf" ? "رقم المتجر أو رقم الموظف غلط" : e.code === "auth/too-many-requests" ? "محاولات كثيرة — انتظر شوي وجرّب" :
        /invalid-credential|wrong-password|user-not-found|invalid-login/.test(e.code || "") ? "الرمز السري غلط" : "تعذّر الدخول: " + (e.code || e.message), true);
    }
  };
  $("slGo").onclick = go;
  $("slPin").addEventListener("keydown", (e) => { if (e.key === "Enter") go(); });
}

// =================== شاشة الموظف ===================
const S = { unsub: null, tab: "orders", me: null, offers: [], offUnsub: null };
export function enterStaffMode(user) {
  document.body.classList.add("staff-mode");
  const root = $("staffApp"); root.classList.remove("hidden");
  root.innerHTML = `<div class="pad"><div class="spinner"></div></div>`;
  S.unsub?.();
  S.unsub = onSnapshot(doc(db, "storeStaff", user.uid), async (d) => {
    const me = d.exists() ? d.data() : null;
    if (!me || !me.active || !staffOn()) {
      root.innerHTML = `<div class="staff-off"><div style="font-size:48px">⏸️</div><b>${!me ? "حسابك انحذف" : !me.active ? "حسابك موقوف" : "دخول الموظفين موقف حالياً"}</b>
        <p>تواصل مع صاحب المحل.</p><button class="btn ghost" id="sfOut">تسجيل الخروج</button></div>`;
      $("sfOut").onclick = staffLogout; state.staff = null; return;
    }
    const first = !S.me; S.me = me; state.staff = me;
    if (first) {
      const cu = await import("./catalog-ui.js");
      cu.watchMerchantCatalog(me.storeId, () => { if (S.tab === "stock") drawStock(); });
      S.offUnsub?.();
      if (me.perms.includes("stock")) S.offUnsub = onSnapshot(query(collection(db, "offers"), where("merchantId", "==", me.storeId)), (q) => {
        S.offers = q.docs.map((x) => ({ ...x.data(), id: x.id })); if (S.tab === "stock") drawStock();
      }, (e) => console.warn("staff offers", e));
    }
    drawShell();
  }, () => { root.innerHTML = `<div class="staff-off"><b>تعذّر تحميل حسابك</b><button class="btn ghost" id="sfOut">تسجيل الخروج</button></div>`; $("sfOut").onclick = staffLogout; });
}
export function leaveStaffMode() {
  S.unsub?.(); S.unsub = null; S.offUnsub?.(); S.offUnsub = null; S.me = null; S.offers = []; state.staff = null;
  document.body.classList.remove("staff-mode");
  const root = $("staffApp"); if (root) { root.classList.add("hidden"); root.innerHTML = ""; }
}
async function staffLogout() { leaveStaffMode(); await signOut(auth); location.reload(); }

function drawShell() {
  const me = S.me, root = $("staffApp"), stock = me.perms.includes("stock");
  if (!stock) S.tab = "orders";
  root.innerHTML = `<header class="sf-head"><div class="sf-store"><b>${esc(me.storeName || "المتجر")}</b><small>👤 ${esc(me.name || "موظف " + me.n)} • ${esc(ROLES[me.role]?.t || "")}</small></div>
      <button class="icon-btn" id="sfMenu" aria-label="القائمة">⋮</button></header>
    ${stock ? `<nav class="sf-tabs"><button data-sft="orders" class="${S.tab === "orders" ? "on" : ""}">🧾 الطلبات</button><button data-sft="stock" class="${S.tab === "stock" ? "on" : ""}">🍽️ نفد اليوم</button></nav>` : ""}
    <div id="sfBody" class="sf-body"></div>`;
  root.querySelectorAll("[data-sft]").forEach((b) => b.onclick = () => { S.tab = b.dataset.sft; drawShell(); });
  $("sfMenu").onclick = () => {
    showSheet(`<div class="pad"><div class="section-title">👤 ${esc(me.name || "موظف " + me.n)}</div>
      <button class="btn call" id="sfSound">🔔 تجربة صوت الطلبات</button>
      <button class="btn call" id="sfPin">🔑 غيّر رمزي السري</button>
      <button class="btn ghost" id="sfOut2">تسجيل الخروج</button></div>`);
    $("sfSound").onclick = async () => { const s = await import("./sounds.js"); s.setSound(true); s.play("new"); };
    $("sfOut2").onclick = staffLogout;
    $("sfPin").onclick = changeMyPin;
  };
  if (S.tab === "orders") import("./catalog-ui.js").then((cu) => cu.openStoreOrders({ el: $("sfBody") }));
  else drawStock();
}

async function drawStock() {
  const box = $("sfBody"); if (!box || S.tab !== "stock") return;
  const cu = await import("./catalog-ui.js");
  const items = cu.storeItems().slice().sort((a, b) => (a.section || "").localeCompare(b.section || "") || (a.slot || 0) - (b.slot || 0));
  const now = Date.now();
  const offers = S.offers.filter((o) => o.isApproved && !o.draft && !X.isExpired(o, now)).sort((a, b) => X.ms(b.createdAt) - X.ms(a.createdAt));
  box.innerHTML = `<div class="pad"><div class="section-title">🍽️ نفد اليوم</div><p class="desc tr">اضغط على اللي خلص — يتعلّم «نفد» وما ينطلب لين ترجّعه (العروض ترجع لحالها بكرة).</p>
    ${offers.length ? `<div class="sf-sec">🔥 العروض</div>${offers.map((o) => { const out = X.isSoldOut(o, now); return `<button class="sf-item${out ? " out" : ""}" data-of="${esc(o.id)}">
      <img src="${esc(X.thumb(o))}" alt=""><span class="nm"><b>${esc(o.offerTitle)}</b><small>${esc(X.priceLabel(o))}${out && o.notifyCount ? ` • 🔔 ${o.notifyCount} ينتظرون` : ""}</small></span>
      <span class="st">${out ? "❌ نفد اليوم" : "✅ متوفر"}</span></button>`; }).join("")}` : ""}
    ${items.length ? `<div class="sf-sec">📋 المنيو</div>` : ""}
    ${items.length ? items.map((it) => `<button class="sf-item${it.available === false ? " out" : ""}" data-it="${esc(it.id)}">
      ${it.imageUrl ? `<img src="${esc(X.cld(it.imageUrl, 120))}" alt="">` : `<span class="ph">🍽️</span>`}
      <span class="nm"><b>${esc(it.name)}</b><small>${esc(it.section || "")}</small></span>
      <span class="st">${it.available === false ? "❌ نفد" : "✅ متوفر"}</span></button>`).join("") : offers.length ? "" : `<div class="empty-inline">ما فيه عروض ولا أصناف</div>`}</div>`;
  box.querySelectorAll("[data-of]").forEach((b) => b.onclick = async () => {
    const o = S.offers.find((x) => x.id === b.dataset.of); if (!o) return;
    const out = X.isSoldOut(o); b.disabled = true;
    const midnight = new Date(); midnight.setHours(24, 0, 0, 0);
    try { await updateDoc(doc(db, "offers", o.id), out ? { soldOutUntil: null, notifyCount: 0 } : { soldOutUntil: Timestamp.fromDate(midnight) }); toast(out ? `✅ «${o.offerTitle}» رجع متوفر` : `❌ «${o.offerTitle}» نفد اليوم`); }
    catch (e) { console.error(e); toast("تعذّر التحديث"); b.disabled = false; }
  });
  box.querySelectorAll("[data-it]").forEach((b) => b.onclick = async () => {
    const it = cu.storeItems().find((x) => x.id === b.dataset.it); if (!it) return;
    b.disabled = true;
    try { await updateDoc(doc(db, "items", it.id), { available: it.available === false, updatedAt: serverTimestamp() }); toast(it.available === false ? `✅ ${it.name} رجع متوفر` : `❌ ${it.name} نفد`); }
    catch (e) { console.error(e); toast("تعذّر التحديث"); b.disabled = false; }
  });
}

function changeMyPin() {
  showSheet(`<div class="pad form"><div class="section-title">🔑 رمز سري جديد</div>
    <input id="mpPin" class="input pin" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="new-password" placeholder="الرمز الجديد (6 أرقام)">
    <input id="mpPin2" class="input pin" inputmode="numeric" maxlength="6" dir="ltr" autocomplete="new-password" placeholder="أعد كتابته">
    <button class="btn dir big" id="mpGo">حفظ</button></div>`);
  $("mpGo").onclick = async () => {
    const pin = toEn($("mpPin").value), pin2 = toEn($("mpPin2").value);
    if (!PIN_RE.test(pin)) return toast("الرمز السري لازم 6 أرقام");
    if (pin !== pin2) return toast("الرمزين مو متطابقين");
    try { await updatePassword(auth.currentUser, pin); toast("✅ تغيّر رمزك السري"); hideSheet(); }
    catch (e) { toast(e.code === "auth/requires-recent-login" ? "سجّل خروج وادخل من جديد، بعدها غيّره" : "تعذّر: " + (e.code || e.message), true); }
  };
}
