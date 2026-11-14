package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CeramicaLightColorScheme = lightColorScheme(
    primary = Cyan500,
    onPrimary = OnDark,
    primaryContainer = Cyan950,
    onPrimaryContainer = Cyan300,
    secondary = Amber500,
    onSecondary = OnDark,
    secondaryContainer = Amber950,
    onSecondaryContainer = Amber300,
    tertiary = Emerald500,
    onTertiary = OnDark,
    tertiaryContainer = Emerald950,
    onTertiaryContainer = Emerald300,
    error = Rose500,
    onError = OnDark,
    errorContainer = Rose950,
    onErrorContainer = Rose400,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate850,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    outlineVariant = Slate800,
    surfaceContainerLowest = Slate900,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700
)

val CeramicaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KaderPosTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CeramicaLightColorScheme,
        typography = Typography,
        shapes = CeramicaShapes,
        content = content
    )
}
