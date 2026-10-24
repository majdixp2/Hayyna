// اختبارات قواعد رصيد العروض على محاكي Firestore الرسمي (تشتغل في GitHub Actions).
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, writeBatch, serverTimestamp, Timestamp, collection } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
const DAY = 864e5;
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
async function seed() {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "config/app"), { credits: { enabled: true, welcome: 30, items: {
      publish: { name: "نشر", effect: "publish", unit: "day", price: 3, days: 1, active: true },
      feature: { name: "تمييز", effect: "feature", unit: "fixed", price: 10, days: 3, active: true },
      off: { name: "موقفة", effect: "listTop", unit: "fixed", price: 40, days: 7, active: false } } } });
    await setDoc(doc(db, "creditCodes/HY-AAAA-1111"), { points: 100, batch: "t", redeemedBy: null, redeemedAt: null, expiresAt: null });
    await setDoc(doc(db, "creditCodes/HY-USED-2222"), { points: 100, batch: "t", redeemedBy: "other", redeemedAt: Timestamp.now(), expiresAt: null });
    await setDoc(doc(db, "creditCodes/HY-OLDX-3333"), { points: 100, batch: "t", redeemedBy: null, redeemedAt: null, expiresAt: Timestamp.fromMillis(Date.now() - DAY) });
    await setDoc(doc(db, "offers/o1"), { merchantId: "m1", offerTitle: "x" });
    await setDoc(doc(db, "offers/o2"), { merchantId: "m2", offerTitle: "y" });
  });
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const redeem = (db, uid, code, newBal) => {
  const wb = writeBatch(db);
  wb.update(doc(db, "creditCodes", code), { redeemedBy: uid, redeemedAt: serverTimestamp() });
  wb.set(doc(db, "wallets", uid), { balance: newBal, lastOp: { kind: "redeem", ref: code }, updatedAt: serverTimestamp() });
  return wb.commit();
};
const spend = (db, uid, { item = "publish", effect = "publish", offerId = "o1", days = 7, cost = 21, bal, startsIn = 0, len }) => {
  const wb = writeBatch(db), ref = doc(collection(db, "boosts")), s = Date.now() + startsIn;
  wb.set(ref, { id: ref.id, merchantId: uid, offerId, item, effect, days, cost, createdAt: serverTimestamp(), startsAt: Timestamp.fromMillis(s), endsAt: Timestamp.fromMillis(s + (len ?? days) * DAY) });
  if (bal !== undefined) wb.set(doc(db, "wallets", uid), { balance: bal, lastOp: { kind: "spend", ref: ref.id }, updatedAt: serverTimestamp() });
  return wb.commit();
};

console.log("شحن الرصيد بالأكواد");
await seed();
await t("كود صحيح يشحن المحفظة (أول مرة)", () => assertSucceeds(redeem(as("m1"), "m1", "HY-AAAA-1111", 100)));
await t("نفس الكود مرة ثانية يُرفض", () => assertFails(redeem(as("m1"), "m1", "HY-AAAA-1111", 200)));
await t("كود مستخدم من شخص ثاني يُرفض", () => assertFails(redeem(as("m1"), "m1", "HY-USED-2222", 200)));
await t("كود منتهي يُرفض", () => assertFails(redeem(as("m1"), "m1", "HY-OLDX-3333", 200)));
await seed();
await t("رصيد أكبر من قيمة الكود يُرفض", () => assertFails(redeem(as("m1"), "m1", "HY-AAAA-1111", 1000)));
await t("تعديل الكود بدون المحفظة يُرفض", () => assertFails(setDoc(doc(as("m1"), "creditCodes/HY-AAAA-1111"), { points: 100, batch: "t", redeemedBy: "m1", redeemedAt: serverTimestamp(), expiresAt: null })));
await t("قائمة الأكواد ممنوعة على التاجر", async () => { const { getDocs } = await import("firebase/firestore"); await assertFails(getDocs(collection(as("m1"), "creditCodes"))); });

console.log("المحفظة");
await seed();
await t("محفظة ترحيب بالقيمة الصحيحة", () => assertSucceeds(setDoc(doc(as("m3"), "wallets/m3"), { balance: 30, lastOp: { kind: "welcome", ref: "" }, updatedAt: serverTimestamp() })));
await t("محفظة ترحيب بقيمة أكبر تُرفض", () => assertFails(setDoc(doc(as("m4"), "wallets/m4"), { balance: 999, lastOp: { kind: "welcome", ref: "" }, updatedAt: serverTimestamp() })));
await t("رفع الرصيد يدوياً يُرفض", () => assertFails(setDoc(doc(as("m3"), "wallets/m3"), { balance: 500, lastOp: { kind: "spend", ref: "zz" }, updatedAt: serverTimestamp() })));
await t("قراءة محفظة شخص ثاني تُرفض", () => assertFails(getDoc(doc(as("m1"), "wallets/m3"))));

console.log("صرف الرصيد على الخدمات");
await seed();
await redeem(as("m1"), "m1", "HY-AAAA-1111", 100);
await t("نشر 7 أيام = 21 نقطة مع خصم صحيح", () => assertSucceeds(spend(as("m1"), "m1", { bal: 79 })));
await t("خصم أقل من السعر يُرفض", () => assertFails(spend(as("m1"), "m1", { bal: 70, cost: 9 })));
await t("خدمة بدون خصم من المحفظة تُرفض", () => assertFails(spend(as("m1"), "m1", {})));
await t("رصيد المحفظة ما يطابق التكلفة يُرفض", () => assertFails(spend(as("m1"), "m1", { bal: 79 })));
await t("تمييز ثابت 10 نقاط / 3 أيام", () => assertSucceeds(spend(as("m1"), "m1", { item: "feature", effect: "feature", days: 3, cost: 10, bal: 69 })));
await t("تمييز بأيام أكثر من المحدد يُرفض", () => assertFails(spend(as("m1"), "m1", { item: "feature", effect: "feature", days: 30, cost: 10, bal: 59 })));
await t("مدة أطول من المدفوع تُرفض", () => assertFails(spend(as("m1"), "m1", { days: 1, cost: 3, bal: 66, len: 30 })));
await t("خدمة موقفة تُرفض", () => assertFails(spend(as("m1"), "m1", { item: "off", effect: "listTop", days: 7, cost: 40, bal: 29 })));
await t("خدمة على عرض تاجر ثاني تُرفض", () => assertFails(spend(as("m1"), "m1", { offerId: "o2", days: 1, cost: 3, bal: 66 })));
await t("رصيد بالسالب يُرفض", () => assertFails(spend(as("m1"), "m1", { days: 30, cost: 90, bal: -21 })));
await t("الزائر يقرأ الخدمات النشطة (للخريطة)", () => assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "boosts/any"))));

await env.cleanup();
console.log(`\n${pass} نجح، ${fail} فشل`);
console.log(`::notice title=rules tests::${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
