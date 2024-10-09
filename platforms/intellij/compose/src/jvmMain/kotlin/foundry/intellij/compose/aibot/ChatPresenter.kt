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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.runtime.presenter.Presenter

class ChatPresenter : Presenter<ChatScreen.State> {
  @Composable
  override fun present(): ChatScreen.State {
    var messages by remember { mutableStateOf(emptyList<Message>()) }
    var isLoading by remember { mutableStateOf(false) }

    return ChatScreen.State(messages = messages, isLoading = isLoading) { event ->
      when (event) {
        is ChatScreen.Event.SendMessage -> {
          val newMessage = Message(event.message, isMe = true)
          messages = messages + newMessage
          isLoading = true
          val response = Message(callApi(event.message), isMe = false)
          messages = messages + response
          isLoading = false
        }
      }
    }
  }

  private fun callApi(message: String): String {
    // function set up to call the DevXP API in the future.
    // right now, just sends back the user input message
    return ("I am a bot. You said \"${message}\"")
  }
}
