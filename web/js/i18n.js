// اللغة الإنجليزية لواجهة حيّنا على الويب.
// العربي هو الأساس: لما يختار المستخدم English نترجم نصوص الواجهة وهي تنرسم (MutationObserver)،
// ونترك محتوى التجار (أسماء المحلات، عناوين العروض، الأحياء) مثل ما كتبوه.

let LANG = "ar";
try { LANG = localStorage.getItem("hayyna_lang") || "ar"; } catch {}
export const lang = () => LANG;
export const isEn = () => LANG === "en";

// عناصر فيها محتوى التاجر: ما نترجمها
const SKIP = ".nt, .desc:not(.tr), .nm, .title, .store-row span, .mname, .amb-row b, .ch-main b, .au-main b, .b-name, .about, .otitle, .sname, script, style, textarea";

// ---- قواعد للنصوص المتغيّرة (أرقام في وسطها) ----
const U = { "دقيقة": "min", "ساعة": "h", "ساعات": "h", "يوم": "days", "أيام": "days" };
const ORD = { "الأول": "1", "الثاني": "2", "الثالث": "3", "الرابع": "4", "الخامس": "5", "السادس": "6", "السابع": "7", "الثامن": "8", "التاسع": "9", "العاشر": "10" };
const RULES = [
  [/باقي (\d+) (دقيقة|ساعة|ساعات|يوم|أيام)/g, (_, n, u) => `${n} ${U[u]} left`],
  [/باقي ساعتين/g, "2 h left"], [/باقي ساعة/g, "1 h left"], [/باقي يومين/g, "2 days left"], [/باقي يوم/g, "1 day left"],
  [/يبدأ بعد (\d+) دقيقة/g, "Starts in $1 min"], [/يبدأ اليوم/g, "Starts today"], [/يبدأ بكرة/g, "Starts tomorrow"],
  [/من اليوم (\S+) إلى (\S+)/g, (_, a, b) => `Days ${ORD[a] || a}–${ORD[b] || b}`],
  [/اليوم (\S+) و(\S+)/g, (_, a, b) => ORD[a] ? `Days ${ORD[a]} & ${ORD[b] || b}` : _],
  [/اليوم (\S+)/g, (_, a) => ORD[a] ? `Day ${ORD[a]}` : _],
  [/(\d+(?:\.\d+)?) كم/g, "$1 km"], [/(\d+) متر/g, "$1 m"],
  [/(\d+(?:\.\d+)?) ر\.س/g, "SAR $1"],
  [/(\d+(?::\d+)?) ص(?![؀-ۿ])/g, "$1 AM"], [/(\d+(?::\d+)?) م(?![؀-ۿ])/g, "$1 PM"],
  [/خصم (\d+)%/g, "$1% off"], [/جديد -(\d+)%/g, "New -$1%"],
  [/دعيت (\d+) (محل|محلات)/g, (_, n) => `You invited ${n} ${n === "1" ? "shop" : "shops"}`],
  [/باقي ([\d,]+) نقطة وتصير/g, "$1 points to become"],
  [/(\d+) (عرض|عروض)/g, (_, n) => `${n} ${n === "1" ? "offer" : "offers"}`],
  [/(\d+) (يوم|أيام)/g, (_, n) => `${n} ${n === "1" ? "day" : "days"}`],
  [/(\d+) نقطة/g, "$1 pts"],
  [/مدة العرض = (\d+) أيام/g, "Offer lasts $1 days"],
  [/عروض \((\d+)\)/g, "offers ($1)"],
];

