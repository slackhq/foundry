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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
fun ChatWindow(modifier: Modifier = Modifier) {
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
  Row(
    modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(4.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextArea(
      value = textValue,
      onValueChange = { newText -> textValue = newText },
      modifier =
        Modifier.weight(1f).padding(4.dp).heightIn(min = 56.dp).onPreviewKeyEvent { event ->
          when {
            (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
              event.type == KeyEventType.KeyDown -> {
              if (event.isShiftPressed) {
                val newText =
                  textValue.text.replaceRange(
                    textValue.selection.start,
                    textValue.selection.end,
                    "\n",
                  )
                val newSelection = TextRange(textValue.selection.start + 1)
                textValue = TextFieldValue(newText, newSelection)
                true
              } else {
                textValue = TextFieldValue("")
                true
              }
            }
            else -> false
          }
        },
      placeholder = { Text("Start your conversation") },
      keyboardActions = KeyboardActions.Default,
      maxLines = Int.MAX_VALUE,
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
          }
        }
      }
    }
  }
}

fun Modifier.fadeWhenDisabled(enabled: Boolean): Modifier {
  return this.then(if (enabled) Modifier else Modifier.alpha(0.5f))
}
