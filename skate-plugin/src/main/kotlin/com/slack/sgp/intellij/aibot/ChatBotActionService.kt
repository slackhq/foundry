package com.slack.sgp.intellij.aibot

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File


class ChatBotActionService() {

  fun handlePromptAndResponse(
    ui: ChatWindow,
    prompt: PromptFormatter
  ) {
    ui.add(prompt.getUIPrompt(), true)
    ui.add("Loading...")

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val result = withTimeoutOrNull(60_000) { // 60 seconds timeout
          executeCommand(prompt.getRequestPrompt())
        }


        withContext(Dispatchers.Main) {
          if (result != null) {
            ui.updateMessage(result)
          } else {
            ui.updateMessage("Error: Request timed out after 60 seconds")
          }
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          ui.updateMessage("Error: ${e.message}")
        }
      }
    }
  }

  suspend fun executeCommand(question: String): String {
    val maxTokens = 2048

    val content = Content(
      messages = listOf(Message("user", question)),
      source = "curl",
      max_tokens = maxTokens
    )

    val jsonContent = Gson().toJson(content)
    // Create a script that sets up the environment and runs the command
    val scriptContent = """
        #!/bin/bash
        export PATH="/usr/local/bin:/usr/bin:${'$'}PATH"
        export SSH_OPTIONS="-T"
        
        /usr/local/bin/slack-uberproxy-curl -X POST https://devxp-ai-api.tinyspeck.com/v1/chat/ -H "Content-Type: application/json" -d '$jsonContent'
    """.trimIndent()

    // Write the script to a temporary file
    val tempScript = withContext(Dispatchers.IO) {
      File.createTempFile("run_command", ".sh")
    }
    tempScript.writeText(scriptContent)
    tempScript.setExecutable(true)

    val commandLine = GeneralCommandLine()
      .withExePath("/bin/bash")
      .withParameters(tempScript.absolutePath)
      .withRedirectErrorStream(true)

    val output = StringBuilder()
    val processHandler = OSProcessHandler(commandLine)

    processHandler.addProcessListener(object : ProcessAdapter() {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        output.append(event.text)
      }
    })

    processHandler.startNotify()
    processHandler.waitFor(600_000) // 600 seconds timeout

    // Clean up the temporary script
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

data class Content(val messages: List<Message>, val source: String = "curl", val max_tokens: Int = 512)

data class Message(val role: String, val content: String)
}
