import { initializeTestEnvironment, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
const U = "vfclB6zIhthwq86YxGoMQOMLP0O2", ID = "hUdXRlr023UUNEZplHrR";
const br = { id: "main", name: "الفرع الرئيسي", latitude: 21.56, longitude: 39.24, region: "مكة المكرمة - جدة", district: "حي الصفا", phone: "+966500000000", openMinute: 720, closeMinute: 0, landmark: "" };
const stored = { adminNote: "", branches: [br], closeMinute: 0, createdAt: Timestamp.fromMillis(Date.parse("2026-10-25T10:53:24Z")), cuisineType: "مقاهي وحلويات ☕", currency: "ر.س",
  dailyPercents: [], delivery: true, deliveryFee: 0, deliveryFreeOver: 0, deliveryMinOrder: 30, description: "عرض متاح لجميع العملاء.", directionsCount: 0, discountBadge: "خصم 28% 🔥",
  discountPercent: 0, district: "حي الصفا", draft: false, exclusive: false, expiresAt: null, grandOpening: false, id: ID, isApproved: true, isFeatured: false, isPromotionalVideo: false,
  itemsScope: "", latitude: 21.56, logoUrl: "", longitude: 39.24, mediaType: "IMAGE", mediaUri: "https://res.cloudinary.com/x/image/upload/v1/abc.jpg", mediaUris: ["https://res.cloudinary.com/x/image/upload/v1/abc.jpg"],
  merchantId: U, offerTitle: "قهوة اليوم", openMinute: 720, originalPrice: 7, postType: "offer", price: 5, rating: 4.8, region: "مكة المكرمة - جدة", reportsCount: 0,
  restaurantName: "شانز كافيه", startsAt: null, tags: [], viewsCount: 30, whatsappCount: 0, workingHours: "يومياً من 12:00 م حتى 12:00 ص", phone: "+966500000000" };
const reset = (cfg) => env.withSecurityRulesDisabled(async (c) => { const db = c.firestore(); await setDoc(doc(db, "offers", ID), stored); await setDoc(doc(db, "merchantStatus", U), { trusted: true, verified: true, suspended: false }); if (cfg) await setDoc(doc(db, "config/app"), cfg); });
const android = { ...stored, deal: { type: "promo", orderable: false } };
["merchantId", "isFeatured", "viewsCount", "directionsCount", "whatsappCount", "reportsCount", "createdAt", "rating"].forEach((k) => delete android[k]);
const db = env.authenticatedContext(U).firestore();
const tries = [
  ["android update", () => updateDoc(doc(db, "offers", ID), android)],
  ["no deal", () => { const a = { ...android }; delete a.deal; return updateDoc(doc(db, "offers", ID), a); }],
  ["title only", () => updateDoc(doc(db, "offers", ID), { offerTitle: "قهوة اليوم ✓" })],
];
for (const cfg of [null, { exclusive: { enabled: true, reward: { enabled: true, percent: 100 } }, catalog: { enabled: true, orderEnabled: true } }]) {
  for (const [n, f] of tries) { await reset(cfg); try { await assertSucceeds(f()); console.log(`::notice title=repro ok::${n} cfg=${!!cfg}`); } catch (e) { console.log(`::error title=repro FAIL ${n} cfg=${!!cfg}::${String(e.message).split("\n")[0].slice(0, 400)}`); } }
}
await env.cleanup();
