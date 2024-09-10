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
package slack.tooling.aibot

import androidx.compose.ui.graphics.Color

object ChatColors {
  val promptBackground = Color(0xFF45494A)

  // Color(0xFF2d2f30) responseBackground
  val responseBackground = Color(0xFF2d2f30)
  //    @Composable @ReadOnlyComposable get() = JewelTheme.globalColors.infoContent

  // Color(0xFFEAEEF7) userTextColor
  val userTextColor = Color(0xFFEAEEF7)
  //    @Composable @ReadOnlyComposable get() = JewelTheme.globalColors.infoContent

  val responseTextColor = Color(0xFFE0EEF7)
}
