// اختبارات «موظفين المتجر»: رقم المتجر، دليل الدخول، صلاحيات الطلبات و«نفد اليوم».
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, collection, query, where, serverTimestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

process.on("unhandledRejection", (e) => { console.log(`::error title=crash::${String(e?.message || e).split("\n")[0].slice(0, 400)}`); process.exit(1); });
const env = await initializeTestEnvironment({ projectId: "demo-hayyna", firestore: { rules: readFileSync(new URL("./firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 } });
let pass = 0, fail = 0;
async function t(name, fn) {
  try { await fn(); pass++; console.log("  ✔", name); }
  catch (e) { fail++; const msg = (e.message || String(e)).split("\n")[0].slice(0, 300); console.log("  ✘", name, "\n     ", msg); console.log(`::error title=${name}::${msg}`); }
}
const as = (uid) => env.authenticatedContext(uid).firestore();
const OWN = () => as("own"), OTHER = () => as("oth"), CASH = () => as("cash"), KIT = () => as("kit"), OFF = () => as("off"), RND = () => as("rnd");
const staff = (n, extra = {}) => ({ storeId: "own", storeName: "تشيكن X", code: "48213", n, name: "أحمد", role: "cashier", perms: ["orders", "stock"], active: true, createdAt: serverTimestamp(), email: `48213-${n}-ab12@staff.lacover.app`, ...extra });

await env.clearFirestore();
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "config/app"), { catalog: { orderEnabled: true, features: {} } });
  await setDoc(doc(db, "storeCodes/48213"), { uid: "own", at: new Date() });
  await setDoc(doc(db, "storeCodes/55555"), { uid: "oth", at: new Date() });
  await setDoc(doc(db, "storeStaff/cash"), { ...staff(1), createdAt: new Date() });
  await setDoc(doc(db, "storeStaff/kit"), { ...staff(2, { role: "kitchen", perms: ["orders"] }), createdAt: new Date() });
  await setDoc(doc(db, "storeStaff/off"), { ...staff(3, { active: false }), createdAt: new Date() });
  await setDoc(doc(db, "orders/o1"), { storeId: "own", customerUid: "c1", status: "sent", items: [], total: 10 });
  await setDoc(doc(db, "orders/o2"), { storeId: "oth", customerUid: "c1", status: "sent", items: [], total: 10 });
  await setDoc(doc(db, "orders/o3"), { storeId: "own", customerUid: "c1", status: "received", items: [], total: 10 });
  await setDoc(doc(db, "items/own_menu_0"), { merchantId: "own", available: true, approved: false, price: 5, name: "x", updatedAt: new Date() });
});

console.log("رقم المتجر ودليل الدخول");
await t("صاحب المحل ياخذ رقم متجر", () => assertSucceeds(setDoc(doc(OWN(), "storeCodes/12345"), { uid: "own", at: serverTimestamp() })));
await t("ما ياخذ رقم مأخوذ", () => assertFails(setDoc(doc(OWN(), "storeCodes/55555"), { uid: "own", at: serverTimestamp() })));
await t("ما ياخذ رقم باسم غيره", () => assertFails(setDoc(doc(OWN(), "storeCodes/23456"), { uid: "oth", at: serverTimestamp() })));
await t("يضيف دليل دخول لموظفه", () => assertSucceeds(setDoc(doc(OWN(), "staffLogin/48213-4"), { email: "48213-4-zz99@staff.lacover.app", storeId: "own", n: 4 })));
await t("ما يضيف دليل لمتجر غيره", () => assertFails(setDoc(doc(OWN(), "staffLogin/55555-1"), { email: "55555-1-zz99@staff.lacover.app", storeId: "own", n: 1 })));
await t("الإيميل لازم يطابق الرقم", () => assertFails(setDoc(doc(OWN(), "staffLogin/48213-5"), { email: "boss@gmail.com", storeId: "own", n: 5 })));
await t("أي أحد يقرأ الدليل (للدخول)", () => assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "staffLogin/48213-4"))));

console.log("إضافة الموظفين");
await t("صاحب المحل يضيف موظف", () => assertSucceeds(setDoc(doc(OWN(), "storeStaff/new1"), staff(6))));
await t("ما يضيف موظف لمتجر غيره", () => assertFails(setDoc(doc(OTHER(), "storeStaff/new2"), staff(7))));
await t("ما يعطي صلاحيات غير مسموحة", () => assertFails(setDoc(doc(OWN(), "storeStaff/new3"), staff(8, { perms: ["orders", "offers"] }))));
await t("الموظف ما يعدّل صلاحياته", () => assertFails(updateDoc(doc(CASH(), "storeStaff/cash"), { perms: ["orders", "stock"], active: true })));
await t("صاحب المحل يوقف الموظف", () => assertSucceeds(updateDoc(doc(OWN(), "storeStaff/new1"), { active: false })));
await t("الموظف يقرأ ملفه", () => assertSucceeds(getDoc(doc(CASH(), "storeStaff/cash"))));
await t("صاحب المحل يشوف موظفينه", () => assertSucceeds(getDocs(query(collection(OWN(), "storeStaff"), where("storeId", "==", "own")))));

console.log("الطلبات");
await t("الكاشير يشوف طلبات محله", () => assertSucceeds(getDocs(query(collection(CASH(), "orders"), where("storeId", "==", "own")))));
await t("الكاشير ما يشوف طلبات محل ثاني", () => assertFails(getDoc(doc(CASH(), "orders/o2"))));
await t("الكاشير يستلم الطلب", () => assertSucceeds(updateDoc(doc(CASH(), "orders/o1"), { status: "received", updatedAt: serverTimestamp() })));
await t("المطبخ يجهّز الطلب", () => assertSucceeds(updateDoc(doc(KIT(), "orders/o3"), { status: "ready", updatedAt: serverTimestamp() })));
await t("الموظف ما يسجّل «ما انستلم»", () => assertFails(updateDoc(doc(CASH(), "orders/o1"), { status: "noshow", updatedAt: serverTimestamp() })));
await t("الموظف الموقوف ما يشوف الطلبات", () => assertFails(getDoc(doc(OFF(), "orders/o1"))));
await t("مستخدم عادي ما يشوف طلبات المحل", () => assertFails(getDoc(doc(RND(), "orders/o1"))));

console.log("نفد اليوم");
await t("الكاشير يعلّم الصنف نفد", () => assertSucceeds(updateDoc(doc(CASH(), "items/own_menu_0"), { available: false, updatedAt: serverTimestamp() })));
await t("الكاشير ما يغيّر السعر", () => assertFails(updateDoc(doc(CASH(), "items/own_menu_0"), { price: 1, updatedAt: serverTimestamp() })));
await t("المطبخ بدون صلاحية نفد", () => assertFails(updateDoc(doc(KIT(), "items/own_menu_0"), { available: true, updatedAt: serverTimestamp() })));

console.log("إيقاف الميزة من الأدمن");
await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "config/app"), { catalog: { orderEnabled: true, features: { staff: false } } }); });
await t("لما الأدمن يوقفها الموظف ما يشوف الطلبات", () => assertFails(getDoc(doc(CASH(), "orders/o1"))));

console.log(`\n${pass} نجح، ${fail} فشل`);
await env.cleanup();
process.exit(fail ? 1 : 0);
