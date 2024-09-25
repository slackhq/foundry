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
package foundry.intellij.compose.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import slack.tooling.projectgen.ProjectGenUi
import slack.tooling.projectgen.ProjectGenUi.ProjectGenApp

fun main() = singleWindowApplication {
  var isDark by remember { mutableStateOf(false) }
  IntUiTheme(isDark) {
    Column(Modifier.background(JewelTheme.globalColors.panelBackground)) {
      DefaultButton(modifier = Modifier.padding(16.dp), onClick = { isDark = !isDark }) {
        Text("Toggle dark mode")
      }
      ProjectGenApp(
        rootDir = "rootDir",
        events =
          object : ProjectGenUi.Events {
            override fun doOKAction() {
              println("doOKAction")
            }

            override fun dismissDialogAndSync() {
              println("dismissDialogAndSync")
            }
          },
      )
    }
  }
}
