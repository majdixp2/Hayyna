// اللغة الإنجليزية لواجهة لكوفر على الويب.
// العربي هو الأساس: لما يختار المستخدم English نترجم نصوص الواجهة وهي تنرسم (MutationObserver)،
// ونترك محتوى التجار (أسماء المحلات، عناوين العروض، الأحياء) مثل ما كتبوه.

let LANG = "ar";
try { LANG = localStorage.getItem("hayyna_lang") || "ar"; } catch {}
export const lang = () => LANG;

// عناصر فيها محتوى التاجر: ما نترجمها
const SKIP = ".oc-items, .co-line > span, .icard .ic-main > b, .ic-main small, .ks-sub, .otr-tx small, .sf-store b, .nt, .desc:not(.tr), .nm, .title, .store-row span, .mname, .amb-row b, .ch-main b, .au-main b, .b-name, .about, .otitle, .sname, script, style, textarea";

// ---- القاموس والقواعد: ملف واحد مشترك مع تطبيق الأندرويد (web/i18n/en.json) ----
// rules: [نمط, بديل] للنصوص اللي فيها أرقام. dict: النص العربي ← الإنجليزي (الأطول يتطبّق أول).
let D = {}, RULES = [];
if (LANG === "en") {
  try {
    const j = await (await fetch(new URL("../i18n/en.json", import.meta.url))).json();
    D = j.dict || {}; RULES = (j.rules || []).map(([p, r]) => [new RegExp(p, "g"), r]);
  } catch (e) { console.warn("i18n", e); }
}
const KEYS = Object.keys(D).sort((a, b) => b.length - a.length);
const esc = (k) => k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
// الكلمة كاملة بس (ما نقص من وسط كلمة عربية)
const L = "\\u0621-\\u064A\\u064B-\\u0652\\u0671-\\u06D3"; // حروف عربية (بدون علامات الترقيم)
const RE = new RegExp(`(?<![${L}])(${KEYS.map(esc).join("|")})(?![${L}])`, "g");
const AR = /[؀-ۿ]/;

export function tr(text) {
  if (LANG !== "en" || !text || !AR.test(text)) return text;
  const lead = text.match(/^\s*/)[0], trail = text.match(/\s*$/)[0], core = text.trim();
  if (D[core]) return lead + D[core] + trail;
  let out = text;
  for (const [re, rep] of RULES) out = out.replace(re, rep);
  out = out.replace(RE, (m) => D[m] ?? m);
  return out;
}

function translateNode(root) {
  if (root.nodeType === 3) { const p = root.parentElement; if (p && !p.closest(SKIP)) { const t = tr(root.nodeValue); if (t !== root.nodeValue) root.nodeValue = t; } return; }
  if (root.nodeType !== 1 || root.closest?.(SKIP)) return;
  for (const a of ["placeholder", "aria-label", "title"]) if (root.hasAttribute?.(a)) { const v = root.getAttribute(a), t = tr(v); if (t !== v) root.setAttribute(a, t); }
  const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
    acceptNode: (n) => n.nodeType === 1 && n.matches(SKIP) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT,
  });
  let n = w.nextNode();
  while (n) {
    if (n.nodeType === 3) { const t = tr(n.nodeValue); if (t !== n.nodeValue) n.nodeValue = t; }
    else for (const a of ["placeholder", "aria-label", "title"]) if (n.hasAttribute(a)) { const v = n.getAttribute(a), t = tr(v); if (t !== v) n.setAttribute(a, t); }
    n = w.nextNode();
  }
}

export function startI18n() {
  if (LANG !== "en") return;
  document.documentElement.lang = "en"; document.documentElement.dir = "ltr";
  translateNode(document.body);
  document.title = tr(document.title);
  new MutationObserver((muts) => {
    for (const m of muts) {
      if (m.type === "characterData") translateNode(m.target);
      else m.addedNodes.forEach(translateNode);
    }
  }).observe(document.body, { childList: true, subtree: true, characterData: true });
  new MutationObserver(() => { const t = tr(document.title); if (t !== document.title) document.title = t; })
    .observe(document.querySelector("title"), { childList: true, characterData: true, subtree: true });
  // نوافذ المتصفح (تأكيد/سؤال)
  const c = window.confirm.bind(window), p = window.prompt.bind(window);
  window.confirm = (msg) => c(tr(msg)); window.prompt = (msg, d) => p(tr(msg), d);
}
