// اختبارات: طلب الإلغاء، إلغاء المحل بسبب، «ما انستلم» + عدّاد العميل والإيقاف، الاستلام من السيارة (وين واقف / وصف السيارة).
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDoc, writeBatch, serverTimestamp, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
const DAY = 864e5;
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const order = (id, extra = {}) => ({ id, code: "H-AB12", storeId: "s1", storeName: "محل", branchId: "", branchName: "", customerUid: "c1", customerName: "محمد", customerPhone: "05",
  items: [{ id: "x", name: "قهوة", kind: "menu", qty: 1, price: 5 }], subtotal: 5, deliveryFee: 0, total: 5, vatRate: 0, vatAmount: 0, vatNumber: "",
  mode: "pickup", location: null, note: "", status: "sent", createdAt: Timestamp.now(), updatedAt: Timestamp.now(), ...extra });
async function seed(stats, noshow = {}) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (c) => {
    const db = c.firestore();
    await setDoc(doc(db, "config/app"), { catalog: { enabled: true, orderEnabled: true, noshow: { max: 2, days: 60, blockDays: 7, ...noshow } } });
    for (const [id, st, extra] of [["o1", "sent"], ["o2", "received"], ["o3", "ready"], ["o4", "ready", { mode: "curbside", car: "كامري" }], ["o5", "done"], ["o6", "sent", { mode: "curbside", car: "" }]])
      await setDoc(doc(db, "orders", id), order(id, { status: st, ...(extra || {}) }));
    if (stats) await setDoc(doc(db, "custStats", "c1"), stats);
  });
}
const markNoshow = (id, stats) => {
  const db = as("s1"), wb = writeBatch(db);
  wb.update(doc(db, "orders", id), { status: "noshow", updatedAt: serverTimestamp() });
  wb.set(doc(db, "noshows", id), { orderId: id, customerUid: "c1", storeId: "s1", at: serverTimestamp() });
  wb.set(doc(db, "custStats", "c1"), { ...stats, lastOrder: id, updatedAt: serverTimestamp() });
  return wb.commit();
};

console.log("طلب الإلغاء");
await seed();
await t("العميل يلغي طلب مرسل مباشرة", () => assertSucceeds(updateDoc(doc(as("c1"), "orders/o1"), { status: "cancelled", cancelBy: "customer", updatedAt: serverTimestamp() })));
await t("العميل ما يلغي طلب استلمه المحل مباشرة", () => assertFails(updateDoc(doc(as("c1"), "orders/o2"), { status: "cancelled", updatedAt: serverTimestamp() })));
await t("العميل يرسل «طلب إلغاء» بسبب", () => assertSucceeds(updateDoc(doc(as("c1"), "orders/o2"), { cancelReq: "تأخرت", cancelReqAt: serverTimestamp(), updatedAt: serverTimestamp() })));
await t("طلب إلغاء ثاني يُرفض", () => assertFails(updateDoc(doc(as("c1"), "orders/o2"), { cancelReq: "مرة ثانية", cancelReqAt: serverTimestamp(), updatedAt: serverTimestamp() })));
await t("المحل يوافق على الإلغاء", () => assertSucceeds(updateDoc(doc(as("s1"), "orders/o2"), { status: "cancelled", cancelReqAns: "approved", cancelBy: "customer", updatedAt: serverTimestamp() })));
await t("المحل يلغي طلب جاهز بسبب", () => assertSucceeds(updateDoc(doc(as("s1"), "orders/o3"), { status: "cancelled", cancelBy: "store", cancelReason: "نفدت الكمية", updatedAt: serverTimestamp() })));
await t("المحل ما يرجّع طلب انتهى", () => assertFails(updateDoc(doc(as("s1"), "orders/o5"), { status: "ready", updatedAt: serverTimestamp() })));
await t("«ما انستلم» لطلب مرسل يُرفض", () => assertFails(updateDoc(doc(as("s1"), "orders/o1"), { status: "noshow", updatedAt: serverTimestamp() })));

