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
package com.slack.sgp.intellij.projectgen

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.slack.sgp.intellij.tracing.SkateTracingEvent.EventType.PROJECT_GEN_OPENED
import com.slack.sgp.intellij.util.isProjectGenMenuActionEnabled
import com.slack.sgp.intellij.util.isTracingEnabled
import com.slack.sgp.intellij.util.projectGenRunCommand
import java.time.Instant

class ProjectGenMenuAction
@JvmOverloads
constructor(
  private val terminalViewWrapper: (Project) -> TerminalViewWrapper = ::RealTerminalViewWrapper,
  private val offline: Boolean = false
) : AnAction() {

  private val skateSpanBuilder = SkateSpanBuilder()
  private val startTimestamp = Instant.now()

  override fun actionPerformed(e: AnActionEvent) {
    val currentProject: Project = e.project ?: return
    val projectGenRunCommand = currentProject.projectGenRunCommand()
    if (!currentProject.isProjectGenMenuActionEnabled()) return

    executeProjectGenCommand(projectGenRunCommand, currentProject)

    if (currentProject.isTracingEnabled()) {
      sendUsageTrace(currentProject)
    }
  }

  fun executeProjectGenCommand(command: String, project: Project) {
    val terminalCommand = TerminalCommand(command, project.basePath, PROJECT_GEN_TAB_NAME)
    terminalViewWrapper(project).executeCommand(terminalCommand)
    skateSpanBuilder.addSpanTag("event", SkateTracingEvent(PROJECT_GEN_OPENED))
  }

  fun sendUsageTrace(project: Project) {
    SkateTraceReporter(project, offline)
      .createPluginUsageTraceAndSendTrace(
        "project_generator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList()
      )
  }

  companion object {
    const val PROJECT_GEN_TAB_NAME: String = "ProjectGen"
  }
}
