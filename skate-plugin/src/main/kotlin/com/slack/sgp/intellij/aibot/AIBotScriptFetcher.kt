package com.slack.sgp.intellij.aibot

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.slack.sgp.intellij.SkatePluginSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class AIBotScriptFetcher(
    private val project: Project,
    private val basePath: String = project.basePath ?: "",
    ) {

    fun getAIBotScript(): Path {
        val settings = project.service<SkatePluginSettings>()
        val aiBotScriptSetting = settings.devxpAPIcall
        println("aiBotScriptSetting $aiBotScriptSetting")

        return aiBotScriptSetting.let { scriptSetting ->
//            val fs = LocalFileSystem.getInstance()
            val path = Path.of(basePath, scriptSetting)
            println("getAIBotScript path location: ${path.toAbsolutePath()}")
            println(printScriptContent(path))
//            fs.findFileByNioFile(path)?.toNioPath()?.toFile()
            path
        }
    }

    private fun printScriptContent(scriptPath: Path) {
        try {
            println("Script content:")
            println("--------------------")
            Files.readAllLines(scriptPath).forEach { println(it) }
            println("--------------------")
        } catch (e: Exception) {
            println("Error reading script content: ${e.message}")
        }
    }
}

