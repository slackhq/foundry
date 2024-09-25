package com.slack.sgp.intellij.aibot

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.slack.sgp.intellij.SkatePluginSettings
import java.nio.file.Path

class AIBotScriptFetcher(
    private val project: Project,
    private val basePath: String = project.basePath ?: "",
    ) {

    private val logger = logger<AIBotScriptFetcher>()
    fun getAIBotScript(): Unit? {
        val settings = project.service<SkatePluginSettings>()
        val aiBotScriptSetting = settings.devxpAPIcall

        return aiBotScriptSetting?.let{
            val fs = LocalFileSystem.getInstance()
            val path = Path.of(basePath, aiBotScriptSetting)
            logger.debug("getAIBotScript path location: $path")
        }
    }
}