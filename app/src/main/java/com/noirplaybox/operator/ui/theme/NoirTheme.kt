    package com.noirplaybox.operator.ui.theme

    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.lightColorScheme
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.graphics.Color

    private val NoirColors = lightColorScheme(
        primary = Color(0xFF2463EB),
        onPrimary = Color.White,

        background = Color(0xFFF7F9FC),
        onBackground = Color(0xFF111827),

        surface = Color.White,
        onSurface = Color(0xFF111827),

        surfaceVariant = Color(0xFFF4F7FB),
        onSurfaceVariant = Color(0xFF6B7F9E),

        outline = Color(0xFFDDE5EF),

        error = Color(0xFFEF4444)
    )

    @Composable
    fun NoirPlayboxTheme(
        content: @Composable () -> Unit
    ) {
        MaterialTheme(
            colorScheme = NoirColors,
            content = content
        )
    }