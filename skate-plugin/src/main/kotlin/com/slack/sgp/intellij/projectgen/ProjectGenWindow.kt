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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.ui.ui
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent

@Stable
class ProjectGenWindow(private val currentProject: Project?, private val event: AnActionEvent) :
  DialogWrapper(currentProject) {
  init {
    init()
    title = "Project Generator"
  }

  override fun createCenterPanel(): JComponent {
    setSize(600, 800)
    return ComposePanel().apply {
      setBounds(0, 0, 600, 800)
      setContent { DialogContent() }
    }
  }

  @Composable
  fun DialogContent() {
    val rootDir = remember {
      val path =
        currentProject?.basePath
          ?: FileSystems.getDefault()
            .getPath(".")
            .toAbsolutePath()
            .normalize()
            .toFile()
            .absolutePath
      check(Paths.get(path).toFile().isDirectory) { "Must pass a valid directory" }
      path
    }
    File("$rootDir/.projectgenlock").createNewFile()

    val circuit = remember {
      Circuit.Builder()
        .addPresenterFactory { _, _, _ ->
          ProjectGenPresenter(
            rootDir = rootDir,
            onDismissDialog = ::doOKAction,
            onSync = ::dismissDialogAndSync,
          )
        }
        .addUiFactory { _, _ ->
          ui<ProjectGenScreen.State> { state, modifier -> ProjectGen(state, modifier) }
        }
        .build()
    }
    SlackDesktopTheme() { CircuitContent(ProjectGenScreen, circuit = circuit) }
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

  private fun dismissDialogAndSync() {
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
