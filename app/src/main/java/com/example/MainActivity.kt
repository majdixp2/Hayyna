package com.example

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
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val viewModel: MerchantViewModel = viewModel()
                        MerchantStoreScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
