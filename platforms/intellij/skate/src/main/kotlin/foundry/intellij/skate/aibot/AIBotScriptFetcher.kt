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
package com.slack.sgp.intellij.aibot

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import foundry.intellij.skate.SkatePluginSettings
import java.nio.file.Files
import java.nio.file.Path

class AIBotScriptFetcher(
  private val project: Project,
  private val basePath: String = project.basePath ?: "",
) {
  val settings = project.service<SkatePluginSettings>()

  fun getAIBotScript(): Path {
    val aiBotScriptSetting = settings.devxpAPIcall
    println("aiBotScriptSetting $aiBotScriptSetting")

    return aiBotScriptSetting.let { scriptSetting ->
      val path = Path.of(basePath, scriptSetting)
      println("getAIBotScript path location: ${path.toAbsolutePath()}")
      println(printScriptContent(path))
      path
    }
  }

  fun getAIBotAPI(): String? {
    val aiBotAPILink = settings.devxpAPIlink
    return aiBotAPILink
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
