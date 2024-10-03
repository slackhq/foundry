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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

@Composable
fun ChatWindowUi(state: ChatScreen.State, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
    LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
      items(state.messages.reversed()) { message ->
        val isMe = message.role == "user"
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
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
  val textState by remember { mutableStateOf(TextFieldState()) }
  val isTextNotEmpty = textState.text.isNotBlank()
  val (hasStartedConversation, setHasStartedConversation) = remember { mutableStateOf(false) }

  val smileyFace = "\uD83D\uDE00"
  val conversationBubble = "\uD83D\uDCAC"

  fun sendMessage() {
    if (isTextNotEmpty) {
      setHasStartedConversation(true)
      onSendMessage(textState.text.toString())
      textState.clearText()
    }
  }
  Row(
    modifier = modifier.padding(4.dp).height(100.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.Bottom,
  ) {
    TextArea(
      // handles shift + enter for new line, enter for send
      state = textState,
      modifier =
        Modifier.weight(1f).heightIn(min = 56.dp).onPreviewKeyEvent { event ->
          when {
            (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
              event.type == KeyEventType.KeyDown -> {
              if (event.isShiftPressed) {
                textState.edit { append("\n") }
                true
              } else {
                sendMessage()
                true
              }
            }
            else -> false
          }
        },
      placeholder = {
        modifier.padding(horizontal = 4.dp, vertical = 10.dp)
        Text(
          if (hasStartedConversation) "Continue the conversation... $conversationBubble"
          else "Start your conversation... $smileyFace"
        )
      },
      textStyle = JewelTheme.defaultTextStyle,
      lineLimits = TextFieldLineLimits.MultiLine(Int.MAX_VALUE),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
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
          painter = painterResource("drawable/send.svg"),
          contentDescription = "Send",
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
private fun ChatBubble(message: Message, modifier: Modifier = Modifier) {
  val isMe = message.role == "user"
  Box(
    Modifier.wrapContentWidth()
      .padding(8.dp)
      .shadow(elevation = 0.5.dp, shape = RoundedCornerShape(25.dp), clip = true)
      .background(color = if (isMe) ChatColors.promptBackground else ChatColors.responseBackground)
      .padding(8.dp)
  ) {
    Text(
      text = message.content,
      color = if (isMe) ChatColors.userTextColor else ChatColors.responseTextColor,
      modifier = modifier.padding(8.dp),
      fontFamily = FontFamily.SansSerif,
    )
  }
}

private fun Modifier.enabled(enabled: Boolean): Modifier {
  return this.then(if (enabled) Modifier.alpha(1.0f) else Modifier.alpha(0.38f))
}

private fun TextFieldState.clearText() {
  edit { replace(0, length, "") }
}
