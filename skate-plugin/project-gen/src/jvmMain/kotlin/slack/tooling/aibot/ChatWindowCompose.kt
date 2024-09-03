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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
fun ChatWindowCompose(modifier: Modifier = Modifier) {
  // add modifier: Modifier = Modifier in params
  Column(
    modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
    verticalArrangement = Arrangement.Bottom,
  ) {
    ConversationField(modifier)
  }
}

@Composable
fun TextBox(modifier: Modifier = Modifier) {
  Text(modifier = modifier.padding(4.dp), text = "Hello world")
}

@Composable
fun UpdatedTextArea(
  state: TextFieldState,
  modifier: Modifier = Modifier,
  placeholder: String = "",
) {
  Box(modifier = modifier.padding(8.dp)) {
    Text("Debug: Inside UpdatedTextArea", color = Color.Magenta)
    TextArea(
      state = state,
      modifier = Modifier.fillMaxSize(),
      placeholder = {
        if (state.text.isEmpty()) {
          Text(
            placeholder,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
          )
        }
      },
    )
  }
}

@Composable
fun ConversationField(modifier: Modifier = Modifier) {
  var text by remember { mutableStateOf("") }
  var textValue by remember { mutableStateOf(TextFieldValue()) }
  val textFieldState = remember { TextFieldState() }
  val placeholder = "Start your conversation"
  val isTextNotEmpty by remember { derivedStateOf { textFieldState.text.isNotEmpty() } }

  Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().height(100.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      UpdatedTextArea(
        state = textFieldState,
        modifier = Modifier.fillMaxSize(),
        placeholder = placeholder,
      )
    }
    SelectableIconButton(
      selected = false,
      onClick = {
        if (isTextNotEmpty) {
          // Handle send action
          text = ""
          textValue = TextFieldValue()
        }
      },
      enabled = isTextNotEmpty,
      modifier = Modifier.fadeWhenDisabled(isTextNotEmpty),
    ) {
      Icon(
        painter = painterResource("/drawable/send.svg"),
        contentDescription = "Send",
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

fun Modifier.fadeWhenDisabled(enabled: Boolean): Modifier {
  return this.then(if (enabled) Modifier else Modifier.alpha(0.5f))
}
