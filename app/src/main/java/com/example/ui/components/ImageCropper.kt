package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CalmSagePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ✂️ قص الصورة بيد التاجر: يسحب ويكبّر بإصبعين داخل إطار ثابت.
 * onDone(uri): رابط ملف الصورة المقصوصة (أو الأصلية لو اختار «كاملة»)، null = ألغى.
 */
@Composable
fun ImageCropDialog(source: String, aspect: Float, round: Boolean = false, title: String = "قص الصورة", outWidth: Int = 1400, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        bmp = withContext(Dispatchers.IO) { runCatching { decodeSampled(context, Uri.parse(source), 2200) }.getOrNull() }
        if (bmp == null) { failed = true; onDone(source) } // صيغة ما نقدر نقرأها: نرفعها مثل ما هي
    }
    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0F0D)).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDone(null) }) { Text("✕", color = Color.White, fontSize = 18.sp) }
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            }
            val b = bmp
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (b == null) { if (!failed) CircularProgressIndicator(color = Color.White); return@BoxWithConstraints }
                val density = LocalDensity.current
                val W = with(density) { maxWidth.toPx() }; val H = with(density) { maxHeight.toPx() }
                val pad = with(density) { 16.dp.toPx() }
                val fw = minOf(W - 2 * pad, (H - 2 * pad) * aspect); val fh = fw / aspect
                val fx = (W - fw) / 2; val fy = (H - fh) / 2
                val base = maxOf(fw / b.width, fh / b.height)
                fun clamp(o: Offset, z: Float): Offset {
                    val s = base * z; val mx = maxOf(0f, (b.width * s - fw) / 2); val my = maxOf(0f, (b.height * s - fh) / 2)
                    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
                }
                val img: ImageBitmap = remember(b) { b.asImageBitmap() }
                Canvas(Modifier.fillMaxSize().pointerInput(b, W, H) {
                    detectTransformGestures { _, pan, gz, _ ->
                        zoom = (zoom * gz).coerceIn(1f, 4f)
                        off = clamp(off + pan, zoom)
                    }
                }) {
                    val s = base * zoom; val iw = b.width * s; val ih = b.height * s
                    val x = W / 2 + off.x - iw / 2; val y = H / 2 + off.y - ih / 2
                    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(b.width, b.height), dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()))
                    // تعتيم خارج الإطار
                    val hole = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        if (round) addOval(androidx.compose.ui.geometry.Rect(fx, fy, fx + fw, fy + fh))
                        else addRoundRect(androidx.compose.ui.geometry.RoundRect(fx, fy, fx + fw, fy + fh, CornerRadius(24f)))
                    }
                    drawPath(hole, Color.Black.copy(alpha = 0.55f))
                    if (round) drawOval(Color.White, Offset(fx, fy), Size(fw, fh), style = Stroke(4f))
                    else drawRoundRect(Color.White, Offset(fx, fy), Size(fw, fh), CornerRadius(24f), style = Stroke(4f))
                }
                // نحفظ الأبعاد للزر «تم»
                LaunchedEffect(W, H) { cropGeom = floatArrayOf(W, H, fx, fy, fw, fh, base) }
            }
            Text("اسحب الصورة لتحريكها، وكبّر بإصبعين", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(6.dp))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onDone(source) }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("الصورة كاملة بدون قص", color = Color.White, fontSize = 13.sp) }
                Button(
                    onClick = {
                        val bb = bmp ?: return@Button
                        val g = cropGeom ?: return@Button
                        saving = true
                        scope.launch {
                            val out = withContext(Dispatchers.IO) {
                                runCatching {
                                    val (W, H, fx, fy, fw, fh, base) = g
                                    val s = base * zoom; val iw = bb.width * s; val ih = bb.height * s
                                    val ix = W / 2 + off.x - iw / 2; val iy = H / 2 + off.y - ih / 2
                                    val sx = ((fx - ix) / s).toInt().coerceIn(0, bb.width - 1); val sy = ((fy - iy) / s).toInt().coerceIn(0, bb.height - 1)
                                    val sw = (fw / s).toInt().coerceIn(1, bb.width - sx); val sh = (fh / s).toInt().coerceIn(1, bb.height - sy)
                                    val crop = Bitmap.createBitmap(bb, sx, sy, sw, sh)
                                    val ow = minOf(outWidth, sw); val oh = (ow / aspect).toInt().coerceAtLeast(1)
                                    val scaled = Bitmap.createScaledBitmap(crop, ow, oh, true)
                                    val f = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                                    f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                    Uri.fromFile(f).toString()
                                }.getOrNull()
                            }
                            saving = false
                            onDone(out ?: source)
                        }
                    },
                    enabled = !saving && bmp != null,
                    colors = ButtonDefaults.buttonColors(containerColor = CalmSagePrimary),
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                ) { Text(if (saving) "…" else "تم ✓", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private var cropGeom: FloatArray? = null
private operator fun FloatArray.component6() = this[5]
private operator fun FloatArray.component7() = this[6]

/** نقرأ الصورة مصغّرة (عشان الذاكرة) ونعدّل اتجاهها حسب EXIF */
private fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val rot = runCatching {
        cr.openInputStream(uri)?.use { s ->
            when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    return if (rot == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
}
