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
package foundry.intellij.skate.projectgen

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import foundry.intellij.compose.projectgen.ProjectGenUi
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Action
import javax.swing.JComponent
import kotlin.io.path.absolutePathString

class ProjectGenWindow(currentProject: Project, private val event: AnActionEvent) :
  DialogWrapper(currentProject), ProjectGenUi.Events {

  private val projectPath =
    (currentProject.basePath?.let(Paths::get) ?: FileSystems.getDefault().getPath("."))
      .normalize()
      .also { check(Files.isDirectory(it)) { "Must pass a valid directory" } }
  private val lockFile: Path

  init {
    init()
    title = "Project Generator"
    lockFile = projectPath.resolve(".projectgenlock")
    deleteProjectLock()
    Files.createFile(lockFile)
  }

  override fun createCenterPanel(): JComponent {
    setSize(600, 800)
    return ProjectGenUi.createPanel(
      rootDir = projectPath.absolutePathString(),
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
    Files.deleteIfExists(lockFile)
  }
}
