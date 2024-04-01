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
package com.slack.sgp.intellij.projectgen

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent
import slack.tooling.projectgen.ProjectGenUi

class ProjectGenWindow(private val currentProject: Project?, private val event: AnActionEvent) :
  DialogWrapper(currentProject), ProjectGenUi.Events {

  init {
    init()
    title = "Project Generator"
  }

  override fun createCenterPanel(): JComponent {
    setSize(600, 800)
    return ProjectGenUi.createPanel(
      projectPath =
        currentProject?.basePath?.let(Paths::get) ?: FileSystems.getDefault().getPath("."),
      isDark = !JBColor.isBright(),
      width = 600,
      height = 800,
      events = this,
    )
  }

  /* Disable default OK and Cancel action button in Dialog window. */
  override fun createActions(): Array<Action> = emptyArray()

  override fun doCancelAction() {
    super.doCancelAction()
    // Remove projectlock file when exit application
    deleteProjectLock()
  }

  override fun doOKAction() {
    super.doOKAction()
    // Remove projectlock file when exit application
    deleteProjectLock()
  }

  override fun dismissDialogAndSync() {
    doOKAction()
    val am: ActionManager = ActionManager.getInstance()
    val sync: AnAction = am.getAction("Android.SyncProject")
    sync.actionPerformed(event)
  }

  private fun deleteProjectLock() {
    val projectLockFile = File(currentProject?.basePath + "/.projectgenlock")
    if (projectLockFile.exists()) {
      projectLockFile.delete()
    }
  }
}
