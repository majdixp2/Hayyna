// يعيد نفس عمليات لوحة الأدمن (المالك) بنفس البيانات بالضبط.
import { initializeTestEnvironment, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, getDocs, writeBatch, collection, serverTimestamp, Timestamp, addDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";
process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) { try { await fn(); pass++; console.log("  ✔", name); } catch (e) { fail++; const m = (e.message || String(e)).split("\n")[0].slice(0, 400); console.log("  ✘", name, m); console.log(`::error title=${name}::${m}`); } }
await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "config/app"), { a: 1 }); });
const owner = env.authenticatedContext("own", { email: "majdixp2@gmail.com", email_verified: true }).firestore();
await t("المالك: إنشاء أكواد (batch)", async () => {
  const wb = writeBatch(owner), exp = Timestamp.fromMillis(Date.now() + 12 * 30 * 864e5);
  for (let k = 0; k < 2; k++) wb.set(doc(owner, "creditCodes", `HY-TEST-00${k}0`), { points: 50, batch: "50نقطة-20261025-1424", createdAt: serverTimestamp(), expiresAt: exp, redeemedBy: null, redeemedAt: null });
  await assertSucceeds(wb.commit());
});
await t("المالك: عرض الأكواد", () => assertSucceeds(getDocs(collection(owner, "creditCodes"))));
await t("المالك: حفظ الباقات", () => assertSucceeds(updateDoc(doc(owner, "config", "app"), { "credits.packs": [{ points: 50, price: 50 }] })));
await t("المالك: سجل العملية", () => assertSucceeds(addDoc(collection(owner, "adminLog"), { at: serverTimestamp(), actor: "majdixp2@gmail.com", actorUid: "own", action: "إنشاء أكواد شحن", area: "credits", targetType: "", targetId: "", targetName: "", details: "2 كود × 50 نقطة", thumb: "", source: "web" })));
await t("المالك: قراءة المحافظ", () => assertSucceeds(getDocs(collection(owner, "wallets"))));
await t("المالك: قراءة الفريق", () => assertSucceeds(getDocs(collection(owner, "staff"))));
await env.cleanup();
console.log(`::notice title=admin tests::${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
