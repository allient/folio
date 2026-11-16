package com.allient.folio

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.allient.folio.home.AccentPreset
import com.allient.folio.home.AccentPresets
import com.allient.folio.home.HomePreferences
import com.allient.folio.home.LocalHomeStyle
import com.allient.folio.home.homeStyle

/**
 * The one place that turns the user's Preferences (accent colour and corner style) into the app's Material theme,
 * so every screen, dialog, sheet and menu follows the selected style. Screens read `MaterialTheme.colorScheme`
 * and `MaterialTheme.shapes`; they never hardcode the accent.
 */
fun folioAccent(id: String): AccentPreset = AccentPresets.firstOrNull { it.id == id } ?: AccentPresets.first()

fun folioColorScheme(accent: AccentPreset): ColorScheme = lightColorScheme(
    primary = accent.primary, onPrimary = Color.White, primaryContainer = accent.soft, onPrimaryContainer = FolioBrand.Ink,
    secondary = FolioBrand.Ink, onSecondary = Color.White, secondaryContainer = accent.soft, onSecondaryContainer = FolioBrand.Ink,
    tertiary = accent.primary,
    background = FolioBrand.Paper, onBackground = FolioBrand.Ink, surface = Color.White, onSurface = FolioBrand.Ink,
    onSurfaceVariant = FolioBrand.Muted, outline = Color(0xFFCBC5B8), outlineVariant = FolioBrand.Hairline,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White, surfaceContainerLow = FolioBrand.Paper,
    // Menus, dialogs and sheets: white warmed with a touch of the chosen accent.
    surfaceContainer = lerp(Color.White, accent.soft, .35f),
    surfaceContainerHigh = lerp(Color.White, accent.soft, .6f),
    surfaceContainerHighest = accent.soft,
)

fun folioShapes(corners: String): Shapes = when (corners) {
    "sharp" -> Shapes(RoundedCornerShape(2.dp), RoundedCornerShape(3.dp), RoundedCornerShape(4.dp), RoundedCornerShape(6.dp), RoundedCornerShape(6.dp))
    "soft" -> Shapes(RoundedCornerShape(12.dp), RoundedCornerShape(18.dp), RoundedCornerShape(24.dp), RoundedCornerShape(30.dp), RoundedCornerShape(34.dp))
    else -> Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(16.dp), RoundedCornerShape(24.dp), RoundedCornerShape(28.dp))
}

@Composable
fun FolioTheme(preferences: HomePreferences, content: @Composable () -> Unit) {
    val accent = folioAccent(preferences.accent)
    MaterialTheme(colorScheme = folioColorScheme(accent), shapes = folioShapes(preferences.corners), typography = folioTypography()) {
        CompositionLocalProvider(LocalHomeStyle provides homeStyle(preferences), content = content)
    }
}

/** Container for every custom dialog: same shape, colour and elevation as Material dialogs, in the selected style. */
@Composable
fun FolioDialogSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp, content = content)
}
