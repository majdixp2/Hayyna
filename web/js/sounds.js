// 🔔 أصوات الطلبات عند المحل (تتولّد بالمتصفح بدون ملفات): طلب جديد / العميل وصل / إلغاء.
// المتصفح ما يسمح بالصوت إلا بعد ضغطة من المستخدم، فالتاجر يفعّلها مرة وحدة من زر «🔔».
let ctx = null;
const KEY = "hayyna_sound";
export const soundOn = () => { try { return localStorage.getItem(KEY) !== "off"; } catch { return true; } };
export const soundReady = () => !!ctx && ctx.state === "running";
export function setSound(on) { try { localStorage.setItem(KEY, on ? "on" : "off"); } catch {} if (on) unlock(); }
/** يفتح الصوت (لازم يُستدعى من ضغطة) */
export function unlock() {
  try {
    ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
    if (ctx.state === "suspended") ctx.resume();
  } catch { ctx = null; }
}
// أي ضغطة بالصفحة تفتح الصوت (مرة وحدة)
document.addEventListener("pointerdown", () => { if (soundOn()) unlock(); }, { once: true, capture: true });

function tone(freq, start, dur, type = "sine", gain = 0.28) {
  const o = ctx.createOscillator(), g = ctx.createGain();
  o.type = type; o.frequency.value = freq;
  g.gain.setValueAtTime(0.0001, ctx.currentTime + start);
  g.gain.exponentialRampToValueAtTime(gain, ctx.currentTime + start + 0.02);
  g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + start + dur);
  o.connect(g).connect(ctx.destination);
  o.start(ctx.currentTime + start); o.stop(ctx.currentTime + start + dur + 0.05);
}
const PATTERNS = {
  // 🛎️ طلب جديد: «دينغ-دونغ» مرتين
  new: () => { [0, 0.9].forEach((t) => { tone(1046, t, 0.45, "triangle"); tone(784, t + 0.3, 0.6, "triangle"); }); },
  // 🚗 العميل وصل: ثلاث نغمات سريعة طالعة
  arrive: () => { [659, 784, 988].forEach((f, i) => tone(f, i * 0.16, 0.22, "square", 0.12)); },
  // ✕ إلغاء / طلب إلغاء: نغمة نازلة هادية
  cancel: () => { tone(523, 0, 0.35, "sine", 0.22); tone(392, 0.3, 0.5, "sine", 0.22); },
};
export function play(kind) {
  if (!soundOn()) return;
  try { navigator.vibrate?.(kind === "new" ? [200, 100, 200] : 150); } catch {}
  if (!ctx) unlock();
  if (!soundReady()) return;
  try { PATTERNS[kind]?.(); } catch {}
}
