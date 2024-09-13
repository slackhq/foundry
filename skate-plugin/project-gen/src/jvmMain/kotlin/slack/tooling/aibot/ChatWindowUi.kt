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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
fun ChatWindowUi(state: ChatScreen.State, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().background(JewelTheme.globalColors.paneBackground)) {
    LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
      items(state.messages.reversed()) { message ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start,
        ) {
          ChatBubble(message)
        }
      }
    }
    ConversationField(
      modifier = Modifier,
      onSendMessage = { userMessage -> state.eventSink(ChatScreen.Event.SendMessage(userMessage)) },
    )
  }
}

@Composable
private fun ConversationField(modifier: Modifier = Modifier, onSendMessage: (String) -> Unit) {
  var textValue by remember { mutableStateOf(TextFieldValue()) }
  val isTextNotEmpty = textValue.text.isNotBlank()

  fun sendMessage() {
    if (isTextNotEmpty) {
      onSendMessage(textValue.text)
      textValue = TextFieldValue("")
    }
  }

  Row(
    modifier.padding(4.dp).height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.Bottom,
  ) {
    // user text input. enter sends the message and clears text
    // shift + enter adds a new line and expands the text field
    TextArea(
      value = textValue,
      onValueChange = { newText -> textValue = newText },
      modifier =
        Modifier.weight(1f).heightIn(min = 56.dp).padding(4.dp).onPreviewKeyEvent { event ->
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
                sendMessage()
                true
              }
            }
            else -> false
          }
        },
      placeholder = { Text("Start your conversation") },
    )
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
      // button will be disabled if there is no text
      IconButton(
        modifier = Modifier.padding(4.dp).enabled(isTextNotEmpty),
        onClick = {
          if (isTextNotEmpty) {
            sendMessage()
          }
        },
        enabled = isTextNotEmpty,
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

@Composable
private fun ChatBubble(message: Message, modifier: Modifier = Modifier) {
  Box(
    Modifier.wrapContentWidth()
      .padding(8.dp)
      .shadow(elevation = 0.5.dp, shape = RoundedCornerShape(25.dp), clip = true)
      .background(
        color = if (message.isMe) ChatColors.promptBackground else ChatColors.responseBackground
      )
      .padding(8.dp)
  ) {
    Text(
      text = message.text,
      color = if (message.isMe) ChatColors.userTextColor else ChatColors.responseTextColor,
      modifier = modifier.padding(8.dp),
      fontFamily = FontFamily.SansSerif,
    )
  }
}

private fun Modifier.enabled(enabled: Boolean): Modifier {
  return this.then(if (enabled) Modifier.alpha(1.0f) else Modifier.alpha(0.38f))
}
