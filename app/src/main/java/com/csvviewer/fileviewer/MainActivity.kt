package com.csvviewer.fileviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import android.graphics.Color as AColor
import com.csvviewer.fileviewer.data.ThemeMode
import com.csvviewer.fileviewer.ui.AppNav
import com.csvviewer.fileviewer.ui.CsvTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = intent?.data
        setContent {
            val dark = when (vm.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status/navigation bar icons follow the app theme, not only the system one
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            CsvTheme(dark) { AppNav(vm, incoming) }
        }
    }
}
