package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import com.example.ui.components.SplashLogo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MerchantStoreScreen
import com.example.ui.MerchantViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // نحترم تكبير الخط في الجوال لكن بحد أقصى، عشان ما ينقص الكلام أو يخرب التصميم
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = systemDensity.density,
                    fontScale = systemDensity.fontScale.coerceIn(0.85f, 1.15f)
                )
            ) {
                MyApplicationTheme {
                    var showSplash by rememberSaveable { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(com.example.ui.components.SPLASH_DURATION_MS)
                        showSplash = false
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            val viewModel: MerchantViewModel = viewModel()
                            MerchantStoreScreen(viewModel = viewModel)
                        }
                        // الشاشة الافتتاحية فوق التطبيق، تختفي تدريجياً بعد ما تجهز الخريطة
                        AnimatedVisibility(visible = showSplash, enter = fadeIn(), exit = fadeOut(tween(450))) {
                            SplashLogo()
                        }
                    }
                }
            }
        }
    }
}
