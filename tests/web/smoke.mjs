// يفتح صفحات الموقع الحي بمقاس جوال وكمبيوتر، ويسجّل أخطاء JavaScript والطلبات الفاشلة
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
const BASE = process.env.BASE || "https://gen-lang-client-0536850806.web.app";
const PAGES = ["/", "/?tab=list", "/admin/", "/privacy.html"];
const IGNORE = /favicon|google-analytics|gstatic\.com\/.*\.map|ERR_BLOCKED_BY_CLIENT|maps\.googleapis\.com\/maps\/api\/mapsjs\/gen_204|Permission denied|permission-denied/i;
mkdirSync("shots", { recursive: true });
const b = await chromium.launch();
let bad = 0;
for (const [label, vp] of [["mobile", { width: 390, height: 844 }], ["desktop", { width: 1366, height: 860 }]]) {
  for (const path of PAGES) {
    const ctx = await b.newContext({ viewport: vp, locale: "ar-SA", geolocation: { latitude: 21.62, longitude: 39.21 }, permissions: ["geolocation"] });
    const p = await ctx.newPage(); const errs = [];
    p.on("pageerror", (e) => errs.push("JS: " + e.message));
    p.on("console", (m) => { if (m.type() === "error" && !IGNORE.test(m.text())) errs.push("console: " + m.text()); });
    p.on("requestfailed", (r) => { const u = r.url(); if (!IGNORE.test(u) && !/firestore|googleapis.com\/google.firestore/.test(u)) errs.push(`request failed: ${u.slice(0, 120)} (${r.failure()?.errorText})`); });
    p.on("response", (r) => { if (r.status() >= 400 && !IGNORE.test(r.url()) && !/firestore/.test(r.url())) errs.push(`HTTP ${r.status()}: ${r.url().slice(0, 120)}`); });
    try { await p.goto(BASE + path, { waitUntil: "networkidle", timeout: 45000 }); } catch (e) { errs.push("load: " + e.message.split("\n")[0]); }
    await p.waitForTimeout(4000);
    // جولة سريعة في التبويبات (الصفحة الرئيسية)
    if (path === "/") for (const t of ["list", "favs", "account", "map"]) { await p.click(`[data-tab="${t}"]`).catch(() => {}); await p.waitForTimeout(700); }
    await p.screenshot({ path: `shots/${label}${path.replace(/[^a-z0-9]/gi, "_") || "_home"}.png`, fullPage: false });
    const uniq = [...new Set(errs)];
    if (uniq.length) { bad += uniq.length; uniq.slice(0, 15).forEach((e) => console.log(`::error title=${label} ${path}::${e.slice(0, 400)}`)); }
    else console.log(`::notice title=${label} ${path}::OK`);
    await ctx.close();
  }
}
await b.close();
console.log(`::notice title=web smoke::${bad} problems`);
