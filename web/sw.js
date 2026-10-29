// عامل خدمة بسيط: يخلّي المتصفح يعتبر لكوفر تطبيقاً قابلاً للتثبيت. ما يخزّن شي — كل الطلبات تروح للشبكة عادي.
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET" || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(fetch(e.request));
});
