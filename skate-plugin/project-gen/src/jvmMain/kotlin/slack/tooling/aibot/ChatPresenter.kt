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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.runtime.presenter.Presenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatPresenter : Presenter<ChatScreen.State> {
  private val chatBotActionService = ChatBotActionService()

  @Composable
  override fun present(): ChatScreen.State {
    var messages by remember { mutableStateOf(emptyList<Message>()) }

    return ChatScreen.State(messages = messages) { event ->
      when (event) {
        is ChatScreen.Event.SendMessage -> {
          val newMessage = Message(event.message, isMe = true)
          messages = messages + newMessage

          CoroutineScope(Dispatchers.IO).launch {
            println("${newMessage}")
            println("ChatPresenter: Fetching a quote")
            val response = chatBotActionService.executeCommand(event.message)
            println("ChatPresenter: Received response: $newMessage")
            messages = messages + Message(response, isMe = false)
          }
        }
      }
    }
  }
}
