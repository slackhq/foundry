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
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import foundry.intellij.compose.aibot.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.VisibleForTesting
import java.io.File

class ChatBotActionService(
  private val scriptPath: Path,
  private val apiLink: String
) {
  suspend fun executeCommand(question: String): String {
    val jsonInput = createJsonInput(question)
    val authInfo = getAuthInfo(scriptPath)
    println("authInfo $authInfo")
    val (userAgent, cookies) = parseAuthJson(authInfo)

    val scriptContent = createScriptContent(userAgent, cookies, jsonInput)

    val tempScript = createTempScript(scriptContent)

    val response = runScript(tempScript)

    println("User Agent: $userAgent")
    println("Cookies: $cookies")

    println("authInfo $authInfo")
    println("scriptContent $scriptContent")

    println("Response from API: $response")

    val parsedOutput = parseOutput(response)

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
  private fun getAuthInfo(scriptPath: Path): String {
    val processBuilder = ProcessBuilder("/bin/bash", scriptPath.toString())
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

    val jsonStartIndex = output.indexOf("{")
    return if (jsonStartIndex != -1) {
      output.substring(jsonStartIndex).trim()
    } else {
      throw IllegalArgumentException("No valid JSON found in output")
    }
  }

  private fun parseAuthJson(authJsonString: String): Pair<String, String> {
    val gson = Gson()
    val authJson = gson.fromJson(authJsonString, JsonObject::class.java)

    println("authJson $authJson")
    println("Parsed AuthJson: $authJson")

    val userAgent = authJson.get("user-agent")?.asString ?: "unknown"
    val machineCookie = authJson.get("machine-cookie")?.asString ?: "unknown"
    val slauthSession = authJson.get("slauth-session")?.asString ?: "unknown"
    val tsa2 = authJson.get("tsa2")?.asString ?: "unknown"

    val cookies = "machine-cookie=$machineCookie; slauth-session=$slauthSession; tsa2=$tsa2"

    println("User Agent: $userAgent")
    println("cookies $cookies")
    return Pair(userAgent, cookies)
  }

  private fun createScriptContent(userAgent: String, cookies: String, jsonInput: String): String {
    return """
        #!/bin/bash
        curl -s -X POST $apiLink \
            -H "Content-Type: application/json" \
            -H "User-Agent: $userAgent" \
            -H "Cookie: $cookies" \
            -d '$jsonInput'
    """.trimIndent()
  }

  private suspend fun createTempScript(scriptContent: String): File {
    return withContext(Dispatchers.IO) {
      val tempScript = File.createTempFile("run_command", ".sh")
      tempScript.writeText(scriptContent)
      tempScript.setExecutable(true)
      tempScript
    }
  }

  private fun runScript(tempScript: File): String {
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
    return output.toString()
  }

  @VisibleForTesting
  private fun parseOutput(output: String): String {
    println("output: $output")
    val regex = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val result = regex.find(output)?.value ?: "{}"
    val gson = Gson()
    val jsonObject = gson.fromJson(result, JsonObject::class.java)
    val contentArray = jsonObject.getAsJsonArray("content")
    val contentObject = contentArray.get(0).asJsonObject
    val actualContent = contentObject.get("content").asString

    println("actual content $actualContent")

    return actualContent
  }

  data class Content(
    val messages: List<Message>,
    val source: String = "curl",
    val max_tokens: Int = 512,
  )
}
