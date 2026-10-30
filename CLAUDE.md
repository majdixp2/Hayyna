# ملاحظات مشروع لكوفر (LACOVER) — اقرأها أول كل جلسة

> ملف مرجعي للهيكل والقرارات. حدّثه بعد أي ميزة أو قرار مهم (قسم «سجل القرارات» تحت).
> صاحب المشروع: majdixp2@gmail.com (المالك/الأدمن). يتكلم عربي **سعودي**، والخطوات تنكتب **كل خطوة بسطر** (بدون أسهم ←).

## الفكرة
خريطة عروض المحلات حولك: المحلات تنشر عروض ومنيو، الزبون يشوف ويطلب (استلام / توصيل / من السيارة) والطلب يكمّل على واتساب المحل.
الاسم السابق **حيّنا (Hayyna)** — أرشيفه في الفرع `archive/hayyna-2026-10-28`.

## الهيكل
| الجزء | المكان | التقنية |
|---|---|---|
| تطبيق أندرويد | `app/` (package `com.example`, applicationId `com.hayyna.app`) | Kotlin + Jetpack Compose (Material3) |
| الموقع (نفس التطبيق) | `web/` (`index.html`, `js/*.js`, `app.css`) | Vanilla ES modules، Firebase JS 11.10.0 من gstatic |
| لوحة الإدارة | `web/admin/` (`app.js`, `style.css`) | Vanilla JS |
| قواعد Firestore | `firestore.rules` + اختبارات `tests/rules/*.test.mjs` | Firebase emulator |

**أهم ملفات أندرويد:** `ui/MerchantViewModel.kt` (كل المنطق)، `ui/MerchantStoreScreen.kt` (الشاشة الرئيسية)، `data/repository/RestaurantOfferRepository.kt` (Firestore)، `ui/components/*` (الواجهات).
**أهم ملفات الويب:** `js/app.js` (الخريطة/العروض/الحساب)، `js/merchant.js` (لوحة التاجر)، `js/catalog-ui.js` (المنيو/السلة/الطلبات)، `js/catalog.js` (منطق مشترك + `FEATURES`)، `js/logic.js` (أدوات)، `js/map.js` (Google Maps + احتياط OSM)، `js/scout.js` (عروض الإعلانات)، `js/staff.js` (موظفين المتجر).
**أي ميزة تنبني في الاثنين (أندرويد + ويب)** إلا لو اتفقنا غير كذا.

## Firebase والخدمات
- مشروع: `gen-lang-client-0536850806` — قاعدة Firestore **مسمّاة**: `ai-studio-android-307cc811-cd30-4d36-b6c1-76e9fb2ac335`.
- الموقع الحي: https://gen-lang-client-0536850806.web.app (الإدارة: `/admin`).
- خطة **Spark (مجانية)** = ما فيه Cloud Functions. أي سيرفر مستقبلاً → **Vercel**.
- الدخول: Google + رابط الإيميل + **Email/Password مفعّل** (لحسابات الموظفين الداخلية).
- رفع الصور: Cloudinary (cloud `ffhqn5dw`, preset `hayyna_offers`).
- الذكاء الاصطناعي: Gemini عبر **Firebase AI Logic** (أندرويد `AiAutofill.kt`، ويب `firebase-ai.js`). مفتاح تشغيل `aiAutofillEnabled` + `aiModel` في `config/app`.
- مفاتيح Google Maps:
  - **«Hayyna Maps»** = مفتاح **أندرويد** (Maps SDK + Places). ⚠️ لا تغيّر قيوده لـ Websites.
  - **«API key 5»** = مفتاح **الويب** (Maps JS + Places New)، مقيّد على `web.app/*` و `firebaseapp.com/*`.
- إعدادات التطبيق كلها في وثيقة `config/app` (الأدمن يغيّرها). مزايا الطلب في `catalog.features.*` (الافتراضي **مفعّل** لو ما انكتب `false`).

## النشر (GitHub Actions — repo `majdixp2/hayyna`)
- `deploy-firebase.yml`: أي push على `main` يمس `web/**` أو القواعد → ينشر الموقع والقواعد.
- `build-apk.yml`: push على `main` أو تشغيل يدوي → APK في Artifacts باسم `lacover-apk`.
- `rules-tests.yml`: اختبارات القواعد (أي ملف test جديد لازم ينضاف لسطر التشغيل فيه).
- طريقة الشغل: فرع `feat/...` أو `fix/...` → تشغيل build-apk على الفرع (`gh api .../dispatches`) → PR + merge عبر REST API (GraphQL محجوب في الجلسة).
- قراءة أخطاء البناء: `gh api repos/.../check-runs/<job>/annotations`.
- بيئة الجلسة: npm/pip الخارجي غالباً محجوب، الموقع الحي ما ينفتح من الصندوق؛ المعاينة بـ Playwright مع stubs لـ Firebase (سكربتات في scratchpad).

