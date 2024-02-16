// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("MagicNumber")

package com.slack.sgp.intellij.projectgen

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Borrowed from `MaterialColors` in SKColors.kt. */
internal object DesktopColors {
  private val mdThemeLightPrimary = Color(0xFF1264A3)
  private val mdThemeLightOnPrimary = Color(0xFFFFFFFF)
  private val mdThemeLightInversePrimary = mdThemeLightOnPrimary
  private val mdThemeLightPrimaryContainer = Color(0xFF9ED6FA) // On Inverse Highlight 1
  private val mdThemeLightOnPrimaryContainer = Color(0xFF1D1C1D) // On Inverse Highlight 1
  private val mdThemeLightSecondary = Color(0xFF1264A3)
  private val mdThemeLightOnSecondary = Color(0xFFFFFFFF)
  private val mdThemeLightSecondaryContainer = Color(0xFF9ED6FA) // Highlight 3, Jade 0
  private val mdThemeLightOnSecondaryContainer = Color(0xFF1D1C1D) // Highlight 3, Jade 0
  private val mdThemeLightTertiary = Color(0xFF007A5A)
  private val mdThemeLightOnTertiary = Color(0xFFFFFFFF)
  private val mdThemeLightTertiaryContainer = Color(0xFFE3FFF3) // Highlight 3, Jade 0
  private val mdThemeLightOnTertiaryContainer = Color(0xFF1D1C1D) // Highlight 3, Jade 0
  private val mdThemeLightError = Color(0xFFE01E5A)
  private val mdThemeLightOnError = Color(0xFFFFFFFF)
  private val mdThemeLightErrorContainer = Color(0xFFFFE8EF) // Destructive, Flamingo 0
  private val mdThemeLightOnErrorContainer = Color(0xFF1D1C1D) // Destructive, Flamingo 0
  private val mdThemeLightBackground = Color(0xFFFFFFFF)
  private val mdThemeLightOnBackground = Color(0xFF1D1C1D)
  private val mdThemeLightSurface = Color(0xFFFFFFFF)
  private val mdThemeLightOnSurface = Color(0xFF1D1C1D)
  private val mdThemeLightInverseSurface = Color(0xFF1D1C1D) // Inverse of surface
  private val mdThemeLightInverseOnSurface = Color(0xFFFFFFFF) // Inverse of onSurface
  private val mdThemeLightSurfaceVariant = Color(0xFFEAEAEA) // On Inverse Variant 1
  private val mdThemeLightOnSurfaceVariant = Color(0xFF1D1C1D) // On Inverse Variant 1
  // We don't want m3's tonal tinting for this so we just reuse the surface value
  private val mdThemeLightSurfaceTint = mdThemeLightSurface
  private val mdThemeLightOutline = Color(0xFF5E5D60) // Outline Primary
  private val mdThemeLightShadow = Color(0xB31D1C1D) // Background / Modal

  // We reuse the same values for primary/secondary/tertiary in both light and dark
  private val mdThemeDarkPrimary = mdThemeLightPrimary
  private val mdThemeDarkOnPrimary = mdThemeLightOnPrimary
  private val mdThemeDarkInversePrimary = mdThemeDarkOnPrimary
  private val mdThemeDarkPrimaryContainer = mdThemeLightPrimaryContainer
  private val mdThemeDarkOnPrimaryContainer = mdThemeLightOnPrimaryContainer
  private val mdThemeDarkSecondary = mdThemeLightSecondary
  private val mdThemeDarkOnSecondary = mdThemeLightOnSecondary
  private val mdThemeDarkSecondaryContainer = mdThemeLightSecondaryContainer
  private val mdThemeDarkOnSecondaryContainer = mdThemeLightOnSecondaryContainer
  private val mdThemeDarkTertiary = mdThemeLightTertiary
  private val mdThemeDarkOnTertiary = mdThemeLightOnTertiary
  private val mdThemeDarkTertiaryContainer = mdThemeLightTertiaryContainer
  private val mdThemeDarkOnTertiaryContainer = mdThemeLightOnTertiaryContainer
  private val mdThemeDarkError = mdThemeLightError
  private val mdThemeDarkOnError = mdThemeLightOnError
  private val mdThemeDarkErrorContainer = Color(0xFF93000A) // Destructive, Flamingo 0
  private val mdThemeDarkOnErrorContainer = Color(0xFFFFDAD6) // Destructive, Flamingo 0
  private val mdThemeDarkBackground = Color(0xFF1A1D21)
  private val mdThemeDarkOnBackground = Color(0xFFD1D2D3)
  private val mdThemeDarkSurface = Color(0xFF1A1D21)
  private val mdThemeDarkOnSurface = Color(0xFFD1D2D3)
  private val mdThemeDarkInverseSurface = Color(0xFFE4E2E6) // Inverse of surface
  private val mdThemeDarkInverseOnSurface = Color(0xFF1B1B1F) // Inverse of onSurface
  private val mdThemeDarkSurfaceVariant = Color(0xFF252425) // Container Gray 10
  private val mdThemeDarkOnSurfaceVariant = Color(0xFFC5C6D0) // On Inverse Variant 1
  /**
   * We don't want tonal tinting but we _do_ want to lighten the surface color when elevated. The
   * metaphor is that it's closer to the light source, so we just lighten with white.
   */
  private val mdThemeDarkSurfaceTint = Color(0xFFFFFFFF)
  private val mdThemeDarkOutline = Color(0xFF8F9099) // Outline Primary
  private val mdThemeDarkShadow = Color(0xFF000000) // Background / Modal

