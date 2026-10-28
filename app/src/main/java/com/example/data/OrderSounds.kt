package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 🔔 أصوات الطلبات عند المحل (تتولّد بالكود بدون ملفات صوت):
 * new = «دينغ-دونغ» مرتين، arrive = ثلاث نغمات طالعة، cancel = نغمة نازلة هادية. ومعها اهتزاز.
 * تشتغل والتطبيق مفتوح. (الإشعارات والتطبيق مقفل تجي لاحقاً مع خادم الإشعارات)
 */
object OrderSounds {
    private const val PREF = "hayyna_prefs"
    private const val KEY = "order_sound"
    private const val RATE = 22050

    fun isOn(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun setOn(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()

    private data class Note(val freq: Double, val start: Double, val dur: Double, val vol: Double = 0.5)

    private val patterns = mapOf(
        "new" to listOf(Note(1046.5, 0.0, 0.45), Note(784.0, 0.3, 0.6), Note(1046.5, 0.9, 0.45), Note(784.0, 1.2, 0.6)),
        "arrive" to listOf(Note(659.3, 0.0, 0.22, 0.35), Note(784.0, 0.16, 0.22, 0.35), Note(987.8, 0.32, 0.3, 0.35)),
        "cancel" to listOf(Note(523.3, 0.0, 0.35, 0.4), Note(392.0, 0.3, 0.5, 0.4))
    )

    fun play(ctx: Context, kind: String) {
        if (!isOn(ctx)) return
        vibrate(ctx, kind)
        val notes = patterns[kind] ?: return
        Thread {
            runCatching {
                val total = notes.maxOf { it.start + it.dur } + 0.1
                val n = (total * RATE).toInt()
                val buf = ShortArray(n)
                val mix = DoubleArray(n)
                notes.forEach { nt ->
                    val s0 = (nt.start * RATE).toInt(); val len = (nt.dur * RATE).toInt()
                    for (i in 0 until len) {
                        val idx = s0 + i; if (idx >= n) break
                        val t = i.toDouble() / RATE
                        val env = (1 - exp(-t * 60)) * exp(-t * 5.5) // ضربة جرس ناعمة
                        val w = sin(2 * PI * nt.freq * t) + 0.3 * sin(4 * PI * nt.freq * t)
                        mix[idx] += w * env * nt.vol
                    }
                }
                for (i in 0 until n) buf[i] = (mix[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.8).toInt().toShort()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n * 2).build()
                track.write(buf, 0, n)
                track.play()
                Thread.sleep((total * 1000).toLong() + 200)
                track.release()
            }
        }.start()
    }

    private fun vibrate(ctx: Context, kind: String) {
        runCatching {
            @Suppress("DEPRECATION")
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val pattern = if (kind == "new") longArrayOf(0, 200, 100, 200) else longArrayOf(0, 150)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1)) else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }
}
