// 📣 مستطيل إعلانات لكوفر (خصم / حدث / ميزة جديدة) — يتحكم فيه الأدمن من لوحة الإدارة.
// فوق الخريطة: سطر واحد يتوسع، و✕ يخفيه لين اليوم الثاني. في «حسابي»: ثابت بدون إغلاق.
import { collection, query, where, onSnapshot, doc, updateDoc, increment, Timestamp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { db, state, $, showSheet, hideSheet, openOffer, openStore, setTab } from "./app.js";
import * as X from "./logic.js";
import { leftText } from "./credits.js";

const A = { list: [], unsub: null };
const esc = X.esc;
const today = () => new Date().toISOString().slice(0, 10);
const en = () => { try { return localStorage.getItem("hayyna_lang") === "en"; } catch { return false; } };
const t = (a, k) => (en() && a[k + "En"]) || a[k] || "";

export function watchAnnouncements(onChange) {
  if (A.unsub) return;
  A.unsub = onSnapshot(query(collection(db, "announcements"), where("endsAt", ">", Timestamp.now())), (s) => {
    A.list = s.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => X.ms(b.createdAt) - X.ms(a.createdAt)); onChange?.();
  }, (e) => console.warn("announcements", e));
}
/** الإعلانات الشغّالة لهذا المستخدم في هذا المكان */
export function activeAnns(place) {
  const now = Date.now(), merchant = state.role === "merchant" || !!state.staff;
  return A.list.filter((a) => a.active !== false && X.ms(a.startsAt) <= now && now < X.ms(a.endsAt) && (a.places || {})[place]
    && (a.audience === "all" || !a.audience || (a.audience === "merchants" ? merchant : !merchant)));
}
const seen = new Set();
function bump(a, k) {
  if (k === "views") { if (seen.has(a.id)) return; seen.add(a.id); try { if (sessionStorage.getItem("ann_v_" + a.id)) return; sessionStorage.setItem("ann_v_" + a.id, "1"); } catch {} }
  updateDoc(doc(db, "announcements", a.id), { [k]: increment(1) }).catch(() => {});
}
const badgeOf = (a) => a.badge || leftText(X.ms(a.endsAt));
/** البطاقة الكاملة (قسمين: الرسالة + شريط الزر) */
export function annCard(a) {
  return `<div class="ann ann-${esc(a.color || "navy")}" data-ann="${esc(a.id)}"><div class="ann-top"><div class="ann-row"><span class="ann-logo">%</span><span class="ann-pill">${esc(badgeOf(a))}</span></div>
    <div class="ann-h">${esc(t(a, "title"))}</div>${t(a, "sub") ? `<div class="ann-s">${esc(t(a, "sub"))}</div>` : ""}</div>
    ${t(a, "cta") && a.action !== "none" ? `<button class="ann-cta" data-anngo="${esc(a.id)}"><i>‹</i>${esc(t(a, "cta"))}</button>` : ""}</div>`;
}
function go(a) {
  bump(a, "clicks");
  const v = a.value || "";
  hideSheet();
  if (a.action === "url" && /^https?:\/\//.test(v)) window.open(v, "_blank", "noopener");
  else if (a.action === "offer" && v) openOffer(v, true);
  else if (a.action === "store" && v) openStore(v, true);
  else if (a.action === "publish") { setTab("account"); import("./merchant.js").then((m) => m.openOfferForm?.(null)).catch(() => {}); }
  else if (a.action === "credits") { const u = state.config?.credits?.promoStoreUrl || state.config?.credits?.storeUrl; if (u) window.open(u, "_blank", "noopener"); else setTab("account"); }
}
export function bindAnn(root) {
  root?.querySelectorAll("[data-anngo]").forEach((b) => b.onclick = (e) => { e.stopPropagation(); const a = A.list.find((x) => x.id === b.dataset.anngo); if (a) go(a); });
}
/** «حسابي»: ثابتة */
export function annAccountHtml() {
  const l = activeAnns("account").slice(0, 3);
  l.forEach((a) => bump(a, "views"));
  return l.length ? `<div class="ann-wrap">${l.map(annCard).join("")}</div>` : "";
}
/** فوق الخريطة: سطر واحد يتوسع، ✕ يخفيه لين بكرة */
export function renderAnnStrip() {
  const el = $("annStrip"); if (!el) return;
  const hid = (a) => { try { return localStorage.getItem("lc_ann_hide_" + a.id) === today(); } catch { return false; } };
  const a = activeAnns("map").find((x) => !hid(x));
  if (!a) { el.classList.add("hidden"); el.innerHTML = ""; return; }
  bump(a, "views");
  el.className = `ann-strip ann-${a.color || "navy"}`;
  el.innerHTML = `<span class="ann-pill">${esc(badgeOf(a))}</span><b>${esc(t(a, "title"))}</b><span class="ann-chev">‹</span><button class="ann-x" aria-label="إغلاق">✕</button>`;
  el.onclick = (e) => {
    if (e.target.closest(".ann-x")) { try { localStorage.setItem("lc_ann_hide_" + a.id, today()); } catch {} renderAnnStrip(); return; }
    showSheet(`<div class="pad">${annCard(a)}</div>`); bindAnn($("sheetBody"));
  };
}
