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
import com.slack.sgp.intellij.tracing.ProjectGenEvent
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.util.isProjectGenMenuActionEnabled
import com.slack.sgp.intellij.util.isTracingEnabled
import java.time.Instant

class ProjectGenMenuAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val currentProject: Project = e.project ?: return
    if (!currentProject.isProjectGenMenuActionEnabled()) return
    val startTimestamp = Instant.now()
    ProjectGenWindow(currentProject, e).show()

    if (currentProject.isTracingEnabled()) {
      sendUsageTrace(currentProject, startTimestamp)
    }
  }

  fun sendUsageTrace(project: Project, startTimestamp: Instant) {
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.addTag("event", ProjectGenEvent.PROJECT_GEN_OPENED)
    SkateTraceReporter(project)
      .createPluginUsageTraceAndSendTrace(
        "project_generator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList(),
      )
  }
}
