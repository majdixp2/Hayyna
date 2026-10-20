// إعدادات Firebase لتطبيق الويب (ليست سرية — كل تطبيقات الويب تكون فيها ظاهرة).
// تُعبّأ من Firebase ← Project settings ← Your apps ← Hayyna Admin.
export const firebaseConfig = {
  apiKey: "AIzaSyAurGMO7mSIhqvvMisJHHub_IfdTmJaTDE",
  authDomain: "gen-lang-client-0536850806.firebaseapp.com",
  projectId: "gen-lang-client-0536850806",
  storageBucket: "gen-lang-client-0536850806.firebasestorage.app",
  messagingSenderId: "352615681327",
  appId: "1:352615681327:web:1e040f01340fd62cc125da",
};

// قاعدة البيانات اللي يستخدمها التطبيق
export const FIRESTORE_DB_ID = "ai-studio-android-307cc811-cd30-4d36-b6c1-76e9fb2ac335";

// نفس قائمة الأدمن في التطبيق وقواعد Firestore
export const ADMIN_EMAILS = ["majdixp2@gmail.com"];

export const AUTO_HIDE_REPORTS = 3;

// مفتاح خرائط قوقل للويب (Maps JavaScript API، مقيّد على دومين الموقع). فاضي = خريطة OpenStreetMap مؤقتاً.
export const MAPS_WEB_KEY = "AIzaSyC-fvihsfnXVq5Y9ZVCmNga3ztKNsIClHo";