// ---- القاموس (الأطول أول عشان ما تنقص الجمل) ----
const D = {
  // عام
  "حيّنا — عروض المحلات حولك على الخريطة": "Hayyna — Shop offers around you on the map",
  "حيّنا — عروض المحلات حولك": "Hayyna — Shop offers around you",
  "حيّنا": "Hayyna", "الخريطة": "Map", "العروض": "Offers", "المفضلة": "Favorites", "المفضلة ❤️": "Favorites ❤️", "حسابي": "Account",
  "إغلاق": "Close", "رجوع": "Back", "مسح": "Clear", "حفظ": "Save", "تم": "Done", "تمام": "OK", "إلغاء": "Cancel", "حذف": "Delete",
  "تعديل": "Edit", "إرسال": "Send", "متابعة": "Continue", "أو": "or", "إزالة": "Remove", "اختر": "Choose", "الكل": "All",
  "قريباً": "Soon", "منتهي": "Expired", "نشط": "Active", "مفضلة": "Favorite", "مشاهدة": "views", "مسح الفلاتر": "Clear filters",
  // البحث والفلاتر
  "ابحث: شاورما، قهوة، جوالات، اسم حي…": "Search: shawarma, coffee, phones, district…",
  "ابحث في العروض: شاورما، قهوة، اسم محل…": "Search offers: shawarma, coffee, shop name…",
  "ابحث عن حي…": "Search a district…", "حولي": "Near me", "الفلاتر": "Filters", "الترتيب": "Sort by",
  "📍 الأقرب": "📍 Nearest", "🔥 أكبر خصم": "🔥 Biggest discount", "💰 الأرخص": "💰 Cheapest", "🆕 الأحدث": "🆕 Newest",
  "● مفتوح الآن": "● Open now", "● مفتوح الآن فقط": "● Open now only", "✨ جديدنا": "✨ New items", "🆕 نزل اليوم": "🆕 Posted today",
  "📍 تصفّح حي": "📍 Browse a district", "📍 حولي (موقعي)": "📍 Near me (my location)", "متاجر": "shops",
  "ما لقينا عروض مطابقة": "No matching offers", "جرّب كلمة ثانية أو شيل الفلاتر": "Try another word or clear the filters",
  "لا توجد عروض حالياً": "No offers right now", "المحلات تنزّل عروضها أول بأول 🔥": "Shops post new offers all the time 🔥",
  "ما فيه عروض مطابقة": "No matching offers", "نطاق العروض": "Offers range",
  "📍 أنت في:": "📍 You're in:", "🎯 حولك 5 كم •": "🎯 Within 5 km •", "📍 عروض حولك": "📍 Offers near you", "عروض حولك (5 كم) 🎯": "Offers near you (5 km) 🎯",
  "ما فيه عروض في حيّك للحين، شوف اللي حولك 🎯": "No offers in your district yet — showing nearby 🎯",
  "ما فيه عروض قريبة منك للحين، نعرض لك كل الأحياء": "No offers near you yet — showing all districts",
  "اضغط رجوع مرة ثانية للخروج من حيّنا": "Press back again to exit Hayyna",
  // البطاقات وتفاصيل العرض
  "مفتوح": "Open", "مغلق": "Closed", "● مفتوح": "● Open", "● مغلق": "● Closed", "مفتوح 24 ساعة": "Open 24 hours", "يومياً": "Daily",
  "🚀 عرض سوبر": "🚀 Super offer", "🚀 عرض سوبر قريب منك": "🚀 Super offer near you", "🚀 عرض سوبر أسبوع": "🚀 Super offer (1 week)", "شوف العرض": "See offer", "لاحقاً": "Later",
  "✨ جديد": "✨ New", "✨ منتج جديد": "✨ New item", "توصيل مجاني": "Free delivery", "توصيل": "Delivery", "• للطلبات فوق": "• min order",
  "• مجاني فوق": "• free over", "على جميع الأصناف": "on all items", "خصم": "Discount", "انتهى العرض": "Offer ended",
  "🧭 الاتجاهات إلى المحل": "🧭 Directions to the shop", "اتجاهات": "Directions", "💬 واتساب": "💬 WhatsApp", "واتساب": "WhatsApp",
  "📞 اتصال": "📞 Call", "💬 طلب واتساب": "💬 Order on WhatsApp", "↗︎ مشاركة": "↗︎ Share", "↗︎ شارك صفحة المتجر": "↗︎ Share store page",
  "🏪 صفحة المتجر ›": "🏪 Store page ›", "🚩 بلاغ عن العرض": "🚩 Report this offer", "إرسال البلاغ": "Send report",
  "وصل بلاغك، شكراً لك 🙏": "Report received, thank you 🙏", "تعذّر إرسال البلاغ، حاول مرة ثانية": "Couldn't send the report, try again",
  "عرض غير حقيقي أو مضلل": "Fake or misleading offer", "السعر أو الخصم غير صحيح": "Wrong price or discount", "المحل مقفل أو غير موجود": "Shop closed or doesn't exist",
  "محتوى غير لائق": "Inappropriate content", "أخرى": "Other", "مرحباً، أود طلب العرض:": "Hi, I'd like to order this offer:",
  "العرض انتهى أو غير متاح": "This offer ended or isn't available", "صفحة المتجر غير متاحة": "Store page not available",
  "🔥 العروض الحالية (": "🔥 Current offers (", "📍 الفروع (": "📍 Branches (", "المنيو": "Menu", "الخدمات والأسعار": "Services & prices",
  "المنتجات والأسعار": "Products & prices", "💰 الأسعار تبدأ من": "💰 Prices from", "على حيّنا": "on Hayyna",
  "انحفظ في المفضلة ❤️": "Saved to favorites ❤️", "نسخنا الرابط ✅": "Link copied ✅", "انسخ الرابط:": "Copy the link:",
  "ما عندك مفضلة للحين": "No favorites yet", "اضغط القلب على أي عرض عشان تحفظه هنا": "Tap the heart on any offer to save it here",
  "فعّل الموقع عشان نعرض عروض حيّك 📍": "Turn on location to see offers in your district 📍",
  // الموقع
  "موقعي": "My location", "المتصفح ما يدعم تحديد الموقع": "Your browser doesn't support location",
  "ما قدرنا نحدد موقعك — تأكد إن الموقع (GPS) شغّال في الجوال": "Couldn't find your location — make sure GPS is on",
  "الموقع مقفول لحيّنا": "Location is blocked for Hayyna",
  "عشان نعرض لك عروض حيّك والأقرب لك، فعّل إذن الموقع:": "To show offers in your district and nearest to you, allow location:",
  "جرّبت، حدّد موقعي": "Done — locate me", "اضغط مطوّلاً على أيقونة": "Long-press the icon", "معلومات التطبيق": "App info",
  "الأذونات": "Permissions", "الموقع": "Location", "السماح أثناء الاستخدام": "Allow while using the app",
  "إذا ما لقيتها: افتح كروم ← ⋮ ← الإعدادات ← إعدادات المواقع ← الموقع، وشيل حيّنا من «محظور»": "If you can't find it: Chrome → ⋮ → Settings → Site settings → Location, and remove Hayyna from “Blocked”",
  "اضغط أيقونة": "Tap the", "القفل / الإعدادات": "lock / settings icon", "جنب الرابط فوق": "next to the address bar",
  "سماح": "Allow", "حدّث الصفحة واضغط زر موقعي": "Refresh and tap the location button", "افتح": "Open", "في الآيفون ←": "on iPhone →",
  "الخصوصية والأمان": "Privacy & Security", "خدمات الموقع": "Location Services", "وتأكد إنها شغّالة": "and make sure it's on",
  "انزل لـ": "Scroll to", "واختر": "and choose", "«أثناء الاستخدام»": "“While Using”", "أو «اسأل»": "or “Ask”",
  "ارجع لحيّنا واضغط زر موقعي مرة ثانية": "Go back to Hayyna and tap the location button again",
  // التثبيت
  "ثبّت حيّنا على جوالك": "Install Hayyna on your phone", "ثبّت حيّنا على جوالك 📲": "Install Hayyna on your phone 📲", "📲 ثبّت": "📲 Install",
  "يفتح بشاشة كاملة مثل التطبيق": "Opens full screen like an app", "يفتح بشاشة كاملة بدون شريط المتصفح، مثل التطبيق تماماً": "Opens full screen without the browser bar, just like an app",
  "اضغط زر المشاركة": "Tap the Share button", "تحت في الشريط": "in the bottom bar", "انزل واختر": "Scroll and choose",
  "«إضافة إلى الشاشة الرئيسية»": "“Add to Home Screen”", "«إضافة»": "“Add”", "، وافتح حيّنا من أيقونته": ", then open Hayyna from its icon",
  "اضغط قائمة المتصفح": "Tap the browser menu", "«تثبيت التطبيق»": "“Install app”", "افتح حيّنا من أيقونته": "Open Hayyna from its icon",
  "تم التثبيت 🎉 افتح حيّنا من أيقونته": "Installed 🎉 Open Hayyna from its icon", "فوق": "at the top",
  // الحساب
  "أهلاً بك في حيّنا": "Welcome to Hayyna", "سجّل دخولك عشان تحفظ مفضلتك، أو تنشر عروض محلك": "Sign in to save favorites or post your shop's offers",
  "أنا من أهل الحي": "I live in the neighborhood", "أدوّر عروض المحلات حولي، وأحفظ اللي يعجبني": "I look for shop offers around me and save what I like",
  "أنا صاحب محل": "I own a shop", "انشر عروضك مجاناً وخلّ أهل الحي يوصلون لك": "Post your offers for free and let neighbors find you",
  "💬 تواصل معنا": "💬 Contact us", "⚙️ الإعدادات": "⚙️ Settings", "الإعدادات": "Settings", "سياسة الخصوصية": "Privacy policy",
  "تسجيل الخروج": "Sign out", "سجّلت خروج": "Signed out", "من أهل الحي 🏡": "Neighbor 🏡", "🏡 من أهل الحي": "🏡 Neighbor", "من أهل الحي": "Neighbor",
  "صاحب محل 🏪": "Shop owner 🏪", "🏪 صاحب محل": "🏪 Shop owner", "من أهل": "Neighbor of", "تعديل البيانات": "Edit profile",
  "❤️ مفضلتي": "❤️ My favorites", "مفضلتي ❤️": "My favorites ❤️", "عرض الكل ‹": "See all ›", "📍 عروض حولك": "📍 Offers near you",
  "حدّد موقعك": "Set location", "مفضلتك فاضية": "Your favorites are empty",
  "اضغط القلب على أي عرض يعجبك، ويظهر هنا عشان ترجع له بسرعة": "Tap the heart on any offer you like and it'll show up here",
  "خلال 24 ساعة — لا يفوتك": "within 24 hours — don't miss it",
  "واتساب الدعم": "Support WhatsApp", "اقتراح، مشكلة، أو محل تبيه ينضم": "Suggestion, problem, or a shop you want to join",
  "المظهر، اللغة، الحساب": "Appearance, language, account", "تواصل معنا": "Contact us",
  "بياناتي": "My details", "الاسم": "Name", "رقم الجوال": "Mobile number", "انحفظت بياناتك ✅": "Your details are saved ✅",
  "كمّل بياناتك 👋": "Complete your details 👋", "خطوة وحدة وتبدأ": "One step and you're in", "اسمك *": "Your name *", "رقم جوالك *": "Your mobile *",
  "نوع الحساب": "Account type", "اكتب اسمك.": "Enter your name.", "اكتب رقم جوال صحيح.": "Enter a valid mobile number.",
  "تعذّر الحفظ.": "Couldn't save.", "تعذّر الحفظ": "Couldn't save", "صار حسابك حساب صاحب محل 🏪": "Your account is now a shop owner account 🏪",
  "صار حسابك حساب تاجر 🏪": "Your account is now a shop owner account 🏪", "رجعنا حسابك لحساب أهل الحي 🏡": "Your account is back to a neighbor account 🏡",
  "المتابعة بحساب Google": "Continue with Google", "أو بالإيميل": "or with email", "✉️ أرسل لي رابط الدخول": "✉️ Email me a sign-in link",
  "بنرسل لك رابط على إيميلك، تضغطه وتدخل مباشرة بدون كلمة سر.": "We'll email you a link — tap it to sign in, no password needed.",
  "اكتب إيميل صحيح": "Enter a valid email", "أرسلنا رابط الدخول إلى": "We sent the sign-in link to", "افتح إيميلك": "Open your email",
  ". اضغط الرابط من نفس الجوال وبتدخل على طول. (شيك على مجلد الرسائل غير المرغوبة لو ما لقيته)": ". Tap the link on this phone to sign in. (Check spam if you can't find it)",
  "اكتب إيميلك لتأكيد الدخول:": "Enter your email to confirm sign-in:", "الدخول بالإيميل مو مفعّل للحين": "Email sign-in isn't enabled yet",
  "الرابط منتهي أو مستخدم، اطلب رابط جديد (": "The link expired or was used — request a new one (", "تعذّر الدخول:": "Sign-in failed:",
  "هذا الإيميل مسجّل بطريقة دخول ثانية، جرّب قوقل أو الإيميل": "This email uses another sign-in method — try Google or email",
  "حذف الحساب؟": "Delete account?", "احذف حسابي": "Delete my account", "بننحذف بياناتك (الاسم، الجوال، المفضلة) وتسجيل دخولك.": "We'll delete your data (name, mobile, favorites) and your login.",
  "طلب حذف حساب التاجر وبياناته (عروض + صفحة المتجر)": "Request to delete the shop account and its data (offers + store page)",
  "لأمانك: سجّل دخول مرة ثانية وبعدها احذف الحساب": "For your security: sign in again, then delete the account",
  "انحذف حسابك. نتمنى نشوفك مرة ثانية 🌿": "Your account was deleted. Hope to see you again 🌿", "تعذّر الحذف": "Couldn't delete", "تعذّر الحذف:": "Couldn't delete:",
  "🗑️ حذف الحساب نهائياً": "🗑️ Delete account permanently", "يحذف بياناتك ومفضلتك": "Deletes your data and favorites",
  " وصفحة متجرك وعروضك": " and your store page and offers", "، وما يمكن استرجاعها.": ", and it can't be undone.",
  "👤 الحساب": "👤 Account", "🌓 المظهر": "🌓 Appearance", "🌐 اللغة": "🌐 Language", "تلقائي": "Auto", "☀️ فاتح": "☀️ Light", "🌙 داكن": "🌙 Dark",
  "جاري التحويل للعربية…": "Switching to Arabic…",
  // التواصل
  "اكتب رسالتك…": "Write your message…", "اكتب رسالتك أول": "Write your message first", "أو كلّمنا واتساب": "or message us on WhatsApp",
  "وصلت رسالتك ✅ بنرد عليك قريب": "Message received ✅ we'll reply soon", "تعذّر الإرسال، حاول مرة ثانية": "Couldn't send, try again", "تعذّر الإرسال:": "Couldn't send:",
  "🐞 مشكلة": "🐞 Problem", "💡 اقتراح": "💡 Suggestion", "✨ فكرة إضافية": "✨ Idea", "🚩 شكوى على متجر": "🚩 Complaint about a shop",
  // سفير الحي والدعوات
  "سفير الحي": "Hayy Ambassador", "سفير فضي": "Silver Ambassador", "سفير ذهبي": "Gold Ambassador", "سفير ماسي": "Diamond Ambassador",
  "نقطة": "points", "ادع المحلات اللي تحبها لحيّنا": "Invite the shops you love to Hayyna", "🎁 شارك رابطك": "🎁 Share your link",
  "كيف أجمع نقاط؟": "How do I earn points?", "كيف تجمع نقاط؟": "How to earn points?", "💎 وصلت أعلى مستوى — أنت فخر الحي": "💎 Top level reached — the pride of the neighborhood",
  "نقطة وتصير": "points to become", "تحت المراجعة": "Under review", "انضم وتوثّق": "Joined & verified", "نزّل عروض": "Posting offers",
  "اضغط على أي محل": "Tap any shop that's", "مو مسجّل": "not registered", "في الخريطة، واختر": "on the map, and choose", "«ادعه»": "“Invite”",
  "— الرسالة فيها رابطك الخاص": "— the message includes your personal link", "أو اضغط": "Or tap", "«شارك رابطك»": "“Share your link”",
  "وأرسله لأصحاب المحلات اللي تعرفهم": "and send it to shop owners you know", "إذا سجّل المحل من رابطك وتوثّق:": "If the shop signs up via your link and gets verified:",
  "إذا نزّل أول عرض:": "When it posts its first offer:", "البداية": "Start",
  "النقاط تنحسب بعد مراجعة فريق حيّنا، وقريباً تقدر تستبدلها بخصومات وكوبونات من المحلات 🎁": "Points are added after the Hayyna team reviews them — soon you'll redeem them for discounts and coupons 🎁",
  "شف حيّنا 📍 تطبيق يعرض لك عروض وخصومات المحلات اللي حولك على الخريطة — مطاعم، مقاهي، ملابس وأكثر. جرّبه:": "Check out Hayyna 📍 — see deals from shops around you on a map: restaurants, cafés, fashion and more. Try it:",
  "هذا المحل مو مسجّل في حيّنا للحين": "This shop isn't on Hayyna yet", "هذا المحل": "This shop", "📨 سجّل دخولك وادعه": "📨 Sign in to invite",
  "🎁 الدعوة تنحسب باسمك، ولو سجّل المحل تاخذ لين": "🎁 The invite counts for you — if the shop joins you earn up to",
  "🎁 لو سجّل المحل عن طريق دعوتك تاخذ لين": "🎁 If the shop joins through your invite you earn up to",
  "📨 تمت دعوة هذا المحل من أحد أهل الحي": "📨 A neighbor already invited this shop", "ننتظره يسجّل — تقدر تدعوه بعد": "Waiting for it to join — you can invite it in",
  "✅ دعيت هذا المحل — ننتظره يسجّل (باقي": "✅ You invited this shop — waiting for it to join (", "محجوز لك)": "reserved for you)",
  "📞 اتصل بالمحل (": "📞 Call the shop (", "فتح في Google Maps": "Open in Google Maps", "💬 ادعه على واتساب المحل": "💬 Invite on the shop's WhatsApp",
  "💬 ذكّره على واتساب المحل": "💬 Remind on the shop's WhatsApp", "📨 ادعه لحيّنا": "📨 Invite to Hayyna", "📨 ذكّره مرة ثانية": "📨 Remind again",
  "🏪 أنا صاحب المحل — سجّله": "🏪 I own this shop — register it", "صاحب المحل؟ سجّل خروج وادخل بحساب": "Own this shop? Sign out and sign in with a",
  "عشان تسجّله": "account to register it",
  // التاجر
  "صفحة متجري": "My store page", "✎ صفحة متجري": "✎ My store page", "＋ عرض جديد": "＋ New post", "عرض جديد": "New post", "تعديل العرض": "Edit offer",
  "عروضي (": "My offers (", "عرض نشط": "active offers", "⏳ بانتظار المراجعة": "⏳ Pending review", "🚩 مخفي للمراجعة": "🚩 Hidden for review",
  "انحذف العرض": "Offer deleted", "حذف العرض؟": "Delete this offer?", "العرض بيظهر للناس بعد ما ينعتمد": "The offer appears after approval",
  "✅ حسابك موثوق — عروضك تنشر مباشرة": "✅ Trusted account — your offers publish instantly",
  "⏳ حساب جديد: عروضك تنراجع قبل ما تظهر (عادة خلال ساعات). بعد عرضين معتمدين تنشر مباشرة.": "⏳ New account: offers are reviewed before showing (usually within hours). After two approved offers you publish instantly.",
  "⛔ حسابك موقوف حالياً. تواصل مع إدارة حيّنا.": "⛔ Your account is suspended. Contact Hayyna.", "ما جهّزت صفحة متجرك للحين": "You haven't set up your store page yet",
  "أهلاً بك 🏪 ابدأ بتجهيز صفحة متجرك": "Welcome 🏪 Start by setting up your store page", "جهّز صفحة متجرك أول 📍": "Set up your store page first 📍",
  "جهّز صفحة متجرك وموقعك أول 📍": "Set up your store page and location first 📍",
  "اسم المتجر *": "Store name *", "التصنيف *": "Category *", "التصنيف": "Category", "نبذة عن المتجر": "About the store", "الشعار": "Logo",
  "صورة الواجهة (غلاف)": "Storefront photo (cover)", "📷 اختر صورة": "📷 Choose a photo", "الأسعار تبدأ من (ر.س)": "Prices from (SAR)", "📍 الفروع *": "📍 Branches *",
  "اكتب اسم المتجر.": "Enter the store name.", "اختر تصنيف المتجر.": "Choose a store category.", "حدد موقع محلك (فرع واحد على الأقل).": "Set your shop location (at least one branch).",
  "حفظ صفحة المتجر ✓": "Save store page ✓", "جاري الحفظ…": "Saving…", "تم حفظ صفحة متجرك ✅": "Store page saved ✅",
  "تم الحفظ ✅ صفحة متجرك بتظهر للعملاء بعد المراجعة": "Saved ✅ your store page will show after review",
  "رفض الحفظ (صلاحيات). تواصل معنا.": "Save rejected (permissions). Contact us.", "في تعديلات ما انحفظت. تطلع بدون حفظ؟": "You have unsaved changes. Leave without saving?",
  "مثال: شاورما على الفحم من 2010…": "e.g. Charcoal shawarma since 2010…", "مثال: 15": "e.g. 15", "مثال: 40": "e.g. 40", "مثال: 50": "e.g. 50",
  "فرع جديد": "New branch", "تعديل الفرع": "Edit branch", "حفظ الفرع": "Save branch", "أضف الفرع": "Add branch", "الفرع الرئيسي": "Main branch",
  "اسم الفرع (اختياري)": "Branch name (optional)", "مثال: فرع الحمدانية": "e.g. Al-Hamdaniyah branch", "موقع المحل *": "Shop location *",
  "(اضغط على الخريطة أو استخدم موقعك)": "(tap the map or use your location)", "📍 أنا في المحل الحين — استخدم موقعي": "📍 I'm at the shop now — use my location",
  "الحي *": "District *", "المدينة": "City", "مثال: الحمدانية": "e.g. Al-Hamdaniyah", "مثال: جدة": "e.g. Jeddah", "علامة مميزة (اختياري)": "Landmark (optional)",
  "مثال: بجوار البريد السعودي": "e.g. next to Saudi Post", "رقم واتساب / جوال الفرع *": "Branch WhatsApp / mobile *", "يفتح": "Opens", "يقفل": "Closes",
  "حدد موقع المحل على الخريطة.": "Set the shop location on the map.", "اكتب اسم الحي.": "Enter the district.", "اكتب رقم جوال صحيح مثل 05xxxxxxxx.": "Enter a valid mobile like 05xxxxxxxx.",
  "تم تحديد الموقع 📍": "Location set 📍", "ما قدرنا نحدد موقعك. اسمح للمتصفح بالموقع أو اضغط على الخريطة": "Couldn't get your location. Allow location or tap the map",
  "🛵 عندنا توصيل": "🛵 We deliver", "رسوم التوصيل (0 = مجاني)": "Delivery fee (0 = free)", "أقل طلب للتوصيل (اختياري)": "Minimum order (optional)",
  "توصيل مجاني للطلبات فوق (اختياري)": "Free delivery over (optional)",
  "عندك متجر مسجّل:": "You already have a store:", "بينضاف": "will be added", "كفرع جديد": "as a new branch", "لمتجرك في موقعه على الخريطة.": "of your store at its map location.",
  "إذا هو محل مستقل باسم ثاني، سجّله بحساب ثاني.": "If it's a separate shop with another name, register it with another account.", "أضفه كفرع لمتجري": "Add as a branch", "لا، إلغاء": "No, cancel",
  // نموذج العرض
  "🔥 عرض": "🔥 Offer", "خصم أو سعر خاص": "Discount or special price", "صنف أو منتج جديد": "A new item or product",
  "صور العرض (حتى 3) أو فيديو *": "Offer photos (up to 3) or video *", "صور المنتج (حتى 3) أو فيديو *": "Product photos (up to 3) or video *",
  "الغلاف": "Cover", "🎬 فيديو": "🎬 Video", "عنوان العرض *": "Offer title *", "اسم المنتج الجديد *": "New product name *",
  "مثال: خصم 30% على كل المنتجات": "e.g. 30% off everything", "مثال: برجر الترافل الجديد": "e.g. New truffle burger",
  "💰 سعر محدد": "💰 Fixed price", "٪ خصم بالنسبة": "% Percentage off", "السعر (ر.س) *": "Price (SAR) *", "السعر (اختياري)": "Price (optional)",
  "قبل الخصم": "Before discount", "قبل الخصم (لو عليه خصم)": "Before discount (if discounted)",
  "الخصم يتغيّر حسب اليوم (مثل: أول يوم 50% وبعدها 30%)": "Discount changes by day (e.g. 50% on day 1, then 30%)",
  "نسبة الخصم % *": "Discount % *", "＋ أضف يوم": "＋ Add day", "أيام، والخصم يتغيّر تلقائياً.": "days, the discount changes automatically.",
  "على وش الخصم؟": "Discount on what?", "تفاصيل العرض": "Offer details", "وصف المنتج": "Product description", "الأصناف (للبحث)": "Items (for search)",
  "شاورما، برجر، بروست": "shawarma, burger, broast", "🏪 وين يظهر العرض؟": "🏪 Where does it show?", "كل الفروع (": "All branches (",
  "🔜 متى يبدأ؟": "🔜 When does it start?", "الحين": "Now", "🎉 عرض افتتاح (محل جديد)": "🎉 Grand opening (new shop)",
  "⏰ مدة العرض": "⏰ Duration", "⏰ كم يظهر كـ «جديد»؟": "⏰ How long to show as “new”?", "بدون تغيير (": "No change (", "بدون انتهاء": "No end date",
  "يوم": "1 day", "3 أيام": "3 days", "أسبوع": "1 week", "أسبوعين": "2 weeks", "شهر": "1 month", "✎ عدد أيام": "✎ Number of days", "كم يوم؟ مثال: 2": "How many days? e.g. 2",
  "بعد انتهاء المدة يختفي العرض تلقائياً من الخريطة. ✨ التعبئة الذكية من الصورة متاحة في تطبيق أندرويد.": "When it ends it disappears from the map automatically. ✨ Smart fill from photo is available in the Android app.",
  "انشر العرض ✨": "Publish ✨", "حفظ التعديلات ✓": "Save changes ✓", "جاري رفع العرض…": "Uploading…", "المعلومات اللي كتبتها بتضيع. تطلع؟": "What you typed will be lost. Leave?",
  "أرفق صورة أو فيديو للعرض.": "Attach a photo or video.", "اكتب عنوان العرض.": "Enter the offer title.", "اكتب اسم المنتج.": "Enter the product name.",
  "اكتب عدد أيام العرض (من 1 إلى 90).": "Enter the number of days (1–90).", "اكتب سعر صحيح.": "Enter a valid price.", "اكتب السعر بعد الخصم.": "Enter the price after discount.",
  "اكتب نسبة الخصم لكل يوم (من 1 إلى 99).": "Enter the discount for each day (1–99).", "اكتب نسبة الخصم (من 1 إلى 99).": "Enter the discount (1–99).",
  "اختر فرع واحد على الأقل.": "Choose at least one branch.", "تاريخ البداية بعيد، اختر تاريخ خلال شهرين.": "Start date is too far, choose within two months.",
  "حسب التاريخ والمدة العرض منتهي، عدّل البداية أو المدة.": "With this date and duration the offer is already over — adjust them.",
  "وصل للمراجعة ⏳ بيظهر على الخريطة بعد الموافقة": "Sent for review ⏳ it'll show on the map once approved", "تم حفظ التعديلات ✅": "Changes saved ✅",
  "تم نشر العرض ✅": "Offer published ✅", "تم نشر منتجك الجديد ✨": "Your new product is published ✨", "تعذّر النشر": "Couldn't publish",
  "رفض النشر (صلاحيات). تأكد إن حسابك مو موقوف أو تواصل معنا.": "Publishing rejected (permissions). Make sure your account isn't suspended or contact us.",
  "الفيديو كبير، الحد 95 ميجا": "Video too large, max 95 MB", "تعذّر رفع الملف": "Couldn't upload the file", "عرض متاح لجميع العملاء.": "Offer available to all customers.",
  "منتج جديد متوفر الحين.": "New product available now.", "عروض) للحسابات الجديدة. جرّب بكرة 🙏": "offers) for new accounts. Try tomorrow 🙏", "وصلت للحد اليومي (": "Daily limit reached (",
  // التصنيفات الافتراضية
  "مطاعم 🍽️": "Restaurants 🍽️", "مطاعم 🍔": "Restaurants 🍽️", "مقاهي وحلويات ☕": "Cafés & sweets ☕", "سوبرماركت وبقالة 🛒": "Supermarkets 🛒", "ملابس وأزياء 👕": "Fashion 👕",
  "خياطة ومشاغل 🧵": "Tailoring 🧵", "جوالات وإلكترونيات 📱": "Phones & electronics 📱", "عطور وتجميل 💄": "Perfume & beauty 💄", "نظارات وبصريات 👓": "Optics 👓",
  "حلاقة وصالونات 💈": "Barbers & salons 💈", "خدمات أخرى 🧰": "Other services 🧰",
  // التواريخ
  "الأحد": "Sun", "الإثنين": "Mon", "الثلاثاء": "Tue", "الأربعاء": "Wed", "الخميس": "Thu", "الجمعة": "Fri", "السبت": "Sat",
  "يناير": "Jan", "فبراير": "Feb", "مارس": "Mar", "أبريل": "Apr", "مايو": "May", "يونيو": "Jun", "يوليو": "Jul", "أغسطس": "Aug", "سبتمبر": "Sep", "أكتوبر": "Oct", "نوفمبر": "Nov", "ديسمبر": "Dec",
  "يبدأ": "Starts", "إلى": "to", "اليوم": "Today", "باقي": "Only",
};
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
