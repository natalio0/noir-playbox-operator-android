package com.noirplaybox.operator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NoirColors = lightColorScheme(
    primary = Color(0xFF2F5BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8EEFF),
    onPrimaryContainer = Color(0xFF12318F),
    secondary = Color(0xFF5B6B92),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF151821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151821),
    surfaceVariant = Color(0xFFF0F3F8),
    onSurfaceVariant = Color(0xFF6A7487),
    outline = Color(0xFFDCE3EE),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1515)
)

@Composable
fun NoirPlayboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoirColors,
        typography = Typography(),
        content = content
    )
}
