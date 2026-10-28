// عرض حقيقي بحقول كثيرة + نوع عرض: لازم يبقى تحت حدّ الـ1000 عملية في قواعد Firestore (كان يرفض التعديل بصمت).
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, Timestamp, serverTimestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";
const U = "vfclB6zIhthwq86YxGoMQOMLP0O2", ID = "hUdXRlr023UUNEZplHrR";
const br = { id: "main", name: "الفرع الرئيسي", latitude: 21.56, longitude: 39.24, region: "مكة المكرمة - جدة", district: "حي الصفا", phone: "+966500000000", openMinute: 720, closeMinute: 0, landmark: "" };
const stored = { adminNote: "", branches: [br], closeMinute: 0, createdAt: Timestamp.fromMillis(Date.parse("2026-10-25T10:53:24Z")), cuisineType: "مقاهي وحلويات ☕", currency: "ر.س",
  dailyPercents: [], delivery: true, deliveryFee: 0, deliveryFreeOver: 0, deliveryMinOrder: 30, description: "عرض متاح لجميع العملاء.", directionsCount: 0, discountBadge: "خصم 28% 🔥",
  discountPercent: 0, district: "حي الصفا", draft: false, exclusive: false, expiresAt: null, grandOpening: false, id: ID, isApproved: true, isFeatured: false, isPromotionalVideo: false,
  itemsScope: "", latitude: 21.56, logoUrl: "", longitude: 39.24, mediaType: "IMAGE", mediaUri: "https://res.cloudinary.com/x/image/upload/v1/abc.jpg", mediaUris: ["https://res.cloudinary.com/x/image/upload/v1/abc.jpg"],
  merchantId: U, offerTitle: "قهوة اليوم", openMinute: 720, originalPrice: 7, postType: "offer", price: 5, rating: 4.8, region: "مكة المكرمة - جدة", reportsCount: 0,
  restaurantName: "شانز كافيه", startsAt: null, tags: [], viewsCount: 30, whatsappCount: 0, workingHours: "يومياً من 12:00 م حتى 12:00 ص", phone: "+966500000000" };
const deal = { type: "discount", scope: "items", kind: "", section: "", itemIds: ["a", "b", "c"] };
const big = { branches: [br, br, br, br, br], tags: ["قهوة", "لاتيه", "شاي"], dailyPercents: [50, 30, 20], mediaUris: ["https://x/1.jpg", "https://x/2.jpg", "https://x/3.jpg"], exclusive: true, startsAt: Timestamp.now(), expiresAt: Timestamp.fromMillis(Date.now() + 864e5), logoUrl: "https://x/l.jpg", itemsScope: "على كل المنيو" };
let pass = 0, fail = 0;
for (const v of ["firestore"]) {
  const env = await initializeTestEnvironment({ projectId: "demo-" + v, firestore: { rules: readFileSync(new URL(`./${v}.rules`, import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
  const db = env.authenticatedContext(U).firestore();
  const seed = (over = {}) => env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), "offers", ID), { ...stored, ...over }); await setDoc(doc(c.firestore(), "merchantStatus", U), { trusted: true, verified: true, suspended: false }); });
  const run = async (n, f) => { try { await f(); pass++; console.log(`  ✔ ${n}`); } catch (e) { fail++; console.log(`::error title=limits FAIL::${n}`); } };
  for (const extra of [0, 1]) {
    const pad = extra ? big : {};
    await seed(pad); await run(`update title, +${extra} fields`, () => updateDoc(doc(db, "offers", ID), { offerTitle: "t" }));
    await seed(pad); await run(`update deal, +${extra} fields`, () => updateDoc(doc(db, "offers", ID), { deal }));
    const o = { ...stored, ...pad, id: "N" + extra, deal, viewsCount: 0, createdAt: serverTimestamp() };
    await seed(); await run(`create with deal, +${extra} fields`, () => setDoc(doc(db, "offers", "N" + extra), o));
  }
  await env.cleanup();
}
console.log(`::notice title=limits tests::${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
