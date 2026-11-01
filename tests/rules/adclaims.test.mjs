// اختبارات «طلب نقل عروض الإعلانات» (adClaims): صاحب المتجر يطلب، والإدارة تشوف وتقرّر.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, collection, serverTimestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const ADM = () => env.authenticatedContext("adm", { email: "majdixp2@gmail.com", email_verified: true }).firestore();
const claim = (uid, extra = {}) => ({ uid, storeName: "كنتاكي", placeIds: ["p1", "p2", "p3"], groups: ["gabc"], count: 3, status: "pending", at: serverTimestamp(), ...extra });

await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), "adClaims/done"), { uid: "done", storeName: "x", placeIds: ["p9"], groups: [], count: 1, status: "approved", at: new Date() });
});

console.log("طلب نقل عروض الإعلانات");
await t("صاحب المتجر يرسل طلب", () => assertSucceeds(setDoc(doc(as("own"), "adClaims/own"), claim("own"))));
await t("يقرا طلبه", () => assertSucceeds(getDoc(doc(as("own"), "adClaims/own"))));
await t("ما يرسل باسم غيره", () => assertFails(setDoc(doc(as("own"), "adClaims/oth"), claim("oth"))));
await t("ما يعتمد طلبه بنفسه", () => assertFails(setDoc(doc(as("own"), "adClaims/own"), claim("own", { status: "approved" }))));
await t("ما يضيف حقول ثانية", () => assertFails(setDoc(doc(as("own"), "adClaims/own"), claim("own", { offers: ["o1"] }))));
await t("ما يرجّع طلب انعتمد", () => assertFails(setDoc(doc(as("done"), "adClaims/done"), claim("done"))));
await t("غيره ما يقرا الطلب", () => assertFails(getDoc(doc(as("rnd"), "adClaims/own"))));
await t("الأدمن يشوف كل الطلبات", () => assertSucceeds(getDocs(collection(ADM(), "adClaims"))));
await t("الأدمن يعتمد", () => assertSucceeds(updateDoc(doc(ADM(), "adClaims/own"), { status: "approved" })));
await t("الأدمن يحذف", () => assertSucceeds(deleteDoc(doc(ADM(), "adClaims/done"))));

console.log(`\n${pass} ✔  ${fail} ✘`);
await env.cleanup();
process.exit(fail ? 1 : 0);
