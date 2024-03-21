/*
 * Copyright (C) 2024 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slack.sgp.intellij.projectgen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.intellij.ui.JBColor

private val FONT_FAMILY = FontFamily.Default

@Composable
fun SlackDesktopTheme(useDarkMode: Boolean = !JBColor.isBright(), content: @Composable () -> Unit) {
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
