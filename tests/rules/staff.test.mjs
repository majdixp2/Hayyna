// اختبارات صلاحيات فريق الإدارة وسجل العمليات على محاكي Firestore.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, updateDoc, deleteDoc, getDocs, collection, serverTimestamp, addDoc } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const who = (uid, email, verified = true) => env.authenticatedContext(uid, { email, email_verified: verified }).firestore();
const OWNER = () => who("own", "majdixp2@gmail.com");
const REV = () => who("rev", "rev@test.com");
const CRED = () => who("cred", "cred@test.com");
const OFF = () => who("off", "off@test.com");
const NOV = () => who("nov", "nov@test.com", false);
const RAND = () => who("rnd", "rnd@test.com");
const log = (db, uid, email, extra = {}) => addDoc(collection(db, "adminLog"), { at: serverTimestamp(), actor: email, actorUid: uid, action: "approve_offer", area: "review", targetType: "offer", targetId: "o1", targetName: "عرض", details: "", thumb: "", source: "web", ...extra });

await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "staff/rev@test.com"), { email: "rev@test.com", active: true, perms: ["review"] });
  await setDoc(doc(db, "staff/cred@test.com"), { email: "cred@test.com", active: true, perms: ["credits"] });
  await setDoc(doc(db, "staff/off@test.com"), { email: "off@test.com", active: false, perms: ["review", "credits"] });
  await setDoc(doc(db, "staff/nov@test.com"), { email: "nov@test.com", active: true, perms: ["review"] });
  await setDoc(doc(db, "offers/o1"), { merchantId: "m1", isApproved: false, offerTitle: "x" });
  await setDoc(doc(db, "wallets/m1"), { balance: 10 });
  await setDoc(doc(db, "config/app"), { a: 1 });
  await setDoc(doc(db, "feedback/f1"), { uid: "u9", message: "hi", status: "new" });
  await setDoc(doc(db, "adminLog/x1"), { actor: "majdixp2@gmail.com", action: "a" });
  await setDoc(doc(db, "adminLog/x2"), { actor: "rev@test.com", action: "a" });
});

console.log("صلاحيات الموظفين");
await t("مراجع العروض يعتمد عرض", () => assertSucceeds(updateDoc(doc(REV(), "offers/o1"), { isApproved: true })));
await t("مراجع العروض يقرأ عرض بانتظار المراجعة", () => assertSucceeds(getDoc(doc(REV(), "offers/o1"))));
await t("مراجع العروض ما يقرأ المحافظ", () => assertFails(getDoc(doc(REV(), "wallets/m1"))));
await t("مراجع العروض ما يقرأ الرسائل", () => assertFails(getDoc(doc(REV(), "feedback/f1"))));
await t("مراجع العروض ما يغيّر الإعدادات", () => assertFails(setDoc(doc(REV(), "config/app"), { a: 2 })));
await t("موظف الرصيد يقرأ المحافظ", () => assertSucceeds(getDoc(doc(CRED(), "wallets/m1"))));
await t("موظف الرصيد ما يعتمد عروض", () => assertFails(updateDoc(doc(CRED(), "offers/o1"), { isApproved: false })));
await t("موظف موقوف ما يقدر يسوي شي", () => assertFails(updateDoc(doc(OFF(), "offers/o1"), { isApproved: false })));
await t("إيميل غير مؤكد ما يُقبل", () => assertFails(updateDoc(doc(NOV(), "offers/o1"), { isApproved: false })));
await t("مستخدم عادي ما يعتمد عروض", () => assertFails(updateDoc(doc(RAND(), "offers/o1"), { isApproved: false })));

console.log("إدارة الفريق");
await t("الموظف يقرأ ملفه", () => assertSucceeds(getDoc(doc(REV(), "staff/rev@test.com"))));
await t("الموظف ما يقرأ ملف غيره", () => assertFails(getDoc(doc(REV(), "staff/cred@test.com"))));
await t("الموظف ما يزيد صلاحياته", () => assertFails(setDoc(doc(REV(), "staff/rev@test.com"), { email: "rev@test.com", active: true, perms: ["review", "credits"] })));
await t("المالك يضيف موظف", () => assertSucceeds(setDoc(doc(OWNER(), "staff/new@test.com"), { email: "new@test.com", active: true, perms: ["messages"] })));

console.log("سجل العمليات");
await t("الموظف يسجّل عمليته باسمه", () => assertSucceeds(log(REV(), "rev", "rev@test.com")));
await t("الموظف ما يسجّل باسم غيره", () => assertFails(log(REV(), "rev", "majdixp2@gmail.com")));
await t("المستخدم العادي ما يكتب في السجل", () => assertFails(log(RAND(), "rnd", "rnd@test.com")));
await t("الموظف يقرأ سجله", () => assertSucceeds(getDoc(doc(REV(), "adminLog/x2"))));
await t("الموظف ما يقرأ سجل المالك", () => assertFails(getDoc(doc(REV(), "adminLog/x1"))));
await t("ما أحد يعدّل السجل (حتى المالك)", () => assertFails(updateDoc(doc(OWNER(), "adminLog/x2"), { action: "zz" })));
await t("ما أحد يحذف من السجل (حتى المالك)", () => assertFails(deleteDoc(doc(OWNER(), "adminLog/x2"))));
await t("المالك يقرأ السجل كامل", () => assertSucceeds(getDocs(collection(OWNER(), "adminLog"))));

await env.cleanup();
console.log(`\n${pass} نجح، ${fail} فشل`);
console.log(`::notice title=staff rules tests::${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
