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
package foundry.intellij.skate

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import foundry.intellij.skate.SkateProjectServiceImpl.Companion.WHATS_NEW_PANEL_ID
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import java.time.Instant

/** Custom listener for WhatsNew Tool Window. */
class WhatsNewToolWindowListener(private val project: Project) : ToolWindowManagerListener {
  //  Initial state of screen should be hidden
  private var wasVisible = false

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val startTimestamp = Instant.now()
    super.stateChanged(toolWindowManager)
    if (!project.isTracingEnabled()) return

    val skateSpanBuilder = SkateSpanBuilder()
    val toolWindow = toolWindowManager.getToolWindow(WHATS_NEW_PANEL_ID) ?: return
    val isVisible = toolWindow.isVisible
    val visibilityChanged = visibilityChanged(isVisible)

    if (visibilityChanged) {
      if (isVisible) {
        skateSpanBuilder.addTag("event", SkateTracingEvent.WhatsNew.PANEL_OPENED)
      } else {
        skateSpanBuilder.addTag("event", SkateTracingEvent.WhatsNew.PANEL_CLOSED)
      }
      project
        .getTraceReporter()
        .createPluginUsageTraceAndSendTrace(
          WHATS_NEW_PANEL_ID.replace('-', '_'),
          startTimestamp,
          skateSpanBuilder.getKeyValueList(),
        )
    }
  }

  fun visibilityChanged(isVisible: Boolean): Boolean {
    val visibilityChanged = isVisible != wasVisible
    wasVisible = isVisible
    return visibilityChanged
  }
}
