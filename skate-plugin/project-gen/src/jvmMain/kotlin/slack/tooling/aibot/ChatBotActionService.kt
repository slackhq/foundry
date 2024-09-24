package slack.tooling.aibot

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

class ChatBotActionService {
    suspend fun executeCommand(question: String): String {
        val jsonInput = createJsonInput(question)
        val scriptContent = createScriptContent(jsonInput)
        val tempScript = createTempScript(scriptContent)
        val output = runScript(tempScript)
        tempScript.delete()
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

    @VisibleForTesting
    private fun createScriptContent(jsonInput: String): String {
        val scriptContent =
            """
        temp
      """
                .trimIndent()
        return scriptContent
    }

    @VisibleForTesting
    private suspend fun createTempScript(scriptContent: String): File {
        val tempScript = withContext(Dispatchers.IO) { File.createTempFile("run_command", ".sh") }
        tempScript.writeText(scriptContent)
        tempScript.setExecutable(true)
        return tempScript
    }

    @VisibleForTesting
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
        val result = regex.find(output.toString())?.value ?: "{}"
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