  val LightTheme =
    lightColorScheme(
      primary = mdThemeLightPrimary,
      onPrimary = mdThemeLightOnPrimary,
      primaryContainer = mdThemeLightPrimaryContainer,
      onPrimaryContainer = mdThemeLightOnPrimaryContainer,
      secondary = mdThemeLightSecondary,
      onSecondary = mdThemeLightOnSecondary,
      secondaryContainer = mdThemeLightSecondaryContainer,
      onSecondaryContainer = mdThemeLightOnSecondaryContainer,
      tertiary = mdThemeLightTertiary,
      onTertiary = mdThemeLightOnTertiary,
      tertiaryContainer = mdThemeLightTertiaryContainer,
      onTertiaryContainer = mdThemeLightOnTertiaryContainer,
      error = mdThemeLightError,
      errorContainer = mdThemeLightErrorContainer,
      onError = mdThemeLightOnError,
      onErrorContainer = mdThemeLightOnErrorContainer,
      background = mdThemeLightBackground,
      onBackground = mdThemeLightOnBackground,
      surface = mdThemeLightSurface,
      onSurface = mdThemeLightOnSurface,
      surfaceVariant = mdThemeLightSurfaceVariant,
      onSurfaceVariant = mdThemeLightOnSurfaceVariant,
      outline = mdThemeLightOutline,
      inverseOnSurface = mdThemeLightInverseOnSurface,
      inverseSurface = mdThemeLightInverseSurface,
      inversePrimary = mdThemeLightInversePrimary,
      surfaceTint = mdThemeLightSurfaceTint,
      scrim = mdThemeLightShadow,
    )

  val DarkTheme =
    darkColorScheme(
      primary = mdThemeDarkPrimary,
      onPrimary = mdThemeDarkOnPrimary,
      primaryContainer = mdThemeDarkPrimaryContainer,
      onPrimaryContainer = mdThemeDarkOnPrimaryContainer,
      secondary = mdThemeDarkSecondary,
      onSecondary = mdThemeDarkOnSecondary,
      secondaryContainer = mdThemeDarkSecondaryContainer,
      onSecondaryContainer = mdThemeDarkOnSecondaryContainer,
      tertiary = mdThemeDarkTertiary,
      onTertiary = mdThemeDarkOnTertiary,
      tertiaryContainer = mdThemeDarkTertiaryContainer,
      onTertiaryContainer = mdThemeDarkOnTertiaryContainer,
      error = mdThemeDarkError,
      errorContainer = mdThemeDarkErrorContainer,
      onError = mdThemeDarkOnError,
      onErrorContainer = mdThemeDarkOnErrorContainer,
      background = mdThemeDarkBackground,
      onBackground = mdThemeDarkOnBackground,
      surface = mdThemeDarkSurface,
      onSurface = mdThemeDarkOnSurface,
      surfaceVariant = mdThemeDarkSurfaceVariant,
      onSurfaceVariant = mdThemeDarkOnSurfaceVariant,
      outline = mdThemeDarkOutline,
      inverseOnSurface = mdThemeDarkInverseOnSurface,
      inverseSurface = mdThemeDarkInverseSurface,
      inversePrimary = mdThemeDarkInversePrimary,
      surfaceTint = mdThemeDarkSurfaceTint,
      scrim = mdThemeDarkShadow,
    )
}
