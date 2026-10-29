// اختبارات «عروض من إعلانات»: الفريق يرفع عرض لمحل Google غير مسجّل (merchantId = poi_<placeId>).
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
const DAY = 864e5;
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const who = (uid, email, verified = true) => env.authenticatedContext(uid, { email, email_verified: verified }).firestore();
const OWNER = () => who("own", "majdixp2@gmail.com");
const SCOUT = () => who("sc", "scout@test.com");
const REV = () => who("rev", "rev@test.com");
const CRED = () => who("cred", "cred@test.com");
const RAND = () => who("rnd", "rnd@test.com");
const PID = "ChIJabc-123_XYZ";
const adOffer = (id, by, extra = {}) => ({
  id, merchantId: "poi_" + PID, placeId: PID, source: "ad", scoutedBy: by,
  restaurantName: "تشيكن X", offerTitle: "عرض الوطن الجامبو", description: "", cuisineType: "مطاعم", price: 12, currency: "ر.س",
  mediaType: "IMAGE", mediaUri: "https://x/y.jpg", district: "الحمدانية", region: "جدة", latitude: 21.7, longitude: 39.2, phone: "0565151896",
  isApproved: true, isFeatured: false, viewsCount: 0, directionsCount: 0, whatsappCount: 0, reportsCount: 0,
  createdAt: serverTimestamp(), expiresAt: Timestamp.fromMillis(Date.now() + 7 * DAY), ...extra,
});

await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "staff/scout@test.com"), { email: "scout@test.com", active: true, perms: ["scout"] });
  await setDoc(doc(db, "staff/rev@test.com"), { email: "rev@test.com", active: true, perms: ["review"] });
  await setDoc(doc(db, "staff/cred@test.com"), { email: "cred@test.com", active: true, perms: ["credits"] });
  await setDoc(doc(db, "offers/ad_old"), { ...adOffer("ad_old", "sc"), createdAt: Timestamp.now() });
  await setDoc(doc(db, "offers/m1o"), { ...adOffer("m1o", "x"), merchantId: "m1", source: "", createdAt: Timestamp.now() });
});

console.log("رفع عرض من إعلان");
await t("المالك يرفع عرض من إعلان", () => assertSucceeds(setDoc(doc(OWNER(), "offers/ad1"), adOffer("ad1", "own"))));
await t("موظف الإعلانات يرفع عرض", () => assertSucceeds(setDoc(doc(SCOUT(), "offers/ad2"), adOffer("ad2", "sc"))));
await t("موظف بدون الصلاحية ما يرفع", () => assertFails(setDoc(doc(CRED(), "offers/ad3"), adOffer("ad3", "cred"))));
await t("مستخدم عادي ما يرفع", () => assertFails(setDoc(doc(RAND(), "offers/ad4"), adOffer("ad4", "rnd"))));
await t("ما يرفع باسم موظف ثاني", () => assertFails(setDoc(doc(SCOUT(), "offers/ad5"), adOffer("ad5", "own"))));
await t("التاجر الوهمي لازم يطابق المحل", () => assertFails(setDoc(doc(SCOUT(), "offers/ad6"), adOffer("ad6", "sc", { merchantId: "m1" }))));
await t("ما يرفع عرض لتاجر حقيقي", () => assertFails(setDoc(doc(SCOUT(), "offers/ad7"), adOffer("ad7", "sc", { merchantId: "m1", source: "" }))));
await t("لازم له تاريخ انتهاء", () => assertFails(setDoc(doc(SCOUT(), "offers/ad8"), adOffer("ad8", "sc", { expiresAt: null }))));
await t("مدة أطول من 31 يوم مرفوضة", () => assertFails(setDoc(doc(SCOUT(), "offers/ad9"), adOffer("ad9", "sc", { expiresAt: Timestamp.fromMillis(Date.now() + 40 * DAY) }))));
await t("ما يرفع عرض مميز", () => assertFails(setDoc(doc(SCOUT(), "offers/ad10"), adOffer("ad10", "sc", { isFeatured: true }))));

console.log("تعديل وحذف");
await t("موظف الإعلانات يعدّل العرض", () => assertSucceeds(updateDoc(doc(SCOUT(), "offers/ad_old"), { offerTitle: "جديد", scoutedBy: "sc" })));
await t("ما يغيّر صاحب العرض", () => assertFails(updateDoc(doc(SCOUT(), "offers/ad_old"), { merchantId: "poi_other", placeId: "other" })));
await t("ما يعدّل عروض التجار", () => assertFails(updateDoc(doc(SCOUT(), "offers/m1o"), { offerTitle: "x" })));
await t("مراجع العروض ينقل العرض لتاجر", () => assertSucceeds(updateDoc(doc(REV(), "offers/ad_old"), { merchantId: "m9", source: "", })));
await t("موظف الإعلانات يحذف عرض إعلان", () => assertSucceeds(deleteDoc(doc(SCOUT(), "offers/ad2"))));
await t("ما يحذف عروض التجار", () => assertFails(deleteDoc(doc(SCOUT(), "offers/m1o"))));
await t("الزائر يقرأ العرض", () => assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "offers/ad1"))));
await t("الزائر يزيد المشاهدات", () => assertSucceeds(updateDoc(doc(env.unauthenticatedContext().firestore(), "offers/ad1"), { viewsCount: 1 })));

console.log(`\n${pass} نجح، ${fail} فشل`);
await env.cleanup();
process.exit(fail ? 1 : 0);
