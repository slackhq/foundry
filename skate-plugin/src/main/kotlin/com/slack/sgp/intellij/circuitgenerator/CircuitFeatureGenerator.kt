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
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.slack.sgp.intellij.util.circuitPresenterBaseTest
import com.slack.sgp.intellij.util.circuitUiBaseTest
import com.slack.sgp.intellij.util.getJavaPackageName
import com.slack.sgp.intellij.util.isCircuitGeneratorEnabled
import slack.tooling.projectgen.circuitgen.CircuitComponent
import java.io.File
import slack.tooling.projectgen.circuitgen.CircuitPresenter
import slack.tooling.projectgen.circuitgen.CircuitScreen
import slack.tooling.projectgen.circuitgen.CircuitTest
import slack.tooling.projectgen.circuitgen.CircuitUiFeature

class CircuitFeatureGenerator : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    val currentProject = event.project ?: return
    if (!currentProject.isCircuitGeneratorEnabled()) return
    val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val selectedDirectory = if (selectedFile.isDirectory) selectedFile.path else selectedFile.parent.path
    showFeatureDialog(currentProject, selectedDirectory)
  }

  private fun showFeatureDialog(project: Project, directory: String) {
    var featureName = ""
    var uiScreen = true
    var presenterClass = true
    var assistedInject = true
    var testClass = true

    val centerPanel = panel {
      row("Name") {
        textField().bindText({ featureName }, { featureName = it })
          .validationOnApply { if (it.text.isBlank()) error("Text cannot be empty") else null }
      }
      row("Class(es) to generate") {
        panel {
          row {
            checkBox("UI Screen").bindSelected({ uiScreen }, { uiScreen = it })
            checkBox("Presenter").bindSelected({ presenterClass }, { presenterClass = it })
          }
        }
      }
      row {
        checkBox("Enable Assisted Injection").bindSelected({ assistedInject }, { assistedInject = it })
      }
      row {
        checkBox("Generate Test Class").bindSelected({ testClass }, { testClass = it })
      }
    }

    val dialog = dialog(title = "New Circuit Feature", panel = centerPanel)
    dialog.showAndGet()
    collectComponents(uiScreen, presenterClass, assistedInject, testClass, project)
      .forEach { component ->
        component.writeToFile(
          directory,
          directory.getJavaPackageName(),
          featureName
        )
      }

    // Refresh local file changes and open new Circuit screen file in editor
    val screenFile = "${directory}/${featureName}Screen.kt"
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(screenFile))?.let {
      FileEditorManager.getInstance(project).openFile(it)
    }
    LocalFileSystem.getInstance().refresh(true)
    GradleDependencyManager().addMissingGradleDependency(project, directory)
  }

  private fun collectComponents(
    uiScreen: Boolean,
    presenter: Boolean,
    assistedInject: Boolean,
    generateTest: Boolean,
    project: Project
  ): MutableList<CircuitComponent> {
    return mutableListOf<CircuitComponent>().apply {
      if (uiScreen) {
        addAll(listOf(CircuitScreen(), CircuitUiFeature()))
        if (generateTest) add(CircuitTest(fileSuffix = "UiTest", project.circuitUiBaseTest()))
      }
      if (presenter) {
        add(CircuitPresenter(assistedInject))
        if (generateTest) add(CircuitTest(fileSuffix = "PresenterTest", project.circuitPresenterBaseTest()))
      }
    }
  }


}