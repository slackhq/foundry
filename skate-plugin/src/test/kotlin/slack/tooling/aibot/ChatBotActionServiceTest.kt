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

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class ChatBotActionServiceTest {
  @Test
  fun `say 1+1 equals 2`() {
    val result = 1 + 1
    val actual = 2
    assertThat(actual).isEqualTo(result)
  }

  @Test
  fun `test creating the Json input`() {
    val question = "What's the capital of Canada"

    val result = createJsonInput(question)

    val expectedJson =
      """
        {
            "messages": [
            {
                "role": "user",
                "content": "What's the capital of Canada"
            }
        ],
        "source": "curl",
        "max_tokens": 2048
    """
        .trimIndent()

    println("expected is ${expectedJson}")
    println("actual is ${result}")

    assertThat(result).isEqualTo(expectedJson)
  }

  private fun createJsonInput(question: String): String {
    val gsonInput = Gson()
    val content =
      Content(messages = listOf(Message(question, isMe = true)), source = "curl", max_tokens = 2048)

    val jsonContent = gsonInput.toJson(content)
    return jsonContent
  }

  data class Content(
    val messages: List<Message>,
    val source: String = "curl",
    val max_tokens: Int = 512,
  )
}