## الهوية
- كحلي `#10318F`، ذهبي `#F4B400`، فاتح `#F7F8FB`. خط Montserrat 800 (إنجليزي) و Cairo 800 (عربي).
- الشعار: دبوس (دائرة + مثلث) فيه فقاعة كلام و `%`. هندسته في صندوق 100×120: دائرة r50، مثلث `M27 86 L50 120 L73 86 Z`، دائرة داخلية r33، ذيل `M77.4 64.6 L80.7 82.9 L69.1 74.4 Z`.

## الأدوار والصلاحيات
- **المالك/الأدمن**: الإيميل في `ADMIN_EMAILS` (`web/config.js`) و `AdminConfig.kt` و `isAdmin()` بالقواعد.
- **فريق الإدارة**: `staff/{email}` فيه `perms`: `review, verify, messages, merchants, credits, scout`.
- **صاحب محل / زبون**: `users/{uid}.role` = `merchant` | `customer`.
- **موظف متجر** (ميزة `staff`): حساب داخلي `<code>-<n>-<rand>@staff.lacover.app` كلمة مروره رمز 6 أرقام. يتعرّف من الدومين. صلاحيات `orders`, `stock` فقط.

## الميزات المهمة وقراراتها
- **رصيد النشر (credits)**: لما يكون مفعّل، العرض يحتاج «نشر» مدفوع ليظهر — **إلا عروض الإعلانات** (`source:"ad"`) معفية.
- **📷 عروض من إعلانات**: الفريق (`scout`) يرفع صورة إعلان لمحل Google غير مسجّل → `merchantId = "poi_<placeId>"`, `source:"ad"`, `placeId`, `scoutedBy`، مدة افتراضية `adScoutDays` (7، حد 31). شارة «📷 من إعلان المحل». الأدمن ينقله لمتجر لما يسجّل.
- **البحث عن محل**: المسجّلين من `stores` + زر «في محلات Google» (Places Text Search) يفتح بطاقة المحل (ادعه / أنا صاحب المحل / 📷 ارفع عرض).
- **شعار الشريط**: ضغطة = قائمة، ضغطة طويلة = تحديث، نقطة ذهبية = عروض جديدة بعد آخر تحديث.
- **متابعة الطلب الحي**: شريط أو دائرة (`catalog.tracker` = bar | bubble | off).
- **👥 موظفين المتجر (المرحلة ١)**: رقم متجر 5 أرقام (`storeCodes/{code}`)، دليل الدخول `staffLogin/{code}-{n}` (عام، بدون أسرار)، الملف `storeStaff/{uid}`. أدوار: كاشير (orders+stock) / مطبخ (orders). «ما انستلم» لصاحب المحل بس. QR دخول يقفله الأدمن (`staffQr`). إعادة الرمز = حساب جديد بنفس الرقم.
- **❌ نفد اليوم**: الأصناف `items.available`؛ العروض `offers.soldOutUntil` (لين منتصف الليل). يوقف الطلب في كل مكان + فحص من السيرفر قبل الإرسال. **🔔 ذكّرني لما يتوفر** محلي (localStorage / SharedPreferences) + عدّاد `notifyCount` يشوفه الموظف. التنبيه يشتغل والتطبيق مفتوح فقط (ما فيه push).
- **إعادة نشر عرض منتهي**: المدة تنحسب من اليوم، وخيار «بدون تغيير» يختفي.
- **الخريطة على الويب**: Google وإذا فشل المفتاح → OpenStreetMap تلقائياً. شريط حقوق Google إلزامي (ما ينشال).

## مؤجل / قادم
- ربط الكاشيرات (Foodics / Deliverect) — مؤجل بطلب صاحب المشروع.
- موظفين المتجر **المرحلة ٢**: صلاحية «مدير» مع قفل الخانات (يحتاج تعديل كل القواعد من `merchantId == auth.uid` لـ «المتجر الفعّال»).
- إشعارات push حقيقية (تحتاج سيرفر Vercel أو خطة Blaze).
- فصل تطبيق الزبون عن «لكوفر للأعمال» — مؤجل لين يكبر عدد المحلات.
- رفع العروض عبر واتساب (بوت) — بعد تجربة الطريقتين الحاليتين.
- تذكيرات للمالك: يقرر اسم الحزمة قبل Google Play (`com.hayyna.app`)، إيميل رسمي للكوفر (التطبيق وصفحة الخصوصية فيها `hayyna.sa@gmail.com`).

## سجل القرارات (الأحدث تحت)
- 2026-10-28: تغيير الاسم من حيّنا إلى لكوفر، والاحتفاظ بالمعرّفات الداخلية القديمة.
- 2026-10-30: عروض الإعلانات (يدوي من الخريطة + ذكاء اصطناعي من الإدارة)، معفية من رصيد النشر.
- 2026-10-30: البحث عن المحلات (مسجّلة + Google)، قائمة الشعار.
- 2026-10-30: موظفين المتجر برقم + رمز سري بدل الإيميل (رفض الموظفين لإعطاء إيميلاتهم). الدخول للزبون أولاً، وأصحاب المحلات في قسم منفصل.
- 2026-10-31: «نفد اليوم» للعروض + منع الطلب + ذكّرني لما يتوفر.
