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
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import slack.tooling.aibot.ChatBotActionService

class ChatPresenter(private val scriptPath: Path, apiLink: String) : Presenter<ChatScreen.State> {
  val user = "user"
  val bot = "bot"
  private val chatBotActionService = ChatBotActionService(scriptPath, apiLink)

  @Composable
  override fun present(): ChatScreen.State {
    var messages by remember { mutableStateOf(emptyList<Message>()) }

    println("print script path $scriptPath")

    return ChatScreen.State(messages = messages) { event ->
      when (event) {
        is ChatScreen.Event.SendMessage -> {
          val newMessage = Message(role = user, event.message)
          messages = messages + newMessage
          CoroutineScope(Dispatchers.IO).launch {
            val response = chatBotActionService.executeCommand(event.message)
            messages = messages + Message(role = bot, response)
          }
        }
      }
    }
  }
}
