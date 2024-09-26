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
package foundry.intellij.skate.projectgen

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isProjectGenMenuActionEnabled
import foundry.intellij.skate.util.isTracingEnabled
import java.time.Instant

class ProjectGenMenuAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val currentProject = e.project ?: return
    if (!currentProject.isProjectGenMenuActionEnabled()) return
    val startTimestamp = Instant.now()
    ProjectGenWindow(currentProject, e).show()

    if (currentProject.isTracingEnabled()) {
      sendUsageTrace(currentProject, startTimestamp)
    }
  }

  fun sendUsageTrace(project: Project, startTimestamp: Instant) {
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.addTag("event", SkateTracingEvent.ProjectGen.DIALOG_OPENED)
    project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "project_generator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList(),
      )
  }
}
