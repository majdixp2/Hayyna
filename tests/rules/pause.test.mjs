// اختبارات «إيقاف استقبال الطلبات» (storeStatus)
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, serverTimestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const order = (id, storeId) => ({ id, code: "H-AB12", storeId, storeName: "محل", branchId: "", branchName: "", customerUid: "c1", customerName: "محمد", customerPhone: "05",
  items: [{ id: "x", name: "قهوة", kind: "menu", qty: 1, price: 5 }], subtotal: 5, deliveryFee: 0, total: 5, vatRate: 0, vatAmount: 0, vatNumber: "",
  mode: "pickup", location: null, note: "", status: "sent", createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
const st = (paused, by = "own") => ({ paused, pausedAt: serverTimestamp(), by });

await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "config/app"), { catalog: { orderEnabled: true, features: {} } });
  await setDoc(doc(db, "storeStaff/cash"), { storeId: "own", code: "48213", n: 1, perms: ["orders", "stock"], active: true, createdAt: new Date() });
  await setDoc(doc(db, "storeStaff/stk"), { storeId: "own", code: "48213", n: 2, perms: ["stock"], active: true, createdAt: new Date() });
});

console.log("إيقاف الطلبات");
await t("صاحب المحل يوقف الطلبات", () => assertSucceeds(setDoc(doc(as("own"), "storeStatus/own"), st(true))));
await t("الزبون ما يقدر يطلب والمحل موقف", () => assertFails(setDoc(doc(as("c1"), "orders/p1"), order("p1", "own"))));
await t("محل ثاني غير موقف يستقبل", () => assertSucceeds(setDoc(doc(as("c1"), "orders/p2"), order("p2", "oth"))));
await t("موظف الطلبات يرجّع الاستقبال", () => assertSucceeds(setDoc(doc(as("cash"), "storeStatus/own"), st(false, "أحمد"))));
await t("بعد الرجوع الزبون يطلب", () => assertSucceeds(setDoc(doc(as("c1"), "orders/p3"), order("p3", "own"))));
await t("موظف المخزون ما يوقف الطلبات", () => assertFails(setDoc(doc(as("stk"), "storeStatus/own"), st(true))));
await t("أحد غريب ما يوقف محل غيره", () => assertFails(setDoc(doc(as("rnd"), "storeStatus/own"), st(true))));
await t("ما ينكتب حقل غريب", () => assertFails(setDoc(doc(as("own"), "storeStatus/own"), { ...st(true), x: 1 })));

console.log(`\n${pass} نجح، ${fail} فشل`);
await env.cleanup();
process.exit(fail ? 1 : 0);