console.log("🚗 الاستلام من السيارة");
await seed();
await t("العميل يعدّل وصف سيارته ووين واقف", () => assertSucceeds(updateDoc(doc(as("c1"), "orders/o4"), { car: "كامري بيضاء", spot: "عند الباب الجانبي", updatedAt: serverTimestamp() })));
await t("العميل يضغط وصلت (جاهز)", () => assertSucceeds(updateDoc(doc(as("c1"), "orders/o4"), { arrivedAt: serverTimestamp(), spot: "قدام المحل", updatedAt: serverTimestamp() })));
await t("وصلت قبل ما يستلمه المحل يُرفض", () => assertFails(updateDoc(doc(as("c1"), "orders/o6"), { arrivedAt: serverTimestamp(), updatedAt: serverTimestamp() })));
await t("وصف سيارة طويل يُرفض", () => assertFails(updateDoc(doc(as("c1"), "orders/o4"), { car: "x".repeat(70), updatedAt: serverTimestamp() })));

console.log("🚫 ما انستلم");
await seed();
await t("المحل يحط «ما انستلم» + أول عدّاد", () => assertSucceeds(markNoshow("o3", { count: 1, windowStart: serverTimestamp() })));
await t("نفس الطلب مرة ثانية ما ينحسب", () => assertFails(markNoshow("o3", { count: 2, windowStart: Timestamp.now() })));
await t("العميل يقرأ عدّاده؟ أي مسجّل يقرأ العدد", () => assertSucceeds(getDoc(doc(as("s9"), "custStats/c1"))));
await t("الثاني يوصل للحد (2) ويتوقف", async () => {
  let st; await env.withSecurityRulesDisabled(async (c) => { st = (await getDoc(doc(c.firestore(), "custStats/c1"))).data(); });
  await assertSucceeds(markNoshow("o2", { count: 2, windowStart: st.windowStart, blockedAt: serverTimestamp() }));
});
await t("العميل الموقوف ما يقدر يطلب", () => assertFails(setDoc(doc(as("c1"), "orders/n1"), order("n1", { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }))));
await t("عميل ثاني يطلب عادي", () => assertSucceeds(setDoc(doc(as("c2"), "orders/n2"), order("n2", { customerUid: "c2", createdAt: serverTimestamp(), updatedAt: serverTimestamp() }))));
await seed({ count: 2, windowStart: Timestamp.fromMillis(Date.now() - 20 * DAY), blockedAt: Timestamp.fromMillis(Date.now() - 10 * DAY), lastOrder: "old", updatedAt: Timestamp.now() });
await t("بعد مدة الإيقاف يطلب عادي", () => assertSucceeds(setDoc(doc(as("c1"), "orders/n3"), order("n3", { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }))));
await t("الوصول للحد بدون إيقاف يُرفض", () => assertFails(markNoshow("o3", { count: 3, windowStart: Timestamp.fromMillis(Date.now() - 20 * DAY), blockedAt: Timestamp.fromMillis(Date.now() - 10 * DAY) })));
await seed({ count: 1, windowStart: Timestamp.fromMillis(Date.now() - 90 * DAY), lastOrder: "old", updatedAt: Timestamp.now() });
await t("بعد نافذة الأيام يبدأ العدّاد من جديد", () => assertSucceeds(markNoshow("o3", { count: 1, windowStart: serverTimestamp() })));
await seed();
await t("محل ثاني ما يحط عدّاد على عميل مو عنده", () => assertFails((() => { const db = as("s2"), wb = writeBatch(db);
  wb.set(doc(db, "noshows", "o3"), { orderId: "o3", customerUid: "c1", storeId: "s2", at: serverTimestamp() });
  wb.set(doc(db, "custStats", "c1"), { count: 1, windowStart: serverTimestamp(), lastOrder: "o3", updatedAt: serverTimestamp() }); return wb.commit(); })()));
await t("العميل ما يصفّر عدّاده", async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "custStats/c1"), { count: 2, windowStart: Timestamp.now(), lastOrder: "x", updatedAt: Timestamp.now() }));
  await assertFails(updateDoc(doc(as("c1"), "custStats/c1"), { count: 0 }));
});

console.log(`\n${pass} نجح، ${fail} فشل`);
console.log(`::notice title=orders2 tests::${pass} passed, ${fail} failed`);
await env.cleanup();
process.exit(fail ? 1 : 0);
