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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.slack.sgp.intellij.util.getJavaPackageName
import com.slack.sgp.intellij.util.isCircuitGeneratorEnabled
import java.io.File
import slack.tooling.projectgen.circuitgen.CircuitPresenter
import slack.tooling.projectgen.circuitgen.CircuitScreen

class UdfViewModelGenerator : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    val currentProject = event.project ?: return
    if (!currentProject.isCircuitGeneratorEnabled()) return
    val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val selectedDir = if (selectedFile.isDirectory) selectedFile.path else selectedFile.parent.path
    createDialog(selectedDir, currentProject)
  }

  private fun createDialog(directory: String, project: Project): DialogWrapper {
    var featureNameField = ""
    var assistedInject = true
    val centerPanel = panel {
      row("Name") {
        textField().bindText({ featureNameField }, { featureNameField = it }).validationOnApply {
          if (it.text.isBlank()) error("Text cannot be empty") else null
        }
      }
      row {
        checkBox("Enable Assisted Injection")
          .bindSelected(
            getter = { assistedInject },
            setter = { assistedInject = it }
          )
      }
    }
    // Create and return the dialog
    return dialog(title = "UDF ViewModel Convert", panel = centerPanel).apply {
      if (showAndGet()) {
          createUdfViewModel(featureNameField, assistedInject, directory, project)
        }
      }
    }
  private fun createUdfViewModel(featureName: String, assistedInject: Boolean, directory: String, project: Project) {
    val circuitComponents = listOf(CircuitScreen(), CircuitPresenter(assistedInject, noUi = true))
    circuitComponents.forEach { component ->
      component.writeToFile(directory, directory.getJavaPackageName(), featureName)
    }

    // Refresh local file changes and open new Circuit screen file in editor
    val circuitScreenPath = File("${directory}/${featureName}Screen.kt")
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(circuitScreenPath)?.let { file ->
      FileEditorManager.getInstance(project).openFile(file)
    }
    LocalFileSystem.getInstance().refresh(true)
    GradleDependencyManager().addMissingGradleDependency(project, directory)
  }
}
