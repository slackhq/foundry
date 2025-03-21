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
package foundry.intellij.skate.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import foundry.intellij.skate.SkateProjectService
import foundry.intellij.skate.SkateProjectServiceImpl
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import java.time.Instant

/** Action to open the "What's New" panel on demand. */
class ShowWhatsNewAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val startTimestamp = Instant.now()

    // Show the What's New window, forcing it to show even if there are no new entries
    project.service<SkateProjectService>().showWhatsNewPanel(forceShow = true)

    // Send usage trace if tracing is enabled
    if (project.isTracingEnabled()) {
      val skateSpanBuilder = SkateSpanBuilder()
      skateSpanBuilder.addTag("event", SkateTracingEvent.WhatsNew.PANEL_OPENED)
      project
        .getTraceReporter()
        .createPluginUsageTraceAndSendTrace(
          "whats_new_button_clicked",
          startTimestamp,
          skateSpanBuilder.getKeyValueList(),
        )
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    // Enable the action if we have a project
    e.presentation.isEnabled = project != null

    // Set the selection state based on whether the tool window is visible
    if (project != null) {
      val toolWindowManager = ToolWindowManager.getInstance(project)
      val toolWindow = toolWindowManager.getToolWindow(SkateProjectServiceImpl.WHATS_NEW_PANEL_ID)
      e.presentation.putClientProperty(Toggleable.SELECTED_KEY, toolWindow?.isVisible == true)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
