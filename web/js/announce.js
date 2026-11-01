// 📣 مستطيل إعلانات لكوفر (خصم / حدث / ميزة جديدة) — يتحكم فيه الأدمن من لوحة الإدارة.
// الخريطة: شريط رفيع تحت فوق التنقل (ضغطة = البطاقة، ✕ = لين بكرة). «حسابي»: بنرات داخل الصفحة تتقلّب.
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
/** اللون: جاهز (navy/gold/green) أو كود حر #RRGGBB — الشريط الأغمق ولون الكتابة ينحسبون لحالهم */
export function annColor(c) {
  if (!/^#[0-9a-f]{6}$/i.test(c || "")) return { cls: `ann-${esc(c || "navy")}`, style: "" };
  const n = parseInt(c.slice(1), 16), r = n >> 16, g = (n >> 8) & 255, b = n & 255;
  const dk = "#" + [r, g, b].map((v) => Math.round(v * 0.82).toString(16).padStart(2, "0")).join("");
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return { cls: "ann-custom", style: `--ab:${c};--ad:${dk};--af:${lum > 0.6 ? "#111827" : "#fff"}` };
}
/** البطاقة الكاملة (قسمين: الرسالة + شريط الزر) */
export function annCard(a) {
  const k = annColor(a.color);
  return `<div class="ann ${k.cls}" style="${k.style}" data-ann="${esc(a.id)}"><div class="ann-top"><div class="ann-row"><span class="ann-logo">%</span><span class="ann-pill">${esc(badgeOf(a))}</span></div>
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
  bindCarousel(root);
  root?.querySelectorAll("[data-anngo]").forEach((b) => b.onclick = (e) => { e.stopPropagation(); const a = A.list.find((x) => x.id === b.dataset.anngo); if (a) go(a); });
}
/** «حسابي»: داخل الصفحة (تتمرر معها). أكثر من إعلان = بنرات تتقلّب كل 4 ثواني + نقاط + سحب */
export function annAccountHtml() {
  const l = activeAnns("account").slice(0, 5);
  l.forEach((a) => bump(a, "views"));
  if (!l.length) return "";
  return `<div class="ann-car"><div class="ann-track">${l.map(annCard).join("")}</div>${l.length > 1 ? `<div class="ann-dots">${l.map((_, i) => `<i class="${i ? "" : "on"}"></i>`).join("")}</div>` : ""}</div>`;
}
let carTimer = 0;
function bindCarousel(root) {
  clearInterval(carTimer);
  const tr = root?.querySelector(".ann-track"), dots = root?.querySelectorAll(".ann-dots i");
  if (!tr || !dots?.length) return;
  const cards = [...tr.children];
  const cur = () => { const x = Math.abs(tr.scrollLeft); let best = 0; cards.forEach((c, i) => { if (Math.abs(Math.abs(c.offsetLeft - cards[0].offsetLeft) - x) < Math.abs(Math.abs(cards[best].offsetLeft - cards[0].offsetLeft) - x)) best = i; }); return best; };
  const mark = () => { const i = cur(); dots.forEach((d, j) => d.classList.toggle("on", i === j)); };
  tr.addEventListener("scroll", () => requestAnimationFrame(mark), { passive: true });
  let hold = 0; tr.addEventListener("pointerdown", () => { hold = Date.now(); }, { passive: true });
  carTimer = setInterval(() => {
    if (!tr.isConnected) return clearInterval(carTimer);
    if (Date.now() - hold < 6000 || tr.offsetParent === null) return;
    const n = (cur() + 1) % cards.length;
    tr.scrollTo({ left: cards[n].offsetLeft - cards[0].offsetLeft, behavior: "smooth" });
  }, 4000);
}
/** الخريطة: شريط رفيع تحت، ✕ يخفيه لين بكرة */
export function renderAnnStrip() {
  const el = $("annStrip"); if (!el) return;
  const setOn = (on) => $("tab-map")?.classList.toggle("ann-on", on);
  const hid = (a) => { try { return localStorage.getItem("lc_ann_hide_" + a.id) === today(); } catch { return false; } };
  const a = activeAnns("map").find((x) => !hid(x));
  if (!a) { el.classList.add("hidden"); el.innerHTML = ""; setOn(false); return; }
  bump(a, "views"); setOn(true);
  const k = annColor(a.color);
  el.className = `ann-strip ${k.cls}`; el.setAttribute("style", k.style);
  el.innerHTML = `<span class="ann-pill">${esc(badgeOf(a))}</span><b>${esc(t(a, "title"))}</b><span class="ann-chev">‹</span><button class="ann-x" aria-label="إغلاق">✕</button>`;
  el.onclick = (e) => {
    if (e.target.closest(".ann-x")) { try { localStorage.setItem("lc_ann_hide_" + a.id, today()); } catch {} renderAnnStrip(); return; }
    showSheet(`<div class="pad">${annCard(a)}</div>`); bindAnn($("sheetBody"));
  };
}
