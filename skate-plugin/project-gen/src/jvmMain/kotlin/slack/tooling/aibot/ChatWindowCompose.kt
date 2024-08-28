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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun ChatWindowCompose(modifier: Modifier = Modifier) {
  Column(
    modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.paneBackground),
    verticalArrangement = Arrangement.Bottom,
  ) {
    ConversationField(modifier)
  }
}

@Composable
fun ConversationField(modifier: Modifier = Modifier) {
  var textValue by remember { mutableStateOf(TextFieldValue()) }
  val isTextNotEmpty = textValue.text.isNotBlank()
  val requester = remember { FocusRequester() }
  Row(
    modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(4.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextField(
      value = textValue,
      onValueChange = { newText -> textValue = newText },
      modifier =
        Modifier.weight(1f)
          .padding(4.dp)
          .heightIn(min = 56.dp)
          .focusRequester(requester)
          .focusable()
          .onPreviewKeyEvent { event ->
            when {
              event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                if (!event.isShiftPressed && isTextNotEmpty) {
                  sendMessage(textValue.text)
                  textValue = TextFieldValue()
                  true
                } else if (event.isShiftPressed) {
                  textValue =
                    TextFieldValue(textValue.text + "\n", TextRange(textValue.text.length + 1))
                  true
                } else {
                  false
                }
              }
              else -> false
            }
          },
      placeholder = { Text("Start your conversation") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions =
        KeyboardActions(
          onSend = {
            if (isTextNotEmpty) {
              sendMessage(textValue.text)
              textValue = TextFieldValue()
            }
          }
        ),
    )
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          modifier = Modifier.padding(4.dp).fadeWhenDisabled(isTextNotEmpty),
          onClick = {
            if (isTextNotEmpty) {
              textValue = TextFieldValue()
            }
          },
          enabled = isTextNotEmpty,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              painter = painterResource("/drawable/send.svg"),
              contentDescription = "Send",
              modifier = Modifier.size(20.dp),
            )
            Text("Send")
          }
        }
      }
    }
  }
}

fun Modifier.fadeWhenDisabled(enabled: Boolean): Modifier {
  return this.then(if (enabled) Modifier else Modifier.alpha(0.5f))
}

fun sendMessage(text: String) {
  println("Sending message: $text")
}
