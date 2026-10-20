package com.example.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaType

@Composable
fun ProductMediaViewer(
    mediaUri: String,
    mediaType: MediaType,
    modifier: Modifier = Modifier,
    isPromotionalVideo: Boolean = false,
    promotionalBadge: String = "",
    autoPlayVideo: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var isVideoPlaying by remember { mutableStateOf(autoPlayVideo) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var hasVideoError by remember { mutableStateOf(false) }

    // Map common drawable resource names
    val drawableResId = remember(mediaUri) {
        when (mediaUri) {
            "cafe_arabica_offer_1791863237123" -> com.example.R.drawable.cafe_arabica_offer_1791863237123
            "restaurant_burger_offer_1791823697805" -> com.example.R.drawable.restaurant_burger_offer_1791823697805
            "restaurant_pizza_offer_1791823722379" -> com.example.R.drawable.restaurant_pizza_offer_1791823722379
            "restaurant_grill_offer_1791823746852" -> com.example.R.drawable.restaurant_grill_offer_1791823746852
            "merchant_store_banner_1791821178163" -> com.example.R.drawable.merchant_store_banner_1791821178163
            "sample_product_watch_1791821194030" -> com.example.R.drawable.sample_product_watch_1791821194030
            "sample_product_perfume_1791821210881" -> com.example.R.drawable.sample_product_perfume_1791821210881
            "sample_product_coffee_1791821388210" -> com.example.R.drawable.sample_product_coffee_1791821388210
            else -> {
                val resId = context.resources.getIdentifier(mediaUri, "drawable", context.packageName)
                if (resId != 0) resId else null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (mediaType == MediaType.VIDEO && !autoPlayVideo) {
            // معاينة فقط (البطاقات والقوائم): صورة من الفيديو + زر تشغيل، بدون تشغيل فعلي
            // عشان ما يشتغل فيديوهين مع بعض لما تنفتح صفحة العرض
            val thumb = remember(mediaUri) { com.example.data.CloudinaryUrls.videoThumbnail(mediaUri, 480) }
            Box(Modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                if (thumb != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(thumb).crossfade(true).build(),
                        contentDescription = "فيديو العرض",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "فيديو", tint = Color.White, modifier = Modifier.padding(6.dp).fillMaxSize())
                }
            }
        } else if (mediaType == MediaType.VIDEO) {
            var showFullscreen by remember { mutableStateOf(false) }
            var resumeAt by remember { mutableStateOf(0) }
            // Video playback container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        videoViewRef?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isVideoPlaying = false
                            } else {
                                vv.start()
                                isVideoPlaying = true
                            }
                        }
                    }
            ) {
                if (!hasVideoError) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // الفيديو داخل FrameLayout بالوسط عشان يحافظ على مقاسه الطولي (ريلز) بدون تمطيط
                            android.widget.FrameLayout(ctx).apply {
                                val vv = VideoView(ctx)
                                addView(vv, android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                    android.view.Gravity.CENTER
                                ))
                                vv.setVideoURI(Uri.parse(mediaUri))
                                vv.setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    vv.start()
                                    isVideoPlaying = true
                                }
                                vv.setOnErrorListener { _, _, _ ->
                                    hasVideoError = true
                                    false
                                }
                                videoViewRef = vv
                            }
                        }
                    )
                }

                // Play / Pause central button (يظهر وقت الإيقاف فقط)
                if (!isVideoPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .testTag("video_play_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "تشغيل الفيديو",
                            tint = Color.White,
                            modifier = Modifier.padding(12.dp).fillMaxSize()
                        )
                    }
                }

                // ملء الشاشة
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(40.dp)
                        .clickable {
                            videoViewRef?.let { vv ->
                                resumeAt = vv.currentPosition
                                vv.pause()
                                isVideoPlaying = false
                            }
                            showFullscreen = true
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "ملء الشاشة",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).fillMaxSize()
                    )
                }
                if (hasVideoError) {
                    Text("تعذّر تشغيل الفيديو", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }

            if (showFullscreen) {
                FullscreenVideoDialog(
                    url = mediaUri,
                    startAt = resumeAt,
                    onDismiss = { position ->
                        showFullscreen = false
                        videoViewRef?.let { vv ->
                            vv.seekTo(position)
                            vv.start()
                            isVideoPlaying = true
                        }
                    }
                )
            }

            DisposableEffect(Unit) {
                onDispose {
                    videoViewRef?.stopPlayback()
                }
            }
        } else {
            // Image Display: Check if resource ID or Coil URI
            if (drawableResId != null) {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = "صورة المنتج",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (mediaUri.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediaUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "صورة المنتج",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "صورة افتراضية",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Promotional badge at top-right
        if (promotionalBadge.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = promotionalBadge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** فيديو بملء الشاشة (طولي أو عرضي) مع زر إغلاق. يرجّع مكان الوقوف عشان يكمّل الفيديو الصغير من نفس النقطة. */
@Composable
fun FullscreenVideoDialog(url: String, startAt: Int, onDismiss: (position: Int) -> Unit) {
    var vvRef by remember { mutableStateOf<VideoView?>(null) }
    val close = { onDismiss(vvRef?.currentPosition ?: startAt) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).apply {
                        val vv = VideoView(ctx)
                        addView(vv, android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER
                        ))
                        vv.setMediaController(MediaController(ctx).also { it.setAnchorView(vv) })
                        vv.setVideoURI(Uri.parse(url))
                        vv.setOnPreparedListener { mp ->
                            mp.isLooping = true
                            if (startAt > 0) vv.seekTo(startAt)
                            vv.start()
                        }
                        vvRef = vv
                    }
                }
            )
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 16.dp)
                    .size(44.dp)
                    .clickable { close() }
            ) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.padding(10.dp).fillMaxSize())
            }
        }
        DisposableEffect(Unit) { onDispose { vvRef?.stopPlayback() } }
    }
}
