/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slack.sgp.intellij.sidepanel

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/** Triggers the creation of the Developer Services side panel. */
class OpenAssistSidePanelAction : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val thisProject = checkNotNull(event.project)
    val actionId = ActionManager.getInstance().getId(this)
    openWindow(actionId, thisProject)
  }

  /** Opens the assistant associated with the given actionId at the end of event thread */
  fun openWindow(actionId: String, project: Project) {
    ApplicationManager.getApplication().invokeLater {
      project.getService(AssistantToolWindowService::class.java).openAssistant(actionId, null)
    }
  }
}
