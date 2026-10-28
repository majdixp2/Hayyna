// اختبارات العروض مع المنيو: نوع العرض (deal) + الخصم السريع + خصم الطلب.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDoc, deleteDoc, serverTimestamp, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
const DAY = 864e5;
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const offer = (id, extra = {}) => ({
  id, merchantId: "m1", restaurantName: "مطعم", offerTitle: "عرض " + id, description: "", cuisineType: "مطاعم", price: 10, currency: "ر.س",
  mediaType: "IMAGE", mediaUri: "https://x/y.jpg", district: "حي", region: "الرياض", latitude: 24.7, longitude: 46.7, phone: "05",
  isApproved: true, isFeatured: false, viewsCount: 0, directionsCount: 0, whatsappCount: 0, reportsCount: 0, createdAt: serverTimestamp(), ...extra,
});
async function seed(features = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "config/app"), { catalog: { enabled: true, orderEnabled: true, features } });
    await setDoc(doc(db, "merchantStatus/m1"), { trusted: true });
  });
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const put = (id, deal) => setDoc(doc(as("m1"), "offers", id), offer(id, { deal }));
const quick = (extra = {}) => ({ merchantId: "m1", pct: 20, scope: "all", kind: "", section: "", itemIds: [], endsAt: Timestamp.fromMillis(Date.now() + DAY), updatedAt: serverTimestamp(), ...extra });

console.log("نوع العرض");
await seed();
await t("خصم على كل المنيو", () => assertSucceeds(put("a", { type: "discount", scope: "all", kind: "", section: "", itemIds: [] })));
await t("خصم على أصناف مختارة", () => assertSucceeds(put("b", { type: "discount", scope: "items", itemIds: ["m1_menu_0", "m1_menu_1"] })));
await t("باقة مربوطة بالمنيو", () => assertSucceeds(put("c", { type: "bundle", itemIds: ["m1_menu_0"] })));
await t("1+1 على صنف", () => assertSucceeds(put("d", { type: "bogo", itemId: "m1_menu_0", getPct: 100 })));
await t("الثاني بنص السعر", () => assertSucceeds(put("e", { type: "bogo", itemId: "m1_menu_0", getPct: 50 })));
await t("إعلان قابل للطلب", () => assertSucceeds(put("f", { type: "promo", orderable: true })));
await t("عرض بدون نوع (القديم) ✓", () => assertSucceeds(setDoc(doc(as("m1"), "offers", "g"), offer("g"))));
await t("deal = null ✓", () => assertSucceeds(put("h", null)));
await t("نوع غريب يُرفض", () => assertFails(put("x1", { type: "free" })));
await t("1+1 بدون صنف يُرفض", () => assertFails(put("x2", { type: "bogo", getPct: 100 })));
await t("نسبة 1+1 غريبة (30) تُرفض", () => assertFails(put("x3", { type: "bogo", itemId: "a", getPct: 30 })));
await t("حقل زايد في deal يُرفض", () => assertFails(put("x4", { type: "bundle", price: 1 })));
await t("نطاق غريب يُرفض", () => assertFails(put("x5", { type: "discount", scope: "store" })));
await t("التاجر يغيّر نوع عرضه", () => assertSucceeds(updateDoc(doc(as("m1"), "offers/a"), { deal: { type: "bundle", itemIds: [] } })));

console.log("تعديل العرض من التطبيق");
await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), "offers/a"), { viewsCount: 22, isFeatured: false }));
const full = (extra = {}) => { const o = offer("a", { deal: { type: "promo", orderable: false }, ...extra }); delete o.createdAt; return o; };
await t("حفظ كامل بعدّادات قديمة يُرفض (السبب القديم)", () => assertFails(setDoc(doc(as("m1"), "offers/a"), { ...full(), viewsCount: 21, createdAt: Timestamp.now() })));
await t("حفظ بدمج بدون العدّادات ينجح ✓", () => { const o = full(); ["merchantId", "isFeatured", "viewsCount", "directionsCount", "whatsappCount", "reportsCount"].forEach((k) => delete o[k]); return assertSucceeds(setDoc(doc(as("m1"), "offers/a"), o, { merge: true })); });

console.log("⚡ الخصم السريع");
await seed();
await t("التاجر يشغّل خصم 20% يوم", () => assertSucceeds(setDoc(doc(as("m1"), "quickDeals/m1"), quick())));
await t("الزائر يقرأ الخصم", () => assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "quickDeals/m1"))));
await t("خصم 95% يُرفض", () => assertFails(setDoc(doc(as("m1"), "quickDeals/m1"), quick({ pct: 95 }))));
await t("مدة فوق شهر تُرفض", () => assertFails(setDoc(doc(as("m1"), "quickDeals/m1"), quick({ endsAt: Timestamp.fromMillis(Date.now() + 40 * DAY) }))));
await t("خصم لتاجر ثاني يُرفض", () => assertFails(setDoc(doc(as("m2"), "quickDeals/m1"), quick())));
await t("حقل زايد يُرفض", () => assertFails(setDoc(doc(as("m1"), "quickDeals/m1"), quick({ extra: 1 }))));
await t("التاجر يوقف الخصم", () => assertSucceeds(deleteDoc(doc(as("m1"), "quickDeals/m1"))));
await seed({ quickDeal: false });
await t("الأدمن موقّف الخصم السريع: يُرفض", () => assertFails(setDoc(doc(as("m1"), "quickDeals/m1"), quick())));

console.log("الطلب بخصم");
await seed();
const order = (extra = {}) => ({ id: "o1", code: "H-AB12", storeId: "m1", storeName: "محل", branchId: "", branchName: "", customerUid: "c1", customerName: "محمد", customerPhone: "05",
  items: [{ id: "m1_menu_0", name: "شاورما", kind: "menu", qty: 2, price: 18.4, was: 23, off: 18.4, bogo: "1+1 مجاناً" }], subtotal: 18.4, discount: 27.6, deliveryFee: 0, total: 18.4,
  vatRate: 0, vatAmount: 0, vatNumber: "", mode: "pickup", location: null, note: "", status: "sent", createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra });
await t("طلب فيه خصم العروض", () => assertSucceeds(setDoc(doc(as("c1"), "orders/o1"), order())));
await t("خصم سالب يُرفض", () => assertFails(setDoc(doc(as("c1"), "orders/o2"), order({ id: "o2", discount: -5 }))));

console.log(`\n${pass} نجح، ${fail} فشل`);
await env.cleanup();
process.exit(fail ? 1 : 0);
