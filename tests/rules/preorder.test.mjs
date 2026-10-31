// اختبارات «📅 اطلب مسبقاً»: الإعدادات، الموعد، وحجز فترات التوصيل
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, writeBatch, serverTimestamp, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const H = 3600e3, later = (h) => Timestamp.fromMillis(Date.now() + h * H);
const KEY = "20991231-1900", KEY2 = "20991231-2000";
const order = (id, extra = {}) => ({ id, code: "H-AB12", storeId: "own", storeName: "محل", branchId: "", branchName: "", customerUid: "c1", customerName: "محمد", customerPhone: "05",
  items: [{ id: "x", name: "قهوة", kind: "menu", qty: 1, price: 5 }], subtotal: 5, deliveryFee: 0, total: 5, vatRate: 0, vatAmount: 0, vatNumber: "",
  mode: "pickup", location: null, note: "", status: "sent", createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra });
const cfg = (x = {}) => ({ on: true, maxDays: 2, prepMin: 30, pickup: true, delivery: true, delFrom: -1, delTo: -1, slotMin: 60, slotCap: 1, updatedAt: serverTimestamp(), ...x });
async function book(uid, id, key, n, extra = {}) {
  const db = as(uid), b = writeBatch(db);
  b.set(doc(db, "orders", id), order(id, { customerUid: uid, mode: "delivery", scheduledAt: later(3), slotKey: key, ...extra }));
  b.set(doc(db, "slotCounts", `own_${key}`), { storeId: "own", slotKey: key, n, last: id });
  return b.commit();
}

await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), "config/app"), { catalog: { orderEnabled: true, features: {} } });
});

console.log("الإعدادات");
await t("صاحب المحل يحفظ إعدادات الطلب المسبق", () => assertSucceeds(setDoc(doc(as("own"), "preorderCfg/own"), cfg())));
await t("ما يحفظ إعدادات محل غيره", () => assertFails(setDoc(doc(as("oth"), "preorderCfg/own"), cfg())));
await t("الحد الأقصى للفترة لازم 1 أو أكثر", () => assertFails(setDoc(doc(as("own"), "preorderCfg/own"), cfg({ slotCap: 0 }))));
await t("أبعد موعد ما يتعدى 14 يوم", () => assertFails(setDoc(doc(as("own"), "preorderCfg/own"), cfg({ maxDays: 30 }))));

console.log("الاستلام المسبق");
await t("زبون يطلب استلام بموعد لاحق", () => assertSucceeds(setDoc(doc(as("c1"), "orders/p1"), order("p1", { scheduledAt: later(2) }))));
await t("الموعد ما يكون في الماضي", () => assertFails(setDoc(doc(as("c1"), "orders/p2"), order("p2", { scheduledAt: later(-1) }))));
await t("الاستلام ما يحجز فترة", () => assertFails(setDoc(doc(as("c1"), "orders/p3"), order("p3", { scheduledAt: later(2), slotKey: KEY }))));

console.log("فترات التوصيل");
await t("أول طلب يحجز الفترة", () => assertSucceeds(book("c1", "d1", KEY, 1)));
await t("الفترة امتلت (الحد 1)", () => assertFails(book("c2", "d2", KEY, 2)));
await t("ما يقدر يتجاهل العدد", () => assertFails(book("c2", "d3", KEY, 1)));
await t("توصيل مجدول بدون حجز يترفض", () => assertFails(setDoc(doc(as("c2"), "orders/d4"), order("d4", { customerUid: "c2", mode: "delivery", scheduledAt: later(3), slotKey: KEY2 }))));
await t("فترة ثانية فاضية تنحجز", () => assertSucceeds(book("c2", "d5", KEY2, 1)));
await t("ما ينزّل العدد بدون إلغاء", () => assertFails(setDoc(doc(as("c1"), "slotCounts/own_" + KEY), { storeId: "own", slotKey: KEY, n: 0, last: "d1" })));
await t("الزبون يلغي ويفك الحجز", async () => {
  const db = as("c1"), b = writeBatch(db);
  b.update(doc(db, "orders/d1"), { status: "cancelled", cancelBy: "customer", updatedAt: serverTimestamp() });
  b.set(doc(db, "slotCounts/own_" + KEY), { storeId: "own", slotKey: KEY, n: 0, last: "d1" });
  await assertSucceeds(b.commit());
});
await t("بعد الإلغاء الفترة ترجع متاحة", () => assertSucceeds(book("c2", "d6", KEY, 1)));

console.log("المحل موقف الطلب المسبق");
await t("صاحب المحل يقفل الطلب المسبق", () => assertSucceeds(setDoc(doc(as("own"), "preorderCfg/own"), cfg({ on: false }))));
await t("ما ينقبل موعد والميزة مقفلة", () => assertFails(setDoc(doc(as("c1"), "orders/p9"), order("p9", { scheduledAt: later(2) }))));

console.log("ملاحظة الجاهزية");
await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "orders/r1"), { ...order("r1"), status: "received", createdAt: new Date() }); });
await t("المحل يكتب ملاحظة «جاهز»", () => assertSucceeds(updateDoc(doc(as("own"), "orders/r1"), { status: "ready", readyNote: "عند كاشير رقم 3", updatedAt: serverTimestamp() })));
await t("الملاحظة ما تتعدى 80 حرف", () => assertFails(updateDoc(doc(as("own"), "orders/r1"), { readyNote: "x".repeat(81), updatedAt: serverTimestamp() })));

console.log(`\n${pass} نجح، ${fail} فشل`);
await env.cleanup();
process.exit(fail ? 1 : 0);
