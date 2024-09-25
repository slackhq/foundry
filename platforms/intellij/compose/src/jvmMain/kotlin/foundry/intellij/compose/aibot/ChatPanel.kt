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
package foundry.intellij.compose.aibot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposePanel
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitContent
import foundry.intellij.compose.projectgen.SlackDesktopTheme
import java.awt.Dimension
import javax.swing.JComponent

object ChatPanel {
  fun createPanel(): JComponent {
    return ComposePanel().apply {
      preferredSize = Dimension(400, 600)
      setContent { SlackDesktopTheme { ChatApp() } }
    }
  }

  @Composable
  private fun ChatApp() {
    val circuit = remember {
      Circuit.Builder()
        .addPresenter<ChatScreen, ChatScreen.State>(ChatPresenter())
        .addUi<ChatScreen, ChatScreen.State> { state, modifier -> ChatWindowUi(state, modifier) }
        .build()
    }

    CircuitContent(ChatScreen, circuit = circuit)
  }
}
