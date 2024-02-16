package com.slack.sgp.intellij.projectgen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.intellij.ui.JBColor

private val FONT_FAMILY = FontFamily.Default

@Composable
fun SlackDesktopTheme(
  // Note: isSystemInDarkTheme() isn't actually implemented in desktop yet:
  // https://github.com/JetBrains/compose-jb/issues/169
  useDarkMode: Boolean = !JBColor.isBright(),
  content: @Composable () -> Unit,
) {
  val typography =
    MaterialTheme.typography.copy(
      displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = FONT_FAMILY),
      displayMedium = MaterialTheme.typography.displayMedium.copy(fontFamily = FONT_FAMILY),
      displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = FONT_FAMILY),
      headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FONT_FAMILY),
      headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = FONT_FAMILY),
      headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontFamily = FONT_FAMILY),
      titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FONT_FAMILY),
      titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = FONT_FAMILY),
      titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = FONT_FAMILY),
      bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FONT_FAMILY),
      bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FONT_FAMILY),
      bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = FONT_FAMILY),
      labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = FONT_FAMILY),
      labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = FONT_FAMILY),
      labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = FONT_FAMILY),
    )
  MaterialTheme(
    colorScheme = if (useDarkMode) DesktopColors.DarkTheme else DesktopColors.LightTheme,
    typography = typography,
    content = content,
  )
}
