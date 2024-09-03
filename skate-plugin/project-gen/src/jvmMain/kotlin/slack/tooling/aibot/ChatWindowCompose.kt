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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
fun ChatWindowCompose(modifier: Modifier = Modifier) {
  Column(
    modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
    verticalArrangement = Arrangement.Bottom,
  ) {
    ConversationField(modifier)
  }
}

@Composable
fun UpdatedTextArea(
  state: TextFieldState,
  modifier: Modifier = Modifier,
  placeholder: String = "Start your conversation",
) {
  Box(modifier) {
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
  val textFieldState = remember { TextFieldState() }
  val isTextNotEmpty by remember { derivedStateOf { textFieldState.text.isNotEmpty() } }
  Row(
    modifier = modifier.fillMaxWidth().padding(4.dp).height(50.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    UpdatedTextArea(
      state = textFieldState,
      modifier =
        Modifier.weight(1f).padding(end = 8.dp).onPreviewKeyEvent { event ->
          when {
            event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
              if (!event.isShiftPressed && isTextNotEmpty) {
                textFieldState.clearText()
                true
              } else if (event.isShiftPressed) {
                textFieldState.setTextAndPlaceCursorAtEnd("${textFieldState.text} \n")
                true
              } else {
                false
              }
            }
            else -> false
          }
        },
    )
    SelectableIconButton(
      selected = false,
      onClick = {
        if (isTextNotEmpty) {
          println("Clear text")
          textFieldState.clearText()
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
