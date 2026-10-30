// الخريطة: خرائط قوقل بنفس ستايل التطبيق (لو فيه مفتاح)، وإلا OpenStreetMap كاحتياط.
// كل الخرائط تستخدم نفس الواجهة: setMarkers / setDots / panTo / fitBounds / setMe / onClick.

export const CLEAN_STYLE = [
  { featureType: "poi", stylers: [{ visibility: "off" }] },
  { featureType: "poi.park", elementType: "geometry", stylers: [{ visibility: "on" }, { color: "#E3EDE4" }] },
  { featureType: "transit", stylers: [{ visibility: "off" }] },
  { featureType: "road", elementType: "labels.icon", stylers: [{ visibility: "off" }] },
  { featureType: "landscape", elementType: "geometry", stylers: [{ color: "#F5F3EF" }] },
  { featureType: "road", elementType: "geometry", stylers: [{ color: "#FFFFFF" }] },
  { featureType: "road.highway", elementType: "geometry", stylers: [{ color: "#F3E3C9" }] },
  { featureType: "road", elementType: "labels.text.fill", stylers: [{ color: "#9A9A9A" }] },
  { featureType: "administrative.neighborhood", elementType: "labels.text.fill", stylers: [{ color: "#7C8A80" }] },
  { featureType: "administrative.land_parcel", stylers: [{ visibility: "off" }] },
  { featureType: "water", elementType: "geometry", stylers: [{ color: "#D3E3EA" }] },
  { featureType: "water", elementType: "labels", stylers: [{ visibility: "off" }] },
];
// الوضع الداكن: نفس الستايل بألوان ليلية هادية
const DARK_COLORS = [
  { elementType: "geometry", stylers: [{ color: "#1B2320" }] },
  { elementType: "labels.text.fill", stylers: [{ color: "#8C9A93" }] },
  { elementType: "labels.text.stroke", stylers: [{ color: "#141B18" }] },
  { featureType: "landscape", elementType: "geometry", stylers: [{ color: "#1B2320" }] },
  { featureType: "road", elementType: "geometry", stylers: [{ color: "#2B3531" }] },
  { featureType: "road.highway", elementType: "geometry", stylers: [{ color: "#4A4130" }] },
  { featureType: "road", elementType: "labels.text.fill", stylers: [{ color: "#7D8A84" }] },
  { featureType: "poi.park", elementType: "geometry", stylers: [{ color: "#1F3328" }] },
  { featureType: "water", elementType: "geometry", stylers: [{ color: "#102A33" }] },
  { featureType: "administrative.neighborhood", elementType: "labels.text.fill", stylers: [{ color: "#9FB3A8" }] },
  { featureType: "poi.business", elementType: "labels.text.fill", stylers: [{ color: "#76827C" }] },
  { featureType: "poi.business", elementType: "labels.text.stroke", stylers: [{ color: "#141B18" }] },
];
const styleFor = (shops, dark) => dark ? [...(shops ? SHOPS_STYLE : CLEAN_STYLE), ...DARK_COLORS] : (shops ? SHOPS_STYLE : CLEAN_STYLE);
const SHOPS_STYLE = [...CLEAN_STYLE,
  { featureType: "poi.business", stylers: [{ visibility: "on" }] },
  { featureType: "poi.business", elementType: "labels.icon", stylers: [{ saturation: -75 }, { lightness: 30 }] },
  { featureType: "poi.business", elementType: "labels.text.fill", stylers: [{ color: "#A8A8A8" }] },
  { featureType: "poi.business", elementType: "labels.text.stroke", stylers: [{ color: "#FFFFFF" }] },
];

