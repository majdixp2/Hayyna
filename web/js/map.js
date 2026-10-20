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
export async function createMap(el, { key, center = [21.5433, 39.1728], zoom = 12, showShops = false, onMapClick } = {}) {
  const opts = { center, zoom, showShops, onMapClick };
  // واجهة ثابتة تحوّل لـ OpenStreetMap لو رفضت قوقل المفتاح (حتى بعد ما تنرسم الخريطة)
  const last = {};
  let impl = null, switching = false;
  const toOsm = async () => {
    if (switching || impl?.kind === "osm") return; switching = true;
    console.warn("Google Maps failed — switching to OpenStreetMap");
    const c = impl?.center?.() || { lat: center[0], lng: center[1] };
    el.innerHTML = ""; el.className = el.className.replace(/\bgm-err\b/, "");
    const L = await loadLeaflet();
    impl = leafletMap(L, el, { ...opts, center: [c.lat, c.lng] });
    if (last.markers) impl.setMarkers(last.markers);
    if (last.dots) impl.setDots(last.dots);
    if (last.me) impl.setMe(...last.me);
    if (last.pin) impl.setPin(...last.pin);
    if (last.fit) impl.fitBounds(last.fit);
  };
  if (key) {
    try {
      const g = await loadGoogle(key);
      impl = googleMap(g, el, opts);
      googleFailHandlers.add(toOsm);
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
    center: () => impl.center(),
    resize: () => impl.resize(),
  };
}

// قوقل تنادي gm_authFailure لو المفتاح مرفوض (فوترة / دومين) — نحوّل كل الخرائط المفتوحة
const googleFailHandlers = new Set();
let googleFailed = false;
window.gm_authFailure = () => { googleFailed = true; googleFailHandlers.forEach((f) => f()); };

function googleMap(g, el, { center, zoom, showShops, onMapClick }) {
  const map = new g.Map(el, {
    center: { lat: center[0], lng: center[1] }, zoom, styles: showShops ? SHOPS_STYLE : CLEAN_STYLE,
    disableDefaultUI: true, gestureHandling: "greedy", clickableIcons: false, backgroundColor: "#F5F3EF",
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
  let me = null, pin = null;
  const node = (html, cls, onClick) => {
    const d = document.createElement("div"); d.className = "ov " + (cls || ""); d.innerHTML = html;
    if (onClick) d.addEventListener("click", (e) => { e.stopPropagation(); onClick(); });
    return d;
  };
  const put = (layer, items, cls) => {
    layers[layer].forEach((o) => o.setMap(null));
    layers[layer] = items.map((it) => { const o = new Html({ lat: it.lat, lng: it.lng }, node(it.html, cls, it.onClick), it.z); o.setMap(map); return o; });
  };
  if (onMapClick) map.addListener("click", (e) => onMapClick({ lat: e.latLng.lat(), lng: e.latLng.lng() }));
  return {
    kind: "google",
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
  let me = null, pin = null;
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
    center: () => { const c = map.getCenter(); return { lat: c.lat, lng: c.lng }; },
    resize: () => map.invalidateSize(),
  };
}
