package org.storyteller_f.bailongmap.ui.theme

import org.storyteller_f.bailongmap.data.settings.MapAppearance
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp



private val Light = lightColorScheme(
    primary = Color(0xFF146C5D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE4), onPrimaryContainer = Color(0xFF143D34),
    secondary = Color(0xFF526E65), secondaryContainer = Color(0xFFEDF4F0),
    onSecondaryContainer = Color(0xFF19383B), tertiary = Color(0xFF486C8A),
    background = Color(0xFFF8FBF9), onBackground = Color(0xFF19383B),
    surface = Color.White, onSurface = Color(0xFF19383B),
    surfaceVariant = Color(0xFFEDF4F0), onSurfaceVariant = Color(0xFF5E7470),
    outline = Color(0xFF738780), outlineVariant = Color(0xFFDBE5DF),
    surfaceContainer = Color(0xFFF2F7F4), surfaceContainerLow = Color(0xFFF8FBF9),
    surfaceContainerHigh = Color(0xFFEDF4F0), surfaceContainerHighest = Color(0xFFE6EFEB),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF92D8BE), onPrimary = Color(0xFF133E30),
    primaryContainer = Color(0xFF285344), onPrimaryContainer = Color(0xFFCCEFDE),
    secondary = Color(0xFFB0C8BE), secondaryContainer = Color(0xFF294242),
    onSecondaryContainer = Color(0xFFE5EFEC), tertiary = Color(0xFFABCBE4),
    background = Color(0xFF102324), onBackground = Color(0xFFE5EFEC),
    surface = Color(0xFF172E30), onSurface = Color(0xFFE5EFEC),
    surfaceVariant = Color(0xFF294242), onSurfaceVariant = Color(0xFFAEC1BA),
    outlineVariant = Color(0xFF3F5751), surfaceContainer = Color(0xFF203738),
    surfaceContainerLow = Color(0xFF172E30), surfaceContainerHigh = Color(0xFF294242),
    surfaceContainerHighest = Color(0xFF344B47),
)

@Composable
fun BailongTheme(appearance: MapAppearance = MapAppearance.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        MapAppearance.SYSTEM -> isSystemInDarkTheme()
        MapAppearance.LIGHT -> false
        MapAppearance.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
