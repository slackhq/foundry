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
  fun `test creating the Json input`() {
    val question = "Why is the sky blue?"

    val result = createJsonInput(question)

    val expectedJson =
      """
        {
            "messages": [
            {
                "role": "user",
                "content": "Why is the sky blue?"
            }
        ],
        "source": "curl",
        "max_tokens": 512
        }
    """
        .trimIndent()

    val trimmedExpected = expectedJson.replace(Regex("\\s"), "")
    val trimmedResult = result.replace(Regex("\\s"), "")
    println("expected is ${trimmedExpected}")
    println("actual is ${trimmedResult}")

    assertThat(trimmedResult).isEqualTo(trimmedExpected)
  }

  private fun createJsonInput(question: String): String {
    val user = "user"
    val gsonInput = Gson()
    val content =
      Content(messages = listOf(Message(role = user, question)), source = "curl", max_tokens = 512)

    val jsonContent = gsonInput.toJson(content).toString()
    return jsonContent
  }

  data class Content(
    val messages: List<Message>,
    val source: String = "curl",
    val max_tokens: Int = 512,
  )
}
