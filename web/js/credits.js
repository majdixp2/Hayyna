// رصيد العروض: الإعدادات الافتراضية + حساب الخدمات النشطة لكل عرض.
// الأسعار والخدمات كلها تنعدّل من لوحة الأدمن (config/app.credits) بدون تحديث للتطبيق.

// أنواع التأثير اللي يفهمها لكوفر (أي خدمة جديدة يضيفها الأدمن تختار واحد منها)
export const EFFECTS = {
  publish: { label: "نشر العرض", hint: "العرض يظهر على الخريطة وفي القائمة طوال المدة" },
  feature: { label: "تمييز على الخريطة", hint: "حلقة صفراء مع وميض، ويطلع أول في الترتيب" },
  listTop: { label: "إعلان بارز في صفحة العروض", hint: "يتثبّت فوق قائمة العروض في قسم «إعلانات بارزة»" },
  spotlight: { label: "🚀 عرض سوبر", hint: "كل مزايا التمييز + اسم المحل ظاهر دايم على الخريطة، أول بطاقة في الشريط، وبطاقة «عرض سوبر قريب منك» مرة باليوم لأهل الحي (3 كم)" },
};

export const DEFAULT_ITEMS = {
  publish: { name: "📢 نشر عرض", effect: "publish", unit: "day", price: 3, days: 1, active: true, order: 0 },
  feature: { name: "✨ تمييز 3 أيام", effect: "feature", unit: "fixed", price: 10, days: 3, active: true, order: 1 },
  boost: { name: "🚀 عرض سوبر أسبوع", effect: "spotlight", unit: "fixed", price: 25, days: 7, active: true, order: 2 },
  listtop: { name: "📌 إعلان بارز في صفحة العروض", effect: "listTop", unit: "fixed", price: 40, days: 7, active: false, order: 3 },
};
export const DEFAULT_PACKS = [{ points: 50, price: 50 }, { points: 100, price: 98 }, { points: 500, price: 475 }];

// الاسم القديم «إبراز» صار «🚀 عرض سوبر» (لو الإعدادات المحفوظة فيها الاسم القديم)
const superName = (items) => Object.fromEntries(Object.entries(items).map(([k, it]) => [k, it?.name && /إبراز|مُبرز/.test(it.name) ? { ...it, name: it.name.replace(/🚀\s*/, "").replace(/الإبراز|إبراز|مُبرز/, "🚀 عرض سوبر") } : it]));
export const creditsOf = (config) => {
  const c = config?.credits || {};
  return { enabled: !!c.enabled, enabledAt: c.enabledAt || null, graceUntil: c.graceUntil || null, graceDays: c.graceDays ?? 14, spotlightMax: c.spotlightMax ?? 3,
    welcome: c.welcome || 0, items: superName(c.items || DEFAULT_ITEMS), packs: c.packs || DEFAULT_PACKS };
};
export const itemList = (items) => Object.entries(items || {}).map(([id, it]) => ({ id, ...it })).sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
export const costOf = (it, days) => it.unit === "day" ? it.price * days : it.price;

export const EXCLUSIVE_DEFAULT = { enabled: true, label: "حصري في لكوفر", icon: "💎", color: "#7C3AED" };
export const exclusiveOf = (config) => ({ ...EXCLUSIVE_DEFAULT, ...(config?.exclusive || {}) });

/** مكافأة الحصري: خصم على «النشر» بس للعروض الحصرية (100% = مجاني). يتحكم فيه الأدمن. */
export const rewardOf = (config) => {
  const r = config?.exclusive?.reward || {}, pct = Math.max(0, Math.min(100, Math.round(+r.percent || 0)));
  return { on: !!r.enabled && exclusiveOf(config).enabled && pct > 0, percent: pct, free: pct >= 100 };
};
/** السعر بعد خصم الحصري (يقرّب لفوق عشان يبقى رقم صحيح) */
export const discounted = (cost, pct) => Math.ceil((cost * (100 - pct)) / 100);
export const rewardText = (rw) => rw.free ? "نشره مجاني 🎁" : `خصم ${rw.percent}% على نشره 🎁`;

const ms = (t) => (t && t.toMillis ? t.toMillis() : (typeof t === "number" ? t : 0));

/** خدمات العرض النشطة الحين من قائمة boosts. */
export function activeEffects(boosts, offerId, now = Date.now()) {
  const out = { feature: false, listTop: false, spotlight: false, publishUntil: 0 };
  for (const b of boosts) {
    if (b.offerId !== offerId) continue;
    const s = ms(b.startsAt), e = ms(b.endsAt);
    if (b.effect === "publish") out.publishUntil = Math.max(out.publishUntil, e);
    else if (s <= now && now < e) out[b.effect] = true;
  }
  if (out.spotlight) out.feature = true; // الإبراز يشمل التمييز
  return out;
}

/** كم إبراز نشط في نفس الحي (للحد الأقصى) */
export function spotlightsInDistrict(boosts, offers, district, now = Date.now()) {
  const key = String(district || "").replace(/^حي\s+/, "").trim();
  const ids = new Set(boosts.filter((b) => b.effect === "spotlight" && ms(b.startsAt) <= now && now < ms(b.endsAt)).map((b) => b.offerId));
  return offers.filter((o) => ids.has(o.id) && String(o.district || "").replace(/^حي\s+/, "").trim() === key).length;
}

/** عرض منشور قبل تشغيل الرصيد: مجاني لين نهاية فترة السماح (0 = مو منها). */
export function graceEnd(o, credits) {
  const since = ms(credits.enabledAt);
  if (!credits.enabled || !since || ms(o.createdAt) >= since) return 0;
  return ms(credits.graceUntil) || Infinity; // بدون تاريخ سماح (إعداد قديم) = مجاني
}

/** هل العرض ظاهر؟ (لما يكون الرصيد مفعّل: لازم نشر مدفوع، أو فترة سماح للعروض القديمة) */
export function isPaidUp(o, boosts, credits, now = Date.now()) {
  if (!credits.enabled) return true;
  if (graceEnd(o, credits) > now) return true;
  return activeEffects(boosts, o.id, now).publishUntil > now;
}
