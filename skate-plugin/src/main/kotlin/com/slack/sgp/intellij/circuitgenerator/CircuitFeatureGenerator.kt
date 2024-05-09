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
package com.slack.sgp.intellij.circuitgenerator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.slack.sgp.intellij.util.isCircuitGeneratorEnabled
import java.io.File
import java.nio.file.Path
import slack.tooling.projectgen.circuitgen.FileGenerationListener

class CircuitFeatureGenerator : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    val currentProject = event.project ?: return
    if (!currentProject.isCircuitGeneratorEnabled()) return
    val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val selectedDirectory =
      if (selectedFile.isDirectory) selectedFile.path else selectedFile.parent.path
    showFeatureDialog(currentProject, selectedDirectory)
    GradleDependencyManager().addMissingGradleDependency(currentProject, selectedDirectory)
  }

  private fun showFeatureDialog(project: Project, selectedDir: String) {
    val listener =
      object : FileGenerationListener {
        override fun onFilesGenerated(fileNames: String) {
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(fileNames))?.let {
            FileEditorManager.getInstance(project).openFile(it)
          }
          LocalFileSystem.getInstance().refresh(true)
        }
      }
    CircuitGeneratorUi(
        "Circuit Feature Generator",
        project,
        Path.of(selectedDir),
        listener,
      )
      .show()
  }
}
