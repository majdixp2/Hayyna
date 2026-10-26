// اختبارات المنتجات/الخدمات/المنيو + ظهورها المدفوع + الطلبات + الرقم الضريبي.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDoc, writeBatch, serverTimestamp, Timestamp, collection } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
const DAY = 864e5;
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
async function seed(catalog = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "config/app"), { catalog: { enabled: true, orderEnabled: true, review: true, limits: { menu: 30, product: 2, service: 0 },
      pricing: { enabled: true, menu: { priceH: 50, active: true }, product: { priceH: 50, active: true }, service: { priceH: 100, active: false }, all: { priceH: 300, active: true } }, ...catalog } });
    await setDoc(doc(db, "merchantStatus/m1"), { trusted: true });
    await setDoc(doc(db, "merchantStatus/m3"), { verified: true });
    await setDoc(doc(db, "wallets/m1"), { balance: 100, lastOp: { kind: "welcome", ref: "" }, updatedAt: Timestamp.now() });
  });
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const item = (uid, kind, slot, extra = {}) => ({ id: `${uid}_${kind}_${slot}`, merchantId: uid, kind, slot, name: "شاورما", desc: "", price: 23, imageUrl: "", section: "",
  available: true, approved: true, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra });
const putItem = (uid, kind, slot, extra) => setDoc(doc(as(uid), "items", `${uid}_${kind}_${slot}`), item(uid, kind, slot, extra));
const buy = (db, { scope = "kind", kind = "menu", itemIds = ["a", "b", "c"], days = 7, cost, bal }) => {
  const wb = writeBatch(db), ref = doc(collection(db, "itemPasses")), s = Date.now();
  wb.set(ref, { id: ref.id, merchantId: "m1", scope, kind, itemIds, days, cost, createdAt: serverTimestamp(), startsAt: Timestamp.fromMillis(s), endsAt: Timestamp.fromMillis(s + days * DAY) });
  if (bal !== undefined) wb.set(doc(db, "wallets", "m1"), { balance: bal, lastOp: { kind: "catalog", ref: ref.id }, updatedAt: serverTimestamp() });
  return wb.commit();
};

console.log("المنتجات");
await seed();
await t("تاجر موثوق ينشر منتج مباشرة", () => assertSucceeds(putItem("m1", "menu", 0)));
await t("تاجر موثّق ✔ ينشر مباشرة", () => assertSucceeds(putItem("m3", "product", 0)));
await t("تاجر جديد: منتج معتمد مباشرة يُرفض", () => assertFails(putItem("m2", "menu", 0)));
await t("تاجر جديد: منتج للمراجعة مقبول", () => assertSucceeds(putItem("m2", "menu", 0, { approved: false })));
await t("فوق الحد (منتجات 2) يُرفض", () => assertFails(putItem("m1", "product", 2)));
await t("الخدمات مفتوحة بدون حد", () => assertSucceeds(putItem("m1", "service", 150)));
await t("رقم خانة ما يطابق المعرّف يُرفض", () => assertFails(setDoc(doc(as("m1"), "items", "m1_menu_9"), item("m1", "menu", 1))));
await t("منتج باسم تاجر ثاني يُرفض", () => assertFails(setDoc(doc(as("m2"), "items", "m1_menu_5"), item("m1", "menu", 5, { approved: false }))));
await t("الزائر يشوف المنتج المعتمد", () => assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "items/m1_menu_0"))));
await t("الزائر ما يشوف اللي بانتظار المراجعة", () => assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "items/m2_menu_0"))));
await seed({ enabled: false });
await t("الخدمة مطفية: الإضافة تُرفض", () => assertFails(putItem("m1", "menu", 0)));

console.log("ظهور المنتجات المدفوع");
await seed();
await t("3 منيو × 7 أيام × نص ريال = 11 نقطة", () => assertSucceeds(buy(as("m1"), { cost: 11, bal: 89 })));
await t("تكلفة أقل (10) تُرفض", () => assertFails(buy(as("m1"), { cost: 10, bal: 79 })));
await t("باقة كل المنتجات 7 أيام = 21", () => assertSucceeds(buy(as("m1"), { scope: "all", kind: "", itemIds: [], cost: 21, bal: 68 })));
await t("نوع موقف (الخدمات) يُرفض", () => assertFails(buy(as("m1"), { kind: "service", itemIds: ["x"], days: 1, cost: 1, bal: 67 })));
await t("بدون خصم من المحفظة يُرفض", () => assertFails(buy(as("m1"), { cost: 11 })));
await seed();
await t("سنة كاملة: منتج × 365 يوم = 183 نقطة", async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), "wallets/m1"), { balance: 200, lastOp: { kind: "welcome", ref: "" }, updatedAt: Timestamp.now() }));
  await assertSucceeds(buy(as("m1"), { itemIds: ["a"], days: 365, cost: 183, bal: 17 }));
});

