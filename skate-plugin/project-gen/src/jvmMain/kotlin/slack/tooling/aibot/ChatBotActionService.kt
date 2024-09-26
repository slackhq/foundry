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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.VisibleForTesting

class ChatBotActionService(private val scriptPath: Path) {
  fun executeCommand(question: String): String {
    val jsonInput = createJsonInput(question)
    //        val scriptContent = createScriptContent(jsonInput)
    //        val tempScript = createTempScript(scriptContent)
    println("executing command $scriptPath")
    val output = runScript(Paths.get(scriptPath.toString()), jsonInput)

    //        val output = runScript(scriptPath, jsonInput)
    //        val output = scriptPath?.let { runScript(it, jsonInput) }
    val parsedOutput = parseOutput(output)
    println("output that is parsed${parsedOutput}")
    //        tempScript.delete()
    return parseOutput(output)
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

  //    @VisibleForTesting
  //    private fun createScriptContent(jsonInput: String): String {
  //        val scriptContent = """"""
  //        return scriptContent
  //    }

  //    @VisibleForTesting
  //    private suspend fun createTempScript(scriptContent: String): File {
  //        val tempScript = withContext(Dispatchers.IO) { File.createTempFile("run_command", ".sh")
  // }
  //        tempScript.writeText(scriptContent)
  //        tempScript.setExecutable(true)
  //        return tempScript
  //    }

  @VisibleForTesting
  private fun runScript(scriptPath: Path, jsonInput: String): String {
    //        val processBuilder = ProcessBuilder("/bin/bash", tempScript.absolutePath)
    println("running script")
    println("scriptPath for runScript: $scriptPath")
    println("jsonInput for runScript: $jsonInput")

    //        val command = listOf("/bin/bash", scriptPath.absolutePathString(), jsonInput)
    //        println(command)
    //        val processBuilder = ProcessBuilder(command)

    //        val processBuilder = ProcessBuilder(scriptPath.toString(), jsonInput)
    val processBuilder = ProcessBuilder("/bin/bash", scriptPath.toString(), jsonInput)
    processBuilder.redirectErrorStream(true)
    println("ProcessBuilder command: ${processBuilder.command().joinToString(" ")}")

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

    //        tempScript.delete()
    println("printing claude output: $output")
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

    // Filter and parse the second JSON object
    for (json in jsonStrings) {
      // Check if the line contains valid JSON format
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
      println("Deserialization successful: $jsonObject")

      val contentArray = jsonObject.getAsJsonArray("content")
      println("Content array: $contentArray")

      if (contentArray.size() > 0) {
        val contentObject = contentArray[0].asJsonObject
        actualContent = contentObject.get("content").asString
        println("Actual content: $actualContent")
      } else {
        println("Content array is empty.")
      }
    } ?: println("No valid second JSON object found.")

    return actualContent

    //        val jsonObject = gson.fromJson(result, JsonObject::class.java)
    //        println("json Object $jsonObject")
    //        val contentArray = jsonObject.getAsJsonArray("content")
    //        println("content array $contentArray")
    //        val contentObject = contentArray.get(0).asJsonObject
    //        val actualContent = contentObject.get("content").asString
    //
    //        println("actual content $actualContent")

    //        return actualContent
  }

  data class Content(
    val messages: List<Message>,
    val source: String = "curl",
    val max_tokens: Int = 512,
  )
}
