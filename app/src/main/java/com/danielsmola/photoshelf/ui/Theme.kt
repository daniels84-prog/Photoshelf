package com.danielsmola.photoshelf.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDECE6),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF8A5A12),
    secondaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFFF8F6F2),
    surface = Color(0xFFF8F6F2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD4CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFFBDECE6),
    secondary = Color(0xFFF2BD6B),
    secondaryContainer = Color(0xFF5E4100),
    background = Color(0xFF121414),
    surface = Color(0xFF121414),
)

@Composable
fun PhotoShelfTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
