/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.IOException
import org.jetbrains.plugins.terminal.TerminalView

class ProjectGenAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val currentProject: Project = e.project ?: return
    val settings = currentProject.service<SkatePluginSettings>()
    if (!settings.isProjectGenMenuActionEnabled) return
    val terminalView: TerminalView = TerminalView.getInstance(currentProject)

    try {
      // Create new terminal window to run project gen command
      terminalView
        .createLocalShellWidget(currentProject.basePath, PROJECT_GEN_TAB_NAME)
        .executeCommand(PROJECT_GEN_COMMAND)
    } catch (err: IOException) {
      err.printStackTrace()
      LOG.warn("Failed to launch Project Gen Desktop App")
    }
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(ProjectGenAction::class.java)
    const val PROJECT_GEN_TAB_NAME: String = "ProjectGen"
    const val PROJECT_GEN_COMMAND: String = "./slackw generate-project"
  }
}