console.log("الطلبات");
await seed();
const order = (uid, extra = {}) => ({ id: "o1", code: "H-AB12", storeId: "m1", storeName: "محل", branchId: "", branchName: "", customerUid: uid, customerName: "محمد", customerPhone: "05",
  items: [{ id: "m1_menu_0", name: "شاورما", kind: "menu", qty: 2, price: 23 }], subtotal: 46, deliveryFee: 0, total: 46, vatRate: 15, vatAmount: 6, vatNumber: "",
  mode: "pickup", location: null, note: "", status: "sent", createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra });
await t("الزبون يحفظ طلبه", () => assertSucceeds(setDoc(doc(as("c1"), "orders/o1"), order("c1"))));
await t("طلب باسم شخص ثاني يُرفض", () => assertFails(setDoc(doc(as("c2"), "orders/o2"), order("c1", { id: "o2" }))));
await t("غريب ما يقرأ الطلب", () => assertFails(getDoc(doc(as("c9"), "orders/o1"))));
await t("المحل يقرأ طلبه", () => assertSucceeds(getDoc(doc(as("m1"), "orders/o1"))));
await t("المحل يحدّث الحالة", () => assertSucceeds(updateDoc(doc(as("m1"), "orders/o1"), { status: "received", updatedAt: serverTimestamp() })));
await t("المحل ما يغيّر الإجمالي", () => assertFails(updateDoc(doc(as("m1"), "orders/o1"), { total: 1 })));
await t("الزبون ما يلغي بعد ما استلمه المحل", () => assertFails(updateDoc(doc(as("c1"), "orders/o1"), { status: "cancelled", updatedAt: serverTimestamp() })));
await t("المحل: جاري التوصيل مع وقت تقريبي", () => assertSucceeds(updateDoc(doc(as("m1"), "orders/o1"), { status: "delivering", etaAt: Timestamp.now(), updatedAt: serverTimestamp() })));
const rate = (uid, extra = {}) => setDoc(doc(as(uid), "ratings/o1"), { orderId: "o1", storeId: "m1", uid, name: "محمد", stars: 5, text: "ممتاز", at: serverTimestamp(), ...extra });
await t("ما يقيّم قبل ما يخلص الطلب", () => assertFails(rate("c1")));
await updateDoc(doc(as("m1"), "orders/o1"), { status: "done", updatedAt: serverTimestamp() });
await t("الزبون يقيّم بعد ما يخلص", () => assertSucceeds(rate("c1")));
await t("غير صاحب الطلب ما يقيّم", () => assertFails(setDoc(doc(as("c2"), "ratings/o1"), { orderId: "o1", storeId: "m1", uid: "c2", name: "x", stars: 1, text: "", at: serverTimestamp() })));
await t("نجوم أكثر من 5 تُرفض", async () => { await env.withSecurityRulesDisabled((c) => import("firebase/firestore").then(({ deleteDoc }) => deleteDoc(doc(c.firestore(), "ratings/o1")))); await assertFails(rate("c1", { stars: 9 })); });
await t("الزائر يقرأ التقييمات", () => assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "ratings/o1"))));
await seed({ orderEnabled: false });
await t("الطلب مطفي: الحفظ يُرفض", () => assertFails(setDoc(doc(as("c1"), "orders/o1"), order("c1"))));

console.log("الرقم الضريبي");
await seed();
const store = (vat) => ({ name: "محل", about: "", menuImages: [], branches: [], approved: false, vatNumber: vat });
await t("رقم ضريبي صحيح", () => assertSucceeds(setDoc(doc(as("m2"), "stores/m2"), store("300000000000003"))));
await t("رقم ضريبي غلط يُرفض", () => assertFails(setDoc(doc(as("m2"), "stores/m2"), store("12345"))));
await t("بدون رقم ضريبي (غير مسجّل)", () => assertSucceeds(setDoc(doc(as("m2"), "stores/m2"), store(""))));

await env.cleanup();
console.log(`\n${pass} نجح، ${fail} فشل`);
console.log(`::notice title=catalog tests::${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
