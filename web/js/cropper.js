// قص الصورة بيد التاجر: يسحب ويكبّر داخل إطار ثابت، ونرفع الجزء اللي اختاره بس.
// cropImage(file, { aspect: 2, round: false, title }) → Promise<File | null>  (null = ألغى)

export function cropImage(file, { aspect = 1, round = false, title = "قص الصورة", outW = 1400 } = {}) {
  if (window.__hayynaNoCrop) return Promise.resolve(file); // الأدمن موقّف القص
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => open();
    img.onerror = () => { URL.revokeObjectURL(url); resolve(file); }; // صيغة غريبة: نرفعها مثل ما هي
    img.src = url;

    function open() {
      const ov = document.createElement("div");
      ov.className = "crop-ov";
      ov.innerHTML = `<div class="crop-top"><button class="crop-x" data-c="no">✕</button><b>${title}</b><span></span></div>
        <div class="crop-stage"><canvas></canvas><div class="crop-frame${round ? " round" : ""}"></div></div>
        <div class="crop-hint">اسحب الصورة لتحريكها، وكبّر بإصبعين أو بالشريط</div>
        <div class="crop-zoom"><span>－</span><input type="range" min="1" max="4" step="0.01" value="1"><span>＋</span></div>
        <div class="crop-act"><button class="btn call" data-c="full">الصورة كاملة بدون قص</button><button class="btn dir" data-c="ok">تم ✓</button></div>`;
      document.body.appendChild(ov);
      const stage = ov.querySelector(".crop-stage"), cv = ov.querySelector("canvas"), frame = ov.querySelector(".crop-frame"), range = ov.querySelector("input");
      const ctx = cv.getContext("2d");
      let W, H, fw, fh, fx, fy, base, zoom = 1, ox = 0, oy = 0; // ox/oy: إزاحة مركز الصورة عن مركز الإطار (بكسل الشاشة)
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      function layout() {
        W = stage.clientWidth; H = stage.clientHeight;
        cv.width = W * dpr; cv.height = H * dpr; cv.style.width = W + "px"; cv.style.height = H + "px";
        fw = Math.min(W - 32, (H - 32) * aspect); fh = fw / aspect;
        fx = (W - fw) / 2; fy = (H - fh) / 2;
        Object.assign(frame.style, { left: fx + "px", top: fy + "px", width: fw + "px", height: fh + "px" });
        base = Math.max(fw / img.width, fh / img.height); // أقل تكبير يغطي الإطار
        clamp(); draw();
      }
      function clamp() {
        const s = base * zoom, iw = img.width * s, ih = img.height * s;
        const mx = Math.max(0, (iw - fw) / 2), my = Math.max(0, (ih - fh) / 2);
        ox = Math.min(mx, Math.max(-mx, ox)); oy = Math.min(my, Math.max(-my, oy));
      }
      function draw() {
        const s = base * zoom, iw = img.width * s, ih = img.height * s;
        const x = W / 2 + ox - iw / 2, y = H / 2 + oy - ih / 2;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.fillStyle = "#000"; ctx.fillRect(0, 0, W, H);
        ctx.drawImage(img, x, y, iw, ih);
      }
      // سحب + تكبير بإصبعين
      const pts = new Map(); let last = null, pinch = null;
      stage.addEventListener("pointerdown", (e) => { stage.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); last = { x: e.clientX, y: e.clientY }; if (pts.size === 2) { const [a, b] = [...pts.values()]; pinch = { d: Math.hypot(a.x - b.x, a.y - b.y), z: zoom }; } });
      stage.addEventListener("pointermove", (e) => {
        if (!pts.has(e.pointerId)) return;
        pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
        if (pts.size === 2 && pinch) { const [a, b] = [...pts.values()]; setZoom(pinch.z * Math.hypot(a.x - b.x, a.y - b.y) / pinch.d); return; }
        if (last) { ox += e.clientX - last.x; oy += e.clientY - last.y; last = { x: e.clientX, y: e.clientY }; clamp(); draw(); }
      });
      const up = (e) => { pts.delete(e.pointerId); pinch = null; last = pts.size ? [...pts.values()][0] : null; };
      stage.addEventListener("pointerup", up); stage.addEventListener("pointercancel", up);
      stage.addEventListener("wheel", (e) => { e.preventDefault(); setZoom(zoom * (e.deltaY < 0 ? 1.08 : 0.93)); }, { passive: false });
      function setZoom(z) { zoom = Math.min(4, Math.max(1, z)); range.value = zoom; clamp(); draw(); }
      range.oninput = () => setZoom(+range.value);
      const onResize = () => layout(); window.addEventListener("resize", onResize);
      const done = (val) => { window.removeEventListener("resize", onResize); ov.remove(); URL.revokeObjectURL(url); resolve(val); };
      ov.querySelector('[data-c="no"]').onclick = () => done(null);
      ov.querySelector('[data-c="full"]').onclick = () => done(file);
      ov.querySelector('[data-c="ok"]').onclick = () => {
        // نحوّل الإطار لإحداثيات الصورة الأصلية
        const s = base * zoom, iw = img.width * s, ih = img.height * s;
        const ix = W / 2 + ox - iw / 2, iy = H / 2 + oy - ih / 2;
        const sx = (fx - ix) / s, sy = (fy - iy) / s, sw = fw / s, sh = fh / s;
        const outWidth = Math.min(outW, Math.round(sw)), outHeight = Math.round(outWidth / aspect);
        const out = document.createElement("canvas"); out.width = outWidth; out.height = outHeight;
        out.getContext("2d").drawImage(img, sx, sy, sw, sh, 0, 0, outWidth, outHeight);
        out.toBlob((b) => done(b ? new File([b], (file.name || "image").replace(/\.\w+$/, "") + ".jpg", { type: "image/jpeg" }) : file), "image/jpeg", 0.9);
      };
      requestAnimationFrame(layout);
    }
  });
}