let gmapsPromise = null;
function loadGoogle(key) {
  if (googleFailed) return Promise.reject(new Error("maps auth failed"));
  if (window.google?.maps) return Promise.resolve(window.google.maps);
  if (gmapsPromise) return gmapsPromise;
  gmapsPromise = new Promise((resolve, reject) => {
    window.__hayynaMapsReady = () => resolve(window.google.maps);
    if (googleFailed) return reject(new Error("maps auth failed"));
    const s = document.createElement("script");
    s.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&v=weekly&language=ar&region=SA&callback=__hayynaMapsReady&loading=async`;
    s.async = true; s.onerror = () => reject(new Error("maps load failed"));
    document.head.appendChild(s);
    setTimeout(() => reject(new Error("maps timeout")), 15000);
  });
  return gmapsPromise;
}
let leafletPromise = null;
function loadLeaflet() {
  if (window.L) return Promise.resolve(window.L);
  if (leafletPromise) return leafletPromise;
  leafletPromise = new Promise((resolve, reject) => {
    const css = document.createElement("link"); css.rel = "stylesheet";
    css.href = "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"; document.head.appendChild(css);
    const s = document.createElement("script"); s.src = "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js";
    s.onload = () => resolve(window.L); s.onerror = reject; document.head.appendChild(s);
  });
  return leafletPromise;
}

/**
 * يرسم خريطة في العنصر el. items في setMarkers: [{lat,lng,html,z,onClick}].
 * يرجّع null لو فشل كل شي.
 */
export async function createMap(el, { key, center = [21.5433, 39.1728], zoom = 12, showShops = false, onMapClick, onPoiClick } = {}) {
  const opts = { center, zoom, showShops, onMapClick, onPoiClick };
  const origStyle = el.getAttribute("style"), origClass = el.className;
  // واجهة ثابتة تحوّل لـ OpenStreetMap لو رفضت قوقل المفتاح (حتى بعد ما تنرسم الخريطة)
  const last = {};
  let impl = null, switching = false;
  const toOsm = async () => {
    if (switching || impl?.kind === "osm") return; switching = true;
    console.warn("Google Maps failed — switching to OpenStreetMap");
    let c = { lat: center[0], lng: center[1] };
    try { c = impl?.center?.() || c; } catch {}
    // قوقل تبقى تكتب رسالة الخطأ داخل عنصرها القديم، فنبدّله بعنصر جديد نظيف ونرسم OSM فيه
    const fresh = document.createElement(el.tagName);
    if (el.id) fresh.id = el.id;
    fresh.className = (origClass || "").replace(/\bgm-err\b/, "");
    if (origStyle) fresh.setAttribute("style", origStyle);
    if (el.parentNode) el.replaceWith(fresh);
    el = fresh;
    const L = await loadLeaflet();
    impl = leafletMap(L, el, { ...opts, center: [c.lat, c.lng] });
    if (last.markers) impl.setMarkers(last.markers);
    if (last.dots) impl.setDots(last.dots);
    if (last.me) impl.setMe(...last.me);
    if (last.pin) impl.setPin(...last.pin);
    if (last.fit) impl.fitBounds(last.fit);
    if (last.circle) impl.setCircle(last.circle);
  };
  if (key) {
    try {
      const g = await loadGoogle(key);
      impl = googleMap(g, el, opts);
      googleFailHandlers.add(toOsm);
      // بعض أخطاء قوقل (مفتاح/دومين/فوترة) ما تنادي gm_authFailure وتكتفي برسالة "حدث خطأ" داخل الخريطة.
      // نراقب العنصر: أول ما تظهر رسالة الخطأ نحوّل لـ OpenStreetMap بدل ما تبقى الخريطة ميتة.
      const hasErr = () => el.querySelector(".gm-err-container, .gm-err-message, .gm-err-title") || el.classList.contains("gm-err");
      if (hasErr()) toOsm();
      else if (window.MutationObserver) {
        const mo = new MutationObserver(() => { if (hasErr()) { mo.disconnect(); toOsm(); } });
        mo.observe(el, { childList: true, subtree: true, attributes: true, attributeFilter: ["class"] });
        setTimeout(() => mo.disconnect(), 20000);
      }
    } catch (e) { console.warn("Google Maps unavailable, using OpenStreetMap", e); el.innerHTML = ""; }
  }
  if (!impl) { const L = await loadLeaflet(); impl = leafletMap(L, el, opts); }
  return {
    get kind() { return impl.kind; },
    setMarkers: (items) => { last.markers = items; impl.setMarkers(items); },
    setDots: (items) => { last.dots = items; impl.setDots(items); },
    panTo: (...a) => impl.panTo(...a),
    setView: (...a) => impl.setView(...a),
    fitBounds: (pts, pad) => { last.fit = pts; impl.fitBounds(pts, pad); },
    setMe: (lat, lng) => { last.me = [lat, lng]; impl.setMe(lat, lng); },
    setPin: (lat, lng) => { last.pin = [lat, lng]; impl.setPin(lat, lng); },
    setCircle: (c) => { last.circle = c; impl.setCircle(c); },
    setShowShops: (on) => impl.setShowShops?.(on),
    setDark: (d) => impl.setDark?.(d),
    placeInfo: (id) => impl.placeInfo ? impl.placeInfo(id) : Promise.resolve({ name: "", phone: "" }),
    center: () => impl.center(),
    resize: () => impl.resize(),
  };
}

// قوقل تنادي gm_authFailure لو المفتاح مرفوض (فوترة / دومين) — نحوّل كل الخرائط المفتوحة
const googleFailHandlers = new Set();
let googleFailed = false;
window.gm_authFailure = () => { googleFailed = true; googleFailHandlers.forEach((f) => f()); };

function googleMap(g, el, { center, zoom, showShops, onMapClick, onPoiClick }) {
  const map = new g.Map(el, {
    center: { lat: center[0], lng: center[1] }, zoom, styles: styleFor(showShops, document.documentElement.dataset.theme === "dark"),
    disableDefaultUI: true, keyboardShortcuts: false, gestureHandling: "greedy", clickableIcons: !!(showShops && onPoiClick), backgroundColor: "#F5F3EF",
  });
  class Html extends g.OverlayView {
    constructor(pos, node, z) { super(); this.pos = new g.LatLng(pos.lat, pos.lng); this.node = node; this.z = z || 0; }
    onAdd() {
      this.node.style.position = "absolute"; this.node.style.zIndex = String(this.z);
      g.OverlayView.preventMapHitsAndGesturesFrom(this.node);
      this.getPanes().overlayMouseTarget.appendChild(this.node);
    }
    draw() { const p = this.getProjection().fromLatLngToDivPixel(this.pos); if (p) { this.node.style.left = p.x + "px"; this.node.style.top = p.y + "px"; } }
    onRemove() { this.node.remove(); }
    setPos(pos) { this.pos = new g.LatLng(pos.lat, pos.lng); this.draw(); }
  }
  const layers = { markers: [], dots: [] };
  let shopsOn = showShops, dark = document.documentElement.dataset.theme === "dark";
  let me = null, pin = null, circle = null;
  const node = (html, cls, onClick) => {
    const d = document.createElement("div"); d.className = "ov " + (cls || ""); d.innerHTML = html;
    if (onClick) d.addEventListener("click", (e) => { e.stopPropagation(); onClick(); });
    return d;
  };
  const put = (layer, items, cls) => {
    layers[layer].forEach((o) => o.setMap(null));
    layers[layer] = items.map((it) => { const o = new Html({ lat: it.lat, lng: it.lng }, node(it.html, cls, it.onClick), it.z); o.setMap(map); return o; });
  };
  map.addListener("click", (e) => {
    // ضغط على محل من محلات Google: نوقف بطاقة قوقل ونعرض بطاقتنا
    if (e.placeId && onPoiClick) { e.stop(); onPoiClick({ placeId: e.placeId, lat: e.latLng.lat(), lng: e.latLng.lng() }); return; }
    if (onMapClick) onMapClick({ lat: e.latLng.lat(), lng: e.latLng.lng() });
  });
  return {
    kind: "google",
    setShowShops: (on) => { shopsOn = on; map.setOptions({ styles: styleFor(on, dark), clickableIcons: !!(on && onPoiClick) }); },
    setDark: (d) => { dark = d; map.setOptions({ styles: styleFor(shopsOn, d), backgroundColor: d ? "#1B2320" : "#F5F3EF" }); },
    // اسم المحل ورقمه من خرائط Google (Places API New) — لحظة الضغط بس، وما نخزّنه
    placeInfo: async (placeId) => {
      try {
        const { Place } = await g.importLibrary("places");
        const p = new Place({ id: placeId, requestedLanguage: "ar" });
        await Promise.race([p.fetchFields({ fields: ["displayName", "nationalPhoneNumber", "internationalPhoneNumber"] }), new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), 5000))]);
        return { name: p.displayName || "", phone: p.internationalPhoneNumber || p.nationalPhoneNumber || "" };
      } catch (e) { console.warn("places", e); return { name: "", phone: "" }; }
    },
    setMarkers: (items) => put("markers", items, "ov-mk"),
    setDots: (items) => put("dots", items, "ov-dot"),
    panTo: (lat, lng, z) => { map.panTo({ lat, lng }); if (z) map.setZoom(z); },
    setView: (lat, lng, z) => { map.setCenter({ lat, lng }); if (z) map.setZoom(z); },
    fitBounds: (pts, pad) => {
      if (!pts.length) return;
      const b = new g.LatLngBounds(); pts.forEach(([a, c]) => b.extend({ lat: a, lng: c }));
      map.fitBounds(b, pad || { top: 140, bottom: 260, left: 40, right: 40 });
      g.event.addListenerOnce(map, "idle", () => { if (map.getZoom() > 15) map.setZoom(15); });
    },
    setMe: (lat, lng) => { if (me) me.setPos({ lat, lng }); else { me = new Html({ lat, lng }, node('<div class="me"></div>', "ov-me"), 5); me.setMap(map); } },
    setPin: (lat, lng) => { if (pin) pin.setPos({ lat, lng }); else { pin = new Html({ lat, lng }, node('<div class="pin">📍</div>', "ov-pin"), 10); pin.setMap(map); } },
    setCircle: (c) => {
      if (circle) { circle.setMap(null); circle = null; }
      if (c) circle = new g.Circle({ map, center: { lat: c.lat, lng: c.lng }, radius: c.radius, fillColor: "#10318F", fillOpacity: 0.06, strokeColor: "#10318F", strokeOpacity: 0.45, strokeWeight: 2, clickable: false });
    },
    center: () => { const c = map.getCenter(); return { lat: c.lat(), lng: c.lng() }; },
    resize: () => g.event.trigger(map, "resize"),
  };
}

function leafletMap(L, el, { center, zoom, onMapClick }) {
  el.setAttribute("dir", "ltr");
  const map = L.map(el, { zoomControl: false }).setView(center, zoom);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);
  map.attributionControl.setPrefix(false);
  el.classList.add("osm");
  const layers = { markers: L.layerGroup().addTo(map), dots: L.layerGroup().addTo(map) };
  let me = null, pin = null, circle = null;
  const put = (layer, items, cls) => {
    layers[layer].clearLayers();
    items.forEach((it) => {
      const m = L.marker([it.lat, it.lng], { icon: L.divIcon({ html: it.html, className: "ov " + cls, iconSize: null }), zIndexOffset: it.z || 0 }).addTo(layers[layer]);
      if (it.onClick) m.on("click", it.onClick);
    });
  };
  if (onMapClick) map.on("click", (e) => onMapClick({ lat: e.latlng.lat, lng: e.latlng.lng }));
  return {
    kind: "osm",
    setMarkers: (items) => put("markers", items, "ov-mk"),
    setDots: (items) => put("dots", items, "ov-dot"),
    panTo: (lat, lng, z) => z ? map.setView([lat, lng], z) : map.panTo([lat, lng]),
    setView: (lat, lng, z) => map.setView([lat, lng], z || map.getZoom()),
    fitBounds: (pts) => { if (pts.length) map.fitBounds(pts, { paddingTopLeft: [40, 140], paddingBottomRight: [40, 260], maxZoom: 15 }); },
    setMe: (lat, lng) => { if (me) me.setLatLng([lat, lng]); else me = L.marker([lat, lng], { icon: L.divIcon({ html: '<div class="me"></div>', className: "ov ov-me", iconSize: null }), interactive: false }).addTo(map); },
    setPin: (lat, lng) => { if (pin) pin.setLatLng([lat, lng]); else pin = L.marker([lat, lng], { icon: L.divIcon({ html: '<div class="pin">📍</div>', className: "ov ov-pin", iconSize: null }), interactive: false }).addTo(map); },
    setCircle: (c) => {
      if (circle) { circle.remove(); circle = null; }
      if (c) circle = L.circle([c.lat, c.lng], { radius: c.radius, color: "#10318F", weight: 2, opacity: 0.45, fillOpacity: 0.06, interactive: false }).addTo(map);
    },
    center: () => { const c = map.getCenter(); return { lat: c.lat, lng: c.lng }; },
    resize: () => map.invalidateSize(),
  };
}

// ---------------- بحث المحلات في Google (لرفع عروض الإعلانات) ----------------
const PLACE_FIELDS = ["id", "displayName", "formattedAddress", "location", "nationalPhoneNumber", "internationalPhoneNumber", "addressComponents"];
function placeOut(p) {
  const comp = (t) => (p.addressComponents || []).find((c) => (c.types || []).includes(t))?.longText || "";
  return {
    placeId: p.id, name: p.displayName || "", address: p.formattedAddress || "",
    lat: p.location?.lat?.(), lng: p.location?.lng?.(),
    phone: p.internationalPhoneNumber || p.nationalPhoneNumber || "",
    district: comp("sublocality_level_1") || comp("sublocality") || comp("neighborhood"),
    city: comp("locality") || comp("administrative_area_level_2") || comp("administrative_area_level_1"),
  };
}
const withTimeout = (p, ms = 8000) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error("timeout")), ms))]);
/** يدوّر على محل بالاسم/العنوان. near = {lat,lng} اختياري يقرّب النتائج */
export async function searchPlaces(key, text, near) {
  const g = await loadGoogle(key);
  const { Place } = await g.importLibrary("places");
  const req = { textQuery: text, fields: PLACE_FIELDS, language: "ar", region: "sa", maxResultCount: 6 };
  if (near?.lat != null) req.locationBias = { center: { lat: near.lat, lng: near.lng }, radius: 30000 };
  const { places } = await withTimeout(Place.searchByText(req));
  return (places || []).map(placeOut).filter((p) => p.lat != null);
}
/** تفاصيل محل واحد (الاسم، الجوال، الحي، المدينة، الإحداثيات) */
export async function placeDetails(key, placeId) {
  const g = await loadGoogle(key);
  const { Place } = await g.importLibrary("places");
  const p = new Place({ id: placeId, requestedLanguage: "ar" });
  await withTimeout(p.fetchFields({ fields: PLACE_FIELDS.filter((f) => f !== "id") }));
  return placeOut(p);
}
