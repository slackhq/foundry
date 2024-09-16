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

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatBotActionService {
  suspend fun executeCommand(question: String): String {
    val gsonInput = Gson()
    val jsonObjectInput =
      JsonObject().apply {
        add(
          "messages",
          JsonArray().apply {
            add(
              JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", question)
              }
            )
          },
        )
        addProperty("source", "curl")
        addProperty("max_tokens", 2048)
      }

    val content = gsonInput.toJson(jsonObjectInput)

    val scriptContent =
      """
        #!/bin/bash
        export PATH="/usr/local/bin:/usr/bin:${'$'}PATH"
        export SSH_OPTIONS="-T"

        /usr/local/bin/slack-uberproxy-curl -X POST https://devxp-ai-api.tinyspeck.com/v1/chat/ -H "Content-Type: application/json" -d '$content'
    """
        .trimIndent()

    val tempScript = withContext(Dispatchers.IO) { File.createTempFile("run_command", ".sh") }
    tempScript.writeText(scriptContent)
    tempScript.setExecutable(true)

    val processBuilder = ProcessBuilder("/bin/bash", tempScript.absolutePath)
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val output = StringBuilder()

    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        output.append(line).append("\n")
      }
    }

    val completed = process.waitFor(600, TimeUnit.SECONDS)
    if (!completed) {
      process.destroyForcibly()
      throw RuntimeException("Process timed out after 600 seconds")
    }

    tempScript.delete()

    val regex = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val result = regex.find(output.toString())?.value ?: "{}"
    val gson = Gson()
    val jsonObject = gson.fromJson(result, JsonObject::class.java)
    val contentArray = jsonObject.getAsJsonArray("content")
    val contentObject = contentArray.get(0).asJsonObject
    val actualContent = contentObject.get("content").asString

    return actualContent
  }
}
