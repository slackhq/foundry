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
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class ChatBotActionService(private val scriptPath: Path) {
    fun executeCommand(question: String): String {
        val jsonInput = createJsonInput(question)
//        val scriptContent = createScriptContent(jsonInput)
//        val tempScript = createTempScript(scriptContent)
        println("executing command $scriptPath")
        val output = runScript(Paths.get(scriptPath.toString()), jsonInput)

//        val output = runScript(scriptPath, jsonInput)
//        val output = scriptPath?.let { runScript(it, jsonInput) }
        println("output $output")
//        tempScript.delete()
        return parseOutput(output)
//        return output?.let { parseOutput(it) } ?: "Sorry, I couldn't generate a response."
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

    @VisibleForTesting
    private suspend fun createTempScript(scriptContent: String): File {
        val tempScript = withContext(Dispatchers.IO) { File.createTempFile("run_command", ".sh") }
        tempScript.writeText(scriptContent)
        tempScript.setExecutable(true)
        return tempScript
    }

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
        val result = regex.find(output.toString())?.value ?: "{}"
        println("parse Output $result")
        val gson = Gson()
        val jsonObject = gson.fromJson(result, JsonObject::class.java)
        println("json Object $jsonObject")
        val contentArray = jsonObject.getAsJsonArray("content")
        println("content array $contentArray")
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