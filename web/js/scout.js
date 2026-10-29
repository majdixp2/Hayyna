// 📷 عروض من إعلانات: الأدمن أو فريق الإعلانات (صلاحية scout) يرفع صورة إعلان لمحل مو مسجّل،
// والذكاء الاصطناعي يقرأ الإعلان، ويتحط العرض على موقع المحل في الخريطة لين يستلمه صاحبه.
// يشتغل في التطبيق (من بطاقة المحل) وفي لوحة الإدارة (من صورة الإعلان بس).
import { collection, doc, setDoc, getDoc, serverTimestamp, Timestamp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { searchPlaces, placeDetails } from "./map.js";

export const AD_DEFAULT_DAYS = 7;
export const AD_MAX_DAYS = 31;
export const AI_MODELS = ["gemini-3.8-flash", "gemini-3.5-flash-lite"];
const DAY = 864e5;
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const num = (v) => { const n = Number(String(v ?? "").replace(/[٠-٩]/g, (d) => "٠١٢٣٤٥٦٧٨٩".indexOf(d)).replace(/[^\d.]/g, "")); return isFinite(n) && n > 0 ? n : null; };
export const adDays = (cfg) => Math.min(AD_MAX_DAYS, Math.max(1, Math.round(Number(cfg?.adScoutDays) || AD_DEFAULT_DAYS)));

/** هل هذا الحساب يقدر يرفع عروض من إعلانات؟ (المالك، أو موظف فعّال عنده scout) */
export async function canScout(db, user, adminEmails) {
  const email = (user?.email || "").toLowerCase();
  if (!email) return false;
  if (adminEmails.includes(email)) return true;
  try { const d = await getDoc(doc(db, "staff", email)); const s = d.exists() ? d.data() : null; return !!(s?.active && (s.perms || []).includes("scout")); }
  catch { return false; }
}

// ---------------- الصورة ----------------
function shrink(file, maxSide = 1600, q = 0.85) {
  return new Promise((resolve) => {
    if (!file.type.startsWith("image/") || file.type === "image/gif") return resolve(file);
    const img = new Image(), url = URL.createObjectURL(file);
    img.onload = () => {
      const s = Math.min(1, maxSide / Math.max(img.width, img.height));
      const c = document.createElement("canvas"); c.width = Math.round(img.width * s); c.height = Math.round(img.height * s);
      c.getContext("2d").drawImage(img, 0, 0, c.width, c.height); URL.revokeObjectURL(url);
      c.toBlob((b) => resolve(b || file), "image/jpeg", q);
    };
    img.onerror = () => resolve(file); img.src = url;
  });
}
async function upload(file) {
  const blob = await shrink(file);
  const fd = new FormData(); fd.append("upload_preset", "hayyna_offers"); fd.append("file", blob, "ad.jpg");
  const r = await fetch("https://api.cloudinary.com/v1_1/ffhqn5dw/image/upload", { method: "POST", body: fd });
  const j = await r.json().catch(() => ({}));
  if (!r.ok || !j.secure_url) throw new Error("تعذّر رفع الصورة" + (j.error?.message ? ": " + j.error.message : ""));
  return j.secure_url;
}
const toBase64 = (blob) => new Promise((res, rej) => { const fr = new FileReader(); fr.onload = () => res(String(fr.result).split(",")[1]); fr.onerror = rej; fr.readAsDataURL(blob); });

// ---------------- القراءة الذكية (Gemini عبر Firebase AI Logic) ----------------
const prompt = (today, cats) => `أنت مساعد لتطبيق عروض محلات في السعودية. هذي صورة إعلان لمحل (بوستر، منشور انستقرام، أو رسالة واتساب).
تاريخ اليوم: ${today}
اقرأ الصورة وكل نص مكتوب عليها بدقة. رجّع JSON فقط بدون أي كلام ثاني:
{"storeName": "اسم المحل كما هو مكتوب (عربي، وإذا ما فيه عربي اكتبه زي ما هو)",
 "storeNameEn": "اسم المحل بالإنجليزي إذا مكتوب أو \\"\\"",
 "isOffer": true إذا فيه عرض أو سعر أو خصم، false إذا إعلان خدمة أو تعريف بالمحل بس,
 "title": "عنوان قصير وجذاب للعرض أو الخدمة (أقل من 60 حرف)",
 "description": "وصف مختصر: وش يشمل العرض أو الخدمة (سطرين بالكثير)",
 "price": السعر بعد الخصم بالريال كرقم أو null,
 "originalPrice": السعر قبل الخصم كرقم أو null,
 "days": عدد أيام العرض إذا مكتوب أو null,
 "endDate": "تاريخ نهاية العرض yyyy-MM-dd إذا مكتوب أو null",
 "phones": ["كل أرقام الجوال/الهاتف المكتوبة، أرقام فقط"],
 "city": "المدينة إذا مكتوبة أو \\"\\"",
 "district": "الحي إذا مكتوب (بدون كلمة حي) أو \\"\\"",
 "address": "العنوان أو علامة المكان كما هي مكتوبة أو \\"\\"",
 "category": "واحد من هذي بالضبط: ${cats.join(" | ")}",
 "tags": ["الأصناف أو الخدمات المذكورة، كلمة أو كلمتين لكل واحد، حتى 8"]}
لا تخترع أي معلومة غير موجودة في الصورة.`;

let aiMod = null;
/** يقرأ صورة الإعلان ويرجّع مسودة. يرمي خطأ لو القراءة الذكية ما اشتغلت. */
export async function readAd(fbApp, file, { model, categories = [] } = {}) {
  aiMod = aiMod || await import("https://www.gstatic.com/firebasejs/11.10.0/firebase-ai.js");
  const { getAI, getGenerativeModel, GoogleAIBackend } = aiMod;
  const ai = getAI(fbApp, { backend: new GoogleAIBackend() });
  const data = await toBase64(await shrink(file, 1280, 0.8));
  const today = new Date().toLocaleDateString("ar-SA-u-ca-gregory-nu-latn", { year: "numeric", month: "2-digit", day: "2-digit", weekday: "long" });
  let last;
  for (const m of [...new Set([model, ...AI_MODELS].filter((x) => x && !String(x).startsWith("gemini-2.5")))]) {
    try {
      const gm = getGenerativeModel(ai, { model: m, generationConfig: { responseMimeType: "application/json" } });
      const res = await gm.generateContent([prompt(today, categories), { inlineData: { data, mimeType: "image/jpeg" } }]);
      const raw = res.response.text().trim();
      const j = JSON.parse(raw.slice(Math.max(0, raw.indexOf("{")), raw.lastIndexOf("}") + 1));
      const phones = (Array.isArray(j.phones) ? j.phones : [j.phone]).map((p) => String(p || "").replace(/\D/g, "")).filter((p) => p.length >= 7).slice(0, 3);
      let days = Number(j.days) > 0 ? Math.round(j.days) : null;
      if (!days && j.endDate) { const e = Date.parse(j.endDate); if (e) days = Math.ceil((e + DAY - Date.now()) / DAY); }
      return {
        storeName: String(j.storeName || j.storeNameEn || "").trim().slice(0, 100), storeNameEn: String(j.storeNameEn || "").trim().slice(0, 100),
        isOffer: j.isOffer !== false, title: String(j.title || "").trim().slice(0, 150), description: String(j.description || "").trim().slice(0, 600),
        price: num(j.price), originalPrice: num(j.originalPrice), days: days && days > 0 ? days : null, phones,
        city: String(j.city || "").trim().slice(0, 60), district: String(j.district || "").replace(/^حي\s+/, "").trim().slice(0, 60),
        address: String(j.address || "").trim().slice(0, 200),
        category: categories.includes(j.category) ? j.category : "",
        tags: (Array.isArray(j.tags) ? j.tags : []).map((t) => String(t).trim()).filter((t) => t && t.length <= 25).slice(0, 8),
      };
    } catch (e) {
      last = e; const msg = String(e?.message || e);
      if (/app.?check|permission|403|401|api key|billing/i.test(msg)) break; // ما تتصلح بتغيير النموذج
    }
  }
  throw last || new Error("ai failed");
}
export function aiErrorText(e) {
  const m = String(e?.message || e);
  if (/app.?check/i.test(m)) return "القراءة الذكية محمية بـ App Check وما تشتغل من هنا. عبّي البيانات يدوي أو استخدم التطبيق.";
  if (/firebase-ai|import|module/i.test(m)) return "تعذّر تحميل أداة القراءة الذكية. تأكد من الإنترنت وجرّب مرة ثانية.";
  return "ما قدرنا نقرأ الإعلان. عبّي البيانات يدوي، أو جرّب صورة أوضح.";
}

// ---------------- الحفظ ----------------
export async function saveAdOffer(db, user, f) {
  const p = f.place;
  const imageUrl = f.imageUrl || await upload(f.file);
  const ref = f.editId ? doc(db, "offers", f.editId) : doc(collection(db, "offers"));
  const price = num(f.price) || 0, orig = num(f.orig);
  const pct = orig && price && orig > price ? Math.floor((orig - price) / orig * 100) : 0;
  const days = Math.min(AD_MAX_DAYS, Math.max(1, Math.round(Number(f.days) || AD_DEFAULT_DAYS)));
  const data = {
    id: ref.id, merchantId: "poi_" + p.placeId, placeId: p.placeId, source: "ad", scoutedBy: user.uid,
    restaurantName: (f.name || p.name || "محل").trim().slice(0, 100), offerTitle: f.title.trim().slice(0, 150),
    description: (f.desc.trim() || "عرض من إعلان المحل.").slice(0, 2000), cuisineType: (f.category || "").slice(0, 60),
    postType: "offer", price, originalPrice: orig && orig > price ? orig : null, currency: "ر.س",
    discountBadge: pct >= 1 ? `خصم ${pct}% 🔥` : "",
    mediaType: "IMAGE", mediaUri: imageUrl, mediaUris: [imageUrl],
    district: (p.district || f.district || "").slice(0, 100), region: (p.city || f.city || "").slice(0, 100),
    latitude: p.lat, longitude: p.lng, phone: String(f.phone || p.phone || "").replace(/[^\d+]/g, "").slice(0, 30),
    openMinute: -1, closeMinute: -1, workingHours: "",
    expiresAt: Timestamp.fromMillis(Date.now() + days * DAY), startsAt: null,
    discountPercent: 0, dailyPercents: [], itemsScope: "", grandOpening: false, exclusive: false,
    tags: (f.tags || []).slice(0, 12), delivery: false, logoUrl: "", branches: [], deal: null,
    isApproved: true, isFeatured: false,
  };
  if (f.editId) await setDoc(ref, data, { merge: true });
  else await setDoc(ref, { ...data, viewsCount: 0, directionsCount: 0, whatsappCount: 0, reportsCount: 0, createdAt: serverTimestamp() });
  return ref.id;
}

// ---------------- الواجهة (نفس الفورم في التطبيق ولوحة الإدارة) ----------------
let cssDone = false;
function css() {
  if (cssDone) return; cssDone = true;
  const st = document.createElement("style");
  st.textContent = `
.adf{display:flex;flex-direction:column;gap:12px;font-family:inherit;color:inherit}
.adf h3{margin:0;font-size:18px;display:flex;align-items:center;gap:8px}
.adf .adf-sub{margin:-6px 0 0;font-size:13px;opacity:.7;line-height:1.6}
.adf-pick{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:6px;min-height:150px;border:2px dashed #10318F55;border-radius:16px;cursor:pointer;background:#10318F0A;text-align:center;padding:12px;position:relative;overflow:hidden}
.adf-pick b{font-size:15px;color:#10318F}.adf-pick small{opacity:.65}
.adf-pick img{max-width:100%;max-height:300px;border-radius:12px;display:block}
.adf-pick .adf-chg{position:absolute;bottom:8px;inset-inline-start:8px;background:#000a;color:#fff;font-size:12px;padding:4px 10px;border-radius:20px}
.adf-ai{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;border:0;border-radius:14px;padding:13px;font:inherit;font-weight:700;font-size:15px;cursor:pointer;color:#10318F;background:linear-gradient(90deg,#F4B400,#FFD54F)}
.adf-ai[disabled]{opacity:.6;cursor:wait}
.adf-note{font-size:12.5px;padding:9px 12px;border-radius:12px;background:#F4B4001F;line-height:1.6}
.adf-note.ok{background:#15803D1A}.adf-note.err{background:#DC26261A}
.adf-place{border:1px solid #10318F33;border-radius:14px;padding:10px 12px;display:flex;gap:10px;align-items:center}
.adf-place .ic{font-size:22px}.adf-place .t{flex:1;min-width:0}.adf-place b{display:block}
.adf-place small{opacity:.7;display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.adf-place button,.adf-res button{font:inherit}
.adf-lnk{background:none;border:0;color:#10318F;font-weight:700;cursor:pointer;padding:4px}
.adf-res{display:flex;flex-direction:column;gap:6px}
.adf-res button{display:block;width:100%;text-align:start;border:1px solid #10318F22;border-radius:12px;background:transparent;padding:9px 12px;cursor:pointer;color:inherit}
.adf-res button:hover{background:#10318F0D}.adf-res small{display:block;opacity:.7}
.adf label{display:flex;flex-direction:column;gap:5px;font-size:13px;font-weight:700}
.adf input,.adf textarea,.adf select{font:inherit;font-weight:400;font-size:15px;padding:10px 12px;border-radius:12px;border:1px solid #8884;background:transparent;color:inherit;width:100%;box-sizing:border-box}
.adf textarea{min-height:64px;resize:vertical}
.adf .adf-row{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.adf-chips{display:flex;flex-wrap:wrap;gap:6px}
.adf-chips button{font:inherit;font-size:13px;border:1px solid #10318F33;background:transparent;color:inherit;border-radius:20px;padding:6px 12px;cursor:pointer}
.adf-chips button.on{background:#10318F;color:#fff;border-color:#10318F}
.adf-save{width:100%;border:0;border-radius:14px;padding:14px;font:inherit;font-weight:800;font-size:16px;background:#10318F;color:#fff;cursor:pointer}
.adf-save[disabled]{opacity:.55;cursor:not-allowed}
.adf-search{display:flex;gap:8px}.adf-search input{flex:1}
.adf-search button{font:inherit;border:0;border-radius:12px;padding:0 14px;background:#10318F;color:#fff;font-weight:700;cursor:pointer}
.adf .spin{width:16px;height:16px;border:2px solid currentColor;border-top-color:transparent;border-radius:50%;display:inline-block;animation:adfspin .8s linear infinite}
@keyframes adfspin{to{transform:rotate(360deg)}}
[data-theme=dark] .adf-pick b,[data-theme=dark] .adf-lnk{color:#9DB4FF}`;
  document.head.appendChild(st);
}

/**
 * يرسم فورم «ارفع عرض من إعلان» داخل el.
 * opts: { db, fbApp, user, config, mapsKey, categories, toast, onDone(id), place?, near?, aiFirst? }
 * place موجود = جاي من بطاقة المحل على الخريطة. بدونه = يدوّر على المحل في Google (لوحة الإدارة).
 */
export function mountAdForm(el, opts) {
  css();
  const { db, fbApp, user, config = {}, mapsKey, categories = [], toast = () => {}, onDone = () => {} } = opts;
  const aiOn = !!config.aiAutofillEnabled;
  const f = {
    file: null, preview: "", place: opts.place || null, name: opts.place?.name || "", title: "", price: "", orig: "", desc: "",
    phone: opts.place?.phone || "", category: categories[0] || "", days: adDays(config), tags: [], city: "", district: "",
    q: "", results: null, searching: false, ai: "idle", aiMsg: "", saving: false,
  };
  const fixedPlace = !!opts.place;
  const ready = () => f.file && f.place && f.title.trim();

  const draw = () => {
    const p = f.place;
    el.innerHTML = `<div class="adf">
      <h3>📷 ارفع عرض من إعلان</h3>
      <p class="adf-sub">${fixedPlace ? "العرض بيتحط على موقع هذا المحل" : "ارفع صورة الإعلان، والذكاء الاصطناعي يقرأه ويدوّر على المحل"}، ويظهر للزبائن بعلامة «📷 من إعلان المحل» ويختفي لحاله بعد المدة.</p>
      <label class="adf-pick" for="adfFile">${f.preview ? `<img src="${esc(f.preview)}" alt=""><span class="adf-chg">تغيير الصورة</span>` : `<span style="font-size:34px">🖼️</span><b>اختر صورة الإعلان</b><small>بوستر، منشور انستقرام، أو صورة من واتساب</small>`}</label>
      <input type="file" id="adfFile" accept="image/*" hidden>
      ${f.file ? (aiOn ? `<button class="adf-ai" id="adfAi" ${f.ai === "busy" ? "disabled" : ""}>${f.ai === "busy" ? `<span class="spin"></span> نقرأ الإعلان…` : f.ai === "done" ? "✨ اقرأه مرة ثانية" : "✨ اقرأ الإعلان بالذكاء الاصطناعي"}</button>`
        : `<div class="adf-note">✨ القراءة الذكية موقفة من الإعدادات — عبّي البيانات يدوي</div>`) : ""}
      ${f.aiMsg ? `<div class="adf-note ${f.ai === "done" ? "ok" : f.ai === "err" ? "err" : ""}">${esc(f.aiMsg)}</div>` : ""}
      ${p ? `<div class="adf-place"><span class="ic">📍</span><div class="t"><b>${esc(p.name || "المحل")}</b><small>${esc(p.address || [p.district, p.city].filter(Boolean).join("، "))}</small></div>
          ${fixedPlace ? "" : `<button class="adf-lnk" id="adfPlaceChg">تغيير</button>`}</div>
          ${fixedPlace ? "" : `<a class="adf-lnk" style="font-size:13px" target="_blank" rel="noopener" href="https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lng}&query_place_id=${encodeURIComponent(p.placeId)}">تأكد من الموقع في Google Maps ↗</a>`}`
      : `<label>📍 وين المحل؟<div class="adf-search"><input id="adfQ" placeholder="اسم المحل + المدينة أو الحي" value="${esc(f.q)}"><button id="adfFind">${f.searching ? `<span class="spin"></span>` : "بحث"}</button></div></label>
        ${f.results ? (f.results.length ? `<div class="adf-res">${f.results.map((r, i) => `<button data-r="${i}"><b>${esc(r.name)}</b><small>${esc(r.address)}</small></button>`).join("")}</div>`
          : `<div class="adf-note">ما لقينا المحل في Google. جرّب اسم ثاني أو أضف المدينة.</div>`) : ""}`}
      <label>اسم المحل<input id="adfName" value="${esc(f.name)}" maxlength="100"></label>
      <label>عنوان العرض *<input id="adfTitle" value="${esc(f.title)}" maxlength="150" placeholder="مثال: عرض الوطن الجامبو"></label>
      <div class="adf-row"><label>السعر (ر.س)<input id="adfPrice" inputmode="decimal" value="${esc(f.price)}" placeholder="اختياري"></label>
        <label>السعر قبل الخصم<input id="adfOrig" inputmode="decimal" value="${esc(f.orig)}" placeholder="اختياري"></label></div>
      <label>الوصف<textarea id="adfDesc" maxlength="2000">${esc(f.desc)}</textarea></label>
      <div class="adf-row"><label>جوال المحل<input id="adfPhone" inputmode="tel" value="${esc(f.phone)}"></label>
        <label>يختفي بعد (أيام)<input id="adfDays" type="number" min="1" max="${AD_MAX_DAYS}" value="${esc(f.days)}"></label></div>
      ${categories.length ? `<label>التصنيف</label><div class="adf-chips">${categories.map((c) => `<button data-cat="${esc(c)}" class="${f.category === c ? "on" : ""}">${esc(c)}</button>`).join("")}</div>` : ""}
      <button class="adf-save" id="adfSave" ${ready() && !f.saving ? "" : "disabled"}>${f.saving ? `<span class="spin"></span> ننشر…` : "🚀 انشر على الخريطة"}</button>
      ${!ready() ? `<small style="opacity:.65;text-align:center">${!f.file ? "اختر صورة الإعلان" : !f.place ? "حدّد موقع المحل" : "اكتب عنوان العرض"} عشان تقدر تنشر</small>` : ""}
    </div>`;
    bind();
  };

  const keep = () => {
    const v = (id) => el.querySelector("#" + id)?.value;
    if (v("adfName") != null) { f.name = v("adfName"); f.title = v("adfTitle"); f.price = v("adfPrice"); f.orig = v("adfOrig"); f.desc = v("adfDesc"); f.phone = v("adfPhone"); f.days = v("adfDays"); }
    if (v("adfQ") != null) f.q = v("adfQ");
  };
  const redraw = () => { keep(); draw(); };

  async function find() {
    keep(); if (!f.q.trim()) return;
    f.searching = true; draw();
    try { f.results = await searchPlaces(mapsKey, f.q.trim(), opts.near); }
    catch (e) { console.warn(e); f.results = []; toast("تعذّر البحث في Google Maps"); }
    f.searching = false; draw();
  }

  function bind() {
    const $ = (id) => el.querySelector("#" + id);
    $("adfFile").onchange = (e) => {
      const file = e.target.files?.[0]; if (!file) return;
      keep(); if (f.preview) URL.revokeObjectURL(f.preview);
      f.file = file; f.preview = URL.createObjectURL(file); f.ai = "idle"; f.aiMsg = "";
      draw();
      if (aiOn && opts.aiFirst !== false) runAi();
    };
    $("adfAi")?.addEventListener("click", runAi);
    $("adfFind")?.addEventListener("click", find);
    $("adfQ")?.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); find(); } });
    $("adfPlaceChg")?.addEventListener("click", () => { keep(); f.place = null; f.results = null; draw(); });
    el.querySelectorAll("[data-r]").forEach((b) => b.onclick = () => {
      keep(); const r = f.results[+b.dataset.r]; f.place = r;
      if (!f.name.trim()) f.name = r.name; if (!f.phone.trim()) f.phone = r.phone; draw();
    });
    el.querySelectorAll("[data-cat]").forEach((b) => b.onclick = (e) => { e.preventDefault(); keep(); f.category = b.dataset.cat; draw(); });
    ["adfTitle"].forEach((id) => $(id)?.addEventListener("input", () => { const ok = !!(f.file && f.place && $(id).value.trim()); $("adfSave").disabled = !ok || f.saving; }));
    $("adfSave").onclick = save;
  }

  async function runAi() {
    if (!f.file || f.ai === "busy") return;
    keep(); f.ai = "busy"; f.aiMsg = ""; draw();
    try {
      const d = await readAd(fbApp, f.file, { model: config.aiModel, categories });
      if (d.title) f.title = d.title;
      if (d.description) f.desc = d.description;
      if (d.price) f.price = String(d.price);
      if (d.originalPrice) f.orig = String(d.originalPrice);
      if (d.days) f.days = Math.min(AD_MAX_DAYS, d.days);
      if (d.phones[0] && !f.phone) f.phone = d.phones[0];
      if (d.category) f.category = d.category;
      if (d.storeName && (!fixedPlace || !f.name)) f.name = d.storeName;
      f.tags = d.tags; f.city = d.city; f.district = d.district;
      const where = [d.district && "حي " + d.district, d.city].filter(Boolean).join(" ");
      f.ai = "done";
      f.aiMsg = `✅ قرأنا الإعلان${d.storeName ? `: ${d.storeName}` : ""}${!d.isOffer ? " (إعلان خدمة بدون سعر)" : ""}. راجع البيانات قبل النشر.`;
      if (!f.place && (d.storeName || d.address)) {
        f.q = [d.storeName || d.storeNameEn, d.address && !d.address.includes(d.city) ? d.address : "", where].filter(Boolean).join(" ").slice(0, 150);
        draw(); await find(); return;
      }
    } catch (e) { console.warn("ai", e); f.ai = "err"; f.aiMsg = aiErrorText(e); }
    draw();
  }

  async function save() {
    keep(); if (!ready() || f.saving) return;
    f.saving = true; draw();
    try {
      const id = await saveAdOffer(db, user, f);
      toast("انتشر العرض على الخريطة ✅");
      onDone(id, f);
    } catch (e) {
      console.error(e); f.saving = false; draw();
      toast(/permission/i.test(String(e?.message)) ? "ما عندك صلاحية رفع عروض الإعلانات" : (e?.message || "تعذّر النشر، جرّب مرة ثانية"));
    }
  }

  draw();
  return { refresh: redraw, dirty: () => !!(f.file || f.title.trim() || f.place && !fixedPlace) };
}
