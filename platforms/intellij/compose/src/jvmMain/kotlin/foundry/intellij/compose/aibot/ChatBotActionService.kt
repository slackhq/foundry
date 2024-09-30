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
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import foundry.intellij.compose.aibot.Message
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.VisibleForTesting

class ChatBotActionService(private val scriptPath: Path) {
  fun executeCommand(question: String): String {
    val jsonInput = createJsonInput(question)
    val output = runScript(Paths.get(scriptPath.toString()), jsonInput)
    val parsedOutput = parseOutput(output)
    return parsedOutput
  }

  @VisibleForTesting
  private fun createJsonInput(question: String): String {
    val gsonInput = Gson()
    val jsonObjectInput =
      Content(
        messages = listOf(Message(role = "user", question)),
        source = "curl",
        max_tokens = 2048,
      )

    val content = gsonInput.toJson(jsonObjectInput)

    println("jsonContent $content")

    return content
  }

  @VisibleForTesting
  private fun runScript(scriptPath: Path, jsonInput: String): String {
    val processBuilder = ProcessBuilder("/bin/bash", scriptPath.toString(), jsonInput)
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val output = StringBuilder()

    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        output.append(line).append("\n")
        println("Script output: $line")
      }
    }

    val completed = process.waitFor(600, TimeUnit.SECONDS)
    if (!completed) {
      process.destroyForcibly()
      throw RuntimeException("Process timed out after 600 seconds")
    }

    return output.toString().trim()
  }

  @VisibleForTesting
  private fun parseOutput(output: String): String {
    println("parseOutput beginning: $output")
    val regex = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val result = regex.find(output)?.value ?: "{}"
    println("parse Output $result")

    val gson = Gson()

    val jsonStrings = result.trim().split("\n")

    var foundFirst = false
    var secondJsonObject: JsonObject? = null

    for (json in jsonStrings) {
      if (json.trim().startsWith("{") && json.trim().endsWith("}")) {
        try {
          if (foundFirst) {
            secondJsonObject = gson.fromJson(json, JsonObject::class.java)
            break
          }
          foundFirst = true
        } catch (e: JsonSyntaxException) {
          println("Invalid JSON: ${e.message}")
        }
      }
    }

    var actualContent = ""
    secondJsonObject?.let { jsonObject ->
      val contentArray = jsonObject.getAsJsonArray("content")
      if (contentArray.size() > 0) {
        val contentObject = contentArray[0].asJsonObject
        actualContent = contentObject.get("content").asString
        println("Actual content: $actualContent")
      } else {
        println("Content array is empty.")
      }
    } ?: println("No valid second JSON object found.")

    return actualContent
  }

  data class Content(
    val messages: List<Message>,
    val source: String = "curl",
    val max_tokens: Int = 512,
  )
}
