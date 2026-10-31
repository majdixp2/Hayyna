// اختبارات «📣 إعلانات لكوفر»
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDoc, increment, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), "announcements/a1"), { title: "خصم", views: 0, clicks: 0, endsAt: Timestamp.fromMillis(Date.now() + 864e5) });
});
const anon = () => env.unauthenticatedContext().firestore(), user = () => env.authenticatedContext("u1").firestore();
console.log("إعلانات لكوفر");
await t("أي أحد يقرأ الإعلانات", () => assertSucceeds(getDoc(doc(anon(), "announcements/a1"))));
await t("مستخدم عادي ما ينشئ إعلان", () => assertFails(setDoc(doc(user(), "announcements/a2"), { title: "x" })));
await t("الزائر يزيد المشاهدات بواحد", () => assertSucceeds(updateDoc(doc(anon(), "announcements/a1"), { views: increment(1) })));
await t("يزيد الضغطات بواحد", () => assertSucceeds(updateDoc(doc(user(), "announcements/a1"), { clicks: increment(1) })));
await t("ما يقدر يزيد بأكثر من واحد", () => assertFails(updateDoc(doc(user(), "announcements/a1"), { views: increment(5) })));
await t("ما يقدر يغيّر العنوان", () => assertFails(updateDoc(doc(user(), "announcements/a1"), { title: "y" })));
console.log(`\n${pass} نجح، ${fail} فشل`);
await env.cleanup();
process.exit(fail ? 1 : 0);
