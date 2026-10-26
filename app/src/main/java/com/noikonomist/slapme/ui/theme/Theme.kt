package com.noikonomist.slapme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RedLight,
    onPrimary = OnRedLight,
    primaryContainer = RedContainerLight,
    onPrimaryContainer = OnRedContainerLight,
    secondary = YellowLight,
    onSecondary = OnYellowLight,
    secondaryContainer = YellowContainerLight,
    onSecondaryContainer = OnYellowContainerLight,
    tertiary = OrangeLight,
    onTertiary = OnOrangeLight,
    tertiaryContainer = OrangeContainerLight,
    onTertiaryContainer = OnOrangeContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = RedDark,
    onPrimary = OnRedDark,
    primaryContainer = RedContainerDark,
    onPrimaryContainer = OnRedContainerDark,
    secondary = YellowDark,
    onSecondary = OnYellowDark,
    secondaryContainer = YellowContainerDark,
    onSecondaryContainer = OnYellowContainerDark,
    tertiary = OrangeDark,
    onTertiary = OnOrangeDark,
    tertiaryContainer = OrangeContainerDark,
    onTertiaryContainer = OnOrangeContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceDim = SurfaceDark,
    surfaceBright = SurfaceBrightDark,
)

/** Brand colors always (comic red and yellow), light or dark with the system. No wallpaper colors. */
@Composable
fun SlapMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SlapMeTypography,
        content = content,
    )
}
