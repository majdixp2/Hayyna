// اختبارات مكافأة الحصري (خصم النشر للعروض الحصرية) + المسودات.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, writeBatch, serverTimestamp, Timestamp, collection } from "firebase/firestore";
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
  isApproved: true, isFeatured: false, viewsCount: 0, directionsCount: 0, whatsappCount: 0, reportsCount: 0, createdAt: Timestamp.now(), ...extra,
});
async function seed(reward) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "config/app"), {
      credits: { enabled: true, items: {
        publish: { name: "نشر", effect: "publish", unit: "day", price: 3, days: 1, active: true },
        feature: { name: "تمييز", effect: "feature", unit: "fixed", price: 10, days: 3, active: true } } },
      exclusive: { enabled: true, ...(reward ? { reward } : {}) },
    });
    await setDoc(doc(db, "offers/oX"), offer("oX", { exclusive: true }));
    await setDoc(doc(db, "offers/oN"), offer("oN", { exclusive: false }));
    await setDoc(doc(db, "offers/oD"), offer("oD", { exclusive: false, draft: true }));
    await setDoc(doc(db, "wallets/m1"), { balance: 100, lastOp: { kind: "welcome", ref: "" }, updatedAt: Timestamp.now() });
  });
}
const db1 = () => env.authenticatedContext("m1").firestore();
const spend = (db, { item = "publish", effect = "publish", offerId = "oX", days = 7, cost, disc, bal, offerUpdate }) => {
  const wb = writeBatch(db), ref = doc(collection(db, "boosts")), s = Date.now();
  if (offerUpdate) wb.update(doc(db, "offers", offerId), offerUpdate);
  wb.set(ref, { id: ref.id, merchantId: "m1", offerId, item, effect, days, cost, ...(disc !== undefined ? { disc } : {}), createdAt: serverTimestamp(),
    startsAt: Timestamp.fromMillis(s), endsAt: Timestamp.fromMillis(s + days * DAY) });
  if (bal !== undefined) wb.set(doc(db, "wallets", "m1"), { balance: bal, lastOp: { kind: "spend", ref: ref.id }, updatedAt: serverTimestamp() });
  return wb.commit();
};

console.log("مكافأة الحصري 50%");
await seed({ enabled: true, percent: 50 });
await t("نشر حصري 7 أيام: 21 ← 11 نقطة", () => assertSucceeds(spend(db1(), { cost: 11, disc: 50, bal: 89 })));
await t("نفس الخصم على عرض مو حصري يُرفض", () => assertFails(spend(db1(), { offerId: "oN", cost: 11, disc: 50, bal: 78 })));
await t("خصم على التمييز يُرفض (النشر بس)", () => assertFails(spend(db1(), { item: "feature", effect: "feature", days: 3, cost: 5, disc: 50, bal: 84 })));
await t("سعر مخفّض أقل من المستحق يُرفض", () => assertFails(spend(db1(), { cost: 10, disc: 50, bal: 79 })));
await t("نسبة خصم أكبر من المسموح تُرفض", () => assertFails(spend(db1(), { cost: 0, disc: 100 })));
await t("السعر الكامل على عرض حصري مقبول", () => assertSucceeds(spend(db1(), { cost: 21, bal: 68 })));
await t("التاجر ما يشيل الحصري والمكافأة شغّالة", () => assertFails(updateDoc(doc(db1(), "offers/oX"), { exclusive: false })));
await t("يعدّل العنوان والحصري باقي ✓", () => assertSucceeds(updateDoc(doc(db1(), "offers/oX"), { offerTitle: "جديد" })));

console.log("الحصري مجاني (100%)");
await seed({ enabled: true, percent: 100 });
await t("نشر حصري مجاني بدون لمس المحفظة", () => assertSucceeds(spend(db1(), { cost: 0, disc: 100 })));
await t("مجاني على عرض مو حصري يُرفض", () => assertFails(spend(db1(), { offerId: "oN", cost: 0, disc: 100 })));
await t("تحويل مسودة لحصري ونشرها مجاناً", () => assertSucceeds(spend(db1(), { offerId: "oD", cost: 0, disc: 100, offerUpdate: { draft: false, exclusive: true } })));
await t("مجاني بدون علامة الخصم يُرفض", () => assertFails(spend(db1(), { cost: 0 })));

console.log("المكافأة مطفية");
await seed({ enabled: false, percent: 50 });
await t("الخصم يُرفض لما المكافأة مطفية", () => assertFails(spend(db1(), { cost: 11, disc: 50, bal: 89 })));
await t("التاجر يقدر يشيل الحصري لما المكافأة مطفية", () => assertSucceeds(updateDoc(doc(db1(), "offers/oX"), { exclusive: false })));
await t("نشر مسودة بالسعر الكامل", () => assertSucceeds(spend(db1(), { offerId: "oD", cost: 21, bal: 79, offerUpdate: { draft: false } })));

await env.cleanup();
console.log(`\n${pass} نجح، ${fail} فشل`);
console.log(`::notice title=exclusive tests::${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
