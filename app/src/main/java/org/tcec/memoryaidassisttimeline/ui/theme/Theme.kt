package org.tcec.memoryaidassisttimeline.ui.theme

import android.app.Activity
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

// Premium Color Palette
val Slate950 = Color(0xFF020617) // Background
val Slate900 = Color(0xFF0F172A) // Surface
val Slate800 = Color(0xFF1E293B) // Surface Variant
val Slate700 = Color(0xFF334155) // Border/Lines
val Violet500 = Color(0xFF6366F1) // Primary
val Violet400 = Color(0xFF818CF8) // Primary Light
val Emerald500 = Color(0xFF10B981) // Secondary (Success)
val Rose500 = Color(0xFFF43F5E)   // Error
val Slate200 = Color(0xFFE2E8F0) // OnSurface
val Slate400 = Color(0xFF94A3B8) // OnSurfaceVariant

private val DarkColorScheme = darkColorScheme(
    primary = Violet500,
    onPrimary = Color.White,
    primaryContainer = Violet500.copy(alpha = 0.2f),
    onPrimaryContainer = Violet400,
    
    secondary = Emerald500,
    onSecondary = Color.White,
    secondaryContainer = Emerald500.copy(alpha = 0.2f),
    onSecondaryContainer = Emerald500,
    
    background = Slate950,
    onBackground = Slate200,
    
    surface = Slate900,
    onSurface = Slate200,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    
    outline = Slate700,
    outlineVariant = Slate700.copy(alpha = 0.5f),
    
    error = Rose500
)

@Composable
fun MemoryAidAssistTimelineTheme(
    darkTheme: Boolean = true, // Force Dark Theme
    dynamicColor: Boolean = false, // Disable dynamic color
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}